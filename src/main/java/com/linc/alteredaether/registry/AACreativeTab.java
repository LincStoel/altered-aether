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
package com.linc.alteredaether.registry;

import com.linc.alteredaether.block.AABlockEvents;
import com.linc.alteredaether.block.StoneSet;
import com.linc.alteredaether.compat.GravititeRework;
import com.linc.alteredaether.electric.ElectricRegistries;
import com.linc.alteredaether.essence.EssenceRegistries;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.registries.DeferredBlock;

/**
 * The mod's own creative tab, which lists everything the mod adds. The items are also slotted into the vanilla and
 * Aether tabs beside their kin (see block/AABlockEvents and compat/GravititeRework); this tab is the one place to
 * find them all. The gravitite rework's items are listed only while the rework is on, as elsewhere.
 */
final class AACreativeTab {
    static void displayItems(CreativeModeTab.ItemDisplayParameters parameters, CreativeModeTab.Output output) {
        output.accept(AARegistries.WHALE_WHISTLE.get());
        output.accept(AARegistries.STORAGE_PEBBLE.get());

        for (StoneSet stone : StoneSet.ALL) {
            for (DeferredBlock<? extends Block> block : stone.blocks()) {
                output.accept(block.get());
            }
        }

        AABlockEvents.nadiriteProcessing(output::accept);

        output.accept(AARegistries.AMBROSIUM_CAMPFIRE_ITEM.get());
        output.accept(AARegistries.GLEAMBERRIES.get());
        ElectricRegistries.creativeTab(output);
        EssenceRegistries.creativeTab(output);

        if (GravititeRework.active()) {
            output.accept(AARegistries.RAW_GRAVITITE_BLOCK_ITEM.get());
            GravititeRework.ingredients(output::accept);
        }

        output.accept(AARegistries.SQUALL_SPAWN_EGG.get());
        output.accept(AARegistries.GRAVITITE_GOLEM_SPAWN_EGG.get());
    }

    private AACreativeTab() {
    }
}
