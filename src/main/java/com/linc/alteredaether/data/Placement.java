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

import java.util.Map;

/** Placement modifier JSON builders. */
final class Placement {
    static JsonArray list(JsonObject... modifiers) {
        JsonArray array = new JsonArray();
        for (JsonObject modifier : modifiers) {
            array.add(modifier);
        }
        return array;
    }

    static JsonObject count(int count) {
        JsonObject json = type("minecraft:count");
        json.addProperty("count", count);
        return json;
    }

    static JsonObject rarity(int chance) {
        JsonObject json = type("minecraft:rarity_filter");
        json.addProperty("chance", chance);
        return json;
    }

    /** {@code count} per chunk, times a common-config multiplier (see ConfigCount). */
    static JsonObject configCount(double count, String key) {
        JsonObject json = type("altered_aether:config_count");
        json.addProperty("count", count);
        json.addProperty("key", key);
        return json;
    }

    /** Moves the position to the top of the nearest island edge column (see IslandEdge). */
    static JsonObject islandEdge(int reach, int drop) {
        JsonObject json = type("altered_aether:island_edge");
        json.addProperty("reach", reach);
        json.addProperty("drop", drop);
        return json;
    }

    /** One candidate per cell of {@code cellChunks} chunks, in a chunk chosen by the seed (see JitteredGrid). */
    static JsonObject jitteredGrid(int cellChunks, int salt, double chance) {
        JsonObject json = type("altered_aether:jittered_grid");
        json.addProperty("cell_chunks", cellChunks);
        json.addProperty("salt", salt);
        json.addProperty("chance", chance);
        return json;
    }

    /** Thins positions out toward the edge of their biome (see BiomeEdgeFalloff). */
    static JsonObject biomeEdgeFalloff(int distance, double minKeep) {
        JsonObject json = type("altered_aether:biome_edge_falloff");
        json.addProperty("distance", distance);
        json.addProperty("min_keep", minKeep);
        return json;
    }

    /** The decoration points of one kind on a type of landmark, in this chunk (see LandmarkPoints). */
    static JsonObject landmarkPoints(String landmark, String kind, double chance) {
        JsonObject json = type("altered_aether:landmark_points");
        json.addProperty("landmark", landmark);
        json.addProperty("kind", kind);
        json.addProperty("chance", chance);
        return json;
    }

    /** As {@link #landmarkPoints}, each hung between {@code min} and {@code max} blocks below the ground around it. */
    static JsonObject landmarkPoints(String landmark, String kind, double chance, int min, int max) {
        JsonObject json = landmarkPoints(landmark, kind, chance);
        JsonArray hang = new JsonArray();
        hang.add(min);
        hang.add(max);
        json.add("hang", hang);
        return json;
    }

    /** Moves to the air block under a ledge above (see Overhang). */
    static JsonObject overhang(int search, int clearance) {
        JsonObject json = type("altered_aether:overhang");
        json.addProperty("search", search);
        json.addProperty("clearance", clearance);
        return json;
    }

    /** Keeps positions hanging in the open between land masses (see RiftGap). */
    static JsonObject riftGap(int reach, int clearance, int sides) {
        JsonObject json = type("altered_aether:rift_gap");
        json.addProperty("reach", reach);
        json.addProperty("clearance", clearance);
        json.addProperty("sides", sides);
        return json;
    }

    /** A random offset of up to {@code xz} blocks sideways and between {@code yMin} and {@code yMax} vertically. */
    static JsonObject randomOffset(int xz, int yMin, int yMax) {
        JsonObject json = type("minecraft:random_offset");
        json.add("xz_spread", uniform(-xz, xz));
        json.add("y_spread", uniform(yMin, yMax));
        return json;
    }

    /** A height from {@code above_bottom} offsets, uniform. */
    static JsonObject heightRange(int minAboveBottom, int maxAboveBottom) {
        JsonObject json = type("minecraft:height_range");
        JsonObject range = type("minecraft:uniform");
        JsonObject min = new JsonObject();
        min.addProperty("above_bottom", minAboveBottom);
        JsonObject max = new JsonObject();
        max.addProperty("above_bottom", maxAboveBottom);
        range.add("min_inclusive", min);
        range.add("max_inclusive", max);
        json.add("height", range);
        return json;
    }

    static JsonObject aboveBottom(int offset) {
        JsonObject json = new JsonObject();
        json.addProperty("above_bottom", offset);
        return json;
    }

    /** An absolute height, in stock-Aether terms; the tall Aether stretches it (see TallRemap). */
    static JsonObject absolute(int y) {
        JsonObject json = new JsonObject();
        json.addProperty("absolute", y);
        return json;
    }

    /** A height between two anchors, drawn {@code shape} ("uniform" or "trapezoid"). */
    static JsonObject heightRange(String shape, JsonObject min, JsonObject max) {
        JsonObject range = type("minecraft:" + shape);
        range.add("min_inclusive", min);
        range.add("max_inclusive", max);
        JsonObject json = type("minecraft:height_range");
        json.add("height", range);
        return json;
    }

    static JsonObject uniform(int min, int max) {
        JsonObject json = type("minecraft:uniform");
        json.addProperty("min_inclusive", min);
        json.addProperty("max_inclusive", max);
        return json;
    }

    static JsonObject inSquare() {
        return type("minecraft:in_square");
    }

    static JsonObject biome() {
        return type("minecraft:biome");
    }

    /**
     * Heightmap placement fails closed in the Aether: a column with no terrain reports the world floor, and the
     * modifier then emits nothing, so features never pool at the bottom of the world or hang in open sky.
     */
    static JsonObject heightmap(String heightmap) {
        JsonObject json = type("minecraft:heightmap");
        json.addProperty("heightmap", heightmap);
        return json;
    }

    static JsonObject surfaceWaterDepth(int maxDepth) {
        JsonObject json = type("minecraft:surface_water_depth_filter");
        json.addProperty("max_water_depth", maxDepth);
        return json;
    }

    static JsonObject wouldSurvive(String block, Map<String, String> properties) {
        JsonObject state = new JsonObject();
        state.addProperty("Name", block);
        if (!properties.isEmpty()) {
            JsonObject props = new JsonObject();
            properties.forEach(props::addProperty);
            state.add("Properties", props);
        }
        JsonObject predicate = type("minecraft:would_survive");
        predicate.add("state", state);
        JsonObject json = type("minecraft:block_predicate_filter");
        json.add("predicate", predicate);
        return json;
    }

    static JsonObject type(String type) {
        JsonObject json = new JsonObject();
        json.addProperty("type", type);
        return json;
    }

    private Placement() {
    }
}
