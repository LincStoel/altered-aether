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

/**
 * One solid piece of a giant tree, as a signed margin: how far inside the piece a point lies, in blocks, negative
 * outside. Margins need not be exact distances; they only have to be positive inside, roughly proportional to depth
 * near the surface, and cheap.
 */
public interface Part {
    /** How the piece's wood is cut. */
    enum Role {
        /** Logs laid along the piece, all-bark wood where it bends too sharply to have a grain. */
        LOG,
        /** All-bark wood throughout: burls, root plates, knots. */
        BARK,
        /** Bark stripped away: dead, weathered or rotten wood. */
        STRIPPED,
        /** The golden heartwood some elders hide. */
        HEART
    }

    Bounds bounds();

    double margin(double x, double y, double z);

    Role role();

    /**
     * The grain at a point inside the piece, or null where it has none (a bend, a knot): the block there becomes
     * all-bark wood.
     */
    @Nullable
    default Direction.Axis axis(double x, double y, double z) {
        return Direction.Axis.Y;
    }

    /**
     * Below this height the piece only fills air, the way a trunk roots down over uneven ground without burying
     * itself. Above it the piece replaces whatever is there.
     */
    default int foot() {
        return Integer.MIN_VALUE;
    }

    /** The axis of a direction, or null when no component clearly dominates. */
    @Nullable
    static Direction.Axis grain(double dx, double dy, double dz) {
        double ax = Math.abs(dx), ay = Math.abs(dy), az = Math.abs(dz);
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 1e-9) {
            return null;
        }
        double threshold = 0.8 * length;
        if (ay >= threshold) {
            return Direction.Axis.Y;
        }
        if (ax >= threshold) {
            return Direction.Axis.X;
        }
        if (az >= threshold) {
            return Direction.Axis.Z;
        }
        return null;
    }
}
