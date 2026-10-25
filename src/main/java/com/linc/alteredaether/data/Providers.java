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

/** Block state provider and number provider JSON builders. States are written as {@code "ns:block[prop=value]"}. */
final class Providers {
    static JsonObject simple(String state) {
        JsonObject json = Placement.type("minecraft:simple_state_provider");
        json.add("state", Substitutes.state(state));
        return json;
    }

    /** Alternating state and weight: {@code weighted("aether:holystone", 4, "aether:icestone", 1)}. */
    static JsonObject weighted(Object... statesAndWeights) {
        JsonArray entries = new JsonArray();
        for (int i = 0; i < statesAndWeights.length; i += 2) {
            JsonObject entry = new JsonObject();
            entry.addProperty("weight", (Integer) statesAndWeights[i + 1]);
            entry.add("data", Substitutes.state((String) statesAndWeights[i]));
            entries.add(entry);
        }
        JsonObject json = Placement.type("minecraft:weighted_state_provider");
        json.add("entries", entries);
        return json;
    }

    static JsonObject uniform(int min, int max) {
        return Placement.uniform(min, max);
    }

    private Providers() {
    }
}
