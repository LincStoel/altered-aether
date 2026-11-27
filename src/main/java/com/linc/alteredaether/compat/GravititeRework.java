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

import com.aetherteam.aether.item.AetherCreativeTabs;
import com.linc.alteredaether.AlteredAether;
import com.linc.alteredaether.config.AACommonConfig;
import com.linc.alteredaether.registry.AARegistries;
import com.linc.alteredaether.registry.TabInserts;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Gravitite as a modern ore. Its data is the built-in {@code altered_aether:gravitite_rework} datapack (see
 * GravititeReworkPack and data/AAGravititeProvider); this is the part that can't be data: two recipes that would
 * undercut it are dropped after every recipe has loaded (the Aether's ore-to-block enchanting, and Deep Aether's
 * poisoning of the block back into ore, which the pack replaces with the ingot's), and the new items are listed in the
 * Aether's creative tabs, crushed raw gravitite only with Create and the dusts only with Oritech. With the rework off
 * ({@code gravitite.rework}), none of it happens and the items stay hidden.
 */
public final class GravititeRework {
    private static final Set<ResourceLocation> REMOVED = Set.of(
            ResourceLocation.fromNamespaceAndPath("aether", "enchanted_gravitite_enchanting"),
            ResourceLocation.fromNamespaceAndPath("deep_aether", "gravitite_ore_from_poison"));

    public static boolean active() {
        return AACommonConfig.GRAVITITE_REWORK.get();
    }

    private static boolean modOn(String mod) {
        return ModList.get().isLoaded(mod) && AACommonConfig.compat(mod);
    }

    /** The rework's ingredients in creative-tab order: crushed raw gravitite only with Create, the dusts only with Oritech. */
    public static void ingredients(Consumer<ItemLike> output) {
        output.accept(AARegistries.RAW_GRAVITITE.get());
        if (modOn(CreateCompat.MOD)) {
            output.accept(AARegistries.CRUSHED_RAW_GRAVITITE.get());
        }
        if (modOn("oritech")) {
            output.accept(AARegistries.GRAVITITE_DUST.get());
            output.accept(AARegistries.SMALL_GRAVITITE_DUST.get());
        }
        output.accept(AARegistries.ENCHANTED_GRAVITITE_NUGGET.get());
        output.accept(AARegistries.ENCHANTED_GRAVITITE_INGOT.get());
    }

    /** Game-bus events: the recipes. */
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
                        .filter(holder -> !REMOVED.contains(holder.id())).collect(Collectors.toList());
                if (kept.size() != recipes.getRecipes().size()) {
                    recipes.replaceRecipes(kept);
                }
            });
        }

        private Server() {
        }
    }

    /** Mod-bus events: the creative tabs. */
    @EventBusSubscriber(modid = AlteredAether.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
    public static final class Tabs {
        @SubscribeEvent
        public static void onCreativeTab(BuildCreativeModeTabContentsEvent event) {
            if (!active()) {
                return;
            }
            if (event.getTabKey().equals(AetherCreativeTabs.AETHER_NATURAL_BLOCKS.getKey())) {
                TabInserts.add(event, AARegistries.RAW_GRAVITITE_BLOCK_ITEM.get());
            }
            if (event.getTabKey().equals(AetherCreativeTabs.AETHER_INGREDIENTS.getKey())) {
                ingredients(item -> TabInserts.add(event, item));
            }
        }

        private Tabs() {
        }
    }

    private GravititeRework() {
    }
}
