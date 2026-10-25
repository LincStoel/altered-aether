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
import org.jetbrains.annotations.Nullable;

/**
 * A ring island around a lagoon, standing on its own in a moat of open sky. Seen in profile: a bowl-shaped lagoon
 * floor rising to a beach, a low sandy crest, an outer shore falling away, and a cone of rock hanging underneath,
 * deepest beneath the lagoon. Horizontal sizes are in blocks; vertical ones are scaled by {@code vertical} for the
 * height mode.
 *
 * @param lagoon radius of the lagoon floor where it meets the beach
 * @param outer  radius where the outer shore ends
 * @param top    height of the crest's base; the lagoon's water stands two blocks below it
 * @param inner  the second ring and islet of a double atoll, or null for a single ring
 */
public record AtollSite(int x, int z, double lagoon, double outer, int top, double depth, double vertical,
                        double phase1, double phase2, @Nullable Inner inner) {
    /** The open-sky gap kept between the atoll's shore and whatever island surrounds it. */
    static final double MOAT = 6;
    /** Beyond the shore plus this, the atoll leaves the terrain alone. */
    static final double INFLUENCE = 26;

    /**
     * A double atoll's inner ring island, standing in the lagoon with two channels cut through it, and the islet at
     * the very centre.
     *
     * @param ring     the ring's centre line, as a fraction of the lagoon radius
     * @param width    the ring's half width at the water line, in blocks
     * @param islet    the islet's radius at the water line, in blocks
     * @param channel  bearing of the first channel through the ring
     * @param apart    angle from the first channel to the second
     */
    public record Inner(double ring, double width, double islet, double channel, double apart) {
    }

    public boolean isDouble() {
        return inner != null;
    }

    public double extent() {
        return outer * BasinSite.MAX_WOBBLE + INFLUENCE;
    }

    /** The lagoon's water, for the basin fill. */
    public BasinSite lagoonBasin() {
        return new BasinSite(BasinType.ATOLL_LAGOON, x, z, lagoon + 1, depth, top - 2, phase1, phase2);
    }

    /**
     * The atoll blended into the terrain around it: the ring wherever it is solid, and the surrounding terrain
     * pushed back to leave a moat. {@code terrain} is the density the atoll replaces.
     */
    public double density(int px, int py, int pz, double terrain) {
        double dx = px - x, dz = pz - z;
        double distance = Math.sqrt(dx * dx + dz * dz);
        double scale = BasinSite.wobble(Math.atan2(dz, dx), phase1, phase2);
        double in = lagoon * scale, out = outer * scale;
        if (distance > out + INFLUENCE) {
            return terrain;
        }
        double surrounding = Math.min(terrain, 0.1 * (distance - out - MOAT));
        if (distance > out) {
            return surrounding;
        }
        double crest = in + (out - in) * 0.45;
        double surface;
        if (distance <= in) {
            surface = top - 1 - depth * Math.pow(1 - (distance / in) * (distance / in), 0.6);
            if (inner != null) {
                surface = Math.max(surface, innerSurface(distance, in, Math.atan2(dz, dx)));
            }
        } else if (distance <= crest) {
            surface = top - 1 + 2 * vertical * smooth((distance - in) / (crest - in));
        } else {
            surface = top + 2 * vertical - 8 * vertical * smooth((distance - crest) / (out - crest));
        }
        double t = distance / out;
        double underside = top - vertical * (10 + 42 * (1 - t * t));
        double ring = Mth.clamp(0.1 * Math.min(surface - py, py - underside), -1, 1);
        return Math.max(ring, surrounding);
    }

    /**
     * The inner ring and the islet, as a ground height to set against the lagoon floor. The water stands at
     * {@code top - 2}: the ring's crest is three blocks over it, falling away in a rounded bank to the floor, except
     * where a channel cuts it down below the water; the islet is a mound five blocks high.
     */
    private double innerSurface(double distance, double in, double angle) {
        Inner ring = inner;
        double water = top - 2;
        double centre = in * ring.ring();
        double across = Math.abs(distance - centre) / ring.width();
        double bank = water + 3 * vertical - across * across * 3 * vertical;
        double gate = Math.max(channel(angle, ring.channel()), channel(angle, ring.channel() + ring.apart()));
        bank -= gate * 6 * vertical;
        double t = distance / ring.islet();
        double islet = water + 5 * vertical - t * t * 5 * vertical;
        return Math.max(bank, islet);
    }

    /** 1 in the middle of a channel at {@code bearing}, falling smoothly to 0 at its banks. */
    private static double channel(double angle, double bearing) {
        double off = Math.abs(Mth.wrapDegrees(Math.toDegrees(angle - bearing)));
        return smooth(1 - off / 9);
    }

    /** The islet's centre, for the tree that crowns it, or null on a single atoll. */
    @Nullable
    public net.minecraft.core.BlockPos isletCentre() {
        return inner == null ? null : new net.minecraft.core.BlockPos(x, top, z);
    }

    private static double smooth(double t) {
        t = Mth.clamp(t, 0, 1);
        return t * t * (3 - 2 * t);
    }
}
