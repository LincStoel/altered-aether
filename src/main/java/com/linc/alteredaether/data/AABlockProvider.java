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

import java.util.Map;
import java.util.TreeMap;

/**
 * The ambrosium campfire's models, loot and recipe, and the language file. The logs are the Aether's skyroot log
 * textures, by reference; the flame is our own strip (tools/textures/ambrosium_campfire_fire.py).
 */
public final class AABlockProvider extends JsonDataProvider {
    private static final String CAMPFIRE = "ambrosium_campfire";
    private static final String LOG = "aether:block/natural/skyroot_log";
    private static final String LIT_LOG = "aether:block/natural/stripped_skyroot_log";
    private static final String FIRE = AlteredAether.MOD_ID + ":block/ambrosium_campfire_fire";

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
        language();
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
        for (AABiomes.Entry biome : AABiomes.ALL) {
            lang.put("biome." + AlteredAether.MOD_ID + "." + biome.name(), title(biome.name()));
        }
        lang.put("entity." + AlteredAether.MOD_ID + ".tamed_aerwhale", "Aerwhale");
        lang.put("message." + AlteredAether.MOD_ID + ".aerwhale.stay", "%s will circle here");
        lang.put("message." + AlteredAether.MOD_ID + ".aerwhale.wander", "%s will roam freely");
        lang.put("message." + AlteredAether.MOD_ID + ".aerwhale.follow", "%s will follow you");
        lang.put("message." + AlteredAether.MOD_ID + ".aerwhale.not_owner", "This aerwhale answers to someone else");
        lang.put("key." + AlteredAether.MOD_ID + ".aerwhale_eject", "Leap off Aerwhale");
        lang.put("key.categories." + AlteredAether.MOD_ID, "Altered Aether");
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
