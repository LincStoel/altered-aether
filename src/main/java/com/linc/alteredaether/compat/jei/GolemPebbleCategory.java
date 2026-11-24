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
import com.linc.alteredaether.entity.golem.GolemInteractions;
import com.linc.alteredaether.entity.golem.GravititeGolem;
import com.linc.alteredaether.registry.AARegistries;
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
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.List;

/**
 * JEI's page for buying a storage pebble: an Aether stone or glowstone dust, used on a gravitite golem (drawn turning in
 * the middle, as the crafting table of the trade), gives a pebble for its network. Laid out like Create's mysterious
 * item conversion: what goes in, an arrow, the golem, an arrow, what comes out.
 */
final class GolemPebbleCategory implements IRecipeCategory<GolemPebbleCategory.Trade> {
    /** The trade has nothing to say beyond what the page shows. */
    record Trade() {
    }

    static final RecipeType<Trade> TYPE = RecipeType.create(AlteredAether.MOD_ID, "golem_pebble", Trade.class);
    private static final int WIDTH = 130, HEIGHT = 56;
    private static final int SLOT_Y = (HEIGHT - 18) / 2;
    private static final int GOLEM_X = WIDTH / 2, GOLEM_FEET = HEIGHT - 8;
    private static final float GOLEM_SCALE = 20.0F;

    private final IDrawable icon;
    private final IDrawableStatic arrow;
    /** A golem to draw, made for the level the player is in (a page can outlive it). */
    @Nullable
    private GravititeGolem golem;

    private GolemPebbleCategory(IGuiHelper gui) {
        icon = gui.createDrawableItemStack(new ItemStack(AARegistries.STORAGE_PEBBLE.get()));
        arrow = gui.getRecipeArrow();
    }

    static void register(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(new GolemPebbleCategory(registration.getJeiHelpers().getGuiHelper()));
    }

    static void registerRecipes(IRecipeRegistration registration) {
        registration.addRecipes(TYPE, List.of(new Trade()));
    }

    static void registerCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(new ItemStack(AARegistries.GRAVITITE_GOLEM_SPAWN_EGG.get()), TYPE);
    }

    @Override
    public RecipeType<Trade> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("jei." + AlteredAether.MOD_ID + ".golem_pebble");
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
    public void setRecipe(IRecipeLayoutBuilder builder, Trade trade, IFocusGroup focuses) {
        builder.addSlot(RecipeIngredientRole.INPUT, 1, SLOT_Y + 1).setStandardSlotBackground()
                .addIngredients(Ingredient.of(GolemInteractions.PEBBLE_PRICE));
        builder.addSlot(RecipeIngredientRole.OUTPUT, WIDTH - 17, SLOT_Y + 1).setOutputSlotBackground()
                .addItemStack(new ItemStack(AARegistries.STORAGE_PEBBLE.get()));
    }

    @Override
    public void draw(Trade trade, IRecipeSlotsView slots, GuiGraphics graphics, double mouseX, double mouseY) {
        int arrowY = SLOT_Y + (18 - arrow.getHeight()) / 2;
        arrow.draw(graphics, 22, arrowY);
        arrow.draw(graphics, WIDTH - 22 - arrow.getWidth(), arrowY);
        GravititeGolem shown = golem();
        if (shown == null) {
            return;
        }
        // a slow turn, a quarter of the way round to either side
        float time = (System.currentTimeMillis() % 8000L) / 8000.0F;
        float yaw = Mth.sin(time * Mth.TWO_PI) * 45.0F;
        Quaternionf pose = new Quaternionf().rotateZ(Mth.PI).rotateY(yaw * Mth.DEG_TO_RAD);
        Quaternionf camera = new Quaternionf().rotateX(8.0F * Mth.DEG_TO_RAD);
        pose.mul(camera);
        shown.setYBodyRot(180.0F);
        shown.setYHeadRot(180.0F);
        shown.yBodyRotO = 180.0F;
        shown.yHeadRotO = 180.0F;
        shown.setXRot(0);
        InventoryScreen.renderEntityInInventory(graphics, GOLEM_X, GOLEM_FEET, GOLEM_SCALE, new Vector3f(), pose, camera,
                shown);
    }

    @Nullable
    private GravititeGolem golem() {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            golem = null;
            return null;
        }
        if (golem == null || golem.level() != level) {
            golem = AARegistries.GRAVITITE_GOLEM.get().create(level);
        }
        return golem;
    }
}
