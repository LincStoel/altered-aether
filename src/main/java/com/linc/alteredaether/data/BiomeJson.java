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
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Map;
import java.util.Set;

/**
 * Edits a biome JSON cloned from a template. Every biome starts as {@code aether:skyroot_forest}, so the Aether's
 * spawners, spawn costs, ores and feature skeleton come along untouched (which also keeps Overworld content out).
 */
final class BiomeJson {
    /** Vanilla GenerationStep.Decoration indices. */
    static final int RAW = 0, LAKES = 1, LOCAL = 2, SURFACE = 4, ORES = 6, UNDERGROUND = 7, SPRINGS = 8, VEG = 9,
            TOP = 10;

    final JsonObject json;

    BiomeJson(JsonObject template) {
        this.json = template.deepCopy();
    }

    BiomeJson climate(float temperature, float downfall) {
        json.addProperty("temperature", temperature);
        json.addProperty("downfall", downfall);
        return this;
    }

    BiomeJson creatureProbability(float probability) {
        json.addProperty("creature_spawn_probability", probability);
        return this;
    }

    BiomeJson effect(String key, int color) {
        effects().addProperty(key, color);
        return this;
    }

    BiomeJson music(String sound) {
        effects().getAsJsonObject("music").addProperty("sound", sound);
        return this;
    }

    BiomeJson particle(JsonObject options, float probability) {
        JsonObject particle = new JsonObject();
        particle.add("options", options);
        particle.addProperty("probability", probability);
        effects().add("particle", particle);
        return this;
    }

    private JsonObject effects() {
        return json.getAsJsonObject("effects");
    }

    /** Replaces {@code from} with {@code to} wherever it appears, keeping its position. */
    BiomeJson replace(String from, String to) {
        for (JsonElement step : json.getAsJsonArray("features")) {
            JsonArray features = step.getAsJsonArray();
            for (int i = 0; i < features.size(); i++) {
                if (features.get(i).getAsString().equals(from)) {
                    features.set(i, new com.google.gson.JsonPrimitive(to));
                }
            }
        }
        return this;
    }

    BiomeJson drop(String... features) {
        Set<String> dropped = Set.of(features);
        for (JsonElement step : json.getAsJsonArray("features")) {
            JsonArray list = step.getAsJsonArray();
            for (int i = list.size() - 1; i >= 0; i--) {
                if (dropped.contains(list.get(i).getAsString())) {
                    list.remove(i);
                }
            }
        }
        return this;
    }

    BiomeJson add(int step, String... features) {
        JsonArray list = json.getAsJsonArray("features").get(step).getAsJsonArray();
        for (String feature : features) {
            list.add(feature);
        }
        return this;
    }

    /** Sets one spawner entry's weight, wherever that mob appears. */
    BiomeJson weight(String entity, int weight) {
        for (Map.Entry<String, JsonElement> category : json.getAsJsonObject("spawners").entrySet()) {
            for (JsonElement entry : category.getValue().getAsJsonArray()) {
                JsonObject spawner = entry.getAsJsonObject();
                if (spawner.get("type").getAsString().equals(entity)) {
                    spawner.addProperty("weight", weight);
                }
            }
        }
        return this;
    }

    /** Replaces a whole spawner category. */
    BiomeJson spawners(String category, JsonObject... entries) {
        JsonArray list = new JsonArray();
        for (JsonObject entry : entries) {
            list.add(entry);
        }
        json.getAsJsonObject("spawners").add(category, list);
        return this;
    }

    /** Appends one entry to a spawner category. */
    BiomeJson addSpawner(String category, JsonObject entry) {
        json.getAsJsonObject("spawners").getAsJsonArray(category).add(entry);
        return this;
    }

    /** Multiplies every creature group size by {@code scale}, keeping at least one. Halves round to even. */
    BiomeJson packScale(double scale) {
        for (JsonElement entry : json.getAsJsonObject("spawners").getAsJsonArray("creature")) {
            JsonObject spawner = entry.getAsJsonObject();
            int min = Math.max(1, (int) Math.rint(spawner.get("minCount").getAsInt() * scale));
            int max = Math.max(min, (int) Math.rint(spawner.get("maxCount").getAsInt() * scale));
            spawner.addProperty("minCount", min);
            spawner.addProperty("maxCount", max);
        }
        return this;
    }

    /** Multiplies every spawn cost's energy budget, thinning every costed mob at once. */
    BiomeJson budgetScale(double scale) {
        for (Map.Entry<String, JsonElement> cost : json.getAsJsonObject("spawn_costs").entrySet()) {
            JsonObject c = cost.getValue().getAsJsonObject();
            c.addProperty("energy_budget", Math.round(c.get("energy_budget").getAsDouble() * scale * 10000) / 10000.0);
        }
        return this;
    }

    BiomeJson cost(String entity, double charge, double budget) {
        JsonObject c = new JsonObject();
        c.addProperty("charge", charge);
        c.addProperty("energy_budget", budget);
        json.getAsJsonObject("spawn_costs").add(entity, c);
        return this;
    }

    BiomeJson dropCost(String... entities) {
        for (String entity : entities) {
            json.getAsJsonObject("spawn_costs").remove(entity);
        }
        return this;
    }

    static JsonObject spawner(String type, int weight, int min, int max) {
        JsonObject s = new JsonObject();
        s.addProperty("type", type);
        s.addProperty("weight", weight);
        s.addProperty("minCount", min);
        s.addProperty("maxCount", max);
        return s;
    }

    static JsonObject particleType(String type) {
        JsonObject o = new JsonObject();
        o.addProperty("type", type);
        return o;
    }
}
