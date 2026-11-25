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

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** A Squall Generator without Create: the {@link SquallGeneratorCore} alone. */
public class SquallGeneratorBlockEntity extends BlockEntity implements SquallGeneratorHost {
    private final SquallGeneratorCore core = new SquallGeneratorCore(this);

    public SquallGeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(ElectricRegistries.SQUALL_GENERATOR_BLOCK_ENTITY.get(), pos, state);
    }

    @Override
    public SquallGeneratorCore core() {
        return core;
    }

    @Override
    public void hostTick() {
        if (level instanceof ServerLevel server) {
            core.tick(server, worldPosition, getBlockState());
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        core.load(tag, registries);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        core.save(tag, registries);
    }
}
