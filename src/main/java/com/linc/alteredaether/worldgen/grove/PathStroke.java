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

import com.linc.alteredaether.worldgen.grove.shape.Bounds;

/**
 * A worn trail between giants: the ground along a polyline, within {@code halfWidth}, trodden into dirt path. On a
 * trail nobody walks any more only {@code coverage} of it is still bare, the rest grown back over.
 */
public record PathStroke(double[] xs, double[] zs, double halfWidth, double coverage, long salt) {
    public Bounds bounds() {
        Bounds b = Bounds.EMPTY;
        for (int i = 0; i < xs.length; i++) {
            b = b.union(Bounds.around(xs[i], 0, zs[i], halfWidth, 0, halfWidth));
        }
        return b;
    }

    /** Distance from a column to the trail's centre line. */
    public double distance(double x, double z) {
        double best = Double.MAX_VALUE;
        for (int i = 0; i + 1 < xs.length; i++) {
            double dx = xs[i + 1] - xs[i], dz = zs[i + 1] - zs[i];
            double length2 = dx * dx + dz * dz;
            double t = length2 < 1e-9 ? 0 : ((x - xs[i]) * dx + (z - zs[i]) * dz) / length2;
            t = t < 0 ? 0 : t > 1 ? 1 : t;
            double ox = x - xs[i] - dx * t, oz = z - zs[i] - dz * t;
            best = Math.min(best, ox * ox + oz * oz);
        }
        return Math.sqrt(best);
    }
}
