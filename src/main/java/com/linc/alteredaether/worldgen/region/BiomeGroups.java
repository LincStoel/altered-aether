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

import java.util.Locale;
import java.util.Set;
import java.util.function.ToIntFunction;

/**
 * Sorts biomes into classes for {@code BiomeEdgeField}, which measures how far a point lies from the nearest biome of
 * another class. Each classification is one way of carving up the dimension.
 */
public enum BiomeGroups {
    /**
     * The landmasses: Peaks (the crags, tempest, rift and the tundra round them), Woodland (the plateau, the giant-tree biomes, the groves and the flower bowl), the Dunes on their
     * own, and Other (the Aether's own biomes, Deep Aether's, and any other mod's).
     */
    GROUP(BiomeGroups::group),
    /** The snow tundra against everything else; its drifts fade out toward the tundra's edge. */
    TUNDRA(biome -> biome.equals(BiomeTable.TUNDRA) ? 1 : 0),
    /** The flower bowl against everything else; its rim follows the distance in from the bowl's edge. */
    BOWL(biome -> biome.equals(BiomeTable.BOWL) ? 1 : 0),
    /**
     * Where the Highlands may float: over the plateau, the flower bowl, and every biome that isn't ours (1), but not
     * the peaks, dunes, giant-tree biomes or groves (0).
     */
    HIGHLANDS(biome -> !biome.startsWith("altered_aether:") || biome.equals(BiomeTable.PLATEAU)
            || biome.equals(BiomeTable.BOWL) ? 1 : 0);

    public static final int OTHER = 0, PEAKS = 1, WOODLAND = 2, DUNES = 3;

    private static final Set<String> PEAK_BIOMES = Set.of(BiomeTable.CRAGS, BiomeTable.TEMPEST, BiomeTable.RIFT,
            BiomeTable.TUNDRA);
    private static final Set<String> WOODLAND_BIOMES = Set.of(BiomeTable.PLATEAU, BiomeTable.CATHEDRAL,
            BiomeTable.NURSELOG, BiomeTable.SHROUDED, BiomeTable.BOWL, BiomeTable.BLAZING, BiomeTable.CASCADING,
            BiomeTable.FLASHING, BiomeTable.FLOURISHING, BiomeTable.VEXING);

    private final ToIntFunction<String> classifier;

    BiomeGroups(ToIntFunction<String> classifier) {
        this.classifier = classifier;
    }

    /** The class of a biome, by its id. */
    public int classify(String biome) {
        return classifier.applyAsInt(biome);
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static int group(String biome) {
        if (PEAK_BIOMES.contains(biome)) {
            return PEAKS;
        }
        if (WOODLAND_BIOMES.contains(biome)) {
            return WOODLAND;
        }
        return biome.equals(BiomeTable.DUNES) ? DUNES : OTHER;
    }
}
