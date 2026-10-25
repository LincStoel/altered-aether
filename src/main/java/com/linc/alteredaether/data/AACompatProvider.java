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

import com.google.gson.JsonObject;
import net.minecraft.data.PackOutput;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Hooks into other mods' content. Nothing here replaces or ships another mod's file: where a mod's own worldgen has to
 * be changed, a biome modifier takes its features out of the biomes (the remove phase runs after every add) and ours
 * put back what is wanted where it is wanted. Every file is conditional on the mods it names (see ModEnabled).
 */
public final class AACompatProvider extends FeatureData {
    private static final String AOO = "aether_overworld_ores";
    private static final String ARS = "ars_nouveau";
    private static final String ELEMENTAL = "ars_elemental";
    private static final String DEEP = "deep_aether";
    private static final String HOLYSTONE = "aether:holystone_ore_replaceables";
    private static final String SURFACE = "WORLD_SURFACE_WG";

    /** Where the Aether Overworld Ores pack its ores by default, and the placed features it uses there. */
    private static final List<String> SKYROOT = List.of("aether:skyroot_meadow", "aether:skyroot_grove",
            "aether:skyroot_woodland", "aether:skyroot_forest");

    public AACompatProvider(PackOutput output) {
        super(output, "compat");
    }

    @Override
    protected void generate() {
        aetherOverworldOres();
        archwood();
        sourcestone();
        deepAetherCover();
        snuffles();
        airshipVillage();
    }

    // ------------------------------------------------------------------------------ overworld ores

    /** One of the pack's ores: its block, and the count and height range its own placed feature uses. */
    private record Ore(String name, String state, int count, String shape, JsonObject min, JsonObject max,
                       int size, double discard) {
    }

    private static Ore ore(String name, String properties, int count, String shape, JsonObject min, JsonObject max,
                           int size, double discard) {
        return new Ore(name, "aether_overworld_ores:holystone_" + name + "_ore" + properties, count, shape, min, max,
                size, discard);
    }

    /**
     * The pack puts every one of its fifteen ores into all four skyroot biomes. This mod gives each ore a place of its
     * own instead, so which ore you need decides where you go: copper in the storm-wracked peaks, emerald in the
     * lushest grove, gold and redstone under the golden light, lapis in the cold, zinc on the crags and plateau,
     * nickel in the dunes. The stock placements are removed from the skyroot biomes, and each curated ore is placed
     * again in its own biomes at the pack's own counts and heights (sized up where the vein is meant to be worth finding).
     */
    private void aetherOverworldOres() {
        List<String> all = List.of("coal", "copper", "diamond", "emerald", "gold", "iron", "lapis", "lead", "nickel",
                "redstone", "silver", "sulfur", "tin", "uranium", "zinc");
        removeFeatures("compat/aoo_stock", SKYROOT, all.stream().map(o -> AOO + ":holystone_ore_" + o).toList(),
                "underground_ores", AOO);

        JsonObject zero = Placement.aboveBottom(0);
        Map<String, Ore> ores = Map.of(
                "copper", ore("copper", "", 12, "uniform", zero, Placement.aboveBottom(80), 30, 0.0),
                "emerald", ore("emerald", "", 4, "trapezoid", Placement.absolute(-16), Placement.absolute(190), 3, 0.0),
                "gold", ore("gold", "", 6, "uniform", zero, Placement.absolute(64), 18, 0.0),
                "lapis", ore("lapis", "", 6, "uniform", zero, Placement.absolute(52), 14, 0.0),
                "nickel", ore("nickel", "", 8, "uniform", zero, Placement.absolute(96), 5, 0.0),
                "redstone", ore("redstone", "[lit=false]", 8, "uniform", zero, Placement.absolute(44), 16, 0.0),
                "zinc", ore("zinc", "", 8, "uniform", zero, Placement.absolute(96), 10, 0.0));

        curate(ores.get("copper"), List.of(biomes("tempest_peaks")), List.of(), List.of());
        curate(ores.get("emerald"), List.of(), List.of(groves("flourishing")), List.of(List.of("deep_aether:luminescent_forest")));
        curate(ores.get("gold"), List.of(), List.of(groves("blazing")),
                List.of(List.of("deep_aether:golden_heights", "deep_aether:golden_grove")));
        curate(ores.get("lapis"), List.of(biomes("frozen_crags")), List.of(groves("cascading")),
                List.of(List.of("deep_aether:blue_aerglow_forest")));
        curate(ores.get("nickel"), List.of(biomes("windswept_dunes")), List.of(), List.of());
        curate(ores.get("redstone"), List.of(), List.of(groves("blazing")),
                List.of(List.of("deep_aether:golden_grove", "deep_aether:golden_heights")));
        curate(ores.get("zinc"), List.of(biomes("frozen_crags", "aether_plateau")), List.of(), List.of());
        // the cold groves' lapis is doubled: the veins are the reward for finding the waterfalls
        // (a second placed feature over the same ore: a biome can't list one placed feature twice in a step)
        Ore lapis = ores.get("lapis");
        placed("compat/aoo_lapis_boost", "compat/aoo_lapis", Placement.list(Placement.count(lapis.count()),
                Placement.inSquare(), Placement.heightRange(lapis.shape(), lapis.min(), lapis.max()), Placement.biome()),
                AOO);
        addFeature("compat/aoo_lapis_boost", groves("cascading"), "underground_ores", AOO, ARS);
    }

