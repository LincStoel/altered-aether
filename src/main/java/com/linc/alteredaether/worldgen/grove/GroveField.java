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
package com.linc.alteredaether.worldgen.grove;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.linc.alteredaether.config.AACommonConfig;
import com.linc.alteredaether.worldgen.TerrainProbe;
import com.linc.alteredaether.worldgen.basin.BasinField;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;

/**
 * Every giant tree in one world, as a pure function of the seed, in the manner of the landmark field but far denser:
 * a jittered grid with at most one giant per cell, so in the Skyroot Cathedral they stand shoulder to shoulder. Each
 * giant is joined to its nearest neighbours by a {@link Link}; a link belongs to one of its two ends, so it is drawn
 * once. Sites are checked against the terrain through a {@link TerrainProbe}, which every chunk reads the same way,
 * and cached per cell.
 */
public final class GroveField {
    static final String CATHEDRAL = "altered_aether:skyroot_cathedral";
    static final String NURSELOG = "altered_aether:nurselog_hollow";
    /** How far any giant may reach from its centre. */
    public static final int REACH = 64;
    /** Grid cell size at the default spacing. */
    private static final int CELL = 56;
    /** Neighbours further apart than this many cells are never linked. */
    private static final double LINK_CELLS = 1.9;
    private static final int LINKS = 3;
    private static final double CHANCE = 0.92, BRIDGE_CHANCE = 0.35, NURSELOG_SHARE = 0.7;
    private static final Map<ResourceKey<Level>, GroveField> FIELDS = new ConcurrentHashMap<>();

    public static void put(ResourceKey<Level> dimension, @Nullable GroveField field) {
        if (field == null) {
            FIELDS.remove(dimension);
        } else {
            FIELDS.put(dimension, field);
        }
    }

    @Nullable
    public static GroveField get(ResourceKey<Level> dimension) {
        return FIELDS.get(dimension);
    }

    private final long seed;
    private final TerrainProbe probe;
    private final double vertical;
    private final int buildTop;
    private final int cell;
    private final double maxLink;
    // A giant's plan is a few hundred shapes, so only the cells around the chunks being generated are kept.
    private final Cache<Long, Optional<Giant>> sites = CacheBuilder.newBuilder().maximumSize(1 << 10).build();
    private final Cache<Long, List<Long>> neighbours = CacheBuilder.newBuilder().maximumSize(1 << 12).build();
    private final Cache<Long, List<Giant>> links = CacheBuilder.newBuilder().maximumSize(1 << 10).build();

    /**
     * @param vertical vertical scale for the height mode: 1 in the tall Aether
     * @param buildTop the top of the world, which no crown may pass
     */
    public GroveField(long seed, TerrainProbe probe, double vertical, int buildTop) {
        this.seed = seed;
        this.probe = probe;
        this.vertical = vertical;
        this.buildTop = buildTop;
        this.cell = (int) Math.round(CELL * AACommonConfig.GIANT_SPACING.get());
        this.maxLink = cell * LINK_CELLS;
    }

    // ---------------------------------------------------------------------------------------------- queries --

    /** Every giant and link whose plan reaches into the given block rectangle. */
    public List<Giant> touching(int minX, int minZ, int maxX, int maxZ) {
        List<Giant> found = new ArrayList<>();
        for (int cx = Math.floorDiv(minX - REACH, cell); cx <= Math.floorDiv(maxX + REACH, cell); cx++) {
            for (int cz = Math.floorDiv(minZ - REACH, cell); cz <= Math.floorDiv(maxZ + REACH, cell); cz++) {
                site(cx, cz).filter(g -> g.bounds().touches(minX, minZ, maxX, maxZ)).ifPresent(found::add);
            }
        }
        int scan = (int) Math.ceil(maxLink) + 8;
        for (int cx = Math.floorDiv(minX - scan, cell); cx <= Math.floorDiv(maxX + scan, cell); cx++) {
            for (int cz = Math.floorDiv(minZ - scan, cell); cz <= Math.floorDiv(maxZ + scan, cell); cz++) {
                for (Giant link : links(cx, cz)) {
                    if (link.bounds().touches(minX, minZ, maxX, maxZ)) {
                        found.add(link);
                    }
                }
            }
        }
        return found;
    }

