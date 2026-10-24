# Altered Aether mod: capture the current state

## Context
Aether world gen is currently split across a code-generated datapack (OpenLoader `wt2_overrides`), a few KubeJS scripts, and Python generators in `dev/aether-rescale/`. The goal is a single mod, namespace `altered_aether`, that owns all of it. Per the user: **no Java specification yet** (build system, classes, datagen layout come later). This plan only records what exists and where it moves. Eventual approach, already chosen: Java datagen (bootstrap emits JSON into the jar's `data/`), mirroring how `explorerstools` and `crop_climates` ship (`com.worldtraveler.*`, data inside the jar).

Paths below are relative to the instance root `WT2/` = `C:\Users\linco\curseforge\minecraft\Instances\World Traveler 2`.
`PACK/` = `WT2/config/openloader/packs/wt2_overrides/data`.
New mod resources root: `altered_aether/src/main/resources` (project location TBD).

## Namespace rule
- Content the pack **adds** (`PACK/world_traveler/**` Aether items below) → `data/altered_aether/**`. Ids change `world_traveler:x` → `altered_aether:x`.
- Content that **overrides another mod** (`PACK/aether`, `deep_aether`, `aether_overworld_ores`, `aether_villages`, `farmers_structures`, `integrated_villages`, `aeroblender`, `minecraft`, `ars_*`) must keep its original namespace path to override it; it just moves into the new mod's `data/`.
- **Risk to decide before migrating:** renaming biome ids orphans biomes in existing saves. Either keep `world_traveler:` ids for the 10 biomes or accept a new world.

## 1. Datapack → new mod (the bulk)

### 1a. Added content (`PACK/world_traveler/` → `data/altered_aether/`)
| Source | Count | Notes |
|---|---|---|
| `worldgen/biome/*.json` | 10 | aether_plateau, windswept_dunes, tempest_peaks, frozen_crags, shattered_rift, {blazing,cascading,flashing,flourishing,vexing}_archwood_grove. Contain spawners, spawn_costs, feature lists |
| `worldgen/density_function/aether/*.json` | 16 | continentalness, crag_*, dune_*, mesa_mask, plateau(_mask), rift_* |
| `worldgen/noise/aether_*.json` | 12 | continents, crag_jag, crags, dune_lean, mesa, mesa_height, path_region, plateau_path, plateau_quicksoil_vein, plateau_relief, rift, rift_carve (`temperature_smooth.json` is overworld, exclude) |
| `worldgen/configured_feature/` | ~57 | ores, boulders, copper spikes, totems, squash, weald walkers, aercloud, familiars |
| `worldgen/placed_feature/` | ~70 | matching placed features (ore counts/heights listed in prior answer) |
| `neoforge/biome_modifier/*.json` | 12 | dune_holystone_iron_vein, frozen_crags_silver_ore, lapis_boost_cascading_archwood_grove, large_aercloud, naga_spawn_cost, scattered_*_archwood (5), starbuncle_wild, tempest_deepslate_copper_ore |
| `tags/block/frozen_crag_ore_replaceables.json`, `tempest_peak_ore_replaceables.json` | 2 | |

Exclude (overworld, not Aether): `worldgen/world_preset`, `worldgen/noise_settings/world_traveler.json`, `density_function/{centroid,continents,overworld}`, `noise/temperature_smooth.json`, `tags/worldgen/biome/none.json`, and the loot_modifiers/loot_table trees (see 3).

### 1b. Overrides of Aether mods (keep namespace)
- `PACK/aether/dimension/the_aether.json`, `dimension_type/the_aether.json` (height -64..319)
- `PACK/aether/worldgen/noise_settings/skylands.json`, `density_function/base_3d_noise_aether.json`
- `PACK/aether/worldgen/biome/` skyroot_{forest,grove,meadow,woodland}
- `PACK/aether/worldgen/structure/` bronze_dungeon, silver_dungeon, gold_dungeon, large_aercloud
- `PACK/aether/worldgen/placed_feature/` (11), `configured_feature/quicksoil_shelf.json`
- `PACK/aether/tags/block/` aechor_plant_spawnable_on, aether_animals_spawnable_on, holystone_ore_replaceables, swet_spawnable_on
- `PACK/aether/tags/worldgen/biome/is_aether.json` (lists the new biomes; ids update with namespace decision)
- `PACK/deep_aether/worldgen/biome/` (11), `structure/` (altar_camp, campfire, combiner_corridor), `placed_feature/` (6), `configured_feature/` (1)
- `PACK/aether_overworld_ores/worldgen/configured_feature/` holystone_ore_{copper,gold,lapis,redstone,zinc}.json
- `PACK/aether_overworld_ores/tags/worldgen/biome/has_holystone_*_ore.json` (15; these are what assign holystone ores to biomes, e.g. copper → tempest_peaks, zinc → frozen_crags + aether_plateau, gold → golden_heights/golden_grove/blazing grove, lapis → frozen_crags/cascading grove/blue_aerglow_forest; iron and coal are empty)
- `PACK/aether_overworld_ores/recipe/` (42 files): recipes, not world gen; decide whether the mod owns them
- `PACK/aether_villages/worldgen/structure/olympic_citadel.json`
- `PACK/farmers_structures/worldgen/structure/aether.json`
- `PACK/integrated_villages/worldgen/structure/airship_village_aether.json`, `structure_set/air_villages.json`, `air_villages_aether.json`
- `PACK/aeroblender/worldgen/density_function/depth.json`
- `PACK/minecraft/worldgen/structure_set/ruined_portals.json` (confirm it is Aether-related before moving)

### 1c. Familiar spawns in the Aether
- `PACK/ars_elemental/neoforge/biome_modifier/` flashing_archwood, flashjack_aether_spawn, flashjack_remove_flashing_forest, flashjack_remove_overworld
- `PACK/ars_nouveau/neoforge/biome_modifier/` alakarkinos_spawn, drygmy_overworld_rarity, drygmy_spawn, rare_archwood_mix, starbuncle_overworld_rarity, starbuncle_spawn
- Some of these are overworld-only; split Aether vs overworld during migration.

## 2. KubeJS → new mod
- `WT2/kubejs/server_scripts/tempest_peaks_lightning.js` → Java event handler (once-a-second strike roll for players in `altered_aether:tempest_peaks`, dimension `aether:the_aether`).
- `WT2/kubejs/assets/world_traveler/lang/en_us.json`: the 8 `biome.world_traveler.*` / `biome.deep_aether.sacred_lands` keys → `assets/altered_aether/lang/en_us.json`.
- `WT2/kubejs/assets/world_traveler/tips/dimensions.json` mentions the Aether; leave in KubeJS (generic tip).
- Not world gen, leave alone: `block_tags.js`, `item_tags.js`, `removals.js`, `misc_recipes.js`, `aeronautics.js`, `client_scripts/recipe_viewer_hide.js`.

## 3. Aether loot (decide: in scope or not)
These touch Aether chests, not generation. Suggest leaving in the datapack for now:
- `PACK/world_traveler/loot_modifiers/unique_weapons/unique_*aether*` (~9), `hammer_iron/aether_*` (3), `hammer_gold/luistercorp_chests_ruined_aether_portal*` (2)
- `PACK/world_traveler/loot_table/injects/unique_gate/*aether*`, `injects/unique_pool/aether.json`

## 4. Generators (source of truth today → input for later Java datagen)
`WT2/dev/aether-rescale/`: `build_biome_table.py` (322 lines), `build_biomes.py` (462), `build_features.py` (849), `build_skylands.py` (507), `build_tempest_structures.py` (280), `remap.py` (291), `noise_stats.py` (274), `validate.py` (162), `verify_biome_partition.py` (85). These are the spec for the Java datagen; keep until parity is proven.

## 5. External mods the Aether setup depends on (config, not moved)
TerraBlender (`config/terrablender.toml`, region weight), Lithostitched, Structurify (`config/structurify.json`, `config/structurify/`), StreamsReflowing (`bank_template` features), Aether/Deep Aether configs under `config/`. Declare as dependencies; their configs stay in `config/`.

# Hard Dependencies

- The Aether
- Aeroblender

# Comments

- The current implementation is limited in how it was implemented. it is more important to have a polished finished product that honors the original intent, but there is room for improvement. As you develop this mod always look for better more polished ways that you could implement these features.