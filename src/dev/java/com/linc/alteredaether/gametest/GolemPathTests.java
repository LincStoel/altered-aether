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
package com.linc.alteredaether.gametest;

import com.linc.alteredaether.AlteredAether;
import com.linc.alteredaether.entity.golem.group.GolemPaths;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Where tidy mode files items: by dimension, category, material group and material, read off recipes, tags and names. */
@GameTestHolder(AlteredAether.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GolemPathTests {
    private GolemPathTests() {
    }

    private static String path(Item item) {
        return GolemPaths.path(new ItemStack(item)).toString();
    }

    private static void expect(GameTestHelper helper, Item item, String expected) {
        String actual = path(item);
        helper.assertTrue(actual.equals(expected), BuiltInRegistries.ITEM.getKey(item) + " filed under " + actual
                + ", not " + expected);
    }

    @GameTest(template = "arena", timeoutTicks = 20)
    public static void itemsAreFiledByWhatTheyAreMadeOf(GameTestHelper helper) {
        // spruce, whatever its shape, worked or not
        expect(helper, Items.SPRUCE_PLANKS, "overworld/blocks/wood/spruce");
        expect(helper, Items.SPRUCE_STAIRS, "overworld/blocks/wood/spruce");
        expect(helper, Items.STRIPPED_SPRUCE_LOG, "overworld/blocks/wood/spruce");
        expect(helper, Items.SPRUCE_FENCE, "overworld/blocks/wood/spruce");
        // stone families through the stonecutter
        expect(helper, Items.ANDESITE, "overworld/blocks/stone/andesite");
        expect(helper, Items.POLISHED_ANDESITE_SLAB, "overworld/blocks/stone/andesite");
        // soils share a tag, not a recipe
        expect(helper, Items.COARSE_DIRT, "overworld/blocks/earth/dirt");
        // a metal is one material from ore to block
        expect(helper, Items.IRON_INGOT, "overworld/resources/metals/iron");
        expect(helper, Items.RAW_IRON, "overworld/resources/metals/iron");
        // food: raw if nothing makes it, a meal if something does
        expect(helper, Items.BEEF, "overworld/food/raw/beef");
        expect(helper, Items.COOKED_BEEF, "overworld/food/meals/beef");
        expect(helper, Items.BREAD, "overworld/food/meals/bread");
        // the nether by tag, and what is made from it by recipe
        expect(helper, Items.CRIMSON_STAIRS, "nether/blocks/wood/crimson");
        expect(helper, Items.POLISHED_BLACKSTONE_BRICKS, "nether/blocks/stone/blackstone");
        String sword = path(Items.IRON_SWORD);
        helper.assertTrue(sword.startsWith("overworld/equipment/weapons"), "the sword is filed under " + sword);
        // the Aether by mod
        Item skyroot = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("aether", "skyroot_stairs"));
        if (skyroot != Items.AIR) {
            String actual = path(skyroot);
            helper.assertTrue(actual.equals("aether/blocks/wood/skyroot"), "skyroot stairs filed under " + actual);
        }
        helper.succeed();
    }
}
