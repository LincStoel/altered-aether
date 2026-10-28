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
import com.linc.alteredaether.worldgen.AABiomes;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Tags. Written as JSON so entries for optional mods can be optional ({@code required: false}) and so tags in other
 * namespaces are appended to, never replaced.
 */
public final class AATagProvider extends JsonDataProvider {
    /** Namespaces that are always present; entries from any other mod are written as optional. */
    private static final Set<String> REQUIRED = Set.of("minecraft", AlteredAether.MOD_ID, "aether", "aeroblender");

    public AATagProvider(PackOutput output) {
        super(output, "tags");
    }

    @Override
    protected void generate() {
        tag("block", AlteredAether.id("breeze_spawnable_on"), "aether:cold_aercloud");
        // Every Aether soil is in #minecraft:dirt (aether grass and dirt, enchanted grass, and Deep Aether's coarse
        // dirt, mud and golden grass), so familiars follow the groves' varied surfaces without a list of their own.
        tag("block", AlteredAether.id("familiar_spawnable_on"), "#minecraft:dirt");
        // Mowzie's own rules want vanilla grass and overworld stone (and the overworld itself, below Y 68).
        tag("block", AlteredAether.id("naga_spawnable_on"), "#minecraft:dirt", "#aether:holystone");
        tag("block", AlteredAether.id("lantern_spawnable_on"), "#minecraft:dirt", "#minecraft:leaves",
                "#minecraft:logs");
        tag("worldgen/biome", AlteredAether.id("has_lanterns"), "altered_aether:skyroot_cathedral",
                "deep_aether:luminescent_forest");
        // Snuffles key their spawning to this tag (and light), not sea level or the biome, so the Frozen Crags' ground
        // and the aerglow forests' grass only need adding to it. They spawn only in the biomes we list.
        tag("block", ResourceLocation.fromNamespaceAndPath("snuffles", "snuffles_spawnable_on"),
                "aether:icestone", "minecraft:snow_block", "minecraft:ice", "minecraft:packed_ice",
                "deep_aether:aether_coarse_dirt", "aether:aether_grass_block");
        // The ores that are holystone-only in the Aether have to be retargeted at the rock of the biomes that replace it.
        tag("block", AlteredAether.id("frozen_crag_ore_replaceables"), "aether:icestone", "deep_aether:clorite",
                "aether:holystone");
        tag("block", AlteredAether.id("tempest_peak_ore_replaceables"), "minecraft:cobbled_deepslate",
                "minecraft:blackstone", "aether:holystone");
        tag("block", ResourceLocation.withDefaultNamespace("campfires"), "altered_aether:ambrosium_campfire");
        // Create's fans enchant through a lit ambrosium campfire, ours or Beyond Parity's (see CreateCompat).
        tag("block", AlteredAether.id("fan_processing_catalysts/enchanting"), "altered_aether:ambrosium_campfire",
                "aether_beyond_parity:ambrosium_campfire");
        tag("block", ResourceLocation.withDefaultNamespace("mineable/axe"), "altered_aether:ambrosium_campfire",
                "altered_aether:gleam_vines", "altered_aether:gleam_vines_plant");
        // dead bushes may stand on the dunes' quicksoil
        tag("block", ResourceLocation.withDefaultNamespace("dead_bush_may_place_on"), "aether:quicksoil");
        // Tempest storms (see TempestStorms) and the natural rock their strikes scorch.
        tag("worldgen/biome", AlteredAether.id("has_storms"), "altered_aether:tempest_peaks");
        // Zephyrs and squalls may spawn in midair here (see SpawnPlacementEvents), where the biome lists them.
        tag("worldgen/biome", AlteredAether.id("midair_zephyrs"), "altered_aether:tempest_peaks",
                "altered_aether:frozen_crags");
        tag("block", AlteredAether.id("scorchable"), "aether:holystone", "aether:mossy_holystone", "aether:icestone",
                "minecraft:cobbled_deepslate", "minecraft:deepslate", "minecraft:stone", "minecraft:snow_block",
                "deep_aether:aseterite", "deep_aether:clorite");
        lightning();
        tag("entity_type", AlteredAether.id("ignores_aercloud_drag"), "aether:zephyr", "altered_aether:squall",
                "aether:aerwhale", "altered_aether:tamed_aerwhale", "mowziesmobs:naga");
        // Deep Aether's sterling aerclouds call lightning on whatever passes through and turn cold; zephyrs are spared,
        // and so are squalls.
        tag("entity_type", ResourceLocation.fromNamespaceAndPath("deep_aether", "sterling_aercloud_blacklist"),
                "altered_aether:squall");
        // Tamed aerwhales (see TamedAerwhale).
        tag("item", AlteredAether.id("aerwhale_taming_food"), "aether:blue_berry");
        tag("item", AlteredAether.id("aerwhale_breeding_food"), "aether:enchanted_berry");
        gleamVines();
        aetherBiomes();
    }

