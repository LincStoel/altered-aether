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

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.linc.alteredaether.worldgen.TerrainProbe;
import net.minecraft.Util;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;

import static com.linc.alteredaether.worldgen.skirt.SkirtGrid.STEP;

/**
 * Where the cloud skirts lie in one world: collars of cold aercloud round the islands whose rim is mostly below y 0
 * (see {@link SkirtGrid}). The terrain is read through a {@link TerrainProbe}, so the answer depends only on the seed
 * and the position and every chunk sees the same collar. Only the tall Aether has a field.
 *
 * <p>Two layers of tiles, each {@link #TILE} grid columns square: what the terrain is at each column (its top, whether
 * it is rift, and once its island is known whether that island is low), and the collar sampled at each column.
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
    private static final byte UNKNOWN = 0, LOW = 1, NOT_LOW = 2, LANDMASS = 3;

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
    private final Cache<Long, Terrain> terrain = CacheBuilder.newBuilder().maximumSize(1 << 13).build();
    private final Cache<Long, int[]> coarse = CacheBuilder.newBuilder().maximumSize(1 << 13).build();
    private final Cache<Long, Collar> collars = CacheBuilder.newBuilder().maximumSize(1 << 12).build();
    private final Cache<Long, Boolean> votes = CacheBuilder.newBuilder().maximumSize(1 << 14).build();

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
            Util.backgroundExecutor().execute(() -> collarTile(tx, tz));
            return true;
        }
        double[] out = new double[2];
        return span(collar, x, z, 1, out) && y >= Math.floor(out[0]) - 1 && y <= Math.ceil(out[1]) + 1;
    }

    /** The nearest point on a low island's rim, searching tile by tile out to {@code maxDistance}; for the debug tools. */
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
                        int top = view.top(gx, gz);
                        if (top != SkirtGrid.SKY && top != SkirtGrid.HIGH && SkirtGrid.rim(view, gx, gz)
                                && low(view, gx, gz)) {
                            return Optional.of(new int[]{gx * STEP, top, gz * STEP});
                        }
                    }
                }
            }
        }
        return Optional.empty();
    }

    /**
     * For the debug tools: a grid column's top, 1 when it is on a rim, and its island's verdict (0 not judged, 1 low,
     * 2 not low, 3 a landmass voted on locally).
     */
    public int[] debugColumn(int gx, int gz) {
        View view = new View();
        int top = view.top(gx, gz);
        if (top == SkirtGrid.SKY || top == SkirtGrid.HIGH) {
            return new int[]{top, 0, 0};
        }
        low(view, gx, gz);
        return new int[]{top, SkirtGrid.rim(view, gx, gz) ? 1 : 0, view.tile(gx, gz).verdict[Terrain.index(gx, gz)]};
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

    private Collar collar(int x, int z) {
        return collarTile(Math.floorDiv(Math.floorDiv(x, STEP), TILE), Math.floorDiv(Math.floorDiv(z, STEP), TILE));
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
                int top = view.top(gx, gz);
                if (top != SkirtGrid.SKY && top != SkirtGrid.HIGH && SkirtGrid.rim(view, gx, gz) && low(view, gx, gz)) {
                    sources.add(gx * STEP, gz * STEP, top);
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
        if (!SkirtGrid.covered(view.top(gx, gz), rim)) {
            return 1;
        }
        return !SkirtGrid.covered(view.top(gx + 1, gz), rim) || !SkirtGrid.covered(view.top(gx - 1, gz), rim)
                || !SkirtGrid.covered(view.top(gx, gz + 1), rim) || !SkirtGrid.covered(view.top(gx, gz - 1), rim)
                ? 1 : 0;
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

    /** Whether the island under a rim column is low: by the whole island, or for a landmass by the local vote. */
    private boolean low(View view, int gx, int gz) {
        Terrain tile = view.tile(gx, gz);
        int index = Terrain.index(gx, gz);
        byte verdict = tile.verdict[index];
        if (verdict == UNKNOWN) {
            SkirtGrid.Island island = SkirtGrid.flood(view, gx, gz);
            verdict = island.overCap() ? LANDMASS : island.low() ? LOW : NOT_LOW;
            for (long point : island.points()) {
                int px = (int) (point >> 32), pz = (int) point;
                view.tile(px, pz).verdict[Terrain.index(px, pz)] = verdict;
            }
        }
        if (verdict == LANDMASS) {
            int cx = Math.floorDiv(gx, SkirtGrid.VOTE_CELL), cz = Math.floorDiv(gz, SkirtGrid.VOTE_CELL);
            return cached(votes, SkirtGrid.key(cx, cz), () -> SkirtGrid.localLow(new View(), cx, cz));
        }
        return verdict == LOW;
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
        return probe.surface(x, z, SkirtGrid.PROBE_TOP - 1, probe.minY());
    }

    private static <T> T cached(Cache<Long, T> cache, long key, Callable<T> loader) {
        try {
            return cache.get(key, loader);
        } catch (ExecutionException e) {
            throw new IllegalStateException(e.getCause());
        } catch (com.google.common.util.concurrent.UncheckedExecutionException e) {
            throw e.getCause() instanceof RuntimeException runtime ? runtime : new IllegalStateException(e.getCause());
        }
    }

    /**
     * What the terrain is at each grid column of a tile. Whether a column is rift is read only when asked, and
     * verdicts are filled in as islands are found.
     */
    private static final class Terrain {
        final int[] top = new int[TILE * TILE];
        /** 0 not yet read, 1 not rift, 2 rift. */
        final byte[] rift = new byte[TILE * TILE];
        final byte[] verdict = new byte[TILE * TILE];

        static int index(int gx, int gz) {
            return Math.floorMod(gx, TILE) + Math.floorMod(gz, TILE) * TILE;
        }
    }

    /** The collar sampled at the grid columns of a tile: distance to the nearest low rim, its height, and the gate. */
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
            Terrain tile = tile(gx, gz);
            int index = Terrain.index(gx, gz);
            if (tile.rift[index] == 0) {
                tile.rift[index] = (byte) (probe.biomeAt(gx * STEP, gz * STEP).equals(RIFT) ? 2 : 1);
            }
            return tile.rift[index] == 2;
        }
    }
}
