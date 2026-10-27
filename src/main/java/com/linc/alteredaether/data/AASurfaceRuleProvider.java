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

import java.util.Arrays;

/**
 * Surface rules for our biomes, ported from World Traveler 2 and extended with cliffs, snow caps and strata. Read by
 * {@code AASurfaceRules} at startup and registered with TerraBlender for the {@code altered_aether} namespace only.
 *
 * <p>Noise thresholds were measured against the exact vanilla surface noises (noise_stats.py); guessing them is how a
 * biome ends up 90% one block. Marginal shares: surface &gt; 0.44 is 8% (64-block patches), packed_ice &gt; 0.35 is
 * 13% (128-block sheets), powder_snow &gt; 0.12 is 35% (64-block drifts), surface_secondary &gt; 0.18 is 28%
 * (64-block bands), calcite &gt; 0.26 is 20% (512-block strata).
 */
public final class AASurfaceRuleProvider extends JsonDataProvider {
    /** Noise values never approach this; it stands in for infinity. */
    private static final double BIG = 4.0;

    private static final String GRASS = "aether:aether_grass_block[double_drops=true,snowy=false]";
    private static final String DIRT = "aether:aether_dirt[double_drops=true]";
    private static final String ENCHANTED_GRASS = "aether:enchanted_aether_grass_block[snowy=false]";
    private static final String QUICKSOIL = "aether:quicksoil[double_drops=true]";
    private static final String DIRT_PATH = "aether:aether_dirt_path";
    private static final String HOLYSTONE = "aether:holystone[double_drops=true]";
    private static final String MOSSY_HOLYSTONE = "aether:mossy_holystone[double_drops=true]";
    private static final String ICESTONE = "aether:icestone";
    private static final String SNOW_BLOCK = "minecraft:snow_block";
    private static final String ICE = "minecraft:ice";
    private static final String DEEPSLATE = "minecraft:cobbled_deepslate";
    private static final String BLACKSTONE = "minecraft:blackstone";
    private static final String GRAVEL = "minecraft:gravel";
    // Deep Aether blocks, placed through substitute_block so they fall back without it.
    private static final String COARSE_DIRT = "deep_aether:aether_coarse_dirt[double_drops=true]";
    private static final String GOLDEN_GRASS = "deep_aether:golden_heights_grass_block[double_drops=true,snowy=false]";
    private static final String MUD = "deep_aether:aether_mud[double_drops=true]";
    private static final String QUICKSAND = "deep_aether:virulent_quicksand";
    private static final String CLORITE = "deep_aether:clorite";

    private static final String PLATEAU = biome("aether_plateau");
    private static final String CRAGS = biome("frozen_crags");
    private static final String TEMPEST = biome("tempest_peaks");
    private static final String RIFT = biome("shattered_rift");
    private static final String DUNES = biome("windswept_dunes");
    private static final String CATHEDRAL = biome("skyroot_cathedral");
    private static final String NURSELOG = biome("nurselog_hollow");

    public AASurfaceRuleProvider(PackOutput output) {
        super(output, "surface rules");
    }

    @Override
    protected void generate() {
        data("surface_rules", AlteredAether.id("aether"), seq(
                when(stoneDepth(false), floor()),
                when(stoneDepth(true), under()),
                body()));
    }

