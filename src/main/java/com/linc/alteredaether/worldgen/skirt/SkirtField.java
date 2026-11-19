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
package com.linc.alteredaether.worldgen.skirt;

import com.linc.alteredaether.worldgen.CellCache;
import com.linc.alteredaether.worldgen.TerrainProbe;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static com.linc.alteredaether.worldgen.skirt.SkirtGrid.STEP;

/**
 * Where the cloud skirts lie in one world: collars of cold aercloud round some of the Highlands' islets (see
 * {@link SkirtGrid}). The terrain is read through a {@link TerrainProbe}, so the answer depends only on the seed and
 * the position and every chunk sees the same collar. Only the tall Aether has a field.
 *
 * <p>Two layers of tiles, each {@link #TILE} grid columns square: what the terrain is at each column (its top, whether
 * it is rift, and once its island is known whether that island is skirted), and the collar sampled at each column.
 */
public final class SkirtField {
    private static final Map<ResourceKey<Level>, SkirtField> FIELDS = new ConcurrentHashMap<>();
    /** Grid columns per tile side. */
    static final int TILE = 16;
    private static final String RIFT = "altered_aether:shattered_rift";
    /** Grid columns searched round a tile for rims that reach into it. */
    private static final int SOURCE_REACH = (int) Math.ceil(SkirtGrid.REACH / STEP);
    /** Grid columns searched for the rift. */
    private static final int RIFT_REACH = (int) Math.ceil(SkirtGrid.RIFT_FADE / STEP);
    private static final byte UNKNOWN = 0, SKIRTED = 1, BARE = 2, OVER_CAP = 3;
    /** A column's biome, as far as the skirts care, once read. */
    private static final byte OTHER = 1, IN_RIFT = 2;

    public static void put(ResourceKey<Level> dimension, @Nullable SkirtField field) {
        if (field == null) {
            FIELDS.remove(dimension);
        } else {
            FIELDS.put(dimension, field);
        }
    }

    @Nullable
    public static SkirtField get(ResourceKey<Level> dimension) {
        return FIELDS.get(dimension);
    }

    private final long seed;
    private final TerrainProbe probe;
    private final CellCache<Terrain> terrain = new CellCache<>(1 << 13);
    private final CellCache<int[]> coarse = new CellCache<>(1 << 13);
    private final CellCache<Collar> collars = new CellCache<>(1 << 12);

    public SkirtField(long seed, TerrainProbe probe) {
        this.seed = seed;
        this.probe = probe;
    }

    public long seed() {
        return seed;
    }

    // ---------------------------------------------------------------------------------------------- queries --

    /**
     * The collar's span in a block column, scaled by {@code gate} (1 for the whole collar), into {@code out} as in
     * {@link SkirtGrid#span}. False when the column has none.
     */
    public boolean span(int x, int z, double gate, double[] out) {
        return span(collar(x, z), x, z, gate, out);
    }

    /**
     * Whether a block lies in a collar, judged without reading the terrain: when the collar there isn't known yet it
     * is worked out in the background, and the answer is yes meanwhile. For spawn checks on the server thread.
     */
    public boolean inCollarQuickly(int x, int y, int z) {
        int tx = Math.floorDiv(Math.floorDiv(x, STEP), TILE), tz = Math.floorDiv(Math.floorDiv(z, STEP), TILE);
        Collar collar = collars.getIfPresent(SkirtGrid.key(tx, tz));
        if (collar == null) {
            prefetchTile(tx, tz);
            return true;
        }
        double[] out = new double[2];
        return span(collar, x, z, 1, out) && y >= Math.floor(out[0]) - 1 && y <= Math.ceil(out[1]) + 1;
    }

    /**
     * The nearest point a collar grows from (a skirted islet's rim), searching tile by tile
     * out to {@code maxDistance}; for the debug tools. The height given is the source's, not the collar's.
     */
    public Optional<int[]> nearestRim(int x, int z, int maxDistance) {
        int tx0 = Math.floorDiv(Math.floorDiv(x, STEP), TILE), tz0 = Math.floorDiv(Math.floorDiv(z, STEP), TILE);
        int rings = maxDistance / (TILE * STEP) + 1;
        View view = new View();
        for (int r = 0; r <= rings; r++) {
            for (int tx = tx0 - r; tx <= tx0 + r; tx++) {
                for (int tz = tz0 - r; tz <= tz0 + r; tz++) {
                    if (Math.max(Math.abs(tx - tx0), Math.abs(tz - tz0)) != r) {
                        continue;
                    }
                    for (int i = 0; i < TILE * TILE; i++) {
                        int gx = tx * TILE + i % TILE, gz = tz * TILE + i / TILE;
                        if (source(view, gx, gz)) {
                            return Optional.of(new int[]{gx * STEP, view.top(gx, gz), gz * STEP});
                        }
                    }
                }
            }
        }
        return Optional.empty();
    }

