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

/**
 * Breaks {@code argument} into drifting slabs. The plane is divided into Voronoi cells about {@code cell_size}
 * blocks across; each cell becomes a slab of the original terrain, lifted or dropped by up to {@code max_offset}
 * blocks and tilted by up to {@code max_tilt} (blocks of rise per block), and the boundaries between cells open into
 * cracks {@code crack_width} blocks wide. Distance to the boundary is the F2 - F1 Voronoi distance, so cracks are
 * long, mostly straight fault lines rather than noise blobs. {@code roughness} (typically a fine 3D noise) widens
 * and narrows each crack with height, so its walls look eroded instead of cut.
 *
 * <p>The cells come from {@code seed}, a noise wired to the world seed, so every world fractures differently.
 * Slab size and drift scale with the config's {@code rift_fracture_size} and {@code rift_drift}.
 */
public record VoronoiFracture(DensityFunction argument, NoiseHolder seed, double cellSize, double crackWidth,
                              double maxOffset, double maxTilt, DensityFunction roughness)
        implements DensityFunction {
    public static final MapCodec<VoronoiFracture> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("argument").forGetter(VoronoiFracture::argument),
            NoiseHolder.CODEC.fieldOf("seed").forGetter(VoronoiFracture::seed),
            Codec.doubleRange(8, 1024).fieldOf("cell_size").forGetter(VoronoiFracture::cellSize),
            Codec.doubleRange(0, 64).fieldOf("crack_width").forGetter(VoronoiFracture::crackWidth),
            Codec.doubleRange(0, 128).fieldOf("max_offset").forGetter(VoronoiFracture::maxOffset),
            Codec.doubleRange(0, 2).fieldOf("max_tilt").forGetter(VoronoiFracture::maxTilt),
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("roughness").forGetter(VoronoiFracture::roughness)
    ).apply(i, VoronoiFracture::new));
    public static final KeyDispatchDataCodec<VoronoiFracture> CODEC = KeyDispatchDataCodec.of(MAP_CODEC);

    /** Density subtracted at the centre of a crack: far below anything the terrain reaches, so cracks are open air. */
    private static final double CRACK_DEPTH = 8.0;

    @Override
    public double compute(FunctionContext context) {
        double size = cellSize * AACommonConfig.RIFT_FRACTURE_SIZE.get();
        double drift = AACommonConfig.RIFT_DRIFT.get();
        double x = context.blockX() / size, z = context.blockZ() / size;
        int gx = Mth.floor(x), gz = Mth.floor(z);

        // Nearest and second-nearest cell points, searching the 3x3 neighbourhood of grid cells.
        double best = Double.MAX_VALUE, second = Double.MAX_VALUE;
        int bestX = 0, bestZ = 0;
        double bestPx = 0, bestPz = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int cx = gx + dx, cz = gz + dz;
                double px = cx + 0.5 + 0.45 * hash(cx, cz, 0);
                double pz = cz + 0.5 + 0.45 * hash(cx, cz, 1);
                double d = Math.hypot(x - px, z - pz);
                if (d < best) {
                    second = best;
                    best = d;
                    bestX = cx;
                    bestZ = cz;
                    bestPx = px;
                    bestPz = pz;
                } else if (d < second) {
                    second = d;
                }
            }
        }

        // This slab's displacement: a lift and a tilt about the cell point.
        double lift = maxOffset * drift * hash(bestX, bestZ, 2);
        double tiltX = maxTilt * drift * hash(bestX, bestZ, 3);
        double tiltZ = maxTilt * drift * hash(bestX, bestZ, 4);
        double rise = lift + tiltX * (x - bestPx) * size + tiltZ * (z - bestPz) * size;
        double slab = argument.compute(new SinglePointContext(context.blockX(),
                context.blockY() - (int) Math.round(rise), context.blockZ()));

        // Distance to the cell boundary in blocks, and the crack carved along it.
        double edge = (second - best) * size * 0.5;
        double width = crackWidth * (1 + 0.5 * roughness.compute(context));
        if (edge >= width) {
            return slab;
        }
        double t = edge / Math.max(width, 1e-3);
        return slab - CRACK_DEPTH * (1 - t * t * (3 - 2 * t));
    }

    /** A per-cell value in [-1, 1], from the world-seeded noise sampled at far-apart integer points. */
    private double hash(int cx, int cz, int channel) {
        double v = seed.getValue(cx * 31.17 + channel * 113.71, channel * 7.3, cz * 31.17 - channel * 57.13);
        return Mth.clamp(v * 1.6, -1, 1);
    }

    @Override
    public void fillArray(double[] array, ContextProvider provider) {
        provider.fillAllDirectly(array, this);
    }

    @Override
    public DensityFunction mapAll(Visitor visitor) {
        return visitor.apply(new VoronoiFracture(argument.mapAll(visitor), visitor.visitNoise(seed), cellSize,
                crackWidth, maxOffset, maxTilt, roughness.mapAll(visitor)));
    }

    @Override
    public double minValue() {
        return argument.minValue() - CRACK_DEPTH;
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
