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

import java.util.List;
import java.util.Map;

/**
 * Fallback chains for Deep Aether blocks: the Deep Aether block, then the closest Aether block, then the closest
 * vanilla block. A block with no entry here has no acceptable substitute, and features using it are left out
 * without Deep Aether (totems, squash, tiki torches).
 */
public final class Substitutes {
    /** Deep Aether block id -> ordered fallbacks (block id, or block id with "[prop=value,...]"). */
    public static final Map<String, List<String>> CHAINS = Map.ofEntries(
            Map.entry("deep_aether:aseterite", List.of("aether:holystone[double_drops=true]")),
            Map.entry("deep_aether:clorite", List.of("aether:icestone")),
            Map.entry("deep_aether:aether_coarse_dirt", List.of("aether:aether_dirt[double_drops=true]")),
            Map.entry("deep_aether:aether_mud", List.of("minecraft:mud")),
            Map.entry("deep_aether:virulent_quicksand", List.of("minecraft:soul_sand")),
            Map.entry("deep_aether:golden_heights_grass_block",
                    List.of("aether:enchanted_aether_grass_block[snowy=false]")),
            Map.entry("deep_aether:skyjade_block", List.of("aether:zanite_block")),
            Map.entry("deep_aether:skyjade_chain", List.of("minecraft:chain")),
            Map.entry("deep_aether:sterling_aercloud", List.of("aether:cold_aercloud[double_drops=true]")));

    /**
     * A {@code altered_aether:substitute} block state provider for a Deep Aether block. {@code primary} may carry
     * properties, as in {@code "deep_aether:clorite"} or {@code "deep_aether:skyjade_chain[axis=y]"}.
     */
    public static JsonObject provider(String primary) {
        String id = primary.contains("[") ? primary.substring(0, primary.indexOf('[')) : primary;
        JsonArray candidates = new JsonArray();
        candidates.add(state(primary));
        for (String fallback : CHAINS.getOrDefault(id, List.of())) {
            candidates.add(state(fallback));
        }
        JsonObject json = new JsonObject();
        json.addProperty("type", "altered_aether:substitute");
        json.add("candidates", candidates);
        return json;
    }

    /** Parses "ns:block[prop=value,...]" into a {Name, Properties} block state object. */
    public static JsonObject state(String spec) {
        JsonObject state = new JsonObject();
        int bracket = spec.indexOf('[');
        state.addProperty("Name", bracket < 0 ? spec : spec.substring(0, bracket));
        if (bracket >= 0) {
            JsonObject properties = new JsonObject();
            for (String pair : spec.substring(bracket + 1, spec.length() - 1).split(",")) {
                String[] kv = pair.split("=", 2);
                properties.addProperty(kv[0].trim(), kv[1].trim());
            }
            state.add("Properties", properties);
        }
        return state;
    }

    private Substitutes() {
    }
}
