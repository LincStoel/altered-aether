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
import net.minecraft.data.PackOutput;

import java.util.List;

import static com.linc.alteredaether.data.Providers.simple;
import static com.linc.alteredaether.data.Providers.uniform;
import static com.linc.alteredaether.data.Providers.weighted;

/**
 * The surface decoration that gives each biome its character: boulders, spires, drifts, ruins, clouds and ores. All of
 * it is built from this mod's own feature types and the Aether's blocks, so it works with only The Aether present
 * (Deep Aether blocks go through the substitute provider). Biomes reference these by the constants below. Content for
 * optional mods is in AAOptionalDecorationProvider.
 */
public final class AADecorationProvider extends FeatureData {
    static final String PLATEAU_BOULDER = NS + "boulder/plateau";
    static final String CRAG_BOULDER = NS + "boulder/crag";
    static final String AMBROSIUM_BOULDER = NS + "boulder/ambrosium";
    static final String DUNE_BOULDER = NS + "boulder/dune";
    static final String RIFT_DEBRIS = NS + "debris/rift";
    static final String RIFT_SLAB_FRAGMENTS = NS + "debris/rift_large";
    static final String TEMPEST_SPIRES = NS + "copper_spike/cluster";
    static final String TEMPEST_LONE_SPIKE = NS + "copper_spike/lone";
    static final String SNOW_DRIFT = NS + "drift/snow";
    static final String ICE_SPIKE = NS + "drift/ice_spike";
    static final String ICESTONE_SHARD = NS + "shard/icestone";
    static final String HANGING_CHAIN = NS + "hanging/chain";
    static final String PLATEAU_RUINS = NS + "ruin/plateau";
    static final String DUNE_RUINS = NS + "ruin/dune";
    static final String SKYJADE_PILLAR = NS + "ruin/skyjade_pillar";
    static final String STRIKE_CRATER = NS + "crater/strike";
    static final String DEAD_BUSH = NS + "vegetation/dead_bush";
    static final String PLATEAU_CLOUD = NS + "cloud/plateau";
    static final String PLATEAU_GOLDEN_CLOUD = NS + "cloud/plateau_golden";
    static final String CRAG_CLOUD = NS + "cloud/crag";
    static final String TEMPEST_CLOUD = NS + "cloud/tempest";
    static final String RIFT_STRAND = NS + "cloud/rift_strand";
    static final String RIFT_BRIDGE = NS + "cloud/rift_bridge";
    static final String CRAG_ORES = NS + "ore/crag_";
    static final String TEMPEST_COPPER_ORE = NS + "ore/tempest_copper";
    static final String CRAG_CAMPFIRE = NS + "camp/crag_campfire";
    static final String LUSH_GRASS = NS + "vegetation/lush_grass";

    private static final String SURFACE = "WORLD_SURFACE_WG";
    private static final String CRAG_ROCK = NS + "frozen_crag_ore_replaceables";
    private static final String TEMPEST_ROCK = NS + "tempest_peak_ore_replaceables";

    public AADecorationProvider(PackOutput output) {
        super(output, "decoration");
    }

    @Override
    protected void generate() {
        boulders();
        riftDebris();
        copperSpikes();
        snow();
        ruins();
        craters();
        hangingChains();
        clouds();
        ores();
        vegetation();
        wanderers();
        campfire();
    }

    // ------------------------------------------------------------------------------------------ boulders

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

