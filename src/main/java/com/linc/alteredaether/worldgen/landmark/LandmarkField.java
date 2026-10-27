/*
 * Altered Aether
 * Copyright (C) 2026 Linc Stoel
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public
 * License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If not, see
 * <https://www.gnu.org/licenses/>.
 */
package com.linc.alteredaether.worldgen.landmark;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.linc.alteredaether.config.AACommonConfig;
import com.linc.alteredaether.worldgen.TerrainProbe;
import com.linc.alteredaether.worldgen.basin.BasinField;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;

/**
 * Every landmark in one world, as a pure function of the seed, in the manner of {@link BasinField}. Each candidate is
 * checked against its biome first, which is cheap, and only then against the terrain, which is read exactly through a
 * {@link TerrainProbe} on the terrain as it is before any landmark or basin, so the answer is the same whichever chunk
 * asks first. Found sites are cached per grid cell.
 *
 * <p>The great fracture is found from biomes alone, because the terrain it is checked against runs through it: the
 * rift's density function asks this field where the fractures are.
 */
public final class LandmarkField {
    private static final Map<ResourceKey<Level>, LandmarkField> FIELDS = new ConcurrentHashMap<>();
    /** Spots tried per cell, and how many of them may go on to a full check against the terrain. */
    private static final int ATTEMPTS = 64, CHECKS = 6;

    public static void put(ResourceKey<Level> dimension, @Nullable LandmarkField field) {
        if (field == null) {
            FIELDS.remove(dimension);
        } else {
            FIELDS.put(dimension, field);
        }
    }

    @Nullable
    public static LandmarkField get(ResourceKey<Level> dimension) {
        return FIELDS.get(dimension);
    }

    private final long seed;
    private final TerrainProbe probe;
    @Nullable
    private final BasinField basins;
    private final double vertical;
    private final int buildTop;
    private final double fractureScale;
    private final Map<LandmarkType, Cache<Long, Optional<Landmark>>> cells = new EnumMap<>(LandmarkType.class);

    /**
     * @param probe    the terrain before any landmark is added
     * @param basins   the world's basins, for the double atolls, if it has any
     * @param vertical vertical scale for the height mode: 1 in the tall Aether
     * @param buildTop the top of the world, which the tallest landmarks reach toward
     */
    public LandmarkField(long seed, TerrainProbe probe, @Nullable BasinField basins, double vertical, int buildTop) {
        this.seed = seed;
        this.probe = probe;
        this.basins = basins;
        this.vertical = vertical;
        this.buildTop = buildTop;
        this.fractureScale = Math.max(1, AACommonConfig.RIFT_FRACTURE_SIZE.get());
        for (LandmarkType type : LandmarkType.values()) {
            if (type.gridded()) {
                cells.put(type, CacheBuilder.newBuilder().maximumSize(1 << 12).build());
            }
        }
    }

    TerrainProbe probe() {
        return probe;
    }

    public double vertical() {
        return vertical;
    }

    int buildTop() {
        return buildTop;
    }

    long seed() {
        return seed;
    }

    // ---------------------------------------------------------------------------------------------- queries --

    /** Every landmark whose extent reaches into the given block rectangle. */
    public List<Landmark> touching(int minX, int minZ, int maxX, int maxZ) {
        List<Landmark> found = new ArrayList<>(1);
        if (rarity() <= 0) {
            return found;
        }
        for (LandmarkType type : LandmarkType.values()) {
            if (!type.gridded() || type == LandmarkType.GREAT_FRACTURE) {
                continue;
            }
            int cell = cell(type);
            for (int cx = Math.floorDiv(minX - type.reach, cell); cx <= Math.floorDiv(maxX + type.reach, cell); cx++) {
                for (int cz = Math.floorDiv(minZ - type.reach, cell); cz <= Math.floorDiv(maxZ + type.reach, cell); cz++) {
                    in(type, cx, cz).filter(site -> touches(site, minX, minZ, maxX, maxZ)).ifPresent(found::add);
                }
            }
        }
        // A fracture is all terrain except its decoration, which needs it here.
        fractureNear((minX + maxX) / 2, (minZ + maxZ) / 2).filter(site -> touches(site, minX, minZ, maxX, maxZ))
                .ifPresent(found::add);
        if (basins != null) {
            int cell = basins.doubleCell();
            for (int cx = Math.floorDiv(minX - cell / 2, cell); cx <= Math.floorDiv(maxX + cell / 2, cell); cx++) {
                for (int cz = Math.floorDiv(minZ - cell / 2, cell); cz <= Math.floorDiv(maxZ + cell / 2, cell); cz++) {
                    basins.doubleInCell(cx, cz).map(DoubleAtoll::new)
                            .filter(site -> touches(site, minX, minZ, maxX, maxZ)).ifPresent(found::add);
                }
            }
        }
        return found;
    }

    /**
     * The great fracture whose influence may cover this column, if any. Each fracture stays inside its own cell, so
     * only that cell is consulted: this is called for every interpolation point in the rift.
     */
    public Optional<GreatFracture> fractureNear(int x, int z) {
        if (rarity() <= 0) {
            return Optional.empty();
        }
        int cell = cell(LandmarkType.GREAT_FRACTURE);
        return in(LandmarkType.GREAT_FRACTURE, Math.floorDiv(x, cell), Math.floorDiv(z, cell))
                .map(GreatFracture.class::cast)
                .filter(site -> {
                    double dx = x - site.x(), dz = z - site.z();
                    return dx * dx + dz * dz <= site.extent() * site.extent();
                });
    }

