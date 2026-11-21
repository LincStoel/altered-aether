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

import net.minecraft.util.Mth;

/**
 * One body of water: a bowl of {@code radius} around (x, z), {@code depth} blocks deep at the centre, holding water up
 * to {@code waterLevel}. The shoreline wobbles with the bearing from the centre, so no two basins are round. With a
 * {@code ring} it is instead a river round a ring island: every column within {@code radius} of the ring's centre
 * line.
 *
 * With a {@code lake} it is one of the big lakes: the shoreline is the lake shape's contour, {@code radius} is the
 * furthest it can reach, and everything over the water and its banks is planed away up to the shape's
 * {@link LakeShape#plane() plane}.
 *
 * <p>Above the water a cone widening at {@link #BANK_SLOPE} blocks out per block up clears the banks, so a basin cut
 * into uneven ground gets a sloping shore instead of a well shaft.
 */
public record BasinSite(BasinType type, int x, int z, double radius, double depth, int waterLevel, double phase1,
                        double phase2, @org.jetbrains.annotations.Nullable RingPath ring,
                        @org.jetbrains.annotations.Nullable LakeShape lake) {
    /** Height of the cleared bank above the water. */
    static final int BANK_HEIGHT = 12;
    /** Blocks the bank recedes per block of height. */
    static final double BANK_SLOPE = 1.5;
    /** The largest the shoreline wobble can make a radius. */
    static final double MAX_WOBBLE = 1.22;
    /** How far past the water a lagoon's quicksoil shore may reach, in blocks; inside a lake's banks anyway. */
    public static final int SHORE_REACH = 14;
    /** How far past a big lake's shore its sloping bank reaches; ground standing higher beyond is left as a cliff. */
    static final double LAKE_BANK = 18;
    /** How far past a big lake's shore it builds a lip of ground to hold its water where the island has none. */
    static final double LAKE_LIP = 4;

    public BasinSite(BasinType type, int x, int z, double radius, double depth, int waterLevel, double phase1,
                     double phase2) {
        this(type, x, z, radius, depth, waterLevel, phase1, phase2, null, null);
    }

    public BasinSite(BasinType type, int x, int z, double radius, double depth, int waterLevel, double phase1,
                     double phase2, @org.jetbrains.annotations.Nullable RingPath ring) {
        this(type, x, z, radius, depth, waterLevel, phase1, phase2, ring, null);
    }

    /** A big lake, shaped by {@code lake}. */
    public static BasinSite lake(BasinType type, int x, int z, double depth, int waterLevel, LakeShape lake) {
        return new BasinSite(type, x, z, lake.radius(), depth, waterLevel, 0, 0, null, lake);
    }

    /** The shoreline's radius multiplier at a bearing. Shared with the atoll, so its lagoon matches its ring. */
    static double wobble(double angle, double phase1, double phase2) {
        return 1 + 0.15 * Math.sin(3 * angle + phase1) + 0.07 * Math.sin(5 * angle + phase2);
    }

    /**
     * How far out (x, z) lies, as a fraction of the shoreline radius in its direction: below 1 is under water. On a
     * river, the distance from its centre line as a fraction of its half width, on either bank.
     */
    public double reach(int px, int pz) {
        if (lake != null) {
            return 1 + lake.beyond(px - x, pz - z) / radius;
        }
        double dx = px - x, dz = pz - z;
        if (ring != null) {
            return ring.distance(dx, dz) / radius;
        }
        double distance = Math.sqrt(dx * dx + dz * dz);
        if (distance < 1e-6) {
            return 0;
        }
        return distance / (radius * wobble(Math.atan2(dz, dx), phase1, phase2));
    }

    /** The furthest any column this basin touches, banks and shores included, lies from its centre. */
    public double extent() {
        if (ring != null) {
            return ring.furthest() + radius + SHORE_REACH;
        }
        if (lake != null) {
            // The bank's distance is read off the field's slope, which overshoots a little where the noise is flat.
            return radius + LAKE_BANK * 1.5 + 2;
        }
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
        if (type.atollShaped() || py < waterLevel - maxDepth()) {
            return false;
        }
        if (lake != null) {
            int[] run = new int[2];
            return lakeColumn(px, pz, run) && py > run[0] && py <= run[1];
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
        return lake != null ? lake.wet(px - x, pz - z) : reach(px, pz) < 1;
    }

    /**
     * A big lake's carve in one column, as the open run {@code run[0] < y <= run[1]}: from the bed up over the water,
     * and on the banks from a cone rising {@link #BANK_SLOPE} blocks out per block up. Everything up to the plane
     * goes, so a hill on the shore is cut back to a sloping bank, or to a cliff where it stands past the bank.
     *
     * @return false where the lake carves nothing in the column
     */
    public boolean lakeColumn(int px, int pz, int[] run) {
        int dx = px - x, dz = pz - z;
        double extent = extent();
        if (lake == null || (double) dx * dx + (double) dz * dz > extent * extent) {
            return false;
        }
        run[1] = waterLevel + lake.plane();
        if (lake.wet(dx, dz)) {
            run[0] = lakeBed(px, pz);
            return true;
        }
        double beyond = lake.beyond(dx, dz);
        if (beyond > LAKE_BANK) {
            return false;
        }
        run[0] = (int) Math.floor(waterLevel + Math.max(0, beyond) / BANK_SLOPE);
        return true;
    }

    /**
     * The ground a big lake builds under itself where it runs past the island's edge, as the run
     * {@code run[0] <= y <= run[1]}: under the water it hangs from the bed, deepening inward to a couple of dozen
     * blocks, and for {@link #LAKE_LIP} blocks past the shore it stands up to the bank as a lip that holds the water.
     * Only open blocks of it are filled, from the top down to the first solid one, so it joins whatever island is there.
     *
     * @return false where the lake builds nothing in the column
     */
    public boolean lakeUnderpin(int px, int pz, int[] run) {
        if (lake == null || !lakeColumn(px, pz, run)) {
            return false;
        }
        int dx = px - x, dz = pz - z;
        double beyond = lake.beyond(dx, dz);
        if (beyond > LAKE_LIP) {
            return false;
        }
        int top = run[0];
        double hang = 3 + Math.min(22, 0.6 * (LAKE_LIP - beyond)) + 2 * lake.undersideNoise(dx, dz);
        run[1] = top;
        run[0] = top - (int) Math.round(Math.max(2, hang));
        return true;
    }

    /** A big lake's bed in a column under its water: the solid block the water rests on. */
    public int lakeBed(int px, int pz) {
        return (int) Math.floor(waterLevel + 1 - depth * lake.depthShare(px - x, pz - z));
    }

    /**
     * The bed under the water in a column, as the carve shapes it: the solid block the water rests on, unless the
     * island had a hole there already. Only meaningful where {@link #inFootprint} holds; not for atoll lagoons.
     */
    public int bed(int px, int pz) {
        return lake != null ? lakeBed(px, pz) : Mth.floor(floor(reach(px, pz)));
    }

    public boolean isLake() {
        return lake != null;
    }

    public boolean isRiver() {
        return ring != null;
    }
}
