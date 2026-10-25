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

import java.util.List;

import static com.linc.alteredaether.data.Providers.simple;
import static com.linc.alteredaether.data.Providers.uniform;
import static com.linc.alteredaether.data.Providers.weighted;

/**
 * Decoration, ores and generation-time wildlife that need another mod. Every file here carries a mod_loaded
 * condition, and reaches the biomes through a biome modifier carrying the same condition, so nothing in a biome file
 * ever names a mod that might be missing.
 */
public final class AAOptionalDecorationProvider extends FeatureData {
    private static final String SURFACE = "WORLD_SURFACE_WG";
    private static final String CRAG_ROCK = NS + "frozen_crag_ore_replaceables";
    private static final String TEMPEST_ROCK = NS + "tempest_peak_ore_replaceables";
    private static final String HOLYSTONE_ORES = "aether:holystone_ore_replaceables";
    private static final String DIRT = "minecraft:dirt";

    public AAOptionalDecorationProvider(PackOutput output) {
        super(output, "optional decoration");
    }

    @Override
    protected void generate() {
        driedGhasts();
        deepAether();
        ores();
        familiars();
    }

    /** Dried ghasts lie about the dunes in loose fields, sun-bleached on the quicksoil. Dunes only. */
    private void driedGhasts() {
        JsonObject config = patch(5, 7, 2, facings("dried_ghast:dried_ghast"), airOnBlock("aether:quicksoil"));
        configured("ghast/dune_field", "minecraft:random_patch", config, "dried_ghast");
        placed("ghast/dune_field", "ghast/dune_field", Placement.list(
                Placement.configCount(1 / 20.0, "dried_ghast_fields"), Placement.inSquare(),
                Placement.heightmap("MOTION_BLOCKING"), Placement.biome()), "dried_ghast");
        addFeature("ghast/dune_field", biomes("windswept_dunes"), "vegetal_decoration", "dried_ghast");
    }

    /** Totems, carved squash and tiki torches: Deep Aether's blocks, without which the decoration is left out. */
    private void deepAether() {
        // Totems of any grove: Deep Aether's own feature insists on aether grass by identity, so this is rebuilt.
        JsonObject totem = new JsonObject();
        totem.add("blocks", weighted("deep_aether:moa_totem", 1, "deep_aether:zephyr_totem", 1,
                "deep_aether:aerwhale_totem", 1));
        totem.add("height", uniform(2, 4));
        totem.add("plinth", weighted("aether:holystone_bricks", 2, "aether:mossy_holystone[double_drops=true]", 2,
                "aether:holystone[double_drops=true]", 1));
        totem.addProperty("ring_chance", 0.5);
        configured("totem/grove", "totem", totem, "deep_aether");
        placed("totem/grove", "totem/grove", Placement.list(Placement.configCount(1 / 20.0, "totems"),
                Placement.inSquare(), Placement.heightmap("MOTION_BLOCKING_NO_LEAVES"),
                Placement.biomeEdgeFalloff(10, 0.3), Placement.biome()), "deep_aether");
        addFeature("totem/grove", biomes(groves("blazing", "cascading", "flourishing", "vexing").toArray(String[]::new)),
                "vegetal_decoration", "deep_aether", "ars_nouveau");
        // the same placed feature; the flashing grove only exists with Ars Elemental, so it has its own modifier
        addFeature("totem/flashing", "totem/grove", biomes(groves("flashing").toArray(String[]::new)),
                "vegetal_decoration", "deep_aether", "ars_nouveau", "ars_elemental");

        squash("cascading", "deep_aether:carved_blue_squash");
        squash("flourishing", "deep_aether:carved_green_squash");
        squash("vexing", "deep_aether:carved_purple_squash");

        // Blue squash, growing wild on the crags' patches of bare ground.
        configured("squash/frozen_blue", "minecraft:random_patch",
                patch(24, 6, 2, simple("deep_aether:blue_squash"), airOnTag(DIRT)), "deep_aether");
        placed("squash/frozen_blue", "squash/frozen_blue", Placement.list(
                Placement.configCount(1 / 10.0, "vegetation"), Placement.inSquare(),
                Placement.heightmap("MOTION_BLOCKING"), Placement.biome()), "deep_aether");
        addFeature("squash/frozen_blue", biomes("frozen_crags"), "vegetal_decoration", "deep_aether");

        // Sparse blue roseroot trees: Deep Aether's own selector tests whether the sapling would survive, which lands
        // the trees on the crags' patches of bare soil and nowhere else, so they read as lone trees in the snow.
        placed("tree/crag_roseroot", "deep_aether:blue_roseroot_trees_placement", Placement.list(Placement.rarity(5),
                Placement.inSquare(), Placement.surfaceWaterDepth(0), Placement.heightmap("OCEAN_FLOOR"),
                Placement.biome()), "deep_aether");
        addFeature("tree/crag_roseroot", biomes("frozen_crags"), "vegetal_decoration", "deep_aether");

        // A tiki torch is a two-block door-style block, so it is a two-layer column.
        JsonObject torch = column("up", 1, 1, simple("deep_aether:ambrosium_tiki_torch[half=lower]"));
        JsonObject upper = new JsonObject();
        upper.add("height", uniform(1, 1));
        upper.add("provider", simple("deep_aether:ambrosium_tiki_torch[half=upper]"));
        torch.getAsJsonArray("layers").add(upper);
        configured("torch/tiki", "minecraft:block_column", torch, "deep_aether");
        placed("torch/tiki", "torch/tiki", Placement.list(Placement.configCount(2 / 10.0, "totems"),
                Placement.inSquare(), Placement.randomOffset(5, 0, 0), Placement.heightmap("MOTION_BLOCKING"),
                Placement.biome()), "deep_aether");
        addFeature("torch/tiki", biomes("frozen_crags"), "vegetal_decoration", "deep_aether");
    }

