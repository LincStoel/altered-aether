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

/** An inclusive block box. */
public record Bounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
    public static final Bounds EMPTY = new Bounds(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE,
            Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE);

    /** The box around a sphere, padded by one block for rounding. */
    public static Bounds around(double x, double y, double z, double rx, double ry, double rz) {
        return new Bounds((int) Math.floor(x - rx) - 1, (int) Math.floor(y - ry) - 1, (int) Math.floor(z - rz) - 1,
                (int) Math.ceil(x + rx) + 1, (int) Math.ceil(y + ry) + 1, (int) Math.ceil(z + rz) + 1);
    }

    public Bounds union(Bounds o) {
        return new Bounds(Math.min(minX, o.minX), Math.min(minY, o.minY), Math.min(minZ, o.minZ),
                Math.max(maxX, o.maxX), Math.max(maxY, o.maxY), Math.max(maxZ, o.maxZ));
    }

    public boolean column(int x, int z) {
        return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
    }

    public boolean height(int y) {
        return y >= minY && y <= maxY;
    }

    public boolean touches(int x0, int z0, int x1, int z1) {
        return maxX >= x0 && minX <= x1 && maxZ >= z0 && minZ <= z1;
    }
}
