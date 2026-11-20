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
package com.linc.alteredaether.compat.boulder;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.physics.callback.BlockSubLevelCollisionCallback;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.joml.Vector3d;

/**
 * What every aercloud does when a Sable body touches it (see SableAercloudPassMixin): a falling boulder punches
 * straight through, the contact dropped so the cloud takes nothing off its speed, and the cloud block broken where
 * the land isn't protected. Any other body (an airship, a contraption) collides with the cloud as it always has.
 *
 * <p>Sable runs this inside its physics step on the server thread, as it does its own fragile-block callback.
 */
final class AercloudPassCallback implements BlockSubLevelCollisionCallback {
    static final AercloudPassCallback INSTANCE = new AercloudPassCallback();

    private static final CollisionResult PASS = new CollisionResult(new Vector3d(), true);

    /** How far from a boulder's centre a cloud it touches can be, when Sable doesn't say which body hit. */
    private static final double REACH = 8;

    /** How many cloud contacts boulders have passed through, for the checks to read. */
    static volatile int passes;

    private AercloudPassCallback() {
    }

    @Override
    public CollisionResult sable$onCollision(BlockPos pos, BlockPos other, Vector3d velocity, double speed) {
        SubLevelPhysicsSystem system = SubLevelPhysicsSystem.getCurrentlySteppingSystem();
        if (system == null || !FallingBoulders.active()) {
            return CollisionResult.NONE;
        }
        ServerLevel level = system.getLevel();
        if (level == null || !byBoulder(level, pos, other)) {
            return CollisionResult.NONE;
        }
        if (BoulderProtection.allowed(level, pos)) {
            level.destroyBlock(pos, true);
        }
        passes++;
        return PASS;
    }

    /** Whether the body touching the cloud at {@code pos} is a boulder; {@code other} is its block, when known. */
    private static boolean byBoulder(ServerLevel level, BlockPos pos, BlockPos other) {
        if (other != null) {
            SubLevel body = Sable.HELPER.getContaining(level, other);
            if (body != null) {
                return FallingBoulders.isBoulder(body);
            }
        }
        double reach = REACH * REACH;
        for (SubLevel body : SubLevelContainer.getContainer(level).getAllSubLevels()) {
            if (FallingBoulders.isBoulder(body)
                    && body.logicalPose().position().distanceSquared(pos.getX() + 0.5, pos.getY() + 0.5,
                    pos.getZ() + 0.5) < reach) {
                return true;
            }
        }
        return false;
    }
}
