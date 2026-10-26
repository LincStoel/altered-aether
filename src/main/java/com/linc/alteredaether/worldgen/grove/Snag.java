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
import com.linc.alteredaether.worldgen.grove.shape.Tube;
import net.minecraft.util.Mth;

/**
 * A snag: a giant that died standing. Its bark has sloughed away, its top has broken off in jagged spires, and it
 * has rotted hollow from the top down, so a rot hole at its foot opens into a chimney you can climb all the way up.
 */
final class Snag {
    static void sketch(Sketch s) {
        double radius = s.range(4.0, 6.5);
        int base = s.ground;
        int top = s.rise(base, s.range(30, 55));
        Trunk trunk = s.trunk(new Trunk(s.x, s.z, base, top, radius, 0.7, 1.0, s.range(-0.03, 0.03),
                s.range(-0.03, 0.03), 4, 0.1, s.random.nextDouble() * Mth.TWO_PI, s.range(-0.4, 0.4), 0.4, 0.12, 12,
                Part.Role.STRIPPED));
        s.footRadius = radius * 1.3;
        // The broken top: spires of the shell standing up round the rim.
        int spires = s.range(3, 5);
        double phase = s.random.nextDouble() * Mth.TWO_PI;
        double rim = trunk.radiusAt(top) - 0.8;
        for (int i = 0; i < spires; i++) {
            double angle = phase + i * Mth.TWO_PI / spires + s.range(-0.3, 0.3);
            double sx = trunk.centreX(top) + Math.cos(angle) * rim, sz = trunk.centreZ(top) + Math.sin(angle) * rim;
            s.trunk(new Trunk(sx, sz, top - 6, s.rise(top, s.range(3, 11)), s.range(1.2, 1.8), 0.2, 1.0, 0, 0, 0, 0,
                    0, 0, 0, 0.01, 0, Part.Role.STRIPPED));
        }
        // Dead limb stubs.
        int stubs = s.range(2, 4);
        for (int i = 0; i < stubs; i++) {
            int y = (int) Mth.lerp(s.range(0.4, 0.85), base, top);
            double angle = s.random.nextDouble() * Mth.TWO_PI;
            double cx = trunk.centreX(y), cz = trunk.centreZ(y);
            double reach = trunk.radiusAt(y) + s.range(3, 7);
            s.tube(Tube.line(cx, y, cz, cx + Math.cos(angle) * reach, y + s.range(1, 4), cz + Math.sin(angle) * reach,
                    s.range(1.0, 1.6), 0.7, Part.Role.STRIPPED));
        }
        // Hollow from the top down to the ground, with a rot hole at the foot.
        int floor = base + 1;
        double inner = Math.max(1.6, radius * 0.7 - 1.5);
        s.carve(Carve.chamber(s.x, (floor + top + 12) / 2.0, s.z, inner, (top + 12 - floor) / 2.0, inner, floor));
        double door = s.random.nextDouble() * Mth.TWO_PI;
        double out = radius * 1.6 + 2;
        s.carve(Carve.arch(s.x, s.z, s.x + Math.cos(door) * out, s.z + Math.sin(door) * out, floor, s.range(1.1, 1.4),
                s.range(3.2, 4.2)));
        s.buttresses(s.x, s.z, radius, s.range(3, 5), s.range(4, 7));
        s.point("hollow_floor", s.x, floor, s.z);
        s.point("snag_top", trunk.centreX(top), top + 1, trunk.centreZ(top));
    }

    private Snag() {
    }
}
