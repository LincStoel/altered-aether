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
package com.linc.alteredaether.compat.boulder;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Where a falling boulder may land and leave blocks: not on a structure, and not in land somebody has claimed with a
 * claim mod and protected from griefing. Claim mods plug in with {@link #add}, so the boulders never name their classes
 * (see {@link FtbChunksProtection}).
 */
public final class BoulderProtection {
    /** One reason a spot is off limits. */
    public interface Check {
        /** Whether a boulder may be dropped on, or settle at, this block. */
        boolean allowed(ServerLevel level, BlockPos pos);
    }

    private static final List<Check> CHECKS = new CopyOnWriteArrayList<>();

    static {
        CHECKS.add((level, pos) -> {
            try {
                return !nearStructure(level, pos);
            } catch (RuntimeException e) {
                // the structure data for that chunk isn't there to ask
                return true;
            }
        });
    }

    /**
     * Whether a piece of a structure is within a few blocks of {@code pos}. The structure manager's own
     * {@code hasAnyStructureAt} is true for any chunk a structure's reach merely touches, which in the Aether's
     * big structures covered most of the land and made boulders vanish.
     */
    private static boolean nearStructure(ServerLevel level, BlockPos pos) {
        for (StructureStart start : level.structureManager().startsForStructure(new ChunkPos(pos), s -> true)) {
            if (!start.isValid() || !start.getBoundingBox().inflatedBy(4).isInside(pos)) {
                continue;
            }
            for (StructurePiece piece : start.getPieces()) {
                if (piece.getBoundingBox().inflatedBy(4).isInside(pos)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static void add(Check check) {
        CHECKS.add(check);
    }

    public static void remove(Check check) {
        CHECKS.remove(check);
    }

    /** Whether a boulder may land at {@code pos}. */
    public static boolean allowed(ServerLevel level, BlockPos pos) {
        for (Check check : CHECKS) {
            if (!check.allowed(level, pos)) {
                return false;
            }
        }
        return true;
    }

    /** Whether a boulder may be dropped over {@code pos}: it and the chunks around it, since a boulder rolls. */
    public static boolean allowedAround(ServerLevel level, BlockPos pos) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (!allowed(level, pos.offset(dx * 16, 0, dz * 16))) {
                    return false;
                }
            }
        }
        return true;
    }

    private BoulderProtection() {
    }
}
