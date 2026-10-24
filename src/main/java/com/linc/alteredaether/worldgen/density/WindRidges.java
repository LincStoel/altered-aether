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
 * Dune ridges as a 0..1 height profile: crests running across the prevailing wind, each with a long gentle windward
 * slope and a short steep lee face, the asymmetry real dunes have. The crest lines meander (domain warp from
 * {@code warp}) and swell and fade along their length ({@code swell}), so no two ridges match.
 *
 * <p>{@code direction} is the wind's bearing in degrees clockwise from north; when omitted it comes from the
 * config's {@code dune_wind_direction}. {@code wavelength} is crest spacing in blocks; {@code lee} is the fraction of
 * each wavelength taken by the lee face.
 */
public record WindRidges(Optional<Double> direction, double wavelength, double lee, NoiseHolder warp,
                         double warpAmount, NoiseHolder swell) implements DensityFunction {
    public static final MapCodec<WindRidges> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.doubleRange(0, 360).optionalFieldOf("direction").forGetter(WindRidges::direction),
            Codec.doubleRange(4, 512).fieldOf("wavelength").forGetter(WindRidges::wavelength),
            Codec.doubleRange(0.05, 0.95).optionalFieldOf("lee", 0.25).forGetter(WindRidges::lee),
            NoiseHolder.CODEC.fieldOf("warp").forGetter(WindRidges::warp),
            Codec.doubleRange(0, 512).optionalFieldOf("warp_amount", 24.0).forGetter(WindRidges::warpAmount),
            NoiseHolder.CODEC.fieldOf("swell").forGetter(WindRidges::swell)
    ).apply(i, WindRidges::new));
    public static final KeyDispatchDataCodec<WindRidges> CODEC = KeyDispatchDataCodec.of(MAP_CODEC);

    @Override
    public double compute(FunctionContext context) {
        double bearing = Math.toRadians(direction.orElseGet(AACommonConfig.DUNE_WIND_DIRECTION::get));
        // Bearing clockwise from north (-z): the wind blows toward (sin, -cos); position along it is u.
        double x = context.blockX(), z = context.blockZ();
        double u = x * Math.sin(bearing) - z * Math.cos(bearing)
                + warp.getValue(x * 0.25, 0, z * 0.25) * warpAmount;
        double phase = u / wavelength;
        double t = phase - Math.floor(phase);
        // The wind climbs the windward slope (most of the wavelength) and drops off the lee face.
        double profile = t < 1 - lee ? smooth(t / (1 - lee)) : 1 - smooth((t - (1 - lee)) / lee);
        double strength = Mth.clamp(0.6 + 0.6 * swell.getValue(x * 0.5, 0, z * 0.5), 0, 1);
        return profile * strength;
    }

    private static double smooth(double t) {
        return t * t * (3 - 2 * t);
    }

    @Override
    public void fillArray(double[] array, ContextProvider provider) {
        provider.fillAllDirectly(array, this);
    }

    @Override
    public DensityFunction mapAll(Visitor visitor) {
        return visitor.apply(new WindRidges(direction, wavelength, lee, visitor.visitNoise(warp), warpAmount,
                visitor.visitNoise(swell)));
    }

    @Override
    public double minValue() {
        return 0;
    }

    @Override
    public double maxValue() {
        return 1;
    }

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return CODEC;
    }
}
