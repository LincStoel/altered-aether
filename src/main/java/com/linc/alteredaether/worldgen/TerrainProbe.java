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
package com.linc.alteredaether.worldgen;

import net.minecraft.core.QuartPos;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import org.jetbrains.annotations.Nullable;

/**
 * Reads one world's terrain without generating it: the biome at a column, and whether a block is solid in a density
 * function sampled exactly, without interpolation. The answers depend only on the seed, so every chunk that asks gets
 * the same answer, which is what lets basins and landmarks span chunks with no seams.
 *
 * @param terrain the density to read, or null when the world has none we understand (then nothing is solid)
 * @param region  the region mask, 1 inside Altered Aether's own region, or null when unknown
 * @param minY    bottom of the noise band
 * @param maxY    top of the noise band, exclusive
 * @param biomeIds the biome id at each quart column already looked up
 */
public record TerrainProbe(BiomeSource biomes, Climate.Sampler sampler, @Nullable DensityFunction terrain,
                           @Nullable DensityFunction region, int minY, int maxY, CellCache<String> biomeIds) {
    /** Coarse step of the column surface search. */
    private static final int SCAN_STEP = 6;

    public TerrainProbe(BiomeSource biomes, Climate.Sampler sampler, @Nullable DensityFunction terrain,
                        @Nullable DensityFunction region, int minY, int maxY) {
        this(biomes, sampler, terrain, region, minY, maxY, new CellCache<>(1 << 16));
    }

    public TerrainProbe {
        terrain = columnMemo(terrain);
        region = columnMemo(region);
    }

    /**
     * {@code function} with each of its 2D caches ({@code flat_cache}, {@code cache_2d}) remembered, per thread, for
     * the last column asked. Sampled one point at a time those markers cache nothing, so a probe scanning down a
     * column worked out every 2D noise again at each height. The cached parts depend on x and z alone (a NoiseChunk
     * samples them once per column too), so the answers are the same.
     */
    @Nullable
    public static DensityFunction columnMemo(@Nullable DensityFunction function) {
        if (function == null) {
            return null;
        }
        java.util.Map<DensityFunction, DensityFunction> memos = new java.util.HashMap<>();
        return function.mapAll(new DensityFunction.Visitor() {
            @Override
            public DensityFunction apply(DensityFunction f) {
                if (f instanceof DensityFunctions.Marker marker && (marker.type() == DensityFunctions.Marker.Type.FlatCache
                        || marker.type() == DensityFunctions.Marker.Type.Cache2D)) {
                    return memos.computeIfAbsent(marker.wrapped(), ColumnMemo::new);
                }
                return f;
            }
        });
    }

    /** A 2D function's value at the last column each thread asked about. */
    private static final class ColumnMemo implements DensityFunction {
        private static final class Last {
            int x = Integer.MIN_VALUE, z = Integer.MIN_VALUE;
            double value;
        }

        private final DensityFunction wrapped;
        private final ThreadLocal<Last> last = ThreadLocal.withInitial(Last::new);

        ColumnMemo(DensityFunction wrapped) {
            this.wrapped = wrapped;
        }

        @Override
        public double compute(FunctionContext context) {
            Last memo = last.get();
            int x = context.blockX(), z = context.blockZ();
            if (memo.x != x || memo.z != z) {
                memo.value = wrapped.compute(context);
                memo.x = x;
                memo.z = z;
            }
            return memo.value;
        }

        @Override
        public void fillArray(double[] array, ContextProvider provider) {
            provider.fillAllDirectly(array, this);
        }

        @Override
        public DensityFunction mapAll(Visitor visitor) {
            return visitor.apply(new ColumnMemo(wrapped.mapAll(visitor)));
        }

        @Override
        public double minValue() {
            return wrapped.minValue();
        }

        @Override
        public double maxValue() {
            return wrapped.maxValue();
        }

        @Override
        public net.minecraft.util.KeyDispatchDataCodec<? extends DensityFunction> codec() {
            return wrapped.codec();
        }
    }

    /**
     * The biome id at a column, read at the middle of the band. Biomes are kept per quart (4 blocks), so the answer is
     * the same for every column in one, and each is looked up once: site searches and the skirts' rift fade ask for
     * thousands of columns, and a lookup samples the climate afresh. The ids are interned, so the cache holds one
     * string per biome.
     */
    public String biomeAt(int x, int z) {
        int qx = QuartPos.fromBlock(x), qz = QuartPos.fromBlock(z);
        return biomeIds.get(((long) qx << 32) ^ (qz & 0xFFFFFFFFL), key -> biomes.getNoiseBiome(qx,
                        QuartPos.fromBlock((minY + maxY) / 2), qz, sampler)
                .unwrapKey().map(id -> id.location().toString().intern()).orElse(""));
    }

    public boolean solid(int x, int y, int z) {
        return terrain != null && terrain.compute(new DensityFunction.SinglePointContext(x, y, z)) > 0;
    }

    /** The region mask at a column; 1 when there is no mask to read. */
    public double regionWeight(int x, int z) {
        return region == null ? 1 : region.compute(new DensityFunction.SinglePointContext(x, 0, z));
    }

    /** The highest solid block in the column across the whole band, or MIN_VALUE if there is none. */
    public int surface(int x, int z) {
        return surface(x, z, maxY - 1, minY);
    }

    /**
     * The highest solid block in a column between {@code from} and {@code to}, or MIN_VALUE if there is none. Steps
     * down coarsely, then bisects back up to the exact top; a ledge thinner than a step can be missed, which only
     * costs a candidate.
     */
    public int surface(int x, int z, int from, int to) {
        if (solid(x, from, z)) {
            return from;
        }
        for (int y = from - SCAN_STEP; y >= to - SCAN_STEP + 1; y -= SCAN_STEP) {
            int probe = Math.max(y, to);
            if (solid(x, probe, z)) {
                // Solid at probe, open at probe + step (or above): bisect for the boundary.
                int solidY = probe, openY = Math.min(from, probe + SCAN_STEP);
                while (openY - solidY > 1) {
                    int mid = (solidY + openY) >>> 1;
                    if (solid(x, mid, z)) {
                        solidY = mid;
                    } else {
                        openY = mid;
                    }
                }
                return solidY;
            }
        }
        return Integer.MIN_VALUE;
    }

    /**
     * The top of the ground in a column, searched outward from {@code guess}: up while solid, or down until solid.
     * Returns {@code high} if the ground reaches it and MIN_VALUE if there is none down to {@code low}.
     */
    public int surfaceNear(int x, int z, int guess, int low, int high) {
        if (solid(x, guess, z)) {
            int y = guess;
            while (y < high && solid(x, y + 1, z)) {
                y++;
            }
            return y;
        }
        for (int y = guess - 1; y >= low; y--) {
            if (solid(x, y, z)) {
                return y;
            }
        }
        return Integer.MIN_VALUE;
    }
}
