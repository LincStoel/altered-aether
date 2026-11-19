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

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/**
 * The arithmetic behind the cloud skirts, kept free of Minecraft so it can be unit-tested.
 *
 * <p>The skirts belong to the Highlands' islets; the islands below have the cloud sheet instead. The terrain is read
 * on a grid of columns {@link #STEP} blocks apart. Each column has a <em>top</em>: the highest solid block in the
 * Highlands' band, from {@link #PROBE_BOTTOM} up to {@link #PROBE_TOP}, with air above it, or {@link #SKY} when the
 * band is empty, or {@link #HIGH} when the column is solid at the band's top. Neighbouring columns belong to one island
 * when their tops are within {@link #DROP} of each other; a column is on its island's <em>rim</em> when a neighbour is
 * sky or drops away by at least that much. An island of at least {@link #MIN_ISLAND} columns is skirted with the
 * chance {@link #CHANCE}, drawn once for the island, and only skirted islands' rims grow a collar.
 *
 * <p>The collar round a rim is a lens like the dune sheets': {@link #GAP} under the rim at the cliff, up to
 * {@link #MAX_THICK} thick there, tapering top and bottom to a feather edge {@link #MIN_WIDTH} to {@link #MAX_WIDTH}
 * blocks out. It stays level: where the rim climbs or drops it fades away ({@link #levelness}), and it thins out where
 * land comes up beneath it ({@link #clearance}).
 */
public final class SkirtGrid {
    /** Blocks between grid columns. */
    public static final int STEP = 4;
    public static final int SKY = Integer.MIN_VALUE;
    public static final int HIGH = Integer.MAX_VALUE;
    /** The top of the band the grid reads, above the Highlands' highest islets (y 233, tall). */
    public static final int PROBE_TOP = 250;
    /** The bottom of the band the grid reads, under the Highlands' lowest islet tops (y 174, tall). */
    public static final int PROBE_BOTTOM = 160;
    /** A neighbour this much lower is off the island's edge (as IslandEdge's drop). */
    public static final int DROP = 16;
    /** Grid columns an island may have before the search gives up on it: far beyond any islet, and unskirted. */
    public static final int CAP = 8192;
    /** Grid columns an island needs for a collar, about 800 square blocks: under 32 blocks across gets none. */
    public static final int MIN_ISLAND = 50;
    /** The chance that an island big enough for a collar has one. */
    public static final double CHANCE = 0.2;

    public static final double MIN_WIDTH = 32, MAX_WIDTH = 48;
    public static final double MIN_THICK = 3, MAX_THICK = 5;
    /** The collar's top at the cliff, under the rim. */
    public static final double GAP = 2;
    /** How far the middle of the collar sinks from the cliff to the feather edge. */
    public static final double DROOP = 1.5;
    /** Spread of the blend between rim heights, in blocks. */
    public static final double SIGMA = 10;
    /** Rims further than this from a point, in blocks, play no part in its collar. */
    public static final double REACH = 64;
    /** Distance recorded where no rim is in reach. */
    public static final double FAR = 80;
    /**
     * Rise per block of the blended rim height where a collar starts to fade, and where it is gone: a rim that climbs
     * or drops gets no skirt, so a collar never steps up or down a slope.
     */
    public static final double LEVEL = 0.04, SLOPED = 0.1;
    /** Open air a collar wants under it, in blocks; it thins as land comes up beneath and is gone with this little. */
    public static final int CLEAR = 16, CLEAR_MIN = 3;
    /** Blocks over which a collar fades out toward the Shattered Rift. */
    public static final double RIFT_FADE = 8;
    /** The terrain as the grid sees it. */
    public interface Columns {
        int top(int gx, int gz);

        boolean rift(int gx, int gz);
    }

    /**
     * An island found by {@link #flood}: its columns, and whether it is skirted; {@code overCap} when it was too big.
     */
    public record Island(long[] points, boolean skirted, boolean overCap) {
    }

    /** Rim columns of skirted islands, in blocks, in grid order (z, then x), so every sum over them is in one order. */
    public static final class Sources {
        int size;
        int[] x = new int[64], z = new int[64];
        float[] height = new float[64];

        public void add(int bx, int bz, double h) {
            if (size == x.length) {
                x = java.util.Arrays.copyOf(x, size * 2);
                z = java.util.Arrays.copyOf(z, size * 2);
                height = java.util.Arrays.copyOf(height, size * 2);
            }
            x[size] = bx;
            z[size] = bz;
            height[size] = (float) h;
            size++;
        }

        public int size() {
            return size;
        }
    }

    // ------------------------------------------------------------------------------------------------ islands --

    public static boolean land(int top) {
        return top != SKY;
    }

