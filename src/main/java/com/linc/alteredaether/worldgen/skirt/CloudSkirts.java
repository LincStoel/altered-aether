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
package com.linc.alteredaether.worldgen.skirt;

import com.aetherteam.aether.block.AetherBlockStateProperties;
import com.aetherteam.aether.block.AetherBlocks;
import com.linc.alteredaether.config.AACommonConfig;
import com.linc.alteredaether.worldgen.feature.WriteBounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/**
 * Lays the cloud skirts ({@link SkirtField}) into the world, a chunk at a time.
 *
 * <p>A collar fills only air. Air walled in beside it (a dungeon room, a hollow) is left alone: a block under cover is
 * filled only when open air lies a few steps away at the same height.
 *
 * <p>Waterfalls need nothing from us: aercloud is no solid block to water, so a fall washes its own channel down
 * through any collar beneath it.
 */
public final class CloudSkirts {
    /** Steps sideways a covered block may look for open air. */
    private static final int OPEN_SEARCH = 8;
    /** Blocks above a block that must be clear for it to count as open. */
    private static final int OPEN_HEIGHT = 8;
    /** Blocks either way the clearance under the collar is averaged over. */
    private static final int CLEAR_BLUR = 4;

    @Nullable
    public static SkirtField field(WorldGenLevel level) {
        if (!AACommonConfig.CLOUD_SKIRTS.get()) {
            return null;
        }
        return SkirtField.get(level.getLevel().dimension());
    }

    /**
     * Lays the collar in one chunk's own columns. Returns how many blocks were placed.
     *
     * <p>Each column is filled as {@link SkirtGrid#layers}, thinned by the open air under it: the air below each
     * column round the chunk is measured ({@link SkirtGrid#clearance}), taken as the least within
     * {@link SkirtGrid#CLEAR_MIN} blocks so a flank fanning out under the collar is felt from beside it too, then
     * averaged over {@link #CLEAR_BLUR} blocks either way so the collar thins smoothly rather than in steps.
     */
    public static int place(WorldGenLevel level, SkirtField field, int x0, int z0) {
        int margin = SkirtGrid.CLEAR_MIN + CLEAR_BLUR, side = 16 + 2 * margin;
        double[][] spans = new double[side * side][];
        double[] out = new double[2];
        boolean any = false;
        for (int i = 0; i < side * side; i++) {
            int x = x0 - margin + i % side, z = z0 - margin + i / side;
            if (field.span(x, z, 1, out)) {
                spans[i] = out.clone();
                any |= x >= x0 && x < x0 + 16 && z >= z0 && z < z0 + 16;
            }
        }
        if (!any) {
            return 0;
        }
        double[] clear = clearance(level, spans, x0 - margin, z0 - margin, side);
        double[][] own = new double[256][];
        boolean collar = false;
        for (int i = 0; i < 256; i++) {
            int x = x0 + (i & 15), z = z0 + (i >> 4), at = ((i >> 4) + margin) * side + (i & 15) + margin;
            if (spans[at] == null || clear[at] <= 0 || !field.span(x, z, clear[at], out)) {
                continue;
            }
            own[i] = out.clone();
            collar = true;
        }
        if (!collar) {
            return 0;
        }
        BlockState cloud = cloud();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int placed = 0;
        for (int i = 0; i < 256; i++) {
            if (own[i] == null) {
                continue;
            }
            int x = x0 + (i & 15), z = z0 + (i >> 4);
            double[] span = own[i];
            int count = SkirtGrid.layers(span[0], span[1]), lowest = SkirtGrid.lowest(span[0], span[1]);
            if (count == 0 || airBelow(level, pos, x, lowest, z, SkirtGrid.CLEAR_MIN) < SkirtGrid.CLEAR_MIN) {
                continue;
            }
            for (int y = lowest; y < lowest + count; y++) {
                pos.set(x, y, z);
                if (level.isOutsideBuildHeight(y) || !level.getBlockState(pos).isAir()
                        || !open(level, pos) && !opensNearby(level, pos)) {
                    continue;
                }
                level.setBlock(pos, cloud, Block.UPDATE_CLIENTS);
                placed++;
            }
        }
        return placed;
    }

