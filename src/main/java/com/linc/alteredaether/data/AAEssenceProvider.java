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
import com.linc.alteredaether.essence.CapstoneGem;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;

/**
 * Essence Channels' and Wellsprings' files (see essence/): the orbs' bare models (their renderer draws them), the
 * capstone with a model for each gem, the plinth, the phial and the Dowsing Rod with its dip, drops, recipes, the
 * infusion recipes, tags, the motes' particle definitions, and the language lines ({@link #language}). The textures
 * come from tools/textures/essence.py.
 */
public final class AAEssenceProvider extends JsonDataProvider {
    private static final String M = AlteredAether.MOD_ID + ":";
    /** Blocks mined faster with a pickaxe, for the mineable tag (see AATagProvider). */
    static final String[] PICKAXE = {M + "essence_capstone", M + "essence_plinth"};
    /** The Dowsing Rod's model for each dip, from upright (0) to bowed right down (3). */
    private static final int DIPS = 4;

    public AAEssenceProvider(PackOutput output) {
        super(output, "essence");
    }

    @Override
    protected void generate() {
        orbs();
        capstone();
        plinth();
        items();
        infusion();
        tags();
        particles();
    }

    static void language(Map<String, String> lang) {
        String block = "block." + AlteredAether.MOD_ID + ".";
        String item = "item." + AlteredAether.MOD_ID + ".";
        String tip = "tooltip." + AlteredAether.MOD_ID + ".";
        String jade = "jade." + AlteredAether.MOD_ID + ".";
        lang.put(block + "essence_channel", "Essence Channel");
        lang.put(block + "essence_wellspring", "Essence Wellspring");
        lang.put(block + "essence_capstone", "Essence Capstone");
        lang.put(block + "essence_plinth", "Essence Plinth");
        lang.put(item + "quintessence_phial", "Quintessence Phial");
        lang.put(item + "dowsing_rod", "Dowsing Rod");
        lang.put("effect." + AlteredAether.MOD_ID + ".quintessence", "Quintessence");
        lang.put(tip + "phial.channel", "Drawn from an Essence Channel");
        lang.put(tip + "phial.wellspring", "Drawn from an Essence Wellspring");
        lang.put(tip + "essence_capstone", "Set level with an essence source, 3 to 6 blocks out; needs a gem");
        lang.put(tip + "essence_plinth", "Set below an essence source: charges Aether Jars and infuses items");
        lang.put(tip + "dowsing_rod", "Dips toward the nearest essence source");
        lang.put(jade + "essence.flow", "Flow: %s Æ/t");
        lang.put(jade + "essence.capture", "Circle: %s of 8 capstones");
        lang.put(jade + "essence.charging", "Charging %s jars");
        lang.put(jade + "essence.plinth", "Infusing: %s / %s Æ");
        lang.put("jei." + AlteredAether.MOD_ID + ".essence_infusion", "Essence Infusion");
        lang.put("jei." + AlteredAether.MOD_ID + ".essence_infusion.cost", "%s Æ");
        lang.put("jei." + AlteredAether.MOD_ID + ".essence_infusion.source", "Stronger from a Wellspring");
    }

    /** The orbs draw nothing themselves; the model only gives their breaking particles a texture. */
    private void orbs() {
        JsonObject model = new JsonObject();
        JsonObject textures = new JsonObject();
        textures.addProperty("particle", M + "block/essence_orb");
        model.add("textures", textures);
        asset("models/block", AlteredAether.id("essence_orb"), model);
        for (String orb : new String[]{"essence_channel", "essence_wellspring"}) {
            JsonObject variants = new JsonObject();
            variants.add("", modelRef("essence_orb"));
            blockstate(orb, variants);
        }
    }

    /**
     * The capstone: smooth celezite with two rings round a gem spot on each side and a frame top and bottom, the top
     * and bottom faces framed too; each gem has its own side texture.
     */
    private void capstone() {
        JsonObject variants = new JsonObject();
        for (CapstoneGem gem : CapstoneGem.values()) {
            String name = "essence_capstone_" + gem.getSerializedName();
            JsonObject model = new JsonObject();
            model.addProperty("parent", "minecraft:block/cube_column");
            JsonObject textures = new JsonObject();
            textures.addProperty("side", M + "block/" + name);
            textures.addProperty("end", M + "block/essence_capstone_end");
            model.add("textures", textures);
            asset("models/block", AlteredAether.id(name), model);
            variants.add("gem=" + gem.getSerializedName(), modelRef(name));
        }
        blockstate("essence_capstone", variants);
        itemFromBlock("essence_capstone", "essence_capstone_none");
        blockLoot("essence_capstone");
        JsonObject key = new JsonObject();
        key.add("P", item(M + "polished_celezite"));
        key.add("C", item(M + "chiseled_celezite"));
        shaped("essence_capstone", "building", key, 2, "PPP", "PCP", "PPP");
    }