    private static JsonObject floor() {
        // Plateau footpaths: the zero contour of a noise is a meandering constant-width ribbon (a window straddling
        // zero), gated by a low-frequency region noise so paths are occasional rather than everywhere.
        JsonObject region = noiseBand("altered_aether:path_region", 0.15, BIG);
        JsonObject paths = seq(
                when(region, when(noiseBand("altered_aether:plateau_path", -0.0125, 0.0125), block(DIRT_PATH))),
                when(region, when(noiseBand("altered_aether:plateau_quicksoil_vein", -0.008, 0.008),
                        block(QUICKSOIL))));

        return seq(
                cliffs(),
                // Snow caps the upper part of each crag, where real peaks hold snow longest.
                when(inBiome(CRAGS), when(islandPosition(0.7, 1.0, 16), block(SNOW_BLOCK))),
                when(inBiome(PLATEAU), paths),
                when(inBiome(grove("cascading")), substitute(COARSE_DIRT)),
                when(inBiome(grove("flashing")), substitute(GOLDEN_GRASS)),
                when(inBiome(grove("flourishing")), block(ENCHANTED_GRASS)),
                // Mirrors Deep Aether's own yagroot swamp: mud with quicksand pools.
                when(inBiome(grove("vexing")), seq(
                        when(noiseBand("minecraft:surface_swamp", 0.7, BIG), substitute(QUICKSAND)),
                        substitute(MUD))),
                // Golden grove ground with scorched dirt patches burnt through it.
                when(inBiome(grove("blazing")), seq(
                        when(noiseBand("minecraft:surface", 0.15, BIG), block(DIRT)),
                        substitute(GOLDEN_GRASS))),
                // Icestone showing through snow drifts and ice sheets, with occasional coarse dirt, which is also
                // the only ground here trees and squash can root in.
                when(inBiome(CRAGS), seq(
                        when(noiseBand("minecraft:surface", 0.44, BIG), substitute(COARSE_DIRT)),
                        when(noiseBand("minecraft:packed_ice", 0.35, BIG), block(ICE)),
                        when(noiseBand("minecraft:powder_snow", 0.12, BIG), block(SNOW_BLOCK)),
                        block(ICESTONE))),
                // Barren blasted stone: blackstone scorch scars, gravel rubble, cobbled deepslate.
                when(inBiome(TEMPEST), seq(
                        when(noiseBand("minecraft:surface", 0.44, BIG), block(BLACKSTONE)),
                        when(noiseBand("minecraft:powder_snow", 0.12, BIG), block(GRAVEL)),
                        block(DEEPSLATE))),
                // Bare broken holystone with coarse dirt patches for the totems, squash and animals.
                when(inBiome(RIFT), seq(
                        when(noiseBand("minecraft:surface", 0.44, BIG), substitute(COARSE_DIRT)),
                        when(noiseBand("minecraft:calcite", 0.26, BIG), block(MOSSY_HOLYSTONE)),
                        block(HOLYSTONE))),
                when(inBiome(DUNES), block(QUICKSOIL)),
                // Root litter: coarse dirt patches in the cathedral's shade, and wide worn ground between the fallen
                // logs, with bare aether dirt in bands.
                when(inBiome(CATHEDRAL), when(noiseBand("minecraft:surface", 0.44, BIG), substitute(COARSE_DIRT))),
                when(inBiome(NURSELOG), seq(
                        when(noiseBand("minecraft:surface", 0.15, BIG), substitute(COARSE_DIRT)),
                        when(noiseBand("minecraft:surface_secondary", 0.18, BIG), block(DIRT)))),
                // Stock Aether ground for everything not matched above.
                when(water(), block(GRASS)),
                block(DIRT));
    }

    /**
     * Steep ground shows the rock beneath instead of grass, so cliffs read as cliffs from every side. Dunes keep their
     * quicksoil on the lee faces, which are meant to be steep sand.
     */
    private static JsonObject cliffs() {
        JsonObject holystoneRock = seq(when(noiseBand("minecraft:calcite", 0.26, BIG), block(MOSSY_HOLYSTONE)),
                block(HOLYSTONE));
        return seq(
                // The mountains show rock on any real slope.
                when(steep(3), seq(
                        when(inBiome(PLATEAU), plateauStrata()),
                        when(inBiome(CRAGS), seq(when(noiseBand("minecraft:surface_secondary", 0.18, BIG),
                                substitute(CLORITE)), block(ICESTONE))),
                        when(inBiome(TEMPEST), seq(when(noiseBand("minecraft:surface_secondary", 0.18, BIG),
                                block(BLACKSTONE)), block(DEEPSLATE))))),
                // Forested and gentler islands only on true cliffs, so ordinary lumpy ground stays green.
                when(steep(5), when(inBiome(RIFT, CATHEDRAL, NURSELOG, grove("blazing"), grove("cascading"), grove("flashing"),
                        grove("flourishing"), grove("vexing")), holystoneRock)));
    }

    private static JsonObject steep(int rise) {
        JsonObject steep = typed("altered_aether:steep");
        steep.addProperty("distance", 2);
        steep.addProperty("rise", rise);
        return steep;
    }

    private static JsonObject under() {
        return seq(
                when(inBiome(grove("vexing")), substitute(MUD)),
                // The crags, peaks and rift are stone all the way down; no dirt under the snow.
                when(inBiome(CRAGS), block(ICESTONE)),
                when(inBiome(TEMPEST), block(DEEPSLATE)),
                when(inBiome(RIFT), block(HOLYSTONE)),
                // A few blocks of quicksoil under the dunes, so digging in doesn't hit ordinary dirt.
                when(inBiome(DUNES), block(QUICKSOIL)),
                block(DIRT));
    }

