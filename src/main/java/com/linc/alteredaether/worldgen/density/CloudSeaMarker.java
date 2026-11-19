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
 * Where the tall Aether's cloud sea lies: computes as its {@code argument}, the sea's mask, which picks the cloud-sea
 * islands over the plain ones. It also carries {@code sheet}, how much of the sea's cloud sheet survives at a column
 * once the landforms above it in the chain have taken their share. The terrain never reads it; it rides along so that
 * the router seeds its noises, and {@link RegionBinding} hands it to the sheet ({@code CloudSea}).
 */
public record CloudSeaMarker(DensityFunction argument) implements DensityFunction {
    public static final MapCodec<CloudSeaMarker> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("argument").forGetter(CloudSeaMarker::argument)
    ).apply(i, CloudSeaMarker::new));
    public static final KeyDispatchDataCodec<CloudSeaMarker> CODEC = KeyDispatchDataCodec.of(MAP_CODEC);

    @Override
    public double compute(FunctionContext context) {
        return argument.compute(context);
    }

    @Override
    public void fillArray(double[] array, ContextProvider provider) {
        argument.fillArray(array, provider);
    }

    @Override
    public DensityFunction mapAll(Visitor visitor) {
        return visitor.apply(new CloudSeaMarker(argument.mapAll(visitor)));
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
