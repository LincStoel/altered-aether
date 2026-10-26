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
package com.linc.alteredaether.worldgen.grove;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootTable;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * One column of a giant's furnishing, placed after its wood: a lamp post, a hanging lantern, a campfire, a chest. A
 * prop finds its footing in its own column only, so like the wood it comes out the same whichever chunk builds it.
 *
 * @param blocks bottom first for {@link Anchor#GROUND} and {@link Anchor#AT}, top first for {@link Anchor#HANG}
 * @param loot   the loot table of a container prop's first block, if it has one
 */
public record Prop(int x, int y, int z, Anchor anchor, List<BlockState> blocks,
                   @Nullable ResourceKey<LootTable> loot) {
    public enum Anchor {
        /** Exactly at y, into air or anything replaceable. */
        AT,
        /** Standing on the first solid top found scanning down from a little above y. */
        GROUND,
        /** Hanging from the first solid underside found scanning up from y. */
        HANG,
        /**
         * Like {@link #GROUND}, but the first block replaces the solid top itself: soil gathered on a fallen log, for
         * what grows in it.
         */
        TOPSOIL
    }

    /** How far a prop searches for its footing. */
    static final int REACH = 8;
}
