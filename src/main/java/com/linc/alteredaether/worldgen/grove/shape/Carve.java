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
 * A space cut out of a giant: a hollow, a tunnel, an alcove. Nothing of the tree is placed inside it, and wood within
 * a block or so of its surface is stripped, as heartwood laid bare. Above its floor it also clears the ground, so a
 * hollow on uneven ground has a level floor and a tunnel runs straight through.
 */
public abstract class Carve {
    protected final int floor;
    private final Bounds bounds;

    protected Carve(int floor, Bounds bounds) {
        this.floor = floor;
        this.bounds = bounds;
    }

    public Bounds bounds() {
        return bounds;
    }

    /** The lowest height the carve clears; it never digs below this. */
    public int floor() {
        return floor;
    }

    /** Positive inside, in roughly blocks. */
    public abstract double margin(double x, double y, double z);

    /** An upright ellipsoid, cut off flat at {@code floor}: a chamber. */
    public static Carve chamber(double x, double y, double z, double rx, double ry, double rz, int floor) {
        return new Carve(floor, Bounds.around(x, y, z, rx, ry, rz)) {
            @Override
            public double margin(double px, double py, double pz) {
                if (py < floor) {
                    return -Double.MAX_VALUE;
                }
                double dx = (px - x) / rx, dy = (py - y) / ry, dz = (pz - z) / rz;
                return (1 - Math.sqrt(dx * dx + dy * dy + dz * dz)) * Math.min(rx, Math.min(ry, rz));
            }
        };
    }

    /**
     * A level passage from one point to another with a round-arched section, {@code halfWidth} across at the floor
     * and {@code height} tall at the crown: a tunnel through a trunk, or the slit into a hollow.
     */
    public static Carve arch(double x0, double z0, double x1, double z1, int floor, double halfWidth, double height) {
        double dx = x1 - x0, dz = z1 - z0;
        double length = Math.sqrt(dx * dx + dz * dz);
        double ux = dx / length, uz = dz / length;
        Bounds bounds = new Bounds((int) Math.floor(Math.min(x0, x1) - halfWidth) - 1, floor,
                (int) Math.floor(Math.min(z0, z1) - halfWidth) - 1, (int) Math.ceil(Math.max(x0, x1) + halfWidth) + 1,
                floor + (int) Math.ceil(height) + 1, (int) Math.ceil(Math.max(z0, z1) + halfWidth) + 1);
        return new Carve(floor, bounds) {
            @Override
            public double margin(double px, double py, double pz) {
                double h = py - floor;
                if (h < 0) {
                    return -Double.MAX_VALUE;
                }
                double ox = px - x0, oz = pz - z0;
                double along = ox * ux + oz * uz;
                double across = -ox * uz + oz * ux;
                double end = Math.min(along, length - along);
                double w = across / halfWidth, v = h / height;
                double section = (1 - Math.sqrt(w * w + v * v)) * Math.min(halfWidth, height);
                return Math.min(section, end);
            }
        };
    }
}
