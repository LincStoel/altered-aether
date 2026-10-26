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

import java.util.List;

/**
 * Shared plumbing for the providers that write configured features, placed features and the biome modifiers that add
 * them. {@code requiredMods} on every call becomes a {@code mod_loaded} condition on that one file, so content for an
 * optional mod disappears whole when the mod is absent.
 */
abstract class FeatureData extends JsonDataProvider {
    static final String NS = AlteredAether.MOD_ID + ":";
    static final String IS_AETHER = "#aether:is_aether";
    private static final String CONFIGURED = "worldgen/configured_feature";
    private static final String PLACED = "worldgen/placed_feature";
    private static final String MODIFIER = "neoforge/biome_modifier/decoration";

    static final List<String> GROVES = List.of("blazing", "cascading", "flourishing", "vexing", "flashing");

    protected FeatureData(PackOutput output, String name) {
        super(output, name);
    }

    /** {@code type} is this mod's feature unless it contains a colon. */
    protected void configured(String name, String type, JsonObject config, String... requiredMods) {
        JsonObject json = new JsonObject();
        json.addProperty("type", type.contains(":") ? type : NS + type);
        json.add("config", config);
        data(CONFIGURED, AlteredAether.id(name), json, requiredMods);
    }

    protected void placed(String name, String configured, JsonArray placement, String... requiredMods) {
        JsonObject json = new JsonObject();
        json.addProperty("feature", configured.contains(":") ? configured : NS + configured);
        json.add("placement", placement);
        data(PLACED, AlteredAether.id(name), json, requiredMods);
    }

    /** A placed feature whose JSON is already built, such as a copy of one read from another mod's jar. */
    protected void placedJson(String name, JsonObject json, String... requiredMods) {
        data(PLACED, AlteredAether.id(name), json, requiredMods);
    }

    /** Adds the placed feature {@code altered_aether:<name>} to {@code biomes} at {@code step}. */
    protected void addFeature(String name, List<String> biomes, String step, String... requiredMods) {
        addFeature(name, name, biomes, step, requiredMods);
    }

    /** As above, with a modifier file named differently from the feature it adds. */
    protected void addFeature(String modifier, String feature, List<String> biomes, String step,
                              String... requiredMods) {
        JsonObject json = new JsonObject();
        json.addProperty("type", "neoforge:add_features");
        if (biomes.size() == 1) {
            json.addProperty("biomes", biomes.getFirst());
        } else {
            JsonArray list = new JsonArray();
            biomes.forEach(list::add);
            json.add("biomes", list);
        }
        json.addProperty("features", feature.contains(":") ? feature : NS + feature);
        json.addProperty("step", step);
        data(MODIFIER, AlteredAether.id(modifier.replace('/', '_')), json, requiredMods);
    }

    /** Takes {@code features} out of {@code biomes}, in the remove phase after every other mod has added its own. */
    protected void removeFeatures(String name, List<String> biomes, List<String> features, String step,
                                  String... requiredMods) {
        JsonObject json = new JsonObject();
        json.addProperty("type", "neoforge:remove_features");
        if (biomes.size() == 1) {
            json.addProperty("biomes", biomes.getFirst());
        } else {
            JsonArray biomeList = new JsonArray();
            biomes.forEach(biomeList::add);
            json.add("biomes", biomeList);
        }
        JsonArray featureList = new JsonArray();
        features.forEach(featureList::add);
        json.add("features", featureList);
        json.addProperty("steps", step);
        data(MODIFIER, AlteredAether.id(name.replace('/', '_')), json, requiredMods);
    }

    static List<String> biomes(String... names) {
        return java.util.Arrays.stream(names).map(n -> n.contains(":") ? n : NS + n).toList();
    }

    static List<String> groves(String... colours) {
        return java.util.Arrays.stream(colours).map(c -> NS + c + "_archwood_grove").toList();
    }

    // ------------------------------------------------------------------ feature config builders

