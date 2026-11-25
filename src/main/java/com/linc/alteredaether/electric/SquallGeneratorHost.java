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
package com.linc.alteredaether.electric;

import com.linc.alteredaether.compat.CreateElectric;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.ModList;

/**
 * A Squall Generator's block entity: {@link SquallGeneratorBlockEntity}, or with Create the one in
 * compat/CreateElectric, which can hold items on a belt as a press does. Both keep their state in the same
 * {@link SquallGeneratorCore} and save it the same way, so a world opens with or without Create.
 */
public interface SquallGeneratorHost {
    SquallGeneratorCore core();

    /** Runs a tick, on the side the block's ticker was made for (both for Create's, the server's alone otherwise). */
    void hostTick();

    /** Whether the generators are Create's kind, which tick on the client too. */
    static boolean createKind() {
        return ModList.get().isLoaded("create");
    }

    /** The block entity a new generator gets; Create's class is only touched when Create is there. */
    static BlockEntity create(BlockPos pos, BlockState state) {
        return createKind() ? CreateElectric.generator(pos, state) : new SquallGeneratorBlockEntity(pos, state);
    }
}
