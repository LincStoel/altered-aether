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

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every point in climate space must belong to exactly one entry, for every combination of optional biomes. A gap
 * falls to the nearest box (a biome where its terrain isn't); an overlap is a seed-dependent coin flip.
 */
class BiomeTablePartitionTest {
    private static final List<String> OPTIONAL = List.of(
            BiomeTable.PLATEAU, BiomeTable.CRAGS, BiomeTable.TEMPEST, BiomeTable.RIFT, BiomeTable.DUNES,
            BiomeTable.CATHEDRAL, BiomeTable.NURSELOG, BiomeTable.BLAZING, BiomeTable.CASCADING, BiomeTable.FLASHING, BiomeTable.FLOURISHING,
            BiomeTable.VEXING);
    private static final int SAMPLES = 40_000;

    @Test
    void everyCombinationIsAStrictPartition() {
        for (int mask = 0; mask < 1 << OPTIONAL.size(); mask++) {
            Set<String> present = new HashSet<>();
            for (int i = 0; i < OPTIONAL.size(); i++) {
                if ((mask & 1 << i) != 0) {
                    present.add(OPTIONAL.get(i));
                }
            }
            // The full set and the empty set get a denser check; the rest a lighter one.
            int samples = mask == 0 || mask == (1 << OPTIONAL.size()) - 1 ? SAMPLES * 10 : SAMPLES / 40;
            assertPartition(present, samples, mask);
        }
    }

    @Test
    void absentBiomesAreNeverPlaced() {
        Set<String> present = Set.of(BiomeTable.PLATEAU, BiomeTable.CRAGS);
        for (BiomeTable.Entry entry : BiomeTable.build(present)) {
            assertTrue(entry.biome().startsWith("aether:") || present.contains(entry.biome()), entry.biome());
        }
    }

    private static void assertPartition(Set<String> present, int samples, long seed) {
        List<BiomeTable.Entry> table = BiomeTable.build(present);
        Random random = new Random(seed);
        for (int n = 0; n < samples; n++) {
            double t = axis(random), h = axis(random), c = axis(random), e = axis(random), w = axis(random);
            List<String> owners = new ArrayList<>(2);
            for (BiomeTable.Entry entry : table) {
                if (entry.contains(t, h, c, e, w)) {
                    owners.add(entry.biome());
                }
            }
            assertEquals(1, owners.size(), () -> "present=" + present + " point=(" + t + ", " + h + ", " + c + ", "
                    + e + ", " + w + ") owners=" + owners);
        }
    }

    /** Mostly inside [-1, 1] like the real noises, with some tail past it. */
    private static double axis(Random random) {
        return Math.max(-1.99, Math.min(1.99, random.nextGaussian() * 0.6));
    }
}
