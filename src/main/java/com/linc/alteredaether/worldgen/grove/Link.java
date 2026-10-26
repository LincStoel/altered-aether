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
import org.jetbrains.annotations.Nullable;

/**
 * What joins two neighbouring giants into one colony: surface roots snaking across the ground from one foot to the
 * other, which read as paths and tie the forest together, and now and then a canopy bridge, where two crowns' limbs
 * grew into each other and fused.
 */
final class Link {
    @Nullable
    static Giant sketch(TerrainProbe probe, double vertical, int buildTop, Giant a, Giant b, RandomSource random,
                        double bridgeChance) {
        double dx = b.x() - a.x(), dz = b.z() - a.z();
        double distance = Math.sqrt(dx * dx + dz * dz);
        double ux = dx / distance, uz = dz / distance, px = -uz, pz = ux;
        Sketch s = new Sketch(probe, random, vertical, buildTop, (a.x() + b.x()) / 2, (a.z() + b.z()) / 2,
                (a.base() + b.base()) / 2);

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
        }
        return s.wood.isEmpty() ? null : s.done(GiantType.LINK, a.palette(), random.nextLong());
    }

    private Link() {
    }
}
