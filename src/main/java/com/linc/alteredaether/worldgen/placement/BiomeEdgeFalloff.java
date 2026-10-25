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
package com.linc.alteredaether.worldgen.placement;

import com.linc.alteredaether.registry.AARegistries;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;

import java.util.stream.Stream;

/**
 * Thins a feature out toward the edge of its biome, so a grove's trees and ground cover fade into the neighbouring
 * biome instead of stopping at a line. A position is kept with a probability that rises from {@code min_keep} at the
 * border to 1 at {@code distance} blocks inside it. The border is found by sampling the biome on two rings around the
 * position, which is also why {@code distance} stops at 16: further out may lie beyond the chunks a feature can see.
 */
public class BiomeEdgeFalloff extends PlacementModifier {
    public static final MapCodec<BiomeEdgeFalloff> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.intRange(2, 16).optionalFieldOf("distance", 12).forGetter(f -> f.distance),
            Codec.floatRange(0, 1).optionalFieldOf("min_keep", 0.15f).forGetter(f -> f.minKeep)
    ).apply(i, BiomeEdgeFalloff::new));

    private static final int SAMPLES = 8;

    private final int distance;
    private final float minKeep;

    public BiomeEdgeFalloff(int distance, float minKeep) {
        this.distance = distance;
        this.minKeep = minKeep;
    }

    @Override
    public Stream<BlockPos> getPositions(PlacementContext context, RandomSource random, BlockPos pos) {
        Holder<Biome> here = context.level.getBiome(pos);
        double nearest = distance;
        for (int ring = 1; ring <= 2; ring++) {
            double radius = distance * ring / 2.0;
            for (int s = 0; s < SAMPLES; s++) {
                double angle = s * Math.PI * 2 / SAMPLES;
                BlockPos sample = pos.offset((int) Math.round(Math.cos(angle) * radius), 0,
                        (int) Math.round(Math.sin(angle) * radius));
                if (!context.level.getBiome(sample).equals(here)) {
                    nearest = Math.min(nearest, radius);
                }
            }
        }
        double keep = minKeep + (1 - minKeep) * (nearest / distance);
        return random.nextDouble() < keep ? Stream.of(pos) : Stream.empty();
    }

    @Override
    public PlacementModifierType<?> type() {
        return AARegistries.BIOME_EDGE_FALLOFF.get();
    }
}
