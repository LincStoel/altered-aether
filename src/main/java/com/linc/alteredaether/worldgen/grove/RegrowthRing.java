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

import com.linc.alteredaether.worldgen.grove.shape.Carve;
import com.linc.alteredaether.worldgen.grove.shape.Part;
import com.linc.alteredaether.worldgen.grove.shape.Trunk;
import net.minecraft.util.Mth;

/**
 * A regrowth ring: the clone starting over. Round an old giant's rotted stump, a ring of young trunks has sprung from
 * its living roots, each with a small crown of its own; in a few centuries they will be a cathedral ring.
 */
final class RegrowthRing {
    static void sketch(Sketch s) {
        double stump = s.range(4.0, 6.0);
        int base = s.ground;
        int top = base + (int) Math.round(s.range(2, 5) * s.v);
        s.trunk(new Trunk(s.x, s.z, base, top, stump, 0.9, 1.0, 0, 0, 5, 0.1, s.random.nextDouble() * Mth.TWO_PI, 0,
                0.3, 0.4, 8, Part.Role.LOG));
        s.carve(Carve.chamber(s.x, top, s.z, stump - 1.5, top - base, stump - 1.5, base + 1));

        int young = s.range(5, 9);
        double phase = s.random.nextDouble() * Mth.TWO_PI;
        for (int i = 0; i < young; i++) {
            double angle = phase + i * Mth.TWO_PI / young + s.range(-0.15, 0.15);
            double d = stump + s.range(2.0, 5.0);
            double tx = s.x + Math.cos(angle) * d, tz = s.z + Math.sin(angle) * d;
            int foot = s.groundAt(tx, tz);
            if (foot == Integer.MIN_VALUE) {
                continue;
            }
            double radius = s.range(1.2, 2.2);
            int crownAt = s.rise(foot, s.range(15, 28));
            Trunk trunk = s.trunk(new Trunk(tx, tz, foot, crownAt, radius, 0.6, 1.0, Math.cos(angle) * 0.03,
                    Math.sin(angle) * 0.03, 0, 0, 0, 0, 0.4, 0.15, 4, Part.Role.LOG));
            s.crown(trunk.centreX(crownAt), crownAt, trunk.centreZ(crownAt), s.range(5, 8), Math.max(0.9, radius * 0.5),
                    Math.cos(angle) * 0.3, Math.sin(angle) * 0.3);
        }
        s.footRadius = stump + 4;
        s.point("stump_top", s.x, base + 1, s.z);
        int growth = s.range(2, 4);
        for (int i = 0; i < growth; i++) {
            double a = s.random.nextDouble() * Mth.TWO_PI, d = (stump - 1.5) * s.range(0.0, 0.7);
            Furnish.growth(s, s.x + Math.cos(a) * d, base, s.z + Math.sin(a) * d);
        }
    }

    private RegrowthRing() {
    }
}
