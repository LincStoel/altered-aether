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
import com.linc.alteredaether.worldgen.grove.shape.Carve;
import com.linc.alteredaether.worldgen.grove.shape.Part;
import com.linc.alteredaether.worldgen.grove.shape.Trunk;
import com.linc.alteredaether.worldgen.grove.shape.Tube;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.List;

/**
 * The Grandfather, the Skyroot Cathedral's landmark: the ancestor of a whole clone, every kind of giant in one. Its foot
 * is a Tule-wide mass of fused lobes with a road-wide tunnel through it. Its great limbs bow down to the ground all
 * round and have risen again as a ring of children, joined back to the old trunk by fused limbs high up. Over the
 * tunnel, reached by walking up one of the bows, is a chamber lined with golden heartwood, and in it what the pilgrims
 * kept there.
 */
public final class GrandfatherTree {
    /** Draws the Grandfather standing on {@code ground} at (x, z). */
    public static Giant sketch(TerrainProbe probe, RandomSource random, double vertical, int buildTop, int x, int z,
                               int ground) {
        Sketch s = new Sketch(probe, random, vertical, buildTop, x, z, ground);
        s.tended = true;
        int base = ground;

        // The foot: fused lobes round a central column.
        int lobes = s.range(6, 8);
        double phase = random.nextDouble() * Mth.TWO_PI;
        double spread = 0;
        for (int i = 0; i < lobes; i++) {
            double angle = phase + i * Mth.TWO_PI / lobes + s.range(-0.2, 0.2);
            double d = s.range(8, 12), radius = s.range(8, 10);
            s.trunk(new Trunk(x + Math.cos(angle) * d, z + Math.sin(angle) * d, base, s.rise(base, s.range(50, 65)),
                    radius, 0.55, 1.0, Math.cos(angle) * 0.03, Math.sin(angle) * 0.03, 4, 0.14,
                    random.nextDouble() * Mth.TWO_PI, s.range(-0.5, 0.5), 0.55, 0.15, 24, Part.Role.LOG));
            spread = Math.max(spread, d + radius);
        }
        int top = s.rise(base, s.range(125, 150));
        Trunk column = s.trunk(new Trunk(x, z, base, top, 11, 0.35, 1.2, 0, 0, 7, 0.08,
                random.nextDouble() * Mth.TWO_PI, 0.4, 0.2, 0.1, 24, Part.Role.LOG));
        s.footRadius = spread;
        s.crown(x, top, z, s.range(30, 36), 3.5, 0, 0);
        for (int i = 0; i < 3; i++) {
            int y = (int) Mth.lerp(s.range(0.6, 0.8), base, top);
            s.crown(column.centreX(y), y, column.centreZ(y), s.range(18, 22), 2.6, 0, 0);
        }

        // The tunnel, straight through the foot.
        double heading = random.nextDouble() * Mth.PI;
        double hx = Math.cos(heading), hz = Math.sin(heading);
        double half = spread + 4;
        int floor = base + 1;
        s.carve(Carve.arch(x - hx * half, z - hz * half, x + hx * half, z + hz * half, floor, 3.6, 8.5));
        s.point("tunnel", x - hx * half, floor, z - hz * half);
        s.point("tunnel", x + hx * half, floor, z + hz * half);
        for (int side = -1; side <= 1; side += 2) {
            for (int k = 1; k <= 3; k++) {
                double t = side * k * half / 4;
                if (!Furnish.hanging(s, Mth.floor(x + hx * t), floor + 6, Mth.floor(z + hz * t), 1)) {
                    break;
                }
            }
        }

        // The heartwood room over the tunnel, walls of golden oak.
        int room = floor + 12;
        double roomRadius = 7.5;
        s.trunk(new Trunk(x, z, room - 3, room + 14, roomRadius + 3, 1.0, 1.0, 0, 0, 0, 0, 0, 0, 0, 0.01, 0,
                Part.Role.HEART));
        s.carve(Carve.chamber(x, room + 5, z, roomRadius, 6.5, roomRadius, room));
        Furnish.chest(s, x, room, z, Direction.NORTH, Furnish.HEARTWOOD, Prop.Anchor.AT);
        Furnish.camp(s, x + 0.5 + Math.cos(heading) * 3.5, room, z + 0.5 + Math.sin(heading) * 3.5, heading + Math.PI,
                3, true, Prop.Anchor.AT);
        s.point("heartwood", x, room, z);

        // The bows, all round; the first is the way up to the room, its doorway cut where it leaves the trunk.
        int bows = s.range(5, 7);
        double bowPhase = heading + Mth.HALF_PI;
        List<double[]> children = new ArrayList<>();
        for (int i = 0; i < bows; i++) {
            double angle = bowPhase + i * Mth.TWO_PI / bows + (i == 0 ? 0 : s.range(-0.2, 0.2));
            double cos = Math.cos(angle), sin = Math.sin(angle);
            double y0 = i == 0 ? room - 0.5 : room + s.range(-4, 8);
            double sx = x + cos * (spread - 3), sz = z + sin * (spread - 3);
            double distance = s.range(26, 34);
            double ex = sx + cos * distance, ez = sz + sin * distance;
            int landing = s.groundAt(ex, ez);
            if (landing == Integer.MIN_VALUE) {
                continue;
            }
            double lift = s.range(6, 10) * vertical;
            Tube bow = s.tube(Tube.bezier(new double[]{sx, y0, sz},
                    new double[]{sx + cos * distance * 0.35, y0 + lift, sz + sin * distance * 0.35},
                    new double[]{sx + cos * distance * 0.85, landing + lift * 0.8, sz + sin * distance * 0.85},
                    new double[]{ex, landing - 0.5, ez}, 3.6, 2.8, 12, Part.Role.LOG));
            double[] apex = bow.at(0.45);
            int under = (int) Math.floor(apex[1] - apex[3] - 1);
            Furnish.hanging(s, Mth.floor(apex[0]), under, Mth.floor(apex[2]), s.range(3, 6));
            if (i == 0) {
                // The doorway, from the bow's back into the room.
                double out = spread + 2;
                s.carve(Carve.arch(x, z, x + cos * out, z + sin * out, room, 1.6, 4.5));
            }
            int childTop = s.rise(landing, s.range(45, 65));
            Trunk child = s.trunk(new Trunk(ex, ez, landing - 1, childTop, s.range(3.5, 4.5), 0.45, 1.1, cos * 0.03,
                    sin * 0.03, 4, 0.1, random.nextDouble() * Mth.TWO_PI, s.range(-0.4, 0.4), 0.45, 0.12, 10,
                    Part.Role.LOG));
            s.crown(child.centreX(childTop), childTop, child.centreZ(childTop), s.range(11, 14), 1.6, cos * 0.3,
                    sin * 0.3);
            children.add(new double[]{child.centreX(childTop - 12), childTop - 12, child.centreZ(childTop - 12)});
        }
        // The children's limbs grown back into the old trunk.
        for (double[] c : children) {
            double cy = c[1];
            double tx = column.centreX(cy), tz = column.centreZ(cy);
            s.tube(Tube.bezier(c, new double[]{Mth.lerp(0.33, c[0], tx), cy - 3 * vertical, Mth.lerp(0.33, c[2], tz)},
                    new double[]{Mth.lerp(0.66, c[0], tx), cy - 3 * vertical, Mth.lerp(0.66, c[2], tz)},
                    new double[]{tx, cy + 4, tz}, 1.8, 2.4, 10, Part.Role.LOG));
        }
        for (int i = 0; i < lobes; i++) {
            double angle = phase + (i + 0.5) * Mth.TWO_PI / lobes;
            if (Math.abs(Math.cos(angle - heading)) > 0.85) {
                continue;
            }
            s.buttresses(x + Math.cos(angle) * spread * 0.6, z + Math.sin(angle) * spread * 0.6, 8, 1,
                    s.range(10, 16));
        }
        return s.done(GiantType.GRANDFATHER, random.nextLong());
    }

    private GrandfatherTree() {
    }
}
