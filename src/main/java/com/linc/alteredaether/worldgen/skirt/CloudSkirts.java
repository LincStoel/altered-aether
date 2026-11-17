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
    /** Blocks either way the clearance under the collar is averaged over. */
    private static final int CLEAR_BLUR = 4;

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
        int low = Integer.MAX_VALUE, high = Integer.MIN_VALUE;
        for (int i = 0; i < 256; i++) {
            int x = x0 + (i & 15), z = z0 + (i >> 4), at = ((i >> 4) + margin) * side + (i & 15) + margin;
            if (spans[at] == null || clear[at] <= 0 || !field.span(x, z, clear[at], out)) {
                continue;
            }
            own[i] = out.clone();
            low = Math.min(low, (int) Math.floor(out[0]) - 1);
            high = Math.max(high, (int) Math.ceil(out[1]) + 1);
        }
        if (high == Integer.MIN_VALUE) {
            return 0;
        }
        List<Fall> falls = scanFalls(level, x0, z0, low - 1, high + 8);
        BlockState cloud = cloud();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int placed = 0;
        for (int i = 0; i < 256; i++) {
            if (own[i] == null) {
                continue;
            }
            int x = x0 + (i & 15), z = z0 + (i >> 4), at = ((i >> 4) + margin) * side + (i & 15) + margin;
            double[] span = own[i];
            if (!falls.isEmpty()) {
                double gate = gapGate(field.seed(), falls, x, z, span[0]);
                if (gate < 1 && !field.span(x, z, gate * clear[at], span)) {
                    continue;
                }
            }
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
                    int keepFrom = keep ? SkirtGrid.lowest(cut[0], cut[1]) : 0;
                    int keepTo = keep ? keepFrom + SkirtGrid.layers(cut[0], cut[1]) : 0;
                    // The whole span and a block either side: the collar there may have been thinned by clearance.
                    int from = SkirtGrid.lowest(whole[0], whole[1]);
                    for (int y = from - 1; y <= from + SkirtGrid.layers(whole[0], whole[1]); y++) {
                        boolean inside = y >= keepFrom && y < keepTo;
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
