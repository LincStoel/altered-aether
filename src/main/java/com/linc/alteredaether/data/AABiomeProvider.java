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

import static com.linc.alteredaether.data.BiomeJson.spawner;

/**
 * The biomes. Values are ported from the final World Traveler 2 biome files, including hand tuning made after the
 * Python generators last ran. Biome JSON can't be conditional per entry, so a biome only names mods it already
 * requires: the archwood groves name Ars Nouveau (and the flashing grove Ars Elemental), and everything else that
 * involves an optional mod arrives through conditional biome modifiers.
 */
public final class AABiomeProvider extends JsonDataProvider {
    private static final String FOREST_TREES = "aether:skyroot_forest_trees";
    private static final String[] VANILLA_GRASS = {"aether:grass_patch", "aether:tall_grass_patch"};
    /** Nothing here can grow on bare stone, and the stock ores only target holystone. */
    private static final String[] BARREN = {
            "aether:grass_patch", "aether:tall_grass_patch", "aether:white_flower_patch",
            "aether:purple_flower_patch", "aether:berry_bush_patch", "aether:holiday_tree",
            "aether:water_lake", "aether:water_spring", "aether:quicksoil_shelf",
            "aether:aether_dirt_ore", "aether:icestone_ore", "aether:ambrosium_ore", "aether:zanite_ore",
            "aether:gravitite_ore", "aether:gravitite_ore_buried"};
    private static final String[] STOCK_VEGETATION = {
            FOREST_TREES, "aether:holiday_tree", "aether:grass_patch", "aether:tall_grass_patch",
            "aether:white_flower_patch", "aether:purple_flower_patch", "aether:berry_bush_patch"};
    private static final String ARIA = "ars_nouveau:aria_biblio";
    private static final int GROVE_FOG = 12638463;
    private static final int ARS_WATER = 7978751;

    private JsonObject template;

    public AABiomeProvider(PackOutput output) {
        super(output, "biomes");
    }

    @Override
    protected void generate() {
        template = ModResources.json("aether", "data", "aether", "worldgen", "biome", "skyroot_forest.json");

        biome("aether_plateau", plateau());
        biome("frozen_crags", frozenCrags());
        biome("tempest_peaks", tempestPeaks());
        biome("shattered_rift", shatteredRift());
        biome("windswept_dunes", windsweptDunes());
        biome("sunken_atoll", sunkenAtoll());

        biome("blazing_archwood_grove", grove("blazing", ARIA)
                .climate(0.5f, 0.65f)
                .effect("foliage_color", 12470327).effect("grass_color", 12873818).effect("sky_color", 16750472),
                "ars_nouveau");
        biome("cascading_archwood_grove", grove("cascading", ARIA)
                .climate(0.45f, 0.5f)
                .effect("foliage_color", 3958452).effect("grass_color", 8166354).effect("sky_color", 8958719)
                .particle(BiomeJson.particleType("aether:crystal_leaves"), 0.0025f)
                // The stock lake is a fixed 16x16x8 blob that hangs off island edges; this grove gets real lakes.
                .drop("aether:water_lake")
                .add(BiomeJson.SPRINGS, AAFeatureProvider.WATERFALL_CASCADING)
                .weight("aether:blue_swet", 20), "ars_nouveau");
        biome("flashing_archwood_grove", grove("flashing", ARIA)
                .climate(1.95f, 0.0f)
                .effect("foliage_color", 13084948).effect("grass_color", 13414701).effect("sky_color", 16769928)
                .drop(VANILLA_GRASS)
                .weight("aether:golden_swet", 20), "ars_nouveau", "ars_elemental");
        biome("flourishing_archwood_grove", grove("flourishing", ARIA)
                .climate(0.65f, 0.8f)
                .effect("foliage_color", 5290563).effect("grass_color", 7654499).effect("sky_color", 9830280),
                "ars_nouveau");
        biome("vexing_archwood_grove", grove("vexing", "ars_nouveau:firel_the_wild_hunt")
                .climate(0.75f, 0.9f)
                .effect("fog_color", 14386175).effect("foliage_color", 8732340).effect("grass_color", 10384582)
                .effect("sky_color", 10979583).effect("water_color", 12080623).effect("water_fog_color", 11832560)
                .drop("aether:water_lake")
                .add(BiomeJson.SPRINGS, AAFeatureProvider.WATERFALL_VEXING),
                "ars_nouveau");
    }