    /**
     * Writes one ore's features and adds it to its biomes: {@code own} lists need only the ore mod, {@code groves}
     * also Ars Nouveau, and {@code deepAether} also Deep Aether.
     */
    private void curate(Ore ore, List<List<String>> own, List<List<String>> groves, List<List<String>> deepAether) {
        configured("compat/aoo_" + ore.name(), "minecraft:ore", FeatureData.ore(ore.state(), ore.size(), ore.discard(),
                HOLYSTONE), AOO);
        placed("compat/aoo_" + ore.name(), "compat/aoo_" + ore.name(), Placement.list(Placement.count(ore.count()),
                Placement.inSquare(), Placement.heightRange(ore.shape(), ore.min(), ore.max()), Placement.biome()), AOO);
        String name = "compat/aoo_" + ore.name();
        int n = 0;
        for (List<String> biomes : own) {
            addFeature(name + "_own" + n++, name, biomes, "underground_ores", AOO);
        }
        n = 0;
        for (List<String> biomes : groves) {
            addFeature(name + "_grove" + n++, name, biomes, "underground_ores", AOO, ARS);
        }
        n = 0;
        for (List<String> biomes : deepAether) {
            addFeature(name + "_deep" + n++, name, biomes, "underground_ores", AOO, DEEP);
        }
    }

    // --------------------------------------------------------------------------------------- archwood

    private static final int SCATTER_RARITY = 300;

    /**
     * Archwood scattered outside the groves, each colour in the biomes that suit it and nowhere else, at one in 300
     * chunks: golden light for blazing and flashing, blue forests for cascading, skyroot and the luminescent forest
     * for flourishing, the yagroot swamp for vexing, and the storm-lit peaks for flashing too.
     */
    private void archwood() {
        List<String> golden = List.of("deep_aether:golden_heights", "deep_aether:golden_grove");
        List<String> skyroot = List.of("aether:skyroot_forest", "aether:skyroot_grove", "aether:skyroot_meadow",
                "aether:skyroot_woodland");
        scatter("blazing", "ars_nouveau:natural_blazing_tree", "ars_nouveau:red_archwood_sapling", golden, DEEP);
        scatter("cascading", "ars_nouveau:natural_cascading_tree", "ars_nouveau:blue_archwood_sapling",
                List.of("deep_aether:blue_aerglow_forest"), DEEP);
        scatter("flourishing_deep", "ars_nouveau:natural_flourishing_tree", "ars_nouveau:green_archwood_sapling",
                List.of("deep_aether:luminescent_forest"), DEEP);
        scatter("flourishing", "ars_nouveau:natural_flourishing_tree", "ars_nouveau:green_archwood_sapling",
                skyroot, null);
        scatter("vexing", "ars_nouveau:natural_vexing_tree", "ars_nouveau:purple_archwood_sapling",
                List.of("deep_aether:yagroot_swamp"), DEEP);
        scatter("flashing", "ars_elemental:random_simple_flashing", "ars_elemental:yellow_archwood_sapling", golden,
                DEEP, ELEMENTAL);
        scatter("flashing_peaks", "ars_elemental:random_simple_flashing", "ars_elemental:yellow_archwood_sapling",
                biomes("tempest_peaks"), null, ELEMENTAL);
    }

    private void scatter(String name, String tree, String sapling, List<String> biomes,
                         String extraMod, String... more) {
        List<String> mods = new ArrayList<>(List.of(ARS));
        if (extraMod != null) {
            mods.add(extraMod);
        }
        mods.addAll(List.of(more));
        String[] required = mods.toArray(String[]::new);
        placed("scatter/" + name, tree, Placement.list(Placement.rarity(SCATTER_RARITY), Placement.inSquare(),
                Placement.surfaceWaterDepth(0), Placement.heightmap("OCEAN_FLOOR"), Placement.biome(),
                Placement.wouldSurvive(sapling, Map.of("stage", "0"))), required);
        addFeature("scatter/" + name, biomes, "vegetal_decoration", required);
    }

