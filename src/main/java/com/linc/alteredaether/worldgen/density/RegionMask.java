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

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.linc.alteredaether.AlteredAether;
import com.linc.alteredaether.worldgen.region.AlteredRegion;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.QuartPos;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.DensityFunction;
import terrablender.api.Region;
import terrablender.worldgen.IExtendedParameterList;

import java.util.concurrent.ExecutionException;

/**
 * 1 inside Altered Aether's TerraBlender region, easing to 0 over {@code falloff} blocks toward its edge, and 0
 * everywhere else. Every one of our landform masks is multiplied by this, so crags, plateaus, dunes and the rift form
 * only under our own biomes and never under another region's (Deep Aether's, say). The ramp lies entirely on our side
 * of the border, so other regions see unmodified terrain right up to it.
 *
 * <p>The region map is per world: TerraBlender seeds it from the world seed. It reaches this function through a
 * {@link Binding} that {@link RegionBinding} fills in when the Aether level loads. An unbound mask reads 0, so a
 * world this mod can't bind generates stock terrain rather than misplaced landforms.
 */
public final class RegionMask implements DensityFunction {
    public static final MapCodec<RegionMask> MAP_CODEC = MapCodec.unit(() -> new RegionMask(null));
    public static final KeyDispatchDataCodec<RegionMask> CODEC = KeyDispatchDataCodec.of(MAP_CODEC);

    /** The mask is solved on this block grid and interpolated in between, which also smooths the region's quart steps. */
    private static final int CELL = 8;
    /** Rings and directions probed when measuring the distance to the region's edge. */
    private static final int RINGS = 6;
    private static final int DIRECTIONS = 16;

    /**
     * Shared by copies of this function made from one router entry. A world's router can reference the mask from
     * several places, each of which gets its own binding; {@link RegionBinding} points them all at one {@link Solver}
     * per world, so the work and its cache are shared.
     */
    public static final class Binding {
        private volatile Solver solver;
        private volatile boolean warned;

        public void bind(Solver solver) {
            this.solver = solver;
        }

        double sample(int x, int z) {
            Solver s = solver;
            if (s == null || !s.parameters.isInitialized()) {
                if (!warned) {
                    warned = true;
                    AlteredAether.LOGGER.warn("Altered Aether's region mask is unbound; its landforms are disabled "
                            + "until the Aether's TerraBlender region map is available.");
                }
                return 0;
            }
            return s.sample(x, z);
        }
    }

    /** Measures, for one world's region map, how far each point lies inside our region. */
    public static final class Solver {
        private final IExtendedParameterList<?> parameters;
        private final double falloff;
        private final Cache<Long, Float> cells = CacheBuilder.newBuilder().maximumSize(1 << 16).build();

        public Solver(IExtendedParameterList<?> parameters, double falloff) {
            this.parameters = parameters;
            this.falloff = falloff;
        }

        public double sample(int x, int z) {
            int cx = Math.floorDiv(x, CELL), cz = Math.floorDiv(z, CELL);
            double fx = (x - cx * CELL) / (double) CELL, fz = (z - cz * CELL) / (double) CELL;
            return Mth.lerp2(fx, fz, cell(cx, cz), cell(cx + 1, cz), cell(cx, cz + 1), cell(cx + 1, cz + 1));
        }

        private float cell(int cx, int cz) {
            long key = ((long) cx << 32) ^ (cz & 0xFFFFFFFFL);
            try {
                return cells.get(key, () -> solve(cx * CELL, cz * CELL));
            } catch (ExecutionException e) {
                throw new IllegalStateException(e.getCause());
            }
        }

        /** Distance from (x, z) to the nearest point outside our region, as a 0..1 ramp over the falloff. */
        private float solve(int x, int z) {
            if (!ours(x, z)) {
                return 0f;
            }
            double previous = 0;
            for (int ring = 1; ring <= RINGS; ring++) {
                double radius = falloff * ring / RINGS;
                for (int d = 0; d < DIRECTIONS; d++) {
                    // Offset each ring's directions by half a step so neighbouring rings probe different bearings.
                    double angle = (d + (ring % 2) * 0.5) * (Math.PI * 2 / DIRECTIONS);
                    if (!ours(x + (int) Math.round(Math.cos(angle) * radius),
                            z + (int) Math.round(Math.sin(angle) * radius))) {
                        // The edge lies between the previous ring and this one; take the midpoint.
                        return smooth((previous + radius) * 0.5 / falloff);
                    }
                }
                previous = radius;
            }
            return 1f;
        }

        private boolean ours(int x, int z) {
            int index = parameters.getUniqueness(QuartPos.fromBlock(x), 0, QuartPos.fromBlock(z));
            Region region = parameters.getRegion(index);
            return region != null && AlteredRegion.NAME.equals(region.getName());
        }

        private static float smooth(double t) {
            t = Mth.clamp(t, 0, 1);
            return (float) (t * t * (3 - 2 * t));
        }
    }

    private final Binding binding;

    private RegionMask(Binding binding) {
        this.binding = binding;
    }

    public Binding binding() {
        return binding;
    }

    /**
     * Copies bound to one solver are the same function. Each NoiseChunk wraps the router afresh and shares one
     * {@code flat_cache} per distinct function; without this, every reference to the mask built and filled its own
     * cache for every chunk, which was over a third of all world generation time.
     */
    @Override
    public boolean equals(Object other) {
        if (!(other instanceof RegionMask mask)) {
            return false;
        }
        if (mask.binding == binding) {
            return true;
        }
        Solver solver = binding == null ? null : binding.solver;
        return solver != null && mask.binding != null && mask.binding.solver == solver;
    }

    @Override
    public int hashCode() {
        return RegionMask.class.hashCode();
    }

    @Override
    public double compute(FunctionContext context) {
        return binding == null ? 0 : binding.sample(context.blockX(), context.blockZ());
    }

    @Override
    public void fillArray(double[] array, ContextProvider provider) {
        provider.fillAllDirectly(array, this);
    }

    /**
     * The copy in the registry has no binding; the first copy made from it (when a world's RandomState wires its
     * router) gets a fresh one, and every later copy of that (each NoiseChunk wraps the router again) shares it.
     */
    @Override
    public DensityFunction mapAll(Visitor visitor) {
        return visitor.apply(new RegionMask(binding != null ? binding : new Binding()));
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
