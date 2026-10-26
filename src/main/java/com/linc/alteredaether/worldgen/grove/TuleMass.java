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

import com.linc.alteredaether.worldgen.grove.shape.Blob;
import com.linc.alteredaether.worldgen.grove.shape.Carve;
import com.linc.alteredaether.worldgen.grove.shape.Part;
import com.linc.alteredaether.worldgen.grove.shape.Trunk;
import com.linc.alteredaether.worldgen.grove.shape.Tube;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/**
 * The Tule mass, after the cypress of Santa María del Tule: wider than it is tall, a knot of stout lobes grown into one
 * another, bulging with burls, folded into alcoves at the ground. Above, great limbs sprawl out sideways to hold up a
 * broad flat crown.
 */
final class TuleMass {
    static void sketch(Sketch s) {
        int lobes = s.range(4, 7);
        int base = s.ground;
        double phase = s.random.nextDouble() * Mth.TWO_PI;
        List<Trunk> trunks = new ArrayList<>();
        double spread = 0;
        for (int i = 0; i < lobes; i++) {
            double angle = phase + i * Mth.TWO_PI / lobes + s.range(-0.3, 0.3);
            double d = s.range(4.0, 8.0);
            double radius = s.range(6.0, 8.5);
            int top = s.rise(base, s.range(38, 55));
            trunks.add(s.trunk(new Trunk(s.x + Math.cos(angle) * d, s.z + Math.sin(angle) * d, base, top, radius,
                    0.55, 1.0, Math.cos(angle) * 0.04, Math.sin(angle) * 0.04, 4, 0.15,
                    s.random.nextDouble() * Mth.TWO_PI, s.range(-0.5, 0.5), 0.6, 0.15, 16, Part.Role.LOG)));
            spread = Math.max(spread, d + radius);
        }
        s.footRadius = spread;

        // Sprawling limbs, nearly level at first, rising at their ends into crowns of their own.
        int limbs = s.range(7, 11);
        for (int i = 0; i < limbs; i++) {
            Trunk from = trunks.get(i % trunks.size());
            double angle = Math.atan2(from.z() - s.z, from.x() - s.x) + s.range(-0.7, 0.7);
            double cos = Math.cos(angle), sin = Math.sin(angle);
            double y0 = Mth.lerp(s.range(0.7, 0.95), base, from.top());
            double sx = from.centreX(y0), sz = from.centreZ(y0);
            double length = s.range(18, 28);
            double ex = sx + cos * length, ez = sz + sin * length, ey = y0 + s.range(4, 12) * s.v;
            s.tube(Tube.bezier(new double[]{sx, y0, sz},
                    new double[]{sx + cos * length * 0.35, y0 + 2 * s.v, sz + sin * length * 0.35},
                    new double[]{sx + cos * length * 0.75, ey - 3 * s.v, sz + sin * length * 0.75},
                    new double[]{ex, ey, ez}, s.range(2.6, 3.4), 1.2, 8, Part.Role.LOG));
            s.crown(ex, ey, ez, s.range(8, 12), 1.1, cos * 0.4, sin * 0.4);
        }

        // Burls, on the lobes' outer faces.
        int burls = s.range(8, 14);
        for (int i = 0; i < burls; i++) {
            Trunk lobe = trunks.get(s.random.nextInt(trunks.size()));
            double y = base + s.range(2, (lobe.top() - base) * 0.7);
            double angle = Math.atan2(lobe.z() - s.z, lobe.x() - s.x) + s.range(-1.2, 1.2);
            double r = lobe.radiusAt(y) * 0.92;
            double size = s.range(1.8, 3.2);
            s.part(new Blob(lobe.centreX(y) + Math.cos(angle) * r, y, lobe.centreZ(y) + Math.sin(angle) * r, size,
                    size * s.range(0.8, 1.3), size, 0.18, s.random.nextDouble() * Mth.TWO_PI, Part.Role.BARK));
        }

        // Alcoves in the folds between neighbouring lobes.
        int alcoves = s.range(2, 3);
        for (int i = 0; i < alcoves; i++) {
            int a = s.random.nextInt(trunks.size());
            Trunk one = trunks.get(a), two = trunks.get((a + 1) % trunks.size());
            double mx = (one.x() + two.x()) / 2, mz = (one.z() + two.z()) / 2;
            double angle = Math.atan2(mz - s.z, mx - s.x);
            double d = Math.hypot(mx - s.x, mz - s.z) + Math.min(one.radius(), two.radius()) * 0.9;
            double ax = s.x + Math.cos(angle) * d, az = s.z + Math.sin(angle) * d;
            double height = s.range(3.2, 4.6);
            double width = s.range(2.2, 3.0);
            s.carve(Carve.chamber(ax, base + 1 + height * 0.4, az, width, height * 0.75, width, base + 1));
            s.point("alcove", ax, base + 1, az);
        }
        for (Trunk lobe : trunks) {
            s.buttresses(lobe.x(), lobe.z(), lobe.radius(), 1, s.range(6, 10));
        }
    }

    private TuleMass() {
    }
}
