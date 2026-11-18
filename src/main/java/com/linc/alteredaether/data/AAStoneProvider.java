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
import com.linc.alteredaether.block.StoneSet;
import com.linc.alteredaether.registry.AARegistries;
import net.minecraft.data.PackOutput;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The stone sets' (see block/StoneSet) block states, models, loot and recipes, made as vanilla's own stone sets are:
 * stairs, slabs and walls crafted and stonecut, polished stone from the stone and bricks from polished, each also cut
 * from anything before it. The natural stone double drops as holystone does. Their tags are in AATagProvider and their
 * names in AABlockProvider's language file. Textures: tools/textures/aether_stones.py.
 */
public final class AAStoneProvider extends JsonDataProvider {
    private static final String NS = AlteredAether.MOD_ID + ":";

    public AAStoneProvider(PackOutput output) {
        super(output, "stones");
    }

    @Override
    protected void generate() {
        for (StoneSet stone : StoneSet.ALL) {
            for (StoneSet.Form form : stone.forms()) {
                assets(form);
                loot(form, form == stone.natural);
                shapeRecipes(form);
            }
            grid(stone.natural.base(), stone.polished.base(), 4, "##", "##");
            grid(stone.polished.base(), stone.bricks.base(), 4, "##", "##");
            List<StoneSet.Form> forms = stone.forms();
            for (int from = 0; from < forms.size(); from++) {
                for (int to = from; to < forms.size(); to++) {
                    StoneSet.Form source = forms.get(from);
                    StoneSet.Form target = forms.get(to);
                    if (to > from) {
                        cut(source.base(), target.base(), 1);
                    }
                    cut(source.base(), target.shape() + "_stairs", 1);
                    cut(source.base(), target.shape() + "_slab", 2);
                    cut(source.base(), target.shape() + "_wall", 1);
                }
            }
        }
        celeziteCrushing();
        sable();
    }

    /**
     * Mass under Sable, on its own scale (normal 1, heavy 2, super heavy 4): holystone and every other stone in
     * {@code #c:stones} is heavy. Celezite, a quartzite, weighs what any stone does, so it is heavy, polished and bricks
     * as much as the stone. Nadirite, the dense rock of island undersides, is super heavy, weighing what a block of iron
     * does. Stairs, slabs and walls weigh half the block, and a double slab the whole. Sable's own tiers put slabs and
     * stairs in "light" and the natural stones (by {@code #c:stones}) in "heavy" at its default priority, so ours are
     * set a step above to win over both.
     */
    private static final Map<String, Double> SABLE_MASS = Map.of("nadirite", 4.0, "celezite", 2.0);

    private void sable() {
        for (StoneSet stone : StoneSet.ALL) {
            double mass = SABLE_MASS.get(stone.name);
            List<String> full = new ArrayList<>();
            List<String> shapes = new ArrayList<>();
            for (StoneSet.Form form : stone.forms()) {
                full.add(NS + form.base());
                for (String shape : new String[]{"_stairs", "_slab", "_wall"}) {
                    shapes.add(NS + form.shape() + shape);
                }
            }
            String name = "sable/" + stone.name;
            blockTag(name, full);
            blockTag(name + "_shapes", shapes);
            mass(name, mass, null);
            mass(name + "_shapes", mass / 2, mass);
        }
    }

    private void blockTag(String name, List<String> blocks) {
        JsonArray values = new JsonArray();
        blocks.forEach(values::add);
        JsonObject json = new JsonObject();
        json.addProperty("replace", false);
        json.add("values", values);
        data("tags/block", AlteredAether.id(name), json);
    }

    /** {@code doubled}: a double slab's mass, or null for blocks without one. */
    private void mass(String tag, double mass, Double doubled) {
        JsonObject properties = new JsonObject();
        properties.addProperty("sable:mass", mass);
        JsonObject json = new JsonObject();
        json.addProperty("priority", 1001);
        json.addProperty("selector", "#" + NS + tag);
        json.add("properties", properties);
        if (doubled != null) {
            JsonObject full = new JsonObject();
            full.addProperty("sable:mass", doubled);
            JsonObject overrides = new JsonObject();
            overrides.add("type=double", full);
            json.add("overrides", overrides);
        }
        data("physics_block_properties", AlteredAether.id(tag.replace("sable/", "")), json, "sable");
    }

