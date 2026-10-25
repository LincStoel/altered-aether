# Altered Aether

A NeoForge addon for [The Aether](https://www.curseforge.com/minecraft/mc-mods/aether) that reshapes the Aether's
worldgen, built to inspire awe and make the Aether worth exploring. It adds a region of new biomes with their own
landforms, lakes and waterfalls, rare landmarks you find by exploring, lightning storms over the peaks, and a
taller Aether.

![The frozen spire, under BSL shaders](docs/screenshots/frozen_spire_0.png)

## Requirements

- Minecraft 1.21.1
- NeoForge 21.1.264 or later
- The Aether 1.5.10 or later
- Aeroblender 1.0.0 or later (and TerraBlender, which it needs)
- Install on both client and server

Every other mod listed below is optional: the content that uses one only loads when that mod is installed (and its
switch in the config's `[compat]` section is on).

## What it adds

**Biomes**, in Altered Aether's own TerraBlender region, so Deep Aether and other Aether worldgen mods keep theirs:

| Biome | Terrain |
|---|---|
| Aether Plateau | stepped mesas with lakes, pools and the odd waterfall off a cliff |
| Frozen Crags | floating mountain massifs along ridgelines, tarns, snow drifts, ice spikes |
| Tempest Peaks | the crags' sharpest summits: needle spires, copper spires, strike craters, storms |
| Shattered Rift | islands broken into drifting, tilted slabs with cracks between them |
| Windswept Dunes | wind ridges with steep lee faces, ruins, dead bushes |
| Sunken Atoll | ring islands round lagoons with coral, kelp, tropical fish, turtles and glow squid |
| Blazing, Cascading, Flourishing and Vexing Archwood Groves | Ars Nouveau's archwood forests (need Ars Nouveau) |
| Flashing Archwood Grove | needs Ars Nouveau and Ars Elemental |

Without Ars Nouveau the grove windows fall back to the Aether's own skyroot biomes. Landforms appear only inside
the region, and blend out at its edges.

**Rare landmarks**, each one-in-many:

- **Tempest crown**: a cluster of storm-black needles rising far above Tempest Peaks, crowned with lightning rods
  and ringed with copper spires. The storms strike it first.
- **Frozen spire**: one colossal twisted spire of ice in the crags, ringed with snowy ledges.
- **Grand cascade**: a staircase of pools stepping down to an island's edge, each spilling into the next, the last
  pouring off into the sky.
- **Great fracture**: a canyon across the rift, its slabs grown into giant plates, one side heaved up over the other.
- **Double atoll**: an atoll with a second ring in its lagoon and a golden oak on an islet at its heart.

`/altered_aether landmark <type>` finds the nearest one (operators only).

**Tempest storms**: storm cells drift over Tempest Peaks and strike the high ground near players, with a warning
of sparks and thunder first. Lightning rods take strikes as in vanilla. Explosions and scorch marks respect
`mobGriefing`. Clients see flashes in the distance, rain, sparks and storm fog.

**Ambrosium campfire**: a campfire of skyroot and ambrosium that burns in the Aether. Aether: Beyond Parity's
campfire is replaced by it when that mod is installed.

## The tall Aether

The built-in datapack **Altered Aether: Tall Aether** (on by default) makes the Aether -64..319 like the Overworld,
with deeper islands and room for the tallest landmarks. Turn it off in the Data Packs screen when creating a world
for the stock 0..255 Aether. The height can't change once a world's Aether has been generated.

With the tall pack, other mods' Aether structures and ores are moved to the matching heights automatically.

## Optional mods

| Mod | What it brings |
|---|---|
| Ars Nouveau | the archwood groves, scattered archwood, familiars and wilden in the groves |
| Ars Elemental | the Flashing Archwood Grove, flashjacks, sirens, sourcestone boulders |
| Deep Aether | its stones, blocks and ground cover in our biomes (closest Aether or vanilla blocks stand in without it); totems, carved squash, tiki torches; the Sacred Lands biome is placed (Deep Aether never places it itself); poison pools |
| Aether Overworld Ores | its ores placed by biome; 42 enchanting recipes |
| Create | zinc in Tempest Peaks |
| Oreganized | silver in the crags and the rift |
| Dried Ghast | dried ghast fields in the Windswept Dunes |
| Snuffles | Snuffles in the Frozen Crags, and only in the Aether |
| Mowzie's Mobs | nagas in the Aether |
| Integrated Villages | its airship village in the Aether |
| Aether: Beyond Parity | its ambrosium campfire is swapped for ours |

Mobs added to the Aether from other mods spawn only in the Aether by default (each has an `aether_exclusive`
switch). Breezes are the exception: they spawn in the Aether as well as anywhere vanilla puts them, and only standing
on cold aerclouds.

## Configuration

`config/altered_aether-common.toml`. Every value is commented with its default and range.

- `[region]`: the region's weight, each biome on or off, and the Sacred Lands fix.
- `[terrain]`: landform strengths, rift fracture size and drift, dune height and wind, atoll size, and
  `landmark_rarity` (higher is rarer, 0 turns landmarks off).
- `[basins]`: each kind of lake on or off and its rarity, waterfall frequency, coral density.
- `[features]`: a density multiplier per decoration family.
- `[spawns]`: per added mob: on or off, `aether_exclusive`, weight, group size and spawn cost.
- `[climate]`: the biome temperature fix.
- `[storms]`: everything about the Tempest storms.
- `[tall_mode]`: structures the tall Aether should leave at their own heights.
- `[compat]`: one switch per optional mod, and the Beyond Parity campfire swap.

`config/altered_aether-client.toml`: storm fog, sky flashes, local rain, particle density, and how the effects adapt
to shaderpacks.

## Shaders

The atmospheric effects are made to work under Iris shaderpacks, and are tested with BSL v10.1.1 (Iris 1.8.14,
Sodium 0.8.13): the flashes are real lightning to the shaderpack, and the storm's rain and thunder levels drive its
wetness and darkening. Storm fog is left to the shaderpack when one is active. See
[docs/shaders.md](docs/shaders.md).

## Performance

Measured with the dev server's benchmark: fresh worlds with the same seed, 225 chunks around each of two points,
twice each. The baseline is the same mod with its worldgen switched off in the config (region weight 0, no
landmarks, no basins), so the terrain is the stock Aether's.

| Area | Stock terrain | Altered Aether | Cost |
|---|---|---|---|
| Around 0, 0 (mixed biomes) | 32.3 ms per chunk | 33.6 ms | +4% |
| Around 3000, -2000 (mostly Aether Plateau) | 29.6 ms | 38.8 ms | +31% |

Most of the extra time over the plateau goes on the extra terrain its mesas put where stock islands leave sky:
more blocks to surface, light and decorate. Overall the difference is about 17%.

## Development

The Aether and the mods it needs are not committed. Copy these from the CurseForge instance's `mods` folder into
`libs/`:

- `aether-1.21.1-1.5.10-neoforge.jar`
- `aeroblender-1.21.1-1.0.0-neoforge.jar`
- `TerraBlender-neoforge-1.21.1-4.1.0.8.jar`
- `accessories-neoforge-1.1.0-beta.54+1.21.1.jar`
- `owo-lib-neoforge-0.12.15.6-beta.1+1.21.jar`

Optional mods to test with go in `libs/optional/` and are copied into the runs; `-PoptionalMods=none` (or a
comma-separated list of jar name prefixes) picks which.

- `.\gradlew runClient`, `.\gradlew runServer`, `.\gradlew build`.
- `.\gradlew runData` regenerates `src/generated/resources` (worldgen, tags, models, lang).
- `.\gradlew test` runs the unit tests, including the biome table's partition across every combination of mods.

Worldgen tools, all on the dev server:

- `-PaaBench=radius[,biome|x:z|landmark:<type>]` generates that many chunks around a point, logs the time per chunk,
  surface, decoration, basin and landmark tallies, then stops. `-PaaRender=true` also writes top and side views of
  the area to `run-server/renders/`.
- `-PaaReport=radius,step` prints a report of biomes, masks and columns.
- `/altered_aether debug` reports the biome, climate point, region mask, basin, landmark and storm at your position.
- `-PaaTour=stop,stop,...` on the client quick-plays `run/saves/tour` and screenshots each landmark (and `storm`,
  `campfire`) into `run/screenshots/tour/`.
- `python tools/parity/parity.py` compares the biomes with the World Traveler 2 datapack they replace.

## License

Altered Aether is free software under the GNU General Public License, version 3 or (at your option) any later
version. See [LICENSE](LICENSE).
