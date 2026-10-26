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
package com.linc.alteredaether.worldgen.grove;

import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A shallow swamp lake round the foot of a Cathedral giant: still water out to a wobbling shore, with the giant, and
 * any neighbour that stands in it, each on an islet of its own. The bed shelves from a block deep at every shore to
 * {@code depth} in open water. Pure shape, like the giants, so every chunk agrees on it ({@link MireBuilder}).
 *
 * @param water   the water line: the top block of water
 * @param radius  the shore's mean radius
 * @param depth   the most water over the bed, in open water
 * @param islets  the islets the giants stand on, the owner's first
 * @param bank    the width of the cleared bank outside the shore
 * @param shore   the shore's radius at evenly spaced bearings from +x, a lake that follows the ground (0 where it
 *                comes no further out than the islet, so that side stays dry); or null for the usual wobbling ring
 *                of {@code radius}, its widest
 */
public record Mire(int x, int z, int water, double radius, double depth, double phase1, double phase2,
                   List<Islet> islets, long salt, double bank, @Nullable double[] shore) {
    /** Blocks from a shore, the lake's or an islet's, over which the bed shelves to full depth. */
    static final double SHELF = 6;
    /** Usual width of the cleared bank outside the shore, and blocks it recedes per block of rise. */
    public static final double BANK_WIDTH = 8, BANK_SLOPE = 2;
    /** The largest the shore's wobble can make the radius. */
    static final double MAX_WOBBLE = 1.22;

    public Mire(int x, int z, int water, double radius, double depth, double phase1, double phase2,
                List<Islet> islets, long salt) {
        this(x, z, water, radius, depth, phase1, phase2, islets, salt, BANK_WIDTH, null);
    }

    /**
     * One giant's islet: a wobbling disc of dry ground round its trunk.
     *
     * @param ground the giant's own ground, which the islet is raised to meet if the lake would drown it
     */
    public record Islet(double x, double z, double radius, double phase, int ground) {
        /** How far out (px, pz) lies as a fraction of the islet's shore: below 1 is dry. */
        double reach(double px, double pz) {
            double dx = px - x, dz = pz - z;
            double distance = Math.sqrt(dx * dx + dz * dz);
            return distance < 1e-6 ? 0 : distance / (radius * wobble(Math.atan2(dz, dx), phase, phase * 1.7));
        }

        /** The islet's radius at a bearing. */
        double shore(double angle) {
            return radius * wobble(angle, phase, phase * 1.7);
        }

        /** Blocks past the islet's shore, out over the water; negative on the islet. */
        double past(double px, double pz) {
            return (reach(px, pz) - 1) * radius;
        }
    }

    static double wobble(double angle, double phase1, double phase2) {
        return 1 + 0.15 * Math.sin(3 * angle + phase1) + 0.07 * Math.sin(5 * angle + phase2);
    }

    /** The lake's shore radius at a bearing; 0 where that side is dry. */
    public double shoreAt(double angle) {
        if (shore == null) {
            return radius * wobble(angle, phase1, phase2);
        }
        double f = Mth.positiveModulo(angle / Mth.TWO_PI, 1.0) * shore.length;
        int i = Mth.floor(f);
        return Mth.lerp(f - i, shore[i % shore.length], shore[(i + 1) % shore.length]);
    }

    /** How far out (px, pz) lies as a fraction of the shore in its direction: below 1 is in the lake. */
    public double reach(double px, double pz) {
        double dx = px - x, dz = pz - z;
        double distance = Math.sqrt(dx * dx + dz * dz);
        if (distance < 1e-6) {
            return 0;
        }
        double r = shoreAt(Math.atan2(dz, dx));
        return r <= 0 ? Double.MAX_VALUE : distance / r;
    }

    /** Blocks past the lake's shore, out onto the bank; negative in the lake. */
    double pastShore(double px, double pz) {
        double dx = px - x, dz = pz - z;
        double distance = Math.sqrt(dx * dx + dz * dz);
        double r = shoreAt(Math.atan2(dz, dx));
        // A dry side has no bank: the islet simply meets the ground there.
        return r <= 0 ? Double.MAX_VALUE : distance - r;
    }

    /** Whether a column is on one of this lake's islets. */
    public boolean islet(int px, int pz) {
        for (Islet islet : islets) {
            if (islet.reach(px + 0.5, pz + 0.5) < 1) {
                return true;
            }
        }
        return false;
    }

    /** Whether a column holds this lake's water: inside the shore and off every islet. */
    public boolean wet(int px, int pz) {
        return reach(px + 0.5, pz + 0.5) < 1 && !islet(px, pz);
    }

    /** Blocks to the nearest shore, the lake's own or an islet's. Only meaningful in a wet column. */
    double shoreDistance(int px, int pz) {
        double nearest = -pastShore(px + 0.5, pz + 0.5);
        for (Islet islet : islets) {
            nearest = Math.min(nearest, islet.past(px + 0.5, pz + 0.5));
        }
        return nearest;
    }

    /**
     * The top of the bed in a wet column: a block under the water at the shore, shelving to full depth in open water,
     * with a gentle unevenness so the bottom isn't a smooth dish.
     */
    public int bed(int px, int pz) {
        double shelf = Mth.clamp(shoreDistance(px, pz) / SHELF, 0, 1);
        shelf = shelf * shelf * (3 - 2 * shelf);
        double rough = 0.6 * (GroveBuilder.hash(salt, Math.floorDiv(px, 3), 0, Math.floorDiv(pz, 3)) - 0.5);
        int deep = Math.max(1, (int) Math.round(1 + (depth - 1) * shelf + rough));
        return water - deep;
    }

    /**
     * The highest the ground may stand in a column of the bank, outside the shore: a block over the water at the
     * shore, rising {@link #BANK_SLOPE} blocks out per block up. MAX_VALUE beyond the bank.
     */
    public int bankTop(int px, int pz) {
        double past = pastShore(px + 0.5, pz + 0.5);
        if (past < 0 || past > bank) {
            return Integer.MAX_VALUE;
        }
        return water + 1 + (int) Math.floor(past / BANK_SLOPE);
    }

    /** The furthest any column the lake or its banks touch lies from its centre. */
    public double extent() {
        return (shore == null ? radius * MAX_WOBBLE : radius) + bank + 2;
    }

    public boolean touches(int minX, int minZ, int maxX, int maxZ) {
        double dx = Math.max(0, Math.max(minX - x, x - maxX)), dz = Math.max(0, Math.max(minZ - z, z - maxZ));
        double e = extent();
        return dx * dx + dz * dz <= e * e;
    }

    /** The lake with a bank of another width, for ground that stands higher round it. */
    public Mire withBank(double width) {
        return new Mire(x, z, water, radius, depth, phase1, phase2, islets, salt, width, shore);
    }

    /** The lake with more islets raised in it, for whatever else stands in the water. */
    public Mire withIslets(List<Islet> more) {
        List<Islet> all = new java.util.ArrayList<>(islets);
        all.addAll(more);
        return settle(water, all);
    }

    /** The lake with its own water line replaced, and its islets, once its neighbours are known. */
    Mire settle(int water, List<Islet> islets) {
        return new Mire(x, z, water, radius, depth, phase1, phase2, List.copyOf(islets), salt, bank, shore);
    }
}
