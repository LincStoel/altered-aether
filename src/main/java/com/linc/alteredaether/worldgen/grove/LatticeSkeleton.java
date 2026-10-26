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
import com.linc.alteredaether.worldgen.grove.shape.Tube;
import net.minecraft.util.Mth;

/**
 * A lattice skeleton, after the strangler figs whose host tree rotted away inside them: a hollow cage of stems woven
 * round an empty core, spiralling both ways so they cross and fuse, open enough to see and step through. A few dead
 * limbs still reach out at the top.
 */
final class LatticeSkeleton {
    static void sketch(Sketch s) {
        int strands = s.range(8, 14);
        int base = s.ground;
        int top = s.rise(base, s.range(25, 40));
        double radius = s.range(4.0, 6.0);
        double phase = s.random.nextDouble() * Mth.TWO_PI;
        int steps = 12;
        for (int i = 0; i < strands; i++) {
            // Half wind one way and half the other, so the strands cross.
            double turns = (i % 2 == 0 ? 1 : -1) * s.range(0.5, 0.9);
            double start = phase + i * Mth.TWO_PI / strands;
            double[] xs = new double[steps + 1], ys = new double[steps + 1], zs = new double[steps + 1],
                    rs = new double[steps + 1];
            double thickness = s.range(1.0, 1.5);
            for (int k = 0; k <= steps; k++) {
                double t = k / (double) steps;
                double angle = start + turns * Mth.TWO_PI * t;
                // Flared at the foot, narrowing as it rises.
                double r = radius * (1 + 0.5 * (1 - t) * (1 - t)) * (1 - 0.35 * t);
                xs[k] = s.x + Math.cos(angle) * r;
                zs[k] = s.z + Math.sin(angle) * r;
                ys[k] = Mth.lerp(t, base - 2, top);
                rs[k] = thickness * (1.2 - 0.4 * t);
            }
            s.tube(new Tube(xs, ys, zs, rs, Part.Role.LOG, base));
        }
        int limbs = s.range(2, 4);
        for (int i = 0; i < limbs; i++) {
            double angle = s.random.nextDouble() * Mth.TWO_PI;
            double cx = s.x + Math.cos(angle) * radius * 0.65, cz = s.z + Math.sin(angle) * radius * 0.65;
            double reach = s.range(6, 11);
            s.tube(Tube.line(cx, top - 2, cz, cx + Math.cos(angle) * reach, top + s.range(2, 7) * s.v,
                    cz + Math.sin(angle) * reach, 1.1, 0.6, Part.Role.STRIPPED));
        }
        s.footRadius = radius * 1.5;
        s.point("lattice_core", s.x, base + 1, s.z);
        s.point("lattice_arch", s.x, top - 3, s.z);
    }

    private LatticeSkeleton() {
    }
}