    /** Ars Elemental's own rock formation, scattered about the groves where ground is stone or soil. */
    private void sourcestone() {
        placed("compat/sourcestone_boulder", "ars_elemental:sourcestone_formation", Placement.list(
                Placement.rarity(30), Placement.inSquare(), Placement.heightmap(SURFACE), Placement.biome()),
                ELEMENTAL, ARS);
        addFeature("compat/sourcestone_boulder", groves("blazing", "cascading", "flourishing", "vexing"), "local_modifications",
                ELEMENTAL, ARS);
        addFeature("compat/sourcestone_boulder_flashing", "compat/sourcestone_boulder", groves("flashing"),
                "local_modifications", ELEMENTAL, ARS);
    }

    // ---------------------------------------------------------------------------------- deep aether

    /** Deep Aether's ground cover: our id, its configured feature, and the placement its own biomes use. */
    private static final Map<String, String[]> COVER = Map.of(
            "golden_grove_grass_patch", new String[]{"deep_aether:golden_grove_grass_patch",
                    "[{\"type\":\"minecraft:noise_threshold_count\",\"above_noise\":9,\"below_noise\":5,\"noise_level\":-0.8},"
                            + "{\"type\":\"minecraft:in_square\"},{\"type\":\"minecraft:heightmap\",\"heightmap\":\"WORLD_SURFACE_WG\"},"
                            + "{\"type\":\"minecraft:biome\"}]"},
            "golden_grass_patch", new String[]{"deep_aether:golden_grass_patch",
                    "[{\"type\":\"minecraft:noise_threshold_count\",\"above_noise\":9,\"below_noise\":5,\"noise_level\":-0.8},"
                            + "{\"type\":\"minecraft:in_square\"},{\"type\":\"minecraft:heightmap\",\"heightmap\":\"WORLD_SURFACE_WG\"},"
                            + "{\"type\":\"minecraft:biome\"}]"},
            "golden_vines_patch", new String[]{"deep_aether:golden_vines_patch",
                    "[{\"type\":\"minecraft:count\",\"count\":9},{\"type\":\"minecraft:heightmap\",\"heightmap\":\"WORLD_SURFACE_WG\"},"
                            + "{\"type\":\"minecraft:biome\"},{\"type\":\"aether:dungeon_blacklist_filter\"}]"},
            "aether_cattail_patch", new String[]{"deep_aether:aether_cattails_patch",
                    "[{\"type\":\"minecraft:count\",\"count\":15},{\"type\":\"minecraft:in_square\"},"
                            + "{\"type\":\"minecraft:heightmap\",\"heightmap\":\"MOTION_BLOCKING\"},{\"type\":\"minecraft:biome\"}]"},
            "aerglow_forest_grass", new String[]{"deep_aether:roseroot_forest_grass",
                    "[{\"type\":\"minecraft:count\",\"count\":3},{\"type\":\"minecraft:in_square\"},"
                            + "{\"type\":\"minecraft:heightmap\",\"heightmap\":\"MOTION_BLOCKING\"},{\"type\":\"minecraft:biome\"}]"},
            "sky_tulips", new String[]{"deep_aether:sky_tulips",
                    "[{\"type\":\"minecraft:rarity_filter\",\"chance\":16},{\"type\":\"aether:improved_layer_placement\","
                            + "\"count\":{\"type\":\"minecraft:uniform\",\"max_inclusive\":1,\"min_inclusive\":0},"
                            + "\"heightmap\":\"MOTION_BLOCKING\",\"verticalBounds\":4},{\"type\":\"minecraft:biome\"}]"},
            "aether_moss", new String[]{"deep_aether:aether_moss_patch_bonemeal",
                    "[{\"type\":\"minecraft:count\",\"count\":5},{\"type\":\"minecraft:heightmap\",\"heightmap\":\"WORLD_SURFACE_WG\"},"
                            + "{\"type\":\"minecraft:in_square\"},{\"type\":\"minecraft:biome\"},"
                            + "{\"type\":\"aether:dungeon_blacklist_filter\"}]"});

