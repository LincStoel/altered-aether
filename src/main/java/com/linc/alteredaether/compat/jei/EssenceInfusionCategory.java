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
import com.linc.alteredaether.electric.AetherJarItem;
import com.linc.alteredaether.essence.EssenceInfusionRecipe;
import com.linc.alteredaether.essence.EssenceRegistries;
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
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.List;

/**
 * JEI's page for essence infusion: what an essence plinth under a source turns into what, and how much Æ it takes
 * (see essence/EssenceInfusionRecipe).
 */
final class EssenceInfusionCategory implements IRecipeCategory<EssenceInfusionRecipe> {
    static final RecipeType<EssenceInfusionRecipe> TYPE =
            RecipeType.create(AlteredAether.MOD_ID, "essence_infusion", EssenceInfusionRecipe.class);
    private static final String KEY = "jei." + AlteredAether.MOD_ID + ".essence_infusion";
    private static final int WIDTH = 100, HEIGHT = 36;
    private static final int SLOT_Y = 2;

    private final IDrawable icon;
    private final IDrawableStatic arrow;

    private EssenceInfusionCategory(IGuiHelper gui) {
        icon = gui.createDrawableItemStack(new ItemStack(EssenceRegistries.ESSENCE_PLINTH_ITEM.get()));
        arrow = gui.getRecipeArrow();
    }

    static void register(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(new EssenceInfusionCategory(registration.getJeiHelpers().getGuiHelper()));
    }

    static void registerRecipes(IRecipeRegistration registration) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        List<EssenceInfusionRecipe> all = level.getRecipeManager()
                .getAllRecipesFor(EssenceRegistries.ESSENCE_INFUSION.get()).stream().map(RecipeHolder::value).toList();
        registration.addRecipes(TYPE, all);
    }

    static void registerCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(new ItemStack(EssenceRegistries.ESSENCE_PLINTH_ITEM.get()), TYPE);
        registration.addRecipeCatalyst(new ItemStack(EssenceRegistries.ESSENCE_CAPSTONE_ITEM.get()), TYPE);
    }

    @Override
    public RecipeType<EssenceInfusionRecipe> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable(KEY);
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
    public void setRecipe(IRecipeLayoutBuilder builder, EssenceInfusionRecipe recipe, IFocusGroup focuses) {
        builder.addSlot(RecipeIngredientRole.INPUT, 10, SLOT_Y + 1).setStandardSlotBackground()
                .addIngredients(recipe.ingredient());
        builder.addSlot(RecipeIngredientRole.OUTPUT, WIDTH - 27, SLOT_Y + 1).setOutputSlotBackground()
                .addItemStack(recipe.result());
    }

    @Override
    public void draw(EssenceInfusionRecipe recipe, IRecipeSlotsView slots, GuiGraphics graphics, double mouseX,
                     double mouseY) {
        arrow.draw(graphics, (WIDTH - arrow.getWidth()) / 2, SLOT_Y + (18 - arrow.getHeight()) / 2);
        Component cost = Component.translatable(KEY + ".cost", AetherJarItem.fe(recipe.cost()));
        var font = Minecraft.getInstance().font;
        graphics.drawString(font, cost, (WIDTH - font.width(cost)) / 2, SLOT_Y + 22, 0xFF808080, false);
        if (recipe.keepSource() && mouseX >= WIDTH - 27 && mouseX < WIDTH - 9 && mouseY >= SLOT_Y && mouseY < SLOT_Y + 20) {
            graphics.renderTooltip(font, Component.translatable(KEY + ".source").withStyle(ChatFormatting.GRAY),
                    (int) mouseX, (int) mouseY);
        }
    }
}
