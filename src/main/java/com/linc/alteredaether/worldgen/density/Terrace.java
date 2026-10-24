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
package com.linc.alteredaether.worldgen.density;

import com.linc.alteredaether.config.AACommonConfig;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.DensityFunction;

import java.util.Optional;

/**
 * {@code argument} sampled at a stepped height, which turns smooth slopes into terraces: flat treads separated by
 * risers. Within each step of {@code step} blocks the sample height rises slowly across the tread and quickly across
 * the riser; {@code sharpness} sets how abrupt the riser is (1 is no terracing).
 *
 * <p>When {@code step} is omitted it comes from the config's {@code plateau_terrace_height} (0 disables terracing),
 * multiplied by {@code step_scale} (the stock-height terrain uses 1/1.5, since the config is in tall-Aether blocks).
 */
public record Terrace(DensityFunction argument, Optional<Double> step, double stepScale, double sharpness)
        implements DensityFunction {
    public static final MapCodec<Terrace> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("argument").forGetter(Terrace::argument),
            Codec.doubleRange(1, 64).optionalFieldOf("step").forGetter(Terrace::step),
            Codec.doubleRange(0.1, 10).optionalFieldOf("step_scale", 1.0).forGetter(Terrace::stepScale),
            Codec.doubleRange(1, 16).optionalFieldOf("sharpness", 4.0).forGetter(Terrace::sharpness)
    ).apply(i, Terrace::new));
    public static final KeyDispatchDataCodec<Terrace> CODEC = KeyDispatchDataCodec.of(MAP_CODEC);

    @Override
    public double compute(FunctionContext context) {
        double height = step.orElseGet(() -> (double) AACommonConfig.PLATEAU_TERRACE_HEIGHT.get()) * stepScale;
        if (height <= 0) {
            return argument.compute(context);
        }
        double y = context.blockY();
        double base = Math.floor(y / height) * height;
        double t = (y - base) / height;
        // Steep in the middle of the step (the riser), flat near both ends (the treads).
        double eased = t < 0.5 ? 0.5 * Math.pow(2 * t, sharpness) : 1 - 0.5 * Math.pow(2 * (1 - t), sharpness);
        int sampleY = Mth.floor(base + eased * height);
        return argument.compute(new SinglePointContext(context.blockX(), sampleY, context.blockZ()));
    }

    @Override
    public void fillArray(double[] array, ContextProvider provider) {
        provider.fillAllDirectly(array, this);
    }

    @Override
    public DensityFunction mapAll(Visitor visitor) {
        return visitor.apply(new Terrace(argument.mapAll(visitor), step, stepScale, sharpness));
    }

    @Override
    public double minValue() {
        return argument.minValue();
    }

    @Override
    public double maxValue() {
        return argument.maxValue();
    }

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return CODEC;
    }
}
