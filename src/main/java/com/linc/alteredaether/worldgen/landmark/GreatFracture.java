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
package com.linc.alteredaether.worldgen.landmark;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The Shattered Rift's landmark: a great fault through the rift. Inside a zone a few rift cells across, the ordinary
 * slabs are merged into a handful of giants by which side of the fault lines their cells' points fall on. Across the
 * main fault the two sides are heaved apart, one far up and one down, each tilted so its edge rears up over the
 * canyon between; a second, lesser fault may split them again. The canyon follows the old cell boundaries, so it is
 * jagged like the cracks around it, only much wider.
 *
 * <p>All of it is terrain, shaped by the rift's own density function (see VoronoiFracture), which asks the
 * {@link LandmarkField} for the fracture near each point. Only its decoration is placed afterward.
 *
 * @param radius     the zone's radius, in blocks: a rift cell joins the fracture if its point lies inside
 * @param normalX    the main fault's normal; the fault runs through the centre offset by {@code offset} along it
 * @param lifts      each slab's lift at its tilt's pivot, in tall-Aether blocks, indexed by {@link #group}
 * @param tilt       blocks of rise per block toward the main fault
 * @param mainWidth  the canyon's width
 * @param secondWidth the lesser fault's width
 * @param surface    the height of a giant's plate before it is lifted
 * @param thickness  the plate's thickness at the centre
 */
public record GreatFracture(int x, int z, double radius, double phase, double normalX, double normalZ, double offset,
                            boolean secondary, double secondX, double secondZ, double secondOffset, double[] lifts,
                            double tilt, double mainWidth, double secondWidth, double cellSize, int surface,
                            double thickness, long salt)
        implements Landmark {
    /** The rift's cell size in blocks before the config's multiplier, as its density function file sets it. */
    static final double RIFT_CELL = 48;
    private static final double MAX_RADIUS = 3.4;

    static int maxExtent(double scale) {
        return (int) Math.ceil((MAX_RADIUS * 1.15 + 1.6) * RIFT_CELL * scale);
    }

    @Nullable
    static GreatFracture tryAt(LandmarkField field, int x, int z, RandomSource random, double scale) {
        double cell = RIFT_CELL * scale;
        double radius = Mth.lerp(random.nextDouble(), 2.6, MAX_RADIUS) * cell;
        if (!field.ringSuits(LandmarkType.GREAT_FRACTURE, x, z, radius * 0.4, 7)
                || !field.ringSuits(LandmarkType.GREAT_FRACTURE, x, z, radius * 0.8, 6)) {
            return null;
        }
        double fault = random.nextDouble() * Math.PI;
        double second = fault + Mth.lerp(random.nextDouble(), 0.35, 0.65) * Math.PI;
        double up = Mth.lerp(random.nextDouble(), 22, 34), down = -Mth.lerp(random.nextDouble(), 8, 16);
        boolean flip = random.nextBoolean();
        double split = Mth.lerp(random.nextDouble(), 3, 7);
        double[] lifts = new double[4];
        for (int group = 0; group < 4; group++) {
            boolean high = ((group & 2) != 0) != flip;
            lifts[group] = (high ? up : down) + ((group & 1) != 0 ? split : -split) * (random.nextBoolean() ? 1 : -1);
        }
        // The plates sit where a rift island's top usually does: y 36 in the tall Aether, its stock equivalent.
        double v = field.vertical();
        int surface = field.probe().minY() + (int) Math.round(100 * v);
        return new GreatFracture(x, z, radius, random.nextDouble() * Mth.TWO_PI, Math.cos(fault), Math.sin(fault),
                (random.nextDouble() - 0.5) * cell * 0.5, random.nextDouble() < 0.7, Math.cos(second), Math.sin(second),
                (random.nextDouble() - 0.5) * cell, lifts, Mth.lerp(random.nextDouble(), 0.11, 0.18),
                Mth.lerp(random.nextDouble(), 9, 13), Mth.lerp(random.nextDouble(), 5, 7), cell, surface,
                Mth.lerp(random.nextDouble(), 24, 34) * v, random.nextLong());
    }

    /**
     * Which giant slab a rift cell joins, from where its point lies: bit 2 is the side of the main fault, bit 1 the
     * side of the lesser one. -1 if the point lies outside the zone, so the cell stays an ordinary slab.
     */
    public int group(double px, double pz) {
        double dx = px - x, dz = pz - z;
        double reach = radius * (1 + 0.15 * Math.sin(3 * Math.atan2(dz, dx) + phase));
        if (dx * dx + dz * dz > reach * reach) {
            return -1;
        }
        int side = dx * normalX + dz * normalZ - offset > 0 ? 2 : 0;
        if (secondary && dx * secondX + dz * secondZ - secondOffset > 0) {
            side |= 1;
        }
        return side;
    }

    /**
     * How far a giant slab is lifted at a block, in tall-Aether blocks: its own lift, plus a tilt that raises it
     * toward the canyon, pivoting two fifths of the way out, so its edge rears up over the chasm.
     */
    public double rise(int group, int bx, int bz) {
        double side = (group & 2) != 0 ? 1 : -1;
        double fromFault = side * ((bx - x) * normalX + (bz - z) * normalZ - offset);
        return lifts[group] + tilt * (radius * 0.4 - fromFault);
    }

    /**
     * A giant's plate as a density: the rift's islands are too broken to make a giant of on their own, so each giant
     * is underlain by a thick plate of rock, lifted and tilted with it ({@code rise}, in blocks) and thinning toward the
     * zone's edge, with {@code wave} (in blocks) rolling its top.
     */
    public double plate(int bx, int by, int bz, double rise, double wave) {
        double dx = bx - x, dz = bz - z;
        double t = Math.min(1, (dx * dx + dz * dz) / (radius * radius));
        double top = surface + rise + wave;
        double bottom = top - thickness * (1 - 0.6 * t);
        return Mth.clamp(0.08 * Math.min(top - by, by - bottom), -1, 1);
    }

    /** The crack width between two cells' slabs: the canyon, the lesser fault, or {@code ordinary}. */
    public double width(int group, int other, double ordinary) {
        if (group < 0 || other < 0) {
            return ordinary;
        }
        return ((group ^ other) & 2) != 0 ? mainWidth : secondWidth;
    }

    @Override
    public LandmarkType type() {
        return LandmarkType.GREAT_FRACTURE;
    }

    @Override
    public double extent() {
        return radius * 1.15 + 1.6 * cellSize;
    }

    /**
     * "chasm": spots along the canyon, for debris hanging in it; their height is found from the walls. "rim": spots
     * beside both faults, for bridges.
     */
    @Override
    public List<BlockPos> points(String kind) {
        List<BlockPos> points = new ArrayList<>();
        double tx = -normalZ, tz = normalX;
        double cx = x + normalX * offset, cz = z + normalZ * offset;
        switch (kind) {
            case "chasm" -> {
                int i = 0;
                for (double u = -radius * 0.9; u <= radius * 0.9; u += 7, i++) {
                    double across = (LandmarkField.hash(salt, i, 3, 0) - 0.5) * mainWidth * 0.6;
                    points.add(new BlockPos((int) Math.round(cx + tx * u + normalX * across), Integer.MIN_VALUE,
                            (int) Math.round(cz + tz * u + normalZ * across)));
                }
            }
            case "rim" -> {
                for (double u = -radius * 0.8; u <= radius * 0.8; u += 18) {
                    for (int side = -1; side <= 1; side += 2) {
                        double out = side * (mainWidth / 2 + 3);
                        points.add(new BlockPos((int) Math.round(cx + tx * u + normalX * out), Integer.MIN_VALUE,
                                (int) Math.round(cz + tz * u + normalZ * out)));
                    }
                }
                if (secondary) {
                    double sx = x + secondX * secondOffset, sz = z + secondZ * secondOffset;
                    for (double u = -radius * 0.8; u <= radius * 0.8; u += 18) {
                        for (int side = -1; side <= 1; side += 2) {
                            double out = side * (secondWidth / 2 + 3);
                            points.add(new BlockPos((int) Math.round(sx - secondZ * u + secondX * out), Integer.MIN_VALUE,
                                    (int) Math.round(sz + secondX * u + secondZ * out)));
                        }
                    }
                }
            }
            default -> {
            }
        }
        return points;
    }
}