    /** The nearest giant of a type to a point, searching out ring by ring of cells. */
    public Optional<Giant> nearest(GiantType type, int x, int z, int maxDistance) {
        int ox = Math.floorDiv(x, cell), oz = Math.floorDiv(z, cell);
        Giant best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int ring = 0; ring <= maxDistance / cell + 1; ring++) {
            for (int cx = ox - ring; cx <= ox + ring; cx++) {
                for (int cz = oz - ring; cz <= oz + ring; cz++) {
                    if (Math.max(Math.abs(cx - ox), Math.abs(cz - oz)) != ring) {
                        continue;
                    }
                    Optional<Giant> site = site(cx, cz).filter(g -> g.type() == type);
                    if (site.isPresent()) {
                        double d = Math.hypot(site.get().x() - x, site.get().z() - z);
                        if (d < bestDistance) {
                            bestDistance = d;
                            best = site.get();
                        }
                    }
                }
            }
            if (best != null && bestDistance < ring * (double) cell) {
                break;
            }
        }
        return Optional.ofNullable(best);
    }

    // ------------------------------------------------------------------------------------------------ cells --

    private static long key(int cx, int cz) {
        return ((long) cx << 32) ^ (cz & 0xFFFFFFFFL);
    }

    private static int cellX(long key) {
        return (int) (key >> 32);
    }

    private static int cellZ(long key) {
        return (int) key;
    }

    Optional<Giant> site(int cx, int cz) {
        return cached(sites, key(cx, cz), () -> Optional.ofNullable(find(cx, cz)));
    }

    private static <T> T cached(Cache<Long, T> cache, long key, Callable<T> loader) {
        try {
            return cache.get(key, loader);
        } catch (ExecutionException e) {
            throw new IllegalStateException(e.getCause());
        }
    }

    @Nullable
    private Giant find(int cx, int cz) {
        RandomSource random = random(key(cx, cz), 0);
        if (random.nextDouble() >= CHANCE) {
            return null;
        }
        int jitter = (int) (cell * 0.3);
        for (int attempt = 0; attempt < 3; attempt++) {
            int x = cx * cell + cell / 2 + random.nextInt(2 * jitter + 1) - jitter;
            int z = cz * cell + cell / 2 + random.nextInt(2 * jitter + 1) - jitter;
            GiantType type = pick(probe.biomeAt(x, z), random);
            if (type == null || probe.regionWeight(x, z) < 0.99) {
                continue;
            }
            int ground = footing(x, z);
            if (ground == Integer.MIN_VALUE) {
                continue;
            }
            // The fallen are thinner on the ground than the living.
            if (type.biome.equals(NURSELOG) && random.nextDouble() >= NURSELOG_SHARE) {
                return null;
            }
            Sketch sketch = new Sketch(probe, random, vertical, buildTop, x, z, ground);
            sketch.palette = palette(cx, cz, type.biome);
            sketch.tended = random.nextDouble() < AACommonConfig.TENDED_FRACTION.get();
            sketch.campChance = AACommonConfig.CAMP_CHANCE.get();
            sketch.secretChance = AACommonConfig.SECRET_CHANCE.get();
            double heart = AACommonConfig.GOLDEN_HEART_CHANCE.get();
            switch (type) {
                case HOLLOW_ELDER -> HollowElder.sketch(sketch);
                case TUNNEL_GIANT -> TunnelGiant.sketch(sketch);
                case TULE_MASS -> TuleMass.sketch(sketch);
                case BOW_COLONY -> BowColony.sketch(sketch);
                case CATHEDRAL_RING -> CathedralRing.sketch(sketch);
                case FALLEN_COLOSSUS -> FallenColossus.sketch(sketch, heart);
                case SNAG -> Snag.sketch(sketch);
                case COLOSSAL_STUMP -> ColossalStump.sketch(sketch, heart);
                case LATTICE_SKELETON -> LatticeSkeleton.sketch(sketch);
                case REGROWTH_RING -> RegrowthRing.sketch(sketch);
                case LINK -> {
                }
            }
            Giant giant = sketch.done(type, random.nextLong());
            if (giant.extent() <= REACH) {
                return giant;
            }
        }
        return null;
    }

    /**
     * The wood a giant is made of. Colonies share it: a block of four by four cells all draw the same palette, so a
     * stand of Deep Aether wood reads as one clone among the skyroot.
     */
    private GrovePalette palette(int cx, int cz, String biome) {
        if (!ModList.get().isLoaded("deep_aether") || !AACommonConfig.compat("deep_aether")) {
            return GrovePalette.SKYROOT;
        }
        List<GrovePalette> woods = biome.equals(CATHEDRAL) ? GrovePalette.DEEP_CATHEDRAL : GrovePalette.DEEP_NURSELOG;
        RandomSource random = random(key(Math.floorDiv(cx, 4), Math.floorDiv(cz, 4)), 2);
        if (woods.isEmpty() || random.nextDouble() >= AACommonConfig.DEEP_AETHER_WOOD_CHANCE.get()) {
            return GrovePalette.SKYROOT;
        }
        return woods.get(random.nextInt(woods.size()));
    }

    @Nullable
    private static GiantType pick(String biome, RandomSource random) {
        int total = 0;
        for (GiantType type : GiantType.values()) {
            if (type.biome.equals(biome)) {
                total += type.weight;
            }
        }
        if (total == 0) {
            return null;
        }
        int roll = random.nextInt(total);
        for (GiantType type : GiantType.values()) {
            if (type.biome.equals(biome)) {
                roll -= type.weight;
                if (roll < 0) {
                    return type;
                }
            }
        }
        return null;
    }

    /**
     * The ground a giant stands on: the lowest of its centre and a ring round it, so trunks root down rather than
     * float over a dip. MIN_VALUE where the ground is too broken, or too near an island's edge, to hold one.
     */
    private int footing(int x, int z) {
        int centre = probe.surface(x, z);
        if (centre == Integer.MIN_VALUE) {
            return Integer.MIN_VALUE;
        }
        int low = centre, held = 0;
        int tolerance = (int) Math.round(14 * vertical);
        for (int d = 0; d < 8; d++) {
            double angle = d * Math.PI / 4;
            int g = probe.surfaceNear(x + (int) Math.round(Math.cos(angle) * 10),
                    z + (int) Math.round(Math.sin(angle) * 10), centre, centre - 40, centre + 40);
            if (g != Integer.MIN_VALUE && Math.abs(g - centre) <= tolerance) {
                held++;
                low = Math.min(low, g);
            }
        }
        return held >= 7 ? low : Integer.MIN_VALUE;
    }

    /** The cells of this cell's nearest linkable neighbours, nearest first. */
    private List<Long> neighbours(int cx, int cz) {
        return cached(neighbours, key(cx, cz), () -> {
            Optional<Giant> self = site(cx, cz);
            if (self.isEmpty()) {
                return List.of();
            }
            Giant a = self.get();
            List<long[]> candidates = new ArrayList<>();
            int span = (int) Math.ceil(LINK_CELLS) + 1;
            for (int nx = cx - span; nx <= cx + span; nx++) {
                for (int nz = cz - span; nz <= cz + span; nz++) {
                    if (nx == cx && nz == cz) {
                        continue;
                    }
                    Optional<Giant> other = site(nx, nz);
                    if (other.isPresent()) {
                        double d = Math.hypot(other.get().x() - a.x(), other.get().z() - a.z());
                        if (d <= maxLink) {
                            candidates.add(new long[]{key(nx, nz), Double.doubleToLongBits(d)});
                        }
                    }
                }
            }
            candidates.sort(Comparator.comparingDouble(c -> Double.longBitsToDouble(c[1])));
            List<Long> nearest = new ArrayList<>(LINKS);
            for (int i = 0; i < Math.min(LINKS, candidates.size()); i++) {
                nearest.add(candidates.get(i)[0]);
            }
            return List.copyOf(nearest);
        });
    }

    /**
     * The links this cell's giant owns: one to each of its nearest neighbours, unless the neighbour also counts this
     * giant among its own nearest and has the lower cell key, in which case that end draws it.
     */
    private List<Giant> links(int cx, int cz) {
        long self = key(cx, cz);
        return cached(links, self, () -> {
            Optional<Giant> a = site(cx, cz);
            if (a.isEmpty()) {
                return List.of();
            }
            List<Giant> owned = new ArrayList<>();
            for (long other : neighbours(cx, cz)) {
                if (other < self && neighbours(cellX(other), cellZ(other)).contains(self)) {
                    continue;
                }
                Giant b = site(cellX(other), cellZ(other)).orElseThrow();
                boolean cathedral = a.get().type().crowned() && b.type().crowned();
                Giant link = Link.sketch(probe, vertical, buildTop, a.get(), b,
                        random(Math.min(self, other) * 31 + Math.max(self, other), 1), cathedral ? BRIDGE_CHANCE : 0,
                        AACommonConfig.TENDED_FRACTION.get());
                if (link != null) {
                    owned.add(link);
                }
            }
            return List.copyOf(owned);
        });
    }

    private RandomSource random(long key, int stream) {
        return new XoroshiroRandomSource(BasinField.mix(seed ^ 0x6A09E667F3BCC909L * (stream + 1))
                ^ BasinField.mix(key * 0x9E3779B97F4A7C15L));
    }
}
