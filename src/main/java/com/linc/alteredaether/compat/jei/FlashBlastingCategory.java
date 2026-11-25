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
import com.linc.alteredaether.electric.ElectricRegistries;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.drawable.IDrawableStatic;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;

import java.util.ArrayList;
import java.util.List;

/**
 * JEI's page for flash blasting: what a Squall Generator's spark turns into what. Its own recipes, then every vanilla
 * blasting recipe, which it does too (see electric/FlashBlasting).
 */
final class FlashBlastingCategory implements IRecipeCategory<FlashBlastingCategory.Flash> {
    record Flash(Ingredient input, ItemStack output) {
    }

    static final RecipeType<Flash> TYPE = RecipeType.create(AlteredAether.MOD_ID, "flash_blasting", Flash.class);
    private static final int WIDTH = 82, HEIGHT = 26;
    private static final int SLOT_Y = (HEIGHT - 18) / 2;

    private final IDrawable icon;
    private final IDrawableStatic arrow;

    private FlashBlastingCategory(IGuiHelper gui) {
        icon = gui.createDrawableItemStack(new ItemStack(ElectricRegistries.SQUALL_GENERATOR_ITEM.get()));
        arrow = gui.getRecipeArrow();
    }

    static void register(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(new FlashBlastingCategory(registration.getJeiHelpers().getGuiHelper()));
    }

    static void registerRecipes(IRecipeRegistration registration) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        RecipeManager recipes = level.getRecipeManager();
        List<Flash> all = new ArrayList<>();
        for (RecipeHolder<?> holder : recipes.getAllRecipesFor(ElectricRegistries.FLASH_BLASTING.get())) {
            all.add(new Flash(holder.value().getIngredients().get(0), holder.value().getResultItem(level.registryAccess())));
        }
        for (RecipeHolder<?> holder : recipes.getAllRecipesFor(net.minecraft.world.item.crafting.RecipeType.BLASTING)) {
            if (!holder.value().getIngredients().isEmpty()) {
                all.add(new Flash(holder.value().getIngredients().get(0),
                        holder.value().getResultItem(level.registryAccess())));
            }
        }
        registration.addRecipes(TYPE, all);
    }

    static void registerCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(new ItemStack(ElectricRegistries.SQUALL_GENERATOR_ITEM.get()), TYPE);
    }

    @Override
    public RecipeType<Flash> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("jei." + AlteredAether.MOD_ID + ".flash_blasting");
    }

    @Override
    public int getWidth() {
        return WIDTH;
    }

    @Override
    public int getHeight() {
        return HEIGHT;
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, Flash flash, IFocusGroup focuses) {
        builder.addSlot(RecipeIngredientRole.INPUT, 1, SLOT_Y + 1).setStandardSlotBackground()
                .addIngredients(flash.input());
        builder.addSlot(RecipeIngredientRole.OUTPUT, WIDTH - 17, SLOT_Y + 1).setOutputSlotBackground()
                .addItemStack(flash.output());
    }

    @Override
    public void draw(Flash flash, IRecipeSlotsView slots, GuiGraphics graphics, double mouseX, double mouseY) {
        arrow.draw(graphics, (WIDTH - arrow.getWidth()) / 2, SLOT_Y + (18 - arrow.getHeight()) / 2);
    }
}
