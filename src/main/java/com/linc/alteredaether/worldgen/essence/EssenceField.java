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
package com.linc.alteredaether.worldgen.essence;

import com.linc.alteredaether.config.AACommonConfig;
import com.linc.alteredaether.worldgen.CellCache;
import com.linc.alteredaether.worldgen.basin.BasinField;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Where the tall Aether's Essence Channels hang: at most one in each {@value #CELL}-block cell, over the top of a
 * Highlands islet. A cell first rolls whether it has one at all ({@code channel_chance}), then a point it would like
 * the channel near, then looks over the middle of the cell for islets: a coarse grid of the islets' footprint noise
 * rules most columns out cheaply, the survivors are checked for an islet really standing there, the one nearest the
 * point is taken, and a fine grid round it finds the islet's highest column. A channel is never more than
 * {@value #SPREAD} blocks from its cell's middle, so two are always at least {@code CELL - 2 * SPREAD} apart.
 *
 * <p>Everything is read off the 2D functions the router's HighlandsMarker carries, never the 3D terrain, and a cell's
 * answer is a pure function of the seed and the cell, so any thread may work it out.
 */
public final class EssenceField {
    public static final int CELL = 512;
    public static final int SPREAD = 128;
    private static final long SALT = 0xE55E_1CE5_C4A7_7E15L;
    /** The coarse grid's step and the fine grid's, in blocks. */
    private static final int COARSE = 16, FINE = 4;
    /** Below this the footprint can't hold an islet anywhere (its least threshold is 0.42). */
    private static final double FOOTPRINT_MIN = 0.42;
    /** How far into an islet a channel must hang over: well inside, not at its rim. */
    private static final double PRESENCE_MIN = 1.6;
    /** How high the lens's jagged noise can lift an islet's top above {@code top}. */
    private static final double JAG = 2.4;
    private static final Map<ResourceKey<Level>, EssenceField> FIELDS = new ConcurrentHashMap<>();

    /** One channel's spot: its column, and about where the islet's top is under it. */
    public record Site(int x, int z, int top) {
    }

    private final long seed;
    private final DensityFunction footprint, presence, top;
    private final CellCache<Optional<Site>> cells = new CellCache<>(1 << 12);

    /** The three functions must be 2D; they are read at one height. */
    public EssenceField(long seed, DensityFunction footprint, DensityFunction presence, DensityFunction top) {
        this.seed = seed;
        this.footprint = footprint;
        this.presence = presence;
        this.top = top;
    }

    public static void put(ResourceKey<Level> dimension, @Nullable EssenceField field) {
        if (field == null) {
            FIELDS.remove(dimension);
        } else {
            FIELDS.put(dimension, field);
        }
    }

    @Nullable
    public static EssenceField get(ResourceKey<Level> dimension) {
        return FIELDS.get(dimension);
    }

    /** The channel in a cell, working it out if it isn't known yet. */
    public Optional<Site> site(int cx, int cz) {
        return cells.get(key(cx, cz), k -> Optional.ofNullable(find(cx, cz)));
    }

    /** The channel in a cell as far as it's known: null if the cell hasn't been worked out. Never works it out. */
    @Nullable
    public Optional<Site> siteIfKnown(int cx, int cz) {
        return cells.getIfPresent(key(cx, cz));
    }

    private static long key(int cx, int cz) {
        return ((long) cx << 32) ^ (cz & 0xFFFFFFFFL);
    }

    @Nullable
    private Site find(int cx, int cz) {
        RandomSource random = new XoroshiroRandomSource(BasinField.mix(seed ^ SALT)
                ^ BasinField.mix(cx * 0x9E3779B97F4A7C15L + cz));
        if (random.nextDouble() >= AACommonConfig.CHANNEL_CHANCE.get()) {
            return null;
        }
        int midX = cx * CELL + CELL / 2, midZ = cz * CELL + CELL / 2;
        int wantX = midX + random.nextInt(2 * SPREAD + 1) - SPREAD;
        int wantZ = midZ + random.nextInt(2 * SPREAD + 1) - SPREAD;
        int bestX = 0, bestZ = 0;
        double bestDistance = Double.MAX_VALUE;
        for (int x = midX - SPREAD; x <= midX + SPREAD; x += COARSE) {
            for (int z = midZ - SPREAD; z <= midZ + SPREAD; z += COARSE) {
                if (sample(footprint, x, z) < FOOTPRINT_MIN || sample(presence, x, z) < PRESENCE_MIN) {
                    continue;
                }
                double distance = Math.hypot(x - wantX, z - wantZ);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    bestX = x;
                    bestZ = z;
                }
            }
        }
        if (bestDistance == Double.MAX_VALUE) {
            return null;
        }
        // the islet's highest column near there, kept well in from its rim and inside the spread
        double bestTop = Double.NEGATIVE_INFINITY;
        int siteX = bestX, siteZ = bestZ;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                int x = Mth.clamp(bestX + dx * FINE, midX - SPREAD, midX + SPREAD);
                int z = Mth.clamp(bestZ + dz * FINE, midZ - SPREAD, midZ + SPREAD);
                if (sample(presence, x, z) < 1.0) {
                    continue;
                }
                double height = sample(top, x, z);
                if (height > bestTop) {
                    bestTop = height;
                    siteX = x;
                    siteZ = z;
                }
            }
        }
        if (bestTop == Double.NEGATIVE_INFINITY) {
            bestTop = sample(top, bestX, bestZ);
        }
        return new Site(siteX, siteZ, Mth.ceil(bestTop + JAG));
    }

    private static double sample(DensityFunction function, int x, int z) {
        return function.compute(new DensityFunction.SinglePointContext(x, 200, z));
    }
}
