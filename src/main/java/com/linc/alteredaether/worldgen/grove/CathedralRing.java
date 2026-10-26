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
 * A cathedral ring, the redwoods' family circle: the ancestor died and rotted away, and the shoots round its stump grew
 * into a ring of tall trunks about a sunlit clearing. They lean a little outward and hold their crowns away from the
 * middle, so the clearing stays open to the sky. Often the ancestor's hollow stump still stands at the centre.
 */
final class CathedralRing {
    static void sketch(Sketch s) {
        int trunks = s.range(6, 10);
        double ring = s.range(10, 16);
        double phase = s.random.nextDouble() * Mth.TWO_PI;
        for (int i = 0; i < trunks; i++) {
            double angle = phase + i * Mth.TWO_PI / trunks + s.range(-0.12, 0.12);
            double cos = Math.cos(angle), sin = Math.sin(angle);
            double tx = s.x + cos * ring * s.range(0.9, 1.1), tz = s.z + sin * ring * s.range(0.9, 1.1);
            int foot = s.groundAt(tx, tz);
            if (foot == Integer.MIN_VALUE) {
                continue;
            }
            double radius = s.range(2.6, 4.0);
            double lean = s.range(0.04, 0.1);
            int top = s.rise(foot, s.range(55, 80));
            Trunk trunk = s.trunk(new Trunk(tx, tz, foot, top, radius, 0.4, 1.2, cos * lean, sin * lean, 3, 0.1,
                    s.random.nextDouble() * Mth.TWO_PI, s.range(-0.5, 0.5), 0.45, 0.1, 10, Part.Role.LOG));
            s.crown(trunk.centreX(top), top, trunk.centreZ(top), s.range(10, 14), radius * 0.4, cos * 0.6, sin * 0.6);
            s.buttresses(tx, tz, radius, 2, s.range(4, 7));
        }
        s.footRadius = ring + 4;

        if (s.random.nextDouble() < 0.6) {
            double radius = s.range(3.5, 5.5);
            int top = s.ground + s.range(3, 7);
            s.trunk(new Trunk(s.x, s.z, s.ground, top, radius, 0.85, 1.0, 0, 0, 5, 0.12,
                    s.random.nextDouble() * Mth.TWO_PI, 0, 0.3, 0.3, 6, Part.Role.LOG));
            // Rotted out from the top, leaving a rim of heartwood.
            s.carve(Carve.chamber(s.x, top, s.z, radius - 1.6, top - s.ground, radius - 1.6, s.ground + 1));
        }
        s.point("clearing", s.x, s.ground + 1, s.z);
    }

    private CathedralRing() {
    }
}
