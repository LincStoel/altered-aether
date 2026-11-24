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
import com.linc.alteredaether.config.AACommonConfig;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import com.linc.alteredaether.registry.AARegistries;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * JEI pages for what this mod adds to other mods' machines: so far Create's bulk enchanting through an ambrosium
 * campfire ({@link FanEnchantingCategory}). Nothing here names a Create type, so the plugin loads without Create. Also
 * the gravitite golem's guide, on its pebble and spawn egg, how a pebble is bought ({@link GolemPebbleCategory}), and
 * the Golem Chest Network screen's search, lookups and "+" ({@link GolemNetworkJei}).
 */
@JeiPlugin
public final class AAJeiPlugin implements IModPlugin {
    private static final String GUIDE = "jei." + AlteredAether.MOD_ID + ".golem.guide.";
    @Nullable
    private IRecipeCategory<?> fanEnchanting;

    @Override
    public ResourceLocation getPluginUid() {
        return AlteredAether.id("jei");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        GolemPebbleCategory.register(registration);
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
        GolemPebbleCategory.registerRecipes(registration);
        // the gravitite golem guide, on its pebble and spawn egg: everything a golem answers to
        registration.addItemStackInfo(List.of(new ItemStack(AARegistries.STORAGE_PEBBLE.get()),
                new ItemStack(AARegistries.GRAVITITE_GOLEM_SPAWN_EGG.get())), golemGuide());
    }

    /** The guide: a line of what a golem is, then one entry for each thing it answers to, a heading and what it does. */
    private static Component[] golemGuide() {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable(GUIDE + "intro"));
        boolean create = ModList.get().isLoaded(CreateCompat.MOD);
        List<String> keys = new ArrayList<>(List.of("build", "hand", "pebble.take", "tidy", "pebble.storage", "pebble.golem",
                "pebble.send", "search", "sorting", "dye", "frames"));
        if (ModList.get().isLoaded("supplementaries")) {
            keys.add("frames.shelf");
        }
        if (create) {
            keys.add("frames.create");
        }
        keys.add("drawers");
        if (ModList.get().isLoaded("quark")) {
            keys.add("crate");
        }
        if (create) {
            keys.add("funnels");
        }
        keys.addAll(List.of("redstone", "comparator", "source", "health"));
        for (String key : keys) {
            // heading and text side by side, so the text keeps the page's own colour
            lines.add(Component.empty().append(Component.translatable(GUIDE + key + ".title").withStyle(ChatFormatting.DARK_PURPLE))
                    .append(Component.translatable(GUIDE + key, AACommonConfig.GOLEM_MAX_NETWORK.get())));
        }
        return lines.toArray(Component[]::new);
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        if (fanEnchanting != null) {
            FanEnchantingCategory.registerCatalysts(fanEnchanting, registration);
        }
        GolemPebbleCategory.registerCatalysts(registration);
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        GolemNetworkJei.registerGuiHandlers(registration);
    }

    @Override
    public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
        GolemNetworkJei.registerTransfer(registration);
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime runtime) {
        GolemNetworkJei.runtimeAvailable(runtime);
    }

    @Override
    public void onRuntimeUnavailable() {
        GolemNetworkJei.runtimeUnavailable();
    }
}