    /**
     * The plinth: a squared-off golem tablet laid flat, a celezite body 12 wide and 13 tall on a polished nadirite
     * frame a pixel wider all round and two tall.
     */
    private void plinth() {
        JsonArray elements = new JsonArray();
        elements.add(box(new double[]{2, 0, 2}, new double[]{14, 13, 14}, "#side", "#top", "#top"));
        // the frame's four bars, standing a pixel proud of the body's foot
        elements.add(box(new double[]{1, 0, 1}, new double[]{15, 2, 2}, "#frame", "#frame", "#frame"));
        elements.add(box(new double[]{1, 0, 14}, new double[]{15, 2, 15}, "#frame", "#frame", "#frame"));
        elements.add(box(new double[]{1, 0, 2}, new double[]{2, 2, 14}, "#frame", "#frame", "#frame"));
        elements.add(box(new double[]{14, 0, 2}, new double[]{15, 2, 14}, "#frame", "#frame", "#frame"));
        JsonObject model = new JsonObject();
        model.addProperty("parent", "minecraft:block/block");
        JsonObject textures = new JsonObject();
        textures.addProperty("side", M + "block/essence_plinth_side");
        textures.addProperty("top", M + "block/essence_plinth_top");
        textures.addProperty("frame", M + "block/polished_nadirite");
        textures.addProperty("particle", M + "block/essence_plinth_top");
        model.add("textures", textures);
        model.add("elements", elements);
        asset("models/block", AlteredAether.id("essence_plinth"), model);
        JsonObject variants = new JsonObject();
        variants.add("", modelRef("essence_plinth"));
        blockstate("essence_plinth", variants);
        itemFromBlock("essence_plinth", "essence_plinth");
        blockLoot("essence_plinth");
        JsonObject key = new JsonObject();
        key.add("C", item(M + "polished_celezite"));
        key.add("N", item(M + "polished_nadirite"));
        shaped("essence_plinth", "building", key, 1, "CCC", "CCC", "NNN");
    }

    private void items() {
        asset("models/item", AlteredAether.id("quintessence_phial"), generated("quintessence_phial"));
        JsonObject rod = generated("dowsing_rod_0");
        rod.addProperty("parent", "minecraft:item/handheld");
        JsonArray overrides = new JsonArray();
        for (int dip = 1; dip < DIPS; dip++) {
            JsonObject predicate = new JsonObject();
            predicate.addProperty(M + "dip", (dip - 0.5) / (DIPS - 1));
            JsonObject override = new JsonObject();
            override.add("predicate", predicate);
            override.addProperty("model", M + "item/dowsing_rod_" + dip);
            overrides.add(override);
            JsonObject frame = generated("dowsing_rod_" + dip);
            frame.addProperty("parent", "minecraft:item/handheld");
            asset("models/item", AlteredAether.id("dowsing_rod_" + dip), frame);
        }
        rod.add("overrides", overrides);
        asset("models/item", AlteredAether.id("dowsing_rod"), rod);
        JsonObject key = new JsonObject();
        key.add("S", item("aether:skyroot_stick"));
        key.add("A", item("aether:ambrosium_shard"));
        shaped("dowsing_rod", "tools", key, 1, "S S", " A ", " S ");
    }

    /** The infusions an essence plinth works out of the box. */
    private void infusion() {
        infusion("holystone_to_celezite", "aether:holystone", M + "celezite", 2_000, false);
        infusion("quintessence_phial", "minecraft:glass_bottle", M + "quintessence_phial", 100_000, true);
    }

    private void infusion(String name, String input, String output, int cost, boolean keepSource) {
        JsonObject json = new JsonObject();
        json.addProperty("type", M + "essence_infusion");
        json.add("ingredient", item(input));
        JsonObject result = new JsonObject();
        result.addProperty("id", output);
        result.addProperty("count", 1);
        json.add("result", result);
        json.addProperty("cost", cost);
        if (keepSource) {
            json.addProperty("keep_source", true);
        }
        data("recipe", AlteredAether.id("essence_infusion/" + name), json);
    }

    private void tags() {
        tag("item", AlteredAether.id("capstone_gems"), "aether:zanite_gemstone", "deep_aether:skyjade",
                M + "enchanted_gravitite_ingot", "aether:ambrosium_shard");
        tag("block", AlteredAether.id("essence_quickened"), "#minecraft:crops", "#minecraft:saplings",
                "aether:berry_bush_stem");
        // the orbs never move: NeoForge's tag, which Create and Sable's assembly also honour, and Create's own
        String[] orbs = {M + "essence_channel", M + "essence_wellspring"};
        tag("block", ResourceLocation.fromNamespaceAndPath("c", "relocation_not_supported"), orbs);
        tag("block", ResourceLocation.fromNamespaceAndPath("create", "non_movable"), orbs);
    }

