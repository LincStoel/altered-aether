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

import java.util.Map;
import java.util.TreeMap;

/**
 * The ambrosium campfire's and gleam vines' models, loot and recipe, and the language file. The campfire's logs are the
 * Aether's skyroot log textures, by reference; the flame is our own strip (tools/textures/ambrosium_campfire_fire.py).
 * The gleam vines are vanilla's cave vines recolored (tools/textures/gleam_vines.py).
 */
public final class AABlockProvider extends JsonDataProvider {
    private static final String CAMPFIRE = "ambrosium_campfire";
    private static final String LOG = "aether:block/natural/skyroot_log";
    private static final String LIT_LOG = "aether:block/natural/stripped_skyroot_log";
    private static final String FIRE = AlteredAether.MOD_ID + ":block/ambrosium_campfire_fire";
    private static final String GLEAM_VINES = "gleam_vines";
    private static final String GLEAM_VINES_PLANT = "gleam_vines_plant";
    private static final String GLEAMBERRIES = "gleamberries";

    public AABlockProvider(PackOutput output) {
        super(output, "blocks and language");
    }

    @Override
    protected void generate() {
        blockstate();
        // Vanilla sets its campfire's cutout layer in code, which ours doesn't share; without it the flame's clear
        // pixels draw as a dark box.
        asset("models/block", AlteredAether.id(CAMPFIRE + "_off"), cutout(model("minecraft:block/campfire_off",
                Map.of("particle", LOG, "log", LOG))));
        asset("models/block", AlteredAether.id(CAMPFIRE), cutout(model("minecraft:block/template_campfire",
                Map.of("particle", LOG, "log", LOG, "lit_log", LIT_LOG, "fire", FIRE))));
        asset("models/item", AlteredAether.id(CAMPFIRE), model(AlteredAether.MOD_ID + ":block/" + CAMPFIRE, Map.of()));
        loot();
        recipe();
        gleamVines();
        asset("models/item", AlteredAether.id("squall_spawn_egg"), model("minecraft:item/template_spawn_egg", Map.of()));
        language();
    }

    /** Vanilla's cave vines, block for block: a crossed sprite with and without berries, dropping a berry if it has one. */
    private void gleamVines() {
        for (String vine : new String[]{GLEAM_VINES, GLEAM_VINES_PLANT}) {
            JsonObject variants = new JsonObject();
            for (boolean berries : new boolean[]{false, true}) {
                String model = vine + (berries ? "_lit" : "");
                // vanilla sets cave vines' cutout layer in code, as it does the campfire's
                asset("models/block", AlteredAether.id(model), cutout(model("minecraft:block/cross",
                        Map.of("cross", AlteredAether.MOD_ID + ":block/" + model))));
                JsonObject variant = new JsonObject();
                variant.addProperty("model", AlteredAether.MOD_ID + ":block/" + model);
                variants.add("berries=" + berries, variant);
            }
            JsonObject blockstate = new JsonObject();
            blockstate.add("variants", variants);
            asset("blockstates", AlteredAether.id(vine), blockstate);

            JsonObject properties = new JsonObject();
            properties.addProperty("berries", "true");
            JsonObject condition = new JsonObject();
            condition.addProperty("condition", "minecraft:block_state_property");
            condition.addProperty("block", AlteredAether.MOD_ID + ":" + vine);
            condition.add("properties", properties);
            JsonObject entry = new JsonObject();
            entry.addProperty("type", "minecraft:item");
            entry.addProperty("name", AlteredAether.MOD_ID + ":" + GLEAMBERRIES);
            JsonObject pool = new JsonObject();
            pool.addProperty("rolls", 1);
            pool.addProperty("bonus_rolls", 0);
            pool.add("conditions", array(condition));
            pool.add("entries", array(entry));
            JsonObject loot = new JsonObject();
            loot.addProperty("type", "minecraft:block");
            loot.add("pools", array(pool));
            loot.addProperty("random_sequence", AlteredAether.MOD_ID + ":blocks/" + vine);
            data("loot_table/blocks", AlteredAether.id(vine), loot);
        }
        asset("models/item", AlteredAether.id(GLEAMBERRIES), model("minecraft:item/generated",
                Map.of("layer0", AlteredAether.MOD_ID + ":item/" + GLEAMBERRIES)));

        // composts like glow berries
        JsonObject chance = new JsonObject();
        chance.addProperty("chance", 0.3F);
        JsonObject values = new JsonObject();
        values.add(AlteredAether.MOD_ID + ":" + GLEAMBERRIES, chance);
        JsonObject compostables = new JsonObject();
        compostables.addProperty("replace", false);
        compostables.add("values", values);
        data("data_maps/item", ResourceLocation.fromNamespaceAndPath("neoforge", "compostables"), compostables);
    }

