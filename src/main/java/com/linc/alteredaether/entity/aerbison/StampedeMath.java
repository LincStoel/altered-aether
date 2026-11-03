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
package com.linc.alteredaether.entity.aerbison;

import com.linc.alteredaether.AATags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * What a stampede's run looks like, shared by the planner that tests a heading and the run itself, so the two agree:
 * the ground stretch hugs the terrain, then the bison climb on the gust, then glide down.
 */
final class StampedeMath {
    static final double RUN_SPEED = 0.45;
    static final double GLIDE_START_SPEED = 0.45;
    static final double GLIDE_END_SPEED = 0.28;
    /** Blocks a glide sinks each tick. */
    static final double SINK = 0.14;
    /** The ground stretch before the climb starts, in blocks, and the blocks of run for each block climbed. */
    static final double GROUND_STRETCH = 7;
    static final double CLIMB_RUN = 5;
    /** Space a bison needs above its feet. */
    static final double HEIGHT = 2.2;

    static double glideSpeed(int ticks) {
        return GLIDE_END_SPEED + (GLIDE_START_SPEED - GLIDE_END_SPEED) * Math.pow(0.96, ticks);
    }

    /** Free of anything solid: air, and what a stampede tramples. */
    static boolean passable(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.is(AATags.AERBISON_TRAMPLES) || state.getCollisionShape(level, pos).isEmpty();
    }

    /**
     * Top of the ground under {@code (x, z)}, looking down from {@code fromY}: NaN if that column isn't loaded,
     * negative infinity if there is nothing within {@code maxDrop} blocks.
     */
    static double groundTop(Level level, double x, double z, double fromY, int maxDrop) {
        for (int i = 0; i <= maxDrop; i++) {
            BlockPos pos = BlockPos.containing(x, fromY - i, z);
            if (!level.hasChunkAt(pos)) {
                return Double.NaN;
            }
            BlockState state = level.getBlockState(pos);
            if (state.is(AATags.AERBISON_TRAMPLES)) {
                continue;
            }
            VoxelShape shape = state.getCollisionShape(level, pos);
            if (!shape.isEmpty()) {
                return pos.getY() + shape.max(Direction.Axis.Y);
            }
        }
        return Double.NEGATIVE_INFINITY;
    }

    /** The height a bison at {@code (x, z)}, {@code s} blocks into the run, should be heading for; NaN if unloaded. */
    static double desiredY(Level level, double x, double z, double hx, double hz, double prevY, double takeoffY, double s,
                           int maxClimb) {
        double ground = groundTop(level, x + hx * 1.5, z + hz * 1.5, prevY + 3, 10);
        if (Double.isNaN(ground)) {
            return Double.NaN;
        }
        if (s < GROUND_STRETCH) {
            return ground == Double.NEGATIVE_INFINITY ? prevY : ground;
        }
        double climb = Math.min(maxClimb, (s - GROUND_STRETCH) / CLIMB_RUN);
        return Math.max(takeoffY + climb, ground);
    }

    /** Whether the space a bison fills at {@code (x, y, z)} is clear. */
    static boolean columnClear(Level level, double x, double z, double y) {
        int from = (int) Math.floor(y + 0.05);
        int to = (int) Math.floor(y + HEIGHT - 0.05);
        for (int cy = from; cy <= to; cy++) {
            BlockPos pos = BlockPos.containing(x, cy, z);
            if (!level.hasChunkAt(pos) || !passable(level, pos)) {
                return false;
            }
        }
        return true;
    }

    private StampedeMath() {
    }
}
