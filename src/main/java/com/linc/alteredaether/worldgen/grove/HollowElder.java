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
 * The hollow elder, after the ancient yews: a ring of stems fused into one great fluted trunk, its heart long rotted
 * away into a chamber two to four storeys tall. A slit between two stems lets you in; above the chamber the stems part
 * and a shaft of light falls through. Each stem carries its own crown.
 */
final class HollowElder {
    static void sketch(Sketch s) {
        int stems = s.range(5, 9);
        double ring = s.range(5.0, 7.0), stemRadius = s.range(3.6, 5.0);
        double height = s.range(55, 85);
        double phase = s.random.nextDouble() * Mth.TWO_PI;
        int base = s.ground;
        for (int i = 0; i < stems; i++) {
            double angle = phase + i * Mth.TWO_PI / stems + s.range(-0.15, 0.15);
            double cos = Math.cos(angle), sin = Math.sin(angle);
            double splay = s.range(0.05, 0.13);
            int top = s.rise(base, height * s.range(0.8, 1.05));
            Trunk stem = s.trunk(new Trunk(s.x + cos * ring, s.z + sin * ring, base, top, stemRadius, 0.4, 1.3,
                    cos * splay, sin * splay, 3, 0.12, s.random.nextDouble() * Mth.TWO_PI, s.range(-0.6, 0.6), 0.5,
                    0.12, 18, Part.Role.LOG));
            s.crown(stem.centreX(top), top, stem.centreZ(top), s.range(13, 19), stemRadius * 0.45, cos * 0.3,
                    sin * 0.3);
            s.buttresses(stem.x(), stem.z(), stemRadius, 1, s.range(6, 10));
        }
        double sheath = ring + stemRadius * 0.75;
        s.footRadius = sheath * 1.2;

        // Low down the stems have grown into one another: a single fluted trunk, one furrow between each pair of
        // stems, that holds the chamber and roofs it over.
        int floor = base + 1;
        int storeys = s.range(2, 4);
        double chamberHeight = storeys * 4.5;
        int fused = (int) Math.round(floor + chamberHeight + s.range(8, 16) * s.v);
        s.trunk(new Trunk(s.x, s.z, base, fused, sheath, 0.8, 1.0, 0, 0, stems, 0.12, phase, 0, 0.35, 0.25, 18,
                Part.Role.LOG));
        // The chamber leaves walls at least a couple of blocks thick, even in the furrows.
        double chamberRadius = sheath * 0.88 - 1.9;
        s.carve(Carve.chamber(s.x, floor + chamberHeight * 0.45, s.z, chamberRadius, chamberHeight * 0.62,
                chamberRadius, floor));
        // A narrow shaft up through the roof to where the stems part: light falls into the hollow.
        double shaftBottom = floor + chamberHeight * 0.7;
        double shaftTop = fused + 12 * s.v;
        s.carve(Carve.chamber(s.x, (shaftBottom + shaftTop) / 2, s.z, s.range(1.1, 1.6), (shaftTop - shaftBottom) / 2,
                s.range(1.1, 1.6), floor));
        // The way in, between two stems.
        double door = phase + (s.random.nextInt(stems) + 0.5) * Mth.TWO_PI / stems;
        double out = ring + stemRadius * 1.6 + 3;
        s.carve(Carve.arch(s.x, s.z, s.x + Math.cos(door) * out, s.z + Math.sin(door) * out, floor, s.range(1.2, 1.6),
                s.range(4.2, 5.4)));

        s.point("hollow_floor", s.x, floor, s.z);
        s.point("entrance", s.x + Math.cos(door) * out, floor, s.z + Math.sin(door) * out);
    }

    private HollowElder() {
    }
}
