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
package com.linc.alteredaether.worldgen.feature;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;

/**
 * Whether a feature may touch a block, asked quietly. {@link WorldGenLevel#ensureCanWrite} answers the same question
 * but logs an error ("Detected setBlock in a far chunk") for every block out of reach, so it can't be used to test a
 * spot a feature might or might not reach into.
 */
public final class WriteBounds {
    /** How far from the chunk being decorated a feature may write, in chunks (vanilla's for the features step). */
    private static final int FEATURE_REACH = 1;

    public static boolean writable(WorldGenLevel level, BlockPos pos) {
        return reachable(level, pos.getX(), pos.getZ()) && level.ensureCanWrite(pos);
    }

    /** Whether the column at {@code (x, z)} lies within a feature's reach, so it may also be read. */
    public static boolean reachable(WorldGenLevel level, int x, int z) {
        if (level instanceof WorldGenRegion region) {
            ChunkPos centre = region.getCenter();
            return Math.abs((x >> 4) - centre.x) <= FEATURE_REACH && Math.abs((z >> 4) - centre.z) <= FEATURE_REACH;
        }
        return true;
    }

    private WriteBounds() {
    }
}
