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
import com.linc.alteredaether.advancement.LandmarkVisits;
import com.linc.alteredaether.loot.RumourMapFunction;
import com.linc.alteredaether.worldgen.AABiomes;
import com.linc.alteredaether.worldgen.landmark.LandmarkType;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The Altered Aether advancement tab. Each biome is an advancement of its own, and the landmarks it holds hang off it,
 * so the tab reads as a map of what there is to find. Rumour maps lead on to visiting every kind of landmark.
 */
public final class AAAdvancementProvider extends JsonDataProvider {
    private static final String ROOT = "root";

    /** One advancement: its file, the mods it needs, and its English title and description. */
    private record Built(String name, JsonObject json, String[] requiredMods, String title, String description) {
    }

    public AAAdvancementProvider(PackOutput output) {
        super(output, "advancements");
    }

    @Override
    protected void generate() {
        for (Built built : build()) {
            data("advancement", AlteredAether.id(built.name), built.json, built.requiredMods);
        }
    }

    /** Adds every advancement's title and description to the language file. */
    static void language(Map<String, String> lang) {
        for (Built built : build()) {
            lang.put(key(built.name) + ".title", built.title);
            lang.put(key(built.name) + ".description", built.description);
        }
    }

    private static List<Built> build() {
        List<Built> all = new ArrayList<>();
        JsonObject enter = criterion("minecraft:changed_dimension");
        enter.getAsJsonObject("conditions").addProperty("to", "aether:the_aether");
        advancement(all, ROOT, null, "altered_aether:ambrosium_campfire", "task", 0,
                "Altered Aether", "The Aether is wider than it looks", Map.of("enter_aether", enter));

        biome(all, "frozen_crags", AABiomes.FROZEN_CRAGS, "aether:icestone", "Thin Air", "Climb into the Frozen Crags");
        landmark(all, "frozen_spire", "frozen_crags", LandmarkType.FROZEN_SPIRE.id(), "minecraft:blue_ice",
                "Spire of Ice", "Find a frozen spire twisting up out of the crags");
        biome(all, "tempest_peaks", AABiomes.TEMPEST_PEAKS, "minecraft:oxidized_copper", "Lightning Country",
                "Brave the Tempest Peaks");
        // Awarded in code by TempestStorms, when a bolt drawn by a player's overworld metal hits them.
        advancement(all, "thunderstruck", "tempest_peaks", "minecraft:iron_chestplate", "task", 0, "Thunderstruck!",
                "Attract lightning with your overworld metal equipment",
                Map.of("struck", criterion("minecraft:impossible")));
        landmark(all, "tempest_crown", "tempest_peaks", LandmarkType.TEMPEST_SPIRES.id(), "minecraft:lightning_rod",
                "The Storm's Crown", "Stand among the black needles of a tempest crown");
        biome(all, "shattered_rift", AABiomes.SHATTERED_RIFT, "aether:holystone", "Mind the Gap",
                "Cross into the Shattered Rift");
        landmark(all, "great_fracture", "shattered_rift", LandmarkType.GREAT_FRACTURE.id(), "aether:mossy_holystone",
                "The Great Fracture", "Find the canyon where the rift heaved one side over the other");
        biome(all, "windswept_dunes", AABiomes.WINDSWEPT_DUNES, "aether:quicksoil", "Sky Sands",
                "Walk the Windswept Dunes");
        biome(all, "aether_plateau", AABiomes.AETHER_PLATEAU, "aether:aether_grass_block", "High Ground",
                "Reach the Aether Plateau");
        landmark(all, "grand_cascade", "aether_plateau", LandmarkType.GRAND_CASCADE.id(), "aether:skyroot_water_bucket",
                "The Grand Cascade", "Follow a staircase of pools to where it spills into the sky");
        biome(all, "skyroot_cathedral", AABiomes.SKYROOT_CATHEDRAL, "aether:skyroot_sapling", "Cathedral of Skyroot",
                "Walk among the giants of the Skyroot Cathedral");
        landmark(all, "grandfather", "skyroot_cathedral", LandmarkType.GRANDFATHER.id(), "aether:golden_oak_log",
                "The Grandfather", "Find the oldest tree in the Aether");
        landmark(all, "village_tree", "skyroot_cathedral", LandmarkType.VILLAGE_TREE.id(), "aether:skyroot_bed",
                "Long Gone", "Find a village tree its people left behind");
        biome(all, "nurselog_hollow", AABiomes.NURSELOG_HOLLOW, "aether:skyroot_log", "Fallen Elders",
                "Wander the Nurselog Hollow");
        // either cache counts, the full one or the leftovers
        JsonObject cache = criterion("minecraft:player_generates_container_loot");
        cache.getAsJsonObject("conditions").addProperty("loot_table", AlteredAether.MOD_ID + ":chests/grove/stump");
        JsonObject poorCache = criterion("minecraft:player_generates_container_loot");
        poorCache.getAsJsonObject("conditions").addProperty("loot_table",
                AlteredAether.MOD_ID + ":chests/grove/stump_poor");
        advancement(all, "stump_cache", "nurselog_hollow", "aether:golden_amber", "task", 0,
                "Buried in the Stump", "Dig up the cache hidden in a colossal stump",
                Map.of("stump", cache, "stump_poor", poorCache));
        landmark(all, "atoll", ROOT, LandmarkVisits.ATOLL, "minecraft:brain_coral", "Lagoon in the Sky",
                "Find an atoll and its lagoon");
        landmark(all, "double_atoll", "atoll", LandmarkType.DOUBLE_ATOLL.id(), "minecraft:heart_of_the_sea",
                "Ring Within a Ring", "Find a double atoll standing alone in open sky");

        Map<String, JsonObject> groves = new LinkedHashMap<>();
        for (ResourceKey<Biome> grove : List.of(AABiomes.BLAZING_ARCHWOOD_GROVE, AABiomes.CASCADING_ARCHWOOD_GROVE,
                AABiomes.FLOURISHING_ARCHWOOD_GROVE, AABiomes.VEXING_ARCHWOOD_GROVE)) {
            groves.put(grove.location().getPath(), inBiome(grove, null));
        }
        all.add(new Built("archwood_grove", advancement(ROOT, "ars_nouveau:blue_archwood_sapling", "task", 0,
                "archwood_grove", groves), new String[]{"ars_nouveau"}, "Archwood Wanderer", "Find an archwood grove"));

        JsonObject riding = new JsonObject();
        JsonObject whale = new JsonObject();
        whale.addProperty("type", AlteredAether.MOD_ID + ":tamed_aerwhale");
        riding.add("vehicle", whale);
        advancement(all, "sky_rider", ROOT, "minecraft:saddle", "task", 0, "Sky Rider", "Ride a tamed aerwhale",
                Map.of("riding", location(riding)));
        advancement(all, "ride_the_storm", "sky_rider", "aether:lightning_knife", "goal", 50, "Ride the Storm",
                "Ride an aerwhale through the Tempest Peaks", Map.of("riding", inBiome(AABiomes.TEMPEST_PEAKS, whale)));
        // Awarded in code by MountUnsaddling.
        advancement(all, "take_a_load_off", ROOT, "minecraft:saddle", "task", 0, "Take a Load Off",
                "Sneak and take the saddle off a phyg or flying cow",
                Map.of("unsaddled", criterion("minecraft:impossible")));

        JsonObject rumour = criterion("minecraft:inventory_changed");
        JsonObject map = new JsonObject();
        map.addProperty("items", "minecraft:filled_map");
        JsonObject predicates = new JsonObject();
        predicates.addProperty("minecraft:custom_data", "{" + RumourMapFunction.TAG + ":{}}");
        map.add("predicates", predicates);
        JsonArray items = new JsonArray();
        items.add(map);
        rumour.getAsJsonObject("conditions").add("items", items);
        advancement(all, "rumour", ROOT, "minecraft:filled_map", "task", 0, "Rumours",
                "Find a map marked with the rumour of a landmark", Map.of("rumour", rumour));

        Map<String, JsonObject> wonders = new LinkedHashMap<>();
        for (LandmarkType type : LandmarkType.values()) {
            wonders.put(type.id(), visit(type.id()));
        }
        advancement(all, "seven_wonders", "rumour", "minecraft:spyglass", "challenge", 250, "Seven Wonders",
                "Visit every kind of landmark in the Aether", wonders);

        Map<String, JsonObject> biomes = new LinkedHashMap<>();
        for (AABiomes.Entry entry : AABiomes.ALL) {
            if (entry.requiredMods().isEmpty()) {
                biomes.put(entry.key().location().getPath(), inBiome(entry.key(), null));
            }
        }
        advancement(all, "altered_cartographer", ROOT, "minecraft:compass", "challenge", 100, "Altered Cartographer",
                "Visit every biome of the Altered Aether", biomes);
        return all;
    }

