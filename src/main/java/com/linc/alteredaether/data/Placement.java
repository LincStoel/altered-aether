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
