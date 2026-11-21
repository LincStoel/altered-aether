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
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementFilter;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;

/**
 * Keeps a position only at or above a height: after a heightmap, only where the ground stands that high, such as the
 * Highlands' islets. The anchor resolves like any other, so it is stretched onto the tall Aether with the rest.
 */
public class MinHeight extends PlacementFilter {
    public static final MapCodec<MinHeight> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            VerticalAnchor.CODEC.fieldOf("min_inclusive").forGetter(m -> m.min)
    ).apply(i, MinHeight::new));

    private final VerticalAnchor min;

    public MinHeight(VerticalAnchor min) {
        this.min = min;
    }

    @Override
    protected boolean shouldPlace(PlacementContext context, RandomSource random, BlockPos pos) {
        return pos.getY() >= min.resolveY(context);
    }

    @Override
    public PlacementModifierType<?> type() {
        return AARegistries.MIN_HEIGHT.get();
    }
}
