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
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;

import java.util.stream.Stream;

/**
 * Keeps only positions in the open gap between land masses: in air, with nothing solid for {@code clearance} blocks
 * straight down (so not resting on or hanging just over land), and with a wall of terrain within {@code reach} blocks
 * level with it in at least {@code sides} of eight compass directions. Choose the height with a height_range first;
 * the result is debris hanging between the islands it broke from rather than lying on top of them.
 */
public class RiftGap extends PlacementModifier {
    public static final MapCodec<RiftGap> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.intRange(1, 16).optionalFieldOf("reach", 14).forGetter(o -> o.reach),
            Codec.intRange(0, 64).optionalFieldOf("clearance", 10).forGetter(o -> o.clearance),
            Codec.intRange(1, 8).optionalFieldOf("sides", 2).forGetter(o -> o.sides)
    ).apply(i, RiftGap::new));

    private static final int[][] COMPASS = {{1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}, {0, -1}, {1, -1}};

    private final int reach;
    private final int clearance;
    private final int sides;

    public RiftGap(int reach, int clearance, int sides) {
        this.reach = reach;
        this.clearance = clearance;
        this.sides = sides;
    }

    @Override
    public Stream<BlockPos> getPositions(PlacementContext context, RandomSource random, BlockPos pos) {
        BlockPos.MutableBlockPos cursor = pos.mutable();
        if (!context.getBlockState(cursor).isAir()) {
            return Stream.empty();
        }
        for (int i = 1; i <= clearance && cursor.getY() > context.getMinBuildHeight(); i++) {
            if (!context.getBlockState(cursor.move(0, -1, 0)).isAir()) {
                return Stream.empty();
            }
        }
        int walls = 0;
        for (int[] direction : COMPASS) {
            // Diagonal steps cover more ground, so they stop sooner.
            int steps = direction[0] != 0 && direction[1] != 0 ? reach * 7 / 10 : reach;
            for (int step = 1; step <= steps; step++) {
                cursor.set(pos.getX() + direction[0] * step, pos.getY(), pos.getZ() + direction[1] * step);
                if (!context.getBlockState(cursor).isAir()) {
                    walls++;
                    break;
                }
            }
            if (walls >= sides) {
                return Stream.of(pos);
            }
        }
        return Stream.empty();
    }

    @Override
    public PlacementModifierType<?> type() {
        return AARegistries.RIFT_GAP.get();
    }
}
