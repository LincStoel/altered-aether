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

import java.util.Map;

/** Configured and placed features. */
public final class AAFeatureProvider extends JsonDataProvider {
    private static final String CONFIGURED = "worldgen/configured_feature";
    private static final String PLACED = "worldgen/placed_feature";
    private static final String NS = AlteredAether.MOD_ID + ":";
    /** The basin fill, in every biome that holds basins. */
    static final String BASIN_FILL = NS + "basin_fill";
    /** The landmark builder, and the decoration placed at landmarks' points, in the order they run. */
    static final String LANDMARK = NS + "landmark/build";
    /** The giant-tree builder of the Skyroot Cathedral and Nurselog Hollow. */
    static final String GROVE = NS + "grove/build";
    /** The roots hanging under the grove islands. */
    static final String HANGING_ROOTS = NS + "grove/hanging_roots";
    /** The young trees and ground cover between the giants, in place of the stock forest's trees. */
    static final String UNDERSTORY = NS + "grove/understory";
    static final String[] LANDMARK_DECORATION = {NS + "landmark/tempest_ring", NS + "landmark/spire_drifts",
            NS + "landmark/fracture_debris", NS + "landmark/fracture_slabs", NS + "landmark/fracture_bridges",
            NS + "landmark/islet_meadow", NS + "landmark/islet_oak"};

    public AAFeatureProvider(PackOutput output) {
        super(output, "features");
    }

    @Override
    protected void generate() {
        groveTrees();
        basinFill();
        waterfalls();
        landmarks();
        configured("grove/build", NS + "grove", new JsonObject());
        placed("grove/build", GROVE, new JsonArray());
        configured("grove/hanging_roots", NS + "hanging_roots", new JsonObject());
        placed("grove/hanging_roots", HANGING_ROOTS, new JsonArray());
        configured("grove/understory", NS + "understory", new JsonObject());
        placed("grove/understory", UNDERSTORY, new JsonArray());
    }

    /**
     * The rare landmarks: the builder, which puts each landmark together a chunk at a time, and the decoration at
     * their points. Copper spires ring the Tempest crown, drifts bank against the frozen spire's foot, debris hangs in
     * the great fracture's canyon with cloud bridges over its lesser fault, and a golden oak grows on the double
     * atoll's islet, in a meadow.
     */
    private void landmarks() {
        configured("landmark/build", NS + "landmark", new JsonObject());
        placed("landmark/build", LANDMARK, new JsonArray());

        placed("landmark/tempest_ring", NS + "copper_spike/cluster", Placement.list(
                Placement.landmarkPoints("tempest_spires", "ring", 1), Placement.heightmap("WORLD_SURFACE_WG")));
        placed("landmark/spire_drifts", NS + "drift/snow", Placement.list(
                Placement.landmarkPoints("frozen_spire", "foot", 0.8), Placement.heightmap("WORLD_SURFACE_WG")));
        placed("landmark/fracture_debris", NS + "debris/rift", Placement.list(
                Placement.landmarkPoints("great_fracture", "chasm", 0.9, 4, 34)));
        placed("landmark/fracture_slabs", NS + "debris/rift_large", Placement.list(
                Placement.landmarkPoints("great_fracture", "chasm", 0.25, 10, 40)));
        placed("landmark/fracture_bridges", NS + "cloud/rift_bridge", Placement.list(
                Placement.landmarkPoints("great_fracture", "rim", 0.6), Placement.heightmap("WORLD_SURFACE_WG")));

        JsonObject meadow = new JsonObject();
        JsonObject provider = new JsonObject();
        provider.add("fallback", Providers.simple("aether:aether_grass_block"));
        provider.add("rules", new JsonArray());
        meadow.add("state_provider", provider);
        JsonObject target = new JsonObject();
        target.addProperty("type", "minecraft:matching_blocks");
        JsonArray sand = new JsonArray();
        sand.add("minecraft:sand");
        target.add("blocks", sand);
        meadow.add("target", target);
        meadow.addProperty("radius", 4);
        meadow.addProperty("half_height", 2);
        configured("landmark/islet_meadow", "minecraft:disk", meadow);
        placed("landmark/islet_meadow", NS + "landmark/islet_meadow", Placement.list(
                Placement.landmarkPoints("double_atoll", "islet", 1), Placement.heightmap("WORLD_SURFACE_WG")));
        placed("landmark/islet_oak", "aether:golden_oak_tree", Placement.list(
                Placement.landmarkPoints("double_atoll", "islet", 1), Placement.heightmap("WORLD_SURFACE_WG")));
    }

