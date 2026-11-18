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
import com.linc.alteredaether.worldgen.surface.LatticeNoise;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementFilter;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;

/**
 * Keeps a position only where a smooth 2D noise passes {@code threshold}, so scattered decoration comes in clumps with
 * bare ground between, after the Aether II's noise-driven counts. {@code scale} sets the clumps' size in blocks; the
 * threshold sets the share of the ground they cover (0.125 keeps about 40%). Give each kind of decoration its own
 * {@code salt} and its clumps lie in their own places.
 */
public final class NoiseClump extends PlacementFilter {
    public static final MapCodec<NoiseClump> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.doubleRange(4, 512).fieldOf("scale").forGetter(c -> c.scale),
            Codec.doubleRange(-1, 1).fieldOf("threshold").forGetter(c -> c.threshold),
            Codec.INT.optionalFieldOf("salt", 0).forGetter(c -> c.salt)
    ).apply(i, NoiseClump::new));

    private final double scale, threshold;
    private final int salt;

    public NoiseClump(double scale, double threshold, int salt) {
        this.scale = scale;
        this.threshold = threshold;
        this.salt = salt;
    }

    @Override
    protected boolean shouldPlace(PlacementContext context, RandomSource random, BlockPos pos) {
        // y = 0 holds the lattice to a single layer: a 2D noise
        return LatticeNoise.value(pos.getX() / scale, 0, pos.getZ() / scale, salt) > threshold;
    }

    @Override
    public PlacementModifierType<?> type() {
        return AARegistries.NOISE_CLUMP.get();
    }
}
