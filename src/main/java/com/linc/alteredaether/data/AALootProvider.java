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

/**
 * Chest loot for the groves. A pilgrims' camp holds supplies for the road; a secret spot holds what someone thought
 * worth hiding; the Grandfather's heartwood room and the caches buried in colossal stumps hold the best of it. Even
 * those hold no dungeon treasure: the groves reward exploring, not replace the dungeons.
 */
public final class AALootProvider extends JsonDataProvider {
    public AALootProvider(PackOutput output) {
        super(output, "loot tables");
    }

    @Override
    protected void generate() {
        table("camp", pool(3, 5, camp()));
        table("secret", pool(3, 5, camp()), pool(1, 2, secret()));
        table("heartwood", pool(2, 4, camp()), pool(2, 3, secret()), pool(1, 1, heartwood()),
                pool(1, 1, entry("aether:golden_amber", 1, 8, 16)));
        table("stump", pool(2, 3, camp()), pool(2, 4, secret()), pool(2, 3, heartwood()), pool(1, 1, stump()),
                pool(1, 1, entry("aether:golden_amber", 1, 12, 24)));
    }

    private static JsonArray camp() {
        return entries(
                entry("aether:blue_berry", 20, 2, 6),
                entry("aether:enchanted_berry", 5, 1, 2),
                entry("aether:blue_gummy_swet", 8, 1, 2),
                entry("aether:golden_gummy_swet", 3, 1, 1),
                entry("aether:ambrosium_shard", 15, 2, 5),
                entry("aether:golden_amber", 10, 2, 6),
                entry("aether:skyroot_stick", 12, 3, 8),
                entry("aether:ambrosium_torch", 12, 2, 6),
                entry("aether:white_apple", 4, 1, 1),
                entry("aether:swet_ball", 8, 1, 3),
                entry("aether:aechor_petal", 6, 1, 3),
                entry("aether:skyroot_bucket", 3, 1, 1),
                entry("aether:book_of_lore", 2, 1, 1));
    }

    private static JsonArray secret() {
        return entries(
                entry("aether:zanite_gemstone", 12, 1, 4),
                entry("aether:healing_stone", 4, 1, 1),
                entry("aether:enchanted_dart", 5, 4, 8),
                entry("aether:iron_ring", 3, 1, 1),
                entry("aether:zanite_ring", 2, 1, 1),
                entry("aether:zanite_pendant", 2, 1, 1),
                enchanted("aether:zanite_pickaxe", 3, 5, 15),
                enchanted("aether:zanite_axe", 3, 5, 15),
                enchanted("aether:zanite_sword", 3, 5, 15),
                enchanted("minecraft:book", 4, 5, 15),
                entry("aether:music_disc_aether_tune", 1, 1, 1),
                entry("aether:music_disc_ascending_dawn", 1, 1, 1),
                entry("aether:music_disc_chinchilla", 1, 1, 1),
                entry("aether:music_disc_high", 1, 1, 1));
    }

    private static JsonArray heartwood() {
        return entries(
                entry("aether:golden_ring", 3, 1, 1),
                entry("aether:golden_pendant", 3, 1, 1),
                entry("aether:agility_cape", 1, 1, 1),
                enchanted("aether:zanite_chestplate", 2, 15, 25),
                enchanted("aether:zanite_helmet", 2, 15, 25));
    }

    /** The prize of a colossal stump's buried cache: the one thing worth the dig. */
    private static JsonArray stump() {
        return entries(
                entry("aether:enchanted_gravitite", 4, 2, 5),
                entry("aether:zanite_gloves", 3, 1, 1),
                entry("aether:golden_gloves", 3, 1, 1),
                entry("aether:agility_cape", 2, 1, 1),
                enchanted("aether:zanite_sword", 2, 25, 30),
                enchanted("aether:zanite_pickaxe", 2, 25, 30),
                enchanted("minecraft:book", 3, 25, 30));
    }

    private void table(String name, JsonObject... pools) {
        JsonObject table = new JsonObject();
        table.addProperty("type", "minecraft:chest");
        JsonArray list = new JsonArray();
        for (JsonObject pool : pools) {
            list.add(pool);
        }
        table.add("pools", list);
        table.addProperty("random_sequence", AlteredAether.MOD_ID + ":chests/grove/" + name);
        data("loot_table/chests/grove", AlteredAether.id(name), table);
    }

    private static JsonObject pool(int min, int max, JsonArray entries) {
        JsonObject pool = new JsonObject();
        pool.add("rolls", uniform(min, max));
        pool.addProperty("bonus_rolls", 0.0);
        pool.add("entries", entries);
        return pool;
    }

    private static JsonObject pool(int min, int max, JsonObject entry) {
        return pool(min, max, entries(entry));
    }

    private static JsonArray entries(JsonObject... entries) {
        JsonArray list = new JsonArray();
        for (JsonObject entry : entries) {
            list.add(entry);
        }
        return list;
    }

    private static JsonObject entry(String item, int weight, int min, int max) {
        JsonObject entry = new JsonObject();
        entry.addProperty("type", "minecraft:item");
        entry.addProperty("name", item);
        entry.addProperty("weight", weight);
        if (max > 1) {
            JsonObject count = new JsonObject();
            count.addProperty("function", "minecraft:set_count");
            count.add("count", uniform(min, max));
            JsonArray functions = new JsonArray();
            functions.add(count);
            entry.add("functions", functions);
        }
        return entry;
    }

    private static JsonObject enchanted(String item, int weight, int min, int max) {
        JsonObject entry = entry(item, weight, 1, 1);
        JsonObject enchant = new JsonObject();
        enchant.addProperty("function", "minecraft:enchant_with_levels");
        enchant.add("levels", uniform(min, max));
        JsonArray functions = new JsonArray();
        functions.add(enchant);
        entry.add("functions", functions);
        return entry;
    }

    private static JsonObject uniform(int min, int max) {
        JsonObject range = new JsonObject();
        range.addProperty("type", "minecraft:uniform");
        range.addProperty("min", min);
        range.addProperty("max", max);
        return range;
    }
}
