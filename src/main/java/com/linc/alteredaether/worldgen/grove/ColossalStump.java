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
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

/**
 * A colossal stump, all that is left of a giant felled or snapped off long ago: low, broad and flat-topped, its top
 * a platform ringed with growth rings, its roots spread wide over the ground. Now and then its heart is gold, and
 * always a treasure lies buried in it.
 * <p>
 * Rarely a spring has risen in one. Its heart has rotted into a brimming pool; the water spills through notches in the
 * rim and runs down grooves it has worn in the flanks, into a mire choked with roots round the foot. Wherever the water
 * runs the bark is worn away, the bare grain lying the way it flows, and the wet top is thick with growth.
 */
final class ColossalStump {
    static void sketch(Sketch s, double heartChance, boolean spring) {
        double radius = s.range(7.0, 11.0);
        int base = s.ground;
        int top = base + (int) Math.round(s.range(4, 12) * s.v);
        if (spring) {
            // Broad and tall enough to hold a pool walled all round, and for its streams to show.
            radius = Math.max(radius, 8.5);
            top = Math.max(top, base + (int) Math.round(8 * Math.max(s.v, 0.75)));
        }
        Trunk trunk = s.trunk(new Trunk(s.x, s.z, base, top, radius, 0.95, 1.0, 0, 0, s.range(5, 8), 0.06,
                s.random.nextDouble() * Mth.TWO_PI, 0, 0.35, 0.4, 14, Part.Role.RINGS));
        if (s.random.nextDouble() < heartChance) {
            s.trunk(new Trunk(s.x, s.z, base - 4, top, radius * 0.3, 1.0, 1.0, 0, 0, 0, 0, 0, 0, 0, 0.01, 0,
                    Part.Role.HEART));
        }
        // Splinters left standing on one side of the break; round a spring, on its rim.
        int splinters = s.range(2, 4);
        double side = s.random.nextDouble() * Mth.TWO_PI;
        for (int i = 0; i < splinters; i++) {
            double angle = side + s.range(-0.6, 0.6);
            double d = radius * (spring ? s.range(0.8, 0.95) : s.range(0.6, 0.9));
            double sx = s.x + Math.cos(angle) * d, sz = s.z + Math.sin(angle) * d;
            s.tube(Tube.line(sx, top - 1, sz, sx + s.range(-1, 1), top + s.range(3, 7) * s.v, sz + s.range(-1, 1),
                    s.range(1.0, 1.6), 0.4, Part.Role.STRIPPED));
        }
        s.buttresses(s.x, s.z, radius, s.range(6, 9), s.range(8, 14));
        s.footRadius = radius * 1.3;
        s.point("stump_top", s.x, top + 1, s.z);
        if (spring) {
            spring(s, trunk, radius, top);
            return;
        }
        int growth = s.range(3, 6);
        for (int i = 0; i < growth; i++) {
            double a = s.random.nextDouble() * Mth.TWO_PI, d = radius * s.range(0.1, 0.7);
            Furnish.growth(s, s.x + Math.cos(a) * d, top, s.z + Math.sin(a) * d);
        }
        // Every stump keeps a treasure sealed in its heart, two blocks under the centre of the top: a cell just big
        // enough for the chest, found only by digging down.
        int cx = Mth.floor(s.x), cz = Mth.floor(s.z), cache = top - 2;
        s.carve(Carve.chamber(cx, cache, cz, 0.6, 0.6, 0.6, cache));
        Furnish.chest(s, cx, cache, cz, Direction.NORTH, Furnish.STUMP, Prop.Anchor.AT);
    }

