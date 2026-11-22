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
import com.linc.alteredaether.compat.CreateCompat;
import com.linc.alteredaether.registry.AARegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Nadirite's way to andesite (see data/AAStoneProvider): with Create, every step's recipe loads and makes what it
 * should, and the items are listed in the Aether's ingredients tab; without it, none of that. Names no Create type.
 */
@GameTestHolder(AlteredAether.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NadiriteProcessingTests {
    /** Each step's recipe, its product and how many it makes. */
    private static final Map<String, Map.Entry<Item, Integer>> RECIPES = Map.of(
            "nadirite/milling", Map.entry(AARegistries.CRUSHED_NADIRITE.get(), 2),
            "nadirite/splashing", Map.entry(AARegistries.NADIRITE_GRIT.get(), 2),
            "nadirite/aether_crust_mix", Map.entry(AARegistries.AETHER_CRUST_MIX.get(), 2),
            "nadirite/andesite_enchanting", Map.entry(Items.ANDESITE, 1));

    @GameTest(template = "arena")
    public static void nadiriteMakesAndesiteWithCreate(GameTestHelper helper) {
        boolean create = ModList.get().isLoaded(CreateCompat.MOD);
        var level = helper.getLevel();
        var recipes = level.getRecipeManager();
        for (var entry : RECIPES.entrySet()) {
            Optional<RecipeHolder<?>> holder = recipes.byKey(AlteredAether.id(entry.getKey()));
            if (holder.isPresent() != create) {
                helper.fail(entry.getKey() + " should " + (create ? "" : "not ") + "load");
                return;
            }
            if (holder.isPresent()) {
                var result = holder.get().value().getResultItem(level.registryAccess());
                Item item = entry.getValue().getKey();
                int count = entry.getValue().getValue();
                if (!result.is(item) || result.getCount() != count) {
                    helper.fail(entry.getKey() + " should make " + count + " " + item + ", got " + result);
                    return;
                }
            }
        }

        CreativeModeTabs.tryRebuildTabContents(level.enabledFeatures(), true, level.registryAccess());
        var ingredients = com.aetherteam.aether.item.AetherCreativeTabs.AETHER_INGREDIENTS.get().getDisplayItems();
        for (Item item : List.of(AARegistries.CRUSHED_NADIRITE.get(), AARegistries.NADIRITE_GRIT.get(),
                AARegistries.AETHER_CRUST_MIX.get())) {
            boolean listed = ingredients.stream().anyMatch(stack -> stack.is(item));
            if (listed != create) {
                helper.fail(item + " should " + (create ? "" : "not ") + "be in the Aether's ingredients tab");
                return;
            }
        }
        helper.succeed();
    }

    private NadiriteProcessingTests() {
    }
}