    /** The height a top stands for; a high column counts as the band's top. */
    public static int height(int top) {
        return top == HIGH ? PROBE_TOP : top;
    }

    /** Whether two neighbouring columns are the same island. */
    public static boolean joined(int a, int b) {
        return a != SKY && b != SKY && Math.abs(height(a) - height(b)) < DROP;
    }

    /** Whether a column is on its island's rim. Rift columns never are. */
    public static boolean rim(Columns columns, int gx, int gz) {
        int top = columns.top(gx, gz);
        if (top == SKY || columns.rift(gx, gz)) {
            return false;
        }
        return off(top, columns.top(gx + 1, gz)) || off(top, columns.top(gx - 1, gz))
                || off(top, columns.top(gx, gz + 1)) || off(top, columns.top(gx, gz - 1));
    }

    private static boolean off(int top, int neighbour) {
        return neighbour == SKY || neighbour != HIGH && height(neighbour) <= height(top) - DROP;
    }

    /**
     * The island holding a land column: every column joined to it, and whether it is skirted. Stops after {@link #CAP}
     * columns and reports the island as over the cap, which any column of that island would also find.
     */
    public static Island flood(Columns columns, long seed, int gx, int gz) {
        Set<Long> seen = new HashSet<>();
        ArrayDeque<Long> queue = new ArrayDeque<>();
        long start = key(gx, gz);
        seen.add(start);
        queue.add(start);
        boolean rimmed = false;
        while (!queue.isEmpty()) {
            long point = queue.poll();
            int x = (int) (point >> 32), z = (int) point;
            int top = columns.top(x, z);
            rimmed |= rim(columns, x, z);
            for (int i = 0; i < 4; i++) {
                int nx = x + (i == 0 ? 1 : i == 1 ? -1 : 0), nz = z + (i == 2 ? 1 : i == 3 ? -1 : 0);
                long next = key(nx, nz);
                if (!seen.contains(next) && joined(top, columns.top(nx, nz))) {
                    if (seen.size() >= CAP) {
                        return new Island(toArray(seen), false, true);
                    }
                    seen.add(next);
                    queue.add(next);
                }
            }
        }
        long[] points = toArray(seen);
        return new Island(points, points.length >= MIN_ISLAND && rimmed && chosen(seed, points), false);
    }

    /**
     * The island's draw against {@link #CHANCE}, keyed by its first column in key order, so it comes out the same
     * whichever column the island was found from.
     */
    public static boolean chosen(long seed, long[] points) {
        long first = Long.MAX_VALUE;
        for (long point : points) {
            first = Math.min(first, point);
        }
        return (hash(seed ^ 0x5C1A7EL, (int) (first >> 32), (int) first) + 1) / 2 < CHANCE;
    }

    public static long key(int gx, int gz) {
        return (long) gx << 32 | gz & 0xFFFFFFFFL;
    }

    private static long[] toArray(Set<Long> points) {
        long[] out = new long[points.size()];
        int i = 0;
        for (long point : points) {
            out[i++] = point;
        }
        return out;
    }

    // ------------------------------------------------------------------------------------------------ collars --

    /**
     * The distance from a point to the nearest rim in reach, and the rims' heights blended round it, into
     * {@code out[0]} and {@code out[1]}. With no rim in reach the distance is {@link #FAR}.
     */
    public static void sample(Sources sources, double px, double pz, double[] out) {
        double nearest = Double.MAX_VALUE;
        double reach = REACH * REACH;
        for (int i = 0; i < sources.size; i++) {
            double dx = sources.x[i] - px, dz = sources.z[i] - pz;
            double d = dx * dx + dz * dz;
            if (d < nearest) {
                nearest = d;
            }
        }
        if (nearest > reach) {
            out[0] = FAR;
            out[1] = 0;
            return;
        }
        double weights = 0, sum = 0;
        for (int i = 0; i < sources.size; i++) {
            double dx = sources.x[i] - px, dz = sources.z[i] - pz;
            double d = dx * dx + dz * dz;
            if (d > reach) {
                continue;
            }
            double w = Math.exp(-(d - nearest) / (2 * SIGMA * SIGMA));
            weights += w;
            sum += w * sources.height[i];
        }
        out[0] = Math.min(FAR, Math.sqrt(nearest));
        out[1] = sum / weights;
    }

    /** The fade toward the rift for a point this many blocks from the nearest rift column. */
    public static double riftFade(double distance) {
        return smoothstep(distance / RIFT_FADE);
    }

    /** Whether a column's ground covers the collar there: an island the collar may only reach just inside. */
    public static boolean covered(int top, double rimHeight) {
        return top == HIGH || top != SKY && top >= rimHeight - GAP - MAX_THICK - DROOP - 1;
    }

