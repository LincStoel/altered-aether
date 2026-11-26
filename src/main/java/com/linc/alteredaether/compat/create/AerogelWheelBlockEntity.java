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

import com.linc.alteredaether.AlteredAether;
import com.linc.alteredaether.compat.CreateElectric;
import com.linc.alteredaether.config.AACommonConfig;
import com.linc.alteredaether.electric.AetherJarBlock;
import com.linc.alteredaether.electric.AetherJarBlockEntity;
import com.linc.alteredaether.electric.ElectricRegistries;
import com.linc.alteredaether.electric.SquallGeneratorCore;
import com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity;
import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.motor.KineticScrollValueBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightningRodBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Franklin's electric wheel. Two lightning rods stand level with it, two blocks out to either side, each on top of an
 * Aether Jar: the jar may be on any side of its rod and turned any way, so long as the rod's foot sits on the jar's top. The blocks
 * between the rods and the wheel are air, and under the wheel is air or a shaft. Set up so, the jars' charge spins it, as a Create Crafts & Additions electric motor would be spun: its speed
 * is set like a motor's, 1 to 256 RPM either way; it gives {@code wheel_stress_per_rpm} stress units per RPM; and it
 * draws {@code wheel_fe_at_max_rpm} FE a tick at 256 RPM, less in step with its speed but never under
 * {@code wheel_min_consumption}, half from each jar. Out of charge in either jar, or given a redstone signal, it stops.
 */
public class AerogelWheelBlockEntity extends GeneratingKineticBlockEntity {
    private static final int RECHECK = 20;
    private static final int DEFAULT_RPM = 8;
    public static final int MAX_RPM = 256;

    private ScrollValueBehaviour speed;
    private boolean running;
    /** Why it isn't turning, for goggles and Jade: none, no frame, or a dry jar. */
    private Stop stop = Stop.FRAME;
    private int recheck;
    @Nullable
    private Direction.Axis frame;

    public enum Stop {
        NONE, FRAME, DRAINED, REDSTONE
    }

