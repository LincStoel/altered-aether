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
package com.linc.alteredaether.worldgen.sea;

import com.linc.alteredaether.config.AACommonConfig;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.synth.PerlinNoise;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One tall Aether's cloud sheet, which lies under the plain islands of every region: the sheet's weight from the
 * terrain ({@code cloud_sheet}, carried by the router's {@code CloudSeaMarker}, 1 except where a landform takes over)
 * and its own two noises ({@link CloudSeaMath}). The marker also carries the Cloud Reaches' mask.
 */
public final class CloudSea {
    private static final Map<ResourceKey<Level>, CloudSea> SEAS = new ConcurrentHashMap<>();
    /** Blocks between the columns the weight is read at; a chunk's sheet interpolates between them. */
    public static final int STEP = 4;

    private final DensityFunction mask;
    private final DensityFunction weight;
    private final PerlinNoise holes;
    private final PerlinNoise drift;
    private final PerlinNoise warpX;
    private final PerlinNoise warpZ;
    /** Blocks the weight is read away from a column, so the sheet's edge wanders rather than following the straight
     * runs of the landform and region masks. */
    public static final double WARP = 32;

    public CloudSea(long seed, DensityFunction mask, DensityFunction weight) {
        this.mask = mask;
        this.weight = weight;
        this.holes = PerlinNoise.create(new XoroshiroRandomSource(seed ^ 0xC10D5EA1L), 0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0);
        this.drift = PerlinNoise.create(new XoroshiroRandomSource(seed ^ 0xD81F7EA1L), 0, 1.0, 1.0);
        this.warpX = PerlinNoise.create(new XoroshiroRandomSource(seed ^ 0x3A4B5C6DL), 0, 1.0, 0.5, 0.25);
        this.warpZ = PerlinNoise.create(new XoroshiroRandomSource(seed ^ 0x6D5C4B3AL), 0, 1.0, 0.5, 0.25);
    }

    @Nullable
    public static CloudSea get(ResourceKey<Level> dimension) {
        return AACommonConfig.CLOUD_SEA.get() ? SEAS.get(dimension) : null;
    }

    /** The sheet whatever the config says, for the command and debugging. */
    @Nullable
    public static CloudSea terrain(ResourceKey<Level> dimension) {
        return SEAS.get(dimension);
    }

    public static void put(ResourceKey<Level> dimension, @Nullable CloudSea sea) {
        if (sea == null) {
            SEAS.remove(dimension);
        } else {
            SEAS.put(dimension, sea);
        }
    }

    /**
     * The sea's weight at a column: 1 in open sea, 0 outside it or where a landform has taken over. Read a wandering
     * distance away ({@link #WARP}), so the sheet's edge is as ragged as its holes.
     */
    public double weight(int x, int z) {
        int wx = x + (int) Math.round(WARP * warpX.getValue(x * 0.01, 0, z * 0.01));
        int wz = z + (int) Math.round(WARP * warpZ.getValue(x * 0.01, 0, z * 0.01));
        return weight.compute(new DensityFunction.SinglePointContext(wx, 0, wz));
    }

    /** Where the Cloud Reaches lie before the landforms take their share: the islands are shaped for the sheet here. */
    public double mask(int x, int z) {
        return mask.compute(new DensityFunction.SinglePointContext(x, 0, z));
    }

    public double holes(int x, int z) {
        return holes.getValue(x * 0.005, 0, z * 0.005);
    }

    public double drift(int x, int z) {
        return drift.getValue(x * 0.001, 0, z * 0.001);
    }

    /** The blocks the sheet fills in a column of this weight, as {@link CloudSeaMath#span}. */
    public boolean span(int x, int z, double weight, int[] out) {
        return CloudSeaMath.span(holes(x, z), drift(x, z), weight, out);
    }

    /** Whether a block is in the sheet's span. */
    public boolean contains(int x, int y, int z) {
        if (y < CloudSeaMath.BOTTOM || y > CloudSeaMath.TOP) {
            return false;
        }
        int[] out = new int[2];
        return span(x, z, weight(x, z), out) && y >= out[0] && y <= out[1];
    }
}
