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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

import java.util.List;

/**
 * Stands our ambrosium campfire in for Aether: Beyond Parity's. Beyond Parity's is taken out of crafting and out of the
 * creative menu (and with them out of JEI and EMI, which list what those offer), and a recipe converts any that are
 * already made into ours. A campfire already placed in a world keeps working. The switch is
 * {@code compat.disable_beyond_parity_campfire}.
 *
 * <p>Nothing of Beyond Parity's is shipped or replaced: its recipe is dropped from the recipe manager after every
 * recipe has loaded, which keeps working whichever way the packs are ordered.
 */
public final class BeyondParityCompat {
    private static final String MOD = "aether_beyond_parity";
    private static final ResourceLocation CAMPFIRE = ResourceLocation.fromNamespaceAndPath(MOD, "ambrosium_campfire");

    private static boolean active() {
        return ModList.get().isLoaded(MOD) && AACommonConfig.DISABLE_BEYOND_PARITY_CAMPFIRE.get();
    }

    /** Game-bus events: the recipe. */
    @EventBusSubscriber(modid = AlteredAether.MOD_ID)
    public static final class Server {
        @SubscribeEvent
        public static void onReload(AddReloadListenerEvent event) {
            var recipes = event.getServerResources().getRecipeManager();
            // Registered after the recipe manager, so its recipes are in by the time this runs.
            event.addListener((ResourceManagerReloadListener) (ResourceManager manager) -> {
                if (!active()) {
                    return;
                }
                List<RecipeHolder<?>> kept = recipes.getRecipes().stream()
                        .filter(holder -> !holder.id().equals(CAMPFIRE)).collect(java.util.stream.Collectors.toList());
                if (kept.size() != recipes.getRecipes().size()) {
                    recipes.replaceRecipes(kept);
                    AlteredAether.LOGGER.info("Removed Aether: Beyond Parity's ambrosium campfire recipe");
                }
            });
        }

        private Server() {
        }
    }

    /** Mod-bus events: the creative menu. */
    @EventBusSubscriber(modid = AlteredAether.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
    public static final class Client {
        @SubscribeEvent
        public static void onCreativeTab(BuildCreativeModeTabContentsEvent event) {
            if (!active()) {
                return;
            }
            BuiltInRegistries.ITEM.getOptional(CAMPFIRE).ifPresent(item -> {
                ItemStack stack = item.getDefaultInstance();
                event.remove(stack, CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
                event.remove(stack, CreativeModeTab.TabVisibility.PARENT_TAB_ONLY);
                event.remove(stack, CreativeModeTab.TabVisibility.SEARCH_TAB_ONLY);
            });
        }

        private Client() {
        }
    }

    private BeyondParityCompat() {
    }
}
