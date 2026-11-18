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
import com.linc.alteredaether.worldgen.TerrainProbe;
import com.linc.alteredaether.worldgen.basin.BasinField;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;

/**
 * The Aether Plateau's sea stacks: columns of rock standing alone in the open sky a little off the plateau's cliffs,
 * after the Aether II's ferrosite pillars, as if the terraces had broken away. Each stands in a column of empty sky
 * with plateau ground {@value #NEAR} to {@value #FAR} blocks off, its top about level with the nearest terrace, its
 * foot hanging below the plateau's underside, and it tapers from a broad top to a narrow root. Some hold a small pool
 * in a dish on top. Sites are on a jittered grid, read off the terrain before any cave, and kept clear of landmarks
 * and basins.
 */
public final class StackField {
    private static final int CELL = 56, ATTEMPTS = 5, NEAR = 10, FAR = 24;
    private static final double CHANCE = 0.65, POND_CHANCE = 0.3;
    private static final Map<ResourceKey<Level>, StackField> FIELDS = new ConcurrentHashMap<>();
    private static final String PLATEAU = "altered_aether:aether_plateau";

    /**
     * A stack: centre, its radius at the top, its top and bottom heights, and the dish of a pool on top (0 for none).
     */
    public record Stack(int x, int z, double radius, int top, int bottom, int pond, long salt) {
        /** How far in from the stack's surface a point lies, in blocks; negative outside. */
        public double inside(double px, int y, double pz) {
            if (y > top || y < bottom) {
                return -1;
            }
            double t = (y - bottom) / (double) Math.max(1, top - bottom);
            // broad near the top, rounding off over its last couple of blocks, narrowing to a root below
            double shoulder = Math.min(1, (top - y + 1) / 2.5);
            double r = radius * (0.3 + 0.7 * Math.pow(t, 0.55)) * (0.75 + 0.25 * shoulder)
                    + 0.8 * Math.sin(y * 0.7 + salt % 7) ;
            double dx = px - x, dz = pz - z;
            double d = Math.sqrt(dx * dx + dz * dz);
            if (pond > 0 && y > top - pond && d < radius - 2.5) {
                return -1;
            }
            return r - d;
        }

        public double extent() {
            return radius + 2;
        }
    }

    private final long seed;
    private final TerrainProbe probe;
    @Nullable
    private final LandmarkField landmarks;
    @Nullable
    private final BasinField basins;
    private final Cache<Long, Optional<Stack>> cells = CacheBuilder.newBuilder().maximumSize(1 << 14).build();

    public StackField(long seed, TerrainProbe probe, @Nullable LandmarkField landmarks, @Nullable BasinField basins) {
        this.seed = seed;
        this.probe = probe;
        this.landmarks = landmarks;
        this.basins = basins;
    }

    public static StackField get(ResourceKey<Level> level) {
        return FIELDS.get(level);
    }

    public static void put(ResourceKey<Level> level, @Nullable StackField field) {
        if (field == null) {
            FIELDS.remove(level);
        } else {
            FIELDS.put(level, field);
        }
    }

    /** Every stack reaching into the given block rectangle. */
    public List<Stack> touching(int minX, int minZ, int maxX, int maxZ) {
        List<Stack> found = new ArrayList<>(1);
        int reach = CELL / 2 + 12;
        for (int cx = Math.floorDiv(minX - reach, CELL); cx <= Math.floorDiv(maxX + reach, CELL); cx++) {
            for (int cz = Math.floorDiv(minZ - reach, CELL); cz <= Math.floorDiv(maxZ + reach, CELL); cz++) {
                stack(cx, cz).filter(s -> s.x() + s.extent() >= minX && s.x() - s.extent() <= maxX
                        && s.z() + s.extent() >= minZ && s.z() - s.extent() <= maxZ).ifPresent(found::add);
            }
        }
        return found;
    }

    private Optional<Stack> stack(int cx, int cz) {
        long key = ((long) cx << 32) ^ (cz & 0xFFFFFFFFL);
        try {
            return cells.get(key, () -> Optional.ofNullable(find(cx, cz)));
        } catch (ExecutionException e) {
            throw new IllegalStateException(e.getCause());
        }
    }

    @Nullable
    private Stack find(int cx, int cz) {
        WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(0));
        random.setLargeFeatureWithSalt(seed, cx, cz, 0x57AC5);
        if (random.nextDouble() >= CHANCE) {
            return null;
        }
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            int x = cx * CELL + random.nextInt(CELL), z = cz * CELL + random.nextInt(CELL);
            Stack stack = validate(x, z, random);
            if (stack != null) {
                return stack;
            }
        }
        return null;
    }

    /** A stack at (x, z), if the sky is open there and a plateau cliff stands near enough. */
    @Nullable
    private Stack validate(int x, int z, RandomSource random) {
        double radius = 4 + random.nextDouble() * 4;
        // open sky over the whole footprint
        for (int d = 0; d < 8; d++) {
            double a = d * Math.PI / 4;
            if (probe.surface(x + (int) Math.round(Math.cos(a) * (radius + 2)),
                    z + (int) Math.round(Math.sin(a) * (radius + 2))) != Integer.MIN_VALUE) {
                return null;
            }
        }
        if (probe.surface(x, z) != Integer.MIN_VALUE) {
            return null;
        }
        // the nearest plateau ground out to FAR: its top sets the stack's
        int nearest = Integer.MIN_VALUE, nx = 0, nz = 0;
        for (int r = NEAR; r <= FAR && nearest == Integer.MIN_VALUE; r += 4) {
            for (int d = 0; d < 16; d++) {
                double a = d * Math.PI / 8;
                int px = x + (int) Math.round(Math.cos(a) * r), pz = z + (int) Math.round(Math.sin(a) * r);
                int top = probe.surface(px, pz);
                if (top != Integer.MIN_VALUE && probe.biomeAt(px, pz).equals(PLATEAU)) {
                    nearest = top;
                    nx = px;
                    nz = pz;
                    break;
                }
            }
        }
        if (nearest == Integer.MIN_VALUE) {
            return null;
        }
        int under = lowest(nx, nz, nearest);
        int top = nearest - 5 + random.nextInt(8);
        int bottom = Math.min(under, top - 24) - 8 - random.nextInt(14);
        double extent = radius + 2;
        if (landmarks != null && !landmarks.touching((int) (x - extent - 8), (int) (z - extent - 8),
                (int) (x + extent + 8), (int) (z + extent + 8)).isEmpty()) {
            return null;
        }
        if (basins != null && !basins.sitesTouching((int) (x - extent), (int) (z - extent), (int) (x + extent),
                (int) (z + extent)).isEmpty()) {
            return null;
        }
        int pond = radius >= 5.5 && random.nextDouble() < POND_CHANCE ? 2 + random.nextInt(2) : 0;
        return new Stack(x, z, radius, top, Math.max(probe.minY() + 2, bottom), pond, random.nextLong());
    }

    /** The lowest solid block in a column under {@code top}. */
    private int lowest(int x, int z, int top) {
        int y = top;
        while (y > probe.minY() && probe.solid(x, y - 1, z)) {
            y -= 2;
        }
        return Mth.clamp(y, probe.minY(), top);
    }
}
