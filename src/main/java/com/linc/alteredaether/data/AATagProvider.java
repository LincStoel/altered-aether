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
import com.linc.alteredaether.worldgen.AABiomes;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;

import java.util.Set;

/**
 * Tags. Written as JSON so entries for optional mods can be optional ({@code required: false}) and so tags in other
 * namespaces are appended to, never replaced.
 */
public final class AATagProvider extends JsonDataProvider {
    /** Namespaces that are always present; entries from any other mod are written as optional. */
    private static final Set<String> REQUIRED = Set.of("minecraft", AlteredAether.MOD_ID, "aether", "aeroblender");

    public AATagProvider(PackOutput output) {
        super(output, "tags");
    }

    @Override
    protected void generate() {
        tag("block", AlteredAether.id("breeze_spawnable_on"), "aether:cold_aercloud");
        // Every Aether soil is in #minecraft:dirt (aether grass and dirt, enchanted grass, and Deep Aether's coarse
        // dirt, mud and golden grass), so familiars follow the groves' varied surfaces without a list of their own.
        tag("block", AlteredAether.id("familiar_spawnable_on"), "#minecraft:dirt");
        // Snuffles key their spawning to this tag (plus a cold biome), not sea level, so the Frozen Crags' ground
        // only needs adding to it.
        tag("block", ResourceLocation.fromNamespaceAndPath("snuffles", "snuffles_spawnable_on"),
                "aether:icestone", "minecraft:snow_block", "minecraft:ice", "minecraft:packed_ice",
                "deep_aether:aether_coarse_dirt");
        tag("worldgen/biome", ResourceLocation.withDefaultNamespace("allows_tropical_fish_spawns_at_any_height"),
                "altered_aether:sunken_atoll");
        aetherBiomes();
    }

    /**
     * Our biomes join {@code #aether:is_aether}, which the Aether and other mods use to find the dimension's biomes
     * (spawns, structures, our own biome modifiers). Biomes that need an optional mod are optional entries, since
     * they don't exist without it.
     */
    private void aetherBiomes() {
        JsonArray values = new JsonArray();
        for (AABiomes.Entry entry : AABiomes.ALL) {
            String id = entry.key().location().toString();
            if (entry.requiredMods().isEmpty()) {
                values.add(id);
            } else {
                JsonObject optional = new JsonObject();
                optional.addProperty("id", id);
                optional.addProperty("required", false);
                values.add(optional);
            }
        }
        JsonObject json = new JsonObject();
        json.addProperty("replace", false);
        json.add("values", values);
        data("tags/worldgen/biome", ResourceLocation.fromNamespaceAndPath("aether", "is_aether"), json);
    }

    private void tag(String registry, ResourceLocation id, String... entries) {
        JsonArray values = new JsonArray();
        for (String entry : entries) {
            String namespace = entry.replace("#", "").split(":")[0];
            if (REQUIRED.contains(namespace)) {
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
}
