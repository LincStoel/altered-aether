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
 * Caves in the island bodies: {@code argument} with {@code caves} (negative inside a cave) cut out of it wherever the
 * {@link CaveField} gives a cave room. Its argument is the terrain before any cave, and {@link RegionBinding} reads
 * the world's site probes off it, so basins, lakes and landmarks are sited on rock the caves then keep clear of.
 * Unbound, it is its argument.
 */
public final class CaveCarve implements DensityFunction {
    public static final MapCodec<CaveCarve> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("argument").forGetter(CaveCarve::argument),
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("caves").forGetter(CaveCarve::caves),
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("entrance").forGetter(CaveCarve::entrance)
    ).apply(i, (argument, caves, entrance) -> new CaveCarve(argument, caves, entrance, null)));
    public static final KeyDispatchDataCodec<CaveCarve> CODEC = KeyDispatchDataCodec.of(MAP_CODEC);

    /** How far a lack of room lifts the cave field, enough to keep it above any cave value. */
    private static final double CLOSED = 2;

    /** Shared by every copy made from one router entry; see {@link RegionMask#mapAll}. */
    public static final class Binding {
        private volatile CaveField field;

        public void bind(CaveField field) {
            this.field = field;
        }
    }

    private final DensityFunction argument, caves, entrance;
    private final Binding binding;

    private CaveCarve(DensityFunction argument, DensityFunction caves, DensityFunction entrance, Binding binding) {
        this.argument = argument;
        this.caves = caves;
        this.entrance = entrance;
        this.binding = binding;
    }

    public DensityFunction argument() {
        return argument;
    }

    public DensityFunction caves() {
        return caves;
    }

    public DensityFunction entrance() {
        return entrance;
    }

    public Binding binding() {
        return binding;
    }

    @Override
    public double compute(FunctionContext context) {
        double ground = argument.compute(context);
        CaveField field = binding == null ? null : binding.field;
        if (field == null || ground <= 0) {
            return ground;
        }
        double room = field.weight(context.blockX(), context.blockY(), context.blockZ(), entrance.compute(context));
        if (room <= 0) {
            return ground;
        }
        return Math.min(ground, caves.compute(context) + CLOSED * (1 - room));
    }

    @Override
    public void fillArray(double[] array, ContextProvider provider) {
        provider.fillAllDirectly(array, this);
    }

    @Override
    public DensityFunction mapAll(Visitor visitor) {
        return visitor.apply(new CaveCarve(argument.mapAll(visitor), caves.mapAll(visitor), entrance.mapAll(visitor),
                binding != null ? binding : new Binding()));
    }

    @Override
    public double minValue() {
        return Math.min(argument.minValue(), caves.minValue());
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
