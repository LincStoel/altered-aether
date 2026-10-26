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
 * What lies across a break: the colossus whose fall began it, its root plate still on the island and its shattered
 * crown on the piece that broke away, hollow, so the gap can be crossed inside it; and the last roots still stretched
 * across, sagging under the gap. Across a chasm there may be two.
 */
final class BreakBridge {
    @Nullable
    static Giant sketch(TerrainProbe probe, RandomSource random, double vertical, int buildTop, BreakField.Site site,
                        double heartChance) {
        double nx = site.nx(), nz = site.nz();
        // The log lies within 25 degrees of a compass axis, so its grain shows, and no more than 40 off square across
        // the gap.
        double normal = Math.atan2(nz, nx);
        double axis = Math.round(normal / Mth.HALF_PI) * Mth.HALF_PI;
        double off = Mth.clamp(Mth.wrapDegrees((normal - axis) * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD, -0.43, 0.43);
        double heading = axis + off;
        double hx = Math.cos(heading), hz = Math.sin(heading);
        int logs = site.kind() == BreakField.Kind.CHASM && random.nextBoolean() ? 2 : 1;
        Sketch s = null;
        int placed = 0;
        for (int attempt = 0; attempt < 8 && placed < logs; attempt++) {
            // Where along the crack it crosses; a second log a way along from the first.
            double t = Mth.lerp(random.nextDouble(), -10, 10) + placed * 20 * (random.nextBoolean() ? 1 : -1);
            double qx = site.x() - nz * t, qz = site.z() + nx * t;
            double back = Mth.lerp(random.nextDouble(), 18, 28), on = site.gap() + Mth.lerp(random.nextDouble(), 12, 22);
            double rootX = qx - hx * back, rootZ = qz - hz * back, tipX = qx + hx * on, tipZ = qz + hz * on;
            int rootGround = probe.surfaceNear(Mth.floor(rootX), Mth.floor(rootZ), site.ground(), site.ground() - 30,
                    site.ground() + 30);
            int tipGround = probe.surfaceNear(Mth.floor(tipX), Mth.floor(tipZ), site.ground() - (int) site.sink(),
                    site.ground() - 50, site.ground() + 20);
            if (rootGround == Integer.MIN_VALUE || tipGround == Integer.MIN_VALUE) {
                continue;
            }
            if (s == null) {
                s = new Sketch(probe, random, vertical, buildTop, Mth.floor(qx), Mth.floor(qz), rootGround);
            }
            FallenColossus.span(s, heartChance, Mth.lerp(random.nextDouble(), 4.5, 6.5), heading, rootX, rootZ,
                    rootGround, tipX, tipZ, tipGround);
            placed++;
        }
        if (s == null) {
            return null;
        }
        // The last roots still holding on, sagging across the gap.
        int roots = (site.kind() == BreakField.Kind.CHASM ? 4 : 2) + random.nextInt(4);
        for (int i = 0; i < roots; i++) {
            double t = Mth.lerp(random.nextDouble(), -16, 16);
            double ax = site.x() - nz * t - nx * Mth.lerp(random.nextDouble(), 3, 6);
            double az = site.z() + nx * t - nz * Mth.lerp(random.nextDouble(), 3, 6);
            double t2 = t + Mth.lerp(random.nextDouble(), -4, 4);
            double far = site.gap() + Mth.lerp(random.nextDouble(), 3, 6);
            double bx = site.x() - nz * t2 + nx * far, bz = site.z() + nx * t2 + nz * far;
            int ay = probe.surfaceNear(Mth.floor(ax), Mth.floor(az), site.ground(), site.ground() - 30,
                    site.ground() + 30);
            int by = probe.surfaceNear(Mth.floor(bx), Mth.floor(bz), site.ground() - (int) site.sink(),
                    site.ground() - 50, site.ground() + 20);
            if (ay == Integer.MIN_VALUE || by == Integer.MIN_VALUE) {
                continue;
            }
            double sag = Math.min(ay, by) - Mth.lerp(random.nextDouble(), 4, 10) * Math.max(vertical, 0.7);
            s.tube(Tube.bezier(new double[]{ax, ay - 1, az},
                    new double[]{Mth.lerp(0.35, ax, bx), sag, Mth.lerp(0.35, az, bz)},
                    new double[]{Mth.lerp(0.65, ax, bx), sag, Mth.lerp(0.65, az, bz)},
                    new double[]{bx, by - 1, bz}, Mth.lerp(random.nextDouble(), 1.2, 1.7), 0.9, 10, Part.Role.LOG));
        }
        s.point("break", site.x(), site.ground(), site.z());
        return s.done(GiantType.BREAK_BRIDGE, random.nextLong());
    }

    private BreakBridge() {
    }
}
