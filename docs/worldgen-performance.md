# Worldgen performance: rules for new terrain and features

These rules come out of the 2026-11-18 audit. In one week of new features, Aether generation became four times slower
than 1.3.0. Flying into new land then showed nothing for 40-50 seconds, then holes and multi-second server freezes.
Every cause was ordinary-looking code in the wrong place. Commits ec67a0e..ddb8720 fixed it, and nearly every rule
below links to the fix that taught it.

## 1. Know which steps are serial

Minecraft 1.21 generates a chunk in steps.

| Step | Runs | So |
|---|---|---|
| Structure starts | **One chunk at a time** (the `worldgen` mailbox) | Every cost here is wall time |
| Biomes, noise (terrain) | On all worker threads | Cheap in wall time, unless it gets out of hand |
| Surface, carvers | **One chunk at a time** | Every cost here is wall time |
| Features | **One chunk at a time** | Every cost here is wall time |
| Light | Its own thread | – |

When exploring, 23 worker threads sat idle while one ran structure starts and features. A millisecond added to a
feature costs about a millisecond per chunk of wall time; a millisecond added to the noise costs about a twentieth of
that.

**No chunk can be shown until structure starts have run for an area about 8 chunks wider than the view.** That
serial backlog is the blank a player sees on arriving somewhere new.

Two traps follow from this.
- **Structure starts evaluate our terrain.** The Aether's dungeons and large aerclouds place themselves with
  `getBaseColumn`/`getBaseHeight`. Each call runs the whole router for a full column, so every density function and
  every Field it reaches costs serial time. The bronze dungeon alone is about a fifth of all worldgen CPU.
- **Fields fill lazily, on first touch**, which usually happens in structure starts. A site search that looks harmless
  "because it's cached" runs its first time on the serial path, for every cell the dungeon probes touch.

## 2. Density functions

1. **A custom function that wraps an `interpolated` subtree must be a `TwoArgumentSimpleFunction` with a leaf.**
   Present it as `min(argument, leaf)` or `max(argument, leaf)`, with the leaf's `mapAll` returning
   `visitor.apply(this)`. See `BasinCarve.Cut` and `PlateauStacks.Rise`. An opaque wrapper makes ByePregen fall back
   to the slow path for every noise chunk and log a stack trace each time (ec67a0e: 17,769 per benchmark). Keep the
   wrapper's own `compute()` early-outs: that is the path without ByePregen.
2. **Everything under `flat_cache` or `cache_2d` must depend on x and z only.** TerrainProbe remembers those subtrees
   per column (0f678f9), and NoiseChunk samples flat caches at y 0. A y-dependent subtree there silently changes where
   sites are found.
3. **Use `cache_2d`, not `flat_cache`, for a 2D input that only one landform reads.** NoiseChunk fills every
   `flat_cache` eagerly in every chunk of the world. `cache_2d` is lazy, so chunks whose `choose` never reaches that
   branch pay nothing.
4. **Wrap a 3D noise read twice at the same point in `cache_once`.** Examples are a shared base noise under two
   blended landforms, or `jag` in several lenses. Without it, each reference samples it again.
5. **Never name a subtree twice in datagen.** Vanilla `mapAll` doesn't memoise, so the tree doubles at every level.
   Use `choose` (or a named density-function file) instead of spelling the same expression out twice.
6. **Blend landforms with `choose`, never `lerp`.** `lerp` evaluates both sides everywhere, even where the mask is 0
   or 1.
7. **Work that only depends on the column belongs in a per-column memo.** NoiseChunk asks for every cell corner down
   a column in turn. VoronoiFracture searched its Voronoi cells again at every height until 0e5adb2. Keep the memo
   per thread, keyed by owner identity plus x and z.
8. **Thread-local memos hold their owner (a Field, a solver, a density function) through a `WeakReference`,** so an
   idle worker never keeps a closed world alive.
9. **Give custom functions `equals`/`hashCode` when they sit under a cache marker,** or NoiseChunk won't share their
   caches (RegionMask).
10. **Keep `minValue`/`maxValue` tight.** Vanilla `min`/`max` skip their second argument when the bounds allow; loose
    bounds such as `Df.y()`'s ±2048 never let them.

## 3. Fields and site searches

1. **Use `worldgen/CellCache`, never Guava's `Cache`.** Size-bounded Guava caches spent about a tenth of worldgen on
   their recency queue (16f10d7). CellCache also mixes its keys. A packed `(cx << 32) ^ cz` key has the
   `Long.hashCode` `cx ^ cz`, which piled whole diagonals into one bucket (47c1330).
2. **Never change a key's value to improve hashing.** GroveField and BreakField keys seed randoms, and SkirtGrid
   decodes its keys. Mix only where the key enters a hash table.
3. **Put the cheapest rejection first, and don't move a random draw.**
   - Check the biome or region before probing terrain. StackField probed about 10k terrain points per 56-block cell
     across the whole dimension before checking whether the plateau was near (47c1330).
   - A reordered search must make exactly the same draws in the same order, so the same sites come out.
   - Skip questions whose answer can't matter. CaveField stopped consulting basins and landmarks for columns too thin
     for a cave (9eefb96).
4. **Know what a TerrainProbe call costs.**

   | Call | Cost |
   |---|---|
   | `solid` | One full uncached evaluation of the pre-cave terrain |
   | `surface` | About 15-35 of those |
   | `surfaceNear` | Steps one block at a time |
   | `biomeAt` | Cached per quart (f6d84cd), but a first lookup samples the climate |

   Count probe calls per site, multiply by the cells a chunk touches, and remember that the first touch is likely
   serial.
