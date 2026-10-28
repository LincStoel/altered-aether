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
 * A ring of land around a lagoon. Seen in profile: a bowl-shaped lagoon floor rising to a beach, a low sandy crest,
 * and an outer shore. Horizontal sizes are in blocks; vertical ones are scaled by {@code vertical} for the height mode.
 *
 * <p>An atoll lagoon is set into the land it stands in (see {@link #setIn()}): the ground is trimmed down to the
 * atoll's surface, and the atoll's own body, a shell sagging deepest under the lagoon, is joined smoothly to the
 * island beneath, so where the bowl goes deeper than the island the two underneaths blend instead of meeting in a
 * seam. A double atoll stands on its own instead, in a moat of open sky, with a cone of rock
 * hanging underneath.
 *
 * <p>A ring site is the other shape: a band of land following a {@link RingPath}, so it may be oval and twist as it
 * goes, with a walkable shore along both edges and a river running round its crown. Set into the land, the island
 * carries on inside and outside it.
 *
 * @param type   the lagoon's basin type, always an atoll lagoon
 * @param lagoon radius of the lagoon floor where it meets the beach; on a ring, the ring's half width
 * @param outer  radius where the outer shore ends; on a ring, the furthest its outer edge strays from the centre
 * @param top    height of the crest's base; the lagoon's water stands two blocks below it
 * @param inner  the second ring and islet of a double atoll, or null for a single ring
 * @param ring      the centre line of a ring with a river round it, or null for a ring around a lagoon
 * @param influence beyond the shore plus this, the atoll leaves the terrain alone
 */
public record AtollSite(BasinType type, int x, int z, double lagoon, double outer, int top, double depth,
                        double vertical, double phase1, double phase2, @Nullable Inner inner, @Nullable RingPath ring,
                        double influence) {
    /** The gap kept between a small atoll's land and a big one's. */
    static final double MOAT = 6;
    /** A double atoll's wider gap, so it stands alone in open sky. */
    static final double DOUBLE_MOAT = 22;
    /** The usual influence: room for the surrounding islands to fall away gently into the moat, or up to the shore. */
    static final double INFLUENCE = 26;
    /** A double atoll's influence, covering its wider moat. */
    static final double DOUBLE_INFLUENCE = 44;
    /** A set-in atoll's body: this deep under its surface at the shore, and this much more under its middle. */
    static final double SHELL = 4, SAG = 6;
    /** Beyond a set-in atoll's shore, the land is trimmed to a slope rising this many blocks per block. */
    static final double TRIM_SLOPE = 1.2;
    /** How softly a set-in atoll's body joins the island under it, in density units of a tenth of a block. */
    static final double BLEND = 0.8;

    public AtollSite(BasinType type, int x, int z, double lagoon, double outer, int top, double depth, double vertical,
                     double phase1, double phase2, @Nullable Inner inner, @Nullable RingPath ring) {
        this(type, x, z, lagoon, outer, top, depth, vertical, phase1, phase2, inner, ring, INFLUENCE);
    }

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

    public boolean isRing() {
        return ring != null;
    }

    /** Set into the land around it, rather than standing alone in a moat: every atoll lagoon but a double. */
    public boolean setIn() {
        return type == BasinType.ATOLL_LAGOON && inner == null;
    }

    /** The same site at another place and height. */
    AtollSite at(int x, int z, int top) {
        return new AtollSite(type, x, z, lagoon, outer, top, depth, vertical, phase1, phase2, inner, ring, influence);
    }

    public double extent() {
        return ring != null ? outer + influence : outer * BasinSite.MAX_WOBBLE + influence;
    }

    /**
     * A ring's river, as a fraction of the ring's half width either side of its centre line. Wide enough that the
     * terrain's eight-block interpolation still leaves it a proper channel.
     */
    static final double RIVER = 0.4;

    /**
     * Where a chest may lie on the bed, as {x, z}: the middle of a lagoon, where it is deepest; on a ring, a point
     * along its river; on a double atoll, the water between the islet and the inner ring, the heart of the ring within
     * a ring (or the outer lagoon, should that water be too narrow).
     */
    public int[] treasure() {
        if (ring != null) {
            double[] point = ring.pointAt(phase1 / Mth.TWO_PI);
            return new int[]{x + Mth.floor(point[0]), z + Mth.floor(point[1])};
        }
        if (inner != null) {
            double bearing = inner.channel() + inner.apart() / 2;
            double in = lagoon * BasinSite.wobble(bearing, phase1, phase2);
            double centre = in * inner.ring();
            double gap = centre - inner.width() - inner.islet();
            double r = gap >= 3 ? inner.islet() + gap / 2 : (centre + inner.width() + in) / 2;
            return new int[]{x + Mth.floor(Math.cos(bearing) * r), z + Mth.floor(Math.sin(bearing) * r)};
        }
        return new int[]{x, z};
    }

    /** The lagoon's water, or the ring's river, for the basin fill. */
    public BasinSite lagoonBasin() {
        if (ring != null) {
            return new BasinSite(type, x, z, RIVER * lagoon + 1, depth, top - 2, phase1, phase2, ring);
        }
        return new BasinSite(type, x, z, lagoon + 1, depth, top - 2, phase1, phase2);
    }

    /**
     * The atoll laid over the terrain around it: set into the land, or standing alone with the surrounding terrain
     * pushed back to leave a moat. {@code terrain} is the density the atoll replaces.
     */
    public double density(int px, int py, int pz, double terrain) {
        double dx = px - x, dz = pz - z;
        double distance = Math.sqrt(dx * dx + dz * dz);
        if (ring != null) {
            return ringDensity(dx, dz, distance, py, terrain);
        }
        double scale = BasinSite.wobble(Math.atan2(dz, dx), phase1, phase2);
        double in = lagoon * scale, out = outer * scale;
        if (distance > out + influence) {
            return terrain;
        }
        boolean setIn = setIn();
        // A set-in atoll's outer shore falls only to the level of the land around it.
        double fall = setIn ? 3 : 8;
        if (setIn && distance > out) {
            return setInDensity(top + (2 - fall) * vertical, distance - out, 0, py, terrain);
        }
        double surrounding = Math.min(terrain, 0.1 * (distance - out - DOUBLE_MOAT));
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
            surface = top + 2 * vertical - fall * vertical * smooth((distance - crest) / (out - crest));
        }
        double t = distance / out;
        if (setIn) {
            return setInDensity(surface, 0, 1 - t * t, py, terrain);
        }
        double underside = top - vertical * (10 + 42 * (1 - t * t));
        double ring = Mth.clamp(0.1 * Math.min(surface - py, py - underside), -1, 1);
        return Math.max(ring, surrounding);
    }

    /**
     * A ring set into the land: across it, a shore at each edge rounding off over the lip and a river down the
     * middle, with the island carrying on inside and outside. Only atoll lagoons are rings, so a ring is always set in.
     */
    private double ringDensity(double dx, double dz, double distance, int py, double terrain) {
        double width = lagoon;
        if (distance > ring.furthest() + width + influence || distance < ring.nearest() - width - influence) {
            return terrain;
        }
        double off = ring.distance(dx, dz);
        double water = top - 2;
        if (off > width) {
            return off > width + influence ? terrain
                    : setInDensity(water + 1 - vertical, off - width, 0, py, terrain);
        }
        // 0 on the ring's centre line, 1 at either edge
        double across = off / width;
        double surface;
        if (across < RIVER) {
            double t = across / RIVER;
            surface = water + 1 - depth * Math.pow(1 - t * t, 0.6);
        } else {
            surface = water + 1 + 2 * vertical * smooth((across - RIVER) / 0.12)
                    - 3 * vertical * smooth((across - 0.85) / 0.15);
        }
        return setInDensity(surface, 0, 1 - across * across, py, terrain);
    }

    /**
     * A set-in atoll over the land. The land is trimmed down to the atoll's surface, and beyond its shore to a slope
     * rising away from it, so nothing stands over the lagoon. Under the surface, the atoll's body is a shell joined
     * smoothly to the island: where the island is thick the shell is lost inside it, and where the bowl goes deeper
     * than the island the two undersides blend. Past the shore the body tapers to nothing, so its edge is no wall.
     *
     * @param surface the atoll's ground height here; beyond its shore, the height at the shore
     * @param past    how far beyond the shore this column is, 0 within it
     * @param sag     0 at the shore, rising to 1 where the body hangs deepest
     */
    private double setInDensity(double surface, double past, double sag, int py, double terrain) {
        double trim = 0.1 * (surface + TRIM_SLOPE * past - py);
        double bodyTop = surface - past, underside = surface - vertical * (SHELL + SAG * sag) + past;
        if (bodyTop <= underside) {
            return Math.min(terrain, trim);
        }
        double body = Mth.clamp(0.1 * Math.min(bodyTop - py, py - underside), -1, 1);
        return Math.min(smoothMax(terrain, body, BLEND), trim);
    }

    /** The larger of two densities, with the crease where they cross rounded off over {@code k}. */
    private static double smoothMax(double a, double b, double k) {
        double h = Mth.clamp(0.5 + 0.5 * (a - b) / k, 0, 1);
        return Mth.lerp(h, b, a) + k * h * (1 - h);
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
