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
package com.linc.alteredaether.worldgen.basin;

import com.linc.alteredaether.AlteredAether;
import com.linc.alteredaether.config.AACommonConfig;

import java.util.Set;

/**
 * The kinds of water body, which biomes hold them, and their size ranges in blocks. {@code chance} is the
 * probability that a grid cell over a matching biome holds one, before the config's rarity multiplier.
 */
public enum BasinType {
    POOL("pool", 0.55, 5, 9, 3, 5, Fluid.WATER, "aether_plateau"),
    DEEP_LAKE("deep_lake", 0.12, 10, 15, 8, 12, Fluid.WATER, "aether_plateau"),
    CASCADING_LAKE("cascading_lake", 0.7, 7, 12, 4, 7, Fluid.WATER, "cascading_archwood_grove"),
    FROZEN_POOL("frozen_pool", 0.6, 4, 7, 3, 5, Fluid.FROZEN, "frozen_crags"),
    POISON_POOL("poison_pool", 0.5, 4, 8, 3, 5, Fluid.POISON, "vexing_archwood_grove"),
    /** The Sunken Atoll's lagoon, inside the ring island the atoll density function builds. */
    ATOLL_LAGOON("atoll_lagoon", 0.9, 30, 42, 9, 13, Fluid.WATER, "sunken_atoll");

    public enum Fluid {
        WATER,
        /** Water under a sheet of ice. */
        FROZEN,
        /** Deep Aether's poison where it exists, water otherwise. */
        POISON
    }

    private final String configName;
    private final double chance;
    final int minRadius, maxRadius, minDepth, maxDepth;
    private final Fluid fluid;
    private final Set<String> biomes;

    BasinType(String configName, double chance, int minRadius, int maxRadius, int minDepth, int maxDepth, Fluid fluid,
              String... biomes) {
        this.configName = configName;
        this.chance = chance;
        this.minRadius = minRadius;
        this.maxRadius = maxRadius;
        this.minDepth = minDepth;
        this.maxDepth = maxDepth;
        this.fluid = fluid;
        this.biomes = java.util.Arrays.stream(biomes).map(b -> AlteredAether.MOD_ID + ":" + b)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    public Fluid fluid() {
        return fluid;
    }

    boolean suits(String biome) {
        return biomes.contains(biome);
    }

    /** This type's chance per cell, with the config's enable switch and rarity multiplier applied. */
    double chance() {
        if (!AACommonConfig.BASIN_ENABLED.get(configName).get()) {
            return 0;
        }
        return chance / AACommonConfig.BASIN_RARITY.get(configName).get();
    }
}