    /**
     * What the groves had of Deep Aether's ground cover in the World Traveler 2 pack: golden grass and vines in the
     * golden light, cattails by the water, moss and tulips in the green.
     *
     * <p>Each is placed here as a feature of our own, over Deep Aether's configured feature and with the placement
     * its own biomes use. A biome has to list its features in an order that agrees with every other biome in the
     * dimension, or the world won't generate; Deep Aether's biomes list these before the Aether's vegetation, and
     * our groves list them after, and one placed feature can't be in both orders. Features of our own are different
     * ones to the sorter, so there is no conflict.
     */
    private void deepAetherCover() {
        COVER.forEach((name, spec) -> {
            JsonObject placed = new JsonObject();
            placed.addProperty("feature", spec[0]);
            placed.add("placement", com.google.gson.JsonParser.parseString(spec[1]));
            placedJson("cover/" + name, placed, DEEP);
        });
        cover("blazing", groves("blazing"), "golden_grove_grass_patch", "golden_vines_patch");
        cover("flashing", groves("flashing"), "golden_grass_patch", "golden_vines_patch");
        cover("cascading", groves("cascading"), "aether_cattail_patch");
        cover("flourishing", groves("flourishing"), "aerglow_forest_grass", "sky_tulips");
        cover("vexing", groves("vexing"), "aether_cattail_patch", "aether_moss");
    }

    private void cover(String grove, List<String> biomes, String... features) {
        String[] mods = grove.equals("flashing") ? new String[]{DEEP, ARS, ELEMENTAL} : new String[]{DEEP, ARS};
        for (String feature : features) {
            addFeature("cover/" + grove + "_" + feature, "cover/" + feature, biomes, "vegetal_decoration", mods);
        }
    }

    // ---------------------------------------------------------------------------------------- snuffles

    /** Snuffles stand about the crags from the first visit; later ones come from the natural spawns. */
    private void snuffles() {
        JsonObject config = new JsonObject();
        config.addProperty("entity", "snuffles:snuffle");
        config.addProperty("count", 2);
        config.addProperty("spread", 4);
        JsonObject ground = Placement.type("minecraft:matching_block_tag");
        ground.addProperty("tag", "snuffles:snuffles_spawnable_on");
        config.add("ground", ground);
        config.addProperty("persistent", true);
        config.addProperty("mob", "snuffle");
        configured("spawn/snuffle", "spawn_entity", config, "snuffles");
        placed("spawn/snuffle", "spawn/snuffle", Placement.list(Placement.rarity(22), Placement.inSquare(),
                Placement.heightmap(SURFACE), Placement.biome()), "snuffles");
        addFeature("spawn/snuffle", biomes("frozen_crags"), "vegetal_decoration", "snuffles");
    }

    // -------------------------------------------------------------------------------- airship village

    /**
     * Integrated Villages' airship village, set drifting through the Aether. Its own structure set puts it in the
     * overworld; this one is ours, with the village's own template pools, on the Aether's biomes. The start height is
     * a stock-Aether height (166 to 176), which the tall Aether stretches to 185..200 like any other.
     */
    private void airshipVillage() {
        JsonObject start = new JsonObject();
        start.addProperty("type", "minecraft:uniform");
        start.add("min_inclusive", Placement.absolute(166));
        start.add("max_inclusive", Placement.absolute(176));
        JsonObject structure = new JsonObject();
        structure.addProperty("type", "integrated_api:generic_structure");
        structure.addProperty("start_pool", "integrated_villages:airship_village/intersection");
        structure.addProperty("rotation_fixed", true);
        structure.addProperty("size", 30);
        structure.addProperty("max_distance_from_center", 125);
        structure.addProperty("biomes", IS_AETHER);
        structure.addProperty("step", "surface_structures");
        structure.addProperty("terrain_adaptation", "none");
        structure.addProperty("liquid_settings", "ignore_waterlogging");
        structure.add("start_height", start);
        structure.addProperty("use_bounding_box_hack", false);
        structure.add("spawn_overrides", new JsonObject());
        data("worldgen/structure", com.linc.alteredaether.AlteredAether.id("airship_village"), structure,
                "integrated_villages");

        JsonObject placement = new JsonObject();
        placement.addProperty("type", "minecraft:random_spread");
        placement.addProperty("salt", 1982700830);
        placement.addProperty("spacing", 120);
        placement.addProperty("separation", 90);
        placement.addProperty("spread_type", "triangular");
        JsonObject entry = new JsonObject();
        entry.addProperty("structure", NS + "airship_village");
        entry.addProperty("weight", 1);
        com.google.gson.JsonArray structures = new com.google.gson.JsonArray();
        structures.add(entry);
        JsonObject set = new JsonObject();
        set.add("structures", structures);
        set.add("placement", placement);
        data("worldgen/structure_set", com.linc.alteredaether.AlteredAether.id("airship_villages"), set,
                "integrated_villages");
    }
}
