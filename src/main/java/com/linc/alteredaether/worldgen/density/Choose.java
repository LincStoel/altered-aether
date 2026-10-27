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

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;

import java.util.Objects;

/**
 * {@code low} where {@code mask} is 0, {@code high} where it is 1, and a lerp between them in between, evaluating only
 * the side that matters at either end. It gives exactly what
 * <pre>range_choice(mask, [-1, 0.0001) -> low,
 *     else range_choice(mask, [0.9999, 2) -> high, else (1 - mask) * low + mask * high))</pre>
 * gives, operation for operation, and reports the same bounds, but names each argument once. Spelled out in vanilla
 * functions, each choice referenced {@code low} and {@code high} twice and {@code mask} four times, so nested choices
 * doubled the tree at every level: the Aether's router walked as 67,000 nodes from under a thousand distinct ones,
 * and every NoiseChunk (including each single-column height probe) rebuilt and hashed all of them.
 */
public final class Choose implements DensityFunction {
    public static final MapCodec<Choose> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("mask").forGetter(Choose::mask),
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("low").forGetter(Choose::low),
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("high").forGetter(Choose::high)
    ).apply(i, Choose::new));
    public static final KeyDispatchDataCodec<Choose> CODEC = KeyDispatchDataCodec.of(MAP_CODEC);

    private final DensityFunction mask;
    private final DensityFunction low;
    private final DensityFunction high;
    private final double minValue;
    private final double maxValue;
    private final int hash;

    public Choose(DensityFunction mask, DensityFunction low, DensityFunction high) {
        this.mask = mask;
        this.low = low;
        this.high = high;
        // The bounds the spelled-out functions would have: vanilla's min and max short-circuit on them.
        DensityFunction lerp = DensityFunctions.add(
                DensityFunctions.mul(DensityFunctions.add(DensityFunctions.constant(1.0),
                        DensityFunctions.mul(DensityFunctions.constant(-1.0), mask)), low),
                DensityFunctions.mul(mask, high));
        DensityFunction spelled = DensityFunctions.rangeChoice(mask, -1.0, 0.0001, low,
                DensityFunctions.rangeChoice(mask, 0.9999, 2.0, high, lerp));
        this.minValue = spelled.minValue();
        this.maxValue = spelled.maxValue();
        this.hash = Objects.hash(mask, low, high);
    }

    public DensityFunction mask() {
        return mask;
    }

    public DensityFunction low() {
        return low;
    }

    public DensityFunction high() {
        return high;
    }

    @Override
    public double compute(FunctionContext context) {
        return choose(mask.compute(context), context);
    }

    private double choose(double m, FunctionContext context) {
        if (m >= -1.0 && m < 0.0001) {
            return low.compute(context);
        }
        if (m >= 0.9999 && m < 2.0) {
            return high.compute(context);
        }
        // As vanilla's mul and add compute it: a product whose first factor is 0 is 0, without the second.
        double complement = m * -1.0 + 1.0;
        double lowPart = complement == 0.0 ? 0.0 : complement * low.compute(context);
        double highPart = m == 0.0 ? 0.0 : m * high.compute(context);
        return lowPart + highPart;
    }

    @Override
    public void fillArray(double[] array, ContextProvider provider) {
        mask.fillArray(array, provider);
        for (int i = 0; i < array.length; i++) {
            array[i] = choose(array[i], provider.forIndex(i));
        }
    }

    @Override
    public DensityFunction mapAll(Visitor visitor) {
        return visitor.apply(new Choose(mask.mapAll(visitor), low.mapAll(visitor), high.mapAll(visitor)));
    }

    @Override
    public double minValue() {
        return minValue;
    }

    @Override
    public double maxValue() {
        return maxValue;
    }

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return CODEC;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Choose choose && hash == choose.hash && mask.equals(choose.mask)
                && low.equals(choose.low) && high.equals(choose.high);
    }

    @Override
    public int hashCode() {
        return hash;
    }
}
