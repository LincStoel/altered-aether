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
import net.minecraft.world.level.levelgen.SurfaceRules;

/**
 * Patches of another block through the rock, after the Aether II's noise palettes: where a smooth 3D noise passes
 * {@code threshold}, {@code then_run} decides the block (typically a block, or a substitute_block that falls back
 * without Deep Aether), and elsewhere this rule doesn't apply and the next one decides. The
 * patches are {@code size} blocks across and {@code flatten} times as squat as they are wide, so a small value makes
 * seams. The noise is a fixed lattice, the same in every world.
 */
public record StonePatches(SurfaceRules.RuleSource thenRun, double size, double flatten, double threshold, int salt)
        implements SurfaceRules.RuleSource {
    public static final MapCodec<StonePatches> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            SurfaceRules.RuleSource.CODEC.fieldOf("then_run").forGetter(StonePatches::thenRun),
            Codec.doubleRange(2, 128).fieldOf("size").forGetter(StonePatches::size),
            Codec.doubleRange(0.05, 4).optionalFieldOf("flatten", 1.0).forGetter(StonePatches::flatten),
            Codec.doubleRange(-1, 1).fieldOf("threshold").forGetter(StonePatches::threshold),
            Codec.INT.optionalFieldOf("salt", 0).forGetter(StonePatches::salt)
    ).apply(i, StonePatches::new));
    public static final KeyDispatchDataCodec<StonePatches> CODEC = KeyDispatchDataCodec.of(MAP_CODEC);

    @Override
    public SurfaceRules.SurfaceRule apply(SurfaceRules.Context context) {
        double scale = 1 / size, vertical = 1 / (size * flatten);
        SurfaceRules.SurfaceRule inner = thenRun.apply(context);
        return (x, y, z) -> LatticeNoise.value(x * scale, y * vertical, z * scale, salt) > threshold
                ? inner.tryApply(x, y, z) : null;
    }

    @Override
    public KeyDispatchDataCodec<? extends SurfaceRules.RuleSource> codec() {
        return CODEC;
    }
}
