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
package com.linc.alteredaether.entity.golem;

import com.linc.alteredaether.entity.golem.group.MaterialTokens;
import com.linc.alteredaether.entity.golem.group.RecipeGraph;
import com.linc.alteredaether.entity.golem.group.RecipeGraph.Recipe;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Materials read from names and from recipe chains. */
class MaterialTest {
    private static final Set<String> WOODS = Set.of("oak", "dark_oak", "spruce", "skyroot", "crimson");

    @Test
    void namesSayTheMaterial() {
        assertEquals("spruce", MaterialTokens.material("stripped_spruce_log", WOODS));
        assertEquals("spruce", MaterialTokens.material("hollow_spruce_log", WOODS));
        assertEquals("spruce", MaterialTokens.material("spruce_fence_gate", WOODS));
        assertEquals("dark_oak", MaterialTokens.material("dark_oak_stairs", WOODS), "the longest wood type wins");
        assertEquals("andesite", MaterialTokens.material("polished_andesite_wall", WOODS));
        assertEquals("copper", MaterialTokens.material("waxed_weathered_cut_copper_slab", WOODS));
        assertEquals("stone", MaterialTokens.material("stone_bricks", WOODS));
        assertEquals("enchanted_gravitite", MaterialTokens.material("enchanted_gravitite", WOODS));
        assertEquals("cloak", MaterialTokens.material("cloak", WOODS), "oak only as a whole word");
    }

    private static List<List<String>> times(int n, List<String> options) {
        List<List<String>> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            out.add(options);
        }
        return out;
    }

    private static List<List<String>> plus(List<List<String>> a, List<List<String>> b) {
        List<List<String>> out = new ArrayList<>(a);
        out.addAll(b);
        return out;
    }

    private static final List<String> SPRUCE_LOGS = List.of("spruce_log", "spruce_wood", "stripped_spruce_log",
            "stripped_spruce_wood");
    private static final List<String> PLANKS = List.of("oak_planks", "spruce_planks", "birch_planks", "jungle_planks",
            "acacia_planks", "dark_oak_planks");

    private static RecipeGraph graph() {
        return new RecipeGraph(List.of(
                new Recipe("spruce_planks", false, "spruce_planks", times(1, SPRUCE_LOGS)),
                new Recipe("spruce_stairs", false, "spruce_stairs", times(6, List.of("spruce_planks"))),
                new Recipe("spruce_fence", false, "spruce_fence",
                        plus(times(4, List.of("spruce_planks")), times(2, List.of("stick")))),
                new Recipe("hollow_spruce_log", false, "hollow_spruce_log", times(4, List.of("spruce_log"))),
                new Recipe("andesite_alloy", false, "andesite_alloy",
                        plus(times(2, List.of("andesite")), times(2, List.of("iron_nugget")))),
                new Recipe("chest", false, "chest", times(8, PLANKS)),
                new Recipe("iron_block", false, "iron_block", times(9, List.of("iron_ingot"))),
                new Recipe("iron_ingot_from_block", false, "iron_ingot", times(1, List.of("iron_block"))),
                new Recipe("polished_andesite", false, "polished_andesite", times(4, List.of("diorite"))),
                new Recipe("polished_andesite_from_stonecutting", true, "polished_andesite",
                        times(1, List.of("andesite")))));
    }

    @Test
    void recipeChainsLeadBackToTheMaterial() {
        RecipeGraph graph = graph();
        assertEquals("spruce_log", graph.root("spruce_stairs"));
        assertEquals("spruce_log", graph.root("spruce_fence"), "the planks are the majority, not the sticks");
        assertEquals("spruce_log", graph.root("hollow_spruce_log"));
        assertEquals(List.of("spruce_stairs", "spruce_planks", "spruce_log"), graph.chain("spruce_stairs"));
    }

    @Test
    void mixedOrVagueRecipesMakeNothingInParticular() {
        RecipeGraph graph = graph();
        assertNull(graph.parent("andesite_alloy"), "half andesite, half nuggets");
        assertNull(graph.parent("chest"), "any planks at all");
        assertEquals("chest", graph.root("chest"));
    }

    @Test
    void stonecuttingCountsFirstAndCyclesShareARoot() {
        RecipeGraph graph = graph();
        assertEquals("andesite", graph.parent("polished_andesite"));
        assertEquals("iron_block", graph.root("iron_ingot"));
        assertEquals("iron_block", graph.root("iron_block"));
    }
}