5. **Reads that come in runs within one cell get a per-thread memo of that cell** (four corners, or the last tile;
   732b1fe), so a run of samples skips the cache.
6. **Site searches must be pure functions of the seed and the cell.** Never make one depend on which chunk asked
   first.

## 4. Features

1. **Assume a feature is serial.** Before adding one to all of `#aether:is_aether`, work out its cost in a chunk with
   nothing to do. That cost is paid in every chunk of the dimension. Return early before any loop.
2. **Expensive per-chunk preparation goes ahead of the features step.** Follow the cloud skirts' prefetcher
   (7371559):
   - work is queued from the first look at a chunk's terrain (`PlateauStacks.stacksAt`);
   - it runs on a few low-priority threads of its own;
   - the queue is bounded and drops what it can't take;
   - each key is asked for once;
   - the feature only reads the cache.

   **Never queue on `Util.backgroundExecutor()`.** That is the worldgen pool, and a naive prefetch there made
   generation *slower* without ByePregen: the terrain the features step waits for queued behind it.
3. **Read blocks by section and skip empty sections** (`LevelChunkSection.hasOnlyAir()`). Most of the tall Aether's
   column is sky (9eefb96).
4. **Don't take a placed feature out of some biomes' lists, or reorder the lists, to save time.** A feature's random
   seed comes from its index in the global feature order, so other decorations move.
5. **Cross-chunk decoration isn't deterministic.** Two identical runs differ in 6-13% of chunks (trees, grass, ores,
   camps). Don't build anything that needs exact repeatability across chunk borders.

## 5. Surface rules

- **Order conditions cheapest first:** the biome before anything that reads neighbouring columns (the `steep` rule,
  9eefb96).
- **Decide without the noise where you can.** If a noise can only move a threshold by ±r, decide outside that band
  without sampling it (the nadirite base).

## 6. The server thread

Never load a Field cell synchronously on the server thread (ticks, spawn rules, loot, commands). Use a non-loading
lookup (`CellCache.getIfPresent`), and warm the cell in the background the way `RumourWarmup` does. `LandmarkVisits`
still loads cells on the server thread; it is the known open case.

## 7. Logging

Nothing in worldgen may log once per chunk, column or block, and a stack trace never.

## 8. Before committing a worldgen change

The dev server's perf probe (`debug/PerfProbe`) does the measuring. It runs at startup, writes to
`run-server/perf/`, and stops the server.

```
./gradlew runServer -PoptionalMods=none -PaaPerf=fingerprint,bench,chunkhash,jfr -PaaPerfTag=before -PaaWorld=perf_before
```

- **`-PaaWorld`** plays a world of that name, made fresh if it doesn't exist, without editing `server.properties`.
  Use a new name for every run with `bench`, since a bench on an existing world just loads chunks from disk.
- **The seed** comes from `server.properties`. Keep it the same for both sides of a comparison.
- **Steps:**

  | Step | What it does |
  |---|---|
  | `fingerprint` | Noise-column hash (no fresh world needed) |
  | `bench` | 484 fixed chunks: wall and worker-CPU ms per chunk, `ms/<feature>` timings |
  | `chunkhash` | Each bench chunk's hash and block counts |
  | `fly` | The flight (`-PaaPerfFly="x0 z0 x1 z1 speed radius"`) |
  | `spark` | A spark profile round the flight, saved to file, if spark is in `run-server/mods` |
  | `jfr` | JFR over bench and flight |

- **On an existing world:** `/altered_aether fingerprint` gives the noise fingerprint without a restart.

The checks:

1. **Fallback check:** run with ByePregen (`-PoptionalMods=all`, or `byepregen`). Grep the log for
   `Disabling final-density column evaluator`; it must be 0.
2. **Speed:** a `bench` before and after.
   - Run each side twice: at around 35 ms per chunk, runs differ by up to 10-15%, and worker CPU is the steadier
     figure.
   - Leave the machine idle while it runs (no builds).
   - To compare against an older commit, build it in a separate `git worktree` with its own `run-server`.
3. **Exploring check:** add `fly` and read the "Flight done" line: chunks missing near the player on average, and the
   longest tick. Missing chunks in the first 30 seconds mean the structure-start backlog has grown.
4. **When something is slow, profile it:**
   `java tools/perf/JfrAgg.java run-server/perf/<tag>.jfr [cpu|park|alloc] [top] [thread prefix|-] [only stacks containing]`.
   - It shows how many threads each generation step keeps busy; about 1 means one chunk at a time.
   - It shows where the samples go by step, by our class and call chain, by the Aether structure whose placement probe
     they fall under, and by feature.
5. **Changes meant to be invisible must prove it:**
   `python -I tools/perf/compare.py before after --watch block.aether.cold_aercloud,...`.
   - The fingerprints must be equal.
   - The blocks the change touches must match in every chunk.
   - Other differences in leaves, grass, ores or the odd water block are cross-chunk ordering noise (§4.5).
6. **Check the stock-height world too:** `initial-disabled-packs=mod/altered_aether:datapacks/tall_aether` (back the
   file up), on a fresh world. It must load. A noise band's height must be a multiple of 16; 04c1e05 fixed a world
   that didn't load.
