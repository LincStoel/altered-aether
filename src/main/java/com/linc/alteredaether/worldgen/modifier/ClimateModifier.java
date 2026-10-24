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
package com.linc.alteredaether.worldgen.modifier;

import com.linc.alteredaether.config.AACommonConfig;
import com.linc.alteredaether.registry.AARegistries;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.common.world.ModifiableBiomeInfo;

import java.util.Map;

/**
 * Replaces the placeholder temperature/downfall the stock Aether and Deep Aether biomes share (0.8 / 0.0) with values
 * matched to comparable overworld biomes. Keyed by biome id rather than a holder set, so entries for biomes that
 * don't exist (Deep Aether not installed) are simply never matched.
 */
public record ClimateModifier(Map<ResourceLocation, Climate> climates) implements BiomeModifier {

    public record Climate(float temperature, float downfall) {
        public static final Codec<Climate> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.FLOAT.fieldOf("temperature").forGetter(Climate::temperature),
                Codec.FLOAT.fieldOf("downfall").forGetter(Climate::downfall)
        ).apply(i, Climate::new));
    }

    public static final MapCodec<ClimateModifier> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.unboundedMap(ResourceLocation.CODEC, Climate.CODEC).fieldOf("climates").forGetter(ClimateModifier::climates)
    ).apply(i, ClimateModifier::new));

    @Override
    public void modify(Holder<Biome> biome, Phase phase, ModifiableBiomeInfo.BiomeInfo.Builder builder) {
        if (phase != Phase.MODIFY || !AACommonConfig.CLIMATE_FIX.get()) {
            return;
        }
        biome.unwrapKey().map(key -> climates.get(key.location())).ifPresent(climate -> {
            builder.getClimateSettings().setTemperature(climate.temperature());
            builder.getClimateSettings().setDownfall(climate.downfall());
        });
    }

    @Override
    public MapCodec<? extends BiomeModifier> codec() {
        return AARegistries.CLIMATE.get();
    }
}
