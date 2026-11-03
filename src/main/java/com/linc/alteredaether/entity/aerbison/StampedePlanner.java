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

import com.linc.alteredaether.config.AACommonConfig;
import com.linc.alteredaether.entity.Aerbison;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Finds a heading a stampede can run: clear of walls along a corridor as wide as the herd, with solid ground to land
 * on at the end of the glide, for every line of the corridor. The test replays the run's own motion (see
 * {@link StampedeMath}) one block at a time, so a heading that passes is one the run will keep to. It reads only
 * loaded chunks; a heading that leaves them fails.
 */
public final class StampedePlanner {
    /** A heading and how far to run along it. */
    public record Plan(double hx, double hz, int runLength) {
    }

    /** The corridor's half width for a herd of this size, as the wedge fills it. */
    static double halfWidth(int herd) {
        return Math.min(1.8 * Math.ceil(herd / 2.0), 4.5) + 0.8;
    }

    /**
     * @param aim when set, the run must pass within two blocks of it (a defence); otherwise any heading will do
     */
    @Nullable
    public static Plan plan(ServerLevel level, Aerbison leader, int herd, @Nullable Entity aim) {
        int min = AACommonConfig.AERBISON_RUN_MIN.get();
        int max = Math.max(min, AACommonConfig.AERBISON_RUN_MAX.get());
        int length = min + leader.getRandom().nextInt(max - min + 1);
        List<Double> angles = new ArrayList<>();
        if (aim != null) {
            double toward = Math.atan2(aim.getZ() - leader.getZ(), aim.getX() - leader.getX());
            double distance = leader.distanceTo(aim);
            for (int deg = -20; deg <= 20; deg += 10) {
                if (Math.abs(distance * Math.sin(Math.toRadians(deg))) <= 2) {
                    angles.add(toward + Math.toRadians(deg));
                }
            }
            Collections.shuffle(angles, new java.util.Random(leader.getRandom().nextLong()));
        } else {
            double start = leader.getRandom().nextDouble() * Math.PI * 2;
            for (int i = 0; i < 12; i++) {
                angles.add(start + i * Math.PI / 6);
            }
            Collections.shuffle(angles, new java.util.Random(leader.getRandom().nextLong()));
        }
        double hw = halfWidth(herd);
        int maxClimb = AACommonConfig.AERBISON_MAX_CLIMB.get();
        for (int runLength : new int[]{length, min}) {
            for (double angle : angles) {
                double hx = Math.cos(angle), hz = Math.sin(angle);
                if (corridorOk(level, leader.position(), hx, hz, hw, runLength, maxClimb)) {
                    return new Plan(hx, hz, runLength);
                }
            }
            if (length == min) {
                break;
            }
        }
        return null;
    }

    private static boolean corridorOk(ServerLevel level, Vec3 start, double hx, double hz, double hw, int runLength,
                                      int maxClimb) {
        double rx = -hz, rz = hx;
        for (double lat : new double[]{0, -hw / 2, hw / 2, -hw, hw}) {
            if (!lineOk(level, start.x + rx * lat, start.y, start.z + rz * lat, hx, hz, runLength, maxClimb)) {
                return false;
            }
        }
        return true;
    }

    private static boolean lineOk(ServerLevel level, double sx, double sy, double sz, double hx, double hz, int runLength,
                                  int maxClimb) {
        double y = sy;
        for (double s = 0.5; s <= runLength; s += 0.5) {
            double x = sx + hx * s, z = sz + hz * s;
            if (!level.hasChunkAt(BlockPos.containing(x, y, z))) {
                return false;
            }
            double target = StampedeMath.desiredY(level, x, z, hx, hz, y, sy, s, maxClimb);
            if (Double.isNaN(target)) {
                return false;
            }
            y += Math.max(-0.4, Math.min(0.5, target - y));
            if (!StampedeMath.columnClear(level, x, z, y)) {
                return false;
            }
        }
        double x = sx + hx * runLength, z = sz + hz * runLength;
        for (int tick = 0; tick < 600; tick++) {
            double speed = StampedeMath.glideSpeed(tick);
            x += hx * speed;
            z += hz * speed;
            y -= StampedeMath.SINK;
            if (y < level.getMinBuildHeight()) {
                return false;
            }
            BlockPos here = BlockPos.containing(x, y, z);
            if (!level.hasChunkAt(here)) {
                return false;
            }
            if (!StampedeMath.columnClear(level, x, z, y)) {
                return false;
            }
            double ground = StampedeMath.groundTop(level, x, z, y + 1, 3);
            if (Double.isNaN(ground)) {
                return false;
            }
            if (ground != Double.NEGATIVE_INFINITY && y <= ground + 0.05) {
                BlockPos floor = BlockPos.containing(x, ground - 0.1, z);
                BlockState state = level.getBlockState(floor);
                return level.getFluidState(floor).isEmpty() && level.getFluidState(floor.above()).isEmpty()
                        && !state.is(Blocks.MAGMA_BLOCK) && !state.is(Blocks.CACTUS);
            }
        }
        return false;
    }

    private StampedePlanner() {
    }
}
