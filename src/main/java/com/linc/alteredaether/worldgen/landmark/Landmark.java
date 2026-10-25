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
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;

import java.util.List;

/**
 * One landmark in a world, fixed by the seed. A landmark's shape is a pure function of its parameters, so the chunks it
 * spans can each build their own columns of it, in any order, and meet with no seams; see LandmarkFeature.
 */
public interface Landmark {
    LandmarkType type();

    int x();

    int z();

    /** The furthest any block of the landmark lies from (x, z), horizontally. */
    double extent();

    /** Builds the landmark's blocks in the chunk's own columns, and nowhere else. */
    default void build(WorldGenLevel level, ChunkPos chunk) {
    }

    /**
     * Where one kind of decoration goes, in block coordinates. A point whose y is {@link Integer#MIN_VALUE} has no
     * height of its own; the placement finds one.
     */
    default List<BlockPos> points(String kind) {
        return List.of();
    }

    /** The height worth teleporting to when visiting it. */
    default int visitY() {
        return Integer.MIN_VALUE;
    }
}
