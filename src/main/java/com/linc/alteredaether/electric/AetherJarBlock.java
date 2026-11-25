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
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

/**
 * The Aether Jar, a Leyden jar of aerogel stoppered with skyroot, a lightning rod's copper running down its middle.
 * Lightning on it, or on a rod stood on its top, charges it (see {@link ElectricStrikes}); its charge is in
 * {@link AetherJarBlockEntity}. {@link #CHARGE} is how full it is, for the glow and the comparator. It is placed in any
 * rotation, like a lightning rod: {@link #FACING} is the way its stopper points. Power goes in at its bottom and out at
 * its top, whichever way that is.
 *
 * <p>The creative jar ({@link #creative}) is never empty and gives out as much as is asked of it, like Create's
 * creative motor.
 */
public class AetherJarBlock extends BaseEntityBlock {
    public static final MapCodec<AetherJarBlock> CODEC = simpleCodec(AetherJarBlock::new);
    public static final IntegerProperty CHARGE = IntegerProperty.create("charge", 0, 4);
    public static final DirectionProperty FACING = DirectionalBlock.FACING;
    private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);

    static {
        for (Direction facing : Direction.values()) {
            SHAPES.put(facing, Shapes.or(box(facing, 3, 0, 3, 13, 12, 13), box(facing, 5, 12, 5, 11, 16, 11)));
        }
    }

    private final boolean creative;

    public AetherJarBlock(Properties properties) {
        this(properties, false);
    }

    public AetherJarBlock(Properties properties, boolean creative) {
        super(properties);
        this.creative = creative;
        registerDefaultState(stateDefinition.any().setValue(CHARGE, creative ? 4 : 0).setValue(FACING, Direction.UP));
    }

    /** Whether this is the creative jar, endless and never charged. */
    public boolean creative() {
        return creative;
    }

    /**
     * The jar, either kind, whose top touches {@code rod} (a lightning rod there is "on top of" it, whichever way the
     * jar is turned); null if none. With more than one, the first found.
     */
    @Nullable
    public static BlockPos jarUnder(BlockGetter level, BlockPos rod) {
        for (Direction side : Direction.values()) {
            BlockPos at = rod.relative(side);
            BlockState state = level.getBlockState(at);
            if (state.getBlock() instanceof AetherJarBlock && state.getValue(FACING) == side.getOpposite()) {
                return at;
            }
        }
        return null;
    }

    /** An upright box (pixels) turned so its top points {@code facing}. */
    private static VoxelShape box(Direction facing, double x0, double y0, double z0, double x1, double y1, double z1) {
        return switch (facing) {
            case UP -> box(x0, y0, z0, x1, y1, z1);
            case DOWN -> box(x0, 16 - y1, z0, x1, 16 - y0, z1);
            case NORTH -> box(x0, z0, 16 - y1, x1, z1, 16 - y0);
            case SOUTH -> box(x0, z0, y0, x1, z1, y1);
            case WEST -> box(16 - y1, x0, z0, 16 - y0, x1, z1);
            case EAST -> box(y0, x0, z0, y1, x1, z1);
        };
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(CHARGE, FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getClickedFace());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.setValue(FACING, mirror.mirror(state.getValue(FACING)));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
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
            // the jar is about a block's middle whichever way it lies
            level.addParticle(ParticleTypes.ELECTRIC_SPARK, pos.getX() + 0.25 + random.nextDouble() * 0.5,
                    pos.getY() + 0.25 + random.nextDouble() * 0.5, pos.getZ() + 0.25 + random.nextDouble() * 0.5,
                    0, 0.02, 0);
        }
    }
}