    private void squash(String grove, String block) {
        configured("squash/" + grove, "minecraft:random_patch",
                patch(12, 5, 2, facings(block), airOnTag(DIRT)), "deep_aether");
        placed("squash/" + grove, "squash/" + grove, Placement.list(Placement.configCount(1 / 18.0, "vegetation"),
                Placement.inSquare(), Placement.heightmap("MOTION_BLOCKING"), Placement.biome()), "deep_aether");
        addFeature("squash/" + grove, biomes(groves(grove).toArray(String[]::new)), "vegetal_decoration",
                "deep_aether", "ars_nouveau");
    }

    /** Veins for the biomes whose rock the other mods' own ore features can't see. */
    private void ores() {
        // Create's zinc, in the deepslate and blackstone of Tempest Peaks: few veins, big ones, worth the trip.
        configured("ore/tempest_zinc", "minecraft:ore", ore("create:deepslate_zinc_ore", 12, 0.2, TEMPEST_ROCK), "create");
        placed("ore/tempest_zinc", "ore/tempest_zinc", Placement.list(Placement.count(3), Placement.inSquare(),
                Placement.heightRange("uniform", Placement.aboveBottom(0), Placement.aboveBottom(128)),
                Placement.biome()), "create");
        addFeature("ore/tempest_zinc", biomes("tempest_peaks"), "underground_ores", "create");

        // Oreganized's silver, in the crags and the rift.
        configured("ore/silver", "minecraft:ore", ore("oreganized:silver_ore", 10, 0.0, CRAG_ROCK), "oreganized");
        placed("ore/silver", "ore/silver", Placement.list(Placement.count(8), Placement.inSquare(),
                Placement.heightRange("uniform", Placement.aboveBottom(0), Placement.absolute(107)),
                Placement.biome()), "oreganized");
        addFeature("ore/silver", biomes("frozen_crags", "shattered_rift"), "underground_ores", "oreganized");

        // Aether Overworld Ores' iron, as large veins in the dunes' ordinary holystone.
        configured("ore/dune_iron", "minecraft:ore", ore("aether_overworld_ores:holystone_iron_ore", 33, 0.0,
                HOLYSTONE_ORES), "aether_overworld_ores");
        placed("ore/dune_iron", "ore/dune_iron", Placement.list(Placement.count(5), Placement.inSquare(),
                Placement.heightRange("uniform", Placement.aboveBottom(0), Placement.aboveBottom(128)),
                Placement.biome()), "aether_overworld_ores");
        addFeature("ore/dune_iron", biomes("windswept_dunes"), "underground_ores", "aether_overworld_ores");
    }

