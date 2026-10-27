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
package com.linc.alteredaether.worldgen;

import com.linc.alteredaether.AlteredAether;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;

import java.util.List;

/** The biomes this mod adds, and which optional mods each one needs. */
public final class AABiomes {
    public static final ResourceKey<Biome> AETHER_PLATEAU = key("aether_plateau");
    public static final ResourceKey<Biome> FROZEN_CRAGS = key("frozen_crags");
    public static final ResourceKey<Biome> TEMPEST_PEAKS = key("tempest_peaks");
    public static final ResourceKey<Biome> SHATTERED_RIFT = key("shattered_rift");
    public static final ResourceKey<Biome> WINDSWEPT_DUNES = key("windswept_dunes");
    public static final ResourceKey<Biome> SKYROOT_CATHEDRAL = key("skyroot_cathedral");
    public static final ResourceKey<Biome> NURSELOG_HOLLOW = key("nurselog_hollow");
    public static final ResourceKey<Biome> BLAZING_ARCHWOOD_GROVE = key("blazing_archwood_grove");
    public static final ResourceKey<Biome> CASCADING_ARCHWOOD_GROVE = key("cascading_archwood_grove");
    public static final ResourceKey<Biome> FLASHING_ARCHWOOD_GROVE = key("flashing_archwood_grove");
    public static final ResourceKey<Biome> FLOURISHING_ARCHWOOD_GROVE = key("flourishing_archwood_grove");
    public static final ResourceKey<Biome> VEXING_ARCHWOOD_GROVE = key("vexing_archwood_grove");

    /**
     * @param key          the biome
     * @param requiredMods mods that must be loaded for the biome to exist at all
     */
    public record Entry(ResourceKey<Biome> key, List<String> requiredMods) {
        public String name() {
            return key.location().getPath();
        }
    }

    public static final List<Entry> ALL = List.of(
            new Entry(AETHER_PLATEAU, List.of()),
            new Entry(FROZEN_CRAGS, List.of()),
            new Entry(TEMPEST_PEAKS, List.of()),
            new Entry(SHATTERED_RIFT, List.of()),
            new Entry(WINDSWEPT_DUNES, List.of()),
            new Entry(SKYROOT_CATHEDRAL, List.of()),
            new Entry(NURSELOG_HOLLOW, List.of()),
            new Entry(BLAZING_ARCHWOOD_GROVE, List.of("ars_nouveau")),
            new Entry(CASCADING_ARCHWOOD_GROVE, List.of("ars_nouveau")),
            new Entry(FLASHING_ARCHWOOD_GROVE, List.of("ars_nouveau", "ars_elemental")),
            new Entry(FLOURISHING_ARCHWOOD_GROVE, List.of("ars_nouveau")),
            new Entry(VEXING_ARCHWOOD_GROVE, List.of("ars_nouveau")));

    private static ResourceKey<Biome> key(String name) {
        return ResourceKey.create(Registries.BIOME, AlteredAether.id(name));
    }

    private AABiomes() {
    }
}
