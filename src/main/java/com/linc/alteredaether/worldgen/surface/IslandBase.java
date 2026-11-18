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
package com.linc.alteredaether.worldgen.surface;

import com.linc.alteredaether.worldgen.TerrainProbe;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.SurfaceRules;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * The base of an island: the lowest {@code depth} blocks above the island's true underside, plus up to
 * {@code variation} more where a broad noise rises, and never more than {@code max_fraction} of the island's
 * thickness. Islands thinner than {@code min_thickness} have no base at all.
 *
 * <p>Unlike a ceiling {@code stone_depth}, a cave's roof is not an underside: where the air under a run of rock was
 * rock before the caves were cut (read off the world's pre-cave terrain probe), the runs above and below the cave are
 * one island, measured from the lower run's bottom. Without a probe (a world with no caves of ours) every gap counts as
 * open sky.
 */
public record IslandBase(int depth, int variation, double maxFraction, int minThickness)
        implements SurfaceRules.ConditionSource {
    public static final MapCodec<IslandBase> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.intRange(1, 128).fieldOf("depth").forGetter(IslandBase::depth),
            Codec.intRange(0, 64).optionalFieldOf("variation", 0).forGetter(IslandBase::variation),
            Codec.doubleRange(0, 1).optionalFieldOf("max_fraction", 1.0).forGetter(IslandBase::maxFraction),
            Codec.intRange(1, 512).optionalFieldOf("min_thickness", 1).forGetter(IslandBase::minThickness)
    ).apply(i, IslandBase::new));
    public static final KeyDispatchDataCodec<IslandBase> CODEC = KeyDispatchDataCodec.of(MAP_CODEC);

    /** Breadth of the noise that varies the base's depth, and of the finer one that roughens its top. */
    private static final double BROAD = 1 / 40.0, FINE = 1 / 7.0;
    /** Blocks the fine noise moves the top of the base up or down. */
    private static final double ROUGH = 2.0;
    /** Slack for rounding in the noise's interpolation when deciding without it. */
    private static final double MARGIN = 1e-6;

    /** Each world's pre-cave terrain, keyed on its random state (one per level); see RegionBinding. */
    private static final Map<RandomState, TerrainProbe> PROBES = Collections.synchronizedMap(new WeakHashMap<>());

    /** The pre-cave terrain bound for a world, or null; for debugging. */
    @Nullable
    public static TerrainProbe probe(RandomState state) {
        return PROBES.get(state);
    }

    public static void bind(RandomState state, @Nullable TerrainProbe probe) {
        if (probe == null) {
            PROBES.remove(state);
        } else {
            PROBES.put(state, probe);
        }
    }

    @Override
    public SurfaceRules.Condition apply(SurfaceRules.Context context) {
        TerrainProbe probe = PROBES.get(context.randomState);
        return new SurfaceRules.Condition() {
            private long column = Long.MIN_VALUE;
            /** Islands in this column as (bottom, top) pairs, lowest first, caves closed up. */
            private int[] islands = new int[0];
            private double broad;

            @Override
            public boolean test() {
                ChunkAccess chunk = context.chunk;
                int x = context.blockX, y = context.blockY, z = context.blockZ;
                long key = BlockPos.asLong(x, 0, z);
                if (key != column) {
                    column = key;
                    islands = closeCaves(IslandPosition.scan(chunk, x, z), probe, x, z);
                    broad = 0.5 + 0.5 * LatticeNoise.value(x * BROAD, 0, z * BROAD, 0x4ad1);
                }
                for (int i = 0; i < islands.length; i += 2) {
                    int bottom = islands[i], top = islands[i + 1];
                    if (y >= bottom && y <= top) {
                        int thickness = top - bottom + 1;
                        if (thickness < minThickness) {
                            return false;
                        }
                        double limit = Math.min(depth + variation * broad, maxFraction * thickness);
                        // The fine noise lies within ±1, so it can only matter within ROUGH of the limit.
                        int above = y - bottom;
                        if (above < limit - ROUGH - MARGIN) {
                            return true;
                        }
                        if (above >= limit + ROUGH + MARGIN) {
                            return false;
                        }
                        double rough = ROUGH * LatticeNoise.value(x * FINE, y * FINE, z * FINE, 0x4ad2);
                        return above < limit + rough;
                    }
                }
                return false;
            }
        };
    }

    /**
     * Joins runs split only by a cave: where the middle of the gap between two runs was solid before the caves were
     * cut. A gap of open sky stays a gap.
     */
    static int[] closeCaves(int[] runs, @Nullable TerrainProbe probe, int x, int z) {
        if (probe == null || runs.length <= 2) {
            return runs;
        }
        int[] joined = new int[runs.length];
        int count = 0;
        joined[count++] = runs[0];
        joined[count++] = runs[1];
        for (int i = 2; i < runs.length; i += 2) {
            int gapLow = joined[count - 1] + 1, gapHigh = runs[i] - 1;
            if (probe.solid(x, (gapLow + gapHigh) >> 1, z)) {
                joined[count - 1] = runs[i + 1];
            } else {
                joined[count++] = runs[i];
                joined[count++] = runs[i + 1];
            }
        }
        return java.util.Arrays.copyOf(joined, count);
    }

    @Override
    public KeyDispatchDataCodec<? extends SurfaceRules.ConditionSource> codec() {
        return CODEC;
    }
}
