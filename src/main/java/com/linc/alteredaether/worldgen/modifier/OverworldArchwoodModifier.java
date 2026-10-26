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
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.common.world.ModifiableBiomeInfo;

import java.util.List;

/**
 * Strips the archwood trees Ars Nouveau and Ars Elemental scatter across the overworld while
 * {@code compat.disable_overworld_archwood} is on. Runs in the REMOVE phase, after their ADD. Features are matched
 * by id rather than a holder set, so Ars Elemental's entries are inert without it. The archwood forests themselves
 * are kept out by ArsArchwoodRegionMixins.
 */
public record OverworldArchwoodModifier(HolderSet<Biome> biomes, List<ResourceLocation> features)
        implements BiomeModifier {

    public static final MapCodec<OverworldArchwoodModifier> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Biome.LIST_CODEC.fieldOf("biomes").forGetter(OverworldArchwoodModifier::biomes),
            ResourceLocation.CODEC.listOf().fieldOf("features").forGetter(OverworldArchwoodModifier::features)
    ).apply(i, OverworldArchwoodModifier::new));

    @Override
    public void modify(Holder<Biome> biome, Phase phase, ModifiableBiomeInfo.BiomeInfo.Builder builder) {
        if (phase != Phase.REMOVE || !AACommonConfig.DISABLE_OVERWORLD_ARCHWOOD.get() || !biomes.contains(biome)) {
            return;
        }
        var generation = builder.getGenerationSettings();
        for (GenerationStep.Decoration step : GenerationStep.Decoration.values()) {
            generation.getFeatures(step).removeIf(feature ->
                    feature.unwrapKey().map(key -> features.contains(key.location())).orElse(false));
        }
    }

    @Override
    public MapCodec<? extends BiomeModifier> codec() {
        return AARegistries.OVERWORLD_ARCHWOOD.get();
    }
}