    private void biome(String name, BiomeJson biome, String... requiredMods) {
        data("worldgen/biome", AlteredAether.id(name), biome.json, requiredMods);
    }

    private BiomeJson base() {
        return new BiomeJson(template);
    }

    /**
     * Flat, open and visible all at once, so it reads far busier than the islands do; mob density is thinned on all
     * three levers (initial population, herd size, crowding tolerance).
     */
    private BiomeJson plateau() {
        return base()
                .climate(0.75f, 0.4f)
                .replace(FOREST_TREES, "aether:skyroot_meadow_trees")
                // Pools and deep lakes replace the stock lake, a fixed blob that hangs off island edges.
                .drop("aether:water_lake")
                .add(BiomeJson.SPRINGS, AAFeatureProvider.WATERFALL_PLATEAU)
                .add(BiomeJson.LOCAL, AADecorationProvider.PLATEAU_BOULDER)
                .creatureProbability(0.07f)
                .packScale(0.5)
                .budgetScale(0.6);
    }

    /** Coldest biome in the dimension. Ground and stone come from the surface rules; this is features and mobs. */
    private BiomeJson frozenCrags() {
        return base()
                .climate(-0.05f, 0.5f)
                .drop(BARREN).drop(FOREST_TREES)
                .drop("aether:water_lake")
                .effect("fog_color", 12575730).effect("foliage_color", 9418436).effect("grass_color", 10470345)
                .effect("sky_color", 11984111).effect("water_color", 4025996).effect("water_fog_color", 1981520)
                .particle(BiomeJson.particleType("minecraft:snowflake"), 0.0035f)
                .add(BiomeJson.LOCAL, AADecorationProvider.CRAG_BOULDER, AADecorationProvider.AMBROSIUM_BOULDER)
                .spawners("creature",
                        spawner("aether:aerbunny", 12, 2, 3),
                        spawner("aether:sheepuff", 10, 2, 3))
                // aechor plants and golden swets need aether grass by identity; a dead entry still eats its share
                // of the category's spawn attempts, so they are removed rather than left to fail.
                .spawners("aether:aether_surface_monster",
                        spawner("aether:blue_swet", 20, 1, 1),
                        spawner("aether:whirlwind", 3, 1, 1),
                        spawner("aether:evil_whirlwind", 1, 1, 1))
                // The zephyr is the only entry in its category, so weight is inert; group size and spawn cost are
                // what make the crags' skies thick with them.
                .spawners("aether:aether_sky_monster", spawner("aether:zephyr", 20, 1, 2))
                // Valkyries have no SpawnPlacements entry, so they spawn naturally; weight 1 keeps them the rare one.
                .spawners("monster", spawner("aether:valkyrie", 1, 2, 3))
                .dropCost("aether:aechor_plant", "aether:golden_swet")
                .cost("aether:zephyr", 0.4, 0.35)
                .cost("aether:valkyrie", 0.9, 0.03);
    }

    private BiomeJson tempestPeaks() {
        return base()
                .climate(1.95f, 0.0f)
                .drop(BARREN).drop(FOREST_TREES)
                .effect("fog_color", 13214247).effect("foliage_color", 9211020).effect("grass_color", 9211020)
                .effect("sky_color", 15915105)
                .particle(BiomeJson.particleType("minecraft:electric_spark"), 0.006f)
                .add(BiomeJson.LOCAL, AADecorationProvider.TEMPEST_SPIRES, AADecorationProvider.TEMPEST_LONE_SPIKE)
                .spawners("aether:aether_surface_monster",
                        spawner("aether:blue_swet", 20, 1, 1),
                        spawner("aether:whirlwind", 3, 1, 1),
                        spawner("aether:evil_whirlwind", 1, 1, 1))
                .dropCost("aether:aechor_plant", "aether:golden_swet");
    }