    /**
     * What survives of the collar at each column of a square for the open air under it: all of it with
     * {@link SkirtGrid#CLEAR} blocks of air under the whole collar, less as land comes nearer. Columns with no collar,
     * or out of reach, don't hold it back.
     */
    private static double[] clearance(WorldGenLevel level, double[][] spans, int ax, int az, int side) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        double[] raw = new double[side * side];
        for (int i = 0; i < side * side; i++) {
            int x = ax + i % side, z = az + i / side;
            if (spans[i] == null || !WriteBounds.reachable(level, x, z)) {
                raw[i] = 1;
                continue;
            }
            raw[i] = SkirtGrid.clearance(airBelow(level, pos, x, SkirtGrid.lowest(spans[i][0], spans[i][1]), z,
                    SkirtGrid.CLEAR));
        }
        int near = SkirtGrid.CLEAR_MIN;
        double[] least = new double[side * side];
        for (int i = 0; i < side * side; i++) {
            int x = i % side, z = i / side;
            double value = 1;
            for (int dz = -near; dz <= near; dz++) {
                for (int dx = -near; dx <= near; dx++) {
                    int nx = x + dx, nz = z + dz;
                    if (nx >= 0 && nz >= 0 && nx < side && nz < side && dx * dx + dz * dz <= near * near) {
                        value = Math.min(value, raw[nz * side + nx]);
                    }
                }
            }
            least[i] = value;
        }
        double[] mean = new double[side * side];
        for (int i = 0; i < side * side; i++) {
            int x = i % side, z = i / side;
            double sum = 0;
            int count = 0;
            for (int dz = -CLEAR_BLUR; dz <= CLEAR_BLUR; dz++) {
                for (int dx = -CLEAR_BLUR; dx <= CLEAR_BLUR; dx++) {
                    int nx = x + dx, nz = z + dz;
                    if (nx >= 0 && nz >= 0 && nx < side && nz < side) {
                        sum += least[nz * side + nx];
                        count++;
                    }
                }
            }
            // A column with land close under it keeps only a little, whatever its neighbours have.
            mean[i] = Math.min(sum / count, raw[i] + 0.35);
        }
        return mean;
    }

    /** Blocks of open air under a block, counting down to {@code limit}; cold aercloud counts as air. */
    private static int airBelow(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int y, int z, int limit) {
        for (int d = 1; d <= limit; d++) {
            pos.set(x, y - d, z);
            if (level.isOutsideBuildHeight(pos)) {
                return limit;
            }
            BlockState state = level.getBlockState(pos);
            if (!state.isAir() && !state.is(AetherBlocks.COLD_AERCLOUD.get())) {
                return d - 1;
            }
        }
        return limit;
    }

    // ------------------------------------------------------------------------------------------------- cover --

    /** Whether a block has open sky above it for a few blocks: out from under the island. */
    private static boolean open(WorldGenLevel level, BlockPos pos) {
        BlockPos.MutableBlockPos up = new BlockPos.MutableBlockPos();
        for (int dy = 1; dy <= OPEN_HEIGHT; dy++) {
            up.set(pos.getX(), pos.getY() + dy, pos.getZ());
            if (level.isOutsideBuildHeight(up)) {
                return true;
            }
            BlockState state = level.getBlockState(up);
            if (!state.isAir() && !state.is(AetherBlocks.COLD_AERCLOUD.get())) {
                return false;
            }
        }
        return true;
    }

    /** Whether open air lies a few steps away through air at the same height, as under an overhang. */
    private static boolean opensNearby(WorldGenLevel level, BlockPos start) {
        Set<Long> seen = new HashSet<>();
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        queue.add(new int[]{start.getX(), start.getZ(), 0});
        seen.add(SkirtGrid.key(start.getX(), start.getZ()));
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int y = start.getY();
        while (!queue.isEmpty()) {
            int[] at = queue.poll();
            if (at[2] >= OPEN_SEARCH) {
                continue;
            }
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                int nx = at[0] + direction.getStepX(), nz = at[1] + direction.getStepZ();
                if (!seen.add(SkirtGrid.key(nx, nz)) || !WriteBounds.reachable(level, nx, nz)) {
                    continue;
                }
                BlockState state = level.getBlockState(pos.set(nx, y, nz));
                if (!state.isAir() && !state.is(AetherBlocks.COLD_AERCLOUD.get())) {
                    continue;
                }
                if (open(level, pos)) {
                    return true;
                }
                queue.add(new int[]{nx, nz, at[2] + 1});
            }
        }
        return false;
    }

    /** Cold aercloud as the Aether's own world generation places it, with double drops. */
    private static BlockState cloud() {
        return AetherBlocks.COLD_AERCLOUD.get().defaultBlockState()
                .setValue(AetherBlockStateProperties.DOUBLE_DROPS, true);
    }

    private CloudSkirts() {
    }
}
