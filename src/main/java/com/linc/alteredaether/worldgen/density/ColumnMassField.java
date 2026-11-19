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
package com.linc.alteredaether.worldgen.density;

import com.linc.alteredaether.worldgen.CellCache;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.DensityFunction;


/**
 * The thickness of solid ground in one density function, column by column on a 16-block grid, and its average over a
 * disc round each grid column ({@link IslandMass}). Read exactly from the function, so every chunk that asks gets the
 * same answer.
 */
public final class ColumnMassField {
    /** Thickness and mass are solved on this block grid. */
    private static final int CELL = 16;
    /** Vertical step of the thickness scan; the Aether's own noise cells are 4 blocks tall. */
    private static final int STEP = 4;

    private static final java.util.Map<net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>,
            ColumnMassField> FIELDS = new java.util.concurrent.ConcurrentHashMap<>();

    private final DensityFunction terrain;
    private final int minY, maxY;
    /** The disc's grid offsets. */
    private final int[][] disc;
    private final CellCache<Float> thickness = new CellCache<>(1 << 16);
    private final CellCache<Float> masses = new CellCache<>(1 << 16);

    public ColumnMassField(DensityFunction terrain, int radius, int minY, int maxY) {
        this.terrain = terrain;
        this.minY = minY;
        this.maxY = maxY;
        int r = (int) Math.ceil(radius / (double) CELL);
        java.util.List<int[]> offsets = new java.util.ArrayList<>();
        for (int i = -r; i <= r; i++) {
            for (int j = -r; j <= r; j++) {
                if ((i * i + j * j) * CELL * CELL <= radius * radius) {
                    offsets.add(new int[]{i, j});
                }
            }
        }
        this.disc = offsets.toArray(int[][]::new);
    }

    public static ColumnMassField get(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> level) {
        return FIELDS.get(level);
    }

    public static void put(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> level,
                           ColumnMassField field) {
        if (field == null) {
            FIELDS.remove(level);
        } else {
            FIELDS.put(level, field);
        }
    }

    /** The average thickness of ground round (x, z), interpolated between grid columns. */
    public double mass(int x, int z) {
        int cx = Math.floorDiv(x, CELL), cz = Math.floorDiv(z, CELL);
        double fx = (x - cx * CELL) / (double) CELL, fz = (z - cz * CELL) / (double) CELL;
        return Mth.lerp2(fx, fz, gridMass(cx, cz), gridMass(cx + 1, cz), gridMass(cx, cz + 1),
                gridMass(cx + 1, cz + 1));
    }

    /** The thickness of ground in one grid column. */
    public double thickness(int x, int z) {
        return gridThickness(Math.floorDiv(x, CELL), Math.floorDiv(z, CELL));
    }

    private float gridMass(int cx, int cz) {
        long key = ((long) cx << 32) ^ (cz & 0xFFFFFFFFL);
        return masses.get(key, k -> {
            float sum = 0;
            for (int[] o : disc) {
                sum += gridThickness(cx + o[0], cz + o[1]);
            }
            return sum / disc.length;
        });
    }

    private float gridThickness(int cx, int cz) {
        long key = ((long) cx << 32) ^ (cz & 0xFFFFFFFFL);
        return thickness.get(key, k -> {
            int x = cx * CELL + CELL / 2, z = cz * CELL + CELL / 2, solid = 0;
            for (int y = minY; y < maxY; y += STEP) {
                if (terrain.compute(new DensityFunction.SinglePointContext(x, y, z)) > 0) {
                    solid++;
                }
            }
            return (float) solid * STEP;
        });
    }
}