    public AerogelWheelBlockEntity(BlockPos pos, BlockState state) {
        super(CreateElectric.AEROGEL_WHEEL_BLOCK_ENTITY.get(), pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
        speed = new KineticScrollValueBehaviour(Component.translatable("block." + AlteredAether.MOD_ID + ".aerogel_wheel"),
                this, new SideValueBox());
        speed.between(-MAX_RPM, MAX_RPM);
        speed.value = DEFAULT_RPM;
        speed.withCallback(rpm -> updateGeneratedRotation());
        behaviours.add(speed);
    }

    @Override
    public void initialize() {
        super.initialize();
        if (!hasSource() || getGeneratedSpeed() > getTheoreticalSpeed()) {
            updateGeneratedRotation();
        }
    }

    @Override
    public float getGeneratedSpeed() {
        return running && speed != null ? speed.getValue() : 0;
    }

    public boolean running() {
        return running;
    }

    public Stop stop() {
        return stop;
    }

    /** FE a tick the wheel draws at {@code rpm}, both jars together; 0 when it isn't set to turn. */
    public static int drain(int rpm) {
        int speed = Math.abs(rpm);
        if (speed == 0) {
            return 0;
        }
        int cost = Mth.ceil(AACommonConfig.WHEEL_FE_AT_MAX_RPM.get() * speed / (double) MAX_RPM);
        return Math.max(cost, AACommonConfig.WHEEL_MIN_CONSUMPTION.get());
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level instanceof ServerLevel)) {
            return;
        }
        if (frame == null || --recheck <= 0) {
            frame = frameAxis(level, worldPosition);
            recheck = RECHECK;
        }
        Stop now = frame == null ? Stop.FRAME : level.hasNeighborSignal(worldPosition) ? Stop.REDSTONE
                : pay(frame) ? Stop.NONE : Stop.DRAINED;
        boolean turning = now == Stop.NONE;
        if (turning != running || now != stop) {
            stop = now;
            if (turning != running) {
                running = turning;
                updateGeneratedRotation();
            }
            sendData();
        }
        if (running && level.getGameTime() % 10 == 0 && frame != null) {
            // sparks from each rod's tip to the disc
            for (int side : new int[]{-1, 1}) {
                BlockPos rodPos = worldPosition.relative(frame, 2 * side);
                BlockState rod = level.getBlockState(rodPos);
                Direction pointing = rod.hasProperty(LightningRodBlock.FACING) ? rod.getValue(LightningRodBlock.FACING)
                        : Direction.UP;
                Vec3 tip = Vec3.atCenterOf(rodPos).add(Vec3.atLowerCornerOf(pointing.getNormal()).scale(0.4));
                SquallGeneratorCore.arc((ServerLevel) level, tip, Vec3.atCenterOf(worldPosition).add(frame == Direction.Axis.X
                        ? side * 0.9 : 0, 0, frame == Direction.Axis.Z ? side * 0.9 : 0));
            }
        }
    }

    /** Draws a tick's charge, half from each jar, if both have it. */
    private boolean pay(Direction.Axis axis) {
        int cost = drain(speed.getValue());
        if (cost == 0) {
            return false;
        }
        AetherJarBlockEntity first = jar(axis, 1), second = jar(axis, -1);
        if (first == null || second == null) {
            return false;
        }
        int half = cost / 2, rest = cost - half;
        if (first.draw(rest, true) < rest || second.draw(half, true) < half) {
            return false;
        }
        first.draw(rest, false);
        second.draw(half, false);
        return true;
    }

    @Nullable
    private AetherJarBlockEntity jar(Direction.Axis axis, int side) {
        BlockPos at = AetherJarBlock.jarUnder(level, worldPosition.relative(axis, 2 * side));
        return at != null && level.getBlockEntity(at) instanceof AetherJarBlockEntity jar ? jar : null;
    }

    /** The line, X or Z, a wheel at {@code pos} has a full frame along; null if neither. */
    @Nullable
    public static Direction.Axis frameAxis(Level level, BlockPos pos) {
        BlockState under = level.getBlockState(pos.below());
        if (!under.isAir() && !(under.getBlock() instanceof IRotate rotate
                && rotate.hasShaftTowards(level, pos.below(), under, Direction.UP))) {
            return null;
        }
        for (Direction.Axis axis : new Direction.Axis[]{Direction.Axis.X, Direction.Axis.Z}) {
            if (side(level, pos, axis, 1) && side(level, pos, axis, -1)) {
                return axis;
            }
        }
        return null;
    }

    private static boolean side(Level level, BlockPos pos, Direction.Axis axis, int sign) {
        BlockPos near = pos.relative(axis, sign), rod = pos.relative(axis, 2 * sign);
        return level.getBlockState(near).isAir() && level.getBlockState(rod).is(Blocks.LIGHTNING_ROD)
                && AetherJarBlock.jarUnder(level, rod) != null;
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean sneaking) {
        boolean added = super.addToGoggleTooltip(tooltip, sneaking);
        String key = switch (stop) {
            case FRAME -> "wheel.incomplete";
            case DRAINED -> "wheel.drained";
            default -> null;
        };
        if (key != null) {
            tooltip.add(Component.literal("    ").append(Component.translatable("jade." + AlteredAether.MOD_ID + "." + key))
                    .withStyle(ChatFormatting.GOLD));
            return true;
        }
        return added;
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putBoolean("Running", running);
        tag.putInt("Stop", stop.ordinal());
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        running = tag.getBoolean("Running");
        stop = Stop.values()[Mth.clamp(tag.getInt("Stop"), 0, Stop.values().length - 1)];
        super.read(tag, registries, clientPacket);
    }

    /** The speed panel, on any side, above the disc. */
    private static final class SideValueBox extends ValueBoxTransform.Sided {
        @Override
        protected Vec3 getSouthLocation() {
            return new Vec3(0.5, 13 / 16.0, 15.5 / 16);
        }

        @Override
        protected boolean isSideActive(BlockState state, Direction direction) {
            return direction.getAxis().isHorizontal();
        }
    }
}