    /** The notes are four sprites that twinkle; the motes two. */
    private void particles() {
        asset("particles", AlteredAether.id("essence_note"), particle("essence_note", 4));
        asset("particles", AlteredAether.id("essence_mote"), particle("essence_mote", 2));
    }

    private static JsonObject particle(String name, int frames) {
        JsonArray textures = new JsonArray();
        for (int i = 0; i < frames; i++) {
            textures.add(M + name + "_" + i);
        }
        JsonObject json = new JsonObject();
        json.add("textures", textures);
        return json;
    }

    // -- JSON helpers --

    private void tag(String registry, ResourceLocation id, String... entries) {
        JsonArray values = new JsonArray();
        for (String entry : entries) {
            String namespace = entry.replace("#", "").split(":")[0];
            if (List.of("minecraft", AlteredAether.MOD_ID, "aether", "c").contains(namespace)) {
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

    /** A box with {@code side} on its four sides and {@code top}/{@code bottom} on the ends, uvs from its extent. */
    private static JsonObject box(double[] from, double[] to, String side, String top, String bottom) {
        JsonObject json = new JsonObject();
        json.add("from", numbers(from));
        json.add("to", numbers(to));
        JsonObject faces = new JsonObject();
        for (String face : new String[]{"down", "up", "north", "south", "west", "east"}) {
            JsonObject entry = new JsonObject();
            entry.addProperty("texture", face.equals("up") ? top : face.equals("down") ? bottom : side);
            double[] uv = switch (face) {
                case "down", "up" -> new double[]{from[0], from[2], to[0], to[2]};
                case "north", "south" -> new double[]{from[0], 16 - to[1], to[0], 16 - from[1]};
                default -> new double[]{from[2], 16 - to[1], to[2], 16 - from[1]};
            };
            entry.add("uv", numbers(uv));
            if (face.equals("down")) {
                entry.addProperty("cullface", "down");
            }
            faces.add(face, entry);
        }
        json.add("faces", faces);
        return json;
    }

    private static JsonObject generated(String texture) {
        JsonObject model = new JsonObject();
        model.addProperty("parent", "minecraft:item/generated");
        JsonObject textures = new JsonObject();
        textures.addProperty("layer0", M + "item/" + texture);
        model.add("textures", textures);
        return model;
    }

    private void blockstate(String name, JsonObject variants) {
        JsonObject json = new JsonObject();
        json.add("variants", variants);
        asset("blockstates", AlteredAether.id(name), json);
    }

    private void itemFromBlock(String item, String blockModel) {
        JsonObject model = new JsonObject();
        model.addProperty("parent", M + "block/" + blockModel);
        asset("models/item", AlteredAether.id(item), model);
    }

    private void blockLoot(String block) {
        JsonObject entry = new JsonObject();
        entry.addProperty("type", "minecraft:item");
        entry.addProperty("name", M + block);
        JsonObject condition = new JsonObject();
        condition.addProperty("condition", "minecraft:survives_explosion");
        JsonObject pool = new JsonObject();
        pool.addProperty("rolls", 1);
        JsonArray conditions = new JsonArray();
        conditions.add(condition);
        pool.add("conditions", conditions);
        JsonArray entries = new JsonArray();
        entries.add(entry);
        pool.add("entries", entries);
        JsonObject json = new JsonObject();
        json.addProperty("type", "minecraft:block");
        JsonArray pools = new JsonArray();
        pools.add(pool);
        json.add("pools", pools);
        json.addProperty("random_sequence", M + "blocks/" + block);
        data("loot_table/blocks", AlteredAether.id(block), json);
    }

    private void shaped(String name, String category, JsonObject key, int count, String... pattern) {
        JsonObject json = new JsonObject();
        json.addProperty("type", "minecraft:crafting_shaped");
        json.addProperty("category", category);
        json.add("key", key);
        JsonArray rows = new JsonArray();
        for (String row : pattern) {
            rows.add(row);
        }
        json.add("pattern", rows);
        JsonObject result = new JsonObject();
        result.addProperty("id", M + name);
        result.addProperty("count", count);
        json.add("result", result);
        data("recipe", AlteredAether.id(name), json);
    }

    private static JsonObject item(String id) {
        JsonObject json = new JsonObject();
        json.addProperty("item", id);
        return json;
    }

    private static JsonObject modelRef(String model) {
        JsonObject json = new JsonObject();
        json.addProperty("model", M + "block/" + model);
        return json;
    }

    private static JsonArray numbers(double... values) {
        JsonArray array = new JsonArray();
        for (double value : values) {
            array.add(value);
        }
        return array;
    }
}
