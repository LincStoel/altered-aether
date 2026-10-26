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
package com.linc.alteredaether.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.linc.alteredaether.AlteredAether;
import net.minecraft.data.PackOutput;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Spawn, exclusivity, climate and overworld-archwood biome modifiers. Optional-mod content carries a mod_loaded condition. */
public final class AABiomeModifierProvider extends JsonDataProvider {
    private static final String FOLDER = "neoforge/biome_modifier";
    private static final String IS_AETHER = "#aether:is_aether";

    public AABiomeModifierProvider(PackOutput output) {
        super(output, "biome modifiers");
    }

    @Override
    protected void generate() {
        JsonObject exclusive = new JsonObject();
        exclusive.addProperty("type", "altered_aether:aether_exclusive");
        exclusive.addProperty("aether_biomes", IS_AETHER);
        data(FOLDER, AlteredAether.id("aether_exclusive"), exclusive);

        // The basin fill runs in every Aether chunk, so a lake that strays over a biome border, ours or another
        // mod's, is still filled on both sides. Chunks with no basin nearby cost one cache lookup.
        JsonObject fill = new JsonObject();
        fill.addProperty("type", "neoforge:add_features");
        fill.addProperty("biomes", IS_AETHER);
        fill.addProperty("features", AAFeatureProvider.BASIN_FILL);
        fill.addProperty("step", "lakes");
        data(FOLDER, AlteredAether.id("basin_fill"), fill);

        // Landmarks, like basins, are built in every Aether chunk they reach, whatever its biome. The builder runs
        // before all other decoration; what dresses them runs with the surface structures.
        JsonObject landmarks = new JsonObject();
        landmarks.addProperty("type", "neoforge:add_features");
        landmarks.addProperty("biomes", IS_AETHER);
        landmarks.addProperty("features", AAFeatureProvider.LANDMARK);
        landmarks.addProperty("step", "raw_generation");
        data(FOLDER, AlteredAether.id("landmarks"), landmarks);
        // The giant trees too: their crowns, bows and roots spill over biome borders.
        JsonObject groves = new JsonObject();
        groves.addProperty("type", "neoforge:add_features");
        groves.addProperty("biomes", IS_AETHER);
        groves.addProperty("features", AAFeatureProvider.GROVE);
        groves.addProperty("step", "raw_generation");
        data(FOLDER, AlteredAether.id("groves"), groves);
        // The roots hanging under the grove islands, once the giants' own roots are in.
        JsonObject hanging = new JsonObject();
        hanging.addProperty("type", "neoforge:add_features");
        hanging.addProperty("biomes", IS_AETHER);
        hanging.addProperty("features", AAFeatureProvider.HANGING_ROOTS);
        hanging.addProperty("step", "underground_decoration");
        data(FOLDER, AlteredAether.id("grove_hanging_roots"), hanging);
        JsonObject dressing = new JsonObject();
        dressing.addProperty("type", "neoforge:add_features");
        dressing.addProperty("biomes", IS_AETHER);
        JsonArray features = new JsonArray();
        for (String feature : AAFeatureProvider.LANDMARK_DECORATION) {
            features.add(feature);
        }
        dressing.add("features", features);
        dressing.addProperty("step", "surface_structures");
        data(FOLDER, AlteredAether.id("landmark_decoration"), dressing);

        // Breezes only spawn standing on cold aerclouds (see SpawnPlacementEvents); the spawn cost keeps them rare.
        // Most Aether biomes have nothing else in the monster category, so weight barely matters there.
        spawns("breeze", "breeze", List.of(IS_AETHER), "monster", 2, 2, 3);
        spawns("naga", "naga", List.of(IS_AETHER), "monster", 10, 1, 1, "mowziesmobs");

        spawns("wilden_hunter_plateau", "wilden_hunter", List.of("altered_aether:aether_plateau"), "monster", 5, 1, 2,
                "ars_nouveau");
        spawns("wilden_hunter_flourishing", "wilden_hunter", List.of("altered_aether:flourishing_archwood_grove"),
                "monster", 2, 1, 1, "ars_nouveau");
        spawns("wilden_guardian_crags", "wilden_guardian", List.of("altered_aether:frozen_crags"), "monster", 8, 1, 2,
                "ars_nouveau");
        spawns("wilden_guardian_cascading", "wilden_guardian", List.of("altered_aether:cascading_archwood_grove"),
                "monster", 2, 1, 1, "ars_nouveau");
        spawns("wilden_stalker_vexing", "wilden_stalker", List.of("altered_aether:vexing_archwood_grove"), "monster",
                2, 1, 1, "ars_nouveau");
        spawns("wilden_stalker_yagroot", "wilden_stalker", List.of("deep_aether:yagroot_swamp"), "monster", 2, 1, 1,
                "ars_nouveau", "deep_aether");
        spawns("snuffle_crags", "snuffle", List.of("altered_aether:frozen_crags"), "creature", 14, 2, 4, "snuffles");

        familiars();

        data(FOLDER, AlteredAether.id("climate"), climate());

        // Ars Nouveau's archwood mix and Ars Elemental's flashing archwood, both added to #minecraft:is_overworld.
        // Our own Aether archwood placements live under our namespace and are untouched.
        JsonObject archwood = new JsonObject();
        archwood.addProperty("type", "altered_aether:overworld_archwood");
        archwood.addProperty("biomes", "#minecraft:is_overworld");
        JsonArray archwoods = new JsonArray();
        archwoods.add("ars_nouveau:placed_mixed_archwoods");
        archwoods.add("ars_elemental:flashing_archwood");
        archwoods.add("ars_elemental:common_flashing_archwood");
        archwood.add("features", archwoods);
        data(FOLDER, AlteredAether.id("overworld_archwood"), archwood, "ars_nouveau");
    }

