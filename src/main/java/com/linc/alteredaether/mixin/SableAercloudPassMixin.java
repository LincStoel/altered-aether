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
package com.linc.alteredaether.mixin;

import com.linc.alteredaether.compat.cloud.AercloudPassCallback;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.ryanhcode.sable.api.physics.callback.BlockSubLevelCollisionCallback;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Sable's Rapier backend bakes each block state's collision callback into its native voxel collider once. Aerclouds,
 * phased or not, get one here (where they have none of their own) that lets every Sable body through them without
 * losing speed and phases them; see AercloudPassCallback.
 *
 * <p>Applies only with Sable loaded (see AAMixinPlugin); {@code require = 0} so a Sable update can't stop the game
 * from starting.
 */
@Pseudo
@Mixin(targets = "dev.ryanhcode.sable.physics.impl.rapier.collider.RapierVoxelColliderBakery")
public abstract class SableAercloudPassMixin {
    @ModifyExpressionValue(method = "buildPhysicsDataForBlock",
            at = @At(value = "INVOKE", target = "Ldev/ryanhcode/sable/api/block/BlockWithSubLevelCollisionCallback;"
                    + "sable$getCallback(Lnet/minecraft/world/level/block/state/BlockState;)"
                    + "Ldev/ryanhcode/sable/api/physics/callback/BlockSubLevelCollisionCallback;"),
            require = 0)
    private BlockSubLevelCollisionCallback altered_aether$aercloudsLetBodiesThrough(
            BlockSubLevelCollisionCallback callback, @Local(argsOnly = true) BlockState state) {
        return AercloudPassCallback.callbackFor(state, callback);
    }
}
