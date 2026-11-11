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
package com.linc.alteredaether.entity.golem;

import com.linc.alteredaether.AlteredAether;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TieredItem;
import net.minecraft.core.component.DataComponents;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.TagsUpdatedEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The families items are sorted in: tidy mode keeps a family to a chest, and a chest is ordered family by family.
 * The first of {@link #TAGS} an item is in, else what kind of thing it is (tool, armour, food, block), else the mod it
 * comes from. Creative tabs aren't used: a dedicated server never builds them.
 */
@EventBusSubscriber(modid = AlteredAether.MOD_ID)
public final class GolemGroups {
    private static final List<TagKey<Item>> TAGS = List.of(
            c("ores"), c("raw_materials"), c("ingots"), c("nuggets"), c("gems"), c("dusts"), c("storage_blocks"),
            c("tools"), c("armors"), c("music_discs"), c("potions"), c("dyes"), c("seeds"), c("crops"), c("foods"),
            ItemTags.LOGS, ItemTags.PLANKS, ItemTags.SAPLINGS, ItemTags.LEAVES, ItemTags.WOOL, ItemTags.WOOL_CARPETS,
            ItemTags.TERRACOTTA, c("glass_blocks"), c("glass_panes"), c("cobblestones"), c("stones"), c("sands"),
            c("gravels"), ItemTags.DIRT, ItemTags.FLOWERS, c("strings"), c("rods"), c("slime_balls"), c("bones"),
            c("feathers"), c("leathers"), c("eggs"), c("buckets"), c("enchantables"));
    private static final int TOOLS = TAGS.size(), ARMOR = TOOLS + 1, FOOD = TOOLS + 2, BLOCKS = TOOLS + 3, MODS = TOOLS + 4;

    private static final Map<Item, Integer> CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Integer> NAMESPACES = new HashMap<>();

    private static TagKey<Item> c(String path) {
        return TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", path));
    }

    private GolemGroups() {
    }

    /** The family {@code stack} sorts with; lower numbers sort first in a chest. */
    public static int group(ItemStack stack) {
        return CACHE.computeIfAbsent(stack.getItem(), item -> compute(stack));
    }

    private static int compute(ItemStack stack) {
        for (int i = 0; i < TAGS.size(); i++) {
            if (stack.is(TAGS.get(i))) {
                return i;
            }
        }
        Item item = stack.getItem();
        if (item instanceof TieredItem) {
            return TOOLS;
        }
        if (item instanceof ArmorItem) {
            return ARMOR;
        }
        if (stack.has(DataComponents.FOOD)) {
            return FOOD;
        }
        if (item instanceof BlockItem) {
            return BLOCKS;
        }
        String namespace = BuiltInRegistries.ITEM.getKey(item).getNamespace();
        synchronized (NAMESPACES) {
            return NAMESPACES.computeIfAbsent(namespace, n -> MODS + NAMESPACES.size());
        }
    }

    /** Where {@code stack} sorts within a chest: by family, then by item, then by its components. */
    public static long order(ItemStack stack) {
        long item = BuiltInRegistries.ITEM.getId(stack.getItem()) & 0xFFFFFL;
        long components = ItemStack.hashItemAndComponents(stack) & 0xFFFFFL;
        return ((long) group(stack) << 40) | (item << 20) | components;
    }

    @SubscribeEvent
    public static void onTagsUpdated(TagsUpdatedEvent event) {
        CACHE.clear();
    }
}
