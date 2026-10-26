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
import com.linc.alteredaether.worldgen.grove.shape.Tube;
import net.minecraft.util.Mth;

/**
 * A fallen colossus, after the downed sequoias: a trunk fifty to eighty-five blocks long lying where it fell. At one end
 * its root plate stands on edge, a wall of roots still clutching the earth they tore up, over the crater they left;
 * at the other the crown end is a mess of splinters. Much of it has rotted hollow inside, a long hall you can walk
 * along, and its back is a nursery for young growth.
 */
final class FallenColossus {
    static void sketch(Sketch s, double heartChance) {
        double length = s.range(50, 85);
        double radius = s.range(5.0, 8.0);
        // Within 25 degrees of a compass axis, so the log's grain lies along it rather than turning to bark.
        double heading = s.random.nextInt(4) * Mth.HALF_PI + s.range(-0.43, 0.43);
        double hx = Math.cos(heading), hz = Math.sin(heading);
        // The root end, with its plate and crater, sits nearer the centre than the tapering crown end.
        double rootX = s.x - hx * length * 0.4, rootZ = s.z - hz * length * 0.4;
        double tipX = s.x + hx * length * 0.6, tipZ = s.z + hz * length * 0.6;
        int rootGround = groundOr(s, rootX, rootZ), tipGround = groundOr(s, tipX, tipZ);
        // Settled a little into the ground, the crown end lower as it tapers.
        double rootY = rootGround + radius * 0.7, tipY = tipGround + radius * 0.45;
        s.tube(Tube.line(rootX, rootY, rootZ, tipX, tipY, tipZ, radius, radius * 0.6, Part.Role.LOG));
        s.footRadius = radius;

        // The hollow: rotted out from the crown end back toward the roots, floored flat.
        double from = s.range(0.15, 0.35), to = 1.02;
        // Walls about two blocks thick all along, as the log tapers.
        double inner = radius * (1 - 0.4 * from) - 2, innerTip = radius * 0.6 - 1.8;
        double ax = Mth.lerp(from, rootX, tipX), ay = Mth.lerp(from, rootY, tipY), az = Mth.lerp(from, rootZ, tipZ);
        double bx = Mth.lerp(to, rootX, tipX), by = Mth.lerp(to, rootY, tipY), bz = Mth.lerp(to, rootZ, tipZ);
        int floor = (int) Math.floor(Math.min(ay, by) - inner * 0.55);
        s.carve(Carve.capsule(ax, ay, az, bx, by, bz, inner, innerTip, floor));
        s.point("hollow_floor", Mth.lerp((from + to) / 2, rootX, tipX), floor, Mth.lerp((from + to) / 2, rootZ, tipZ));
        // Now and then the solid part toward the roots hides golden heartwood.
        if (s.random.nextDouble() < heartChance) {
            double h0 = from * 0.25, h1 = from * 0.85;
            s.tube(Tube.line(Mth.lerp(h0, rootX, tipX), Mth.lerp(h0, rootY, tipY), Mth.lerp(h0, rootZ, tipZ),
                    Mth.lerp(h1, rootX, tipX), Mth.lerp(h1, rootY, tipY), Mth.lerp(h1, rootZ, tipZ), radius * 0.4,
                    radius * 0.35, Part.Role.HEART));
        }

        // The root plate, on edge: a thin disc across the log's end, wood at its heart and earth round it.
        double plateX = rootX - hx * 1.5, plateZ = rootZ - hz * 1.5;
        double across = radius * s.range(2.0, 2.6), tall = radius * s.range(1.8, 2.3);
        boolean alongX = Math.abs(hx) > Math.abs(hz);
        double thin = s.range(2.0, 3.0);
        double py = rootY + radius * 0.4;
        s.part(new Blob(plateX - hx * 1.2, py, plateZ - hz * 1.2, alongX ? thin + 1 : across,
                tall, alongX ? across : thin + 1, 0.2, s.random.nextDouble() * Mth.TWO_PI, Part.Role.SOIL));
        s.part(new Blob(plateX, py, plateZ, alongX ? thin : across * 0.75, tall * 0.8, alongX ? across * 0.75 : thin,
                0.15, s.random.nextDouble() * Mth.TWO_PI, Part.Role.BARK));
        int roots = s.range(7, 11);
        for (int i = 0; i < roots; i++) {
            double angle = i * Mth.TWO_PI / roots + s.range(-0.3, 0.3);
            double ux = Math.cos(angle), uy = Math.sin(angle);
            // Spokes in the plane of the plate, reaching past its rim and curling back away from the log.
            double px = alongX ? 0 : ux, pz = alongX ? ux : 0;
            double reach = Math.max(across, tall) * s.range(1.0, 1.35);
            double ex = plateX + px * reach - hx * s.range(2, 6), ey = py + uy * reach * (tall / across),
                    ez = plateZ + pz * reach - hz * s.range(2, 6);
            s.tube(Tube.line(plateX, py, plateZ, ex, ey, ez, s.range(1.2, 1.8), 0.6, Part.Role.LOG));
        }
        // The crater it tore out, behind the plate.
        double craterX = plateX - hx * (across * 0.8 + 4), craterZ = plateZ - hz * (across * 0.8 + 4);
        s.carve(Carve.chamber(craterX, rootGround + 1, craterZ, across * 0.7, 4.5, across * 0.7, rootGround - 3));
        s.point("crater", craterX, rootGround - 3, craterZ);

        // The crown end, shattered: splinters of bare wood bristling out of the break.
        int splinters = s.range(6, 10);
        for (int i = 0; i < splinters; i++) {
            double angle = s.random.nextDouble() * Mth.TWO_PI;
            double off = radius * s.range(0.2, 0.8);
            double sx = tipX - hx * 1.5, sz = tipZ - hz * 1.5;
            double ox = -hz * Math.cos(angle) * off, oy = Math.sin(angle) * off, oz = hx * Math.cos(angle) * off;
            double reach = s.range(3, 8);
            s.tube(Tube.line(sx + ox, tipY + oy, sz + oz, sx + ox + hx * reach + s.range(-1, 1),
                    tipY + oy * 1.2 + s.range(-1, 1), sz + oz + hz * reach + s.range(-1, 1), s.range(0.9, 1.4), 0.4,
                    Part.Role.STRIPPED));
        }

        // Broken stubs of limbs along its back and flanks.
        int stubs = s.range(3, 6);
        for (int i = 0; i < stubs; i++) {
            double t = s.range(0.35, 0.9);
            double cx = Mth.lerp(t, rootX, tipX), cy = Mth.lerp(t, rootY, tipY), cz = Mth.lerp(t, rootZ, tipZ);
            double side = s.range(-1.0, 1.0);
            double dx = -hz * side, dy = s.range(0.4, 1.0), dz = hx * side;
            double reach = radius + s.range(3, 7);
            double norm = Math.sqrt(dx * dx + dy * dy + dz * dz);
            s.tube(Tube.line(cx, cy, cz, cx + dx / norm * reach, cy + dy / norm * reach, cz + dz / norm * reach,
                    s.range(1.2, 2.0), 0.9, Part.Role.LOG));
        }

        // Its back: where young growth takes hold.
        for (double t = 0.1; t < 0.95; t += 0.08) {
            s.point("nurse_top", Mth.lerp(t, rootX, tipX), Mth.lerp(t, rootY, tipY) + radius * 0.8 + 1,
                    Mth.lerp(t, rootZ, tipZ));
        }
    }

    private static int groundOr(Sketch s, double px, double pz) {
        int g = s.groundAt(px, pz);
        return g == Integer.MIN_VALUE ? s.ground : g;
    }

    private FallenColossus() {
    }
}