    /**
     * Ars familiars standing in the groves at generation, so a first visit finds them (later visitors meet them
     * through the natural spawns in AABiomeModifierProvider). They skip spawn placements, which several of them could
     * never pass here, and the persistent ones stay.
     */
    private void familiars() {
        familiar("starbuncle", "ars_nouveau:starbuncle", 3, 8, false, groves("blazing", "cascading", "flourishing", "vexing"),
                "ars_nouveau");
        familiar("starbuncle_flashing", "ars_nouveau:starbuncle", 3, 8, false, groves("flashing"), "ars_nouveau", "ars_elemental");
        familiar("drygmy", "ars_nouveau:drygmy", 2, 10, false, groves("flourishing"), "ars_nouveau");
        familiar("whirlisprig", "ars_nouveau:whirlisprig", 2, 10, false, groves("flourishing"), "ars_nouveau");
        familiar("alakarkinos", "ars_nouveau:alakarkinos", 1, 14, false, groves("cascading"), "ars_nouveau");
        familiar("flashjack", "ars_elemental:flashjack_entity", 3, 10, false, groves("flashing"), "ars_nouveau", "ars_elemental");
        familiar("siren", "ars_elemental:siren_entity", 1, 12, true, groves("cascading"), "ars_nouveau", "ars_elemental");
        for (String grove : List.of("blazing", "cascading", "flourishing", "vexing")) {
            familiar(grove + "_weald_walker", "ars_nouveau:" + grove + "_weald_walker", 1, 12, false, groves(grove),
                    "ars_nouveau");
        }
        familiar("flashing_weald_walker", "ars_elemental:flashing_weald_walker", 1, 12, false, groves("flashing"),
                "ars_nouveau", "ars_elemental");

        // A starbuncle band here and there across the whole dimension, on top of the groves' own, and a pair of
        // drygmies on the plateau.
        familiar("starbuncle_wild", "ars_nouveau:starbuncle", 3, 625, false, List.of(IS_AETHER), "ars_nouveau");
        familiar("drygmy_plateau", "ars_nouveau:drygmy", 2, 60, false, biomes("aether_plateau"), "ars_nouveau");
    }

    private void familiar(String name, String entity, int group, int rarity, boolean water, List<String> biomes,
                          String... mods) {
        JsonObject config = new JsonObject();
        config.addProperty("entity", entity);
        config.addProperty("count", group);
        config.addProperty("spread", group > 1 ? 4 : 0);
        config.addProperty("medium", water ? "water" : "ground");
        if (!water) {
            JsonObject ground = Placement.type("minecraft:matching_block_tag");
            ground.addProperty("tag", DIRT);
            config.add("ground", ground);
        }
        config.addProperty("persistent", true);
        // the config key under [spawns] is the entity's path, or its grove-prefixed form for the weald walkers
        String path = entity.substring(entity.indexOf(':') + 1);
        config.addProperty("mob", path.equals("flashjack_entity") ? "flashjack" : path.equals("siren_entity") ? "siren" : path);
        configured("spawn/" + name, "spawn_entity", config, mods);
        placed("spawn/" + name, "spawn/" + name, Placement.list(Placement.rarity(rarity), Placement.inSquare(),
                Placement.heightmap(SURFACE), Placement.biome()), mods);
        addFeature("spawn/" + name, biomes, "vegetal_decoration", mods);
    }
}
