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

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.linc.alteredaether.worldgen.TerrainProbe;
import com.linc.alteredaether.worldgen.basin.BasinField;
import com.linc.alteredaether.worldgen.landmark.LandmarkField;
import it.unimi.dsi.fastutil.HashCommon;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.ExecutionException;

/**
 * Where caves may open ({@link CaveCarve}): inside thick island bodies only, with a roof of rock under the ground and
 * a floor of it over the underside, and never near a basin, lake, atoll or landmark, whose water or build a cave
 * would breach. Columns are read off the terrain before any cave on a 16-block grid; between grid columns the
 * tightest of the four corners holds, so a cave never strays past where every corner allows it.
 */
public final class CaveField {
    private static final int CELL = 16;
    /** Rock kept over a cave under the ground, and under it above the underside. */
    public static final int ROOF = 10, FLOOR = 8;
    /** Columns thinner than this get no caves; the weight eases in over the next {@link #EASE} blocks. */
    public static final int MIN_THICKNESS = 24, EASE = 8;
    /** Blocks kept clear round each site's extent. */
    private static final int GUARD = 16;
    private static final int SCAN_STEP = 6;
    /** A grid column with no ground. */
    private static final long EMPTY = Long.MIN_VALUE;

    private final TerrainProbe probe;
    @Nullable
    private final BasinField basins;
    @Nullable
    private final LandmarkField landmarks;
    /** Top, bottom and guard of each grid column, packed: top in the high int, bottom in the low, guard as a flag. */
    private final Cache<Long, long[]> columns = CacheBuilder.newBuilder().maximumSize(1 << 16).build();

    public CaveField(TerrainProbe probe, @Nullable BasinField basins, @Nullable LandmarkField landmarks) {
        this.probe = probe;
        this.basins = basins;
        this.landmarks = landmarks;
    }

    /**
     * The room a cave has at (x, y, z), 0 to 1: the column bounds are the tightest of the grid corners round it.
     * {@code entrance} (0 to 1) thins the roof, so here and there a cave reaches daylight.
     */
    public double weight(int x, int y, int z, double entrance) {
        int cx = Math.floorDiv(x, CELL), cz = Math.floorDiv(z, CELL);
        Cell cell = CELLS.get();
        if (cell.field != this || cell.cx != cx || cell.cz != cz) {
            cell.field = this;
            cell.cx = cx;
            cell.cz = cz;
            cell.open = true;
            cell.top = Integer.MAX_VALUE;
            cell.bottom = Integer.MIN_VALUE;
            for (int i = 0; i <= 1 && cell.open; i++) {
                for (int j = 0; j <= 1; j++) {
                    long[] column = column(cx + i, cz + j);
                    if (column[0] == EMPTY || column[2] == 0) {
                        cell.open = false;
                        break;
                    }
                    cell.top = Math.min(cell.top, (int) column[0]);
                    cell.bottom = Math.max(cell.bottom, (int) column[1]);
                }
            }
        }
        if (!cell.open) {
            return 0;
        }
        int top = cell.top, bottom = cell.bottom;
        double roof = ROOF - (ROOF + 2) * Mth.clamp(entrance, 0, 1);
        double thick = Mth.clamp((top - bottom - MIN_THICKNESS) / (double) EASE, 0, 1);
        return thick * Mth.clamp((top - roof - y) / 4.0, 0, 1) * Mth.clamp((y - bottom - FLOOR) / 4.0, 0, 1);
    }

    /** The grid cell this thread last asked about; NoiseChunk fills a chunk's points cell by cell. */
    private static final class Cell {
        CaveField field;
        int cx = Integer.MIN_VALUE, cz = Integer.MIN_VALUE, top, bottom;
        boolean open;
    }

    private static final ThreadLocal<Cell> CELLS = ThreadLocal.withInitial(Cell::new);

    private long[] column(int cx, int cz) {
        long key = ((long) cx << 32) ^ (cz & 0xFFFFFFFFL);
        try {
            return columns.get(HashCommon.mix(key), () -> solve(cx, cz));
        } catch (ExecutionException e) {
            throw new IllegalStateException(e.getCause());
        }
    }

    private long[] solve(int cx, int cz) {
        int x = cx * CELL, z = cz * CELL;
        int top = probe.surface(x, z);
        if (top == Integer.MIN_VALUE) {
            return new long[]{EMPTY, 0, 0};
        }
        return new long[]{top, lowest(x, z, top), guarded(x, z) ? 0 : 1};
    }

    /** The lowest solid block in the column, up to {@code top}. */
    private int lowest(int x, int z, int top) {
        int from = probe.minY();
        if (probe.solid(x, from, z)) {
            return from;
        }
        for (int y = from + SCAN_STEP; y <= top + SCAN_STEP - 1; y += SCAN_STEP) {
            int at = Math.min(y, top);
            if (probe.solid(x, at, z)) {
                int open = Math.max(from, at - SCAN_STEP), solid = at;
                while (solid - open > 1) {
                    int mid = (open + solid) >>> 1;
                    if (probe.solid(x, mid, z)) {
                        solid = mid;
                    } else {
                        open = mid;
                    }
                }
                return solid;
            }
        }
        return top;
    }

    /** Whether a site or landmark reaches within {@link #GUARD} blocks of the grid cell round (x, z). */
    private boolean guarded(int x, int z) {
        int minX = x - CELL - GUARD, minZ = z - CELL - GUARD, maxX = x + CELL + GUARD, maxZ = z + CELL + GUARD;
        return basins != null && !basins.sitesTouching(minX, minZ, maxX, maxZ).isEmpty()
                || landmarks != null && !landmarks.touching(minX, minZ, maxX, maxZ).isEmpty();
    }
}
