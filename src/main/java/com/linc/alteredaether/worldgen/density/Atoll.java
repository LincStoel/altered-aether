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
import net.minecraft.world.level.levelgen.DensityFunctions;


/**
 * Sunken Atolls laid over {@code argument}: at each atoll site the terrain is replaced by a ring island around a
 * lagoon (see {@link AtollSite}). Sites come from the world's {@link BasinField}; {@code weight} is the region mask,
 * which the field uses to keep every atoll wholly inside Altered Aether's region, and {@code surface} is the height of
 * an average atoll's crest. The Frozen Lakes' lakes are built the same way, around {@code frozen_surface}.
 *
 * <p>Where {@code clear} is 1 the ordinary islands are taken away first, so the atolls stand alone in open sky and a
 * biome made of them is mostly lagoon; between 0 and 1 the islands thin out toward it.
 */
public final class Atoll implements DensityFunction {
    public static final MapCodec<Atoll> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("argument").forGetter(Atoll::argument),
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("weight").forGetter(Atoll::weight),
            Codec.INT.fieldOf("surface").forGetter(Atoll::surface),
            Codec.INT.fieldOf("frozen_surface").forGetter(Atoll::frozenSurface),
            Codec.doubleRange(0.1, 4).optionalFieldOf("vertical_scale", 1.0).forGetter(Atoll::verticalScale),
            DensityFunction.HOLDER_HELPER_CODEC.optionalFieldOf("clear", DensityFunctions.zero()).forGetter(Atoll::clear)
    ).apply(i, (argument, weight, surface, frozen, vertical, clear) -> new Atoll(argument, weight, surface, frozen,
            vertical, clear, null)));
    public static final KeyDispatchDataCodec<Atoll> CODEC = KeyDispatchDataCodec.of(MAP_CODEC);

    private final DensityFunction argument;
    private final DensityFunction weight;
    private final int surface;
    private final int frozenSurface;
    private final double verticalScale;
    private final DensityFunction clear;
    private final BasinCarve.Binding binding;
    /** Density taken from the islands where {@code clear} is 1: more than any island holds. */
    private static final double CLEAR_DEPTH = 3;

    private Atoll(DensityFunction argument, DensityFunction weight, int surface, int frozenSurface,
                  double verticalScale, DensityFunction clear, BasinCarve.Binding binding) {
        this.argument = argument;
        this.weight = weight;
        this.surface = surface;
        this.frozenSurface = frozenSurface;
        this.verticalScale = verticalScale;
        this.clear = clear;
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

    public int frozenSurface() {
        return frozenSurface;
    }

    public double verticalScale() {
        return verticalScale;
    }

    public DensityFunction clear() {
        return clear;
    }

    public BasinCarve.Binding binding() {
        return binding;
    }

    @Override
    public double compute(FunctionContext context) {
        double density = argument.compute(context);
        double cleared = clear.compute(context);
        if (cleared > 0) {
            density -= CLEAR_DEPTH * cleared;
        }
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
        return visitor.apply(new Atoll(argument.mapAll(visitor), weight.mapAll(visitor), surface, frozenSurface,
                verticalScale, clear.mapAll(visitor),
                binding != null ? binding : new BasinCarve.Binding()));
    }

    @Override
    public double minValue() {
        return Math.min(-1, argument.minValue() - CLEAR_DEPTH);
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
