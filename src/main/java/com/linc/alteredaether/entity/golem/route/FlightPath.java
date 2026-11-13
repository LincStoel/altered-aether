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
package com.linc.alteredaether.entity.golem.route;

import java.util.ArrayList;
import java.util.List;

/**
 * How an item moves along its route, tick by tick: "Slider legs, soft corners". Along each leg it speeds up like the
 * Aether's Slider, gaining a little speed each tick up to a top speed; nearing a corner it eases into the turn the way
 * a shulker bullet steers, its speed moving a fifth of the way toward a slower cornering speed each tick, round a short
 * curve with no full stop. Pure and deterministic, so the server (to know when the item lands) and every client (to
 * draw it) work out the same table from the same points.
 */
public final class FlightPath {
    /** Speed gained each tick on a straight, and the most it reaches, in blocks per tick. */
    static final double ACCEL = 0.03, TOP_SPEED = 0.55;
    /** Speed setting off, taking a corner, and at the end. */
    static final double START_SPEED = 0.06, CORNER_SPEED = 0.2, END_SPEED = 0.1;
    /** How much of the way to the cornering speed it eases each tick, close to a corner. */
    static final double EASE = 0.2;
    /** How far before a corner it starts easing, and how far from the corner the curve begins. */
    static final double EASE_DISTANCE = 1.5, CORNER_RADIUS = 0.45;
    /** The most ticks a flight may take, whatever its length. */
    static final int MAX_TICKS = 2400;

    private FlightPath() {
    }

    /**
     * Where the item is at each tick from setting off (index 0, the first point) to landing (the last entry, the last
     * point), along {@code points} with its corners rounded.
     */
    public static double[][] table(List<double[]> points) {
        List<double[]> dense = smooth(points);
        int n = dense.size();
        double[] along = new double[n];
        for (int i = 1; i < n; i++) {
            along[i] = along[i - 1] + distance(dense.get(i - 1), dense.get(i));
        }
        double length = along[n - 1];
        List<Double> corners = cornerDistances(points);
        List<double[]> table = new ArrayList<>();
        table.add(dense.get(0).clone());
        double s = 0, v = START_SPEED;
        int seg = 0;
        while (s < length && table.size() < MAX_TICKS) {
            double toCorner = Double.MAX_VALUE;
            for (double c : corners) {
                if (c >= s - CORNER_RADIUS) {
                    toCorner = Math.max(0, c - s);
                    break;
                }
            }
            if (length - s < 1.0) {
                v += (END_SPEED - v) * EASE;
            } else if (toCorner < EASE_DISTANCE) {
                v += (CORNER_SPEED - v) * EASE;
            } else {
                v = Math.min(TOP_SPEED, v + ACCEL);
            }
            v = Math.max(v, 0.02);
            s = Math.min(length, s + v);
            while (seg < n - 2 && along[seg + 1] < s) {
                seg++;
            }
            double span = along[seg + 1] - along[seg];
            double u = span <= 1.0E-9 ? 1 : (s - along[seg]) / span;
            table.add(lerp(dense.get(seg), dense.get(seg + 1), u));
        }
        table.set(table.size() - 1, dense.get(n - 1).clone());
        return table.toArray(new double[0][]);
    }

    /** The ticks a flight along {@code points} takes, at least {@code minTicks}. */
    public static int ticks(List<double[]> points, int minTicks) {
        return Math.max(minTicks, table(points).length - 1);
    }

    /**
     * Where the item is at {@code time} ticks into a flight lasting {@code ticks}: the table is stretched to fill it
     * when the flight is slowed to its least length.
     */
    public static double[] at(double[][] table, int ticks, double time) {
        if (table.length == 1) {
            return table[0];
        }
        double t = Math.max(0, Math.min(1, time / Math.max(1, ticks))) * (table.length - 1);
        int i = Math.min(table.length - 2, (int) Math.floor(t));
        return lerp(table[i], table[i + 1], t - i);
    }

    /** The points with each corner replaced by a short curve. */
    private static List<double[]> smooth(List<double[]> points) {
        List<double[]> dense = new ArrayList<>();
        dense.add(points.get(0));
        for (int i = 1; i < points.size() - 1; i++) {
            double[] prev = points.get(i - 1), corner = points.get(i), next = points.get(i + 1);
            double in = distance(prev, corner), out = distance(corner, next);
            double r = Math.min(CORNER_RADIUS, Math.min(in, out) / 2);
            if (r <= 1.0E-6) {
                dense.add(corner);
                continue;
            }
            double[] a = lerp(corner, prev, r / in), b = lerp(corner, next, r / out);
            for (int k = 0; k <= 6; k++) {
                double t = k / 6.0;
                double[] p = new double[3];
                for (int c = 0; c < 3; c++) {
                    p[c] = (1 - t) * (1 - t) * a[c] + 2 * (1 - t) * t * corner[c] + t * t * b[c];
                }
                dense.add(p);
            }
        }
        if (points.size() > 1) {
            dense.add(points.get(points.size() - 1));
        } else {
            dense.add(points.get(0));
        }
        return dense;
    }

    /** How far along the unrounded route each corner is. */
    private static List<Double> cornerDistances(List<double[]> points) {
        List<Double> corners = new ArrayList<>();
        double s = 0;
        for (int i = 1; i < points.size() - 1; i++) {
            s += distance(points.get(i - 1), points.get(i));
            corners.add(s);
        }
        return corners;
    }

    static double distance(double[] a, double[] b) {
        double dx = a[0] - b[0], dy = a[1] - b[1], dz = a[2] - b[2];
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    static double[] lerp(double[] a, double[] b, double t) {
        return new double[]{a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t};
    }
}
