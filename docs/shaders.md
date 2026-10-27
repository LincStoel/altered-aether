# Shaders: Iris and BSL checklist

Tested on 2026-10-24 with Iris 1.8.14 (beta 1), Sodium 0.8.13 and BSL v10.1.1 on its default (LOW) profile, NeoForge
21.1.264, RTX 4080. The test used the debug camera tour (`-PaaTour`, see the README). It flies to each landmark and
to a forced storm, and builds a campfire stage at night, then saves screenshots. The same tour was run without
shaders for comparison.

## How the effects are built

Shaderpacks draw their own sky, fog and lighting, so nothing important depends on vanilla fog or sky tints.

1. **Works under any shaderpack.**
   - Distant flashes are real lightning bolts that exist only on the client. Each brings vanilla's sky flash and
     thunder, and Iris passes it to the shaderpack as lightning.
   - The storm sets the client's rain and thunder levels, which shaderpacks read for wetness and darkening.
   - Spark particles, and biome particles defined in the biome data: sparks in Tempest Peaks, snowflakes in the
     Frozen Crags, dust in the Windswept Dunes.
   - The groves' lamps are blocks with their own light (ambrosium torches, and with Deep Aether skyjade lanterns and
     tiki torches), which shaderpacks light as usual.
2. **Without a shaderpack only.** Storm fog draws the fog closer and greys it. When Iris reports an active
   shaderpack (`client.shader_mode = AUTO`), the fog is skipped and flashes and sparks are turned up 40%.

## Results

| Check | Result |
|---|---|
| How BSL treats the Aether (no `dimension.properties` entry, so the overworld program) | Fine: sky and fog in our biomes look right; daylight scenes are clear, blue and hazy with BSL's volumetric clouds |
| Storm darkening and wetness | Works: under a forced storm at dusk, BSL's sky closes in dark and amber over the Tempest crown |
| Storm rain | Works without shaders (dense rain in Tempest Peaks). Under BSL, rain is subtle in a still image |
| Distant flashes | **Not confirmed visually**: a flash lasts a few frames and no still caught one. Thunder and sky flash use vanilla's own code path |
| Spark particles, crag snowflakes, dune dust | Not singled out in these stills |
| Grove lamps | **Not checked yet** |
| Atoll water colour and clarity, coral | Good: the reef shows through BSL water |
| Waterfalls | Rendered; no artifacts seen, but not framed closely |
| Ambrosium campfire | Good: golden flame, smoke, and it lights its surroundings under BSL. The flame needed a cutout render type, which is fixed |
| Aerclouds and landmarks at distance | Good: the frozen spire reads clearly far off through BSL's haze |
| Errors | None from our code. Iris logged only BSL's own option warnings (`FOG_DISTANCE_LOD`, `LOD_TAPER_WAVES`) |

## Still to check by hand

- Watch a storm in Tempest Peaks for a minute with BSL on. Check that flashes light the clouds and the thunder
  follows.
- Stand in the Frozen Crags and the Windswept Dunes to see their particles under BSL.
- Walk a lit trail in the Skyroot Cathedral with BSL on: the lamps should light the ground round them.
