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

import com.linc.alteredaether.worldgen.grove.shape.Part;
import com.linc.alteredaether.worldgen.grove.shape.Trunk;
import com.linc.alteredaether.worldgen.grove.shape.Tube;
import net.minecraft.util.Mth;

/**
 * A layering colony, as old yews and banyans spread: limbs bow out and down until they touch the ground, root there,
 * and turn upward as trunks of their own. A child may layer again, so one parent stands at the heart of a ring of
 * descendants all still joined to it. The bows are thick enough to walk up into the canopy.
 */
final class BowColony {
    /** How far a colony may spread from its parent: within the grove field's reach, leaving room for crowns. */
    private static final double SPREAD = GroveField.REACH - 14;

    static void sketch(Sketch s) {
        double radius = s.range(4.5, 6.5);
        int base = s.ground;
        int top = s.rise(base, s.range(60, 80));
        Trunk parent = s.trunk(new Trunk(s.x, s.z, base, top, radius, 0.4, 1.2, s.range(-0.03, 0.03),
                s.range(-0.03, 0.03), 4, 0.1, s.random.nextDouble() * Mth.TWO_PI, s.range(-0.5, 0.5), 0.4, 0.1, 16,
                Part.Role.LOG));
        s.crown(parent.centreX(top), top, parent.centreZ(top), s.range(16, 20), radius * 0.4, 0, 0);
        s.buttresses(s.x, s.z, radius, s.range(4, 6), s.range(6, 10));
        s.footRadius = radius * 1.4;

        int bows = s.range(2, 5);
        double phase = s.random.nextDouble() * Mth.TWO_PI;
        for (int i = 0; i < bows; i++) {
            double angle = phase + i * Mth.TWO_PI / bows + s.range(-0.3, 0.3);
            double y0 = Mth.lerp(s.range(0.3, 0.5), base, top);
            bow(s, parent.centreX(y0), y0, parent.centreZ(y0), radius * 0.6, angle, s.range(18, 30),
                    s.range(2.6, 3.2), 1);
        }
    }

    /**
     * One bow from a point on a trunk, out along {@code angle}, touching down {@code distance} blocks away to raise a
     * child; at depth 1 the child may bow again.
     */
    private static void bow(Sketch s, double sx, double sy, double sz, double offset, double angle, double distance,
                            double thickness, int depth) {
        double cos = Math.cos(angle), sin = Math.sin(angle);
        double ex = sx + cos * (offset + distance), ez = sz + sin * (offset + distance);
        if (Math.hypot(ex - s.x, ez - s.z) > SPREAD) {
            return;
        }
        int landing = s.groundAt(ex, ez);
        if (landing == Integer.MIN_VALUE || Math.abs(landing - sy) > 40 * s.v + distance) {
            return;
        }
        double ox = sx + cos * offset, oz = sz + sin * offset;
        double lift = s.range(6, 12) * s.v;
        Tube limb = s.tube(Tube.bezier(new double[]{ox, sy, oz},
                new double[]{ox + cos * distance * 0.35, sy + lift, oz + sin * distance * 0.35},
                new double[]{ox + cos * distance * 0.85, landing + lift * 0.8, oz + sin * distance * 0.85},
                new double[]{ex, landing - 0.5, ez}, thickness, thickness * 0.75, 10, Part.Role.LOG));
        double[] apex = limb.at(0.45);
        s.point("bow_hang", apex[0], apex[1] - apex[3] - 1, apex[2]);

        double childRadius = depth == 1 ? s.range(2.6, 3.4) : s.range(1.8, 2.4);
        int childBase = landing;
        int childTop = s.rise(childBase, depth == 1 ? s.range(25, 45) : s.range(18, 28));
        Trunk child = s.trunk(new Trunk(ex, ez, childBase, childTop, childRadius, 0.45, 1.1, cos * 0.03, sin * 0.03,
                3, 0.08, s.random.nextDouble() * Mth.TWO_PI, s.range(-0.4, 0.4), 0.5, 0.12, 8, Part.Role.LOG));
        s.crown(child.centreX(childTop), childTop, child.centreZ(childTop), depth == 1 ? s.range(9, 13) : s.range(7, 9),
                Math.max(1, childRadius * 0.4), cos * 0.2, sin * 0.2);

        if (depth == 1 && s.random.nextDouble() < 0.5) {
            int grandchildren = s.range(1, 2);
            for (int i = 0; i < grandchildren; i++) {
                double turn = angle + s.range(-0.9, 0.9);
                double y = Mth.lerp(s.range(0.35, 0.55), childBase, childTop);
                bow(s, child.centreX(y), y, child.centreZ(y), childRadius * 0.5, turn, s.range(12, 18),
                        s.range(1.7, 2.0), 2);
            }
        }
    }

    private BowColony() {
    }
}
