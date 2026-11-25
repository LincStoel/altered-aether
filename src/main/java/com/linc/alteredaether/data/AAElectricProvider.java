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
import com.linc.alteredaether.electric.WheelPonderText;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The Tempest Peaks electricity's files (see electric/ and compat/CreateElectric): models and blockstates, drops,
 * recipes, tags, the electrocution damage type, and its language lines (see {@link #language}). The models are
 * PLACEHOLDERS built from the Aether's aerogel and skyroot and the vanilla lightning rod, with the few new textures from
 * tools/textures/electricity.py. The wheel's files carry a Create condition, since the wheel exists only with Create.
 */
public final class AAElectricProvider extends JsonDataProvider {
    private static final String M = AlteredAether.MOD_ID + ":";
    private static final String AEROGEL = "aether:block/construction/aerogel";
    private static final String PLANKS = "aether:block/construction/skyroot_planks";
    private static final String LOG = "aether:block/natural/skyroot_log";
    private static final String LOG_TOP = "aether:block/natural/skyroot_log_top";
    private static final String ROD = "minecraft:block/lightning_rod";
    /** Blocks mined faster with a pickaxe, for the mineable tag (see AATagProvider); the wheel only with Create. */
    static final String[] PICKAXE = {M + "squall_insulator", M + "squall_generator", "?" + M + "aerogel_wheel"};
    /** The facings a bowl (insulator, generator) can take, and the blockstate rotation that turns its open top there. */
    private static final Map<String, int[]> FACINGS = new LinkedHashMap<>();

    static {
        FACINGS.put("up", new int[]{0, 0});
        FACINGS.put("down", new int[]{180, 0});
        FACINGS.put("north", new int[]{90, 0});
        FACINGS.put("east", new int[]{90, 90});
        FACINGS.put("south", new int[]{90, 180});
        FACINGS.put("west", new int[]{90, 270});
    }

    public AAElectricProvider(PackOutput output) {
        super(output, "electricity");
    }

    @Override
    protected void generate() {
        jar();
        bowls();
        stormAercloud();
        staticBoots();
        wheel();
        flashBlasting();
        tags();
        JsonObject damage = new JsonObject();
        damage.addProperty("exhaustion", 0.1);
        damage.addProperty("message_id", AlteredAether.MOD_ID + "_electrocution");
        damage.addProperty("scaling", "never");
        data("damage_type", AlteredAether.id("electrocution"), damage);
    }

    static void language(Map<String, String> lang) {
        String block = "block." + AlteredAether.MOD_ID + ".";
        String tip = "tooltip." + AlteredAether.MOD_ID + ".";
        String jade = "jade." + AlteredAether.MOD_ID + ".";
        lang.put(block + "aether_jar", "Aether Jar");
        lang.put(block + "squall_insulator", "Squall Insulator");
        lang.put(block + "squall_generator", "Squall Generator");
        lang.put(block + "storm_aercloud", "Storm Aercloud");
        lang.put(block + "aerogel_wheel", "Aerogel Wheel");
        lang.put("item." + AlteredAether.MOD_ID + ".static_boots", "Static Boots");
        lang.put(tip + "energy", "%s / %s FE");
        lang.put(tip + "squall_insulator", "Catch a squall: let its lightning strike the bowl, or wear it down and use this on it");
        lang.put(tip + "squall_generator", "Burns ambrosium or takes FE; charges the jar behind it and shocks what it faces");
        lang.put(tip + "static_boots", "Grounded: resists knockback, sinks faster, jumps no higher than plain");
        lang.put(tip + "aerogel_wheel", "Spun by two charged Aether Jars with lightning rods, two blocks either side");
        lang.put("death.attack." + AlteredAether.MOD_ID + "_electrocution", "%1$s was electrocuted");
        lang.put("death.attack." + AlteredAether.MOD_ID + "_electrocution.player",
                "%1$s was electrocuted while fighting %2$s");
        lang.put(jade + "energy", "Charge: %s / %s FE");
        lang.put(jade + "live_copper", "Electrifying %s copper blocks");
        lang.put(jade + "generator.fuel", "Ambrosium: %s s left");
        lang.put(jade + "generator.superheated", "Superheated: %s s left");
        lang.put(jade + "generator.idle", "Needs ambrosium or FE");
        lang.put(jade + "wheel.incomplete", "Needs an Aether Jar and a lightning rod two blocks out on each side");
        lang.put(jade + "wheel.drained", "A jar is out of charge");
        lang.put("jei." + AlteredAether.MOD_ID + ".flash_blasting", "Flash Blasting");
        lang.put("message." + AlteredAether.MOD_ID + ".squall_caught", "The squall is caught in the bowl");
        WheelPonderText.language(lang);
    }

    /**
     * The jar: aerogel, a skyroot stopper, the lightning rod's copper down its middle, and copper foil round its lower
     * half, as on a real Leyden jar, glowing brighter (at full brightness) the fuller the jar is.
     */
    private void jar() {
        JsonObject variants = new JsonObject();
        for (int charge = 0; charge <= 4; charge++) {
            JsonArray elements = new JsonArray();
            elements.add(element(new double[]{7.5, 1, 7.5}, new double[]{8.5, 16, 8.5}, "#rod", new double[]{0, 0, 2, 15}));
            JsonObject foil = element(new double[]{2.75, 0.25, 2.75}, new double[]{13.25, 6, 13.25}, "#foil", null);
            foil.getAsJsonObject("faces").remove("up");
            if (charge > 0) {
                JsonObject lit = new JsonObject();
                lit.addProperty("block_light", 15);
                lit.addProperty("sky_light", 15);
                foil.add("neoforge_data", lit);
            }
            elements.add(foil);
            elements.add(element(new double[]{3, 0, 3}, new double[]{13, 11, 13}, "#glass", null));
            elements.add(element(new double[]{4, 11, 4}, new double[]{12, 12, 12}, "#glass", null));
            elements.add(element(new double[]{6, 12, 6}, new double[]{10, 13, 10}, "#glass", null));
            elements.add(element(new double[]{5, 13, 5}, new double[]{11, 15, 11}, "#stopper", null));
            Map<String, String> textures = new LinkedHashMap<>();
            textures.put("glass", AEROGEL);
            textures.put("stopper", PLANKS);
            textures.put("rod", ROD);
            textures.put("particle", AEROGEL);
            textures.put("foil", M + "block/aether_jar_foil_" + charge);
            asset("models/block", AlteredAether.id("aether_jar_" + charge), elementModel(elements, textures, true));
            variants.add("charge=" + charge, modelRef("aether_jar_" + charge, 0, 0));
        }
        blockstate("aether_jar", variants);
        itemFromBlock("aether_jar", "aether_jar_0");
        // a broken jar keeps its charge
        JsonObject copy = new JsonObject();
        copy.addProperty("function", "minecraft:copy_components");
        copy.addProperty("source", "block_entity");
        copy.add("include", strings(M + "stored_energy"));
        blockLoot("aether_jar", copy);
        JsonObject key = new JsonObject();
        key.add("A", item("aether:aerogel"));
        key.add("S", item("aether:skyroot_slab"));
        key.add("L", item("minecraft:lightning_rod"));
        shaped("aether_jar", "redstone", key, 1, " S ", "ALA", "AAA");
    }

    /**
     * The Squall Insulator and Generator: a bowl of aerogel on a skyroot base with a skyroot rim, its open top turned
     * the way the block faces. A lit generator crackles on the bowl's floor; the squall itself is drawn by its renderer.
     */
    private void bowls() {
        asset("models/block", AlteredAether.id("squall_insulator"), elementModel(bowl(false), bowlTextures(), true));
        asset("models/block", AlteredAether.id("squall_generator"), elementModel(bowl(false), bowlTextures(), true));
        Map<String, String> lit = bowlTextures();
        lit.put("spark", M + "block/squall_generator_spark");
        asset("models/block", AlteredAether.id("squall_generator_lit"), elementModel(bowl(true), lit, true));

        JsonObject insulator = new JsonObject();
        JsonArray generator = new JsonArray();
        FACINGS.forEach((facing, rotation) -> {
            insulator.add("facing=" + facing, modelRef("squall_insulator", rotation[0], rotation[1]));
            for (boolean on : new boolean[]{false, true}) {
                JsonObject when = new JsonObject();
                when.addProperty("facing", facing);
                when.addProperty("lit", Boolean.toString(on));
                JsonObject part = new JsonObject();
                part.add("when", when);
                part.add("apply", modelRef(on ? "squall_generator_lit" : "squall_generator", rotation[0], rotation[1]));
                generator.add(part);
            }
        });
        blockstate("squall_insulator", insulator);
        JsonObject multipart = new JsonObject();
        multipart.add("multipart", generator);
        asset("blockstates", AlteredAether.id("squall_generator"), multipart);
        itemFromBlock("squall_insulator", "squall_insulator");
        itemFromBlock("squall_generator", "squall_generator_lit");
        blockLoot("squall_insulator", null);
        blockLoot("squall_generator", null);

        JsonObject key = new JsonObject();
        key.add("S", item("aether:skyroot_slab"));
        key.add("A", item("aether:aerogel"));
        key.add("P", item("aether:skyroot_planks"));
        shaped("squall_insulator", "redstone", key, 1, "S S", "A A", "PPP");
    }

    private static Map<String, String> bowlTextures() {
        Map<String, String> textures = new LinkedHashMap<>();
        textures.put("glass", AEROGEL);
        textures.put("wood", PLANKS);
        textures.put("particle", AEROGEL);
        return textures;
    }

    private static JsonArray bowl(boolean lit) {
        JsonArray elements = new JsonArray();
        elements.add(element(new double[]{2, 0, 2}, new double[]{14, 2, 14}, "#wood", null));
        elements.add(element(new double[]{2, 2, 2}, new double[]{14, 9, 3}, "#glass", null));
        elements.add(element(new double[]{2, 2, 13}, new double[]{14, 9, 14}, "#glass", null));
        elements.add(element(new double[]{2, 2, 3}, new double[]{3, 9, 13}, "#glass", null));
        elements.add(element(new double[]{13, 2, 3}, new double[]{14, 9, 13}, "#glass", null));
        elements.add(element(new double[]{1, 9, 1}, new double[]{15, 10, 3}, "#wood", null));
        elements.add(element(new double[]{1, 9, 13}, new double[]{15, 10, 15}, "#wood", null));
        elements.add(element(new double[]{1, 9, 3}, new double[]{3, 10, 13}, "#wood", null));
        elements.add(element(new double[]{13, 9, 3}, new double[]{15, 10, 13}, "#wood", null));
        if (lit) {
            JsonObject spark = new JsonObject();
            spark.add("from", numbers(3, 2.05, 3));
            spark.add("to", numbers(13, 2.1, 13));
            JsonObject faces = new JsonObject();
            JsonObject up = new JsonObject();
            up.addProperty("texture", "#spark");
            faces.add("up", up);
            spark.add("faces", faces);
            spark.addProperty("shade", false);
            elements.add(spark);
        }
        return elements;
    }

    /** A cold aercloud's shape, and its drop, in storm grey; a squall drops it (see AALootProvider). */
    private void stormAercloud() {
        JsonObject model = new JsonObject();
        model.addProperty("parent", M + "block/phased_aercloud");
        JsonObject textures = new JsonObject();
        textures.addProperty("cloud", M + "block/storm_aercloud");
        textures.addProperty("particle", M + "block/storm_aercloud");
        model.add("textures", textures);
        asset("models/block", AlteredAether.id("storm_aercloud"), model);
        JsonObject variants = new JsonObject();
        variants.add("", modelRef("storm_aercloud", 0, 0));
        blockstate("storm_aercloud", variants);
        itemFromBlock("storm_aercloud", "storm_aercloud");
        blockLoot("storm_aercloud", null);
    }

    private void staticBoots() {
        JsonObject model = new JsonObject();
        model.addProperty("parent", "minecraft:item/generated");
        JsonObject textures = new JsonObject();
        textures.addProperty("layer0", M + "item/static_boots");
        model.add("textures", textures);
        asset("models/item", AlteredAether.id("static_boots"), model);
        JsonObject json = new JsonObject();
        json.addProperty("type", "minecraft:crafting_shapeless");
        json.addProperty("category", "equipment");
        JsonArray ingredients = new JsonArray();
        ingredients.add(item("minecraft:leather_boots"));
        ingredients.add(item(M + "storm_aercloud"));
        json.add("ingredients", ingredients);
        json.add("result", result("static_boots", 1));
        data("recipe", AlteredAether.id("static_boots"), json);
    }

    /**
     * The Aerogel Wheel, Create only: a flat aerogel disc on a skyroot hub, turning on a vertical axle. The block draws
     * nothing itself; its renderer spins this model.
     */
    private void wheel() {
        JsonArray elements = new JsonArray();
        elements.add(element(new double[]{6, 0, 6}, new double[]{10, 16, 10}, "#hub", null));
        elements.add(element(new double[]{-4, 6, -4}, new double[]{20, 10, 20}, "#glass", null));
        JsonObject diagonal = element(new double[]{-4, 6.01, -4}, new double[]{20, 9.99, 20}, "#glass", null);
        JsonObject rotation = new JsonObject();
        rotation.addProperty("angle", 45);
        rotation.addProperty("axis", "y");
        rotation.add("origin", numbers(8, 8, 8));
        diagonal.add("rotation", rotation);
        elements.add(diagonal);
        elements.add(element(new double[]{4, 5, 4}, new double[]{12, 11, 12}, "#hub", null));
        Map<String, String> textures = new LinkedHashMap<>();
        textures.put("glass", AEROGEL);
        textures.put("hub", LOG);
        textures.put("particle", LOG_TOP);
        asset("models/block", AlteredAether.id("aerogel_wheel"), elementModel(elements, textures, true));
        JsonObject variants = new JsonObject();
        variants.add("", modelRef("aerogel_wheel", 0, 0));
        blockstate("aerogel_wheel", variants);
        itemFromBlock("aerogel_wheel", "aerogel_wheel");
        JsonObject loot = blockLootJson("aerogel_wheel", null);
        data("loot_table/blocks", AlteredAether.id("aerogel_wheel"), loot, "create");
        JsonObject key = new JsonObject();
        key.add("A", item("aether:aerogel"));
        JsonObject logs = new JsonObject();
        logs.addProperty("tag", "aether:skyroot_logs");
        key.add("L", logs);
        JsonObject json = shapedJson("aerogel_wheel", "redstone", key, 1, "AAA", "ALA", "AAA");
        data("recipe", AlteredAether.id("aerogel_wheel"), json, "create");
    }

    /** The Squall Generator's own flash-blasting recipes; anything vanilla blasts it blasts too. */
    private void flashBlasting() {
        flash("sand_to_glass", "minecraft:sand", "minecraft:glass");
        flash("red_sand_to_glass", "minecraft:red_sand", "minecraft:glass");
        flash("quicksoil_to_quicksoil_glass", "aether:quicksoil", "aether:quicksoil_glass");
    }

    private void flash(String name, String input, String output) {
        JsonObject json = new JsonObject();
        json.addProperty("type", M + "flash_blasting");
        json.add("ingredient", item(input));
        JsonObject result = new JsonObject();
        result.addProperty("id", output);
        result.addProperty("count", 1);
        json.add("result", result);
        data("recipe", AlteredAether.id("flash_blasting/" + name), json);
    }

    private void tags() {
        tag("item", ResourceLocation.fromNamespaceAndPath("accessories", "shoes"), M + "static_boots");
        tag("item", AlteredAether.id("squall_generator_fuel"), "aether:ambrosium_shard", "aether:ambrosium_block");
        tag("item", AlteredAether.id("superheats_squall_generator"), M + "storm_aercloud",
                "deep_aether:sterling_aercloud");
        tag("damage_type", ResourceLocation.withDefaultNamespace("no_knockback"), M + "electrocution");
        tag("damage_type", ResourceLocation.withDefaultNamespace("bypasses_armor"), M + "electrocution");
    }

    private void tag(String registry, ResourceLocation id, String... entries) {
        JsonArray values = new JsonArray();
        for (String entry : entries) {
            String namespace = entry.replace("#", "").split(":")[0];
            if (List.of("minecraft", AlteredAether.MOD_ID, "aether").contains(namespace)) {
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

    // -- JSON helpers --

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

    private void blockLoot(String block, JsonObject function) {
        data("loot_table/blocks", AlteredAether.id(block), blockLootJson(block, function));
    }

    private static JsonObject blockLootJson(String block, JsonObject function) {
        JsonObject entry = new JsonObject();
        entry.addProperty("type", "minecraft:item");
        entry.addProperty("name", M + block);
        if (function != null) {
            JsonArray functions = new JsonArray();
            functions.add(function);
            entry.add("functions", functions);
        }
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
        return json;
    }

    private void shaped(String name, String category, JsonObject key, int count, String... pattern) {
        data("recipe", AlteredAether.id(name), shapedJson(name, category, key, count, pattern));
    }

    private static JsonObject shapedJson(String name, String category, JsonObject key, int count, String... pattern) {
        JsonObject json = new JsonObject();
        json.addProperty("type", "minecraft:crafting_shaped");
        json.addProperty("category", category);
        json.add("key", key);
        json.add("pattern", strings(pattern));
        json.add("result", result(name, count));
        return json;
    }

    private static JsonObject result(String name, int count) {
        JsonObject result = new JsonObject();
        result.addProperty("id", M + name);
        result.addProperty("count", count);
        return result;
    }

    private static JsonObject item(String id) {
        JsonObject json = new JsonObject();
        json.addProperty("item", id);
        return json;
    }

    private static JsonObject modelRef(String model, int x, int y) {
        JsonObject json = new JsonObject();
        json.addProperty("model", M + "block/" + model);
        if (x != 0) {
            json.addProperty("x", x);
        }
        if (y != 0) {
            json.addProperty("y", y);
        }
        return json;
    }

    private static JsonObject elementModel(JsonArray elements, Map<String, String> textures, boolean translucent) {
        JsonObject json = new JsonObject();
        json.addProperty("parent", "minecraft:block/block");
        if (translucent) {
            json.addProperty("render_type", "minecraft:translucent");
        }
        JsonObject map = new JsonObject();
        textures.forEach(map::addProperty);
        json.add("textures", map);
        json.add("elements", elements);
        return json;
    }

    /** A box with every face drawn with {@code texture}, each face's uv its own extent (or {@code uv} for all). */
    private static JsonObject element(double[] from, double[] to, String texture, double[] uv) {
        JsonObject json = new JsonObject();
        json.add("from", numbers(from));
        json.add("to", numbers(to));
        JsonObject faces = new JsonObject();
        for (String side : new String[]{"down", "up", "north", "south", "west", "east"}) {
            JsonObject face = new JsonObject();
            face.addProperty("texture", texture);
            face.add("uv", numbers(uv != null ? uv : faceUv(side, from, to)));
            faces.add(side, face);
        }
        json.add("faces", faces);
        return json;
    }

    /** The uv a face would get automatically, clamped into the texture for boxes that spill past the block. */
    private static double[] faceUv(String side, double[] from, double[] to) {
        double[] uv = switch (side) {
            case "down", "up" -> new double[]{from[0], from[2], to[0], to[2]};
            case "north", "south" -> new double[]{from[0], 16 - to[1], to[0], 16 - from[1]};
            default -> new double[]{from[2], 16 - to[1], to[2], 16 - from[1]};
        };
        for (int i = 0; i < 4; i++) {
            uv[i] = Math.max(0, Math.min(16, uv[i]));
        }
        return uv;
    }

    private static JsonArray numbers(double... values) {
        JsonArray array = new JsonArray();
        for (double value : values) {
            array.add(value);
        }
        return array;
    }

    private static JsonArray strings(String... values) {
        JsonArray array = new JsonArray();
        for (String value : values) {
            array.add(value);
        }
        return array;
    }
}
