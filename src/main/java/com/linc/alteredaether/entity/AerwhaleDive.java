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
package com.linc.alteredaether.entity;

/**
 * The path of a ridden aerwhale's dive: a smooth arc that leaves at the angle the whale already flies at, bends steadily
 * downward and ends {@code depth} below where it started, nose down. The path is a parabola over the horizontal
 * distance travelled, {@code height(s) = slope0 s - k s²}, so its slope falls evenly from {@code slope0} at the start
 * to {@code slope0 - 2 k length} at the end.
 *
 * @param depth  how far below the start it ends, in blocks
 * @param length the horizontal distance it covers, in blocks
 * @param slope0 rise over run where it starts: the whale's pitch as the dive begins
 * @param k      how sharply the parabola bends down
 */
public record AerwhaleDive(double depth, double length, double slope0, double k) {
    /** Depth on the shortest charge, in blocks. */
    public static final double MIN_DEPTH = 3;
    /** Depth on a full charge, in blocks. */
    public static final double MAX_DEPTH = 15;
    /** The steepest it flies, in degrees, as a ridden whale's pitch is held to. */
    public static final double MAX_PITCH = 60;
    /** How much steeper than level, as a slope, a dive from level flight ends: a long, shallow arc. */
    private static final double LEVEL_END_SLOPE = 0.8;
    /** How much steeper, as a slope, the end is than the start at least, so even a steep dive still bends down. */
    private static final double MIN_BEND = 0.4;
    /** The longest a dive runs, per block of depth, should a climbing whale's arc need longer to come back down. */
    private static final double MAX_LENGTH_PER_DEPTH = 4;

    /** A dive {@code depth} deep that starts at {@code pitch} degrees, nose up positive. */
    public static AerwhaleDive of(double depth, float pitch) {
        double steepest = Math.tan(Math.toRadians(MAX_PITCH));
        double slope0 = Math.clamp(Math.tan(Math.toRadians(pitch)), -steepest, steepest);
        // the end: a climbing whale arcs over as far below level as it was above, a falling one only steepens
        double slope1 = Math.max(Math.min(-(slope0 + LEVEL_END_SLOPE), slope0 - MIN_BEND), -steepest);
        slope1 = Math.min(slope1, slope0);
        // a parabola's average slope is the mean of its end slopes, which over the length must drop depth
        double sum = slope0 + slope1;
        double length = sum < 0 ? -2 * depth / sum : Double.POSITIVE_INFINITY;
        if (length > MAX_LENGTH_PER_DEPTH * depth) {
            length = MAX_LENGTH_PER_DEPTH * depth;
            slope1 = -2 * depth / length - slope0;
        }
        return new AerwhaleDive(depth, length, slope0, (slope0 - slope1) / (2 * length));
    }

    /**
     * How deep a dive a charge of {@code power} (0 to 100, from the rider's jump bar) asks for. As with a horse's jump,
     * anything from 90 up is a full charge.
     */
    public static double depthFor(int power) {
        double charge = Math.clamp(power / 90.0, 0, 1);
        return MIN_DEPTH + (MAX_DEPTH - MIN_DEPTH) * charge;
    }

    /** Height above the start after travelling {@code s} blocks horizontally. */
    public double height(double s) {
        return slope0 * s - k * s * s;
    }

    /** Rise over run at {@code s}. */
    public double slope(double s) {
        return slope0 - 2 * k * s;
    }

    /** Pitch at {@code s} in degrees, nose up positive. */
    public float pitch(double s) {
        return (float) Math.toDegrees(Math.atan(slope(s)));
    }

    public boolean done(double s) {
        return s >= length;
    }
}
