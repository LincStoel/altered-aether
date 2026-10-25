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

import com.linc.alteredaether.worldgen.basin.AtollSite;
import net.minecraft.core.BlockPos;

import java.util.List;

/**
 * A double atoll, seen as a landmark. Its rings are terrain, shaped by the atoll density function, and its lagoon is
 * filled with the rest of the basins; this only marks its islet, where a golden oak grows.
 */
record DoubleAtoll(AtollSite site) implements Landmark {
    @Override
    public LandmarkType type() {
        return LandmarkType.DOUBLE_ATOLL;
    }

    @Override
    public int x() {
        return site.x();
    }

    @Override
    public int z() {
        return site.z();
    }

    @Override
    public double extent() {
        return site.extent();
    }

    @Override
    public int visitY() {
        return site.top() + 24;
    }

    /** "islet": the islet's centre. */
    @Override
    public List<BlockPos> points(String kind) {
        BlockPos islet = site.isletCentre();
        return kind.equals("islet") && islet != null ? List.of(islet.atY(Integer.MIN_VALUE)) : List.of();
    }
}