    /** A vertical run of one block provider, placed only into air. */
    static JsonObject column(String direction, int minHeight, int maxHeight, JsonObject provider) {
        JsonObject layer = new JsonObject();
        layer.add("height", Providers.uniform(minHeight, maxHeight));
        layer.add("provider", provider);
        JsonArray layers = new JsonArray();
        layers.add(layer);
        JsonObject config = new JsonObject();
        config.addProperty("direction", direction);
        config.add("allowed_placement", air());
        config.addProperty("prioritize_tip", false);
        config.add("layers", layers);
        return config;
    }

    /** Several tries of a single block around the origin, each only where {@code predicate} holds. */
    static JsonObject patch(int tries, int xzSpread, int ySpread, JsonObject provider, JsonObject predicate) {
        JsonObject block = new JsonObject();
        block.addProperty("type", "minecraft:simple_block");
        JsonObject blockConfig = new JsonObject();
        blockConfig.add("to_place", provider);
        block.add("config", blockConfig);
        JsonObject filter = Placement.type("minecraft:block_predicate_filter");
        filter.add("predicate", predicate);
        JsonObject inner = new JsonObject();
        inner.add("feature", block);
        inner.add("placement", Placement.list(filter));
        JsonObject config = new JsonObject();
        config.addProperty("tries", tries);
        config.addProperty("xz_spread", xzSpread);
        config.addProperty("y_spread", ySpread);
        config.add("feature", inner);
        return config;
    }

    /** An ore blob of {@code state} in blocks of the given tag. */
    static JsonObject ore(String state, int size, double discardOnAirExposure, String replaceableTag) {
        JsonObject target = new JsonObject();
        target.addProperty("predicate_type", "minecraft:tag_match");
        target.addProperty("tag", replaceableTag);
        JsonObject entry = new JsonObject();
        entry.add("state", Substitutes.state(state));
        entry.add("target", target);
        JsonArray targets = new JsonArray();
        targets.add(entry);
        JsonObject config = new JsonObject();
        config.addProperty("discard_chance_on_air_exposure", discardOnAirExposure);
        config.addProperty("size", size);
        config.add("targets", targets);
        return config;
    }

    static JsonObject air() {
        JsonObject json = Placement.type("minecraft:matching_blocks");
        json.addProperty("blocks", "minecraft:air");
        return json;
    }

    /** Air with a block of the given tag directly beneath. */
    static JsonObject airOnTag(String tag) {
        JsonObject below = Placement.type("minecraft:matching_block_tag");
        JsonArray offset = new JsonArray();
        offset.add(0);
        offset.add(-1);
        offset.add(0);
        below.add("offset", offset);
        below.addProperty("tag", tag);
        return allOf(air(), below);
    }

    /** Air with the given block directly beneath. */
    static JsonObject airOnBlock(String block) {
        JsonObject below = Placement.type("minecraft:matching_blocks");
        JsonArray offset = new JsonArray();
        offset.add(0);
        offset.add(-1);
        offset.add(0);
        below.add("offset", offset);
        below.addProperty("blocks", block);
        return allOf(air(), below);
    }

    private static JsonObject allOf(JsonObject... predicates) {
        JsonArray list = new JsonArray();
        for (JsonObject predicate : predicates) {
            list.add(predicate);
        }
        JsonObject json = Placement.type("minecraft:all_of");
        json.add("predicates", list);
        return json;
    }

    /** The four horizontal facings of one block, equally likely. */
    static JsonObject facings(String block) {
        Object[] entries = new Object[8];
        String[] facings = {"north", "south", "east", "west"};
        for (int i = 0; i < 4; i++) {
            entries[i * 2] = block + "[facing=" + facings[i] + "]";
            entries[i * 2 + 1] = 1;
        }
        return Providers.weighted(entries);
    }

    /** {@code aercloud}: a cloud of one block. */
    static JsonObject cloud(JsonObject block, String shape, int minSize, int maxSize, int thickness) {
        JsonObject config = new JsonObject();
        config.add("block", block);
        config.addProperty("shape", shape);
        config.add("size", Providers.uniform(minSize, maxSize));
        config.addProperty("thickness", thickness);
        return config;
    }
}
