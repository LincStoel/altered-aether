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
package com.linc.alteredaether.worldgen.surface;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.SurfaceRules;

import java.util.List;

/**
 * Horizontal rock bands, like the strata of a badlands cliff but in any blocks. The pattern repeats every
 * {@code bands.size() * band_height} blocks; the bands undulate gently across the landscape so they read as
 * sedimentary layers rather than a ruler-straight stripe.
 */
public record Strata(List<BlockState> bands, int bandHeight, double undulation) implements SurfaceRules.RuleSource {
    public static final MapCodec<Strata> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            BlockState.CODEC.listOf(1, 32).fieldOf("bands").forGetter(Strata::bands),
            Codec.intRange(1, 16).optionalFieldOf("band_height", 3).forGetter(Strata::bandHeight),
            Codec.doubleRange(0, 16).optionalFieldOf("undulation", 2.5).forGetter(Strata::undulation)
    ).apply(i, Strata::new));
    public static final KeyDispatchDataCodec<Strata> CODEC = KeyDispatchDataCodec.of(MAP_CODEC);

    @Override
    public SurfaceRules.SurfaceRule apply(SurfaceRules.Context context) {
        return (x, y, z) -> {
            // Two crossed sine waves of incommensurate periods: smooth and never visibly periodic.
            double wobble = undulation * (Mth.sin(x * 0.031f + z * 0.017f) + 0.6 * Mth.sin(z * 0.043f - x * 0.011f));
            int band = Math.floorDiv(Mth.floor(y + wobble), bandHeight);
            return bands.get(Math.floorMod(band, bands.size()));
        };
    }

    @Override
    public KeyDispatchDataCodec<? extends SurfaceRules.RuleSource> codec() {
        return CODEC;
    }
}