    /**
     * For the debug tools: a grid column's top, 1 when it is on a rim, and its island's verdict (0 not judged,
     * 1 skirted, 2 not, 3 too big to judge).
     */
    public int[] debugColumn(int gx, int gz) {
        View view = new View();
        int top = view.top(gx, gz);
        int edge = top != SkirtGrid.SKY && top != SkirtGrid.HIGH && SkirtGrid.rim(view, gx, gz) ? 1 : 0;
        if (top == SkirtGrid.SKY || top == SkirtGrid.HIGH) {
            return new int[]{top, edge, 0};
        }
        skirted(view, gx, gz);
        return new int[]{top, edge, view.tile(gx, gz).verdict[Terrain.index(gx, gz)]};
    }

    private boolean span(Collar collar, int x, int z, double gate, double[] out) {
        if (collar.empty()) {
            return false;
        }
        int gx = Math.floorDiv(x, STEP), gz = Math.floorDiv(z, STEP);
        int i = gx - collar.gx0, j = gz - collar.gz0;
        double fx = (x - gx * STEP) / (double) STEP, fz = (z - gz * STEP) / (double) STEP;
        double distance = collar.lerp(collar.distance, i, j, fx, fz);
        if (distance >= SkirtGrid.MAX_WIDTH) {
            return false;
        }
        double g = collar.lerp(collar.gate, i, j, fx, fz) * gate;
        if (g <= 0) {
            return false;
        }
        double rim = collar.lerp(collar.height, i, j, fx, fz);
        return SkirtGrid.span(distance, rim, g, SkirtGrid.width(seed, x, z), SkirtGrid.thickness(seed, x, z),
                SkirtGrid.billowTop(seed, x, z), SkirtGrid.billowBottom(seed, x, z), out);
    }

    /**
     * Starts working out, in the background, the collar tiles the cloud-skirt feature will read for a chunk. The
     * feature runs in the features step, which worldgen runs one chunk at a time, while the terrain before it runs on
     * every worker; asked for from the terrain, the tiles are usually ready by the time the feature wants them, and
     * the feature only reads them.
     */
    public static void prefetch(int chunkX, int chunkZ) {
        if (FIELDS.isEmpty()) {
            return;
        }
        int margin = CloudSkirts.MARGIN;
        int tx0 = Math.floorDiv(Math.floorDiv((chunkX << 4) - margin, STEP), TILE);
        int tx1 = Math.floorDiv(Math.floorDiv((chunkX << 4) + 15 + margin, STEP), TILE);
        int tz0 = Math.floorDiv(Math.floorDiv((chunkZ << 4) - margin, STEP), TILE);
        int tz1 = Math.floorDiv(Math.floorDiv((chunkZ << 4) + 15 + margin, STEP), TILE);
        for (SkirtField field : FIELDS.values()) {
            for (int tx = tx0; tx <= tx1; tx++) {
                for (int tz = tz0; tz <= tz1; tz++) {
                    field.prefetchTile(tx, tz);
                }
            }
        }
    }

    /** Tiles queued or being worked out in the background, so each is asked for once. */
    private final java.util.Set<Long> prefetching = ConcurrentHashMap.newKeySet();

    private void prefetchTile(int tx, int tz) {
        long key = SkirtGrid.key(tx, tz);
        if (collars.getIfPresent(key) != null || !prefetching.add(key)) {
            return;
        }
        try {
            Prefetch.EXECUTOR.execute(() -> {
                try {
                    collarTile(tx, tz);
                } catch (RuntimeException ignored) {
                    // The feature works the tile out itself if it is still missing.
                } finally {
                    prefetching.remove(key);
                }
            });
        } catch (java.util.concurrent.RejectedExecutionException full) {
            prefetching.remove(key);
        }
    }

