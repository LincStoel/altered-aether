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

import net.minecraft.world.item.CreativeModeTab.TabVisibility;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

import java.util.Set;

/**
 * Every item the mod adds to a creative tab it doesn't own (vanilla's, the Aether's, anyone's) goes in through here,
 * never through the event's own {@code accept} or {@code insertAfter}: those throw, and take the creative menu down,
 * if the anchor isn't in the tab or the item already is, and another mod or a pack's scripts can make either true. A
 * tab's own list and the search tab's are checked apart, since a mod can change one and not the other: an item already
 * in a list is left alone, and one whose anchor is gone goes at the end.
 */
public final class TabInserts {
    /** Adds {@code item} right after {@code anchor}, or at the end if the anchor isn't there. Returns the item, to chain on. */
    public static ItemLike after(BuildCreativeModeTabContentsEvent event, ItemLike anchor, ItemLike item) {
        place(event, event.getParentEntries(), TabVisibility.PARENT_TAB_ONLY, anchor, item);
        place(event, event.getSearchEntries(), TabVisibility.SEARCH_TAB_ONLY, anchor, item);
        return item;
    }

    /** Adds {@code item} at the end of the tab. */
    public static void add(BuildCreativeModeTabContentsEvent event, ItemLike item) {
        place(event, event.getParentEntries(), TabVisibility.PARENT_TAB_ONLY, null, item);
        place(event, event.getSearchEntries(), TabVisibility.SEARCH_TAB_ONLY, null, item);
    }

    private static void place(BuildCreativeModeTabContentsEvent event, Set<ItemStack> entries, TabVisibility visibility,
                              ItemLike anchor, ItemLike item) {
        ItemStack stack = new ItemStack(item);
        if (entries.contains(stack)) {
            return;
        }
        ItemStack after = anchor == null ? null : new ItemStack(anchor);
        if (after != null && entries.contains(after)) {
            event.insertAfter(after, stack, visibility);
        } else {
            event.accept(stack, visibility);
        }
    }

    private TabInserts() {
    }
}