    /** Names for the language file: "Nadirite", "Polished Nadirite Stairs", "Nadirite Brick Wall" and so on. */
    public static void language(Map<String, String> lang) {
        for (StoneSet stone : StoneSet.ALL) {
            for (StoneSet.Form form : stone.forms()) {
                lang.put("block." + AlteredAether.MOD_ID + "." + form.base(), title(form.base()));
                for (String shape : new String[]{"_stairs", "_slab", "_wall"}) {
                    lang.put("block." + AlteredAether.MOD_ID + "." + form.shape() + shape, title(form.shape() + shape));
                }
            }
        }
    }

    /** Every block's id, for tags. {@code shapes} picks out the stairs, slabs or walls alone. */
    public static String[] ids(String shapes) {
        List<String> ids = new ArrayList<>();
        for (StoneSet stone : StoneSet.ALL) {
            for (StoneSet.Form form : stone.forms()) {
                if (shapes == null) {
                    ids.add(NS + form.base());
                    for (String shape : new String[]{"_stairs", "_slab", "_wall"}) {
                        ids.add(NS + form.shape() + shape);
                    }
                } else {
                    ids.add(NS + form.shape() + "_" + shapes);
                }
            }
        }
        return ids.toArray(String[]::new);
    }

    /** The natural stones' ids. */
    public static String[] naturalIds() {
        return StoneSet.ALL.stream().map(stone -> NS + stone.natural.base()).toArray(String[]::new);
    }

    // ---- block states and models

    private void assets(StoneSet.Form form) {
        String texture = NS + "block/" + form.base();
        String full = NS + "block/" + form.base();
        asset("models/block", AlteredAether.id(form.base()), model("minecraft:block/cube_all", Map.of("all", texture)));
        asset("blockstates", AlteredAether.id(form.base()), variants(Map.of("", full)));
        item(form.base(), full);

        String stairs = form.shape() + "_stairs";
        Map<String, String> sides = Map.of("bottom", texture, "top", texture, "side", texture);
        for (String kind : new String[]{"", "_inner", "_outer"}) {
            String parent = "minecraft:block/" + (kind.isEmpty() ? "stairs" : kind.substring(1) + "_stairs");
            asset("models/block", AlteredAether.id(stairs + kind), model(parent, sides));
        }
        asset("blockstates", AlteredAether.id(stairs), stairsState(NS + "block/" + stairs));
        item(stairs, NS + "block/" + stairs);

        String slab = form.shape() + "_slab";
        asset("models/block", AlteredAether.id(slab), model("minecraft:block/slab", sides));
        asset("models/block", AlteredAether.id(slab + "_top"), model("minecraft:block/slab_top", sides));
        asset("blockstates", AlteredAether.id(slab), variants(Map.of("type=bottom", NS + "block/" + slab,
                "type=top", NS + "block/" + slab + "_top", "type=double", full)));
        item(slab, NS + "block/" + slab);

        String wall = form.shape() + "_wall";
        Map<String, String> wallTexture = Map.of("wall", texture);
        asset("models/block", AlteredAether.id(wall + "_post"), model("minecraft:block/template_wall_post", wallTexture));
        asset("models/block", AlteredAether.id(wall + "_side"), model("minecraft:block/template_wall_side", wallTexture));
        asset("models/block", AlteredAether.id(wall + "_side_tall"),
                model("minecraft:block/template_wall_side_tall", wallTexture));
        asset("models/block", AlteredAether.id(wall + "_inventory"), model("minecraft:block/wall_inventory", wallTexture));
        asset("blockstates", AlteredAether.id(wall), wallState(NS + "block/" + wall));
        item(wall, NS + "block/" + wall + "_inventory");
    }

    private void item(String name, String model) {
        asset("models/item", AlteredAether.id(name), model(model, Map.of()));
    }

