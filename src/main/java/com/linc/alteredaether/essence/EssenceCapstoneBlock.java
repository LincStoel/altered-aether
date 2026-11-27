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

import com.linc.alteredaether.AATags;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.Containers;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A celezite capstone for an essence circle: set level with the orb at a compass point, with a gem from
 * {@code #altered_aether:capstone_gems} in the slot at its middle, it catches an eighth of the source's flow, but only
 * while the capstone at the opposite compass point holds the same gem and stands just as far out. Use a gem
 * on it to set one, an empty hand to take it back; a broken capstone drops its gem. The gem shows on every side
 * ({@link #GEM}); the stack itself is kept in the block entity, so a gem a pack adds comes back as it went in.
 */
public class EssenceCapstoneBlock extends BaseEntityBlock {
    public static final MapCodec<EssenceCapstoneBlock> CODEC = simpleCodec(EssenceCapstoneBlock::new);
    public static final EnumProperty<CapstoneGem> GEM = EnumProperty.create("gem", CapstoneGem.class);

    public EssenceCapstoneBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(GEM, CapstoneGem.NONE));
    }

    /** Whether a capstone at this state has a gem in it. */
    public static boolean gemmed(BlockState state) {
        return state.getBlock() instanceof EssenceCapstoneBlock && state.getValue(GEM) != CapstoneGem.NONE;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(GEM);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new EssenceCapstoneBlockEntity(pos, state);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(AATags.CAPSTONE_GEMS) || state.getValue(GEM) != CapstoneGem.NONE) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof EssenceCapstoneBlockEntity capstone) {
            capstone.setGem(stack.copyWithCount(1));
            stack.consume(1, player);
            level.setBlock(pos, state.setValue(GEM, CapstoneGem.of(capstone.gem())), Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_PLACE, SoundSource.BLOCKS, 1.0F, 1.2F);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (state.getValue(GEM) == CapstoneGem.NONE) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof EssenceCapstoneBlockEntity capstone) {
            ItemStack gem = capstone.gem();
            capstone.setGem(ItemStack.EMPTY);
            level.setBlock(pos, state.setValue(GEM, CapstoneGem.NONE), Block.UPDATE_ALL);
            if (!player.addItem(gem)) {
                player.drop(gem, false);
            }
            level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.BLOCKS, 1.0F, 1.2F);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof EssenceCapstoneBlockEntity capstone
                && !capstone.gem().isEmpty()) {
            Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), capstone.gem());
            capstone.setGem(ItemStack.EMPTY);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
