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

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

import java.util.Objects;

/**
 * How much island there is round a column: the average thickness of solid ground in {@code argument} over a disc
 * about {@code radius} blocks across, in blocks. A small island floating alone reads low and a big island's body
 * reads high, which is what lets the terrain shed its scattered fragments and keep its landmasses. The terrain never
 * evaluates {@code argument} through this function; a {@link ColumnMassField}, bound per world by
 * {@link RegionBinding}, samples it. Unbound, it reads a large value, so nothing is shed.
 */
public final class IslandMass implements DensityFunction {
    public static final MapCodec<IslandMass> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("argument").forGetter(IslandMass::argument),
            Codec.intRange(16, 128).fieldOf("radius").forGetter(IslandMass::radius),
            Codec.INT.fieldOf("min_y").forGetter(IslandMass::minY),
            Codec.INT.fieldOf("max_y").forGetter(IslandMass::maxY)
    ).apply(i, (argument, radius, minY, maxY) -> new IslandMass(argument, radius, minY, maxY, null)));
    public static final KeyDispatchDataCodec<IslandMass> CODEC = KeyDispatchDataCodec.of(MAP_CODEC);

    /** What an unbound function reads: more than any real island, so nothing is cut. */
    private static final double UNBOUND = 1000;

    /** Shared by every copy made from one router entry; see {@link RegionMask#mapAll}. */
    public static final class Binding {
        private volatile ColumnMassField field;

        public void bind(ColumnMassField field) {
            this.field = field;
        }
    }

    private final DensityFunction argument;
    private final int radius, minY, maxY;
    private final Binding binding;

    private IslandMass(DensityFunction argument, int radius, int minY, int maxY, Binding binding) {
        this.argument = argument;
        this.radius = radius;
        this.minY = minY;
        this.maxY = maxY;
        this.binding = binding;
    }

    public DensityFunction argument() {
        return argument;
    }

    public int radius() {
        return radius;
    }

    public int minY() {
        return minY;
    }

    public int maxY() {
        return maxY;
    }

    public Binding binding() {
        return binding;
    }

    @Override
    public double compute(FunctionContext context) {
        ColumnMassField field = binding == null ? null : binding.field;
        return field == null ? UNBOUND : field.mass(context.blockX(), context.blockZ());
    }

    @Override
    public void fillArray(double[] array, ContextProvider provider) {
        provider.fillAllDirectly(array, this);
    }

    @Override
    public DensityFunction mapAll(Visitor visitor) {
        return visitor.apply(new IslandMass(argument.mapAll(visitor), radius, minY, maxY,
                binding != null ? binding : new Binding()));
    }

    /** Copies reading the same field are the same function, so NoiseChunk shares their caches. */
    @Override
    public boolean equals(Object other) {
        if (!(other instanceof IslandMass mass) || mass.radius != radius || mass.minY != minY || mass.maxY != maxY) {
            return false;
        }
        if (mass.binding == binding) {
            return true;
        }
        ColumnMassField field = binding == null ? null : binding.field;
        return field != null && mass.binding != null && mass.binding.field == field;
    }

    @Override
    public int hashCode() {
        return Objects.hash(radius, minY, maxY);
    }

    @Override
    public double minValue() {
        return 0;
    }

    @Override
    public double maxValue() {
        return UNBOUND;
    }

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return CODEC;
    }
}
