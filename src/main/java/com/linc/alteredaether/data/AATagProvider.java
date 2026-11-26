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

import java.util.ArrayList;
import java.util.List;
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
        // empty: a place for packs to name ores boulders must never carry
        tag("block", AlteredAether.id("boulder_ore_blacklist"));
        // Every Aether soil is in #minecraft:dirt (aether grass and dirt, enchanted grass, and Deep Aether's coarse
        // dirt, mud and golden grass), so familiars follow the groves' varied surfaces without a list of their own.
        tag("block", AlteredAether.id("familiar_spawnable_on"), "#minecraft:dirt");
        // Mowzie's own rules want vanilla grass and overworld stone (and the overworld itself, below Y 68).
        tag("block", AlteredAether.id("naga_spawnable_on"), "#minecraft:dirt", "#aether:holystone");
        tag("block", AlteredAether.id("lantern_spawnable_on"), "#minecraft:dirt", "#minecraft:leaves",
                "#minecraft:logs");
        tag("worldgen/biome", AlteredAether.id("has_lanterns"), "altered_aether:skyroot_cathedral",
                "deep_aether:luminescent_forest");
        // Snuffles key their spawning to this tag (and light), not sea level or the biome, so the Frozen Crags' ground
        // and the aerglow forests' grass only need adding to it. They spawn only in the biomes we list.
        tag("block", ResourceLocation.fromNamespaceAndPath("snuffles", "snuffles_spawnable_on"),
                "aether:icestone", "minecraft:snow_block", "minecraft:ice", "minecraft:packed_ice",
                "deep_aether:aether_coarse_dirt", "aether:aether_grass_block");
        // The ores that are holystone-only in the Aether have to be retargeted at the rock of the biomes that replace it.
        tag("block", AlteredAether.id("frozen_crag_ore_replaceables"), "aether:icestone", "deep_aether:clorite",
                "aether:holystone");
        tag("block", AlteredAether.id("tempest_peak_ore_replaceables"), "minecraft:cobbled_deepslate",
                "minecraft:blackstone", "aether:holystone");
        tag("block", ResourceLocation.withDefaultNamespace("campfires"), "altered_aether:ambrosium_campfire");
        // Create's fans enchant through a lit ambrosium campfire, ours or Beyond Parity's (see CreateCompat).
        tag("block", AlteredAether.id("fan_processing_catalysts/enchanting"), "altered_aether:ambrosium_campfire",
                "aether_beyond_parity:ambrosium_campfire");
        tag("block", ResourceLocation.withDefaultNamespace("mineable/axe"), "altered_aether:ambrosium_campfire",
                "altered_aether:gleam_vines", "altered_aether:gleam_vines_plant");
        // the raw gravitite block mines like the ore it comes from
        tag("block", ResourceLocation.withDefaultNamespace("mineable/pickaxe"),
                concat(concat(new String[]{"altered_aether:raw_gravitite_block"}, AAStoneProvider.ids(null)),
                        AAElectricProvider.PICKAXE));
        tag("block", ResourceLocation.withDefaultNamespace("needs_iron_tool"), "altered_aether:raw_gravitite_block");
        // dead bushes may stand on the dunes' quicksoil
        tag("block", ResourceLocation.withDefaultNamespace("dead_bush_may_place_on"), "aether:quicksoil");
        // Tempest storms (see TempestStorms) and the natural rock their strikes scorch.
        tag("worldgen/biome", AlteredAether.id("has_storms"), "altered_aether:tempest_peaks");
        // Zephyrs and squalls may spawn in midair here (see SpawnPlacementEvents), where the biome lists them.
        tag("worldgen/biome", AlteredAether.id("midair_zephyrs"), "altered_aether:tempest_peaks",
                "altered_aether:frozen_crags", "altered_aether:shattered_rift");
        tag("block", AlteredAether.id("scorchable"), "aether:holystone", "aether:mossy_holystone", "aether:icestone",
                "minecraft:cobbled_deepslate", "minecraft:deepslate", "minecraft:stone", "minecraft:snow_block",
                "deep_aether:aseterite", "deep_aether:clorite");
        lightning();
        tag("entity_type", AlteredAether.id("ignores_aercloud_drag"), "aether:zephyr", "altered_aether:squall",
                "aether:aerwhale", "altered_aether:tamed_aerwhale", "mowziesmobs:naga");
        // Deep Aether's sterling aerclouds call lightning on whatever passes through and turn cold; zephyrs are spared,
        // and so are squalls.
        tag("entity_type", ResourceLocation.fromNamespaceAndPath("deep_aether", "sterling_aercloud_blacklist"),
                "altered_aether:squall");
        // Tamed aerwhales (see TamedAerwhale).
        tag("item", AlteredAether.id("aerwhale_taming_food"), "aether:blue_gummy_swet");
        tag("item", AlteredAether.id("aerwhale_breeding_food"), "aether:enchanted_berry");
        gleamVines();
        stones();
        aetherBiomes();
        golem();
    }

    /**
     * Gravitite golems (see entity/golem/GolemStorage): what can be their head, what they only empty, and what they
     * never link. Most machines are caught by what their block entity is; the blacklist names the rest, for vanilla,
     * the Aether and the popular mods with machines or storage networks, as optional entries.
     */
    private void golem() {
        tag("block", AlteredAether.id("golem_heads"), "minecraft:carved_pumpkin", "deep_aether:carved_blue_squash",
                "deep_aether:carved_green_squash", "deep_aether:carved_purple_squash");
        tag("block", AlteredAether.id("golem_empty_only"), "minecraft:hopper", "create:depot");
        tag("block", AlteredAether.id("golem_linkable"));
        // Aether stones (ours, the Aether's and Deep Aether's) and glowstone dust buy a storage pebble from a golem
        tag("item", AlteredAether.id("aether_stones"), concat(new String[]{"#aether:holystone", "aether:icestone",
                "deep_aether:aseterite", "deep_aether:clorite", "deep_aether:highstone"}, AAStoneProvider.naturalIds()));
        tag("item", AlteredAether.id("aether_cobblestones"), "deep_aether:cobbled_aseterite");
        tag("item", AlteredAether.id("golem_pebble_price"), "#altered_aether:aether_stones",
                "#altered_aether:aether_cobblestones", "minecraft:glowstone_dust");
        // the odds and ends go into crates first; drawers are found by their slots, compacting ones named
        tag("block", AlteredAether.id("golem_misc_storage"), "quark:crate");
        tag("block", AlteredAether.id("golem_bulk"));
        tag("block", AlteredAether.id("golem_bulk_pooled"), "functionalstorage:compacting_drawer",
                "functionalstorage:simple_compacting_drawer", "functionalstorage:compacting_framed_drawer",
                "functionalstorage:framed_simple_compacting_drawer", "storagedrawers:compacting_drawers_2",
                "storagedrawers:compacting_drawers_3", "storagedrawers:compacting_half_drawers_2",
                "storagedrawers:compacting_half_drawers_3");
        List<String> never = new ArrayList<>(List.of(
                // vanilla machines and inventories that aren't storage
                "minecraft:furnace", "minecraft:blast_furnace", "minecraft:smoker", "minecraft:brewing_stand",
                "minecraft:dropper", "minecraft:dispenser", "minecraft:crafter", "#minecraft:campfires",
                "minecraft:composter", "minecraft:lectern", "minecraft:jukebox", "minecraft:chiseled_bookshelf",
                "minecraft:decorated_pot", "minecraft:ender_chest", "minecraft:beacon", "minecraft:vault",
                // the Aether's machines, its locked dungeon chests and mimics
                "aether:altar", "aether:freezer", "aether:incubator", "aether:treasure_chest", "aether:chest_mimic",
                "aether:sun_altar", "altered_aether:ambrosium_campfire",
                "deep_aether:combiner", "deep_aether:poison_cauldron", "aether_beyond_parity:ambrosium_campfire",
                // Create: everything that moves or processes items, belts most of all (a brass funnel putting items
                // into a block is linked as a sink, see CreateFunnels)
                "create:belt", "create:chute", "create:smart_chute", "create:andesite_funnel",
                "create:andesite_belt_funnel", "create:brass_belt_funnel", "create:andesite_tunnel", "create:brass_tunnel",
                "create:basin", "create:millstone", "create:crushing_wheel_controller", "create:mechanical_crafter",
                "create:deployer", "create:mechanical_saw", "create:item_drain", "create:spout", "create:weighted_ejector",
                "create:portable_storage_interface", "create:stock_link", "create:stock_ticker", "create:packager",
                "create:repackager", "create:item_hatch", "create:package_frogport", "create:redstone_requester",
                "create:blaze_burner", "create:lit_blaze_burner", "create:clipboard",
                // Ars Nouveau
                "ars_nouveau:imbuement_chamber", "ars_nouveau:enchanting_apparatus", "ars_nouveau:arcane_pedestal",
                "ars_nouveau:scribes_table", "ars_nouveau:basic_spell_turret", "ars_nouveau:spell_turret",
                "ars_nouveau:timer_spell_turret", "ars_nouveau:rotating_spell_turret", "ars_nouveau:wixie_cauldron",
                "ars_nouveau:storage_lectern", "ars_nouveau:repository_controller", "ars_nouveau:bookwyrm_lectern",
                "ars_nouveau:item_detector", "ars_nouveau:volcanic_sourcelink", "ars_nouveau:mycelial_sourcelink",
                "ars_nouveau:alchemical_sourcelink", "ars_nouveau:potion_melder", "ars_nouveau:ritual_brazier",
                "ars_nouveau:scryers_crystal", "ars_nouveau:mob_jar",
                // Supplementaries: displays and devices, but its safes and sacks are storage
                "supplementaries:item_shelf", "supplementaries:pedestal", "supplementaries:notice_board",
                "supplementaries:jar", "supplementaries:jar_tinted", "supplementaries:soul_jar", "supplementaries:faucet",
                "supplementaries:hourglass", "supplementaries:book_pile", "supplementaries:book_pile_horizontal",
                "supplementaries:cannon", "supplementaries:crystal_display", "supplementaries:urn",
                "supplementaries:cage", "supplementaries:mob_jar",
                // Quark
                "quark:pipe", "quark:encased_pipe", "quark:feeding_trough", "quark:matrix_enchanter",
                // Lootr's per-player loot
                "lootr:lootr_chest", "lootr:lootr_trapped_chest", "lootr:lootr_barrel", "lootr:lootr_shulker",
                "lootr:lootr_inventory",
                // storage networks: their interfaces would hand the golem a whole network
                "ae2:interface", "ae2:pattern_provider", "ae2:drive", "ae2:chest", "ae2:io_port",
                "ae2:molecular_assembler", "ae2:inscriber", "ae2:charger", "ae2:cell_workbench", "ae2:condenser",
                "ae2:spatial_io_port", "ae2:vibration_chamber", "ae2:controller", "ae2:crafting_unit",
                "refinedstorage:interface", "refinedstorage:external_storage", "refinedstorage:disk_drive",
                "refinedstorage:importer", "refinedstorage:exporter", "refinedstorage:constructor",
                "refinedstorage:destructor", "refinedstorage:autocrafter", "refinedstorage:disk_interface",
                "toms_storage:ts.inventory_connector", "toms_storage:ts.inventory_proxy",
                "toms_storage:ts.storage_terminal", "toms_storage:ts.crafting_terminal", "toms_storage:ts.inventory_cable_connector",
                "toms_storage:ts.inventory_interface", "toms_storage:ts.level_emitter",
                "sophisticatedstorage:controller", "sophisticatedstorage:storage_link", "sophisticatedstorage:storage_io",
                "sophisticatedstorage:storage_input", "sophisticatedstorage:storage_output",
                "storagedrawers:controller", "storagedrawers:controller_io",
                "functionalstorage:storage_controller", "functionalstorage:controller_extension",
                "functionalstorage:framed_storage_controller", "functionalstorage:framed_controller_extension",
                "integrateddynamics:cable", "integratedtunnels:part_interface_item",
                // Mekanism: its machines, and the personal chests its security guards
                "mekanism:personal_chest", "mekanism:personal_barrel", "mekanism:metallurgic_infuser",
                "mekanism:enrichment_chamber", "mekanism:crusher", "mekanism:osmium_compressor", "mekanism:combiner",
                "mekanism:purification_chamber", "mekanism:energized_smelter", "mekanism:digital_miner",
                "mekanism:logistical_sorter", "mekanism:formulaic_assemblicator", "mekanism:precision_sawmill",
                "mekanism:chemical_injection_chamber", "mekanism:chemical_oxidizer", "mekanism:chemical_infuser",
                "mekanism:electrolytic_separator", "mekanism:rotary_condensentrator", "mekanism:pressurized_reaction_chamber",
                "mekanism:isotopic_centrifuge", "mekanism:antiprotonic_nucleosynthesizer", "mekanism:pigment_extractor",
                "mekanism:painting_machine", "mekanism:dimensional_stabilizer", "mekanism:qio_drive_array",
                "mekanism:qio_importer", "mekanism:qio_exporter", "mekanism:qio_dashboard", "mekanism:qio_redstone_adapter",
                "mekanism:basic_smelting_factory", "mekanism:elite_smelting_factory",
                // Thermal
                "thermal:machine_furnace", "thermal:machine_sawmill", "thermal:machine_pulverizer",
                "thermal:machine_smelter", "thermal:machine_insolator", "thermal:machine_centrifuge",
                "thermal:machine_press", "thermal:machine_crucible", "thermal:machine_chiller", "thermal:machine_refinery",
                "thermal:machine_pyrolyzer", "thermal:machine_bottler", "thermal:machine_brewer", "thermal:machine_crafter",
                "thermal:dynamo_stirling", "thermal:dynamo_compression", "thermal:dynamo_magmatic",
                "thermal:dynamo_numismatic", "thermal:dynamo_lapidary", "thermal:dynamo_disenchantment",
                "thermal:dynamo_gourmand", "thermal:device_tree_extractor", "thermal:device_fisher",
                "thermal:device_composter", "thermal:device_nullifier", "thermal:device_potion_diffuser",
                // Oritech
                "oritech:pulverizer_block", "oritech:grinder_block", "oritech:assembler_block", "oritech:foundry_block",
                "oritech:centrifuge_block", "oritech:atomic_forge_block", "oritech:powered_furnace_block",
                "oritech:fragment_forge_block", "oritech:cooler_block", "oritech:enchanter_block",
                "oritech:basic_generator_block", "oritech:bio_generator_block", "oritech:lava_generator_block",
                "oritech:fuel_generator_block", "oritech:steam_engine_block", "oritech:laser_arm_block",
                "oritech:deep_drill_block", "oritech:destroyer_block", "oritech:placer_block", "oritech:fertilizer_block",
                "oritech:item_pipe", "oritech:item_pipe_connection",
                // Immersive Engineering (its crates stay storage)
                "immersiveengineering:sorter", "immersiveengineering:item_batcher", "immersiveengineering:craftingtable",
                "immersiveengineering:conveyor_basic", "immersiveengineering:alloy_smelter",
                "immersiveengineering:coke_oven", "immersiveengineering:blast_furnace",
                "immersiveengineering:advanced_blast_furnace", "immersiveengineering:assembler",
                // Farmer's Delight
                "farmersdelight:cooking_pot", "farmersdelight:stove", "farmersdelight:skillet",
                "farmersdelight:cutting_board", "farmersdelight:basket",
                // Botania
                "botania:open_crate", "botania:runic_altar", "botania:apothecary_default", "botania:terra_plate",
                "botania:alchemy_catalyst"));
        // Create's postboxes and table cloths take things to send or sell
        for (String color : new String[]{"white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
                "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"}) {
            never.add("create:" + color + "_postbox");
            never.add("create:" + color + "_table_cloth");
            never.add("create:" + color + "_toolbox");
        }
        never.addAll(List.of("create:andesite_table_cloth", "create:brass_table_cloth", "create:copper_table_cloth"));
        tag("block", AlteredAether.id("golem_link_blacklist"), never.toArray(String[]::new));

        // Where tidy mode files items (see GolemPaths): the Aether goes by mod; vanilla's nether and end items are
        // named here, their raw materials mostly, since what is made from them follows its recipe back to them.
        tag("item", AlteredAether.id("golem_origin/nether"), "minecraft:netherrack", "minecraft:nether_brick",
                "minecraft:red_nether_bricks", "minecraft:nether_wart", "minecraft:nether_wart_block",
                "minecraft:warped_wart_block", "minecraft:crimson_nylium", "minecraft:warped_nylium",
                "#minecraft:crimson_stems", "#minecraft:warped_stems", "minecraft:crimson_fungus",
                "minecraft:warped_fungus", "minecraft:crimson_roots", "minecraft:warped_roots", "minecraft:nether_sprouts",
                "minecraft:weeping_vines", "minecraft:twisting_vines", "minecraft:shroomlight", "minecraft:soul_sand",
                "minecraft:soul_soil", "minecraft:basalt", "minecraft:blackstone", "minecraft:gilded_blackstone",
                "minecraft:quartz", "minecraft:nether_quartz_ore", "minecraft:nether_gold_ore", "minecraft:glowstone",
                "minecraft:glowstone_dust", "minecraft:magma_block", "minecraft:magma_cream", "minecraft:ancient_debris",
                "minecraft:netherite_scrap", "minecraft:netherite_ingot", "minecraft:blaze_rod", "minecraft:blaze_powder",
                "minecraft:ghast_tear", "minecraft:wither_skeleton_skull", "minecraft:nether_star", "minecraft:piglin_head");
        tag("item", AlteredAether.id("golem_origin/end"), "minecraft:end_stone", "minecraft:chorus_fruit",
                "minecraft:popped_chorus_fruit", "minecraft:chorus_flower", "minecraft:purpur_block",
                "minecraft:shulker_shell", "minecraft:elytra", "minecraft:dragon_breath", "minecraft:dragon_egg",
                "minecraft:dragon_head", "minecraft:end_rod", "minecraft:end_crystal");
        tag("item", AlteredAether.id("golem_origin/aether"));
        tag("item", AlteredAether.id("golem_origin/overworld"));
    }

    /**
     * What draws Tempest lightning onto a player (see TempestStorms): overworld-metal armour and tools, and standing on
     * copper without leather or gem boots. Aether gear (zanite, gravitite, the dungeon sets) is left out, so it is safe to wear in a storm.
     */
    private void lightning() {
        List<String> items = new ArrayList<>();
        for (String metal : new String[]{"iron", "golden", "chainmail", "netherite"}) {
            for (String piece : new String[]{"helmet", "chestplate", "leggings", "boots"}) {
                items.add("minecraft:" + metal + "_" + piece);
            }
        }
        for (String metal : new String[]{"iron", "golden", "netherite"}) {
            for (String tool : new String[]{"sword", "shovel", "pickaxe", "axe", "hoe"}) {
                items.add("minecraft:" + metal + "_" + tool);
            }
        }
        items.addAll(List.of("minecraft:shears", "minecraft:flint_and_steel", "minecraft:mace"));
        tag("item", AlteredAether.id("attracts_lightning"), items.toArray(String[]::new));
        // leather and gemstone soles keep a player off charged copper; any other boots still conduct
        tag("item", AlteredAether.id("insulating_boots"), "minecraft:leather_boots", "minecraft:diamond_boots",
                "aether:zanite_boots", "deep_aether:skyjade_boots");

        List<String> blocks = new ArrayList<>();
        for (String wax : new String[]{"", "waxed_"}) {
            for (String age : new String[]{"", "exposed_", "weathered_", "oxidized_"}) {
                String copper = age.isEmpty() ? "copper_block" : age + "copper";
                for (String block : new String[]{copper, age + "cut_copper", age + "cut_copper_stairs",
                        age + "cut_copper_slab", age + "chiseled_copper", age + "copper_grate", age + "copper_bulb"}) {
                    blocks.add("minecraft:" + wax + block);
                }
            }
        }
        tag("block", AlteredAether.id("charged_copper"), blocks.toArray(String[]::new));
    }

    /**
     * Gleam vines and gleamberries join every tag that names cave vines or glow berries outright, in vanilla, NeoForge
     * and the optional mods we know of (mineable/axe is shared with the campfire, above); tags that hold {@code #minecraft:cave_vines} (moss_replaceable, Create's) take
     * them in through it.
     */
    private void gleamVines() {
        String vines = "altered_aether:gleam_vines";
        String plant = "altered_aether:gleam_vines_plant";
        for (String tag : new String[]{"cave_vines", "climbable", "bee_growables", "sword_efficient"}) {
            tag("block", ResourceLocation.withDefaultNamespace(tag), vines, plant);
        }
        // Ars Nouveau lists only the head
        tag("block", ResourceLocation.fromNamespaceAndPath("ars_nouveau", "harvest/foliage"), vines);
        String berries = "altered_aether:gleamberries";
        tag("item", ResourceLocation.withDefaultNamespace("fox_food"), berries);
        tag("item", ResourceLocation.fromNamespaceAndPath("c", "foods/berry"), berries);
        tag("item", ResourceLocation.fromNamespaceAndPath("supplementaries", "flower_box_tall_plantable"), berries);
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

    /**
     * The stone sets (see data/AAStoneProvider): shapes in vanilla's shape tags, the natural stones wherever holystone
     * is as a stone, and everything counted as an Aether block, so non-Aether tools are slowed on it as on holystone.
     */
    private void stones() {
        for (String registry : new String[]{"block", "item"}) {
            for (String shape : new String[]{"stairs", "slabs", "walls"}) {
                tag(registry, ResourceLocation.withDefaultNamespace(shape),
                        AAStoneProvider.ids(shape.substring(0, shape.length() - (shape.equals("stairs") ? 0 : 1))));
            }
            tag(registry, ResourceLocation.fromNamespaceAndPath("c", "stones"), AAStoneProvider.naturalIds());
        }
        tag("item", ResourceLocation.withDefaultNamespace("stone_crafting_materials"), AAStoneProvider.naturalIds());
        tag("block", ResourceLocation.withDefaultNamespace("sculk_replaceable"), AAStoneProvider.naturalIds());
        tag("block", ResourceLocation.fromNamespaceAndPath("aether", "treated_as_aether_block"), AAStoneProvider.ids(null));
        // Nadirite lines the bases of the islands (see AASurfaceRuleProvider); the Aether's ores still spawn through it.
        tag("block", ResourceLocation.fromNamespaceAndPath("aether", "holystone_ore_replaceables"), "altered_aether:nadirite");
    }

    private static String[] concat(String[] first, String[] second) {
        String[] out = java.util.Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, out, first.length, second.length);
        return out;
    }

    /** Writes a tag. Other mods' entries are optional, and so is any marked with a leading {@code ?}. */
    private void tag(String registry, ResourceLocation id, String... entries) {
        JsonArray values = new JsonArray();
        for (String marked : entries) {
            boolean optional = marked.startsWith("?");
            String entry = optional ? marked.substring(1) : marked;
            String namespace = entry.replace("#", "").split(":")[0];
            if (REQUIRED.contains(namespace) && !optional) {
                values.add(entry);
            } else {
                JsonObject soft = new JsonObject();
                soft.addProperty("id", entry);
                soft.addProperty("required", false);
                values.add(soft);
            }
        }
        JsonObject json = new JsonObject();
        json.addProperty("replace", false);
        json.add("values", values);
        data("tags/" + registry, id, json);
    }
}
