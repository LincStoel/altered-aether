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
package com.linc.alteredaether.compat;

import com.linc.alteredaether.AlteredAether;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;

import java.lang.reflect.Field;

/**
 * Quark's crates: hundreds of slots, but a cap on the items in all of them ({@code CrateModule.maxItems}, 640 unless
 * configured), which slot sizes don't show. Read by reflection: Quark can't be on the compile classpath.
 */
public final class QuarkCrates {
    private static final String MODULE = "org.violetmoon.quark.addons.oddities.module.CrateModule";
    private static final int DEFAULT = 640;

    private static Field maxItems;
    private static boolean looked;

    private QuarkCrates() {
    }

    /** The most items the crate at {@code state} holds, or {@link Integer#MAX_VALUE} if it isn't a Quark crate. */
    public static int capacity(BlockState state) {
        if (!BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString().equals("quark:crate")) {
            return Integer.MAX_VALUE;
        }
        if (!looked) {
            looked = true;
            try {
                maxItems = Class.forName(MODULE).getField("maxItems");
            } catch (ReflectiveOperationException | LinkageError e) {
                AlteredAether.LOGGER.warn("Couldn't read how much Quark's crates hold; golems assume {}", DEFAULT, e);
            }
        }
        if (maxItems == null) {
            return DEFAULT;
        }
        try {
            return Math.max(1, maxItems.getInt(null));
        } catch (ReflectiveOperationException | RuntimeException e) {
            return DEFAULT;
        }
    }
}
