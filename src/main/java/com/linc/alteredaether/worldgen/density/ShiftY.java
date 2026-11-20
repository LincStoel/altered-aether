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

/**
 * {@code argument} sampled {@code offset} blocks lower, so terrain is raised by {@code offset} wherever it is
 * positive. Raising a whole column keeps cliffs and undersides intact, which adding density near the surface would
 * not; it is how ripples are laid onto existing islands.
 */
public record ShiftY(DensityFunction argument, DensityFunction offset) implements DensityFunction {
    public static final MapCodec<ShiftY> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("argument").forGetter(ShiftY::argument),
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("offset").forGetter(ShiftY::offset)
    ).apply(i, ShiftY::new));
    public static final KeyDispatchDataCodec<ShiftY> CODEC = KeyDispatchDataCodec.of(MAP_CODEC);

    @Override
    public double compute(FunctionContext context) {
        double shift = offset.compute(context);
        if (shift == 0) {
            return argument.compute(context);
        }
        return argument.compute(new SinglePointContext(context.blockX(),
                context.blockY() - (int) Math.round(shift), context.blockZ()));
    }

    /**
     * Where nothing is shifted (most of the world, when the offset is a lift confined to one landform), the argument
     * fills the array itself, at full speed; only a column that is shifted anywhere is computed point by point.
     */
    @Override
    public void fillArray(double[] array, ContextProvider provider) {
        offset.fillArray(array, provider);
        boolean shifted = false;
        for (double shift : array) {
            if (shift != 0) {
                shifted = true;
                break;
            }
        }
        if (!shifted) {
            argument.fillArray(array, provider);
            return;
        }
        for (int i = 0; i < array.length; i++) {
            FunctionContext context = provider.forIndex(i);
            double shift = array[i];
            array[i] = shift == 0 ? argument.compute(context) : argument.compute(new SinglePointContext(
                    context.blockX(), context.blockY() - (int) Math.round(shift), context.blockZ()));
        }
    }

    @Override
    public DensityFunction mapAll(Visitor visitor) {
        return visitor.apply(new ShiftY(argument.mapAll(visitor), offset.mapAll(visitor)));
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
