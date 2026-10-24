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
package com.linc.alteredaether.worldgen.surface;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.SurfaceRules;

/**
 * Where a block sits within the island it belongs to: its height above that island's underside, as a fraction of
 * the island's thickness in this column, between {@code min} and {@code max}, and at least {@code min_thickness}
 * blocks of island in the column. Absolute height can't express "near the top of this island" in a sky full of
 * islands at every altitude; this can. Snow caps the upper part of a crag, a band of darker rock rings a mesa's base.
 *
 * <p>The island is the run of solid blocks through this block's position, scanned once per column.
 */
public record IslandPosition(double min, double max, int minThickness) implements SurfaceRules.ConditionSource {
    public static final MapCodec<IslandPosition> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.doubleRange(0, 1).optionalFieldOf("min", 0.0).forGetter(IslandPosition::min),
            Codec.doubleRange(0, 1).optionalFieldOf("max", 1.0).forGetter(IslandPosition::max),
            Codec.intRange(1, 512).optionalFieldOf("min_thickness", 1).forGetter(IslandPosition::minThickness)
    ).apply(i, IslandPosition::new));
    public static final KeyDispatchDataCodec<IslandPosition> CODEC = KeyDispatchDataCodec.of(MAP_CODEC);

    @Override
    public SurfaceRules.Condition apply(SurfaceRules.Context context) {
        return new SurfaceRules.Condition() {
            private long column = Long.MIN_VALUE;
            /** Solid runs in this column as (bottom, top) pairs, lowest first. */
            private int[] runs = new int[0];

            @Override
            public boolean test() {
                ChunkAccess chunk = context.chunk;
                int x = context.blockX, y = context.blockY, z = context.blockZ;
                long key = BlockPos.asLong(x, 0, z);
                if (key != column) {
                    column = key;
                    runs = scan(chunk, x, z);
                }
                for (int i = 0; i < runs.length; i += 2) {
                    int bottom = runs[i], top = runs[i + 1];
                    if (y >= bottom && y <= top) {
                        int thickness = top - bottom + 1;
                        if (thickness < minThickness) {
                            return false;
                        }
                        double position = thickness == 1 ? 1 : (y - bottom) / (double) (thickness - 1);
                        return position >= min && position <= max;
                    }
                }
                return false;
            }
        };
    }

    private static int[] scan(ChunkAccess chunk, int x, int z) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int[] found = new int[16];
        int count = 0;
        int start = Integer.MIN_VALUE;
        for (int y = chunk.getMinBuildHeight(); y < chunk.getMaxBuildHeight(); y++) {
            boolean solid = !chunk.getBlockState(cursor.set(x, y, z)).isAir();
            if (solid && start == Integer.MIN_VALUE) {
                start = y;
            } else if (!solid && start != Integer.MIN_VALUE) {
                if (count + 2 > found.length) {
                    found = java.util.Arrays.copyOf(found, found.length * 2);
                }
                found[count++] = start;
                found[count++] = y - 1;
                start = Integer.MIN_VALUE;
            }
        }
        if (start != Integer.MIN_VALUE) {
            if (count + 2 > found.length) {
                found = java.util.Arrays.copyOf(found, found.length + 2);
            }
            found[count++] = start;
            found[count++] = chunk.getMaxBuildHeight() - 1;
        }
        return java.util.Arrays.copyOf(found, count);
    }

    @Override
    public KeyDispatchDataCodec<? extends SurfaceRules.ConditionSource> codec() {
        return CODEC;
    }
}