    private static void biome(List<Built> all, String name, ResourceKey<Biome> biome, String icon, String title,
                              String description) {
        advancement(all, name, ROOT, icon, "task", 0, title, description, Map.of("visit", inBiome(biome, null)));
    }

    private static void landmark(List<Built> all, String name, String parent, String landmark, String icon,
                                 String title, String description) {
        advancement(all, name, parent, icon, "goal", 50, title, description, Map.of("visit", visit(landmark)));
    }

    private static void advancement(List<Built> all, String name, String parent, String icon, String frame,
                                    int experience, String title, String description,
                                    Map<String, JsonObject> criteria) {
        all.add(new Built(name, advancement(parent, icon, frame, experience, name, criteria), new String[0], title,
                description));
    }

    /** An advancement's JSON. Any one criterion earns a task or goal; a challenge needs them all. */
    private static JsonObject advancement(String parent, String icon, String frame, int experience, String name,
                                          Map<String, JsonObject> criteria) {
        JsonObject json = new JsonObject();
        if (parent != null) {
            json.addProperty("parent", AlteredAether.MOD_ID + ":" + parent);
        }
        JsonObject display = new JsonObject();
        JsonObject item = new JsonObject();
        item.addProperty("id", icon);
        display.add("icon", item);
        display.add("title", translate(key(name) + ".title"));
        display.add("description", translate(key(name) + ".description"));
        display.addProperty("frame", frame);
        if (parent == null) {
            display.addProperty("background", "aether:textures/block/natural/holystone.png");
            display.addProperty("show_toast", false);
            display.addProperty("announce_to_chat", false);
        }
        json.add("display", display);
        JsonObject criteriaJson = new JsonObject();
        criteria.forEach(criteriaJson::add);
        json.add("criteria", criteriaJson);
        JsonArray requirements = new JsonArray();
        if (frame.equals("challenge")) {
            for (String criterion : criteria.keySet()) {
                JsonArray one = new JsonArray();
                one.add(criterion);
                requirements.add(one);
            }
        } else {
            JsonArray anyOf = new JsonArray();
            criteria.keySet().forEach(anyOf::add);
            requirements.add(anyOf);
        }
        json.add("requirements", requirements);
        if (experience > 0) {
            JsonObject rewards = new JsonObject();
            rewards.addProperty("experience", experience);
            json.add("rewards", rewards);
        }
        return json;
    }

