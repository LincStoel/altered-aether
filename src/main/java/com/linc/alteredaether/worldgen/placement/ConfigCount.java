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

import com.linc.alteredaether.config.AACommonConfig;
import com.linc.alteredaether.registry.AARegistries;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.stream.Stream;

/**
 * {@code count} copies of each position, times a multiplier from the common config: {@code waterfall_frequency}, or
 * one of the {@code [features]} density families. A fractional count rounds up or down at random, so 0.3 places a copy
 * in about three chunks out of ten.
 */
public class ConfigCount extends PlacementModifier {
    public static final MapCodec<ConfigCount> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.floatRange(0, 256).fieldOf("count").forGetter(c -> c.count),
            Codec.STRING.fieldOf("key").forGetter(c -> c.key)
    ).apply(i, ConfigCount::new));

    private final float count;
    private final String key;

    public ConfigCount(float count, String key) {
        this.count = count;
        this.key = key;
    }

    private double multiplier() {
        ModConfigSpec.DoubleValue value = key.equals("waterfall_frequency")
                ? AACommonConfig.WATERFALL_FREQUENCY : AACommonConfig.FEATURE_DENSITY.get(key);
        return value == null ? 1 : value.get();
    }

    @Override
    public Stream<BlockPos> getPositions(PlacementContext context, RandomSource random, BlockPos pos) {
        double expected = count * multiplier();
        int copies = (int) expected;
        if (random.nextDouble() < expected - copies) {
            copies++;
        }
        return Stream.generate(() -> pos).limit(copies);
    }

    @Override
    public PlacementModifierType<?> type() {
        return AARegistries.CONFIG_COUNT.get();
    }
}
