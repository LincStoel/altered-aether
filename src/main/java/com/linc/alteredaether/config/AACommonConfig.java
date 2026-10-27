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
package com.linc.alteredaether.config;

import com.linc.alteredaether.compat.AddedMob;
import com.linc.alteredaether.entity.AerwhaleMode;
import com.linc.alteredaether.worldgen.AABiomes;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.ModConfigSpec.BooleanValue;
import net.neoforged.neoforge.common.ModConfigSpec.DoubleValue;
import net.neoforged.neoforge.common.ModConfigSpec.IntValue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Common config. It is read before biome modifiers apply and before worlds generate, so everything worldgen-facing
 * lives here. Values that shape terrain only affect chunks generated after a change.
 */
public final class AACommonConfig {
    public static final ModConfigSpec SPEC;

    // [region]
    public static final IntValue REGION_WEIGHT;
    public static final Map<String, BooleanValue> BIOME_ENABLED = new LinkedHashMap<>();

    // [terrain]
    public static final DoubleValue REGION_FALLOFF;
    public static final DoubleValue CRAG_STRENGTH;
    public static final DoubleValue TEMPEST_STRENGTH;
    public static final IntValue PLATEAU_TERRACE_HEIGHT;
    public static final DoubleValue RIFT_FRACTURE_SIZE;
    public static final DoubleValue RIFT_DRIFT;
    public static final DoubleValue DUNE_HEIGHT;
    public static final DoubleValue DUNE_WIND_DIRECTION;
    public static final DoubleValue ATOLL_SIZE;
    public static final DoubleValue LANDMARK_RARITY;
    public static final DoubleValue GIANT_SPACING;
    public static final DoubleValue GOLDEN_HEART_CHANCE;
    public static final DoubleValue DEEP_AETHER_WOOD_CHANCE;
    public static final DoubleValue CAMP_CHANCE;
    public static final DoubleValue SECRET_CHANCE;
    public static final DoubleValue TENDED_FRACTION;
    public static final DoubleValue UNDER_ROOT_CHANCE;
    public static final DoubleValue HANGING_ROOTS;
    public static final DoubleValue GLEAM_VINES;
    public static final DoubleValue MIRE_CHANCE;
    public static final DoubleValue UNDERSTORY;
    public static final DoubleValue MIRE_SIZE;
    public static final DoubleValue SPRING_STUMP_CHANCE;
    public static final DoubleValue RUIN_CHANCE;
    public static final DoubleValue STONE_HEAD_CHANCE;
    public static final DoubleValue VILLAGE_NITWIT_CHANCE;
    public static final DoubleValue VILLAGE_MIRE_CHANCE;
    public static final DoubleValue FLOODED_CRATER_CHANCE;
    public static final DoubleValue GROVE_BREAK_CHANCE;
    public static final DoubleValue GROVE_CHASM_SHARE;

    // [basins]
    public static final Map<String, BooleanValue> BASIN_ENABLED = new LinkedHashMap<>();
    public static final Map<String, DoubleValue> BASIN_RARITY = new LinkedHashMap<>();
    public static final DoubleValue WATERFALL_FREQUENCY;
    public static final DoubleValue CORAL_DENSITY;

    // [features]
    public static final Map<String, DoubleValue> FEATURE_DENSITY = new LinkedHashMap<>();
    public static final DoubleValue FAMILIAR_DENSITY;

    // [spawns]
    public static final Map<String, MobSettings> MOBS = new LinkedHashMap<>();

    // [climate]
    public static final BooleanValue CLIMATE_FIX;