    /**
     * The island bodies. Anything not matched here keeps the dimension's holystone. The plateau's body is banded
     * rock, so every mesa cliff shows sedimentary layers.
     */
    private static JsonObject body() {
        return seq(
                when(inBiome(PLATEAU), plateauStrata()),
                when(inBiome(CRAGS), seq(
                        when(noiseBand("minecraft:surface_secondary", 0.18, BIG), substitute(CLORITE)),
                        when(noiseBand("minecraft:calcite", 0.26, BIG), block(HOLYSTONE)),
                        block(ICESTONE))),
                when(inBiome(TEMPEST), seq(
                        when(noiseBand("minecraft:surface_secondary", 0.18, BIG), block(BLACKSTONE)),
                        when(noiseBand("minecraft:calcite", 0.26, BIG), block(HOLYSTONE)),
                        block(DEEPSLATE))),
                when(inBiome(RIFT), seq(
                        when(noiseBand("minecraft:surface_secondary", 0.18, BIG), block(MOSSY_HOLYSTONE)),
                        block(HOLYSTONE))));
    }

    /** Holystone layers with mossy and icy seams; the pattern repeats every 24 blocks. */
    private static JsonObject plateauStrata() {
        JsonObject strata = typed("altered_aether:strata");
        JsonArray bands = new JsonArray();
        for (String band : new String[]{HOLYSTONE, HOLYSTONE, MOSSY_HOLYSTONE, HOLYSTONE, ICESTONE, HOLYSTONE,
                MOSSY_HOLYSTONE, MOSSY_HOLYSTONE}) {
            bands.add(Substitutes.state(band));
        }
        strata.add("bands", bands);
        strata.addProperty("band_height", 3);
        strata.addProperty("undulation", 2.5);
        return strata;
    }

    // ------------------------------------------------------------------------------------------- builders --

    private static String biome(String name) {
        return AlteredAether.MOD_ID + ":" + name;
    }

    private static String grove(String colour) {
        return biome(colour + "_archwood_grove");
    }

    private static JsonObject seq(JsonObject... rules) {
        JsonObject json = typed("minecraft:sequence");
        JsonArray list = new JsonArray();
        Arrays.stream(rules).forEach(list::add);
        json.add("sequence", list);
        return json;
    }

    private static JsonObject when(JsonObject condition, JsonObject then) {
        JsonObject json = typed("minecraft:condition");
        json.add("if_true", condition);
        json.add("then_run", then);
        return json;
    }

    private static JsonObject inBiome(String... biomes) {
        JsonObject json = typed("minecraft:biome");
        JsonArray list = new JsonArray();
        Arrays.stream(biomes).forEach(list::add);
        json.add("biome_is", list);
        return json;
    }

    private static JsonObject noiseBand(String noise, double min, double max) {
        JsonObject json = typed("minecraft:noise_threshold");
        json.addProperty("noise", noise);
        json.addProperty("min_threshold", min);
        json.addProperty("max_threshold", max);
        return json;
    }

    private static JsonObject stoneDepth(boolean addSurfaceDepth) {
        JsonObject json = typed("minecraft:stone_depth");
        json.addProperty("add_surface_depth", addSurfaceDepth);
        json.addProperty("offset", 0);
        json.addProperty("secondary_depth_range", 0);
        json.addProperty("surface_type", "floor");
        return json;
    }

    private static JsonObject water() {
        JsonObject json = typed("minecraft:water");
        json.addProperty("add_stone_depth", false);
        json.addProperty("offset", -1);
        json.addProperty("surface_depth_multiplier", 0);
        return json;
    }

    private static JsonObject islandPosition(double min, double max, int minThickness) {
        JsonObject json = typed("altered_aether:island_position");
        json.addProperty("min", min);
        json.addProperty("max", max);
        json.addProperty("min_thickness", minThickness);
        return json;
    }

    private static JsonObject block(String state) {
        JsonObject json = typed("minecraft:block");
        json.add("result_state", Substitutes.state(state));
        return json;
    }

    private static JsonObject substitute(String primary) {
        JsonObject provider = Substitutes.provider(primary);
        JsonObject json = typed("altered_aether:substitute_block");
        json.add("candidates", provider.get("candidates"));
        return json;
    }

    private static JsonObject typed(String type) {
        JsonObject json = new JsonObject();
        json.addProperty("type", type);
        return json;
    }
}