    static final String WATERFALL_WATER = NS + "waterfall/water", WATERFALL_POISON = NS + "waterfall/poison";
    static final String WATERFALL_CASCADING = NS + "waterfall/cascading", WATERFALL_VEXING = NS + "waterfall/vexing",
            WATERFALL_PLATEAU = NS + "waterfall/plateau";

    /**
     * Springs set into cliff faces at island edges, pouring off into the sky. The cascading grove is named for them;
     * the vexing grove's run with Deep Aether's poison where it can; the plateau's high cliffs get the odd one.
     */
    private void waterfalls() {
        configured("waterfall/water", NS + "edge_spring", spring("minecraft:water"));
        configured("waterfall/poison", NS + "edge_spring", spring("deep_aether:poison", "minecraft:water"));
        placed("waterfall/cascading", WATERFALL_WATER, waterfallPlacement(4), "ars_nouveau");
        placed("waterfall/vexing", WATERFALL_POISON, waterfallPlacement(2), "ars_nouveau");
        placed("waterfall/plateau", WATERFALL_WATER, waterfallPlacement(0.4));
    }

    private static JsonObject spring(String... fluids) {
        JsonObject config = new JsonObject();
        JsonArray list = new JsonArray();
        for (String fluid : fluids) {
            list.add(fluid);
        }
        config.add("fluid", list);
        config.addProperty("min_depth", 2);
        config.addProperty("max_depth", 8);
        return config;
    }

    private static JsonArray waterfallPlacement(double perChunk) {
        return Placement.list(
                Placement.configCount(perChunk, "waterfall_frequency"),
                Placement.inSquare(),
                Placement.islandEdge(12, 24),
                Placement.biome());
    }

    /** Fills every basin reaching into a chunk. It places nothing itself, so it takes no placement modifiers. */
    private void basinFill() {
        configured("basin_fill", NS + "basin_fill", new JsonObject());
        placed("basin_fill", NS + "basin_fill", new JsonArray());
    }

    /**
     * The archwood groves' canopy: Ars Nouveau's natural trees at four per chunk, gated on the sapling surviving, the
     * same recipe Ars Elemental uses for its clusters. Owning the placement means these groves only need Ars Nouveau.
     */
    private void groveTrees() {
        Map<String, String> saplings = Map.of(
                "blazing", "ars_nouveau:red_archwood_sapling",
                "cascading", "ars_nouveau:blue_archwood_sapling",
                "flourishing", "ars_nouveau:green_archwood_sapling",
                "vexing", "ars_nouveau:purple_archwood_sapling");
        saplings.forEach((colour, sapling) -> placed("grove_trees/" + colour,
                "ars_nouveau:natural_" + colour + "_tree",
                Placement.list(
                        Placement.count(4),
                        Placement.inSquare(),
                        Placement.surfaceWaterDepth(0),
                        Placement.heightmap("OCEAN_FLOOR"),
                        Placement.biomeEdgeFalloff(10, 0.25),
                        Placement.biome(),
                        Placement.wouldSurvive(sapling, Map.of("stage", "0"))),
                "ars_nouveau"));
    }

    private void placed(String name, String feature, JsonArray placement, String... requiredMods) {
        JsonObject json = new JsonObject();
        json.addProperty("feature", feature);
        json.add("placement", placement);
        data(PLACED, AlteredAether.id(name), json, requiredMods);
    }

    private void configured(String name, String type, JsonObject config, String... requiredMods) {
        JsonObject json = new JsonObject();
        json.addProperty("type", type);
        json.add("config", config);
        data(CONFIGURED, AlteredAether.id(name), json, requiredMods);
    }
}