    // [storms]
    public static final BooleanValue STORMS_ENABLED;
    public static final IntValue STORM_CHECK_INTERVAL;
    public static final DoubleValue STORM_STRIKE_CHANCE;
    public static final DoubleValue STORM_FIELD_SCALE;
    public static final DoubleValue STORM_DRIFT_SPEED;
    public static final IntValue STORM_MIN_RADIUS;
    public static final IntValue STORM_MAX_RADIUS;
    public static final DoubleValue STORM_DIRECT_HIT_CHANCE;
    public static final IntValue STORM_MAX_STRIKES_PER_TICK;
    public static final IntValue STORM_WARNING_TICKS;
    public static final DoubleValue STORM_EXPLOSION_POWER;
    public static final BooleanValue STORM_EXPLOSION_FIRE;
    public static final BooleanValue STORM_RESPECT_MOB_GRIEFING;
    public static final BooleanValue STORM_SCORCH;
    public static final BooleanValue STORM_LIGHTNING_RODS;
    public static final DoubleValue STORM_METAL_STRIKE_CHANCE;
    public static final BooleanValue STORM_COPPER_STRIKES;
    public static final IntValue STORM_PLAYER_WARNING_TICKS;

    // [aerwhales]
    public static final BooleanValue AERWHALE_USE_DEEP_AETHER;
    public static final DoubleValue AERWHALE_TAME_CHANCE;
    public static final IntValue AERWHALE_BREEDING_COOLDOWN;
    public static final IntValue AERWHALE_GROWTH_TIME;
    public static final IntValue AERWHALE_FOLLOW_TELEPORT;

    // [tall_mode]
    public static final ModConfigSpec.ConfigValue<List<? extends String>> TALL_MODE_EXCLUSIONS;

    // [compat]
    public static final Map<String, BooleanValue> COMPAT_ENABLED = new LinkedHashMap<>();
    public static final BooleanValue DISABLE_BEYOND_PARITY_CAMPFIRE;
    public static final BooleanValue DEEP_AETHER_FALLBACKS;
    public static final BooleanValue DISABLE_OVERWORLD_ARCHWOOD;

    public static final List<String> BASIN_TYPES =
            List.of("pool", "deep_lake", "cascading_lake", "frozen_pool", "poison_pool", "atoll_lagoon",
                    "frozen_lake");
    public static final List<String> FEATURE_FAMILIES =
            List.of("boulders", "copper_spikes", "aerclouds", "rift_debris", "totems", "ruins", "craters",
                    "dried_ghast_fields", "snow_drifts", "hanging_chains", "vegetation");
    public static final List<String> COMPAT_MODS =
            List.of("ars_nouveau", "ars_elemental", "deep_aether", "aether_overworld_ores", "create", "oreganized",
                    "dried_ghast", "snuffles", "mowziesmobs", "integrated_villages", "immersiveengineering");

    /** Per-mob spawn settings. */
    public record MobSettings(BooleanValue enabled, BooleanValue aetherExclusive, DoubleValue weightMultiplier,
                              IntValue minGroup, IntValue maxGroup, DoubleValue charge, DoubleValue energyBudget) {
    }

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.comment("Altered Aether's own TerraBlender region in the Aether.").push("region");
        REGION_WEIGHT = b.comment("Region weight. Deep Aether's main region defaults to 15, Aeroblender's default region to 10.")
                .defineInRange("weight", 12, 0, 1000);
        b.comment("Turn individual biomes off. A disabled biome's climate window falls back to the stock skyroot",
                        "biome it was cut from. Archwood groves also need Ars Nouveau (and the flashing grove Ars Elemental).")
                .push("biomes");
        for (AABiomes.Entry biome : AABiomes.ALL) {
            BIOME_ENABLED.put(biome.name(), b.define(biome.name(), true));
        }
        b.pop();
        b.pop();

