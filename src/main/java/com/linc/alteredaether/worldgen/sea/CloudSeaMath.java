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
package com.linc.alteredaether.worldgen.sea;

/**
 * The shape of the cloud sea's sheet, kept free of Minecraft so it can be unit-tested. After Aether II's cloudbed.
 *
 * <p>Each column reads two noises: {@code p}, about 200 blocks across, whose low ground is the sheet's holes and sky
 * lakes, and {@code q}, about 1000 across, which lifts the sheet by up to {@link #DRIFT}, so its top steps up and down
 * a block at a time over long distances. Where {@code p} is high the sheet is a lens {@link #DOWN} + {@link #UP}
 * blocks thick; it eases to a single block at a hole's rim. The sea's weight {@code w} (1 in open sea, falling to 0
 * at its border and where a landform takes over) eats into it from the holes outward, so the sheet breaks up into
 * lakes and scraps at its edges rather than ending in a wall.
 */
public final class CloudSeaMath {
    /** The sheet's middle where the drift is nothing. */
    public static final int CENTRE = -26;
    /** How far the drift lifts the sheet. */
    public static final double DRIFT = 8;
    /** How far a full lens reaches below and above the middle. */
    public static final double DOWN = 9, UP = 10;
    /** How much more of the sheet is hole where the sea's weight is 0. */
    public static final double GAP_BIAS = 1.2;
    /** Weight under which there is no sheet at all. */
    public static final double MIN_WEIGHT = 0.02;
    /** The lowest and highest blocks the sheet can fill. */
    public static final int BOTTOM = (int) Math.floor(CENTRE - DOWN), TOP = (int) Math.floor(CENTRE + DRIFT + UP);

    /** {@code 0} at 0 to {@code 1} at 1 along half a cosine. Not clamped: past either end it swings back. */
    public static double cosineEase(double t) {
        return (1 - Math.cos(Math.PI * t)) * 0.5;
    }

    /** How far the drift noise lifts the sheet, 0 to {@link #DRIFT}; it rolls up and back down as the noise climbs. */
    public static double lift(double q) {
        return cosineEase(1.5 * q + 0.5) * DRIFT;
    }

    /** The sheet's middle in a column. */
    public static double middle(double q) {
        return CENTRE + lift(q);
    }

    /** How thick the sheet is in a column, 0 (a hole) to 1 (a full lens). */
    public static double thickness(double p, double w) {
        if (w < MIN_WEIGHT) {
            return -1;
        }
        double n = 0.1 + 1.5 * p - GAP_BIAS * (1 - Math.min(1, w));
        return n < 0 ? -1 : cosineEase(Math.min(1, n));
    }

    /**
     * The blocks the sheet fills in a column, {@code out[0]} to {@code out[1]} inclusive. False where the column is a
     * hole. A rim is one block thick.
     */
    public static boolean span(double p, double q, double w, int[] out) {
        double t = thickness(p, w);
        if (t < 0) {
            return false;
        }
        double middle = middle(q);
        out[0] = (int) Math.floor(middle - DOWN * t);
        out[1] = (int) Math.floor(middle + UP * t);
        return true;
    }

    private CloudSeaMath() {
    }
}
