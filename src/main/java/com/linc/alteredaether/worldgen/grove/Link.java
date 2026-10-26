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
package com.linc.alteredaether.worldgen.grove;

import com.linc.alteredaether.worldgen.TerrainProbe;
import com.linc.alteredaether.worldgen.grove.shape.Part;
import com.linc.alteredaether.worldgen.grove.shape.Tube;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * What joins two neighbouring giants into one colony: surface roots snaking across the ground from one foot to the
 * other, which read as paths and tie the forest together, and now and then a canopy bridge, where two crowns' limbs
 * grew into each other and fused.
 */
final class Link {
    @Nullable
    static Giant sketch(TerrainProbe probe, double vertical, int buildTop, Giant a, Giant b, RandomSource random,
                        double bridgeChance, double tendedFraction) {
        double dx = b.x() - a.x(), dz = b.z() - a.z();
        double distance = Math.sqrt(dx * dx + dz * dz);
        double ux = dx / distance, uz = dz / distance, px = -uz, pz = ux;
        Sketch s = new Sketch(probe, random, vertical, buildTop, (a.x() + b.x()) / 2, (a.z() + b.z()) / 2,
                (a.base() + b.base()) / 2);
        s.palette = a.palette();

        int roots = random.nextDouble() < 0.5 ? 2 : 1;
        for (int r = 0; r < roots; r++) {
            double side0 = s.range(-4.0, 4.0), side1 = s.range(-4.0, 4.0);
            double x0 = a.x() + ux * a.footRadius() * 0.8 + px * side0, z0 = a.z() + uz * a.footRadius() * 0.8 + pz * side0;
            double x1 = b.x() - ux * b.footRadius() * 0.8 + px * side1, z1 = b.z() - uz * b.footRadius() * 0.8 + pz * side1;
            double length = Math.hypot(x1 - x0, z1 - z0);
            int n = Math.max(3, (int) (length / 5) + 1);
            double[] xs = new double[n], ys = new double[n], zs = new double[n], rs = new double[n];
            double wander = s.range(-3.0, 3.0), waves = s.range(1.0, 2.5);
            double thickness = s.range(1.4, 2.0);
            boolean ok = true;
            for (int k = 0; k < n && ok; k++) {
                double t = k / (double) (n - 1);
                double sway = Math.sin(t * Math.PI * waves) * wander;
                xs[k] = Mth.lerp(t, x0, x1) + px * sway;
                zs[k] = Mth.lerp(t, z0, z1) + pz * sway;
                int g = s.groundAt(xs[k], zs[k]);
                // A root can't cross a void between islands.
                ok = g != Integer.MIN_VALUE;
                ys[k] = g + 0.2;
                rs[k] = thickness * (0.8 + 0.2 * Math.sin(t * Math.PI));
            }
            if (ok) {
                s.tube(new Tube(xs, ys, zs, rs, Part.Role.LOG, Integer.MIN_VALUE));
            }
        }

        boolean tended = random.nextDouble() < tendedFraction;
        if (random.nextDouble() < (bridgeChance > 0 ? 0.65 : 0.45)) {
            trail(s, a, b, tended);
        }

        if (random.nextDouble() < bridgeChance && a.crown() != Integer.MIN_VALUE && b.crown() != Integer.MIN_VALUE
                && Math.abs(a.crown() - b.crown()) < 30 * vertical) {
            double y = Math.min(a.crown(), b.crown()) - s.range(8, 14) * vertical;
            double sag = s.range(2, 5) * vertical;
            double ax = a.x() + ux * 3, az = a.z() + uz * 3, bx = b.x() - ux * 3, bz = b.z() - uz * 3;
            Tube bridge = s.tube(Tube.bezier(new double[]{ax, y, az},
                    new double[]{Mth.lerp(0.33, ax, bx), y - sag, Mth.lerp(0.33, az, bz)},
                    new double[]{Mth.lerp(0.66, ax, bx), y - sag, Mth.lerp(0.66, az, bz)},
                    new double[]{bx, y, bz}, s.range(1.6, 2.2), s.range(1.6, 2.2), 10, Part.Role.LOG));
            int twigs = s.range(3, 6);
            for (int i = 0; i < twigs; i++) {
                double[] p = bridge.at(s.range(0.15, 0.85));
                s.twig(p[0], p[1], p[2], px * s.range(-1.0, 1.0), 1, pz * s.range(-1.0, 1.0), s.range(2.0, 3.5));
            }
            double[] mid = bridge.at(0.5);
            s.point("bridge", mid[0], mid[1], mid[2]);
            for (double t : new double[]{0.3, 0.5, 0.7}) {
                double[] p = bridge.at(t);
                int under = (int) Math.floor(p[1] - p[3] - 1);
                if (tended) {
                    Furnish.hanging(s, Mth.floor(p[0]), under, Mth.floor(p[2]), s.range(2, 6));
                } else {
                    Furnish.glowVine(s, Mth.floor(p[0]), under, Mth.floor(p[2]));
                }
            }
        }
        return s.wood.isEmpty() && s.paths.isEmpty() ? null : s.done(GiantType.LINK, random.nextLong());
    }