    /** The pool, the notches in its rim and the grooves down the flanks, and the growth round the rim. */
    private static void spring(Sketch s, Trunk trunk, double radius, int top) {
        double pool = radius - 2.4;
        int water = top - 1;
        int depth = s.range(2, 3);
        int bottom = water - depth + 1;
        int outlets = s.range(2, 4);
        double phase = s.random.nextDouble() * Mth.TWO_PI;
        double[] angles = new double[outlets];
        for (int i = 0; i < outlets; i++) {
            angles[i] = phase + i * Mth.TWO_PI / outlets + s.range(-0.4, 0.4);
        }
        // The bowl opens through the top, flat-bottomed where its floor cuts it; its still water drifts toward the
        // nearest notch.
        int ox = s.x, oz = s.z;
        s.carve(Carve.chamber(ox, top + 1, oz, pool, depth + 1.5, pool, bottom)
                .wet(water, (px, py, pz) -> radial(nearest(angles, Math.atan2(pz - oz, px - ox)))));
        s.spring = new double[outlets][];
        for (int i = 0; i < outlets; i++) {
            double a = angles[i], cos = Math.cos(a), sin = Math.sin(a);
            Direction.Axis out = radial(a);
            // The notch, at the water line, out through the rim.
            s.carve(Carve.arch(s.x + cos * pool * 0.6, s.z + sin * pool * 0.6, s.x + cos * (radius + 3),
                    s.z + sin * (radius + 3), water, s.range(0.8, 1.3), 2.5).wet(water, (px, py, pz) -> out));
            // The groove the falling water has worn, hugging the flank from the lip down to the foot.
            // It starts just under the lip, so it never reaches back into the pool's floor.
            int lip = water - 1;
            int steps = Math.max(3, (lip - s.ground) / 2);
            double[] xs = new double[steps + 2], ys = new double[steps + 2], zs = new double[steps + 2],
                    rs = new double[steps + 2];
            for (int k = 0; k <= steps; k++) {
                double y = lip - (lip - s.ground) * k / (double) steps;
                double r = surface(trunk, a, y) - 0.3;
                xs[k] = s.x + cos * r;
                ys[k] = y;
                zs[k] = s.z + sin * r;
                rs[k] = 1.1;
            }
            double foot = surface(trunk, a, s.ground) + 1.5;
            xs[steps + 1] = s.x + cos * foot;
            ys[steps + 1] = s.ground - 0.5;
            zs[steps + 1] = s.z + sin * foot;
            rs[steps + 1] = 1.1;
            Carve groove = Carve.tube(new Tube(xs, ys, zs, rs, Part.Role.LOG, Integer.MIN_VALUE), Integer.MIN_VALUE);
            s.carve(groove.wet(Integer.MIN_VALUE, groove::flow));
            s.spring[i] = new double[]{a, foot};
        }
        s.point("spring", s.x, water, s.z);
        // The wet top is thick with growth: grass and saplings all round the rim, and a few young trees grown up.
        int growth = s.range(8, 14);
        for (int i = 0; i < growth; i++) {
            double a = s.random.nextDouble() * Mth.TWO_PI, d = s.range(pool + 0.6, radius - 0.4);
            Furnish.growth(s, s.x + Math.cos(a) * d, top, s.z + Math.sin(a) * d);
        }
        int young = s.range(1, 3);
        for (int i = 0; i < young; i++) {
            double a = s.random.nextDouble() * Mth.TWO_PI, d = s.range(pool + 0.8, radius - 1.0);
            s.twig(s.x + Math.cos(a) * d, top + 1, s.z + Math.sin(a) * d, s.range(-0.3, 0.3), 1, s.range(-0.3, 0.3),
                    s.range(3.0, 5.0));
        }
        // The treasure is still there, sealed in the wood under the pool's floor.
        int cx = Mth.floor(s.x), cz = Mth.floor(s.z), cache = bottom - 2;
        s.carve(Carve.chamber(cx, cache, cz, 0.6, 0.6, 0.6, cache));
        Furnish.chest(s, cx, cache, cz, Direction.NORTH, Furnish.STUMP, Prop.Anchor.AT);
    }

    /**
     * The runnels from the foot of each groove out across the ground: into the mire round the stump where there is
     * one, otherwise a short trench where the water stands. Drawn once the mire is known.
     */
    static void runoff(Sketch s, @Nullable Mire mire) {
        if (s.spring == null) {
            return;
        }
        int water = mire != null ? mire.water() : s.ground - 1;
        for (double[] outlet : s.spring) {
            double a = outlet[0], cos = Math.cos(a), sin = Math.sin(a);
            double from = outlet[1] - 1.5;
            double to = mire != null ? Math.max(from + 3, mire.islets().getFirst().shore(a) + 3)
                    : from + s.range(4, 7);
            Direction.Axis out = radial(a);
            s.carve(Carve.arch(s.x + cos * from, s.z + sin * from, s.x + cos * to, s.z + sin * to, water - 1,
                    s.range(0.9, 1.3), Math.max(3, s.ground - water + 3)).wet(water, (px, py, pz) -> out));
        }
    }

    /** How far out from the stump's axis its surface lies, at a bearing and a height. */
    private static double surface(Trunk trunk, double angle, double y) {
        double cos = Math.cos(angle), sin = Math.sin(angle);
        double lo = 0, hi = trunk.radius() * 2.5;
        for (int i = 0; i < 14; i++) {
            double mid = (lo + hi) / 2;
            if (trunk.margin(trunk.x() + cos * mid, y, trunk.z() + sin * mid) > 0) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        return lo;
    }

    /** Of a stump's notches, the bearing nearest a given one. */
    private static double nearest(double[] angles, double angle) {
        double best = angles[0], gap = Double.MAX_VALUE;
        for (double a : angles) {
            double d = Math.abs(Mth.wrapDegrees((a - angle) * Mth.RAD_TO_DEG));
            if (d < gap) {
                gap = d;
                best = a;
            }
        }
        return best;
    }

    /** The grain lying out along a bearing: whichever level axis is nearer it. */
    private static Direction.Axis radial(double angle) {
        return Math.abs(Math.cos(angle)) >= Math.abs(Math.sin(angle)) ? Direction.Axis.X : Direction.Axis.Z;
    }

    private ColossalStump() {
    }
}