        b.comment("Landform shaping. Only affects newly generated chunks.").push("terrain");
        REGION_FALLOFF = b.comment("Blocks over which our landforms fade out toward the edge of our region.")
                .defineInRange("region_falloff", 48.0, 8.0, 256.0);
        CRAG_STRENGTH = b.comment("Frozen Crags ridge sharpness.").defineInRange("crag_strength", 1.0, 0.0, 3.0);
        TEMPEST_STRENGTH = b.comment("Tempest Peaks spire sharpness and height.")
                .defineInRange("tempest_strength", 1.0, 0.0, 3.0);
        PLATEAU_TERRACE_HEIGHT = b.comment("Height of each terrace step on Aether Plateau mesas, in blocks. 0 disables",
                        "terracing.")
                .defineInRange("plateau_terrace_height", 8, 0, 32);
        RIFT_FRACTURE_SIZE = b.comment("Shattered Rift slab size multiplier.")
                .defineInRange("rift_fracture_size", 1.0, 0.25, 4.0);
        RIFT_DRIFT = b.comment("How far Shattered Rift slabs are displaced and tilted, as a multiplier.")
                .defineInRange("rift_drift", 1.0, 0.0, 3.0);
        DUNE_HEIGHT = b.comment("Windswept Dunes ridge height multiplier.").defineInRange("dune_height", 1.0, 0.0, 3.0);
        DUNE_WIND_DIRECTION = b.comment("Prevailing wind over the dunes, in degrees clockwise from north.")
                .defineInRange("dune_wind_direction", 60.0, 0.0, 360.0);
        ATOLL_SIZE = b.comment("Atoll and Frozen Lakes lake size multiplier.").defineInRange("atoll_size", 1.0, 0.5, 2.0);
        LANDMARK_RARITY = b.comment("Multiplier on how rare landmarks are. Higher is rarer, 0 disables them.")
                .defineInRange("landmark_rarity", 1.0, 0.0, 10.0);
        b.pop();

        b.comment("The giant trees of the Skyroot Cathedral and Nurselog Hollow. Only affects newly generated chunks.")
                .push("groves");
        GIANT_SPACING = b.comment("Spacing between giant trees, as a multiplier. Lower packs them closer.")
                .defineInRange("giant_spacing", 1.0, 0.6, 3.0);
        GOLDEN_HEART_CHANCE = b.comment("Chance a fallen colossus or colossal stump hides golden oak heartwood.")
                .defineInRange("golden_heart_chance", 0.08, 0.0, 1.0);
        DEEP_AETHER_WOOD_CHANCE = b.comment("With Deep Aether, the share of grove colonies grown from its woods",
                        "(roseroot and sunroot in the cathedral; cruderoot, yagroot, conberry and roseroot among the",
                        "fallen) instead of skyroot.")
                .defineInRange("deep_aether_wood_chance", 0.35, 0.0, 1.0);
        CAMP_CHANCE = b.comment("Chance a hollow, alcove or clearing holds a pilgrims' camp round an ambrosium campfire.")
                .defineInRange("camp_chance", 0.15, 0.0, 1.0);
        SECRET_CHANCE = b.comment("Chance a giant hides a chest in a secret spot: a sealed log section, a burl, a stump,",
                        "a nest at the top of a trunk.")
                .defineInRange("secret_chance", 0.10, 0.0, 1.0);
        TENDED_FRACTION = b.comment("Share of trails and camps still tended: lamps lit and campfires burning. The rest",
                        "are forgotten, their posts bare and their trails grown over.")
                .defineInRange("tended_fraction", 0.55, 0.0, 1.0);
        UNDER_ROOT_CHANCE = b.comment("Chance a giant sends a great root out through the underside of its island.")
                .defineInRange("under_root_chance", 0.5, 0.0, 1.0);
        HANGING_ROOTS = b.comment("Density of the roots hanging under the grove islands, as a multiplier. 0 disables them.")
                .defineInRange("hanging_roots", 1.0, 0.0, 4.0);
        GLEAM_VINES = b.comment("Density of the gleam vines hanging from the Skyroot Cathedral's canopy, as a multiplier.",
                        "0 disables them.")
                .defineInRange("gleam_vines", 1.0, 0.0, 4.0);
        MIRE_CHANCE = b.comment("Chance a living Skyroot Cathedral giant stands in a shallow swamp lake, on an islet of",
                        "its own. Neighbouring lakes may join. 0 disables them.")
                .defineInRange("mire_chance", 0.17, 0.0, 1.0);
        MIRE_SIZE = b.comment("Size of the swamp lakes round the giants, as a multiplier. The grid spacing caps it.")
                .defineInRange("mire_size", 1.0, 0.5, 1.5);
        SPRING_STUMP_CHANCE = b.comment("Chance a colossal stump in the Nurselog Hollow holds a spring: a brimming pool in",
                        "its hollow heart, streams down its flanks and a mire choked with roots round its foot.")
                .defineInRange("spring_stump_chance", 0.3, 0.0, 1.0);
        RUIN_CHANCE = b.comment("Chance each candidate spot in the groves holds a ruin: a cottage shell, a broken keep",
                        "or a ring of standing stones. Sites too steep or under a giant are skipped anyway.")
                .defineInRange("ruin_chance", 0.75, 0.0, 1.0);
        STONE_HEAD_CHANCE = b.comment("Chance each (very sparse) candidate spot on the Aether Plateau holds a group of one",
                        "to three great stone heads, about one spot in every sixteen-chunk cell.")
                .defineInRange("stone_head_chance", 0.5, 0.0, 1.0);
        VILLAGE_NITWIT_CHANCE = b.comment("Chance one nitwit still lives in a deserted village tree, the only one who",
                        "never left.")
                .defineInRange("village_nitwit_chance", 0.3, 0.0, 1.0);
        VILLAGE_MIRE_CHANCE = b.comment("Chance a deserted village tree stands in a broad swamp lake, the ruins round it on",
                        "islets of their own. Only where the ground will hold one.")
                .defineInRange("village_mire_chance", 0.5, 0.0, 1.0);
        FLOODED_CRATER_CHANCE = b.comment("Chance the crater a fallen colossus tore out with its roots has filled with water",
                        "and mud.")
                .defineInRange("flooded_crater_chance", 0.4, 0.0, 1.0);
        GROVE_BREAK_CHANCE = b.comment("Chance each 320-block cell tries for a break in a Nurselog Hollow island, where a",
                        "piece has cracked away and drifted, a fallen giant across the gap. Only suitable spots are used.")
                .defineInRange("grove_break_chance", 0.6, 0.0, 1.0);
        GROVE_CHASM_SHARE = b.comment("Share of those breaks that are a crack right across an island rather than a bite",
                        "out of its edge. Few spots suit one.")
                .defineInRange("grove_chasm_share", 0.15, 0.0, 1.0);
        UNDERSTORY = b.comment("Density of the young skyroots and ground cover between the giants, as a multiplier.",
                        "0 leaves the ground between them bare.")
                .defineInRange("understory", 1.0, 0.0, 2.0);
        b.pop();