    /** The collar's width at a column: 32 to 48 blocks, wandering round the rim. */
    public static double width(long seed, double x, double z) {
        return MIN_WIDTH + (MAX_WIDTH - MIN_WIDTH) * (0.5 + 0.5 * fbm(seed ^ 0x77A3L, x / 96, z / 96));
    }

    /** The collar's thickness at the cliff: 3 to 5 blocks. */
    public static double thickness(long seed, double x, double z) {
        return MIN_THICK + (MAX_THICK - MIN_THICK) * (0.5 + 0.5 * fbm(seed ^ 0x3171L, x / 80, z / 80));
    }

    /** The billow of the collar's top, about 1.5 blocks either way. */
    public static double billowTop(long seed, double x, double z) {
        return 1.5 * fbm(seed ^ 0xB111L, x / 12, z / 12);
    }

    /** The billow of the collar's underside, gentler than the top's. */
    public static double billowBottom(long seed, double x, double z) {
        return 0.75 * fbm(seed ^ 0xB0B0L, x / 14, z / 14);
    }

    /**
     * The collar's span in a column: {@code out[0]} its underside and {@code out[1]} its top, as heights a block
     * fills when its centre lies between them. False when there is none.
     *
     * @param distance  blocks from the nearest skirted rim
     * @param rim       the blended rim height
     * @param gate      what survives of the collar here (rift fade, inner reach, clearance), 0 to 1
     */
    public static boolean span(double distance, double rim, double gate, double width, double thick, double billowTop,
                               double billowBottom, double[] out) {
        double lens = smoothstep(1 - distance / width);
        if (lens <= 0 || gate <= 0) {
            return false;
        }
        double half = thick / 2 * lens * gate;
        double middle = rim - GAP - thick / 2 - DROOP * smoothstep(distance / width);
        out[0] = middle - half + billowBottom * lens;
        out[1] = middle + half + billowTop * lens;
        return out[1] > out[0];
    }

    /** What survives of a collar where the blended rim height rises this much per block. */
    public static double levelness(double rise) {
        return 1 - smoothstep((rise - LEVEL) / (SLOPED - LEVEL));
    }

    /** What survives of a collar with this many blocks of open air under it. */
    public static double clearance(int air) {
        return smoothstep((air - CLEAR_MIN) / (double) (CLEAR - CLEAR_MIN));
    }

    /**
     * The whole blocks a span fills: as many as it is thick, rounded, centred on its middle, so a thin collar is still
     * one unbroken layer rather than a scatter where the span happens to cross a block's centre. Returns how many,
     * the lowest being {@code (int) Math.round(middle - count / 2.0)}; see {@link #lowest}.
     */
    public static int layers(double bottom, double top) {
        return Math.max(0, (int) Math.round(top - bottom));
    }

    /** The lowest block of a span's {@link #layers}. */
    public static int lowest(double bottom, double top) {
        int count = layers(bottom, top);
        return (int) Math.round((bottom + top) / 2 - count / 2.0);
    }

    public static double smoothstep(double t) {
        if (t <= 0) {
            return 0;
        }
        if (t >= 1) {
            return 1;
        }
        return t * t * (3 - 2 * t);
    }

    // -------------------------------------------------------------------------------------------------- noise --

    /** Two octaves of smooth value noise, in about [-1, 1]. */
    public static double fbm(long seed, double x, double z) {
        return 0.67 * noise(seed, x, z) + 0.33 * noise(seed * 31 + 7, x * 2.03 + 17.1, z * 2.03 - 5.3);
    }

    /** Smooth value noise in [-1, 1] with features one unit across. */
    public static double noise(long seed, double x, double z) {
        int ix = (int) Math.floor(x), iz = (int) Math.floor(z);
        double tx = x - ix, tz = z - iz;
        double u = tx * tx * tx * (tx * (tx * 6 - 15) + 10), v = tz * tz * tz * (tz * (tz * 6 - 15) + 10);
        double a = hash(seed, ix, iz), b = hash(seed, ix + 1, iz);
        double c = hash(seed, ix, iz + 1), d = hash(seed, ix + 1, iz + 1);
        return (a + (b - a) * u) * (1 - v) + (c + (d - c) * u) * v;
    }

    /** A hash of a lattice point in [-1, 1). */
    public static double hash(long seed, int x, int z) {
        long h = seed ^ x * 0x9E3779B97F4A7C15L ^ z * 0xC2B2AE3D27D4EB4FL;
        h = (h ^ h >>> 30) * 0xBF58476D1CE4E5B9L;
        h = (h ^ h >>> 27) * 0x94D049BB133111EBL;
        h ^= h >>> 31;
        return (h >>> 11) * 0x1.0p-52 - 1;
    }

    private SkirtGrid() {
    }
}
