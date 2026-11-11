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
package com.linc.alteredaether.compat.jei;

import com.linc.alteredaether.AlteredAether;
import com.linc.alteredaether.compat.CreateCompat;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import com.linc.alteredaether.registry.AARegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

import javax.annotation.Nullable;
import java.util.List;

/**
 * JEI pages for what this mod adds to other mods' machines: so far Create's bulk enchanting through an ambrosium
 * campfire ({@link FanEnchantingCategory}). Nothing here names a Create type, so the plugin loads without Create. Also
 * the gravitite golem's how-to, on its pebble and spawn egg.
 */
@JeiPlugin
public final class AAJeiPlugin implements IModPlugin {
    @Nullable
    private IRecipeCategory<?> fanEnchanting;

    @Override
    public ResourceLocation getPluginUid() {
        return AlteredAether.id("jei");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        fanEnchanting = null;
        if (ModList.get().isLoaded(CreateCompat.MOD)) {
            fanEnchanting = FanEnchantingCategory.create();
            registration.addRecipeCategories(fanEnchanting);
        }
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        if (fanEnchanting != null) {
            FanEnchantingCategory.registerRecipes(fanEnchanting, registration);
        }
        // how to build and use a gravitite golem, on its pebble and spawn egg
        Component[] golem = {info("build"), info("pebble"), info("controls"), info("more")};
        registration.addItemStackInfo(List.of(new ItemStack(AARegistries.STORAGE_PEBBLE.get()),
                new ItemStack(AARegistries.GRAVITITE_GOLEM_SPAWN_EGG.get())), golem);
    }

    private static Component info(String key) {
        return Component.translatable("jei." + AlteredAether.MOD_ID + ".golem." + key);
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        if (fanEnchanting != null) {
            FanEnchantingCategory.registerCatalysts(fanEnchanting, registration);
        }
    }
}