    /** Vanilla's own: four facings, lit and unlit, turned about the vertical axis. */
    private void blockstate() {
        JsonObject variants = new JsonObject();
        String[] facings = {"south", "west", "north", "east"};
        for (int i = 0; i < facings.length; i++) {
            for (boolean lit : new boolean[]{false, true}) {
                JsonObject variant = new JsonObject();
                variant.addProperty("model", AlteredAether.MOD_ID + ":block/" + CAMPFIRE + (lit ? "" : "_off"));
                if (i > 0) {
                    variant.addProperty("y", i * 90);
                }
                variants.add("facing=" + facings[i] + ",lit=" + lit, variant);
            }
        }
        JsonObject json = new JsonObject();
        json.add("variants", variants);
        asset("blockstates", AlteredAether.id(CAMPFIRE), json);
    }

    private static JsonObject cutout(JsonObject model) {
        model.addProperty("render_type", "minecraft:cutout");
        return model;
    }

    private static JsonObject model(String parent, Map<String, String> textures) {
        JsonObject json = new JsonObject();
        json.addProperty("parent", parent);
        if (!textures.isEmpty()) {
            JsonObject map = new JsonObject();
            new TreeMap<>(textures).forEach(map::addProperty);
            json.add("textures", map);
        }
        return json;
    }

    private void loot() {
        JsonObject condition = new JsonObject();
        condition.addProperty("condition", "minecraft:survives_explosion");
        JsonObject entry = new JsonObject();
        entry.addProperty("type", "minecraft:item");
        entry.addProperty("name", AlteredAether.MOD_ID + ":" + CAMPFIRE);
        JsonObject pool = new JsonObject();
        pool.addProperty("rolls", 1);
        pool.add("conditions", array(condition));
        pool.add("entries", array(entry));
        JsonObject json = new JsonObject();
        json.addProperty("type", "minecraft:block");
        json.add("pools", array(pool));
        data("loot_table/blocks", AlteredAether.id(CAMPFIRE), json);
    }

    /** A campfire with ambrosium for coal and skyroot for wood. */
    private void recipe() {
        JsonObject key = new JsonObject();
        key.add("L", ingredientTag("aether:skyroot_logs"));
        key.add("S", ingredientTag("aether:skyroot_stick"));
        JsonObject shard = new JsonObject();
        shard.addProperty("item", "aether:ambrosium_shard");
        key.add("A", shard);
        JsonObject result = new JsonObject();
        result.addProperty("id", AlteredAether.MOD_ID + ":" + CAMPFIRE);
        result.addProperty("count", 1);
        JsonObject json = new JsonObject();
        json.addProperty("type", "minecraft:crafting_shaped");
        json.addProperty("category", "misc");
        json.add("key", key);
        json.add("pattern", array(" S ", "SAS", "LLL"));
        json.add("result", result);
        data("recipe", AlteredAether.id(CAMPFIRE), json);

        // Aether: Beyond Parity's campfire, which this one replaces, turns into ours (see BeyondParityCompat)
        JsonObject theirs = new JsonObject();
        theirs.addProperty("item", "aether_beyond_parity:ambrosium_campfire");
        JsonObject swap = new JsonObject();
        swap.addProperty("type", "minecraft:crafting_shapeless");
        swap.addProperty("category", "misc");
        swap.add("ingredients", array(theirs));
        swap.add("result", result);
        data("recipe", AlteredAether.id(CAMPFIRE + "_from_parity"), swap, "aether_beyond_parity");
    }

