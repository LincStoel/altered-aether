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
package com.linc.alteredaether.essence;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.Optional;

/**
 * Where the essence sources are: the Channels on Highlands islets and the Wellsprings over the landmarks, as the world
 * generates them. Only what the generator has already worked out is looked at, so this never loads anything.
 */
public final class EssenceSites {
    /** The nearest source to {@code from} within {@code range} blocks, if any is known. */
    public static Optional<BlockPos> nearest(ServerLevel level, BlockPos from, int range) {
        return Optional.empty();
    }

    private EssenceSites() {
    }
}
