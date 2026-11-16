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
package com.linc.alteredaether.worldgen.boulder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * The lumpy shape every boulder shares: two to four overlapping ellipsoids with a noisy surface, so no two look alike.
 * Used by the worldgen boulders, the ancient boulders and the boulders that fall (see compat/boulder).
 */
public final class BoulderShape {
    /** The most a boulder's radius may be: a feature may only write to the area around its origin. */
    public static final int MAX_RADIUS = 10;

    /** How tall a boulder of this radius is, squashed vertically by {@code squash}. */
    public static double height(int radius, double squash) {
        return Math.max(1.2, radius * squash);
    }

    /**
     * The lobes of a boulder: the main ellipsoid at the origin, then one to three more around it, each as
     * {@code {x, y, z, radius, height}} relative to the boulder's centre.
     */
    public static List<double[]> lobes(RandomSource random, int radius, double height, double squash) {
        List<double[]> lobes = new ArrayList<>();
        lobes.add(new double[]{0, 0, 0, radius, height});
        for (int i = random.nextInt(3) + 1; i > 0 && radius > 1; i--) {
            double angle = random.nextDouble() * Math.PI * 2;
            double offset = radius * (0.35 + random.nextDouble() * 0.45);
            double lobeRadius = radius * (0.5 + random.nextDouble() * 0.4);
            lobes.add(new double[]{Math.cos(angle) * offset, (random.nextDouble() - 0.5) * height * 0.4,
                    Math.sin(angle) * offset, lobeRadius, Math.max(1, lobeRadius * squash)});
        }
        return lobes;
    }

    /**
     * Carves the lobes out around {@code center}: every block inside them, within {@code reach} of {@code origin}
     * horizontally and accepted by {@code allowed}, with the state {@code state} gives it. The surface is rough: the
     * threshold wobbles per block, more on larger boulders.
     */
    public static Map<BlockPos, BlockState> carve(RandomSource random, List<double[]> lobes, BlockPos center,
                                                  BlockPos origin, double reach, int radius,
                                                  Predicate<BlockPos> allowed, Function<BlockPos, BlockState> state) {
        Map<BlockPos, BlockState> shape = new HashMap<>();
        for (double[] lobe : lobes) {
            int extent = (int) Math.ceil(lobe[3]) + 1;
            int vertical = (int) Math.ceil(lobe[4]) + 1;
            BlockPos lobeCenter = center.offset((int) Math.round(lobe[0]), (int) Math.round(lobe[1]),
                    (int) Math.round(lobe[2]));
            for (int dx = -extent; dx <= extent; dx++) {
                for (int dz = -extent; dz <= extent; dz++) {
                    for (int dy = -vertical; dy <= vertical; dy++) {
                        double distance = (dx * dx) / (lobe[3] * lobe[3]) + (dy * dy) / (lobe[4] * lobe[4])
                                + (dz * dz) / (lobe[3] * lobe[3]);
                        if (distance > 1.0 - random.nextDouble() * Math.min(0.35, 0.08 * radius)) {
                            continue;
                        }
                        BlockPos at = lobeCenter.offset(dx, dy, dz);
                        if (Math.abs(at.getX() - origin.getX()) <= reach && Math.abs(at.getZ() - origin.getZ()) <= reach
                                && allowed.test(at)) {
                            shape.put(at, state.apply(at));
                        }
                    }
                }
            }
        }
        return shape;
    }

    /** Whether the shape holds all six neighbours of {@code pos}: a block buried by the boulder itself. */
    public static boolean enclosed(Map<BlockPos, ?> shape, BlockPos pos) {
        for (Direction side : Direction.values()) {
            if (!shape.containsKey(pos.relative(side))) {
                return false;
            }
        }
        return true;
    }

    private BoulderShape() {
    }
}
