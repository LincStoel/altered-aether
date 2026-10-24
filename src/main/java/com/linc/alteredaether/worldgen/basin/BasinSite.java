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
package com.linc.alteredaether.worldgen.basin;

/**
 * One body of water: a bowl of {@code radius} around (x, z), {@code depth} blocks deep at the centre, holding water up
 * to {@code waterLevel}. The shoreline wobbles with the bearing from the centre, so no two basins are round.
 *
 * <p>Above the water a cone widening at {@link #BANK_SLOPE} blocks out per block up clears the banks, so a basin cut
 * into uneven ground gets a sloping shore instead of a well shaft.
 */
public record BasinSite(BasinType type, int x, int z, double radius, double depth, int waterLevel, double phase1,
                        double phase2) {
    /** Height of the cleared bank above the water. */
    static final int BANK_HEIGHT = 12;
    /** Blocks the bank recedes per block of height. */
    static final double BANK_SLOPE = 1.5;
    /** The largest the shoreline wobble can make a radius. */
    static final double MAX_WOBBLE = 1.22;

    /** The shoreline's radius multiplier at a bearing. Shared with the atoll, so its lagoon matches its ring. */
    static double wobble(double angle, double phase1, double phase2) {
        return 1 + 0.15 * Math.sin(3 * angle + phase1) + 0.07 * Math.sin(5 * angle + phase2);
    }

    /** How far out (x, z) lies, as a fraction of the shoreline radius in its direction: below 1 is under water. */
    public double reach(int px, int pz) {
        double dx = px - x, dz = pz - z;
        double distance = Math.sqrt(dx * dx + dz * dz);
        if (distance < 1e-6) {
            return 0;
        }
        return distance / (radius * wobble(Math.atan2(dz, dx), phase1, phase2));
    }

    /** The furthest any column this basin touches, banks included, lies from its centre. */
    public double extent() {
        return radius * MAX_WOBBLE + BANK_HEIGHT * BANK_SLOPE + 2;
    }

    /** The deepest the water can reach below its surface. */
    public int maxDepth() {
        return (int) Math.ceil(depth) + 2;
    }

    /** The bowl's floor in a column: the bowl is open above it. Only meaningful where {@link #reach} is below 1. */
    double floor(double reach) {
        return waterLevel + 1 - depth * Math.pow(1 - reach * reach, 0.7);
    }

    /**
     * Whether the carve opens this block: inside the bowl below the water line, or inside the bank cone above it.
     * Atoll lagoons are shaped by the atoll's own terrain and never carve.
     */
    public boolean carves(int px, int py, int pz) {
        if (type == BasinType.ATOLL_LAGOON || py < waterLevel - maxDepth()) {
            return false;
        }
        double reach = reach(px, pz);
        if (py <= waterLevel) {
            return reach < 1 && py > floor(reach);
        }
        int above = py - waterLevel;
        if (above > BANK_HEIGHT) {
            return false;
        }
        double dx = px - x, dz = pz - z;
        double shore = radius * wobble(Math.atan2(dz, dx), phase1, phase2);
        return Math.sqrt(dx * dx + dz * dz) < shore + BANK_SLOPE * above;
    }

    public boolean inFootprint(int px, int pz) {
        return reach(px, pz) < 1;
    }
}