    /**
     * A trail from one giant to the other, beside their roots: trodden bare and lit along its length if it is still
     * tended, overgrown with bare posts if not. A trail to a tunnel giant runs on through its tunnel.
     */
    private static void trail(Sketch s, Giant a, Giant b, boolean tended) {
        double dx = b.x() - a.x(), dz = b.z() - a.z();
        double distance = Math.sqrt(dx * dx + dz * dz);
        double ux = dx / distance, uz = dz / distance, px = -uz, pz = ux;
        double side = (s.random.nextBoolean() ? 1 : -1) * s.range(5.0, 7.0);
        List<double[]> points = new ArrayList<>();
        ends(points, a, b.x(), b.z(), ux, uz, side, px, pz, false);
        double x0 = points.getLast()[0], z0 = points.getLast()[1];
        List<double[]> tail = new ArrayList<>();
        ends(tail, b, a.x(), a.z(), -ux, -uz, side, px, pz, true);
        double x1 = tail.getFirst()[0], z1 = tail.getFirst()[1];
        double length = Math.hypot(x1 - x0, z1 - z0);
        int n = Math.max(2, (int) (length / 8));
        double sway = s.range(-3.0, 3.0), waves = s.range(0.8, 2.0);
        for (int k = 1; k < n; k++) {
            double t = k / (double) n;
            double w = Math.sin(t * Math.PI * waves) * sway;
            points.add(new double[]{Mth.lerp(t, x0, x1) + px * w, Mth.lerp(t, z0, z1) + pz * w});
        }
        points.addAll(tail);
        double[] xs = new double[points.size()], zs = new double[points.size()];
        for (int i = 0; i < points.size(); i++) {
            xs[i] = points.get(i)[0];
            zs[i] = points.get(i)[1];
        }
        s.path(new PathStroke(xs, zs, s.range(1.3, 1.8), tended ? 0.95 : 0.55, s.random.nextLong()));

        // Lamps every ten blocks or so, on alternate sides, outside the tunnels.
        double walked = 0, next = s.range(4, 8);
        int flip = 1;
        for (int i = 0; i + 1 < xs.length; i++) {
            double segment = Math.hypot(xs[i + 1] - xs[i], zs[i + 1] - zs[i]);
            while (walked + segment >= next) {
                double t = (next - walked) / segment;
                double lx = Mth.lerp(t, xs[i], xs[i + 1]), lz = Mth.lerp(t, zs[i], zs[i + 1]);
                double sx = -(zs[i + 1] - zs[i]) / segment, sz = (xs[i + 1] - xs[i]) / segment;
                double ox = lx + sx * flip * 2.8, oz = lz + sz * flip * 2.8;
                if (!inside(a, ox, oz) && !inside(b, ox, oz)) {
                    int g = s.groundAt(ox, oz);
                    if (g != Integer.MIN_VALUE) {
                        Furnish.lamp(s, ox, g, oz, tended);
                    }
                }
                flip = -flip;
                next += s.range(9, 13);
            }
            walked += segment;
        }
    }

    /** Within a giant's foot, where no lamp should stand. */
    private static boolean inside(Giant g, double x, double z) {
        return Math.hypot(x - g.x(), z - g.z()) < g.footRadius() + 2;
    }

    /**
     * The trail's end at a giant, toward the other: the near edge of its foot, or for a tunnel giant both mouths of
     * its tunnel, far then near (or near then far, {@code reversed}, at the trail's other end).
     */
    private static void ends(List<double[]> out, Giant g, double ox, double oz, double ux, double uz, double side,
                             double px, double pz, boolean reversed) {
        List<BlockPos> mouths = g.points("tunnel");
        if (mouths.size() == 2) {
            BlockPos m0 = mouths.get(0), m1 = mouths.get(1);
            boolean firstNear = Math.hypot(m0.getX() - ox, m0.getZ() - oz) < Math.hypot(m1.getX() - ox, m1.getZ() - oz);
            BlockPos near = firstNear ? m0 : m1, far = firstNear ? m1 : m0;
            double[] f = {far.getX() + 0.5, far.getZ() + 0.5}, n = {near.getX() + 0.5, near.getZ() + 0.5};
            if (reversed) {
                out.add(n);
                out.add(f);
            } else {
                out.add(f);
                out.add(n);
            }
            return;
        }
        double reach = g.footRadius() + 2;
        out.add(new double[]{g.x() + ux * reach + px * side * 0.3, g.z() + uz * reach + pz * side * 0.3});
    }

    private Link() {
    }
}
