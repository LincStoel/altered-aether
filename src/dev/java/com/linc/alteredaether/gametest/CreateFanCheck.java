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

import com.linc.alteredaether.registry.AARegistries;
import com.simibubi.create.content.kinetics.fan.processing.AllFanProcessingTypes;
import com.simibubi.create.content.kinetics.fan.processing.FanProcessingType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.CampfireBlock;

import javax.annotation.Nullable;
import java.util.List;

/** The Create side of {@link CreateFanTests}; loaded only when Create is. */
final class CreateFanCheck {
    /** Null when everything holds, or what went wrong. */
    @Nullable
    static String run(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        level.setBlockAndUpdate(pos, AARegistries.AMBROSIUM_CAMPFIRE.get().defaultBlockState()
                .setValue(CampfireBlock.LIT, true));
        FanProcessingType type = FanProcessingType.getAt(level, pos);
        if (type == null || type == AllFanProcessingTypes.SMOKING) {
            return "A lit ambrosium campfire should enchant, not " + type;
        }
        level.setBlockAndUpdate(pos, AARegistries.AMBROSIUM_CAMPFIRE.get().defaultBlockState()
                .setValue(CampfireBlock.LIT, false));
        if (FanProcessingType.getAt(level, pos) == type) {
            return "An unlit ambrosium campfire shouldn't enchant";
        }

        ItemStack berries = new ItemStack(item("aether:blue_berry"), 5);
        if (!type.canProcess(berries, level)) {
            return "Blue berries should be enchantable";
        }
        List<ItemStack> out = type.process(berries, level);
        if (out == null || out.size() != 1 || !out.getFirst().is(item("aether:enchanted_berry"))
                || out.getFirst().getCount() != 5) {
            return "Five blue berries should come out as five enchanted berries, got " + out;
        }

        ItemStack pickaxe = new ItemStack(item("aether:zanite_pickaxe"));
        pickaxe.setDamageValue(100);
        if (type.canProcess(pickaxe, level)) {
            return "Repairs are the altar's alone; a damaged pickaxe shouldn't be processed";
        }
        if (type.canProcess(new ItemStack(Items.DIRT), level)) {
            return "Dirt has no altar recipe";
        }
        return null;
    }

    private static net.minecraft.world.item.Item item(String id) {
        return BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
    }

    private CreateFanCheck() {
    }
}
