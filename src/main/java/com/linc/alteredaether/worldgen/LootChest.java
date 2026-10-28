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
package com.linc.alteredaether.worldgen;

import com.linc.alteredaether.config.AACommonConfig;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.ModList;

/**
 * The chest our loot sits in: Aether: Beyond Parity's skyroot chest when it is installed (and its compat switch is on),
 * a plain chest otherwise. The skyroot chest is a vanilla chest underneath, so loot tables go in it the same way.
 */
public final class LootChest {
    private static final String BEYOND_PARITY = "aether_beyond_parity";
    private static final ResourceLocation SKYROOT_CHEST = ResourceLocation.fromNamespaceAndPath(BEYOND_PARITY, "skyroot_chest");

    /** A single chest facing {@code facing}. */
    public static BlockState facing(Direction facing) {
        Block chest = Blocks.CHEST;
        if (ModList.get().isLoaded(BEYOND_PARITY) && AACommonConfig.compat(BEYOND_PARITY)) {
            chest = BuiltInRegistries.BLOCK.getOptional(SKYROOT_CHEST).filter(ChestBlock.class::isInstance)
                    .orElse(Blocks.CHEST);
        }
        return chest.defaultBlockState().setValue(ChestBlock.FACING, facing);
    }

    private LootChest() {
    }
}
