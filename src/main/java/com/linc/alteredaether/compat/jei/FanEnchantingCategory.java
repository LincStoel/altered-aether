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

import com.aetherteam.aether.recipe.AetherRecipeTypes;
import com.aetherteam.aether.recipe.recipes.item.AbstractAetherCookingRecipe;
import com.aetherteam.aether.recipe.recipes.item.AltarRepairRecipe;
import com.linc.alteredaether.AlteredAether;
import com.linc.alteredaether.registry.AARegistries;
import com.simibubi.create.compat.jei.category.CreateRecipeCategory;
import com.simibubi.create.compat.jei.category.ProcessingViaFanCategory;
import com.simibubi.create.compat.jei.category.animations.AnimatedKinetics;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.createmod.catnip.gui.element.GuiGameElement;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.CampfireBlock;

/**
 * JEI's page for Create's bulk enchanting (see CreateCompat): Create's own fan page, with a lit ambrosium campfire in
 * the air stream, listing the altar's enchanting recipes that the fan runs. Its repairs, which the fan leaves alone, are
 * left out. Loaded only when Create is.
 */
final class FanEnchantingCategory extends ProcessingViaFanCategory<AbstractAetherCookingRecipe> {
    /** The page's name, which also names its title ({@code altered_aether.recipe.fan_enchanting}) and fan tooltip. */
    static final String NAME = "fan_enchanting";

    private FanEnchantingCategory(Info<AbstractAetherCookingRecipe> info) {
        super(info);
    }

    @SuppressWarnings("unchecked")
    static IRecipeCategory<?> create() {
        return new CreateRecipeCategory.Builder<>(AbstractAetherCookingRecipe.class)
                .addTypedRecipesIf(() -> (RecipeType<AbstractAetherCookingRecipe>) AetherRecipeTypes.ENCHANTING.get(),
                        holder -> !(holder.value() instanceof AltarRepairRecipe))
                .removeNonAutomation()
                .catalystStack(ProcessingViaFanCategory.getFan(NAME))
                .catalyst(AARegistries.AMBROSIUM_CAMPFIRE::get)
                .doubleItemIcon(ProcessingViaFanCategory.getFan(NAME).get().getItem(), AARegistries.AMBROSIUM_CAMPFIRE.get())
                .emptyBackground(178, 72)
                .build(AlteredAether.id(NAME), FanEnchantingCategory::new);
    }

    static void registerRecipes(IRecipeCategory<?> category, IRecipeRegistration registration) {
        ((CreateRecipeCategory<?>) category).registerRecipes(registration);
    }

    static void registerCatalysts(IRecipeCategory<?> category, IRecipeCatalystRegistration registration) {
        ((CreateRecipeCategory<?>) category).registerCatalysts(registration);
    }

    @Override
    protected AllGuiTextures getBlockShadow() {
        return AllGuiTextures.JEI_LIGHT;
    }

    @Override
    protected void renderAttachedBlock(GuiGraphics graphics) {
        GuiGameElement.of(AARegistries.AMBROSIUM_CAMPFIRE.get().defaultBlockState().setValue(CampfireBlock.LIT, true))
                .scale(SCALE)
                .atLocal(0, 0, 2)
                .lighting(AnimatedKinetics.DEFAULT_LIGHTING)
                .render(graphics);
    }
}
