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
import com.linc.alteredaether.worldgen.landmark.GreatFracture;
import com.linc.alteredaether.worldgen.landmark.LandmarkField;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.DensityFunction;

import java.util.Optional;

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
 *
 * <p>With {@code pillars}, the cells stand apart instead: each is a tall, blocky column of rock of its own, taller than
 * it is wide, flat-topped, each side a sheer cliff, a staircase or a slope, and hanging underneath in a ragged
 * root as Aether islands do, with wide gaps of open sky
 * between neighbours and some cells left empty altogether. The column's top follows the slab's lift and tilt; the
 * terrain in {@code argument} is not used.
 *
 * <p>Where a {@link GreatFracture} lies, cells whose points fall inside it merge into its giant slabs: they share the
 * giant's lift and tilt, and the cracks between cells of one giant close up, leaving only the faults between giants.
 * The world's landmark field is bound per world by {@link RegionBinding}.
 */
public record VoronoiFracture(DensityFunction argument, NoiseHolder seed, double cellSize, double crackWidth,
                              double maxOffset, double maxTilt, DensityFunction roughness, Optional<Pillars> pillars,
                              Binding binding)
        implements DensityFunction {
    /**
     * Free-standing columns in place of slabs.
     *
     * @param top         height of an average column's top, before its lift
     * @param minHeight   the shortest column, top to tip
     * @param maxHeight   the tallest
     * @param emptyChance the share of cells left as open sky
     * @param step        height of the ledges stepping down a column's sides
     * @param gap         open sky kept either side of each boundary between cells, in blocks
     */
    public record Pillars(int top, double minHeight, double maxHeight, double emptyChance, int step, double gap) {
        public static final Codec<Pillars> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("top").forGetter(Pillars::top),
                Codec.doubleRange(4, 256).fieldOf("min_height").forGetter(Pillars::minHeight),
                Codec.doubleRange(4, 256).fieldOf("max_height").forGetter(Pillars::maxHeight),
                Codec.doubleRange(0, 1).optionalFieldOf("empty_chance", 0.3).forGetter(Pillars::emptyChance),
                Codec.intRange(2, 32).optionalFieldOf("step", 7).forGetter(Pillars::step),
                Codec.doubleRange(0, 64).optionalFieldOf("gap", 8.0).forGetter(Pillars::gap)
        ).apply(i, Pillars::new));
    }

    /** Shared by every copy made from one world's router; see {@link RegionMask#mapAll}. */
    public static final class Binding {
        private volatile LandmarkField field;

        public void bind(LandmarkField field) {
            this.field = field;
        }
    }

    public static final MapCodec<VoronoiFracture> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("argument").forGetter(VoronoiFracture::argument),
            NoiseHolder.CODEC.fieldOf("seed").forGetter(VoronoiFracture::seed),
            Codec.doubleRange(8, 1024).fieldOf("cell_size").forGetter(VoronoiFracture::cellSize),
            Codec.doubleRange(0, 64).fieldOf("crack_width").forGetter(VoronoiFracture::crackWidth),
            Codec.doubleRange(0, 128).fieldOf("max_offset").forGetter(VoronoiFracture::maxOffset),
            Codec.doubleRange(0, 2).fieldOf("max_tilt").forGetter(VoronoiFracture::maxTilt),
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("roughness").forGetter(VoronoiFracture::roughness),
            Pillars.CODEC.optionalFieldOf("pillars").forGetter(VoronoiFracture::pillars)
    ).apply(i, (argument, seed, cell, crack, offset, tilt, roughness, pillars) ->
            new VoronoiFracture(argument, seed, cell, crack, offset, tilt, roughness, pillars, null)));
    public static final KeyDispatchDataCodec<VoronoiFracture> CODEC = KeyDispatchDataCodec.of(MAP_CODEC);

    /** Density subtracted at the centre of a crack: far below anything the terrain reaches, so cracks are open air. */
    private static final double CRACK_DEPTH = 8.0;

    @Override
    public double compute(FunctionContext context) {
        double size = cellSize * AACommonConfig.RIFT_FRACTURE_SIZE.get();
        double drift = AACommonConfig.RIFT_DRIFT.get();
        double x = context.blockX() / size, z = context.blockZ() / size;
        int gx = Mth.floor(x), gz = Mth.floor(z);
        LandmarkField field = binding == null ? null : binding.field;
        if (field != null) {
            GreatFracture fracture = field.fractureNear(context.blockX(), context.blockZ()).orElse(null);
            if (fracture != null) {
                return great(context, fracture, field.vertical(), size, drift, x, z, gx, gz);
            }
        }

        // Nearest and second-nearest cell points, searching the 3x3 neighbourhood of grid cells.
        double best = Double.MAX_VALUE, second = Double.MAX_VALUE;
        int bestX = 0, bestZ = 0, secondX = 0, secondZ = 0;
        double bestPx = 0, bestPz = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int cx = gx + dx, cz = gz + dz;
                double px = cx + 0.5 + 0.45 * hash(cx, cz, 0);
                double pz = cz + 0.5 + 0.45 * hash(cx, cz, 1);
                double d = Math.hypot(x - px, z - pz);
                if (d < best) {
                    second = best;
                    secondX = bestX;
                    secondZ = bestZ;
                    best = d;
                    bestX = cx;
                    bestZ = cz;
                    bestPx = px;
                    bestPz = pz;
                } else if (d < second) {
                    second = d;
                    secondX = cx;
                    secondZ = cz;
                }
            }
        }

        // This slab's displacement: a lift and a tilt about the cell point.
        double lift = maxOffset * drift * hash(bestX, bestZ, 2);
        double tiltX = maxTilt * drift * hash(bestX, bestZ, 3);
        double tiltZ = maxTilt * drift * hash(bestX, bestZ, 4);
        double rise = lift + tiltX * (x - bestPx) * size + tiltZ * (z - bestPz) * size;
        if (pillars.isPresent()) {
            return pillar(context, pillars.get(), size, (second - best) * size * 0.5, rise, bestX, bestZ, secondX,
                    secondZ);
        }
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

    /**
     * The terrain in and around a great fracture. The nearest cell point picks the slab as usual; a slab whose cell
     * belongs to a giant takes the giant's displacement instead of its own. The crack is measured to the nearest
     * neighbour whose cell belongs to a different slab, so the boundaries inside a giant vanish.
     */
    private double great(FunctionContext context, GreatFracture fracture, double vertical, double size, double drift,
                         double x, double z, int gx, int gz) {
        double[] distance = new double[9];
        int[] group = new int[9];
        int[] cellX = new int[9], cellZ = new int[9];
        double[] pointX = new double[9], pointZ = new double[9];
        int best = 0;
        for (int i = 0; i < 9; i++) {
            int cx = gx + i % 3 - 1, cz = gz + i / 3 - 1;
            double px = cx + 0.5 + 0.45 * hash(cx, cz, 0);
            double pz = cz + 0.5 + 0.45 * hash(cx, cz, 1);
            cellX[i] = cx;
            cellZ[i] = cz;
            pointX[i] = px;
            pointZ[i] = pz;
            distance[i] = Math.hypot(x - px, z - pz);
            // Cells outside the zone each stand alone: give each a group of its own.
            int g = fracture.group(px * size, pz * size);
            group[i] = g >= 0 ? g : -1 - i;
            if (distance[i] < distance[best]) {
                best = i;
            }
        }
        double rise;
        if (group[best] >= 0) {
            rise = fracture.rise(group[best], context.blockX(), context.blockZ()) * vertical * drift;
        } else {
            int bx = cellX[best], bz = cellZ[best];
            rise = maxOffset * drift * hash(bx, bz, 2)
                    + maxTilt * drift * hash(bx, bz, 3) * (x - pointX[best]) * size
                    + maxTilt * drift * hash(bx, bz, 4) * (z - pointZ[best]) * size;
        }
        double slab = argument.compute(new SinglePointContext(context.blockX(),
                context.blockY() - (int) Math.round(rise), context.blockZ()));
        if (group[best] >= 0) {
            double wave = 4 * vertical * seed.getValue(context.blockX() / 37.0, 0, context.blockZ() / 37.0);
            slab = Math.max(slab, fracture.plate(context.blockX(), context.blockY(), context.blockZ(), rise, wave));
        }

        double edge = Double.MAX_VALUE, width = crackWidth;
        for (int i = 0; i < 9; i++) {
            if (i == best || group[i] == group[best]) {
                continue;
            }
            double e = (distance[i] - distance[best]) * size * 0.5;
            if (e < edge) {
                edge = e;
                width = fracture.width(group[best], group[i], crackWidth);
            }
        }
        width *= 1 + 0.5 * roughness.compute(context);
        if (edge >= width) {
            return slab;
        }
        double t = edge / Math.max(width, 1e-3);
        return slab - CRACK_DEPTH * (1 - t * t * (3 - 2 * t));
    }

    /**
     * One cell's column, measured inward from the gap's edge. Each side, the face toward one neighbouring cell, takes
     * one of three forms: a sheer cliff, broken only by ledges every {@code step} blocks set back by their own small
     * amounts; a staircase of broad terraces climbing in toward the top; or a slope rounding off from the rim. Below
     * the top half the column hangs in a ragged root, narrowing faster and faster to a point off its middle, its
     * surface roughened, as Aether islands do.
     *
     * @param edge distance to the cell's boundary, in blocks; the boundary is shared with cell (nx, nz)
     */
    private double pillar(FunctionContext context, Pillars shape, double size, double edge, double rise, int cx,
                          int cz, int nx, int nz) {
        if (unit(cx, cz, 5) < shape.emptyChance()) {
            return -1;
        }
        double top = shape.top() + rise;
        double height = Mth.lerp(unit(cx, cz, 6), shape.minHeight(), shape.maxHeight());
        int y = context.blockY();
        if (y > top + 4 || y < top - height - 4) {
            return -1;
        }
        double rough = roughness.compute(context);
        double width = shape.gap() * (1 + 0.4 * rough);
        double below = top - y;
        int band = Math.floorDiv((int) Math.floor(top) - y, shape.step());
        // The side's form, the same from both cells that share it.
        double form = unit(Math.min(cx * 7919 + cz, nx * 7919 + nz), Math.max(cx * 7919 + cz, nx * 7919 + nz), 8);
        double setBack;
        if (form < 0.45) {
            // A cliff: each band of the wall sits back by up to four blocks; the top band least, for a sharp rim.
            setBack = band <= 0 ? 0 : 4 * unit(cx * 31 + band, cz * 17 - band, 7);
        } else if (form < 0.75) {
            // A staircase: terraces a few blocks deep, each band stepping out from the one above.
            setBack = Math.max(0, 8 - 2.5 * Math.max(0, band));
        } else {
            // A slope: the rim rounds off and the face leans out as it falls.
            setBack = Math.max(0, 8 - below * 0.5);
        }
        double inside = edge - width - setBack;
        // The root: from half way down, the walls draw in ever faster toward a point, roughened and off-centre.
        double waist = top - height * 0.5;
        if (y < waist) {
            double t = (waist - y) / (height * 0.5);
            inside -= t * Math.sqrt(t) * size * 0.5 + t * 10 * rough;
        }
        double density = Math.min(inside, below) * 0.25;
        return Mth.clamp(density, -1, 1);
    }

    /** A per-cell value in [0, 1). */
    private double unit(int cx, int cz, int channel) {
        return (hash(cx, cz, channel) + 1) * 0.4999;
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
                crackWidth, maxOffset, maxTilt, roughness.mapAll(visitor), pillars,
                binding != null ? binding : new Binding()));
    }

    @Override
    public double minValue() {
        return Math.min(-1, argument.minValue() - CRACK_DEPTH);
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