    private static JsonObject visit(String landmark) {
        JsonObject criterion = criterion(AlteredAether.MOD_ID + ":visit_landmark");
        criterion.getAsJsonObject("conditions").addProperty("landmark", landmark);
        return criterion;
    }

    /** A player standing in a biome, riding the given vehicle if there is one. */
    private static JsonObject inBiome(ResourceKey<Biome> biome, JsonObject vehicle) {
        JsonObject predicate = new JsonObject();
        JsonObject location = new JsonObject();
        location.addProperty("biomes", biome.location().toString());
        predicate.add("location", location);
        if (vehicle != null) {
            predicate.add("vehicle", vehicle);
        }
        return location(predicate);
    }

    /** {@code minecraft:location}, checked every second, on a player matching the entity predicate. */
    private static JsonObject location(JsonObject predicate) {
        JsonObject check = new JsonObject();
        check.addProperty("condition", "minecraft:entity_properties");
        check.addProperty("entity", "this");
        check.add("predicate", predicate);
        JsonArray player = new JsonArray();
        player.add(check);
        JsonObject criterion = criterion("minecraft:location");
        criterion.getAsJsonObject("conditions").add("player", player);
        return criterion;
    }

    private static JsonObject criterion(String trigger) {
        JsonObject criterion = new JsonObject();
        criterion.addProperty("trigger", trigger);
        criterion.add("conditions", new JsonObject());
        return criterion;
    }

    private static JsonObject translate(String key) {
        JsonObject text = new JsonObject();
        text.addProperty("translate", key);
        return text;
    }

    private static String key(String name) {
        return "advancements." + AlteredAether.MOD_ID + "." + name;
    }
}
