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

import com.simibubi.create.content.decoration.placard.PlacardBlock;
import com.simibubi.create.content.decoration.placard.PlacardBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Create's item placards as golem pins: a placard on a chest pins the item it shows (see GolemStorage.pins). Only
 * called with Create loaded.
 */
public final class CreatePlacards {
    private CreatePlacards() {
    }

    /** The item shown on a placard hanging on the {@code side} face of the block at {@code pos}, or empty. */
    public static ItemStack heldOn(Level level, BlockPos pos, Direction side) {
        BlockPos at = pos.relative(side);
        BlockState state = level.getBlockState(at);
        if (!(state.getBlock() instanceof PlacardBlock)) {
            return ItemStack.EMPTY;
        }
        // a placard faces away from what it hangs on: its connected direction points from the block to it
        if (PlacardBlock.connectedDirection(state) != side) {
            return ItemStack.EMPTY;
        }
        return level.getBlockEntity(at) instanceof PlacardBlockEntity placard ? placard.getHeldItem() : ItemStack.EMPTY;
    }
}
