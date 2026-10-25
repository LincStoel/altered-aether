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

import static com.linc.alteredaether.data.Providers.simple;
import static com.linc.alteredaether.data.Providers.uniform;
import static com.linc.alteredaether.data.Providers.weighted;

/**
 * The surface decoration that gives each biome its character: boulders, spires, debris, and the like. Everything here
 * is built from this mod's own feature types, so it works with only The Aether present. Biomes reference these by the
 * constants below; whatever needs an optional mod is in a separate file with a mod_loaded condition and arrives
 * through a biome modifier instead.
 */
public final class AADecorationProvider extends JsonDataProvider {
    private static final String CONFIGURED = "worldgen/configured_feature";
    private static final String PLACED = "worldgen/placed_feature";
    private static final String NS = AlteredAether.MOD_ID + ":";

    static final String PLATEAU_BOULDER = NS + "boulder/plateau";
    static final String CRAG_BOULDER = NS + "boulder/crag";
    static final String AMBROSIUM_BOULDER = NS + "boulder/ambrosium";
    static final String DUNE_BOULDER = NS + "boulder/dune";
    static final String RIFT_DEBRIS = NS + "debris/rift";
    static final String RIFT_SLAB_FRAGMENTS = NS + "debris/rift_large";
    static final String TEMPEST_SPIRES = NS + "copper_spike/cluster";
    static final String TEMPEST_LONE_SPIKE = NS + "copper_spike/lone";

    public AADecorationProvider(PackOutput output) {
        super(output, "decoration");
    }

    @Override
    protected void generate() {
        boulders();
        riftDebris();
        copperSpikes();
    }

    private void boulders() {
        // The plateau's own palette: mostly holystone, mossed where it is damp, the odd block of icestone.
        boulder("plateau", weighted("aether:holystone[double_drops=true]", 4,
                        "aether:mossy_holystone[double_drops=true]", 3, "aether:icestone", 1),
                2, 4, 0.7, 0.3, null, false);
        boulder("crag", weighted("aether:icestone", 6, "minecraft:packed_ice", 1, "minecraft:blue_ice", 1),
                2, 5, 0.8, 0.25, simple("minecraft:snow_block"), false);
        boulder("ambrosium", simple("aether:ambrosium_block"), 1, 3, 0.8, 0.35, null, false);
        boulder("dune", weighted("aether:quicksoil[double_drops=true]", 5, "aether:aether_dirt[double_drops=true]", 2,
                "aether:holystone[double_drops=true]", 1), 2, 4, 0.6, 0.35, null, false);

        placed("boulder/plateau", "boulder/plateau", Placement.list(Placement.configCount(1 / 10.0, "boulders"),
                Placement.inSquare(), Placement.heightmap("WORLD_SURFACE_WG"), Placement.biome()));
        placed("boulder/crag", "boulder/crag", Placement.list(Placement.configCount(1 / 5.0, "boulders"),
                Placement.inSquare(), Placement.heightmap("WORLD_SURFACE_WG"), Placement.biome()));
        placed("boulder/ambrosium", "boulder/ambrosium", Placement.list(Placement.configCount(1 / 16.0, "boulders"),
                Placement.inSquare(), Placement.heightmap("WORLD_SURFACE_WG"), Placement.biome()));
        placed("boulder/dune", "boulder/dune", Placement.list(Placement.configCount(1 / 12.0, "boulders"),
                Placement.inSquare(), Placement.heightmap("WORLD_SURFACE_WG"), Placement.biome()));
    }

    /**
     * Fragments of the shattered slabs hanging in the gaps between them: many small floating chunks anchored 1 to 10
     * blocks over the surface, so they sit in the open air beside terrain, and a few larger pieces that read as
     * slabs that came free.
     */
    private void riftDebris() {
        JsonObject stone = weighted("aether:holystone[double_drops=true]", 4,
                "aether:mossy_holystone[double_drops=true]", 3, "aether:icestone", 1);
        boulder("debris/rift", stone, 1, 2, 0.8, 0.0, null, true);
        boulder("debris/rift_large", stone, 3, 4, 0.5, 0.0, simple("aether:mossy_holystone[double_drops=true]"), true);

        placed("debris/rift", "debris/rift", Placement.list(Placement.configCount(7, "rift_debris"),
                Placement.inSquare(), Placement.heightmap("WORLD_SURFACE_WG"), Placement.randomOffset(7, 1, 12),
                Placement.biome()));
        placed("debris/rift_large", "debris/rift_large", Placement.list(Placement.configCount(0.5, "rift_debris"),
                Placement.inSquare(), Placement.heightmap("WORLD_SURFACE_WG"), Placement.randomOffset(4, 4, 14),
                Placement.biome()));
    }

    /**
     * Tempest Peaks: clusters of leaning copper spires, one site per 4x4 chunks in most cells, three to five spikes
     * of different heights within a few blocks of each other, plus the odd lone short one. They aim to read as a
     * lightning field from far off, and the rods at their tips are what the storms strike.
     */
    private void copperSpikes() {
        JsonArray copper = new JsonArray();
        for (String block : new String[]{"copper_block", "exposed_copper", "weathered_copper", "oxidized_copper"}) {
            copper.add(Substitutes.state("minecraft:" + block));
        }
        spike("cluster", 8, 24, 2.4f, 0.28f, 1.4f, 0.15f, 0.55f, copper);
        spike("lone", 4, 10, 1.3f, 0.18f, 1.1f, 0.05f, 0.35f, copper);

        placed("copper_spike/cluster", "copper_spike/cluster", Placement.list(
                Placement.jitteredGrid(4, 11, 0.8), Placement.inSquare(),
                Placement.configCount(4, "copper_spikes"), Placement.randomOffset(3, 0, 0),
                Placement.heightmap("WORLD_SURFACE_WG"), Placement.biome()));
        placed("copper_spike/lone", "copper_spike/lone", Placement.list(Placement.configCount(0.3, "copper_spikes"),
                Placement.inSquare(), Placement.heightmap("WORLD_SURFACE_WG"), Placement.biome()));
    }

    private void spike(String name, int minHeight, int maxHeight, float radius, float lean, float taper,
                       float branch, float rod, JsonArray blocks) {
        JsonObject config = new JsonObject();
        config.add("height", uniform(minHeight, maxHeight));
        config.addProperty("base_radius", radius);
        config.addProperty("lean", lean);
        config.addProperty("taper", taper);
        config.addProperty("branch_chance", branch);
        config.addProperty("rod_chance", rod);
        config.add("blocks", blocks);
        configured("copper_spike/" + name, "copper_spike", config);
    }

    private void boulder(String name, JsonObject state, int minRadius, int maxRadius, double squash, double burial,
                         JsonObject topCover, boolean floating) {
        JsonObject config = new JsonObject();
        config.add("state", state);
        config.add("radius", uniform(minRadius, maxRadius));
        config.addProperty("squash", squash);
        config.addProperty("burial", burial);
        if (topCover != null) {
            config.add("top_cover", topCover);
        }
        config.addProperty("floating", floating);
        configured(name.contains("/") ? name : "boulder/" + name, "boulder", config);
    }

    private void configured(String name, String type, JsonObject config) {
        JsonObject json = new JsonObject();
        json.addProperty("type", NS + type);
        json.add("config", config);
        data(CONFIGURED, AlteredAether.id(name), json);
    }

    private void placed(String name, String configured, JsonArray placement) {
        JsonObject json = new JsonObject();
        json.addProperty("feature", NS + configured);
        json.add("placement", placement);
        data(PLACED, AlteredAether.id(name), json);
    }
}
