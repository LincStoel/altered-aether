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
import com.linc.alteredaether.worldgen.grove.GroveField;
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
 * Keeps a position only where the grove region it lies in grows archwood of the given colour, so each stretch of
 * forest has archwood of one colour, in step with its wood.
 */
public class ArchwoodRegion extends PlacementModifier {
    public static final MapCodec<ArchwoodRegion> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.fieldOf("colour").forGetter(m -> m.colour)
    ).apply(i, ArchwoodRegion::new));

    private final String colour;

    public ArchwoodRegion(String colour) {
        this.colour = colour;
    }

    @Override
    public Stream<BlockPos> getPositions(PlacementContext context, RandomSource random, BlockPos pos) {
        GroveField field = GroveField.get(context.level.getLevel().dimension());
        return field != null && field.archwoodAt(pos.getX(), pos.getZ()).equals(colour) ? Stream.of(pos)
                : Stream.empty();
    }

    @Override
    public PlacementModifierType<?> type() {
        return AARegistries.ARCHWOOD_REGION.get();
    }
}
