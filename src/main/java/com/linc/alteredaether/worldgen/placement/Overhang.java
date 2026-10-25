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
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;

import java.util.stream.Stream;

/**
 * Finds the underside of a ledge for hanging features. From the position it searches up through air, for at most
 * {@code search} blocks, to a block with a sturdy underside, and moves to the air block just beneath it. Positions with
 * no ledge above, or with fewer than {@code clearance} open blocks below the ledge, are dropped. Vanilla's
 * environment_scan plus an offset does the same job; this keeps the clearance check in one place.
 *
 * <p>Choose the starting height with a height_range before this, since a heightmap can't see under a ledge.
 */
public class Overhang extends PlacementModifier {
    public static final MapCodec<Overhang> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.intRange(1, 64).optionalFieldOf("search", 12).forGetter(o -> o.search),
            Codec.intRange(1, 32).optionalFieldOf("clearance", 3).forGetter(o -> o.clearance)
    ).apply(i, Overhang::new));

    private final int search;
    private final int clearance;

    public Overhang(int search, int clearance) {
        this.search = search;
        this.clearance = clearance;
    }

    @Override
    public Stream<BlockPos> getPositions(PlacementContext context, RandomSource random, BlockPos pos) {
        BlockPos.MutableBlockPos cursor = pos.mutable();
        if (!context.getBlockState(cursor).isAir()) {
            return Stream.empty();
        }
        for (int step = 0; step < search && cursor.getY() < context.level.getMaxBuildHeight() - 1; step++) {
            BlockPos above = cursor.above();
            BlockState state = context.getBlockState(above);
            if (state.isFaceSturdy(context.level, above, Direction.DOWN)) {
                return open(context, cursor.immutable()) >= clearance ? Stream.of(cursor.immutable()) : Stream.empty();
            }
            if (!state.isAir()) {
                return Stream.empty();
            }
            cursor.move(Direction.UP);
        }
        return Stream.empty();
    }

    private static int open(PlacementContext context, BlockPos from) {
        int count = 0;
        BlockPos.MutableBlockPos cursor = from.mutable();
        while (count < 32 && cursor.getY() > context.getMinBuildHeight() && context.getBlockState(cursor).isAir()) {
            count++;
            cursor.move(Direction.DOWN);
        }
        return count;
    }

    @Override
    public PlacementModifierType<?> type() {
        return AARegistries.OVERHANG.get();
    }
}
