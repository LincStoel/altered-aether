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
package com.linc.alteredaether.compat.create;

import com.linc.alteredaether.compat.CreateElectric;
import com.simibubi.create.content.kinetics.base.KineticBlock;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Aerogel Wheel: a flat aerogel disc on a skyroot hub, turning on an upright axle that takes a shaft above and
 * below. What spins it is in {@link AerogelWheelBlockEntity}; its renderer draws the turning model, so the block itself
 * draws nothing.
 */
public class AerogelWheelBlock extends KineticBlock implements IBE<AerogelWheelBlockEntity> {
    private static final VoxelShape SHAPE = Shapes.or(box(0, 6, 0, 16, 10, 16), box(6, 0, 6, 10, 16, 10));

    public AerogelWheelBlock(Properties properties) {
        super(properties);
    }

    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        return Direction.Axis.Y;
    }

    @Override
    public boolean hasShaftTowards(LevelReader level, BlockPos pos, BlockState state, Direction face) {
        return face.getAxis() == Direction.Axis.Y;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public Class<AerogelWheelBlockEntity> getBlockEntityClass() {
        return AerogelWheelBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends AerogelWheelBlockEntity> getBlockEntityType() {
        return CreateElectric.AEROGEL_WHEEL_BLOCK_ENTITY.get();
    }
}
