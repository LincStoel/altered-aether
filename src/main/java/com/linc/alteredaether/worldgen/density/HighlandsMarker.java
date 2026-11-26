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
 * Where the tall Aether's Highlands islets float, for finding a spot for an Essence Channel (see
 * worldgen/essence/EssenceField). All three are 2D: {@code footprint}, the islets' raw footprint noise, which has to pass
 * 0.42 for an islet to stand at all, so a low one rules a column out cheaply; {@code presence}, the lens's own footprint
 * term, positive where an islet stands and the larger the further in; and {@code top}, the height of an islet's domed
 * top there. It sits in the router's {@code fluid_level_floodedness} slot, which nothing evaluates (the Aether has no
 * aquifers), so it costs the terrain nothing; it rides along so that the router seeds its noises and binds its biome
 * edges, and {@link RegionBinding} hands it to the field. Computes as {@code presence}.
 */
public record HighlandsMarker(DensityFunction footprint, DensityFunction presence, DensityFunction top)
        implements DensityFunction {
    public static final MapCodec<HighlandsMarker> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("footprint").forGetter(HighlandsMarker::footprint),
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("presence").forGetter(HighlandsMarker::presence),
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("top").forGetter(HighlandsMarker::top)
    ).apply(i, HighlandsMarker::new));
    public static final KeyDispatchDataCodec<HighlandsMarker> CODEC = KeyDispatchDataCodec.of(MAP_CODEC);

    @Override
    public double compute(FunctionContext context) {
        return presence.compute(context);
    }

    @Override
    public void fillArray(double[] array, ContextProvider provider) {
        presence.fillArray(array, provider);
    }

    @Override
    public DensityFunction mapAll(Visitor visitor) {
        return visitor.apply(new HighlandsMarker(footprint.mapAll(visitor), presence.mapAll(visitor),
                top.mapAll(visitor)));
    }

    @Override
    public double minValue() {
        return presence.minValue();
    }

    @Override
    public double maxValue() {
        return presence.maxValue();
    }

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return CODEC;
    }
}
