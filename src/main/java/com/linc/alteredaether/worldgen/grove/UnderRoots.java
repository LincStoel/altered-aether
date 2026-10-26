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

import com.linc.alteredaether.worldgen.grove.shape.Bounds;
import com.linc.alteredaether.worldgen.grove.shape.Part;
import com.linc.alteredaether.worldgen.grove.shape.Tube;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * The great roots a giant drives down through its island, which break out of the island's underside and hang into
 * the void: thick where they leave the rock, plunging or arching out before they fall, now and then forking. Inside
 * the island they only fill air, so all that shows is what comes out below (and a root crossing a cave).
 */
final class UnderRoots {
    /** How far below the ground the underside is looked for. */
    private static final int DEPTH = 120;
    /** How much void a root needs under the island, so it doesn't come out into a cave. */
    private static final int CLEARANCE = 16;

    /**
     * Adds up to {@code count} roots to a giant. A root that would come out nowhere, or reach further than
     * {@code reach} from the giant's centre, is left out. Draws from its own {@code random}, so the rest of the
     * giant's plan is unchanged.
     */
    static void sketch(Sketch s, RandomSource random, int count, double reach) {
        double phase = random.nextDouble() * Mth.TWO_PI;
        for (int i = 0; i < count; i++) {
            double angle = phase + i * Mth.TWO_PI / count + range(random, -0.6, 0.6);
            double out = Math.max(s.footRadius, 3) * range(random, 0.5, 0.9) + range(random, 2.0, 9.0);
            double ex = s.x + Math.cos(angle) * out, ez = s.z + Math.sin(angle) * out;
            int under = underside(s, Mth.floor(ex), Mth.floor(ez));
            if (under == Integer.MIN_VALUE) {
                continue;
            }
            double radius = range(random, 2.6, 3.8) * Math.max(1, Math.min(1.5, s.footRadius / 8));
            Tube root = root(s, random, ex, under, ez, angle, radius, range(random, 16.0, 32.0));
            if (fits(s, root.bounds(), reach)) {
                s.tube(root);
                double[] tip = root.at(1);
                s.point("under_root", tip[0], tip[1], tip[2]);
                // A side root off the upper half, smaller and shorter.
                if (random.nextDouble() < 0.5) {
                    double[] p = root.at(range(random, 0.3, 0.55));
                    double turn = angle + (random.nextBoolean() ? 1 : -1) * range(random, 0.7, 1.4);
                    Tube fork = root(s, random, p[0], p[1], p[2], turn, p[3] * 0.6, range(random, 8.0, 16.0));
                    if (fits(s, fork.bounds(), reach)) {
                        s.tube(fork);
                    }
                }
            }
        }
    }

    /**
     * One root leaving the rock at (x, y, z) heading out along {@code angle}: it starts a few blocks up inside the
     * island, then either plunges, bowing a little outward, or arches out nearly level before it falls.
     */
    private static Tube root(Sketch s, RandomSource random, double x, double y, double z, double angle, double radius,
                             double drop) {
        double ux = Math.cos(angle), uz = Math.sin(angle), px = -uz, pz = ux;
        double fall = drop * s.v, lateral = range(random, -3.0, 3.0);
        boolean arching = random.nextDouble() < 0.5;
        double out = arching ? range(random, 8.0, 14.0) : range(random, 3.0, 8.0);
        double[] p0 = {x, y + 1, z};
        double[] p1 = arching ? new double[]{x + ux * out * 0.7, y - fall * 0.05, z + uz * out * 0.7}
                : new double[]{x + ux * out * 0.3, y - fall * 0.35, z + uz * out * 0.3};
        double[] p2 = arching ? new double[]{x + ux * out * 1.05 + px * lateral * 0.5, y - fall * 0.45,
                z + uz * out * 1.05 + pz * lateral * 0.5}
                : new double[]{x + ux * out * 0.8 + px * lateral * 0.5, y - fall * 0.7, z + uz * out * 0.8 + pz * lateral * 0.5};
        double[] p3 = {x + ux * out + px * lateral, y - fall, z + uz * out + pz * lateral};
        int steps = 10;
        // The first vertex is buried in the island, so the root grows out of it rather than sitting under it.
        double[] xs = new double[steps + 2], ys = new double[steps + 2], zs = new double[steps + 2],
                rs = new double[steps + 2];
        xs[0] = x - ux * 2;
        ys[0] = y + 7 * s.v;
        zs[0] = z - uz * 2;
        rs[0] = radius * 1.15;
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps, u = 1 - t;
            double a = u * u * u, b = 3 * u * u * t, c = 3 * u * t * t, d = t * t * t;
            xs[i + 1] = a * p0[0] + b * p1[0] + c * p2[0] + d * p3[0];
            ys[i + 1] = a * p0[1] + b * p1[1] + c * p2[1] + d * p3[1];
            zs[i + 1] = a * p0[2] + b * p1[2] + c * p2[2] + d * p3[2];
            // Keeps its girth well out of the rock, then thins to a strand.
            rs[i + 1] = Mth.lerp(Math.pow(t, 1.6), radius, 0.7);
        }
        return new Tube(xs, ys, zs, rs, Part.Role.LOG, Integer.MAX_VALUE);
    }

    /**
     * The lowest solid block of the island under a column, where it opens onto at least {@link #CLEARANCE} blocks of
     * void; MIN_VALUE if there is none within reach. Steps down while solid, then bisects to the exact block.
     */
    private static int underside(Sketch s, int x, int z) {
        int top = s.groundAt(x + 0.5, z + 0.5);
        if (top == Integer.MIN_VALUE) {
            return Integer.MIN_VALUE;
        }
        int floor = Math.max(s.probe.minY(), top - (int) Math.round(DEPTH * s.v));
        int step = 4;
        int solidY = top;
        for (int y = top - step; y >= floor; y -= step) {
            if (!s.probe.solid(x, y, z)) {
                int openY = y;
                while (solidY - openY > 1) {
                    int mid = (solidY + openY) >>> 1;
                    if (s.probe.solid(x, mid, z)) {
                        solidY = mid;
                    } else {
                        openY = mid;
                    }
                }
                for (int d = 2; d <= CLEARANCE; d += 3) {
                    if (s.probe.solid(x, solidY - d, z)) {
                        return Integer.MIN_VALUE;
                    }
                }
                return solidY;
            }
            solidY = y;
        }
        return Integer.MIN_VALUE;
    }

    /** Whether a root keeps within the giant's reach, horizontally. */
    private static boolean fits(Sketch s, Bounds b, double reach) {
        return s.x - b.minX() <= reach && b.maxX() - s.x <= reach && s.z - b.minZ() <= reach
                && b.maxZ() - s.z <= reach;
    }

    private static double range(RandomSource random, double min, double max) {
        return Mth.lerp(random.nextDouble(), min, max);
    }

    private UnderRoots() {
    }
}
