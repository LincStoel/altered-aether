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
package com.linc.alteredaether.block;

import com.linc.alteredaether.AlteredAether;
import com.linc.alteredaether.compat.CreateCompat;
import com.linc.alteredaether.config.AACommonConfig;
import com.linc.alteredaether.registry.AARegistries;
import com.aetherteam.aether.block.AetherBlocks;
import com.aetherteam.aether.item.AetherCreativeTabs;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.BlockEntityTypeAddBlocksEvent;

import java.util.function.Consumer;

/** Mod-bus events for our blocks. */
@EventBusSubscriber(modid = AlteredAether.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class AABlockEvents {
    /** The campfire block entity cooks and smokes, so ours simply shares it. */
    @SubscribeEvent
    public static void onAddBlocks(BlockEntityTypeAddBlocksEvent event) {
        event.modify(BlockEntityType.CAMPFIRE, AARegistries.AMBROSIUM_CAMPFIRE.get());
    }

    /**
     * The campfire right after the soul campfire, gleamberries after glow berries wherever those are listed, and the
     * squall's and gravitite golem's spawn eggs with the others, and the whale whistle and storage pebble among the tools.
     */
    @SubscribeEvent
    public static void onCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(CreativeModeTabs.FUNCTIONAL_BLOCKS)) {
            event.insertAfter(Items.SOUL_CAMPFIRE.getDefaultInstance(),
                    AARegistries.AMBROSIUM_CAMPFIRE_ITEM.get().getDefaultInstance(),
                    net.minecraft.world.item.CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
        }
        if (event.getTabKey().equals(CreativeModeTabs.NATURAL_BLOCKS) || event.getTabKey().equals(CreativeModeTabs.FOOD_AND_DRINKS)) {
            event.insertAfter(Items.GLOW_BERRIES.getDefaultInstance(), AARegistries.GLEAMBERRIES.get().getDefaultInstance(),
                    net.minecraft.world.item.CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
        }
        if (event.getTabKey().equals(CreativeModeTabs.TOOLS_AND_UTILITIES)) {
            event.accept(AARegistries.WHALE_WHISTLE.get());
            event.accept(AARegistries.STORAGE_PEBBLE.get());
        }
        if (event.getTabKey().equals(CreativeModeTabs.SPAWN_EGGS)) {
            event.accept(AARegistries.SQUALL_SPAWN_EGG.get());
            event.accept(AARegistries.GRAVITITE_GOLEM_SPAWN_EGG.get());
        }
        stones(event);
    }

    /** The new stones follow holystone: the natural stone among the Aether's natural blocks, every block among its building blocks. */
    private static void stones(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(AetherCreativeTabs.AETHER_NATURAL_BLOCKS.getKey())) {
            ItemLike after = AetherBlocks.MOSSY_HOLYSTONE.get();
            for (StoneSet stone : StoneSet.ALL) {
                after = insertAfter(event, after, stone.natural.block().get());
            }
        }
        if (event.getTabKey().equals(AetherCreativeTabs.AETHER_BUILDING_BLOCKS.getKey())) {
            ItemLike after = AetherBlocks.HOLYSTONE_BRICK_WALL.get();
            for (StoneSet stone : StoneSet.ALL) {
                for (DeferredBlock<? extends Block> block : stone.blocks()) {
                    after = insertAfter(event, after, block.get());
                }
            }
        }
        if (event.getTabKey().equals(AetherCreativeTabs.AETHER_INGREDIENTS.getKey())) {
            nadiriteProcessing(event::accept);
        }
    }

    /** Nadirite's way to andesite (see data/AAStoneProvider), listed only with Create, the only way to start it. */
    public static void nadiriteProcessing(Consumer<ItemLike> output) {
        if (ModList.get().isLoaded(CreateCompat.MOD) && AACommonConfig.compat(CreateCompat.MOD)) {
            output.accept(AARegistries.CRUSHED_NADIRITE.get());
            output.accept(AARegistries.NADIRITE_GRIT.get());
            output.accept(AARegistries.AETHER_CRUST_MIX.get());
        }
    }

    /** Inserts {@code item} after {@code anchor}, or at the end if another mod has taken the anchor out of the tab. */
    private static ItemLike insertAfter(BuildCreativeModeTabContentsEvent event, ItemLike anchor, ItemLike item) {
        boolean present = event.getParentEntries().stream().anyMatch(stack -> stack.is(anchor.asItem()));
        if (present) {
            event.insertAfter(new ItemStack(anchor), new ItemStack(item), CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
        } else {
            event.accept(item);
        }
        return item;
    }

    private AABlockEvents() {
    }
}