        placed("boulder/plateau", "boulder/plateau", surface(1 / 10.0, "boulders"));
        placed("boulder/crag", "boulder/crag", surface(1 / 5.0, "boulders"));
        placed("boulder/ambrosium", "boulder/ambrosium", surface(1 / 16.0, "boulders"));
        placed("boulder/dune", "boulder/dune", surface(1 / 12.0, "boulders"));
    }

    /**
     * Fragments of the shattered slabs hanging in the gaps between them: many small floating chunks anchored 1 to 12
     * blocks over the surface, so they sit in the open air beside terrain, and a few larger pieces that read as slabs
     * that came free.
     */
    private void riftDebris() {
        JsonObject stone = weighted("aether:holystone[double_drops=true]", 4,
                "aether:mossy_holystone[double_drops=true]", 3, "aether:icestone", 1);
        boulder("debris/rift", stone, 1, 2, 0.8, 0.0, null, true);
        boulder("debris/rift_large", stone, 3, 4, 0.5, 0.0, simple("aether:mossy_holystone[double_drops=true]"), true);

        placed("debris/rift", "debris/rift", Placement.list(Placement.configCount(7, "rift_debris"),
                Placement.inSquare(), Placement.heightmap(SURFACE), Placement.randomOffset(7, 1, 12),
                Placement.biome()));
        placed("debris/rift_large", "debris/rift_large", Placement.list(Placement.configCount(0.5, "rift_debris"),
                Placement.inSquare(), Placement.heightmap(SURFACE), Placement.randomOffset(4, 4, 14),
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
                Placement.heightmap(SURFACE), Placement.biome()));
        placed("copper_spike/lone", "copper_spike/lone", surface(0.3, "copper_spikes"));
    }

    // ------------------------------------------------------------------------------------ snow and ice

    private void snow() {
        JsonObject config = new JsonObject();
        config.add("block", simple("minecraft:snow_block"));
        config.add("radius", uniform(3, 7));
        config.add("height", uniform(2, 4));
        config.addProperty("elongation", 2.2);
        config.addProperty("wind_aligned", true);
        configured("drift/snow", "snow_drift", config);
        placed("drift/snow", "drift/snow", surface(0.5, "snow_drifts"));

        // Vanilla's tapered packed-ice spire grows only from snow blocks, so it rises out of the drifts and
        // nowhere else, which is the whole spacing rule.
        placed("drift/ice_spike", "minecraft:ice_spike", Placement.list(Placement.configCount(1 / 5.0, "snow_drifts"),
                Placement.inSquare(), Placement.heightmap("MOTION_BLOCKING"), Placement.biome()));

        // short jagged teeth of the biome's own stone between the spires
        configured("shard/icestone", "minecraft:block_column", column("up", 2, 6, simple("aether:icestone")));
        placed("shard/icestone", "shard/icestone", Placement.list(Placement.configCount(2 / 14.0, "snow_drifts"),
                Placement.inSquare(), Placement.randomOffset(3, 0, 0), Placement.heightmap("MOTION_BLOCKING"),
                Placement.biome()));
    }

    // ------------------------------------------------------------------------------------- ruins etc.

    private void ruins() {
        JsonObject stone = weighted("aether:pillar[axis=y]", 6, "aether:holystone_bricks", 3,
                "aether:mossy_holystone[double_drops=true]", 2, "aether:carved_stone", 1);
        JsonObject plinth = weighted("aether:holystone_bricks", 2, "aether:holystone_brick_slab", 2,
                "aether:mossy_holystone[double_drops=true]", 1);
        pillar("plateau", stone, plinth, 3, 8, 0.35f);
        pillar("dune", stone, plinth, 2, 6, 0.25f);

        placed("ruin/plateau", "ruin/plateau", surface(0.04, "ruins"));
        placed("ruin/dune", "ruin/dune", surface(0.035, "ruins"));

        // The dunes' standing columns of green stone; a plain block of zanite stands in without Deep Aether.
        configured("ruin/skyjade_pillar", "minecraft:block_column",
                column("up", 3, 5, Substitutes.provider("deep_aether:skyjade_block")));
        placed("ruin/skyjade_pillar", "ruin/skyjade_pillar", surface(1 / 28.0, "ruins"));
    }

    private void pillar(String name, JsonObject blocks, JsonObject plinth, int minHeight, int maxHeight, float wide) {
        JsonObject config = new JsonObject();
        config.add("blocks", blocks);
        config.add("height", uniform(minHeight, maxHeight));
        config.addProperty("wide_chance", wide);
        config.addProperty("fallen", 2);
        config.add("plinth", plinth);
        configured("ruin/" + name, "ruined_pillar", config);
    }

    /**
     * Strike craters in Tempest Peaks, one in about eight chunks: a dish with a fused floor, aerogel threaded through
     * it and scorched streaks running out from the rim. They are where the lightning has been for ages.
     */
    private void craters() {
        JsonObject config = new JsonObject();
        config.add("radius", uniform(3, 6));
        config.add("depth", uniform(1, 3));
        config.add("liner", weighted("aether:quicksoil[double_drops=true]", 4, "minecraft:blackstone", 3,
                "minecraft:gravel", 1));
        config.add("glass", simple("aether:aerogel"));
        config.addProperty("glass_chance", 0.2);
        configured("crater/strike", "strike_crater", config);
        placed("crater/strike", "crater/strike", surface(1 / 8.0, "craters"));
    }

    /**
     * Chains hanging from the undersides of ledges in the crags: Deep Aether's skyjade chain, plain chain without it.
     * The ledge is found by the overhang placement, so a chain only ever hangs from something solid.
     */
    private void hangingChains() {
        configured("hanging/chain", "minecraft:block_column", column("down", 3, 9,
                Substitutes.provider("deep_aether:skyjade_chain[axis=y]")));
        placed("hanging/chain", "hanging/chain", Placement.list(Placement.configCount(5, "hanging_chains"),
                Placement.inSquare(), Placement.heightRange("uniform", Placement.aboveBottom(0), Placement.aboveBottom(128)),
                Placement.overhang(12, 3), Placement.biome()));
    }

    private void vegetation() {
        // vanilla's dead bush patch: a few tufts across the dunes, quicksoil being among the blocks it may stand on
        // the flourishing grove should read as visibly lusher than the other four: the stock grass patch, at 20 a chunk
        placed("vegetation/lush_grass", "aether:grass_patch", Placement.list(
                Placement.configCount(20, "vegetation"), Placement.inSquare(), Placement.heightmap("MOTION_BLOCKING"),
                Placement.biome()));
        placed("vegetation/dead_bush", "minecraft:patch_dead_bush", Placement.list(
                Placement.configCount(1 / 8.0, "vegetation"), Placement.inSquare(), Placement.heightmap(SURFACE),
                Placement.biome()));
    }

    // -------------------------------------------------------------------------------------------- clouds

    private void clouds() {
        JsonObject cold = simple("aether:cold_aercloud[double_drops=true]");
        JsonObject blue = simple("aether:blue_aercloud[double_drops=true]");
        JsonObject golden = simple("aether:golden_aercloud[double_drops=true]");

        configured("cloud/plateau", "aercloud", cloud(cold, "streak", 12, 22, 2));
        configured("cloud/plateau_golden", "aercloud", cloud(golden, "puff", 3, 5, 1));
        configured("cloud/crag", "aercloud", cloud(blue, "streak", 8, 14, 2));
        configured("cloud/tempest", "aercloud", cloud(Substitutes.provider("deep_aether:sterling_aercloud"), "streak", 10, 18, 2));
        configured("cloud/rift_strand", "aercloud", cloud(blue, "streak", 10, 18, 1));

        placed("cloud/plateau", "cloud/plateau", skyPlacement(1 / 12.0, 4, 12));
        placed("cloud/plateau_golden", "cloud/plateau_golden", skyPlacement(1 / 14.0, 3, 10));
        placed("cloud/crag", "cloud/crag", skyPlacement(0.6, 2, 8));
        placed("cloud/tempest", "cloud/tempest", skyPlacement(1 / 8.0, 4, 14));
        placed("cloud/rift_strand", "cloud/rift_strand", skyPlacement(0.5, 2, 8));

        JsonObject bridge = new JsonObject();
        bridge.add("block", cold);
        configured("cloud/rift_bridge", "aercloud_bridge", bridge);
        placed("cloud/rift_bridge", "cloud/rift_bridge", Placement.list(Placement.configCount(0.35, "aerclouds"),
                Placement.inSquare(), Placement.islandEdge(12, 16), Placement.biome()));
    }

    /** Clouds anchor over the surface, so they sit near the terrain they decorate rather than in empty sky. */
    private static JsonArray skyPlacement(double perChunk, int minAbove, int maxAbove) {
        return Placement.list(Placement.configCount(perChunk, "aerclouds"), Placement.inSquare(),
                Placement.heightmap(SURFACE), Placement.randomOffset(0, minAbove, maxAbove), Placement.biome());
    }

    // ---------------------------------------------------------------------------------------------- ores

    /**
     * Ore for the biomes whose stone isn't holystone, which is all the Aether's own ores can replace. Counts and
     * sizes carry over from the World Traveler 2 pack; heights are the stock Aether's, stretched in the tall Aether.
     */
    private void ores() {
        crags("aether_dirt", "aether:aether_dirt[double_drops=true]", 33, 0.0, 20, 0, 128, "uniform");
        crags("ambrosium", "aether:ambrosium_ore[double_drops=true]", 16, 0.0, 20, 0, 128, "uniform");
        crags("zanite", "aether:zanite_ore", 5, 0.5, 14, 0, 75, "uniform");
        crags("gravitite", "aether:gravitite_ore", 4, 0.0, 7, -58, 74, "trapezoid");
        crags("gravitite_buried", "aether:gravitite_ore", 3, 0.5, 5, 0, 74, "uniform");

        configured("ore/tempest_copper", "minecraft:ore", ore("minecraft:deepslate_copper_ore", 30, 0.0, TEMPEST_ROCK));
        placed("ore/tempest_copper", "ore/tempest_copper", Placement.list(Placement.count(12), Placement.inSquare(),
                Placement.heightRange("uniform", Placement.aboveBottom(0), Placement.aboveBottom(53)),
                Placement.biome()));
    }

    private void crags(String name, String state, int size, double discard, int count, int min, int max, String shape) {
        configured("ore/crag_" + name, "minecraft:ore", ore(state, size, discard, CRAG_ROCK));
        placed("ore/crag_" + name, "ore/crag_" + name, Placement.list(Placement.count(count), Placement.inSquare(),
                Placement.heightRange(shape, Placement.aboveBottom(min), Placement.aboveBottom(max)), Placement.biome()));
    }

    /** A lone ambrosium campfire burning on the crags: a sign somebody has been here, and a landmark in the snow. */
    private void campfire() {
        Object[] lit = new Object[8];
        String[] facings = {"north", "south", "east", "west"};
        for (int i = 0; i < 4; i++) {
            lit[i * 2] = "altered_aether:ambrosium_campfire[facing=" + facings[i]
                    + ",lit=true,signal_fire=false,waterlogged=false]";
            lit[i * 2 + 1] = 1;
        }
        JsonObject config = new JsonObject();
        config.add("to_place", weighted(lit));
        configured("camp/crag_campfire", "minecraft:simple_block", config);
        placed("camp/crag_campfire", "camp/crag_campfire", Placement.list(Placement.rarity(180), Placement.inSquare(),
                Placement.heightmap("MOTION_BLOCKING"), Placement.biome()));
    }

    // --------------------------------------------------------------------------------------- wanderers

    /**
     * Aether mobs the pack places at generation, so a visitor finds them standing in the world: a fire minion now and
     * then, a sentry rarely. Both are the Aether's own, so they are always available.
     */
    private void wanderers() {
        spawner("fire_minion", "aether:fire_minion", 1, 1 / 30.0);
        spawner("sentry", "aether:sentry", 1, 1 / 44.0);
        addFeature("spawn/fire_minion", List.of(IS_AETHER), "vegetal_decoration");
        addFeature("spawn/sentry", List.of(IS_AETHER), "vegetal_decoration");
    }

    private void spawner(String name, String entity, int count, double chance) {
        JsonObject config = new JsonObject();
        config.addProperty("entity", entity);
        config.addProperty("count", count);
        config.addProperty("spread", 0);
        JsonObject ground = Placement.type("minecraft:matching_block_tag");
        ground.addProperty("tag", "minecraft:dirt");
        config.add("ground", ground);
        config.addProperty("persistent", true);
        configured("spawn/" + name, "spawn_entity", config);
        placed("spawn/" + name, "spawn/" + name, Placement.list(Placement.rarity((int) Math.round(1 / chance)),
                Placement.inSquare(), Placement.heightmap(SURFACE), Placement.biome()));
    }

    // ---------------------------------------------------------------------------------------- helpers

    private static JsonArray surface(double perChunk, String family) {
        return Placement.list(Placement.configCount(perChunk, family), Placement.inSquare(),
                Placement.heightmap(SURFACE), Placement.biome());
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
}
