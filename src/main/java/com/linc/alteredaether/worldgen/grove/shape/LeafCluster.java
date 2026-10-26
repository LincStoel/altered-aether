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
package com.linc.alteredaether.worldgen.grove.shape;

/**
 * A clump of foliage round a twig, the twig itself being a thin log {@link Tube} placed alongside. The clump is an
 * ellipsoid swept along the twig, so every leaf in it lies within a few blocks of a log: its taxicab reach is under
 * the six blocks leaves survive at, so the canopy never decays on its own, yet still falls once players fell the
 * wood.
 *
 * @param rx horizontal radius
 * @param ry vertical radius
 */
public record LeafCluster(double x0, double y0, double z0, double x1, double y1, double z1, double rx, double ry) {
    /** Largest radii that keep every leaf within taxicab 5 of the twig, leaving a block of slack. */
    public static final double MAX_RX = 3.2, MAX_RY = 2.2;

    public Bounds bounds() {
        return Bounds.around(x0, y0, z0, rx, ry, rx).union(Bounds.around(x1, y1, z1, rx, ry, rx));
    }

    /** Positive inside; the second element of {@code out} is the taxicab distance to the twig. */
    public double margin(double x, double y, double z, double[] out) {
        double dx = x1 - x0, dy = y1 - y0, dz = z1 - z0;
        double length2 = dx * dx + dy * dy + dz * dz;
        double t = length2 < 1e-9 ? 0 : ((x - x0) * dx + (y - y0) * dy + (z - z0) * dz) / length2;
        t = t < 0 ? 0 : t > 1 ? 1 : t;
        double ox = x - (x0 + dx * t), oy = y - (y0 + dy * t), oz = z - (z0 + dz * t);
        double h = Math.sqrt(ox * ox + oz * oz) / rx, v = oy / ry;
        out[0] = (1 - Math.sqrt(h * h + v * v)) * ry;
        out[1] = Math.abs(ox) + Math.abs(oy) + Math.abs(oz);
        return out[0];
    }
}
