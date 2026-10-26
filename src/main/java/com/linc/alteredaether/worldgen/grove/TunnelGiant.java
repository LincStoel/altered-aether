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
 * The tunnel giant, after the sequoias cut through for wagons: one colossal deeply furrowed column with an arched
 * passage straight through its foot, wide enough to ride through. Its crown sits far overhead, with a few great limbs
 * breaking out below it.
 */
final class TunnelGiant {
    static void sketch(Sketch s) {
        double radius = s.range(7.0, 9.0);
        int base = s.ground;
        int top = s.rise(base, s.range(75, 105));
        Trunk trunk = s.trunk(new Trunk(s.x, s.z, base, top, radius, 0.3, 1.1, s.range(-0.02, 0.02),
                s.range(-0.02, 0.02), s.range(5, 7), 0.09, s.random.nextDouble() * Mth.TWO_PI, s.range(-0.3, 0.3),
                0.45, 0.1, 20, Part.Role.LOG));
        s.footRadius = radius * 1.3;

        double heading = s.random.nextDouble() * Mth.PI;
        double hx = Math.cos(heading), hz = Math.sin(heading);
        double half = radius * 1.7 + 3;
        // A level floor: the lowest of the ground at the two mouths and the middle, so neither mouth is a step up.
        int floor = Math.min(s.ground, Math.min(groundOr(s, s.x - hx * half, s.z - hz * half),
                groundOr(s, s.x + hx * half, s.z + hz * half))) + 1;
        s.carve(Carve.arch(s.x - hx * half, s.z - hz * half, s.x + hx * half, s.z + hz * half, floor,
                s.range(2.6, 3.2), s.range(6.0, 8.0)));

        s.crown(trunk.centreX(top), top, trunk.centreZ(top), s.range(18, 24), radius * 0.35, 0, 0);
        int limbs = s.range(2, 3);
        for (int i = 0; i < limbs; i++) {
            int y = (int) Mth.lerp(s.range(0.6, 0.8), base, top);
            s.crown(trunk.centreX(y), y, trunk.centreZ(y), s.range(12, 16), radius * 0.25, 0, 0);
        }
        s.buttresses(s.x, s.z, radius, s.range(7, 10), s.range(7, 12), heading, 0.45);

        s.point("tunnel", s.x - hx * half, floor, s.z - hz * half);
        s.point("tunnel", s.x + hx * half, floor, s.z + hz * half);
    }

    private static int groundOr(Sketch s, double px, double pz) {
        int g = s.groundAt(px, pz);
        return g == Integer.MIN_VALUE ? s.ground : g;
    }

    private TunnelGiant() {
    }
}