        b.comment("Lakes, pools, lagoons and waterfalls.").push("basins");
        for (String type : BASIN_TYPES) {
            b.push(type);
            BASIN_ENABLED.put(type, b.define("enabled", true));
            BASIN_RARITY.put(type, b.comment("Rarity multiplier. Higher is rarer.")
                    .defineInRange("rarity", 1.0, 0.1, 10.0));
            b.pop();
        }
        WATERFALL_FREQUENCY = b.comment("Waterfall frequency multiplier. 0 disables waterfalls.")
                .defineInRange("waterfall_frequency", 1.0, 0.0, 10.0);
        CORAL_DENSITY = b.comment("Atoll lagoon reef density multiplier.").defineInRange("coral_density", 1.0, 0.0, 4.0);
        b.pop();

        b.comment("Density multipliers for decoration families. 0 disables a family, 2 doubles it.").push("features");
        for (String family : FEATURE_FAMILIES) {
            FEATURE_DENSITY.put(family, b.defineInRange(family, 1.0, 0.0, 10.0));
        }
        FAMILIAR_DENSITY = b.comment("Population multiplier for mobs placed at world generation (familiars, fire minions, sentries).")
                .defineInRange("familiar_density", 1.0, 0.0, 10.0);
        b.pop();

        b.comment("Mobs from other mods (and vanilla) brought into the Aether. Entries for mods that aren't loaded are ignored.",
                        "aether_exclusive removes that mob's natural spawns from every biome outside #aether:is_aether.",
                        "It does not touch spawners, summons or rituals. Ars familiars also get a population placed at world",
                        "generation; features.familiar_density scales that, the settings here scale their natural spawns.")
                .push("spawns");
        for (AddedMob mob : AddedMob.ALL) {
            b.comment(mob.entityId()).push(mob.key());
            MOBS.put(mob.key(), new MobSettings(
                    b.define("enabled", true),
                    b.define("aether_exclusive", mob.exclusiveDefault()),
                    b.comment("Multiplier on this mob's spawn weight in each biome it is added to.")
                            .defineInRange("weight_multiplier", 1.0, 0.0, 100.0),
                    b.comment("Group size override, -1 keeps the per-biome default.").defineInRange("min_group", -1, -1, 16),
                    b.comment("Group size override, -1 keeps the per-biome default.").defineInRange("max_group", -1, -1, 16),
                    b.comment("Spawn cost charge (crowding this mob creates). 0 removes its spawn cost.")
                            .defineInRange("charge", mob.charge(), 0.0, 10.0),
                    b.comment("Spawn cost energy budget (crowding it tolerates). Lower is rarer.")
                            .defineInRange("energy_budget", mob.energyBudget(), 0.0, 10.0)));
            b.pop();
        }
        b.pop();

