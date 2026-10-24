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
package com.linc.alteredaether.worldgen.region;

import com.linc.alteredaether.AlteredAether;
import com.linc.alteredaether.config.AACommonConfig;
import com.linc.alteredaether.worldgen.AABiomes;
import com.mojang.datafixers.util.Pair;
import io.github.razordevs.aeroblender.aether.AetherRegionType;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.neoforged.fml.ModList;
import terrablender.api.Region;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

/** Altered Aether's TerraBlender region in the Aether, built from {@link BiomeTable}. */
public final class AlteredRegion extends Region {
    public static final ResourceLocation NAME = AlteredAether.id("altered_aether");

    public AlteredRegion(int weight) {
        super(NAME, AetherRegionType.THE_AETHER, weight);
    }

    @Override
    public void addBiomes(Registry<Biome> registry,
                          Consumer<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> mapper) {
        Set<String> present = presentBiomes(registry);
        for (BiomeTable.Entry entry : BiomeTable.build(present)) {
            ResourceKey<Biome> key = ResourceKey.create(Registries.BIOME, ResourceLocation.parse(entry.biome()));
            Climate.ParameterPoint point = new Climate.ParameterPoint(
                    span(entry.temperature()), span(entry.humidity()), span(entry.continentalness()),
                    span(entry.erosion()), Climate.Parameter.span(BiomeTable.LOW, BiomeTable.HIGH),
                    span(entry.weirdness()), 0L);
            mapper.accept(Pair.of(point, key));
        }
    }

    /**
     * Our biomes that are enabled in config, have their required mods, and actually loaded (a biome whose data was
     * skipped by a load condition must never be placed).
     */
    private static Set<String> presentBiomes(Registry<Biome> registry) {
        Set<String> present = new HashSet<>();
        for (AABiomes.Entry biome : AABiomes.ALL) {
            boolean modsLoaded = biome.requiredMods().stream()
                    .allMatch(id -> ModList.get().isLoaded(id) && AACommonConfig.compat(id));
            if (modsLoaded && AACommonConfig.BIOME_ENABLED.get(biome.name()).get() && registry.containsKey(biome.key())) {
                present.add(biome.key().location().toString());
            }
        }
        ResourceLocation sacredLands = ResourceLocation.parse(BiomeTable.SACRED_LANDS);
        if (AACommonConfig.PLACE_SACRED_LANDS.get() && AACommonConfig.compat("deep_aether")
                && registry.containsKey(sacredLands)) {
            present.add(BiomeTable.SACRED_LANDS);
        }
        return present;
    }

    private static Climate.Parameter span(BiomeTable.Span span) {
        return Climate.Parameter.span(span.min(), span.max());
    }
}
