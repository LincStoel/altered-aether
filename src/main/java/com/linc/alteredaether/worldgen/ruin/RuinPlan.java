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
package com.linc.alteredaether.worldgen.ruin;

import com.linc.alteredaether.worldgen.grove.shape.Bounds;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.loot.LootTable;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A ruin as a finished plan: every block it places, each at a height over the ground of an anchor column. A wall's
 * stones each stand on their own column, so the wall follows the ground; a standing stone or a head is anchored at
 * its own centre, so it stays whole on a slope. The plan is pure, so any span of columns can build its own share of
 * it, as the chunks of a landmark do.
 */
public final class RuinPlan {
    /**
     * One block of the plan.
     *
     * @param dy     height over the anchor's ground: 0 is the ground block itself, and below it the ruin may replace
     *               earth and stone; above it, only air or plants
     * @param ax     the column whose ground the block stands on
     * @param loot   a container's loot table, if it is one
     */
    public record Piece(int x, int dy, int z, int ax, int az, BlockState state,
                        @Nullable ResourceKey<LootTable> loot) {
    }

    /** The ground at a column: its top solid block, or MIN_VALUE where there is none to build on. */
    @FunctionalInterface
    public interface Ground {
        int at(int x, int z);
    }

    private final List<Piece> pieces;
    private final Bounds bounds;
    private final long salt;
    private final String kind;

    RuinPlan(String kind, List<Piece> pieces, long salt) {
        this.kind = kind;
        this.pieces = List.copyOf(pieces);
        this.salt = salt;
        Bounds b = Bounds.EMPTY;
        for (Piece piece : pieces) {
            b = b.union(new Bounds(piece.x(), piece.dy(), piece.z(), piece.x(), piece.dy(), piece.z()));
        }
        this.bounds = b;
    }

    public String kind() {
        return kind;
    }

    /** The plan's columns, and its heights over the ground. */
    public Bounds bounds() {
        return bounds;
    }

    public List<Piece> pieces() {
        return pieces;
    }

    /** The anchor columns of the plan's pieces, once each. */
    public List<long[]> anchors() {
        List<long[]> out = new ArrayList<>();
        java.util.Set<Long> seen = new java.util.HashSet<>();
        for (Piece piece : pieces) {
            if (seen.add(ChunkPos.asLong(piece.ax(), piece.az()))) {
                out.add(new long[]{piece.ax(), piece.az()});
            }
        }
        return out;
    }

    /** Builds the plan's share of a block rectangle. */
    public void place(WorldGenLevel level, int minX, int minZ, int maxX, int maxZ, Ground ground) {
        place(level, minX, minZ, maxX, maxZ, ground, false);
    }

    /**
     * Builds the plan's share of a block rectangle; {@code drowned}, it stands in water, taking the water's place,
     * and whatever of it can hold water is waterlogged.
     */
    public void place(WorldGenLevel level, int minX, int minZ, int maxX, int maxZ, Ground ground, boolean drowned) {
        Long2IntOpenHashMap grounds = new Long2IntOpenHashMap();
        grounds.defaultReturnValue(Integer.MAX_VALUE);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (Piece piece : pieces) {
            if (piece.x() < minX || piece.x() > maxX || piece.z() < minZ || piece.z() > maxZ) {
                continue;
            }
            long key = ChunkPos.asLong(piece.ax(), piece.az());
            int g = grounds.get(key);
            if (g == Integer.MAX_VALUE) {
                g = ground.at(piece.ax(), piece.az());
                grounds.put(key, g);
            }
            if (g == Integer.MIN_VALUE) {
                continue;
            }
            pos.set(piece.x(), g + piece.dy(), piece.z());
            if (level.isOutsideBuildHeight(pos.getY())) {
                continue;
            }
            BlockState here = level.getBlockState(pos);
            boolean wet = !here.getFluidState().isEmpty();
            // Nothing breaks into the giants' wood, and above the ground only air and plants give way.
            if (here.is(BlockTags.LOGS) || wet && !drowned
                    || piece.dy() > 0 && !here.isAir() && !here.canBeReplaced()) {
                continue;
            }
            BlockState state = piece.state();
            if (wet && state.hasProperty(BlockStateProperties.WATERLOGGED)) {
                state = state.setValue(BlockStateProperties.WATERLOGGED, true);
            }
            level.setBlock(pos, state, 2);
            if (piece.loot() != null) {
                RandomizableContainer.setBlockEntityLootTable(level, RandomSource.create(salt ^ pos.asLong()), pos,
                        piece.loot());
            }
        }
    }

    /**
     * The ground as the world has it now: the top solid block, looking down through leaves, logs and plants, so a
     * ruin stands on the earth under a canopy or a root. MIN_VALUE over water or a void.
     */
    public static int groundOf(WorldGenLevel level, int x, int z) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        // A finished world keeps only the live heightmaps; worldgen, only the worldgen ones.
        Heightmap.Types surface = level instanceof net.minecraft.server.level.WorldGenRegion
                ? Heightmap.Types.WORLD_SURFACE_WG : Heightmap.Types.WORLD_SURFACE;
        int top = level.getHeight(surface, x, z) - 1;
        for (int y = top; y > Math.max(level.getMinBuildHeight(), top - 64); y--) {
            BlockState state = level.getBlockState(pos.set(x, y, z));
            if (!state.getFluidState().isEmpty()) {
                return Integer.MIN_VALUE;
            }
            if (state.isAir() || state.canBeReplaced() || state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS)) {
                continue;
            }
            return y;
        }
        return Integer.MIN_VALUE;
    }
}
