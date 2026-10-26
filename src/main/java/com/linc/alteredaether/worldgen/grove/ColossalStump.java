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
 * A colossal stump, all that is left of a giant felled or snapped off long ago: low, broad and flat-topped, its top
 * a platform ringed with growth rings, its roots spread wide over the ground. Now and then its heart is gold.
 */
final class ColossalStump {
    static void sketch(Sketch s, double heartChance) {
        double radius = s.range(7.0, 11.0);
        int base = s.ground;
        int top = base + (int) Math.round(s.range(4, 12) * s.v);
        s.trunk(new Trunk(s.x, s.z, base, top, radius, 0.95, 1.0, 0, 0, s.range(5, 8), 0.06,
                s.random.nextDouble() * Mth.TWO_PI, 0, 0.35, 0.4, 14, Part.Role.RINGS));
        if (s.random.nextDouble() < heartChance) {
            s.trunk(new Trunk(s.x, s.z, base - 4, top, radius * 0.3, 1.0, 1.0, 0, 0, 0, 0, 0, 0, 0, 0.01, 0,
                    Part.Role.HEART));
        }
        // Splinters left standing on one side of the break.
        int splinters = s.range(2, 4);
        double side = s.random.nextDouble() * Mth.TWO_PI;
        for (int i = 0; i < splinters; i++) {
            double angle = side + s.range(-0.6, 0.6);
            double d = radius * s.range(0.6, 0.9);
            double sx = s.x + Math.cos(angle) * d, sz = s.z + Math.sin(angle) * d;
            s.tube(Tube.line(sx, top - 1, sz, sx + s.range(-1, 1), top + s.range(3, 7) * s.v, sz + s.range(-1, 1),
                    s.range(1.0, 1.6), 0.4, Part.Role.STRIPPED));
        }
        s.buttresses(s.x, s.z, radius, s.range(6, 9), s.range(8, 14));
        s.footRadius = radius * 1.3;
        s.point("stump_top", s.x, top + 1, s.z);
        int growth = s.range(3, 6);
        for (int i = 0; i < growth; i++) {
            double a = s.random.nextDouble() * Mth.TWO_PI, d = radius * s.range(0.1, 0.7);
            Furnish.growth(s, s.x + Math.cos(a) * d, top, s.z + Math.sin(a) * d);
        }
        if (s.random.nextDouble() < s.secretChance * 1.5) {
            s.carve(com.linc.alteredaether.worldgen.grove.shape.Carve.chamber(s.x, top - 3, s.z, 1.6, 1.4, 1.6,
                    top - 4));
            Furnish.chest(s, s.x, top - 4, s.z, net.minecraft.core.Direction.NORTH, Furnish.SECRET, Prop.Anchor.AT);
        }
    }

    private ColossalStump() {
    }
}
