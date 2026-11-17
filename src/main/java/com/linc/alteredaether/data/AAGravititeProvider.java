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
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * The gravitite rework's datapack (see compat/GravititeRework). Nothing here replaces one of the Aether's files except
 * its gravitite trim material, which has no other way to change its ingredient; the rest is appended tags (with
 * NeoForge's {@code remove} to take the block out where the ingot takes its place), a loot modifier and our own
 * recipes. The ingot only ever comes from enchanting: the altar (and Create's fans through an ambrosium campfire, which
 * run the altar's recipes) and Ars Nouveau's imbuement, never a furnace. Create's washing gives nuggets, by choice.
 */
public final class AAGravititeProvider extends JsonDataProvider {
    private static final String ID = AlteredAether.MOD_ID + ":";
    private static final String ORE = "aether:gravitite_ore";
    private static final String BLOCK = "aether:enchanted_gravitite";
    private static final String RAW = ID + "raw_gravitite";
    private static final String RAW_BLOCK = ID + "raw_gravitite_block";
    private static final String INGOT = ID + "enchanted_gravitite_ingot";
    private static final String NUGGET = ID + "enchanted_gravitite_nugget";
    private static final String CRUSHED = ID + "crushed_raw_gravitite";
    private static final String DUST = ID + "gravitite_dust";
    private static final String SMALL_DUST = ID + "small_gravitite_dust";
    private static final String ZANITE = "aether:zanite_gemstone";
    private static final String HOLYSTONE = "aether:holystone";
    /** The Aether's own enchanting time for the ore, in ticks. */
    private static final int ENCHANT = 750;

    public AAGravititeProvider(PackOutput output) {
        super(output, "gravitite rework");
    }

    @Override
    protected void generate() {
        packMeta();
        tags();
        trimMaterial();
        oreDrops();
        altar();
        crafting();
        create();
        oritech();
        arsNouveau();
        deepAether();
        sable();
    }

    private void packMeta() {
        JsonObject pack = new JsonObject();
        pack.addProperty("pack_format", 48);
        pack.addProperty("description", "Gravitite as a modern ore: raw gravitite, enchanted ingots and nuggets");
        JsonObject json = new JsonObject();
        json.add("pack", pack);
        root("pack.mcmeta", json);
    }

    // -------------------------------------------------------------------------------------------------- tags --

    private void tags() {
        // The ingot takes the block's place everywhere the Aether and Deep Aether name gravitite by tag: gear, ring,
        // stratus and smithing-template recipes, repairs, beacons and trims. The block stays a beacon base.
        for (String tag : List.of("aether:processed/gravitite", "aether:gravitite_repairing",
                "minecraft:beacon_payment_items", "minecraft:trim_materials")) {
            tag("item", tag, List.of(INGOT), List.of(BLOCK));
        }
        for (String registry : List.of("block", "item")) {
            tag(registry, "c:ores/gravitite", List.of(ORE), List.of());
            tag(registry, "c:ores", List.of("#c:ores/gravitite"), List.of());
            tag(registry, "c:storage_blocks/enchanted_gravitite", List.of(BLOCK), List.of());
            tag(registry, "c:storage_blocks/raw_gravitite", List.of(RAW_BLOCK), List.of());
            // Sable makes every storage block super heavy (mass 4) through this tag.
            tag(registry, "c:storage_blocks",
                    List.of("#c:storage_blocks/enchanted_gravitite", "#c:storage_blocks/raw_gravitite"), List.of());
        }
        tag("item", "c:raw_materials/gravitite", List.of(RAW), List.of());
        tag("item", "c:raw_materials", List.of("#c:raw_materials/gravitite"), List.of());
        // "enchanted_gravitite", never "gravitite": tools that generate processing recipes from matching raw, dust
        // and ingot tags must not find a way to smelt dust into the ingot.
        tag("item", "c:ingots/enchanted_gravitite", List.of(INGOT), List.of());
        tag("item", "c:ingots", List.of("#c:ingots/enchanted_gravitite"), List.of());
        tag("item", "c:nuggets/enchanted_gravitite", List.of(NUGGET), List.of());
        tag("item", "c:nuggets", List.of("#c:nuggets/enchanted_gravitite"), List.of());
        tag("item", "c:dusts/gravitite", List.of(DUST), List.of());
        tag("item", "c:dusts", List.of("#c:dusts/gravitite"), List.of());
        tag("item", "create:crushed_raw_materials", List.of(CRUSHED), List.of());
        // what lifts under Sable, always (the enchanted block only while powered: see compat/SableLift)
        tag("block", ID + "lifts_lightly", List.of(ORE, RAW_BLOCK), List.of());
    }

    private void tag(String registry, String tag, List<String> values, List<String> remove) {
        ResourceLocation id = ResourceLocation.parse(tag);
        JsonObject json = new JsonObject();
        json.addProperty("replace", false);
        json.add("values", strings(values));
        if (!remove.isEmpty()) {
            json.add("remove", strings(remove));
        }
        data("tags/" + registry, id, json);
    }

    // ----------------------------------------------------------------------------------------- trim and drops --

    /** The Aether's gravitite trim, as it ships, but smithed with an ingot rather than the block. */
    private void trimMaterial() {
        JsonObject description = new JsonObject();
        description.addProperty("color", "#CC54C3");
        description.addProperty("translate", "trim_material.aether.gravitite");
        JsonObject json = new JsonObject();
        json.addProperty("asset_name", "gravitite");
        json.add("description", description);
        json.addProperty("ingredient", INGOT);
        json.addProperty("item_model_index", 1.0);
        data("trim_material", ResourceLocation.fromNamespaceAndPath("aether", "gravitite"), json);
    }

    /** Raw gravitite from the ore, except with silk touch (see loot/GravititeOreDrops). */
    private void oreDrops() {
        JsonObject table = new JsonObject();
        table.addProperty("condition", "neoforge:loot_table_id");
        table.addProperty("loot_table_id", "aether:blocks/gravitite_ore");
        JsonObject enchantment = new JsonObject();
        enchantment.addProperty("enchantments", "minecraft:silk_touch");
        JsonObject levels = new JsonObject();
        levels.addProperty("min", 1);
        enchantment.add("levels", levels);
        JsonArray enchantments = new JsonArray();
        enchantments.add(enchantment);
        JsonObject predicates = new JsonObject();
        predicates.add("minecraft:enchantments", enchantments);
        JsonObject predicate = new JsonObject();
        predicate.add("predicates", predicates);
        JsonObject silkTouch = new JsonObject();
        silkTouch.addProperty("condition", "minecraft:match_tool");
        silkTouch.add("predicate", predicate);
        JsonObject noSilkTouch = new JsonObject();
        noSilkTouch.addProperty("condition", "minecraft:inverted");
        noSilkTouch.add("term", silkTouch);
        JsonArray conditions = new JsonArray();
        conditions.add(table);
        conditions.add(noSilkTouch);
        JsonObject modifier = new JsonObject();
        modifier.addProperty("type", ID + "gravitite_ore_drops");
        modifier.add("conditions", conditions);
        data("loot_modifiers", AlteredAether.id("gravitite_ore_drops"), modifier);

        JsonObject list = new JsonObject();
        list.addProperty("replace", false);
        list.add("entries", strings(List.of(ID + "gravitite_ore_drops")));
        data("loot_modifiers", ResourceLocation.fromNamespaceAndPath("neoforge", "global_loot_modifiers"), list);
    }

    // ------------------------------------------------------------------------------------------------ recipes --

    /**
     * The altar's enchanting, which Create's fans also run. Ore and raw gravitite take the Aether's own time for the
     * ore, crushed raw gravitite too; dust, already fine, half that; small dust a ninth of dust; the raw block nine raw.
     */
    private void altar() {
        enchanting("ore", item(ORE), INGOT, ENCHANT);
        enchanting("raw", tag("c:raw_materials/gravitite"), INGOT, ENCHANT);
        enchanting("raw_block", tag("c:storage_blocks/raw_gravitite"), BLOCK, ENCHANT * 9);
        enchanting("crushed", item(CRUSHED), INGOT, ENCHANT, "create");
        enchanting("dust", tag("c:dusts/gravitite"), INGOT, ENCHANT / 2, "oritech");
        enchanting("small_dust", item(SMALL_DUST), NUGGET, ENCHANT / 2 / 9, "oritech");
    }

    private void enchanting(String name, JsonObject ingredient, String result, int time, String... mods) {
        JsonObject json = new JsonObject();
        json.addProperty("type", "aether:enchanting");
        json.addProperty("category", "enchanting_misc");
        json.addProperty("cookingtime", time);
        json.addProperty("experience", 1.0);
        json.add("ingredient", ingredient);
        json.add("result", result(result, 1));
        data("recipe", AlteredAether.id("gravitite/enchanting/" + name), json, mods);
    }

    /** Nine to one and back: nuggets and ingots, ingots and the block, raw and its block, small dust and dust. */
    private void crafting() {
        compress("ingot_from_nuggets", NUGGET, INGOT);
        uncompress("nuggets_from_ingot", INGOT, NUGGET);
        compress("block_from_ingots", INGOT, BLOCK);
        uncompress("ingots_from_block", BLOCK, INGOT);
        compress("raw_block_from_raw", RAW, RAW_BLOCK);
        uncompress("raw_from_raw_block", RAW_BLOCK, RAW);
        compress("dust_from_small_dust", SMALL_DUST, DUST, "oritech");
        uncompress("small_dust_from_dust", DUST, SMALL_DUST, "oritech");
    }

    private void compress(String name, String from, String to, String... mods) {
        JsonObject key = new JsonObject();
        key.add("#", item(from));
        JsonObject json = new JsonObject();
        json.addProperty("type", "minecraft:crafting_shaped");
        json.addProperty("category", "misc");
        json.add("key", key);
        json.add("pattern", strings(List.of("###", "###", "###")));
        json.add("result", result(to, 1));
        data("recipe", AlteredAether.id("gravitite/" + name), json, mods);
    }

    private void uncompress(String name, String from, String to, String... mods) {
        JsonArray ingredients = new JsonArray();
        ingredients.add(item(from));
        JsonObject json = new JsonObject();
        json.addProperty("type", "minecraft:crafting_shapeless");
        json.addProperty("category", "misc");
        json.add("ingredients", ingredients);
        json.add("result", result(to, 9));
        data("recipe", AlteredAether.id("gravitite/" + name), json, mods);
    }

    /**
     * Create, as it treats its own ores: raw gravitite crushes to one crushed and maybe experience, the silk-touched
     * ore to one or two and a little holystone, the raw block to nine; zanite comes up a quarter of the time. Crushed
     * gravitite washes into enchanted nuggets, the one way to them that skips enchanting.
     */
    private void create() {
        JsonArray raw = new JsonArray();
        raw.add(output(CRUSHED, 1, 1));
        raw.add(output("create:experience_nugget", 1, 0.75));
        raw.add(output(ZANITE, 1, 0.25));
        createRecipe("crushing", "raw", tag("c:raw_materials/gravitite"), raw, 400);

        JsonArray ore = new JsonArray();
        ore.add(output(CRUSHED, 1, 1));
        ore.add(output(CRUSHED, 1, 0.75));
        ore.add(output("create:experience_nugget", 1, 0.75));
        ore.add(output(ZANITE, 1, 0.25));
        ore.add(output(HOLYSTONE, 1, 0.125));
        createRecipe("crushing", "ore", item(ORE), ore, 250);

        JsonArray block = new JsonArray();
        block.add(output(CRUSHED, 9, 1));
        block.add(output("create:experience_nugget", 9, 0.75));
        block.add(output(ZANITE, 9, 0.25));
        createRecipe("crushing", "raw_block", tag("c:storage_blocks/raw_gravitite"), block, 400);

        JsonArray washed = new JsonArray();
        washed.add(output(NUGGET, 9, 1));
        createRecipe("splashing", "crushed", item(CRUSHED), washed, 0);
    }

    private void createRecipe(String type, String name, JsonObject ingredient, JsonArray results, int time) {
        JsonArray ingredients = new JsonArray();
        ingredients.add(ingredient);
        JsonObject json = new JsonObject();
        json.addProperty("type", "create:" + type);
        json.add("ingredients", ingredients);
        if (time > 0) {
            json.addProperty("processing_time", time);
        }
        json.add("results", results);
        data("recipe", AlteredAether.id("gravitite/create/" + type + "_" + name), json, "create");
    }

    /** A Create result: {@code chance} below 1 rolls the whole count at once, as Create's own do. */
    private static JsonObject output(String id, int count, double chance) {
        JsonObject json = new JsonObject();
        json.addProperty("id", id);
        if (count != 1) {
            json.addProperty("count", count);
        }
        if (chance < 1) {
            json.addProperty("chance", chance);
        }
        return json;
    }

    /**
     * Oritech's pulverizer only: ore to two raw, raw to dust and small dust. Its Fragment Forge, refinery and
     * centrifuge chain and its foundry are left out, the foundry because it smelts.
     */
    private void oritech() {
        pulverizer("ore", tag("c:ores/gravitite"), List.of(result(RAW, 2)));
        pulverizer("raw", tag("c:raw_materials/gravitite"), List.of(result(DUST, 1), result(SMALL_DUST, 3)));
    }

    private void pulverizer(String name, JsonObject ingredient, List<JsonObject> results) {
        JsonArray ingredients = new JsonArray();
        ingredients.add(ingredient);
        JsonArray out = new JsonArray();
        results.forEach(out::add);
        JsonObject json = new JsonObject();
        json.addProperty("type", "oritech:pulverizer");
        json.add("ingredients", ingredients);
        json.add("results", out);
        json.addProperty("time", 100);
        data("recipe", AlteredAether.id("gravitite/oritech/pulverizer_" + name), json, "oritech");
    }

    /**
     * Ars Nouveau: the imbuement chamber enchants raw, crushed or dust gravitite into the ingot, as its own chamber
     * turns amethyst into source gems; the crush spell breaks the ore into raw gravitite and sometimes zanite.
     */
    private void arsNouveau() {
        imbuement("raw", tag("c:raw_materials/gravitite"), "ars_nouveau");
        imbuement("crushed", item(CRUSHED), "ars_nouveau", "create");
        imbuement("dust", tag("c:dusts/gravitite"), "ars_nouveau", "oritech");

        JsonArray output = new JsonArray();
        output.add(crushOutput(RAW, 1.0));
        output.add(crushOutput(RAW, 0.75));
        output.add(crushOutput(ZANITE, 0.25));
        JsonObject crush = new JsonObject();
        crush.addProperty("type", "ars_nouveau:crush");
        crush.add("input", item(ORE));
        crush.add("output", output);
        data("recipe", AlteredAether.id("gravitite/ars_nouveau/crush_ore"), crush, "ars_nouveau");
    }

    private void imbuement(String name, JsonObject input, String... mods) {
        JsonObject json = new JsonObject();
        json.addProperty("type", "ars_nouveau:imbuement");
        json.add("input", input);
        json.add("output", result(INGOT, 1));
        json.add("pedestalItems", new JsonArray());
        json.addProperty("source", 500);
        data("recipe", AlteredAether.id("gravitite/ars_nouveau/imbuement_" + name), json, mods);
    }

    private static JsonObject crushOutput(String id, double chance) {
        JsonObject json = new JsonObject();
        json.addProperty("chance", chance);
        json.addProperty("maxRange", 1);
        json.add("stack", result(id, 1));
        return json;
    }

    /** Deep Aether's poison turns enchanted gravitite back into ore; now the ingot, not the block (see GravititeRework). */
    private void deepAether() {
        JsonObject json = new JsonObject();
        json.addProperty("type", "deep_aether:poison_recipe");
        json.add("ingredient", item(INGOT));
        json.add("result", result(ORE, 1));
        data("recipe", AlteredAether.id("gravitite/deep_aether/ore_from_poison"), json, "deep_aether");
    }

    // ---------------------------------------------------------------------------------------------------- Sable --

    /**
     * Lift under Sable, as levitite and end stone have it: gravitite ore and raw gravitite lightly (end stone's
     * strength), enchanted gravitite moderately, and that only while it has a redstone signal (see SableLift). Their
     * mass comes from Sable's own tiers: the two storage blocks are super heavy, as every storage block is.
     */
    private void sable() {
        floatingMaterial("gravitite", 2, 2, 0.4, 0.02, 0.3, 0.01);
        floatingMaterial("enchanted_gravitite", 5, 2.5, 1.0, 0.05, 0.8, 0.03);
        lifts("gravitite", "#" + ID + "lifts_lightly", "gravitite");
        lifts("enchanted_gravitite", BLOCK, "enchanted_gravitite");
    }

    private void floatingMaterial(String name, double lift, double transition, double slowVertical,
                                  double fastVertical, double slowHorizontal, double fastHorizontal) {
        JsonObject json = new JsonObject();
        json.addProperty("prevent_self_lift", true);
        json.addProperty("scale_friction_with_gravity", true);
        json.addProperty("lift_strength", lift);
        json.addProperty("transition_speed", transition);
        json.addProperty("slow_vertical_friction", slowVertical);
        json.addProperty("fast_vertical_friction", fastVertical);
        json.addProperty("slow_horizontal_friction", slowHorizontal);
        json.addProperty("fast_horizontal_friction", fastHorizontal);
        data("floating_materials", AlteredAether.id(name), json, "sable");
    }

    private void lifts(String name, String selector, String material) {
        JsonObject properties = new JsonObject();
        properties.addProperty("sable:floating_material", ID + material);
        JsonObject json = new JsonObject();
        json.addProperty("selector", selector);
        json.add("properties", properties);
        data("physics_block_properties", AlteredAether.id(name), json, "sable");
    }

    // ---------------------------------------------------------------------------------------------- helpers --

    private static JsonObject item(String id) {
        JsonObject json = new JsonObject();
        json.addProperty("item", id);
        return json;
    }

    private static JsonObject tag(String id) {
        JsonObject json = new JsonObject();
        json.addProperty("tag", id);
        return json;
    }

    private static JsonObject result(String id, int count) {
        JsonObject json = new JsonObject();
        json.addProperty("id", id);
        json.addProperty("count", count);
        return json;
    }

    private static JsonArray strings(List<String> values) {
        JsonArray array = new JsonArray();
        values.forEach(array::add);
        return array;
    }
}