    /**
     * Ars familiars spawn in their groves like passive animals, through the Aether spawn rules in
     * SpawnPlacementEvents, on top of the population placed at generation. Categories come from the entities.
     */
    private void familiars() {
        List<String> anGroves = List.of("blazing", "cascading", "flourishing", "vexing");
        spawns("familiars/starbuncle", "starbuncle",
                anGroves.stream().map(g -> "altered_aether:" + g + "_archwood_grove").toList(), null, 6, 2, 3,
                "ars_nouveau");
        spawns("familiars/starbuncle_flashing", "starbuncle", List.of("altered_aether:flashing_archwood_grove"), null,
                6, 2, 3, "ars_nouveau", "ars_elemental");
        spawns("familiars/drygmy_flourishing", "drygmy", List.of("altered_aether:flourishing_archwood_grove"), null,
                4, 1, 2, "ars_nouveau");
        spawns("familiars/drygmy_plateau", "drygmy", List.of("altered_aether:aether_plateau"), null, 2, 1, 2,
                "ars_nouveau");
        spawns("familiars/whirlisprig", "whirlisprig", List.of("altered_aether:flourishing_archwood_grove"), null,
                4, 1, 2, "ars_nouveau");
        spawns("familiars/alakarkinos", "alakarkinos", List.of("altered_aether:cascading_archwood_grove"), null,
                3, 1, 1, "ars_nouveau");
        for (String grove : anGroves) {
            spawns("familiars/" + grove + "_weald_walker", grove + "_weald_walker",
                    List.of("altered_aether:" + grove + "_archwood_grove"), null, 2, 1, 1, "ars_nouveau");
        }
        spawns("familiars/flashing_weald_walker", "flashing_weald_walker",
                List.of("altered_aether:flashing_archwood_grove"), null, 2, 1, 1, "ars_nouveau", "ars_elemental");
        spawns("familiars/flashjack", "flashjack", List.of("altered_aether:flashing_archwood_grove"), null, 5, 2, 3,
                "ars_nouveau", "ars_elemental");
        spawns("familiars/siren", "siren", List.of("altered_aether:cascading_archwood_grove"), null, 4, 1, 1,
                "ars_nouveau", "ars_elemental");
    }

    /** {@code category} may be null to use the entity's own. */
    private void spawns(String file, String mob, List<String> biomes, String category, int weight, int min, int max,
                        String... requiredMods) {
        JsonObject json = new JsonObject();
        json.addProperty("type", "altered_aether:configured_spawns");
        if (biomes.size() == 1) {
            json.addProperty("biomes", biomes.getFirst());
        } else {
            JsonArray list = new JsonArray();
            biomes.forEach(list::add);
            json.add("biomes", list);
        }
        json.addProperty("mob", mob);
        if (category != null) {
            json.addProperty("category", category);
        }
        json.addProperty("weight", weight);
        json.addProperty("min_count", min);
        json.addProperty("max_count", max);
        data(FOLDER, AlteredAether.id("spawns/" + file), json, requiredMods);
    }

    /**
     * Temperatures and downfall matched to comparable overworld biomes, replacing the 0.8 / 0.0 placeholder every
     * stock Aether and Deep Aether biome shares. Keyed by id, so Deep Aether's entries are inert without it.
     */
    private static JsonObject climate() {
        Map<String, float[]> values = new LinkedHashMap<>();
        values.put("aether:skyroot_forest", new float[]{0.65f, 0.8f});
        values.put("aether:skyroot_grove", new float[]{-0.25f, 0.8f});
        values.put("aether:skyroot_meadow", new float[]{0.45f, 0.8f});
        values.put("aether:skyroot_woodland", new float[]{0.65f, 0.8f});
        values.put("deep_aether:aerglow_forest", new float[]{0.2f, 0.8f});
        values.put("deep_aether:aerlavender_fields", new float[]{0.45f, 0.8f});
        values.put("deep_aether:blue_aerglow_forest", new float[]{0.2f, 0.8f});
        values.put("deep_aether:cloud", new float[]{0.45f, 0.5f});
        values.put("deep_aether:golden_grove", new float[]{0.5f, 0.65f});
        values.put("deep_aether:golden_heights", new float[]{0.95f, 0.3f});
        values.put("deep_aether:luminescent_forest", new float[]{0.9f, 0.9f});
        values.put("deep_aether:mystic_aerglow_forest", new float[]{0.2f, 0.8f});
        values.put("deep_aether:overgrown_cloud", new float[]{0.95f, 0.3f});
        values.put("deep_aether:yagroot_swamp", new float[]{0.75f, 0.9f});

        JsonObject climates = new JsonObject();
        values.forEach((biome, v) -> {
            JsonObject c = new JsonObject();
            c.addProperty("temperature", v[0]);
            c.addProperty("downfall", v[1]);
            climates.add(biome, c);
        });
        JsonObject json = new JsonObject();
        json.addProperty("type", "altered_aether:climate");
        json.add("climates", climates);
        return json;
    }
}
