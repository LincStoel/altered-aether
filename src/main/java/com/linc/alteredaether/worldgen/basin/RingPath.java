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

import net.minecraft.util.Mth;

/**
 * The centre line of a ring island: a closed loop around the site's centre, stretched into an oval and bent by a few
 * low harmonics so it twists like a kidney or a clover rather than running round a circle. The ring itself is every
 * column within a fixed distance of this line, so it keeps the same width all the way round however the line bends.
 *
 * <p>In the line's own frame (rotated by {@code rotation}, its long axis squashed by {@code aspect}), the line is a
 * curve of radius {@code radius * (1 + sum of harmonics)} about the centre, so it is star-shaped and inside and
 * outside are a radius comparison. Offsets are relative to the site's centre.
 */
public final class RingPath {
    private static final int POINTS = 96;
    /** Harmonic orders bending the line; the second is covered by the aspect, the rest twist it. */
    private static final int[] ORDERS = {2, 3, 4};

    private final double radius, aspect, cos, sin;
    private final double[] amplitudes, phases;
    private final double[] px = new double[POINTS], pz = new double[POINTS];
    private final double nearest, furthest;

    /**
     * @param radius     the line's mean radius, before stretching
     * @param aspect     how much longer the oval is than it is wide, 1 for round
     * @param rotation   bearing of the oval's long axis
     * @param amplitudes the twist harmonics' strengths, one per order in {@link #ORDERS}
     */
    public RingPath(double radius, double aspect, double rotation, double[] amplitudes, double[] phases) {
        this.radius = radius;
        this.aspect = aspect;
        this.cos = Math.cos(rotation);
        this.sin = Math.sin(rotation);
        this.amplitudes = amplitudes;
        this.phases = phases;
        double near = Double.MAX_VALUE, far = 0;
        for (int i = 0; i < POINTS; i++) {
            double angle = i * Mth.TWO_PI / POINTS;
            double r = lineRadius(angle);
            double u = r * Math.cos(angle) * aspect, v = r * Math.sin(angle);
            px[i] = u * cos - v * sin;
            pz[i] = u * sin + v * cos;
            double reach = Math.hypot(px[i], pz[i]);
            near = Math.min(near, reach);
            far = Math.max(far, reach);
        }
        this.nearest = near;
        this.furthest = far;
    }

    public static int harmonics() {
        return ORDERS.length;
    }

    /** The same line scaled by {@code factor}. */
    public RingPath scaled(double factor) {
        return new RingPath(radius * factor, aspect, Math.atan2(sin, cos), amplitudes, phases);
    }

    private double lineRadius(double angle) {
        double r = 1;
        for (int k = 0; k < ORDERS.length; k++) {
            r += amplitudes[k] * Math.sin(ORDERS[k] * angle + phases[k]);
        }
        return radius * r;
    }

    /** The closest the line comes to the centre. */
    public double nearest() {
        return nearest;
    }

    /** The furthest the line strays from the centre. */
    public double furthest() {
        return furthest;
    }

    /** The point a fraction of the way round the line, as an offset {x, z} from the centre. */
    public double[] pointAt(double fraction) {
        int i = Math.floorMod((int) Math.round(fraction * POINTS), POINTS);
        return new double[]{px[i], pz[i]};
    }

    /** Whether the offset lies inside the loop, toward the centre. */
    public boolean inside(double dx, double dz) {
        double u = (dx * cos + dz * sin) / aspect, v = -dx * sin + dz * cos;
        return Math.hypot(u, v) < lineRadius(Math.atan2(v, u));
    }

    /** Distance in blocks from the offset to the line. */
    public double distance(double dx, double dz) {
        double best = Double.MAX_VALUE;
        for (int i = 0, j = POINTS - 1; i < POINTS; j = i++) {
            double ax = px[j], az = pz[j], ex = px[i] - ax, ez = pz[i] - az;
            double t = Mth.clamp(((dx - ax) * ex + (dz - az) * ez) / (ex * ex + ez * ez), 0, 1);
            double ox = dx - ax - t * ex, oz = dz - az - t * ez;
            best = Math.min(best, ox * ox + oz * oz);
        }
        return Math.sqrt(best);
    }

    @Override
    public String toString() {
        return String.format("RingPath[radius=%.1f, aspect=%.2f, reach %.0f..%.0f]", radius, aspect, nearest,
                furthest);
    }
}