        b.push("climate");
        CLIMATE_FIX = b.comment("Give the stock Aether and Deep Aether biomes real temperature and downfall values instead",
                        "of the shared 0.8/0.0 placeholder. Matters to Cold Sweat, crop-growth mods and anything else that",
                        "reads biome climate.")
                .define("climate_fix", true);
        b.pop();

        b.comment("Tempest Peaks lightning storms.").push("storms");
        STORMS_ENABLED = b.define("enabled", true);
        STORM_CHECK_INTERVAL = b.comment("Ticks between strike rolls for each player in a storm biome.")
                .defineInRange("check_interval", 20, 1, 1200);
        STORM_STRIKE_CHANCE = b.comment("Strike chance per roll at full storm intensity.")
                .defineInRange("strike_chance", 0.3, 0.0, 1.0);
        STORM_FIELD_SCALE = b.comment("Size of storm cells in blocks. Intensity varies across this scale.")
                .defineInRange("field_scale", 160.0, 16.0, 4096.0);
        STORM_DRIFT_SPEED = b.comment("How fast storm cells drift, in blocks per second.")
                .defineInRange("drift_speed", 0.6, 0.0, 20.0);
        STORM_MIN_RADIUS = b.defineInRange("min_radius", 6, 0, 64);
        STORM_MAX_RADIUS = b.defineInRange("max_radius", 18, 1, 64);
        STORM_DIRECT_HIT_CHANCE = b.comment("Fraction of strikes aimed straight at the player.")
                .defineInRange("direct_hit_chance", 1.0 / 30.0, 0.0, 1.0);
        STORM_MAX_STRIKES_PER_TICK = b.defineInRange("max_strikes_per_tick", 3, 1, 32);
        STORM_WARNING_TICKS = b.comment("Ticks of sparks and rumbling before a strike lands. 0 strikes without warning.")
                .defineInRange("warning_ticks", 30, 0, 200);
        STORM_EXPLOSION_POWER = b.comment("Explosion power at each strike (TNT is 4.0). 0 disables explosions.")
                .defineInRange("explosion_power", 3.5, 0.0, 10.0);
        STORM_EXPLOSION_FIRE = b.define("explosion_fire", false);
        STORM_RESPECT_MOB_GRIEFING = b.comment("When the mobGriefing gamerule is off, explosions don't break blocks.")
                .define("respect_mob_griefing", true);
        STORM_SCORCH = b.comment("Leave gravel and blackstone scorch marks where lightning lands.").define("scorch", true);
        STORM_LIGHTNING_RODS = b.comment("Lightning rods near a strike attract it, as they do in vanilla.")
                .define("lightning_rods", true);
        STORM_METAL_STRIKE_CHANCE = b.comment("Chance per roll that a player wearing overworld-metal armour or carrying an",
                        "overworld-metal tool in the hotbar (#altered_aether:attracts_lightning) is struck, storm or calm.",
                        "Aether gear never draws lightning. 0 disables.")
                .defineInRange("metal_strike_chance", 0.15, 0.0, 1.0);
        STORM_COPPER_STRIKES = b.comment("Standing on copper (#altered_aether:charged_copper) in a storm biome without boots",
                        "calls down a strike.")
                .define("copper_strikes", true);
        STORM_PLAYER_WARNING_TICKS = b.comment("Ticks between the warning crackle and a strike aimed at a player for their metal",
                        "or for standing on copper.")
                .defineInRange("player_warning_ticks", 20, 0, 200);
        b.pop();

