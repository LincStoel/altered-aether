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
package com.linc.alteredaether.worldgen.camp;

import java.util.List;
import java.util.Locale;

/**
 * The camp and farm groups: which blueprints, how they sit on the ground, the grid their sites follow and the biomes
 * that may hold them. The data generator and the locate command both read this, so they cannot disagree.
 */
public enum CampSites {
    AETHER_CAMP("aether_camp", false, 2, 14, 71, 0.8f, List.of()),
    BLUE_BERRY_FARM("aether_blue_berry_farm", true, 2, 10, 72, 0.7f, List.of("aether:skyroot_meadow",
            "aether:skyroot_forest", "altered_aether:aether_plateau")),
    GLEAMBERRY_FARM("aether_gleamberry_farm", true, 2, 7, 73, 0.6f, List.of("altered_aether:skyroot_cathedral",
            "altered_aether:nurselog_hollow"));

    public final String group;
    public final boolean farm;
    public final int slope, cellChunks, salt;
    public final float chance;
    /** The biomes it may stand in; empty for every Aether biome. */
    public final List<String> biomes;

    CampSites(String group, boolean farm, int slope, int cellChunks, int salt, float chance, List<String> biomes) {
        this.group = group;
        this.farm = farm;
        this.slope = slope;
        this.cellChunks = cellChunks;
        this.salt = salt;
        this.chance = chance;
        this.biomes = biomes;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }
}