    /** Vanilla's stairs block state: four facings, two halves, five shapes, the model turned to match. */
    private static JsonObject stairsState(String model) {
        String[] facings = {"east", "south", "west", "north"};
        JsonObject variants = new JsonObject();
        for (int f = 0; f < facings.length; f++) {
            for (String half : new String[]{"bottom", "top"}) {
                for (String shape : new String[]{"straight", "inner_left", "inner_right", "outer_left", "outer_right"}) {
                    boolean top = half.equals("top");
                    int y = f * 90;
                    // vanilla's rule: left-hand corners turn a quarter back, and upside-down right-hand ones a quarter on
                    if (shape.endsWith("left") && !top) {
                        y -= 90;
                    }
                    if (shape.endsWith("right") && top) {
                        y += 90;
                    }
                    y = Math.floorMod(y, 360);
                    JsonObject variant = new JsonObject();
                    variant.addProperty("model", model + (shape.startsWith("inner") ? "_inner"
                            : shape.startsWith("outer") ? "_outer" : ""));
                    if (top) {
                        variant.addProperty("x", 180);
                    }
                    if (y != 0) {
                        variant.addProperty("y", y);
                    }
                    if (top || y != 0) {
                        variant.addProperty("uvlock", true);
                    }
                    variants.add("facing=" + facings[f] + ",half=" + half + ",shape=" + shape, variant);
                }
            }
        }
        JsonObject json = new JsonObject();
        json.add("variants", variants);
        return json;
    }

    /** Vanilla's wall block state: a post when up, low and tall sides turned to each direction. */
    private static JsonObject wallState(String model) {
        JsonArray multipart = new JsonArray();
        multipart.add(part("up", "true", model + "_post", 0));
        String[] directions = {"north", "east", "south", "west"};
        for (String height : new String[]{"low", "tall"}) {
            for (int i = 0; i < directions.length; i++) {
                multipart.add(part(directions[i], height, model + (height.equals("low") ? "_side" : "_side_tall"), i * 90));
            }
        }
        JsonObject json = new JsonObject();
        json.add("multipart", multipart);
        return json;
    }

    private static JsonObject part(String property, String value, String model, int y) {
        JsonObject when = new JsonObject();
        when.addProperty(property, value);
        JsonObject apply = new JsonObject();
        apply.addProperty("model", model);
        if (y != 0) {
            apply.addProperty("y", y);
            apply.addProperty("uvlock", true);
        }
        JsonObject json = new JsonObject();
        json.add("when", when);
        json.add("apply", apply);
        return json;
    }

    private static JsonObject variants(Map<String, String> models) {
        JsonObject variants = new JsonObject();
        models.forEach((key, model) -> {
            JsonObject variant = new JsonObject();
            variant.addProperty("model", model);
            variants.add(key, variant);
        });
        JsonObject json = new JsonObject();
        json.add("variants", variants);
        return json;
    }

    private static JsonObject model(String parent, Map<String, String> textures) {
        JsonObject json = new JsonObject();
        json.addProperty("parent", parent);
        if (!textures.isEmpty()) {
            JsonObject map = new JsonObject();
            textures.keySet().stream().sorted().forEach(key -> map.addProperty(key, textures.get(key)));
            json.add("textures", map);
        }
        return json;
    }

    // ---- loot

    private void loot(StoneSet.Form form, boolean natural) {
        JsonObject full = selfDrop(form.base());
        if (natural) {
            // holystone's own: two for one when mined with a skyroot pickaxe from where it generated
            JsonObject doubleDrops = new JsonObject();
            doubleDrops.addProperty("function", "aether:double_drops");
            JsonArray functions = new JsonArray();
            functions.add(doubleDrops);
            full.add("functions", functions);
        }
        data("loot_table/blocks", AlteredAether.id(form.base()), full);
        data("loot_table/blocks", AlteredAether.id(form.shape() + "_stairs"), selfDrop(form.shape() + "_stairs"));
        data("loot_table/blocks", AlteredAether.id(form.shape() + "_wall"), selfDrop(form.shape() + "_wall"));
        data("loot_table/blocks", AlteredAether.id(form.shape() + "_slab"), slabDrop(form.shape() + "_slab"));
    }

    private static JsonObject selfDrop(String name) {
        JsonObject condition = new JsonObject();
        condition.addProperty("condition", "minecraft:survives_explosion");
        JsonArray conditions = new JsonArray();
        conditions.add(condition);
        return table(name, conditions, null);
    }

