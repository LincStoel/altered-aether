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
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** The JEI side of {@link ElectricTour}: opens a category's page outright. Loaded only with JEI. */
final class ElectricTourJei {
    static void showType(ResourceLocation id) {
        var jei = GolemTourJei.runtime;
        if (jei == null) {
            AlteredAether.LOGGER.error("Electric tour: JEI isn't ready");
            return;
        }
        var type = jei.getJeiHelpers().getRecipeType(id);
        if (type.isEmpty()) {
            AlteredAether.LOGGER.error("Electric tour: JEI has no category {}", id);
            return;
        }
        int count = jei.getRecipeManager().createRecipeLookup(type.get()).get().toList().size();
        ElectricTour.log("JEI {} has {} recipes", id, count);
        jei.getRecipesGui().showTypes(List.of(type.get()));
    }

    private ElectricTourJei() {
    }
}