    /**
     * A few low-priority threads of their own, with a bounded queue that drops what it can't take: on the shared
     * worldgen pool the tiles would queue ahead of the terrain the features step is waiting for.
     */
    private static final class Prefetch {
        static final java.util.concurrent.ThreadPoolExecutor EXECUTOR;

        static {
            int threads = Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors() / 6));
            java.util.concurrent.atomic.AtomicInteger count = new java.util.concurrent.atomic.AtomicInteger();
            EXECUTOR = new java.util.concurrent.ThreadPoolExecutor(threads, threads, 30, java.util.concurrent.TimeUnit.SECONDS,
                    new java.util.concurrent.ArrayBlockingQueue<>(256), runnable -> {
                Thread thread = new Thread(runnable, "Altered Aether skirt prefetch " + count.incrementAndGet());
                thread.setDaemon(true);
                thread.setPriority(Thread.MIN_PRIORITY);
                return thread;
            }, new java.util.concurrent.ThreadPoolExecutor.AbortPolicy());
            EXECUTOR.allowCoreThreadTimeOut(true);
        }
    }

    /** The last collar tile each thread read: the feature reads some 900 columns of one or two tiles per chunk. */
    private static final class LastTile {
        /** Weak, so a worker thread doesn't keep a closed world's field alive. */
        java.lang.ref.WeakReference<SkirtField> owner = new java.lang.ref.WeakReference<>(null);
        int tx = Integer.MIN_VALUE, tz = Integer.MIN_VALUE;
        Collar collar;
    }

    private static final ThreadLocal<LastTile> LAST_TILE = ThreadLocal.withInitial(LastTile::new);

    private Collar collar(int x, int z) {
        int tx = Math.floorDiv(Math.floorDiv(x, STEP), TILE), tz = Math.floorDiv(Math.floorDiv(z, STEP), TILE);
        LastTile last = LAST_TILE.get();
        if (last.owner.get() != this || last.tx != tx || last.tz != tz) {
            last.collar = collarTile(tx, tz);
            last.owner = new java.lang.ref.WeakReference<>(this);
            last.tx = tx;
            last.tz = tz;
        }
        return last.collar;
    }

    private Collar collarTile(int tx, int tz) {
        return cached(collars, SkirtGrid.key(tx, tz), () -> buildCollar(tx, tz));
    }

    // ------------------------------------------------------------------------------------------------ collars --

    /** The collar at every grid column of a tile, plus one more row and column so a block can interpolate. */
    private Collar buildCollar(int tx, int tz) {
        int gx0 = tx * TILE, gz0 = tz * TILE;
        View view = new View();
        SkirtGrid.Sources sources = new SkirtGrid.Sources();
        for (int gz = gz0 - SOURCE_REACH; gz <= gz0 + TILE + SOURCE_REACH; gz++) {
            for (int gx = gx0 - SOURCE_REACH; gx <= gx0 + TILE + SOURCE_REACH; gx++) {
                if (source(view, gx, gz)) {
                    sources.add(gx * STEP, gz * STEP, view.top(gx, gz));
                }
            }
        }
        if (sources.size() == 0) {
            return Collar.EMPTY;
        }
        int n = TILE + 1, m = n + 2;
        // Sampled one column further out all round, so the rise of the rim height can be taken at every column.
        double[] near = new double[m * m], blend = new double[m * m];
        double[] sample = new double[2];
        for (int j = 0; j < m; j++) {
            for (int i = 0; i < m; i++) {
                SkirtGrid.sample(sources, (gx0 + i - 1) * STEP, (gz0 + j - 1) * STEP, sample);
                near[j * m + i] = sample[0];
                blend[j * m + i] = sample[1];
            }
        }
        float[] distance = new float[n * n], height = new float[n * n], gate = new float[n * n];
        boolean any = false;
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < n; i++) {
                int gx = gx0 + i, gz = gz0 + j, at = (j + 1) * m + i + 1;
                distance[j * n + i] = (float) near[at];
                height[j * n + i] = (float) blend[at];
                if (near[at] < SkirtGrid.FAR) {
                    double rise = Math.hypot(rise(near, blend, at, 1), rise(near, blend, at, m));
                    gate[j * n + i] = (float) (inner(view, gx, gz, blend[at]) * riftFade(view, gx, gz)
                            * SkirtGrid.levelness(rise));
                    any |= gate[j * n + i] > 0 && near[at] < SkirtGrid.MAX_WIDTH;
                }
            }
        }
        return any ? new Collar(gx0, gz0, distance, height, gate) : Collar.EMPTY;
    }

    /**
     * How fast the blended rim height changes per block across a sample, along one axis ({@code stride} 1 for x, the
     * row length for z), from the samples either side that have a rim in reach.
     */
    private static double rise(double[] near, double[] blend, int at, int stride) {
        boolean before = near[at - stride] < SkirtGrid.FAR, after = near[at + stride] < SkirtGrid.FAR;
        if (before && after) {
            return Math.abs(blend[at + stride] - blend[at - stride]) / (2.0 * STEP);
        }
        if (before || after) {
            return Math.abs(blend[before ? at - stride : at + stride] - blend[at]) / STEP;
        }
        return 0;
    }

    /** 1 where the collar may lie: off the island, or one grid step inside its edge; 0 deeper in. */
    private static double inner(View view, int gx, int gz, double rim) {
        if (!covered(view, gx, gz, rim)) {
            return 1;
        }
        return !covered(view, gx + 1, gz, rim) || !covered(view, gx - 1, gz, rim)
                || !covered(view, gx, gz + 1, rim) || !covered(view, gx, gz - 1, rim) ? 1 : 0;
    }

    private static boolean covered(View view, int gx, int gz, double rim) {
        return SkirtGrid.covered(view.top(gx, gz), rim);
    }

    private static double riftFade(View view, int gx, int gz) {
        double nearest = Double.MAX_VALUE;
        for (int dz = -RIFT_REACH; dz <= RIFT_REACH; dz++) {
            for (int dx = -RIFT_REACH; dx <= RIFT_REACH; dx++) {
                if (view.rift(gx + dx, gz + dz)) {
                    nearest = Math.min(nearest, Math.sqrt(dx * dx + dz * dz) * STEP);
                }
            }
        }
        return nearest == Double.MAX_VALUE ? 1 : SkirtGrid.riftFade(nearest);
    }

    /** Whether a collar grows from a grid column: a rim column of a skirted island. */
    private boolean source(View view, int gx, int gz) {
        int top = view.top(gx, gz);
        return top != SkirtGrid.SKY && top != SkirtGrid.HIGH && SkirtGrid.rim(view, gx, gz) && skirted(view, gx, gz);
    }

    /** Whether the island under a land column is skirted, worked out once for the whole island. */
    private boolean skirted(View view, int gx, int gz) {
        Terrain tile = view.tile(gx, gz);
        int index = Terrain.index(gx, gz);
        byte verdict = tile.verdict[index];
        if (verdict == UNKNOWN) {
            SkirtGrid.Island island = SkirtGrid.flood(view, seed, gx, gz);
            verdict = island.overCap() ? OVER_CAP : island.skirted() ? SKIRTED : BARE;
            for (long point : island.points()) {
                int px = (int) (point >> 32), pz = (int) point;
                view.tile(px, pz).verdict[Terrain.index(px, pz)] = verdict;
            }
        }
        return verdict == SKIRTED;
    }

    // ------------------------------------------------------------------------------------------------ terrain --

    private Terrain terrainTile(int tx, int tz) {
        return cached(terrain, SkirtGrid.key(tx, tz), () -> readTerrain(tx, tz));
    }

    /**
     * Reads a tile's columns. The even columns come from the coarse tiles; an odd one is read only when an even one
     * beside it holds land, since sky on both sides of it means at most a scrap too small for a collar.
     */
    private Terrain readTerrain(int tx, int tz) {
        Terrain tile = new Terrain();
        int gx0 = tx * TILE, gz0 = tz * TILE;
        for (int i = 0; i < TILE * TILE; i++) {
            int gx = gx0 + i % TILE, gz = gz0 + i / TILE;
            boolean oddX = (gx & 1) != 0, oddZ = (gz & 1) != 0;
            if (!oddX && !oddZ) {
                tile.top[i] = coarseTop(gx, gz);
            } else if (oddX && !oddZ ? landBeside(gx - 1, gz, gx + 1, gz)
                    : !oddX ? landBeside(gx, gz - 1, gx, gz + 1)
                    : landBeside(gx - 1, gz - 1, gx + 1, gz + 1) || landBeside(gx - 1, gz + 1, gx + 1, gz - 1)) {
                tile.top[i] = readColumn(gx, gz);
            } else {
                tile.top[i] = SkirtGrid.SKY;
            }
        }
        return tile;
    }

    private boolean landBeside(int ax, int az, int bx, int bz) {
        return coarseTop(ax, az) != SkirtGrid.SKY || coarseTop(bx, bz) != SkirtGrid.SKY;
    }

    /** The top of an even grid column, from the coarse tiles. */
    private int coarseTop(int gx, int gz) {
        int tx = Math.floorDiv(gx, TILE), tz = Math.floorDiv(gz, TILE);
        int[] tops = cached(coarse, SkirtGrid.key(tx, tz), () -> {
            int[] read = new int[TILE * TILE / 4];
            for (int i = 0; i < read.length; i++) {
                read[i] = readColumn(tx * TILE + 2 * (i % (TILE / 2)), tz * TILE + 2 * (i / (TILE / 2)));
            }
            return read;
        });
        return tops[Math.floorMod(gx, TILE) / 2 + Math.floorMod(gz, TILE) / 2 * (TILE / 2)];
    }

    /** The top of one grid column, read from the terrain. */
    private int readColumn(int gx, int gz) {
        int x = gx * STEP, z = gz * STEP;
        if (probe.solid(x, SkirtGrid.PROBE_TOP, z)) {
            return SkirtGrid.HIGH;
        }
        // MIN_VALUE, which is SKY, when the band is empty.
        return probe.surface(x, z, SkirtGrid.PROBE_TOP - 1, SkirtGrid.PROBE_BOTTOM);
    }

    private static <T> T cached(CellCache<T> cache, long key, java.util.function.Supplier<T> loader) {
        return cache.get(key, k -> loader.get());
    }

    /**
     * What the terrain is at each grid column of a tile. Whether a column is rift is read only when asked, and
     * verdicts are filled in as islands are found.
     */
    private static final class Terrain {
        final int[] top = new int[TILE * TILE];
        /** 0 not yet read, else OTHER or IN_RIFT. */
        final byte[] biome = new byte[TILE * TILE];
        final byte[] verdict = new byte[TILE * TILE];

        static int index(int gx, int gz) {
            return Math.floorMod(gx, TILE) + Math.floorMod(gz, TILE) * TILE;
        }
    }

    /** The collar at the grid columns of a tile: distance to the nearest source, its height, the gate. */
    private record Collar(int gx0, int gz0, float[] distance, float[] height, float[] gate) {
        static final Collar EMPTY = new Collar(0, 0, null, null, null);
        static final int N = TILE + 1;

        boolean empty() {
            return distance == null;
        }

        double lerp(float[] values, int i, int j, double fx, double fz) {
            double a = values[j * N + i], b = values[j * N + i + 1];
            double c = values[(j + 1) * N + i], d = values[(j + 1) * N + i + 1];
            return (a + (b - a) * fx) * (1 - fz) + (c + (d - c) * fx) * fz;
        }
    }

    /** The terrain grid read through the tiles, remembering the last tile it looked in. */
    private final class View implements SkirtGrid.Columns {
        private long lastKey = Long.MIN_VALUE;
        private Terrain last;

        Terrain tile(int gx, int gz) {
            int tx = Math.floorDiv(gx, TILE), tz = Math.floorDiv(gz, TILE);
            long key = SkirtGrid.key(tx, tz);
            if (key != lastKey || last == null) {
                last = terrainTile(tx, tz);
                lastKey = key;
            }
            return last;
        }

        @Override
        public int top(int gx, int gz) {
            return tile(gx, gz).top[Terrain.index(gx, gz)];
        }

        @Override
        public boolean rift(int gx, int gz) {
            return biome(gx, gz) == IN_RIFT;
        }

        private byte biome(int gx, int gz) {
            Terrain tile = tile(gx, gz);
            int index = Terrain.index(gx, gz);
            if (tile.biome[index] == 0) {
                String biome = probe.biomeAt(gx * STEP, gz * STEP);
                tile.biome[index] = biome.equals(RIFT) ? IN_RIFT : OTHER;
            }
            return tile.biome[index];
        }
    }
}