    /**
     * What draws Tempest lightning onto a player (see TempestStorms): overworld-metal armour and tools, and standing on
     * copper without leather or gem boots. Aether gear (zanite, gravitite, the dungeon sets) is left out, so it is safe to wear in a storm.
     */
    private void lightning() {
        List<String> items = new ArrayList<>();
        for (String metal : new String[]{"iron", "golden", "chainmail", "netherite"}) {
            for (String piece : new String[]{"helmet", "chestplate", "leggings", "boots"}) {
                items.add("minecraft:" + metal + "_" + piece);
            }
        }
        for (String metal : new String[]{"iron", "golden", "netherite"}) {
            for (String tool : new String[]{"sword", "shovel", "pickaxe", "axe", "hoe"}) {
                items.add("minecraft:" + metal + "_" + tool);
            }
        }
        items.addAll(List.of("minecraft:shears", "minecraft:flint_and_steel", "minecraft:mace"));
        tag("item", AlteredAether.id("attracts_lightning"), items.toArray(String[]::new));
        // leather and gemstone soles keep a player off charged copper; any other boots still conduct
        tag("item", AlteredAether.id("insulating_boots"), "minecraft:leather_boots", "minecraft:diamond_boots",
                "aether:zanite_boots", "deep_aether:skyjade_boots");

        List<String> blocks = new ArrayList<>();
        for (String wax : new String[]{"", "waxed_"}) {
            for (String age : new String[]{"", "exposed_", "weathered_", "oxidized_"}) {
                String copper = age.isEmpty() ? "copper_block" : age + "copper";
                for (String block : new String[]{copper, age + "cut_copper", age + "cut_copper_stairs",
                        age + "cut_copper_slab", age + "chiseled_copper", age + "copper_grate", age + "copper_bulb"}) {
                    blocks.add("minecraft:" + wax + block);
                }
            }
        }
        tag("block", AlteredAether.id("charged_copper"), blocks.toArray(String[]::new));
    }

    /**
     * Gleam vines and gleamberries join every tag that names cave vines or glow berries outright, in vanilla, NeoForge
     * and the optional mods we know of (mineable/axe is shared with the campfire, above); tags that hold {@code #minecraft:cave_vines} (moss_replaceable, Create's) take
     * them in through it.
     */
    private void gleamVines() {
        String vines = "altered_aether:gleam_vines";
        String plant = "altered_aether:gleam_vines_plant";
        for (String tag : new String[]{"cave_vines", "climbable", "bee_growables", "sword_efficient"}) {
            tag("block", ResourceLocation.withDefaultNamespace(tag), vines, plant);
        }
        // Ars Nouveau lists only the head
        tag("block", ResourceLocation.fromNamespaceAndPath("ars_nouveau", "harvest/foliage"), vines);
        String berries = "altered_aether:gleamberries";
        tag("item", ResourceLocation.withDefaultNamespace("fox_food"), berries);
        tag("item", ResourceLocation.fromNamespaceAndPath("c", "foods/berry"), berries);
        tag("item", ResourceLocation.fromNamespaceAndPath("supplementaries", "flower_box_tall_plantable"), berries);
    }

    /**
     * Our biomes join {@code #aether:is_aether}, which the Aether and other mods use to find the dimension's biomes
     * (spawns, structures, our own biome modifiers). Biomes that need an optional mod are optional entries, since
     * they don't exist without it.
     */
    private void aetherBiomes() {
        JsonArray values = new JsonArray();
        for (AABiomes.Entry entry : AABiomes.ALL) {
            String id = entry.key().location().toString();
            if (entry.requiredMods().isEmpty()) {
                values.add(id);
            } else {
                JsonObject optional = new JsonObject();
                optional.addProperty("id", id);
                optional.addProperty("required", false);
                values.add(optional);
            }
        }
        JsonObject json = new JsonObject();
        json.addProperty("replace", false);
        json.add("values", values);
        data("tags/worldgen/biome", ResourceLocation.fromNamespaceAndPath("aether", "is_aether"), json);
    }

    private void tag(String registry, ResourceLocation id, String... entries) {
        JsonArray values = new JsonArray();
        for (String entry : entries) {
            String namespace = entry.replace("#", "").split(":")[0];
            if (REQUIRED.contains(namespace)) {
                values.add(entry);
            } else {
                JsonObject optional = new JsonObject();
                optional.addProperty("id", entry);
                optional.addProperty("required", false);
                values.add(optional);
            }
        }
        JsonObject json = new JsonObject();
        json.addProperty("replace", false);
        json.add("values", values);
        data("tags/" + registry, id, json);
    }
}
