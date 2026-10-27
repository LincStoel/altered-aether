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

import com.linc.alteredaether.worldgen.basin.AtollSite;
import com.linc.alteredaether.worldgen.basin.BasinField;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;


/**
 * Atolls laid over {@code argument}: at each atoll site a ring of land around a lagoon is set into the terrain, or a
 * double atoll stands alone in a moat of open sky (see {@link AtollSite}). Sites come from the world's
 * {@link BasinField}, which reads their height off {@code argument}; {@code weight} is the region mask, which the field
 * uses to keep every atoll wholly inside Altered Aether's region, and {@code surface} is the height a double atoll
 * stands at where there are no islands round it to match.
 */
public final class Atoll implements DensityFunction {
    public static final MapCodec<Atoll> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("argument").forGetter(Atoll::argument),
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("weight").forGetter(Atoll::weight),
            Codec.INT.fieldOf("surface").forGetter(Atoll::surface),
            Codec.doubleRange(0.1, 4).optionalFieldOf("vertical_scale", 1.0).forGetter(Atoll::verticalScale)
    ).apply(i, (argument, weight, surface, vertical) -> new Atoll(argument, weight, surface, vertical, null)));
    public static final KeyDispatchDataCodec<Atoll> CODEC = KeyDispatchDataCodec.of(MAP_CODEC);

    private final DensityFunction argument;
    private final DensityFunction weight;
    private final int surface;
    private final double verticalScale;
    private final BasinCarve.Binding binding;

    private Atoll(DensityFunction argument, DensityFunction weight, int surface, double verticalScale,
                  BasinCarve.Binding binding) {
        this.argument = argument;
        this.weight = weight;
        this.surface = surface;
        this.verticalScale = verticalScale;
        this.binding = binding;
    }

    public DensityFunction argument() {
        return argument;
    }

    public DensityFunction weight() {
        return weight;
    }

    public int surface() {
        return surface;
    }

    public double verticalScale() {
        return verticalScale;
    }

    public BasinCarve.Binding binding() {
        return binding;
    }

    @Override
    public double compute(FunctionContext context) {
        double density = argument.compute(context);
        BasinField field = binding == null ? null : binding.field();
        if (field == null) {
            return density;
        }
        return field.atollDensity(context.blockX(), context.blockY(), context.blockZ(), density);
    }

    @Override
    public void fillArray(double[] array, ContextProvider provider) {
        provider.fillAllDirectly(array, this);
    }

    @Override
    public DensityFunction mapAll(Visitor visitor) {
        return visitor.apply(new Atoll(argument.mapAll(visitor), weight.mapAll(visitor), surface,
                verticalScale, binding != null ? binding : new BasinCarve.Binding()));
    }

    @Override
    public double minValue() {
        return Math.min(-1, argument.minValue());
    }

    @Override
    public double maxValue() {
        return Math.max(1, argument.maxValue());
    }

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return CODEC;
    }
}
