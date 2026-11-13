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
import com.linc.alteredaether.config.AACommonConfig;
import net.minecraft.world.Container;
import net.neoforged.fml.ModList;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Quark's own container sort, for the golem to tidy within a chest the way a player's sort button would. Reached by
 * reflection: Quark can't be on the compile classpath, as it breaks the GameTest server.
 */
public final class QuarkSorting {
    public static final String MOD = "quark";
    private static final String HANDLER = "org.violetmoon.quark.base.handler.SortingHandler";

    /** Quark's {@code SortingHandler.sortContainer(Container, int[])}, or null without Quark or if it changed. */
    private static MethodHandle sort;
    private static boolean looked;

    private QuarkSorting() {
    }

    /** Whether Quark is there to sort with. */
    public static boolean available() {
        if (!ModList.get().isLoaded(MOD) || !AACommonConfig.compat(MOD)) {
            return false;
        }
        if (!looked) {
            looked = true;
            try {
                Class<?> handler = Class.forName(HANDLER);
                sort = MethodHandles.publicLookup().findStatic(handler, "sortContainer",
                        MethodType.methodType(void.class, Container.class, int[].class));
            } catch (ReflectiveOperationException | LinkageError e) {
                AlteredAether.LOGGER.warn("Couldn't find Quark's container sort; golems sort chests their own way", e);
            }
        }
        return sort != null;
    }

    /** Sorts {@code container} as Quark's sort button would; false if that failed, to sort it another way. */
    public static boolean sort(Container container) {
        if (!available()) {
            return false;
        }
        try {
            sort.invokeExact(container, new int[0]);
            return true;
        } catch (Throwable e) {
            AlteredAether.LOGGER.warn("Quark's container sort failed; golems sort chests their own way from now on", e);
            sort = null;
            return false;
        }
    }
}
