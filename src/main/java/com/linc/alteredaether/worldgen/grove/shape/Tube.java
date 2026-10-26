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
package com.linc.alteredaether.worldgen.grove.shape;

import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

/**
 * A limb, root or bow: a polyline swept by a radius that varies along it, with rounded joints and ends. Curves are
 * sampled into short straight segments; the grain follows whichever segment a point is nearest.
 */
public final class Tube implements Part {
    private final double[] xs, ys, zs, rs;
    /** Each segment's bounding sphere, for skipping segments far from a point: centre and squared radius. */
    private final double[] mx, my, mz, reach2;
    private final Role role;
    private final int foot;
    private final Bounds bounds;

    public Tube(double[] xs, double[] ys, double[] zs, double[] rs, Role role, int foot) {
        if (xs.length < 2 || ys.length != xs.length || zs.length != xs.length || rs.length != xs.length) {
            throw new IllegalArgumentException("a tube needs at least two matching vertices");
        }
        this.xs = xs;
        this.ys = ys;
        this.zs = zs;
        this.rs = rs;
        this.role = role;
        this.foot = foot;
        Bounds b = Bounds.EMPTY;
        for (int i = 0; i < xs.length; i++) {
            b = b.union(Bounds.around(xs[i], ys[i], zs[i], rs[i], rs[i], rs[i]));
        }
        this.bounds = b;
        int n = xs.length - 1;
        mx = new double[n];
        my = new double[n];
        mz = new double[n];
        reach2 = new double[n];
        for (int i = 0; i < n; i++) {
            mx[i] = (xs[i] + xs[i + 1]) / 2;
            my[i] = (ys[i] + ys[i + 1]) / 2;
            mz[i] = (zs[i] + zs[i + 1]) / 2;
            double half = Math.sqrt(sq(xs[i + 1] - xs[i]) + sq(ys[i + 1] - ys[i]) + sq(zs[i + 1] - zs[i])) / 2;
            double reach = half + Math.max(rs[i], rs[i + 1]) + 1;
            reach2[i] = reach * reach;
        }
    }

    private static double sq(double v) {
        return v * v;
    }

    /** A straight tube from one point to another, tapering from {@code r0} to {@code r1}. */
    public static Tube line(double x0, double y0, double z0, double x1, double y1, double z1, double r0, double r1,
                            Role role) {
        return new Tube(new double[]{x0, x1}, new double[]{y0, y1}, new double[]{z0, z1}, new double[]{r0, r1}, role,
                Integer.MIN_VALUE);
    }

    /**
     * A cubic Bézier tube through four control points (x, y, z triples), tapering from {@code r0} to {@code r1},
     * sampled into {@code steps} segments.
     */
    public static Tube bezier(double[] p0, double[] p1, double[] p2, double[] p3, double r0, double r1, int steps,
                              Role role) {
        int n = steps + 1;
        double[] xs = new double[n], ys = new double[n], zs = new double[n], rs = new double[n];
        for (int i = 0; i < n; i++) {
            double t = i / (double) steps, u = 1 - t;
            double a = u * u * u, b = 3 * u * u * t, c = 3 * u * t * t, d = t * t * t;
            xs[i] = a * p0[0] + b * p1[0] + c * p2[0] + d * p3[0];
            ys[i] = a * p0[1] + b * p1[1] + c * p2[1] + d * p3[1];
            zs[i] = a * p0[2] + b * p1[2] + c * p2[2] + d * p3[2];
            rs[i] = r0 + (r1 - r0) * t;
        }
        return new Tube(xs, ys, zs, rs, role, Integer.MIN_VALUE);
    }

    @Override
    public Bounds bounds() {
        return bounds;
    }

    @Override
    public Role role() {
        return role;
    }

    @Override
    public int foot() {
        return foot;
    }

    @Override
    public double margin(double x, double y, double z) {
        double best = -Double.MAX_VALUE;
        for (int i = 0; i + 1 < xs.length; i++) {
            best = Math.max(best, segmentMargin(i, x, y, z));
        }
        return best;
    }

    @Nullable
    @Override
    public Direction.Axis axis(double x, double y, double z) {
        int bestSegment = 0;
        double best = -Double.MAX_VALUE;
        for (int i = 0; i + 1 < xs.length; i++) {
            double m = segmentMargin(i, x, y, z);
            if (m > best) {
                best = m;
                bestSegment = i;
            }
        }
        int i = bestSegment;
        return Part.grain(xs[i + 1] - xs[i], ys[i + 1] - ys[i], zs[i + 1] - zs[i]);
    }

    private double segmentMargin(int i, double x, double y, double z) {
        if (sq(x - mx[i]) + sq(y - my[i]) + sq(z - mz[i]) > reach2[i]) {
            return -1;
        }
        double ax = xs[i], ay = ys[i], az = zs[i];
        double dx = xs[i + 1] - ax, dy = ys[i + 1] - ay, dz = zs[i + 1] - az;
        double length2 = dx * dx + dy * dy + dz * dz;
        double t = length2 < 1e-9 ? 0 : ((x - ax) * dx + (y - ay) * dy + (z - az) * dz) / length2;
        t = t < 0 ? 0 : t > 1 ? 1 : t;
        double px = ax + dx * t - x, py = ay + dy * t - y, pz = az + dz * t - z;
        double r = rs[i] + (rs[i + 1] - rs[i]) * t;
        return r - Math.sqrt(px * px + py * py + pz * pz);
    }

    /** The point a fraction of the way along the tube, by vertex count: for hanging things off a limb. */
    public double[] at(double fraction) {
        double f = Math.max(0, Math.min(1, fraction)) * (xs.length - 1);
        int i = Math.min((int) f, xs.length - 2);
        double t = f - i;
        return new double[]{xs[i] + (xs[i + 1] - xs[i]) * t, ys[i] + (ys[i + 1] - ys[i]) * t,
                zs[i] + (zs[i + 1] - zs[i]) * t, rs[i] + (rs[i + 1] - rs[i]) * t};
    }
}
