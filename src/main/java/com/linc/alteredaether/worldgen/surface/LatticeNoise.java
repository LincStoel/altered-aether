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
package com.linc.alteredaether.worldgen.surface;

/**
 * Smooth 3D value noise on a unit lattice, -1 to 1: hashed corner values blended with a smoothstep. Free of Minecraft
 * classes, so it can be unit tested.
 */
public final class LatticeNoise {
    /** The noise at a point, -1 to 1; {@code salt} picks an independent field. */
    public static double value(double x, double y, double z, int salt) {
        int x0 = (int) Math.floor(x), y0 = (int) Math.floor(y), z0 = (int) Math.floor(z);
        double fx = smooth(x - x0), fy = smooth(y - y0), fz = smooth(z - z0);
        double c00 = lerp(fx, corner(x0, y0, z0, salt), corner(x0 + 1, y0, z0, salt));
        double c10 = lerp(fx, corner(x0, y0 + 1, z0, salt), corner(x0 + 1, y0 + 1, z0, salt));
        double c01 = lerp(fx, corner(x0, y0, z0 + 1, salt), corner(x0 + 1, y0, z0 + 1, salt));
        double c11 = lerp(fx, corner(x0, y0 + 1, z0 + 1, salt), corner(x0 + 1, y0 + 1, z0 + 1, salt));
        return lerp(fz, lerp(fy, c00, c10), lerp(fy, c01, c11));
    }

    private static double corner(int x, int y, int z, int salt) {
        long h = x * 0x9E3779B97F4A7C15L ^ y * 0xC2B2AE3D27D4EB4FL ^ z * 0x165667B19E3779F9L ^ salt * 0x27D4EB2F165667C5L;
        h ^= h >>> 31;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 29;
        return ((h >>> 11) * 0x1.0p-53) * 2 - 1;
    }

    private static double smooth(double t) {
        return t * t * (3 - 2 * t);
    }

    private static double lerp(double t, double a, double b) {
        return a + t * (b - a);
    }

    private LatticeNoise() {
    }
}
