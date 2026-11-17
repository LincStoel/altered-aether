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
 * <p>The terrain is read on a grid of columns {@link #STEP} blocks apart. Each column has a <em>top</em>: the highest
 * solid block in the low band under {@link #PROBE_TOP} with air above it, or {@link #SKY} when the band is empty, or
 * {@link #HIGH} when the column is solid at the band's top (an island too high to matter). Neighbouring columns belong
 * to one island when their tops are within {@link #DROP} of each other; a column is on its island's <em>rim</em> when
 * a neighbour is sky or drops away by at least that much. An island is low when more than half its rim lies below
 * {@link #LOW_BELOW}, and only low islands' rims grow a collar; scraps under {@link #MIN_ISLAND} columns get none.
 *
 * <p>The collar round a rim is a lens like the dune sheets': {@link #GAP} under the rim at the cliff, up to
 * {@link #MAX_THICK} thick there, tapering top and bottom to a feather edge {@link #MIN_WIDTH} to {@link #MAX_WIDTH}
 * blocks out.
 */
public final class SkirtGrid {
    /** Blocks between grid columns. */
    public static final int STEP = 4;
    public static final int SKY = Integer.MIN_VALUE;
    public static final int HIGH = Integer.MAX_VALUE;
    /** A column solid at this height belongs to an island whose rim is too high to matter. */
    public static final int PROBE_TOP = 23;
    /** A neighbour this much lower is off the island's edge (as IslandEdge's drop). */
    public static final int DROP = 16;
    /** Rim heights under this are low. */
    public static final int LOW_BELOW = 0;
    /** Grid columns an island may have before it counts as a landmass, voted on locally. */
    public static final int CAP = 8192;
    /** Grid columns an island needs for a collar: a scrap of rock a few blocks across gets none. */
    public static final int MIN_ISLAND = 6;
    /** Radius of the local vote on a landmass, in grid columns (128 blocks). */
    public static final int VOTE_RADIUS = 32;
    /** Side of a local vote's cell, in grid columns: one vote serves the cell. */
    public static final int VOTE_CELL = 8;

    public static final double MIN_WIDTH = 16, MAX_WIDTH = 32;
    public static final double MIN_THICK = 3, MAX_THICK = 5;
    /** The collar's top at the cliff, under the rim. */
    public static final double GAP = 2;
    /** How far the middle of the collar sinks from the cliff to the feather edge. */
    public static final double DROOP = 1.5;
    /** Spread of the blend between rim heights, in blocks. */
    public static final double SIGMA = 10;
    /** Rims further than this from a point, in blocks, play no part in its collar. */
    public static final double REACH = 48;
    /** Distance recorded where no rim is in reach. */
    public static final double FAR = 64;
    /** Blocks over which a collar fades out toward the Shattered Rift. */
    public static final double RIFT_FADE = 8;
    /** Blocks over which a collar thins round the gap a waterfall leaves. */
    public static final double FEATHER = 3;

    /** The terrain as the grid sees it. */
    public interface Columns {
        int top(int gx, int gz);

        boolean rift(int gx, int gz);
    }

    /** An island found by {@link #flood}: its columns, and whether it is low; {@code overCap} when it was too big. */
    public record Island(long[] points, boolean low, boolean overCap) {
    }

    /** Rim columns of low islands, in blocks, in grid order (z, then x), so every sum over them is in one order. */
    public static final class Sources {
        int size;
        int[] x = new int[64], z = new int[64];
        float[] height = new float[64];

        public void add(int bx, int bz, int h) {
            if (size == x.length) {
                x = java.util.Arrays.copyOf(x, size * 2);
                z = java.util.Arrays.copyOf(z, size * 2);
                height = java.util.Arrays.copyOf(height, size * 2);
            }
            x[size] = bx;
            z[size] = bz;
            height[size] = h;
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

    /** Whether a rim column counts as low in a vote. */
    public static boolean lowRim(int top) {
        return top != HIGH && top < LOW_BELOW;
    }

    /**
     * The island holding a land column: every column joined to it, and the vote of its rim. Stops after {@link #CAP}
     * columns and reports the island as over the cap, which any column of that island would also find.
     */
    public static Island flood(Columns columns, int gx, int gz) {
        Set<Long> seen = new HashSet<>();
        ArrayDeque<Long> queue = new ArrayDeque<>();
        long start = key(gx, gz);
        seen.add(start);
        queue.add(start);
        int low = 0, total = 0;
        while (!queue.isEmpty()) {
            long point = queue.poll();
            int x = (int) (point >> 32), z = (int) point;
            int top = columns.top(x, z);
            if (rim(columns, x, z)) {
                total++;
                if (lowRim(top)) {
                    low++;
                }
            }
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
        return new Island(toArray(seen), seen.size() >= MIN_ISLAND && total > 0 && low * 2 > total, false);
    }

    /** The local vote for a landmass, taken once per {@link #VOTE_CELL}: more than half the rim in reach is low. */
    public static boolean localLow(Columns columns, int cellX, int cellZ) {
        int cx = cellX * VOTE_CELL + VOTE_CELL / 2, cz = cellZ * VOTE_CELL + VOTE_CELL / 2;
        int low = 0, total = 0;
        for (int dz = -VOTE_RADIUS; dz <= VOTE_RADIUS; dz++) {
            for (int dx = -VOTE_RADIUS; dx <= VOTE_RADIUS; dx++) {
                if (dx * dx + dz * dz > VOTE_RADIUS * VOTE_RADIUS || !rim(columns, cx + dx, cz + dz)) {
                    continue;
                }
                total++;
                if (lowRim(columns.top(cx + dx, cz + dz))) {
                    low++;
                }
            }
        }
        return total > 0 && low * 2 > total;
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

    /** How much of the collar survives this many blocks from a waterfall whose gap has this radius. */
    public static double feather(double distance, double radius) {
        return distance <= radius ? 0 : smoothstep((distance - radius) / FEATHER);
    }

    /** The radius of the gap round a waterfall, 1 to 3 blocks, fixed by where it falls. */
    public static int gapRadius(long seed, int x, int z) {
        return 1 + (int) Math.floor((hash(seed ^ 0x5EEDFA11L, x, z) * 0.5 + 0.5) * 2.999);
    }

    /** Whether a column's ground covers the collar there: an island the collar may only reach just inside. */
    public static boolean covered(int top, double rimHeight) {
        return top == HIGH || top != SKY && top >= rimHeight - GAP - MAX_THICK - DROOP - 1;
    }

    /** The collar's width at a column: 16 to 32 blocks, wandering round the rim. */
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
     * @param distance  blocks from the nearest low rim
     * @param rim       the blended rim height
     * @param gate      what survives of the collar here (rift fade, inner reach, waterfall gaps), 0 to 1
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
