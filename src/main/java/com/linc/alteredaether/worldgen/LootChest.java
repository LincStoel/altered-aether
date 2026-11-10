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
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.StreamSupport;

/**
 * The chest our loot sits in: Aether: Beyond Parity's skyroot chest when it is installed (and its compat switch is on),
 * a plain chest otherwise. The skyroot chest is a vanilla chest underneath, so loot tables go in it the same way.
 */
public final class LootChest {
    private static final String BEYOND_PARITY = "aether_beyond_parity";
    private static final ResourceLocation SKYROOT_CHEST = ResourceLocation.fromNamespaceAndPath(BEYOND_PARITY, "skyroot_chest");
    private static final String ASCENDED_QUARK = "ascended_quark";
    private static final ResourceLocation QUARK_SKYROOT_CHEST = ResourceLocation.fromNamespaceAndPath(ASCENDED_QUARK, "skyroot_chest");
    /** How far from where it was meant to go a chest of belongings may be set instead, in blocks. */
    private static final int PLACE_REACH = 2;

    /** A single chest facing {@code facing}. */
    public static BlockState facing(Direction facing) {
        Block chest = Blocks.CHEST;
        if (ModList.get().isLoaded(BEYOND_PARITY) && AACommonConfig.compat(BEYOND_PARITY)) {
            chest = BuiltInRegistries.BLOCK.getOptional(SKYROOT_CHEST).filter(ChestBlock.class::isInstance)
                    .orElse(Blocks.CHEST);
        }
        return chest.defaultBlockState().setValue(ChestBlock.FACING, facing);
    }

    /**
     * A single skyroot chest facing {@code facing}, for belongings rather than loot: Ascended Quark's when it's installed
     * and Quark's variant chests are switched on, else Beyond Parity's, else a plain chest.
     */
    public static BlockState skyroot(Direction facing) {
        if (ModList.get().isLoaded(ASCENDED_QUARK) && AACommonConfig.compat(ASCENDED_QUARK)) {
            Optional<Block> quark = BuiltInRegistries.BLOCK.getOptional(QUARK_SKYROOT_CHEST)
                    .filter(ChestBlock.class::isInstance).filter(LootChest::zetaEnabled);
            if (quark.isPresent()) {
                return quark.get().defaultBlockState().setValue(ChestBlock.FACING, facing);
            }
        }
        return facing(facing);
    }

    /**
     * Sets a skyroot chest holding {@code items} as near {@code at} as there's room for one, within a couple of blocks,
     * nearest first; one set in water is waterlogged. Returns false, setting nothing, if there's nowhere to put it.
     */
    public static boolean placeWith(ServerLevel level, Vec3 at, Direction facing, List<ItemStack> items) {
        BlockPos centre = BlockPos.containing(at);
        Optional<BlockPos> spot = StreamSupport.stream(BlockPos.withinManhattan(centre, PLACE_REACH, PLACE_REACH,
                        PLACE_REACH).spliterator(), false)
                .map(BlockPos::immutable)
                .sorted(Comparator.comparingDouble(pos -> pos.distToCenterSqr(at)))
                .filter(pos -> level.isInWorldBounds(pos) && level.getWorldBorder().isWithinBounds(pos)
                        && level.isLoaded(pos) && level.getBlockState(pos).canBeReplaced())
                .findFirst();
        if (spot.isEmpty()) {
            return false;
        }
        BlockPos pos = spot.get();
        BlockState chest = skyroot(facing);
        if (chest.hasProperty(BlockStateProperties.WATERLOGGED)) {
            chest = chest.setValue(BlockStateProperties.WATERLOGGED, level.getFluidState(pos).is(Fluids.WATER));
        }
        level.setBlockAndUpdate(pos, chest);
        if (!(level.getBlockEntity(pos) instanceof Container container) || container.getContainerSize() < items.size()) {
            level.removeBlock(pos, false);
            return false;
        }
        for (int i = 0; i < items.size(); i++) {
            container.setItem(i, items.get(i).copy());
        }
        container.setChanged();
        return true;
    }

    /** Whether Quark hasn't switched the block off, as its variant chests can be; a block that can't say is on. */
    private static boolean zetaEnabled(Block block) {
        try {
            return !(block.getClass().getMethod("isEnabled").invoke(block) instanceof Boolean enabled) || enabled;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return true;
        }
    }

    private LootChest() {
    }
}
