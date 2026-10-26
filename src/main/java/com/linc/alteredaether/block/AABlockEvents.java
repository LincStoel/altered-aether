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
import com.linc.alteredaether.registry.AARegistries;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.BlockEntityTypeAddBlocksEvent;

/** Mod-bus events for our blocks. */
@EventBusSubscriber(modid = AlteredAether.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class AABlockEvents {
    /** The campfire block entity cooks and smokes, so ours simply shares it. */
    @SubscribeEvent
    public static void onAddBlocks(BlockEntityTypeAddBlocksEvent event) {
        event.modify(BlockEntityType.CAMPFIRE, AARegistries.AMBROSIUM_CAMPFIRE.get());
    }

    /** The campfire right after the soul campfire, and gleamberries after glow berries wherever those are listed. */
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
    }

    private AABlockEvents() {
    }
}
