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
package com.linc.alteredaether.essence;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** The gem set in an essence capstone. */
public class EssenceCapstoneBlockEntity extends BlockEntity {
    private ItemStack gem = ItemStack.EMPTY;

    public EssenceCapstoneBlockEntity(BlockPos pos, BlockState state) {
        super(EssenceRegistries.CAPSTONE_BLOCK_ENTITY.get(), pos, state);
    }

    public ItemStack gem() {
        return gem;
    }

    public void setGem(ItemStack gem) {
        this.gem = gem;
        setChanged();
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        gem = tag.contains("gem") ? ItemStack.parseOptional(registries, tag.getCompound("gem")) : ItemStack.EMPTY;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!gem.isEmpty()) {
            tag.put("gem", gem.save(registries));
        }
    }
}