    /** Two from a double slab, as vanilla's slabs. */
    private static JsonObject slabDrop(String name) {
        JsonObject properties = new JsonObject();
        properties.addProperty("type", "double");
        JsonObject isDouble = new JsonObject();
        isDouble.addProperty("condition", "minecraft:block_state_property");
        isDouble.addProperty("block", NS + name);
        isDouble.add("properties", properties);
        JsonArray when = new JsonArray();
        when.add(isDouble);
        JsonObject count = new JsonObject();
        count.addProperty("function", "minecraft:set_count");
        count.addProperty("count", 2.0);
        count.addProperty("add", false);
        count.add("conditions", when);
        JsonObject decay = new JsonObject();
        decay.addProperty("function", "minecraft:explosion_decay");
        JsonArray functions = new JsonArray();
        functions.add(count);
        functions.add(decay);
        return table(name, null, functions);
    }

    private static JsonObject table(String name, JsonArray poolConditions, JsonArray entryFunctions) {
        JsonObject entry = new JsonObject();
        entry.addProperty("type", "minecraft:item");
        entry.addProperty("name", NS + name);
        if (entryFunctions != null) {
            entry.add("functions", entryFunctions);
        }
        JsonArray entries = new JsonArray();
        entries.add(entry);
        JsonObject pool = new JsonObject();
        pool.addProperty("rolls", 1);
        pool.addProperty("bonus_rolls", 0);
        if (poolConditions != null) {
            pool.add("conditions", poolConditions);
        }
        pool.add("entries", entries);
        JsonArray pools = new JsonArray();
        pools.add(pool);
        JsonObject json = new JsonObject();
        json.addProperty("type", "minecraft:block");
        json.add("pools", pools);
        json.addProperty("random_sequence", NS + "blocks/" + name);
        return json;
    }

    // ---- recipes

    private void shapeRecipes(StoneSet.Form form) {
        grid(form.base(), form.shape() + "_stairs", 4, "#  ", "## ", "###");
        grid(form.base(), form.shape() + "_slab", 6, "###");
        grid(form.base(), form.shape() + "_wall", 6, "###", "###");
    }

    private void grid(String input, String output, int count, String... pattern) {
        JsonObject item = new JsonObject();
        item.addProperty("item", NS + input);
        JsonObject key = new JsonObject();
        key.add("#", item);
        JsonArray rows = new JsonArray();
        for (String row : pattern) {
            rows.add(row);
        }
        JsonObject json = new JsonObject();
        json.addProperty("type", "minecraft:crafting_shaped");
        json.addProperty("category", "building");
        json.add("key", key);
        json.add("pattern", rows);
        json.add("result", result(output, count));
        data("recipe", AlteredAether.id(output), json);
    }

    private void cut(String input, String output, int count) {
        JsonObject ingredient = new JsonObject();
        ingredient.addProperty("item", NS + input);
        JsonObject json = new JsonObject();
        json.addProperty("type", "minecraft:stonecutting");
        json.add("ingredient", ingredient);
        json.add("result", result(output, count));
        data("recipe", AlteredAether.id(output + "_from_" + input + "_stonecutting"), json);
    }

    private static JsonObject result(String name, int count) {
        JsonObject json = new JsonObject();
        json.addProperty("id", NS + name);
        json.addProperty("count", count);
        return json;
    }

    /** Quartzite is nearly all quartz: Create's crushing wheels grind celezite to quicksoil, with now and then some quartz. */
    private void celeziteCrushing() {
        JsonObject ingredient = new JsonObject();
        ingredient.addProperty("item", NS + AARegistries.CELEZITE.natural.base());
        JsonArray ingredients = new JsonArray();
        ingredients.add(ingredient);
        JsonObject quicksoil = new JsonObject();
        quicksoil.addProperty("id", "aether:quicksoil");
        JsonObject quartz = new JsonObject();
        quartz.addProperty("id", "minecraft:quartz");
        quartz.addProperty("chance", 0.1);
        JsonArray results = new JsonArray();
        results.add(quicksoil);
        results.add(quartz);
        JsonObject json = new JsonObject();
        json.addProperty("type", "create:crushing");
        json.add("ingredients", ingredients);
        json.addProperty("processing_time", 250);
        json.add("results", results);
        data("recipe", AlteredAether.id("create/crushing/celezite"), json, "create");
    }

    private static String title(String id) {
        StringBuilder out = new StringBuilder();
        for (String word : id.split("_")) {
            out.append(out.isEmpty() ? "" : " ").append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.toString();
    }
}
