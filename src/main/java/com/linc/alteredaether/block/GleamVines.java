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
package com.linc.alteredaether.block;

import com.linc.alteredaether.registry.AARegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CaveVines;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Gleam vines are cave vines in Aether colours, hung with gleamberries. They share everything with vanilla's vines but
 * the berry: {@link #pick} is vanilla's {@link CaveVines#use} with our berry in place of the glow berry, and the two
 * places vanilla names the cave vine blocks outright (foxes picking berries, bees growing vines) are taught ours in
 * mixin/GleamVinesMixins. Fire is told how they burn by the blocks themselves, at vanilla's rates. Unlike cave vines
 * they also hang from leaves, so they can drape the groves' canopies.
 */
public final class GleamVines {
    /** Vanilla's cave vines catch (15) and burn away (60) like leaves. */
    public static final int FIRE_SPREAD_SPEED = 15;
    public static final int FLAMMABILITY = 60;

    public static boolean is(BlockState state) {
        return state.is(AARegistries.GLEAM_VINES.get()) || state.is(AARegistries.GLEAM_VINES_PLANT.get());
    }

    /** Whether a check for one of vanilla's cave vine blocks should also accept this state. */
    public static boolean standsInFor(BlockState state, Block vanilla) {
        return vanilla == Blocks.CAVE_VINES && state.is(AARegistries.GLEAM_VINES.get())
                || vanilla == Blocks.CAVE_VINES_PLANT && state.is(AARegistries.GLEAM_VINES_PLANT.get());
    }

    /** Whether the vine at {@code pos} hangs from leaves, which hold gleam vines though they hold no cave vines. */
    public static boolean hangsFromLeaves(LevelReader level, BlockPos pos) {
        return level.getBlockState(pos.above()).is(BlockTags.LEAVES);
    }

    /** {@link CaveVines#use}, dropping a gleamberry. */
    public static InteractionResult pick(@Nullable Entity entity, BlockState state, Level level, BlockPos pos) {
        if (!state.getValue(CaveVines.BERRIES)) {
            return InteractionResult.PASS;
        }
        Block.popResource(level, pos, new ItemStack(AARegistries.GLEAMBERRIES.get(), 1));
        float pitch = Mth.randomBetween(level.random, 0.8F, 1.2F);
        level.playSound(null, pos, SoundEvents.CAVE_VINES_PICK_BERRIES, SoundSource.BLOCKS, 1.0F, pitch);
        BlockState picked = state.setValue(CaveVines.BERRIES, false);
        level.setBlock(pos, picked, Block.UPDATE_CLIENTS);
        level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(entity, picked));
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private GleamVines() {
    }
}