    /** The nearest landmark of a type to a point, searching out ring by ring of grid cells. */
    public Optional<Landmark> nearest(LandmarkType type, int x, int z, int maxDistance) {
        int cell = type.gridded() ? cell(type) : basins == null ? 0 : basins.doubleCell();
        if (cell == 0 || rarity() <= 0) {
            return Optional.empty();
        }
        int ox = Math.floorDiv(x, cell), oz = Math.floorDiv(z, cell);
        Landmark best = null;
        double bestDistance = Double.MAX_VALUE;
        int rings = maxDistance / cell + 1;
        for (int ring = 0; ring <= rings; ring++) {
            for (int cx = ox - ring; cx <= ox + ring; cx++) {
                for (int cz = oz - ring; cz <= oz + ring; cz++) {
                    if (Math.max(Math.abs(cx - ox), Math.abs(cz - oz)) != ring) {
                        continue;
                    }
                    Optional<Landmark> site = type.gridded() ? in(type, cx, cz)
                            : basins.doubleInCell(cx, cz).map(DoubleAtoll::new);
                    if (site.isPresent()) {
                        double d = Math.hypot(site.get().x() - x, site.get().z() - z);
                        if (d < bestDistance) {
                            bestDistance = d;
                            best = site.get();
                        }
                    }
                }
            }
            // Anything in a further ring is at least this far away.
            if (best != null && bestDistance < ring * (double) cell) {
                break;
            }
        }
        return Optional.ofNullable(best);
    }

    private static boolean touches(Landmark site, int minX, int minZ, int maxX, int maxZ) {
        double dx = Math.max(0, Math.max(minX - site.x(), site.x() - maxX));
        double dz = Math.max(0, Math.max(minZ - site.z(), site.z() - maxZ));
        return dx * dx + dz * dz <= site.extent() * site.extent();
    }

    // ------------------------------------------------------------------------------------------------ cells --

    int cell(LandmarkType type) {
        // A fracture's slabs are rift cells merged together, so it grows with them, and so must its grid.
        return type == LandmarkType.GREAT_FRACTURE ? (int) Math.round(type.cell * fractureScale) : type.cell;
    }

    /** How far from its centre a landmark of this type may reach: the margin it keeps from its cell's edge. */
    int reach(LandmarkType type) {
        return type == LandmarkType.GREAT_FRACTURE ? GreatFracture.maxExtent(fractureScale) : type.reach;
    }

    private Optional<Landmark> in(LandmarkType type, int cx, int cz) {
        long key = ((long) cx << 32) ^ (cz & 0xFFFFFFFFL);
        try {
            return cells.get(type).get(key, () -> Optional.ofNullable(find(type, cx, cz)));
        } catch (ExecutionException e) {
            throw new IllegalStateException(e.getCause());
        }
    }

    @Nullable
    private Landmark find(LandmarkType type, int cx, int cz) {
        RandomSource random = random(type, cx, cz);
        if (random.nextDouble() >= type.chance / rarity()) {
            return null;
        }
        int cell = cell(type);
        int jitter = Math.max(0, cell / 2 - reach(type));
        int checks = 0;
        for (int attempt = 0; attempt < ATTEMPTS && checks < CHECKS; attempt++) {
            int x = cx * cell + cell / 2 + random.nextInt(2 * jitter + 1) - jitter;
            int z = cz * cell + cell / 2 + random.nextInt(2 * jitter + 1) - jitter;
            if (!type.suits(probe.biomeAt(x, z)) || probe.regionWeight(x, z) < 0.99) {
                continue;
            }
            checks++;
            Landmark site = switch (type) {
                case TEMPEST_SPIRES -> SpireCluster.tryAt(this, x, z, random);
                case FROZEN_SPIRE -> FrozenSpire.tryAt(this, x, z, random);
                case GRAND_CASCADE -> GrandCascade.tryAt(this, x, z, random);
                case GREAT_FRACTURE -> GreatFracture.tryAt(this, x, z, random, fractureScale);
                case GRANDFATHER -> Grandfather.tryAt(this, x, z, random);
                case VILLAGE_TREE -> VillageTreeSite.tryAt(this, x, z, random);
                case DOUBLE_ATOLL -> null;
            };
            if (site != null && site.extent() <= reach(type)) {
                return site;
            }
        }
        return null;
    }

    /** True if most of a ring of points around (x, z) lies in the biomes a type suits. */
    boolean ringSuits(LandmarkType type, int x, int z, double radius, int needed) {
        int hits = 0;
        for (int d = 0; d < 8; d++) {
            double angle = d * Math.PI / 4;
            int px = x + (int) Math.round(Math.cos(angle) * radius), pz = z + (int) Math.round(Math.sin(angle) * radius);
            if (type.suits(probe.biomeAt(px, pz)) && probe.regionWeight(px, pz) >= 0.99) {
                hits++;
            }
        }
        return hits >= needed;
    }

    private static double rarity() {
        return AACommonConfig.LANDMARK_RARITY.get();
    }

    private RandomSource random(LandmarkType type, int cx, int cz) {
        return new XoroshiroRandomSource(BasinField.mix(seed ^ (0x51ED2701L * (type.ordinal() + 1)))
                ^ BasinField.mix(cx * 0x9E3779B97F4A7C15L + cz));
    }

    /** A fixed value in [0, 1) for a block, for choosing a landmark's materials the same way in every chunk. */
    static double hash(long salt, int x, int y, int z) {
        long h = BasinField.mix(salt ^ BasinField.mix(x * 0x9E3779B97F4A7C15L ^ y * 0xC2B2AE3D27D4EB4FL ^ z));
        return (h >>> 11) * 0x1.0p-53;
    }

    /** The visiting spot of a landmark: its centre, at the height worth arriving at. */
    public static BlockPos visit(Landmark site) {
        return new BlockPos(site.x(), site.visitY(), site.z());
    }
}
