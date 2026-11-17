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
package com.linc.alteredaether.debug;

import com.linc.alteredaether.AlteredAether;
import com.linc.alteredaether.registry.AARegistries;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.world.item.ItemStack;

/** The JEI side of {@link GolemTour}: opens the storage pebble's pages. Loaded only with JEI. */
final class GolemTourJei {
    static volatile IJeiRuntime runtime;

    private GolemTourJei() {
    }

    /** JEI's page for how to make {@code stack} ({@code recipes}), or for what it is used in. */
    static void show(ItemStack stack, boolean recipes) {
        IJeiRuntime jei = runtime;
        if (jei == null) {
            AlteredAether.LOGGER.error("Tour: JEI isn't ready");
            return;
        }
        var focus = jei.getJeiHelpers().getFocusFactory().createFocus(
                recipes ? RecipeIngredientRole.OUTPUT : RecipeIngredientRole.INPUT, VanillaTypes.ITEM_STACK, stack);
        jei.getRecipesGui().show(focus);
    }

    static void showPebble() {
        IJeiRuntime jei = runtime;
        if (jei == null) {
            AlteredAether.LOGGER.error("Golem tour: JEI isn't ready");
            return;
        }
        var focus = jei.getJeiHelpers().getFocusFactory().createFocus(RecipeIngredientRole.OUTPUT, VanillaTypes.ITEM_STACK,
                new ItemStack(AARegistries.STORAGE_PEBBLE.get()));
        jei.getRecipesGui().show(focus);
    }
}
