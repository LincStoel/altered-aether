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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Lays the cloud skirts ({@link SkirtField}) into the world, a chunk at a time, and keeps them clear of waterfalls.
 *
 * <p>A collar fills only air. Air walled in beside it (a dungeon room, a hollow) is left alone: a block under cover is
 * filled only when open air lies a few steps away at the same height.
 *
 * <p>Water that will pour over an edge leaves a gap of 1 to 3 blocks in the collar beneath it, the collar thinning to
 * a feather round the gap. Water already there when a chunk's collar is laid is seen then; a spring placed afterwards,
 * in a neighbouring chunk, calls {@link #clearFalls} to cut its gap into the collar already laid.
 */
public final class CloudSkirts {
    /** Steps sideways a covered block may look for open air. */
    private static final int OPEN_SEARCH = 8;
    /** Blocks above a block that must be clear for it to count as open. */
    private static final int OPEN_HEIGHT = 8;
    /** How far a spring's water may run over flat ground before it falls. */
    private static final int RUN = 6;
    /** How far round a chunk water is looked for: the widest gap plus its feather. */
    private static final int WATER_REACH = 7;

    /** A column water will pour down, from {@code y}. */
    public record Fall(int x, int y, int z) {
    }

    @Nullable
    public static SkirtField field(WorldGenLevel level) {
        if (!AACommonConfig.CLOUD_SKIRTS.get()) {
            return null;
        }
        return SkirtField.get(level.getLevel().dimension());
    }

    /** Lays the collar in one chunk's own columns. Returns how many blocks were placed. */
    public static int place(WorldGenLevel level, SkirtField field, int x0, int z0) {
        double[][] spans = new double[256][];
        double[] out = new double[2];
        int low = Integer.MAX_VALUE, high = Integer.MIN_VALUE;
        for (int i = 0; i < 256; i++) {
            if (field.span(x0 + (i & 15), z0 + (i >> 4), 1, out)) {
                spans[i] = out.clone();
                low = Math.min(low, (int) Math.floor(out[0]));
                high = Math.max(high, (int) Math.ceil(out[1]));
            }
        }
        if (high == Integer.MIN_VALUE) {
            return 0;
        }
        List<Fall> falls = scanFalls(level, x0, z0, low - 1, high + 8);
        BlockState cloud = cloud();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int placed = 0;
        for (int i = 0; i < 256; i++) {
            if (spans[i] == null) {
                continue;
            }
            int x = x0 + (i & 15), z = z0 + (i >> 4);
            double[] span = spans[i];
            if (!falls.isEmpty()) {
                double gate = gapGate(field.seed(), falls, x, z, span[0]);
                if (gate < 1 && !field.span(x, z, gate, span)) {
                    continue;
                }
            }
            for (int y = (int) Math.ceil(span[0] - 0.5); y <= (int) Math.floor(span[1] - 0.5); y++) {
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
     * Cuts the gap a fresh fluid block's waterfall needs into whatever collar is already laid round it, as far as the
     * feature placing it may write. Only collar cloud is taken away.
     */
    public static void clearFalls(WorldGenLevel level, BlockPos fluid) {
        SkirtField field = field(level);
        if (field == null) {
            return;
        }
        List<Fall> falls = new ArrayList<>();
        predict(level, fluid, falls);
        if (falls.isEmpty()) {
            return;
        }
        BlockState cloud = cloud();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        double[] whole = new double[2], cut = new double[2];
        int reach = 3 + (int) Math.ceil(SkirtGrid.FEATHER);
        Set<Long> done = new HashSet<>();
        for (Fall fall : falls) {
            for (int x = fall.x() - reach; x <= fall.x() + reach; x++) {
                for (int z = fall.z() - reach; z <= fall.z() + reach; z++) {
                    if (!done.add(SkirtGrid.key(x, z)) || !WriteBounds.reachable(level, x, z)
                            || !field.span(x, z, 1, whole)) {
                        continue;
                    }
                    double gate = gapGate(field.seed(), falls, x, z, whole[0]);
                    if (gate >= 1) {
                        continue;
                    }
                    boolean keep = field.span(x, z, gate, cut);
                    for (int y = (int) Math.ceil(whole[0] - 0.5); y <= (int) Math.floor(whole[1] - 0.5); y++) {
                        boolean inside = keep && y + 0.5 >= cut[0] && y + 0.5 <= cut[1];
                        if (!inside && level.getBlockState(pos.set(x, y, z)).is(cloud.getBlock())
                                && WriteBounds.writable(level, pos)) {
                            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                        }
                    }
                }
            }
        }
    }

    /** What survives of the collar in a column near waterfalls: 0 in a gap, rising through the feather to 1. */
    private static double gapGate(long seed, List<Fall> falls, int x, int z, double bottom) {
        double gate = 1;
        for (Fall fall : falls) {
            if (fall.y() < bottom) {
                continue;
            }
            double dx = x - fall.x(), dz = z - fall.z();
            gate = Math.min(gate, SkirtGrid.feather(Math.sqrt(dx * dx + dz * dz),
                    SkirtGrid.gapRadius(seed, fall.x(), fall.z())));
            if (gate <= 0) {
                return 0;
            }
        }
        return gate;
    }

    // ---------------------------------------------------------------------------------------------- waterfalls --

    /** Every waterfall from the fluids round a chunk, between two heights. */
    private static List<Fall> scanFalls(WorldGenLevel level, int x0, int z0, int minY, int maxY) {
        List<Fall> falls = new ArrayList<>();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        minY = Math.max(minY, level.getMinBuildHeight());
        maxY = Math.min(maxY, level.getMaxBuildHeight() - 1);
        for (int cx = (x0 - WATER_REACH) >> 4; cx <= (x0 + 15 + WATER_REACH) >> 4; cx++) {
            for (int cz = (z0 - WATER_REACH) >> 4; cz <= (z0 + 15 + WATER_REACH) >> 4; cz++) {
                if (!WriteBounds.reachable(level, cx << 4, cz << 4)) {
                    continue;
                }
                ChunkAccess chunk = level.getChunk(cx, cz);
                for (int sy = minY >> 4; sy <= maxY >> 4; sy++) {
                    int index = chunk.getSectionIndexFromSectionY(sy);
                    if (index < 0 || index >= chunk.getSectionsCount()) {
                        continue;
                    }
                    LevelChunkSection section = chunk.getSection(index);
                    if (section.hasOnlyAir() || !section.maybeHas(state -> !state.getFluidState().isEmpty())) {
                        continue;
                    }
                    int ax = Math.max(cx << 4, x0 - WATER_REACH), bx = Math.min((cx << 4) + 15, x0 + 15 + WATER_REACH);
                    int az = Math.max(cz << 4, z0 - WATER_REACH), bz = Math.min((cz << 4) + 15, z0 + 15 + WATER_REACH);
                    int ay = Math.max(sy << 4, minY), by = Math.min((sy << 4) + 15, maxY);
                    for (int x = ax; x <= bx; x++) {
                        for (int z = az; z <= bz; z++) {
                            for (int y = ay; y <= by; y++) {
                                if (!section.getFluidState(x & 15, y & 15, z & 15).isEmpty()) {
                                    predict(level, pos.set(x, y, z).immutable(), falls);
                                }
                            }
                        }
                    }
                }
            }
        }
        return falls;
    }

    /**
     * Where water at a block will pour down: straight below it if that is open, else off each open side, running over
     * flat ground for a few blocks until it finds a drop.
     */
    private static void predict(WorldGenLevel level, BlockPos fluid, List<Fall> falls) {
        if (passable(level, fluid.below())) {
            falls.add(new Fall(fluid.getX(), fluid.getY(), fluid.getZ()));
            return;
        }
        Set<Long> seen = new HashSet<>();
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos side = fluid.relative(direction);
            if (reachable(level, side) && level.getBlockState(side).isAir()) {
                queue.add(new int[]{side.getX(), side.getZ(), 1});
                seen.add(SkirtGrid.key(side.getX(), side.getZ()));
            }
        }
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int y = fluid.getY();
        while (!queue.isEmpty()) {
            int[] at = queue.poll();
            if (passable(level, pos.set(at[0], y - 1, at[1]))) {
                falls.add(new Fall(at[0], y, at[1]));
                continue;
            }
            if (at[2] >= RUN) {
                continue;
            }
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                int nx = at[0] + direction.getStepX(), nz = at[1] + direction.getStepZ();
                if (seen.add(SkirtGrid.key(nx, nz)) && reachable(level, pos.set(nx, y, nz))
                        && level.getBlockState(pos).isAir()) {
                    queue.add(new int[]{nx, nz, at[2] + 1});
                }
            }
        }
    }

    /** Whether water would fall through a block: air, or the cloud of a collar already laid. */
    private static boolean passable(WorldGenLevel level, BlockPos pos) {
        if (!reachable(level, pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        return state.isAir() || state.is(AetherBlocks.COLD_AERCLOUD.get());
    }

    private static boolean reachable(WorldGenLevel level, BlockPos pos) {
        return WriteBounds.reachable(level, pos.getX(), pos.getZ()) && !level.isOutsideBuildHeight(pos);
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
