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

import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

/** A lumpy ellipsoid of wood: a burl, a knot, a clump of root plate. Its lumps come from a few fixed lobes. */
public record Blob(double x, double y, double z, double rx, double ry, double rz, double lumps, double phase,
                   Role role) implements Part {
    @Override
    public Bounds bounds() {
        double grow = 1 + lumps;
        return Bounds.around(x, y, z, rx * grow, ry * grow, rz * grow);
    }

    @Override
    public double margin(double px, double py, double pz) {
        double dx = (px - x) / rx, dy = (py - y) / ry, dz = (pz - z) / rz;
        double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double bump = lumps * Math.sin(3 * Math.atan2(dz, dx) + phase) * Math.cos(2 * dy + phase);
        return (1 + bump - d) * Math.min(rx, Math.min(ry, rz));
    }

    @Nullable
    @Override
    public Direction.Axis axis(double px, double py, double pz) {
        return null;
    }
}
