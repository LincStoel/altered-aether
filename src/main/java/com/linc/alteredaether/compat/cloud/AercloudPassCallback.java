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
package com.linc.alteredaether.compat.cloud;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.physics.callback.BlockSubLevelCollisionCallback;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3d;

/**
 * What every aercloud does when a Sable body touches it (see SableAercloudPassMixin): the contact is dropped, so the
 * body passes through without losing any speed, and the cloud and those round it phase (see AercloudPhasing). A cloud
 * built into a body collides as any other block does.
 *
 * <p>Sable runs this inside its physics step on the server thread, as it does its own fragile-block callback.
 */
public final class AercloudPassCallback implements BlockSubLevelCollisionCallback {
    static final AercloudPassCallback INSTANCE = new AercloudPassCallback();

    private static final CollisionResult PASS = new CollisionResult(new Vector3d(), true);

    /** How many cloud contacts bodies have passed through, for the checks to read. */
    private static volatile int passes;

    private AercloudPassCallback() {
    }

    /**
     * The collision callback Sable bakes into a block state (SableAercloudPassMixin): this one for every cloud that has
     * none of its own.
     */
    public static BlockSubLevelCollisionCallback callbackFor(BlockState state, BlockSubLevelCollisionCallback callback) {
        return callback == null && AercloudPhasing.passes(state) ? INSTANCE : callback;
    }

    /** How many cloud contacts bodies have passed through since the last call; and clears it. */
    public static int takePasses() {
        int taken = passes;
        passes = 0;
        return taken;
    }

    @Override
    public CollisionResult sable$onCollision(BlockPos pos, BlockPos other, Vector3d velocity, double speed) {
        SubLevelPhysicsSystem system = SubLevelPhysicsSystem.getCurrentlySteppingSystem();
        if (system == null || !AercloudPhasing.enabled()) {
            return CollisionResult.NONE;
        }
        ServerLevel level = system.getLevel();
        if (level == null || Sable.HELPER.getContaining(level, pos) != null) {
            return CollisionResult.NONE;
        }
        AercloudPhasing.queue(level, pos, 1);
        passes++;
        return PASS;
    }
}