    private static JsonObject ingredientTag(String tag) {
        JsonObject json = new JsonObject();
        json.addProperty("tag", tag);
        return json;
    }

    private static JsonArray array(Object... values) {
        JsonArray array = new JsonArray();
        for (Object value : values) {
            if (value instanceof String string) {
                array.add(string);
            } else {
                array.add((JsonObject) value);
            }
        }
        return array;
    }

    private void language() {
        Map<String, String> lang = new TreeMap<>();
        lang.put("block." + AlteredAether.MOD_ID + "." + CAMPFIRE, "Ambrosium Campfire");
        lang.put("block." + AlteredAether.MOD_ID + "." + GLEAM_VINES, "Gleam Vines");
        lang.put("block." + AlteredAether.MOD_ID + "." + GLEAM_VINES_PLANT, "Gleam Vines Plant");
        lang.put("item." + AlteredAether.MOD_ID + "." + GLEAMBERRIES, "Gleamberries");
        for (AABiomes.Entry biome : AABiomes.ALL) {
            lang.put("biome." + AlteredAether.MOD_ID + "." + biome.name(), title(biome.name()));
        }
        lang.put("entity." + AlteredAether.MOD_ID + ".tamed_aerwhale", "Aerwhale");
        lang.put("entity." + AlteredAether.MOD_ID + ".squall", "Squall");
        lang.put("item." + AlteredAether.MOD_ID + ".squall_spawn_egg", "Squall Spawn Egg");
        lang.put("message." + AlteredAether.MOD_ID + ".aerwhale.stay", "%s will circle here");
        lang.put("message." + AlteredAether.MOD_ID + ".aerwhale.wander", "%s will roam freely");
        lang.put("message." + AlteredAether.MOD_ID + ".aerwhale.follow", "%s will follow you");
        lang.put("message." + AlteredAether.MOD_ID + ".aerwhale.not_owner", "This aerwhale answers to someone else");
        lang.put("key." + AlteredAether.MOD_ID + ".aerwhale_dive", "Dive Aerwhale");
        // Create's JEI page for bulk enchanting (see compat/jei); Create names the page's fan under its own namespace
        lang.put(AlteredAether.MOD_ID + ".recipe.fan_enchanting", "Bulk Enchanting");
        lang.put("create.recipe.fan_enchanting.fan", "Fan behind Ambrosium Campfire");
        lang.put("key." + AlteredAether.MOD_ID + ".aerwhale_eject", "Leap off Aerwhale");
        lang.put("key.categories." + AlteredAether.MOD_ID, "Altered Aether");
        lang.put("subtitles." + AlteredAether.MOD_ID + ".storm.incoming_lightning", "Lightning gathers");
        lang.put("item." + AlteredAether.MOD_ID + ".rumour_map.tempest_spires", "Rumour of a Tempest Crown");
        lang.put("item." + AlteredAether.MOD_ID + ".rumour_map.frozen_spire", "Rumour of a Frozen Spire");
        lang.put("item." + AlteredAether.MOD_ID + ".rumour_map.grand_cascade", "Rumour of a Grand Cascade");
        lang.put("item." + AlteredAether.MOD_ID + ".rumour_map.great_fracture", "Rumour of a Great Fracture");
        lang.put("item." + AlteredAether.MOD_ID + ".rumour_map.grandfather", "Rumour of the Grandfather");
        lang.put("item." + AlteredAether.MOD_ID + ".rumour_map.village_tree", "Rumour of a Village Tree");
        lang.put("item." + AlteredAether.MOD_ID + ".rumour_map.double_atoll", "Rumour of a Double Atoll");
        AAAdvancementProvider.language(lang);
        JsonObject json = new JsonObject();
        lang.forEach(json::addProperty);
        asset("lang", AlteredAether.id("en_us"), json);
    }

    private static String title(String id) {
        StringBuilder out = new StringBuilder();
        for (String word : id.split("_")) {
            out.append(out.isEmpty() ? "" : " ").append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.toString();
    }
}