    /** Bare broken holystone: no lakes, springs or stock vegetation, and aerwhales drift through the gaps. */
    private BiomeJson shatteredRift() {
        return base()
                .climate(1.95f, 0.0f)
                .drop("aether:quicksoil_shelf", "aether:water_lake", "aether:water_spring")
                .drop(STOCK_VEGETATION)
                .effect("fog_color", 9412787).effect("foliage_color", 7240558).effect("grass_color", 7240558)
                .effect("sky_color", 11583177).effect("water_color", 4875634).effect("water_fog_color", 2965066)
                .add(BiomeJson.LOCAL, AADecorationProvider.RIFT_DEBRIS, AADecorationProvider.RIFT_SLAB_FRAGMENTS)
                .spawners("aether:aether_aerwhale", spawner("aether:aerwhale", 10, 1, 3))
                .cost("aether:aerwhale", 0.3, 0.4);
    }

    /** Arid quicksoil dunes: whirlwinds in numbers, hardly any wildlife. */
    private BiomeJson windsweptDunes() {
        BiomeJson biome = base()
                .climate(1.95f, 0.0f)
                .drop("aether:quicksoil_shelf", "aether:water_lake", "aether:cold_aercloud", "aether:blue_aercloud")
                .drop(STOCK_VEGETATION)
                .effect("fog_color", 14263434).effect("foliage_color", 12105576).effect("grass_color", 12766328)
                .effect("sky_color", 15787686).effect("water_color", ARS_WATER)
                .particle(dust(0.87f, 0.75f, 0.42f), 0.015f)
                .add(BiomeJson.LOCAL, AADecorationProvider.DUNE_BOULDER)
                .spawners("aether:aether_aerwhale")
                .spawners("aether:aether_darkness_monster")
                .spawners("aether:aether_sky_monster", spawner("aether:zephyr", 20, 1, 1))
                .spawners("aether:aether_surface_monster", spawner("aether:whirlwind", 20, 2, 4))
                .spawners("creature", spawner("aether:aerbunny", 11, 3, 3));
        JsonObject costs = new JsonObject();
        biome.json.add("spawn_costs", costs);
        return biome.cost("aether:whirlwind", 0.4, 0.1).cost("aether:zephyr", 0.6, 0.16);
    }

    /**
     * A ring of land around a sunlit lagoon. The basin and its water come from the terrain and the basin fill
     * feature. Vanilla's water mobs and turtles key their spawning to sea level, which the Aether doesn't have:
     * tropical fish use the atoll's place in vanilla's any-height tag, and the rest have Aether-only spawn rules
     * (SpawnPlacementEvents). Values follow vanilla's warm ocean and beach.
     */
    private BiomeJson sunkenAtoll() {
        return base()
                .climate(0.85f, 0.8f)
                .replace(FOREST_TREES, "aether:skyroot_meadow_trees")
                .drop("aether:water_lake", "aether:holiday_tree")
                .effect("fog_color", 11396863).effect("sky_color", 10875903)
                .effect("water_color", 0x3FCFD8).effect("water_fog_color", 0x1A8C9C)
                .effect("grass_color", 0x9EDB8A).effect("foliage_color", 0x7FCC6A)
                .spawners("water_ambient", spawner("minecraft:tropical_fish", 25, 8, 8))
                .spawners("underground_water_creature", spawner("minecraft:glow_squid", 10, 2, 4))
                .addSpawner("creature", spawner("minecraft:turtle", 5, 2, 5));
    }

    private BiomeJson grove(String colour, String music) {
        return base()
                .replace(FOREST_TREES, groveTrees(colour))
                .music(music)
                .effect("fog_color", GROVE_FOG)
                .effect("water_color", ARS_WATER);
    }

    /** Four groves use our own placement of Ars Nouveau's trees; the flashing archwood belongs to Ars Elemental. */
    static String groveTrees(String colour) {
        return colour.equals("flashing")
                ? "ars_elemental:cluster_flashing_archwood"
                : AlteredAether.MOD_ID + ":grove_trees/" + colour;
    }

    private static JsonObject dust(float r, float g, float b) {
        JsonObject options = BiomeJson.particleType("minecraft:dust");
        JsonArray color = new JsonArray();
        color.add(r);
        color.add(g);
        color.add(b);
        options.add("color", color);
        options.addProperty("scale", 1.0f);
        return options;
    }
}
