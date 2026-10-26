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
 * A standing trunk: a leaning column that tapers from a flared foot to a narrower top, its bark furrowed into flutes
 * that twist slowly as it climbs. Below its base it keeps the foot's full width for {@code rootDepth} blocks, filling
 * the air under it down to the ground.
 *
 * @param radius     radius at the base, before the flare
 * @param tipFraction radius at the top as a fraction of {@code radius}
 * @param taper      the taper curve's exponent: above 1 the trunk stays thick longer
 * @param flutes     number of furrows round the trunk
 * @param fluteDepth furrow depth as a fraction of the radius
 * @param twist      turns of the furrows from base to top
 * @param flare      extra radius at the very base, as a fraction of {@code radius}
 * @param flareHeight fraction of the height over which the flare fades
 */
public record Trunk(double x, double z, int base, int top, double radius, double tipFraction, double taper,
                    double leanX, double leanZ, int flutes, double fluteDepth, double phase, double twist,
                    double flare, double flareHeight, int rootDepth, Role role) implements Part {
    @Override
    public Bounds bounds() {
        double reach = radius * (1 + flare + fluteDepth) + 1;
        double rise = top - base;
        double tx = x + leanX * rise, tz = z + leanZ * rise;
        return new Bounds((int) Math.floor(Math.min(x, tx) - reach), base - rootDepth,
                (int) Math.floor(Math.min(z, tz) - reach), (int) Math.ceil(Math.max(x, tx) + reach), top,
                (int) Math.ceil(Math.max(z, tz) + reach));
    }

    @Override
    public int foot() {
        return base;
    }

    /** The trunk's centre at a height. */
    public double centreX(double y) {
        return x + leanX * Math.max(0, y - base);
    }

    public double centreZ(double y) {
        return z + leanZ * Math.max(0, y - base);
    }

    /** The trunk's radius at a height, before flutes. */
    public double radiusAt(double y) {
        double s = y <= base ? 0 : Math.min(1, (y - base) / (double) (top - base));
        double r = radius * (1 - (1 - tipFraction) * Math.pow(s, taper));
        if (s < flareHeight) {
            double f = 1 - s / flareHeight;
            r += radius * flare * f * f;
        }
        return r;
    }

    @Override
    public double margin(double px, double py, double pz) {
        if (py > top || py < base - rootDepth) {
            return -Double.MAX_VALUE;
        }
        double dx = px - centreX(py), dz = pz - centreZ(py);
        double r = radiusAt(py);
        if (flutes > 0) {
            double s = py <= base ? 0 : (py - base) / (double) (top - base);
            double angle = Math.atan2(dz, dx);
            r *= 1 + fluteDepth * Math.cos(flutes * (angle - phase - twist * Math.PI * 2 * s));
        }
        // The top is cut flat, but the crown limbs that leave it hide the cut.
        return Math.min(r - Math.sqrt(dx * dx + dz * dz), top + 0.5 - py);
    }
}
