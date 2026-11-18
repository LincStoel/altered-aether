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
package com.linc.alteredaether.worldgen.feature;

/**
 * The shape of a wind-carved snow drift, free of Minecraft classes so it can be unit tested. Drifts are ridges running
 * across the wind, {@link #SPACING} blocks apart: a long gentle ramp up the windward side to a crest, then a steep drop
 * on the lee. Higher layers of snow are carried further downwind than lower ones (the shear grows with the square of
 * the height), so near the crest the snow leans out over the lee face in a curled lip with a scooped hollow under it.
 */
public final class DriftShape {
    /** Blocks from one crest to the next, along the wind. */
    public static final double SPACING = 46;
    /** Where the crest sits along a ridge's span, 0 to 1 from its windward foot. */
    static final double CREST = 0.62;
    /** The lee face's width, as a share of the span. */
    static final double LEE = 0.16;
    /** How far the top of a full-height drift is carried downwind, as a share of its height. */
    static final double SHEAR = 0.55;

    /** The ridge profile, 0 to 1, at a position {@code t} (0 to 1) along a span. */
    static double profile(double t) {
        t -= Math.floor(t);
        if (t < CREST) {
            return smooth(t / CREST);
        }
        if (t < CREST + LEE) {
            return 1 - smooth((t - CREST) / LEE);
        }
        return 0;
    }

    /**
     * Whether a point {@code above} blocks over the ground lies in the drift, where {@code along} is its position
     * along the wind (blocks, downwind positive) and {@code peak} the drift's full height there.
     */
    public static boolean inside(double along, double above, double peak) {
        return depth(along, above, peak) > 0;
    }

    /**
     * How far under the drift's surface a point lies, in blocks (negative above it): the height of the profile, read
     * where the layer at this height came from upwind, less the point's own height.
     */
    public static double depth(double along, double above, double peak) {
        if (peak <= 0 || above < 0) {
            return above < 0 ? 1 : -above;
        }
        double carried = SHEAR * above * (above / peak);
        return peak * profile((along - carried) / SPACING) - above;
    }

    private static double smooth(double t) {
        t = Math.max(0, Math.min(1, t));
        return t * t * (3 - 2 * t);
    }

    private DriftShape() {
    }
}
