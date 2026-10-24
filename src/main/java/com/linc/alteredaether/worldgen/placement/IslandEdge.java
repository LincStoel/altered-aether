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
package com.linc.alteredaether.worldgen.placement;

import com.linc.alteredaether.registry.AARegistries;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Moves a position to the nearest island edge: walking out from it in the four directions, in random order, for up
 * to {@code reach} blocks, the last column of ground before the ground drops away by at least {@code drop} blocks or
 * ends. The result is the top block of that edge column. Positions with no edge in reach are dropped.
 *
 * <p>Keep {@code reach} within a chunk's width, so the edge stays inside the area a feature may write to.
 */
public class IslandEdge extends PlacementModifier {
    public static final MapCodec<IslandEdge> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.intRange(1, 16).optionalFieldOf("reach", 12).forGetter(e -> e.reach),
            Codec.intRange(1, 256).optionalFieldOf("drop", 16).forGetter(e -> e.drop)
    ).apply(i, IslandEdge::new));

    private final int reach;
    private final int drop;

    public IslandEdge(int reach, int drop) {
        this.reach = reach;
        this.drop = drop;
    }

    @Override
    public Stream<BlockPos> getPositions(PlacementContext context, RandomSource random, BlockPos pos) {
        int top = context.getHeight(Heightmap.Types.WORLD_SURFACE_WG, pos.getX(), pos.getZ());
        if (top <= context.getMinBuildHeight()) {
            return Stream.empty();
        }
        List<Direction> directions = new ArrayList<>(Direction.Plane.HORIZONTAL.stream().toList());
        Util.shuffle(directions, random);
        for (Direction direction : directions) {
            int x = pos.getX(), z = pos.getZ(), height = top;
            for (int step = 1; step <= reach; step++) {
                int nx = x + direction.getStepX(), nz = z + direction.getStepZ();
                int next = context.getHeight(Heightmap.Types.WORLD_SURFACE_WG, nx, nz);
                if (next <= height - drop) {
                    return Stream.of(new BlockPos(x, height - 1, z));
                }
                x = nx;
                z = nz;
                height = next;
            }
        }
        return Stream.empty();
    }

    @Override
    public PlacementModifierType<?> type() {
        return AARegistries.ISLAND_EDGE.get();
    }
}
