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
package com.linc.alteredaether.worldgen.density;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.linc.alteredaether.worldgen.TerrainProbe;
import com.linc.alteredaether.worldgen.region.BiomeGroups;
import it.unimi.dsi.fastutil.HashCommon;
import net.minecraft.core.QuartPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.resources.ResourceKey;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;

/**
 * One world's biome map, read without generating it, and the distances from each column to the nearest biome of
 * another class ({@link BiomeEdge}). Biomes are looked up once per quart column and kept; distances are solved on an
 * 8-block grid and interpolated in between, like {@link RegionMask}.
 */
public final class BiomeEdgeField {
    /** Distances are solved on this block grid. */
    private static final int CELL = 8;

    private static final Map<ResourceKey<Level>, BiomeEdgeField> FIELDS = new ConcurrentHashMap<>();

    private final TerrainProbe probe;
    private final Cache<Long, String> biomes = CacheBuilder.newBuilder().maximumSize(1 << 18).build();
    private final Map<String, Solver> solvers = new ConcurrentHashMap<>();

    public BiomeEdgeField(TerrainProbe probe) {
        this.probe = probe;
    }

    public static BiomeEdgeField get(ResourceKey<Level> level) {
        return FIELDS.get(level);
    }

    public static void put(ResourceKey<Level> level, BiomeEdgeField field) {
        if (field == null) {
            FIELDS.remove(level);
        } else {
            FIELDS.put(level, field);
        }
    }

    /** The solver for one classifier and reach, shared by every function that reads them. */
    public Solver solver(BiomeGroups classifier, int reach) {
        return solvers.computeIfAbsent(classifier.id() + ":" + reach, key -> new Solver(classifier, reach));
    }

    /** The biome at a column, as its id; looked up once per quart column. */
    public String biomeAt(int x, int z) {
        int qx = QuartPos.fromBlock(x), qz = QuartPos.fromBlock(z);
        long key = ((long) qx << 32) ^ (qz & 0xFFFFFFFFL);
        try {
            return biomes.get(HashCommon.mix(key), () -> probe.biomeAt(QuartPos.toBlock(qx), QuartPos.toBlock(qz)));
        } catch (ExecutionException e) {
            throw new IllegalStateException(e.getCause());
        }
    }

    /** Distances for one classifier, up to {@code reach} blocks. */
    public final class Solver {
        private final BiomeGroups classifier;
        private final int reach;
        private final Cache<Long, long[]> cells = CacheBuilder.newBuilder().maximumSize(1 << 16).build();

        private Solver(BiomeGroups classifier, int reach) {
            this.classifier = classifier;
            this.reach = reach;
        }

        public int classAt(int x, int z) {
            return classifier.classify(biomeAt(x, z));
        }

        /** Blocks from (x, z) to the nearest column of another class, up to the reach. */
        public double distance(int x, int z) {
            int cx = Math.floorDiv(x, CELL), cz = Math.floorDiv(z, CELL);
            double fx = (x - cx * CELL) / (double) CELL, fz = (z - cz * CELL) / (double) CELL;
            return Mth.lerp2(fx, fz, distance(cell(cx, cz)), distance(cell(cx + 1, cz)), distance(cell(cx, cz + 1)),
                    distance(cell(cx + 1, cz + 1)));
        }

        /**
         * Blocks from (x, z) in to the edge of class {@code inside}, and 0 outside it. The grid corners carry the
         * distance signed by their class, so the edge falls smoothly between corners of different classes.
         */
        public double inside(int x, int z, int inside) {
            int cx = Math.floorDiv(x, CELL), cz = Math.floorDiv(z, CELL);
            double fx = (x - cx * CELL) / (double) CELL, fz = (z - cz * CELL) / (double) CELL;
            return Math.max(0, Mth.lerp2(fx, fz, signed(cell(cx, cz), inside), signed(cell(cx + 1, cz), inside),
                    signed(cell(cx, cz + 1), inside), signed(cell(cx + 1, cz + 1), inside)));
        }

        /** Blocks in to the edge of class {@code inside}, positive inside it and negative outside. */
        public double signed(int x, int z, int inside) {
            int cx = Math.floorDiv(x, CELL), cz = Math.floorDiv(z, CELL);
            double fx = (x - cx * CELL) / (double) CELL, fz = (z - cz * CELL) / (double) CELL;
            return Mth.lerp2(fx, fz, signed(cell(cx, cz), inside), signed(cell(cx + 1, cz), inside),
                    signed(cell(cx, cz + 1), inside), signed(cell(cx + 1, cz + 1), inside));
        }

        private static double distance(long[] cell) {
            return Float.intBitsToFloat((int) cell[0]);
        }

        private static double signed(long[] cell, int inside) {
            double d = distance(cell);
            return cell[1] == inside ? d : -d;
        }

        private long[] cell(int cx, int cz) {
            long key = ((long) cx << 32) ^ (cz & 0xFFFFFFFFL);
            try {
                return cells.get(HashCommon.mix(key), () -> {
                    int x = cx * CELL, z = cz * CELL;
                    float d = (float) EdgeSearch.distance(x, z, reach, this::classAt);
                    return new long[]{Float.floatToIntBits(d), classAt(x, z)};
                });
            } catch (ExecutionException e) {
                throw new IllegalStateException(e.getCause());
            }
        }
    }
}
