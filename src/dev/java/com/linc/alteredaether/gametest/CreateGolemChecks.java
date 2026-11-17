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

import com.simibubi.create.AllDataComponents;
import com.simibubi.create.content.logistics.funnel.AbstractDirectionalFunnelBlock;
import com.simibubi.create.content.logistics.funnel.AbstractFunnelBlock;
import com.simibubi.create.content.logistics.funnel.FunnelBlock;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import com.simibubi.create.content.redstone.thresholdSwitch.ThresholdSwitchBlock;
import com.simibubi.create.content.redstone.thresholdSwitch.ThresholdSwitchBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/** The gravitite golem's Create checks, kept apart so Create's classes load only when Create is there. */
final class CreateGolemChecks {
    private CreateGolemChecks() {
    }

    /** Puts a stockpile switch at {@code pos} watching the block to its west. */
    static void placeSwitch(GameTestHelper helper, BlockPos pos) {
        Block block = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("create", "stockpile_switch"));
        for (BlockState state : block.getStateDefinition().getPossibleStates()) {
            if (ThresholdSwitchBlock.getTargetDirection(state) == Direction.WEST) {
                helper.setBlock(pos, state);
                return;
            }
        }
        throw new IllegalStateException("no stockpile switch state faces west");
    }

    /** Asserts the switch at {@code pos} counts {@code items} of {@code capacity}. */
    static void assertReads(GameTestHelper helper, BlockPos pos, int items, int capacity) {
        ThresholdSwitchBlockEntity be = (ThresholdSwitchBlockEntity) helper.getBlockEntity(pos);
        be.updateCurrentLevel();
        helper.assertTrue(be.currentLevel == items && be.currentMaxLevel == capacity,
                "the switch reads " + be.currentLevel + " / " + be.currentMaxLevel + ", not " + items + " / " + capacity);
    }

    /** Puts a brass funnel at {@code pos} facing {@code facing} (away from the block it feeds), putting items in. */
    static void placeBrassFunnel(GameTestHelper helper, BlockPos pos, Direction facing, boolean powered) {
        Block block = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("create", "brass_funnel"));
        helper.setBlock(pos, block.defaultBlockState().setValue(AbstractDirectionalFunnelBlock.FACING, facing)
                .setValue(FunnelBlock.EXTRACTING, false).setValue(AbstractFunnelBlock.POWERED, powered));
    }

    /** Puts {@code filter} in the filter slot of the funnel at {@code pos}. */
    static void setFilter(GameTestHelper helper, BlockPos pos, ItemStack filter) {
        FilteringBehaviour filtering = BlockEntityBehaviour.get(helper.getLevel(), helper.absolutePos(pos), FilteringBehaviour.TYPE);
        helper.assertTrue(filtering != null, "the funnel has no filter slot");
        filtering.setFilter(filter);
    }

    /** A list filter letting {@code items} through. */
    static ItemStack listFilter(ItemStack... items) {
        ItemStack filter = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("create", "filter")));
        filter.set(AllDataComponents.FILTER_ITEMS, ItemContainerContents.fromItems(List.of(items)));
        return filter;
    }
}