        b.comment("Tamed aerwhales: tame a wild one with blue berries, saddle it and fly it. Sneak and use your own",
                        "aerwhale to tell it to stay, wander or follow; open its saddle and saddlebag with the inventory key",
                        "while riding it.")
                .push("aerwhales");
        AERWHALE_USE_DEEP_AETHER = b.comment("With Deep Aether loaded, use its aerwhales (saddle, chest, two seats) instead of",
                        "ours: taming is off and tamed aerwhales turn back into wild ones, keeping any saddle. When false,",
                        "Deep Aether's aerwhale changes are switched off entirely and any saddles and chest contents its",
                        "aerwhales carried drop where they are. Takes effect after a restart; a server and its clients must",
                        "use the same setting, or clients can't join.")
                .gameRestart()
                .define(AerwhaleMode.CONFIG_KEY, false);
        AERWHALE_TAME_CHANCE = b.comment("Chance that each blue berry tames a wild aerwhale. 0 disables taming.")
                .defineInRange("tame_chance", 1.0 / 3.0, 0.0, 1.0);
        AERWHALE_BREEDING_COOLDOWN = b.comment("Ticks before a parent can breed again.")
                .defineInRange("breeding_cooldown", 6000, 0, 240000);
        AERWHALE_GROWTH_TIME = b.comment("Ticks a calf takes to grow up. Each enchanted berry fed to it takes a tenth off.")
                .defineInRange("growth_time", 24000, 1, 480000);
        AERWHALE_FOLLOW_TELEPORT = b.comment("A following aerwhale this far from its owner, in blocks, catches up by",
                        "teleporting to them, as wolves and cats do. 0 never teleports.")
                .defineInRange("follow_teleport_distance", 24, 0, 512);
        b.pop();

        b.comment("The taller Aether (-64..319) comes from the built-in 'altered_aether:tall_aether' datapack, chosen when",
                        "creating a world. These settings only apply when it is enabled.")
                .push("tall_mode");
        TALL_MODE_EXCLUSIONS = b.comment("Placed feature and structure ids whose heights must not be rescaled.")
                .defineListAllowEmpty("exclusions", List.of("deep_aether:brass_dungeon"), () -> "", o -> o instanceof String);
        b.pop();

        b.comment("Optional mod integrations. Each only takes effect when that mod is loaded.").push("compat");
        for (String mod : COMPAT_MODS) {
            COMPAT_ENABLED.put(mod, b.define(mod, true));
        }
        DISABLE_BEYOND_PARITY_CAMPFIRE = b.comment("Hide Aether: Beyond Parity's ambrosium campfire and make it uncraftable,",
                        "in favor of Altered Aether's own. A recipe converts theirs into ours.")
                .define("disable_beyond_parity_campfire", true);
        DEEP_AETHER_FALLBACKS = b.comment("Without Deep Aether, swap its blocks for the closest Aether or vanilla block. When",
                        "false, decorations that need Deep Aether blocks are left out instead.")
                .define("deep_aether_fallbacks", true);
        DISABLE_OVERWORLD_ARCHWOOD = b.comment("With Ars Nouveau loaded, keep archwood in the Aether: no archwood trees in the",
                        "overworld, and none of Ars Nouveau's or Ars Elemental's archwood forests. Trees take effect on the",
                        "next world load, forests after a restart; only newly generated chunks change.")
                .gameRestart()
                .define("disable_overworld_archwood", true);
        b.pop();

        SPEC = b.build();
    }

    public static MobSettings mob(String key) {
        return MOBS.get(key);
    }

    public static boolean compat(String modId) {
        BooleanValue value = COMPAT_ENABLED.get(modId);
        return value == null || value.get();
    }

    private AACommonConfig() {
    }
}
