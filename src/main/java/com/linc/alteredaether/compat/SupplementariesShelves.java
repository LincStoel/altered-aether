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
package com.linc.alteredaether.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Supplementaries' item shelves as golem pins: a shelf on a chest pins the item it shows, like an item frame (see
 * GolemStorage.pins). Read through vanilla types (its block entity is a one-slot Container, its facing the vanilla
 * horizontal one, pointing away from the wall), so Supplementaries needn't be on the compile classpath.
 */
public final class SupplementariesShelves {
    public static final String MOD = "supplementaries";
    private static final ResourceLocation SHELF = ResourceLocation.fromNamespaceAndPath(MOD, "item_shelf");

    private SupplementariesShelves() {
    }

    /** The item shown on an item shelf hanging on the {@code side} face of the block at {@code pos}, or empty. */
    public static ItemStack heldOn(Level level, BlockPos pos, Direction side) {
        if (side.getAxis().isVertical()) {
            return ItemStack.EMPTY;
        }
        BlockPos at = pos.relative(side);
        BlockState state = level.getBlockState(at);
        if (!BuiltInRegistries.BLOCK.getKey(state.getBlock()).equals(SHELF)
                || !state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
                || state.getValue(BlockStateProperties.HORIZONTAL_FACING) != side) {
            return ItemStack.EMPTY;
        }
        return level.getBlockEntity(at) instanceof Container shelf && shelf.getContainerSize() > 0
                ? shelf.getItem(0) : ItemStack.EMPTY;
    }
}
