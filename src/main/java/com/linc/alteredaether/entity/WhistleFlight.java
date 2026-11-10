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
package com.linc.alteredaether.entity;

import net.minecraft.world.phys.Vec3;

/**
 * The path a whistled-for aerwhale flies, from where it appears behind and above its owner round to their front: a
 * cubic Bézier curve that leaves level and heading the way they look, so it passes beside them, and arrives level and
 * broadside, crossing in front of them. It's walked by distance along the curve, through a table of the curve's length.
 */
public final class WhistleFlight {
    /** How many pieces the length table splits the curve into. */
    private static final int STEPS = 96;

    private final Vec3 p0;
    private final Vec3 p1;
    private final Vec3 p2;
    private final Vec3 p3;
    /** The length of the curve up to each step. */
    private final double[] lengths = new double[STEPS + 1];

    /**
     * @param start  where the whale appears
     * @param leave  the way it sets off, scaled to how far it keeps on that way before turning
     * @param end    where it arrives
     * @param arrive the way it's going as it arrives, scaled to how far it comes in on that line
     */
    public WhistleFlight(Vec3 start, Vec3 leave, Vec3 end, Vec3 arrive) {
        p0 = start;
        p1 = start.add(leave);
        p2 = end.subtract(arrive);
        p3 = end;
        Vec3 previous = p0;
        for (int i = 1; i <= STEPS; i++) {
            Vec3 point = bezier((double) i / STEPS);
            lengths[i] = lengths[i - 1] + point.distanceTo(previous);
            previous = point;
        }
    }

    public double length() {
        return lengths[STEPS];
    }

    public Vec3 start() {
        return p0;
    }

    public Vec3 end() {
        return p3;
    }

    /** The point {@code distance} along the curve, held to its ends. */
    public Vec3 at(double distance) {
        return bezier(parameter(distance));
    }

    /** The unit direction of travel {@code distance} along the curve. */
    public Vec3 heading(double distance) {
        double t = parameter(distance);
        double u = 1 - t;
        Vec3 derivative = p1.subtract(p0).scale(3 * u * u)
                .add(p2.subtract(p1).scale(6 * u * t))
                .add(p3.subtract(p2).scale(3 * t * t));
        return derivative.lengthSqr() < 1.0E-12 ? p3.subtract(p0).normalize() : derivative.normalize();
    }

    /** The curve's parameter at {@code distance} along it, read off the length table. */
    private double parameter(double distance) {
        if (distance <= 0) {
            return 0;
        }
        if (distance >= length()) {
            return 1;
        }
        int low = 0;
        int high = STEPS;
        while (high - low > 1) {
            int mid = (low + high) >>> 1;
            if (lengths[mid] <= distance) {
                low = mid;
            } else {
                high = mid;
            }
        }
        double span = lengths[high] - lengths[low];
        double along = span <= 0 ? 0 : (distance - lengths[low]) / span;
        return (low + along) / STEPS;
    }

    private Vec3 bezier(double t) {
        double u = 1 - t;
        return p0.scale(u * u * u)
                .add(p1.scale(3 * u * u * t))
                .add(p2.scale(3 * u * t * t))
                .add(p3.scale(t * t * t));
    }
}
