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
package com.linc.alteredaether.worldgen.landmark;

import com.linc.alteredaether.AlteredAether;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The rare landmarks, each one-in-many and found by exploring. Each grid type sits on its own jittered grid of
 * {@code cell} blocks: a cell holds at most one, kept {@code reach} blocks clear of the cell's edge so neighbours never
 * meet, and {@code chance} is the probability that a cell over its biomes holds one, before the config's rarity
 * multiplier. The double atoll is a kind of Sunken Atoll and comes from the atoll grid instead.
 */
public enum LandmarkType {
    /** A crown of storm-black needles far taller than the peaks, ringed by copper crystals. */
    TEMPEST_SPIRES(1024, 1.0, 72, "tempest_peaks"),
    /** One colossal twisted spire of ice in the crags, with its shards around it. */
    FROZEN_SPIRE(1024, 0.85, 64, "frozen_crags"),
    /** A staircase of pools stepping down to an island's edge, each spilling into the next, the last into the sky. */
    GRAND_CASCADE(1024, 0.5, 128, "cascading_archwood_grove", "aether_plateau"),
    /** A canyon across the rift: its slabs grown to giants, one side heaved up over the other. */
    GREAT_FRACTURE(1536, 1.0, 0, "shattered_rift"),
    /** A Sunken Atoll with a second ring in its lagoon and an islet at its heart. */
    DOUBLE_ATOLL(0, 0, 0, "sunken_atoll");

    final int cell;
    final double chance;
    final int reach;
    private final Set<String> biomes;

    LandmarkType(int cell, double chance, int reach, String... biomes) {
        this.cell = cell;
        this.chance = chance;
        this.reach = reach;
        this.biomes = Arrays.stream(biomes).map(b -> AlteredAether.MOD_ID + ":" + b)
                .collect(Collectors.toUnmodifiableSet());
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    boolean suits(String biome) {
        return biomes.contains(biome);
    }

    /** Placed on a grid of its own, rather than coming from the atolls. */
    boolean gridded() {
        return this != DOUBLE_ATOLL;
    }
}
