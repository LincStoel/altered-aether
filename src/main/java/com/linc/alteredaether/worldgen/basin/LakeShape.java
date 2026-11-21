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
package com.linc.alteredaether.worldgen.basin;

/**
 * A big lake's outline, after the Aether II's noise lakes: a contour of {@code noise + bump}, where the bump is high in
 * the middle and falls away to below any noise by {@link #radius}, so the water always covers the centre and never
 * reaches the edge, while the noise between gives it lobes, inlets and now and then an island.
 *
 * <p>Plain Java on block offsets from the lake's centre, so it can be tested without Minecraft. Values are memoised per
 * thread on a 64-block window, as the fill asks about the same columns many times over.
 */
public final class LakeShape {
    /** Noise weight, the bump's height in the middle and the cut taken off both. */
    static final double AMP = 1.4, BUMP = 2.9, CUT = 1.45;
    /** The noise's feature size, as a share of the radius. */
    static final double SCALE = 0.5;
    /** The field value at which the floor reaches its full depth. */
    static final double DEPTH_SPAN = 0.6;
    /** The smallest slope {@link #beyond} divides by, so flat patches of the field don't read as far away. */
    private static final double MIN_SLOPE = 0.004;

    private final long seed;
    private final double radius;
    private final double scale;
    private final int plane;

    /**
     * @param seed   the lake's own seed
     * @param radius the furthest the water can reach from the centre
     * @param plane  how far above the water the ground over the lake and its banks is cleared
     */
    public LakeShape(long seed, double radius, int plane) {
        this.seed = seed;
        this.radius = radius;
        this.scale = Math.max(16, radius * SCALE);
        this.plane = plane;
    }

    public double radius() {
        return radius;
    }

    public int plane() {
        return plane;
    }

    /** The field at an offset from the centre: above 0 is water. */
    public double value(int dx, int dz) {
        Memo memo = MEMO.get();
        int slot = (dx & 63) << 6 | (dz & 63);
        if (memo.owner[slot] == this && memo.xs[slot] == dx && memo.zs[slot] == dz) {
            return memo.values[slot];
        }
        double r2 = ((double) dx * dx + (double) dz * dz) / (radius * radius);
        double n = (noise(seed, dx / scale, dz / scale) + 0.4 * noise(seed ^ 0x632BE59BD9B4E019L, dx / (scale * 0.4),
                dz / (scale * 0.4))) / 1.4;
        double value = AMP * n + BUMP * (1 - r2) - CUT;
        memo.owner[slot] = this;
        memo.xs[slot] = dx;
        memo.zs[slot] = dz;
        memo.values[slot] = value;
        return value;
    }

    public boolean wet(int dx, int dz) {
        return value(dx, dz) > 0;
    }

    /**
     * Roughly how many blocks an offset lies outside the shore: the field over its slope. Negative inside, with the
     * same scale, so the distance in from the shore too.
     */
    public double beyond(int dx, int dz) {
        double gx = (value(dx + 1, dz) - value(dx - 1, dz)) / 2, gz = (value(dx, dz + 1) - value(dx, dz - 1)) / 2;
        return -value(dx, dz) / Math.max(MIN_SLOPE, Math.sqrt(gx * gx + gz * gz));
    }

    /**
     * The share of the full depth the floor lies at: 0 at the shore, rising over the first stretch of water to 1 with a
     * gentle swell of its own, so the bed shelves down from the beach and is uneven in the middle.
     */
    public double depthShare(int dx, int dz) {
        double value = value(dx, dz);
        if (value <= 0) {
            return 0;
        }
        double share = Math.pow(Math.min(1, value / DEPTH_SPAN), 0.7);
        return share * (0.8 + 0.2 * noise(seed ^ 0x1B873593L, dx / 14.0, dz / 14.0));
    }

    /** A slow wobble in [-1, 1] for the underside of the ground a lake builds where the island runs out. */
    public double undersideNoise(int dx, int dz) {
        return noise(seed ^ 0x5851F42D4C957F2DL, dx / 9.0, dz / 9.0);
    }

    // -------------------------------------------------------------------------------------------- noise --

    /** Smooth value noise in [-1, 1], features a unit apart. */
    static double noise(long seed, double x, double z) {
        int ix = (int) Math.floor(x), iz = (int) Math.floor(z);
        double tx = x - ix, tz = z - iz;
        tx = tx * tx * (3 - 2 * tx);
        tz = tz * tz * (3 - 2 * tz);
        double a = lattice(seed, ix, iz), b = lattice(seed, ix + 1, iz);
        double c = lattice(seed, ix, iz + 1), d = lattice(seed, ix + 1, iz + 1);
        double top = a + (b - a) * tx, bottom = c + (d - c) * tx;
        return top + (bottom - top) * tz;
    }

    private static double lattice(long seed, int x, int z) {
        long h = mix(seed ^ mix(x * 0x9E3779B97F4A7C15L + z));
        return (h >>> 11) * 0x1.0p-53 * 2 - 1;
    }

    static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    private static final class Memo {
        final LakeShape[] owner = new LakeShape[4096];
        final int[] xs = new int[4096], zs = new int[4096];
        final double[] values = new double[4096];
    }

    private static final ThreadLocal<Memo> MEMO = ThreadLocal.withInitial(Memo::new);
}
