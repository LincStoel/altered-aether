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
package com.linc.alteredaether.client.golem;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.fml.ModList;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * The Golem Chest Network screen's search, in JEI's syntax: words separated by spaces must all match. A word matches
 * an item's name; {@code @word} its mod (id or name), {@code #word} a line of its tooltip, {@code $word} one of its
 * tags; a word starting {@code -} must not match. Case is ignored.
 */
final class ItemSearch {
    private ItemSearch() {
    }

    /** What {@code text} lets through; everything when it is blank. */
    static Predicate<ItemStack> parse(String text) {
        List<Predicate<ItemStack>> words = new ArrayList<>();
        for (String word : text.toLowerCase(Locale.ROOT).trim().split("\\s+")) {
            Predicate<ItemStack> test = word(word);
            if (test != null) {
                words.add(test);
            }
        }
        return stack -> {
            for (Predicate<ItemStack> word : words) {
                if (!word.test(stack)) {
                    return false;
                }
            }
            return true;
        };
    }

    @Nullable
    private static Predicate<ItemStack> word(String word) {
        boolean negate = word.startsWith("-");
        if (negate) {
            word = word.substring(1);
        }
        if (word.isEmpty()) {
            return null;
        }
        char prefix = word.charAt(0);
        String rest = word.substring(1);
        Predicate<ItemStack> test;
        if (prefix == '@' || prefix == '#' || prefix == '$') {
            if (rest.isEmpty()) {
                return null;
            }
            test = switch (prefix) {
                case '@' -> stack -> mod(stack).contains(rest) || modName(stack).contains(rest);
                case '#' -> stack -> tooltip(stack, rest);
                default -> stack -> tag(stack.getItem(), rest);
            };
        } else {
            String name = word;
            test = stack -> stack.getHoverName().getString().toLowerCase(Locale.ROOT).contains(name);
        }
        return negate ? test.negate() : test;
    }

    private static String mod(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace();
    }

    private static String modName(ItemStack stack) {
        return ModList.get().getModContainerById(mod(stack))
                .map(container -> container.getModInfo().getDisplayName().toLowerCase(Locale.ROOT)).orElse("");
    }

    private static boolean tooltip(ItemStack stack, String text) {
        Minecraft mc = Minecraft.getInstance();
        List<Component> lines = stack.getTooltipLines(Item.TooltipContext.of(mc.level), mc.player, TooltipFlag.NORMAL);
        for (int i = 1; i < lines.size(); i++) {
            if (lines.get(i).getString().toLowerCase(Locale.ROOT).contains(text)) {
                return true;
            }
        }
        return false;
    }

    private static boolean tag(Item item, String text) {
        return item.builtInRegistryHolder().tags().anyMatch(tag -> {
            ResourceLocation id = tag.location();
            return id.toString().contains(text);
        });
    }
}
