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
 * True where the surface is steep: the ground {@code distance} blocks away, in any of the four directions, lies at
 * least {@code rise} blocks higher or lower, or falls away entirely at an island's edge. Vanilla's {@code steep}
 * only looks north and east, so south- and west-facing cliffs keep their grass; this sees all four, so every cliff
 * shows bare rock.
 *
 * <p>Only the chunk being surfaced is available, so near its border a direction that would leave the chunk is
 * measured twice as far in the opposite direction instead.
 */
public record Steep(int distance, int rise) implements SurfaceRules.ConditionSource {
    public static final MapCodec<Steep> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.intRange(1, 7).optionalFieldOf("distance", 2).forGetter(Steep::distance),
            Codec.intRange(1, 32).optionalFieldOf("rise", 3).forGetter(Steep::rise)
    ).apply(i, Steep::new));
    public static final KeyDispatchDataCodec<Steep> CODEC = KeyDispatchDataCodec.of(MAP_CODEC);

    /** How far up or down a neighbouring column is searched for its surface. */
    private static final int SEARCH = 12;

    @Override
    public SurfaceRules.Condition apply(SurfaceRules.Context context) {
        return () -> {
            ChunkAccess chunk = context.chunk;
            int x = context.blockX, y = context.blockY, z = context.blockZ;
            int minX = chunk.getPos().getMinBlockX(), maxX = chunk.getPos().getMaxBlockX();
            int minZ = chunk.getPos().getMinBlockZ(), maxZ = chunk.getPos().getMaxBlockZ();
            BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
            return steepAlong(chunk, cursor, x, y, z, 1, 0, minX, maxX)
                    || steepAlong(chunk, cursor, x, y, z, 0, 1, minZ, maxZ);
        };
    }

    /** Both directions along one axis, staying inside the chunk. */
    private boolean steepAlong(ChunkAccess chunk, BlockPos.MutableBlockPos cursor, int x, int y, int z, int dx, int dz,
                               int min, int max) {
        int here = dx != 0 ? x : z;
        boolean forward = here + distance <= max, back = here - distance >= min;
        if (forward && back) {
            return steepAt(chunk, cursor, x + dx * distance, y, z + dz * distance)
                    || steepAt(chunk, cursor, x - dx * distance, y, z - dz * distance);
        }
        int reach = distance * 2;
        int sign = forward ? 1 : -1;
        int to = here + sign * reach;
        if (to < min || to > max) {
            return false;
        }
        return steepAt(chunk, cursor, x + dx * sign * reach, y, z + dz * sign * reach);
    }

    /** Whether the ground in column (x, z) lies {@code rise} or more blocks from {@code y}, or is missing. */
    private boolean steepAt(ChunkAccess chunk, BlockPos.MutableBlockPos cursor, int x, int y, int z) {
        int top = Math.min(chunk.getMaxBuildHeight() - 2, y + SEARCH);
        int bottom = Math.max(chunk.getMinBuildHeight(), y - SEARCH);
        for (int ny = top; ny >= bottom; ny--) {
            if (solid(chunk, cursor.set(x, ny, z)) && !solid(chunk, cursor.set(x, ny + 1, z))) {
                return Math.abs(ny - y) >= rise;
            }
        }
        // No ground anywhere near this height: the island ends here, a cliff by any measure.
        return true;
    }

    private static boolean solid(ChunkAccess chunk, BlockPos pos) {
        return !chunk.getBlockState(pos).isAir() && chunk.getFluidState(pos).isEmpty();
    }

    @Override
    public KeyDispatchDataCodec<? extends SurfaceRules.ConditionSource> codec() {
        return CODEC;
    }
}
