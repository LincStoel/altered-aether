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

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * The Aether Jar, a Leyden jar of aerogel stoppered with skyroot, a lightning rod's copper running down its middle.
 * Lightning on it, or on a rod stood on top of it, charges it (see {@link ElectricStrikes}); its charge is in
 * {@link AetherJarBlockEntity}. {@link #CHARGE} is how full it is, for the glow and the comparator.
 */
public class AetherJarBlock extends BaseEntityBlock {
    public static final MapCodec<AetherJarBlock> CODEC = simpleCodec(AetherJarBlock::new);
    public static final IntegerProperty CHARGE = IntegerProperty.create("charge", 0, 4);
    private static final VoxelShape SHAPE = Shapes.or(box(3, 0, 3, 13, 12, 13), box(5, 12, 5, 11, 16, 11));

    public AetherJarBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(CHARGE, 0));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(CHARGE);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AetherJarBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null
                : createTickerHelper(type, ElectricRegistries.AETHER_JAR_BLOCK_ENTITY.get(), AetherJarBlockEntity::serverTick);
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof AetherJarBlockEntity jar ? jar.comparatorSignal() : 0;
    }

    /** A charged jar crackles now and then, more the fuller it is. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        int charge = state.getValue(CHARGE);
        if (charge > 0 && random.nextInt(12) < charge) {
            level.addParticle(ParticleTypes.ELECTRIC_SPARK, pos.getX() + 0.3 + random.nextDouble() * 0.4,
                    pos.getY() + 0.2 + random.nextDouble() * 0.6, pos.getZ() + 0.3 + random.nextDouble() * 0.4,
                    0, 0.02, 0);
        }
    }
}
