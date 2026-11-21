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

import com.linc.alteredaether.compat.cloud.ShipRiders;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Deep Aether's sterling and chromatic aerclouds aren't the Aether's AercloudBlock, so AercloudDragMixin's rider rule
 * doesn't reach them: these give them the same one, letting anyone riding a Sable body through them (see ShipRiders).
 * Applied only with Deep Aether and Sable (see AAMixinPlugin); {@code require = 0} so a Deep Aether update can't stop
 * the game from starting. (Classes in this package can't be called at runtime, so each hook spells out its rule.)
 */
public final class DeepAetherCloudRiderMixins {
    private DeepAetherCloudRiderMixins() {
    }

    @Pseudo
    @Mixin(targets = "io.github.razordevs.deep_aether.block.natural.SterlingAercloudBlock")
    public abstract static class Sterling {
        @Inject(method = "getCollisionShape", at = @At("HEAD"), cancellable = true, require = 0)
        private void altered_aether$ridersPass(BlockState state, BlockGetter level, BlockPos pos,
                CollisionContext context, CallbackInfoReturnable<VoxelShape> cir) {
            if (context instanceof EntityCollisionContext entityContext
                    && ShipRiders.onShip(entityContext.getEntity())) {
                cir.setReturnValue(Shapes.empty());
            }
        }

        @Inject(method = "entityInside", at = @At("HEAD"), cancellable = true, require = 0)
        private void altered_aether$ridersUntouched(BlockState state, Level level, BlockPos pos, Entity entity,
                CallbackInfo ci) {
            if (ShipRiders.onShip(entity)) {
                ci.cancel();
            }
        }
    }

    @Pseudo
    @Mixin(targets = "io.github.razordevs.deep_aether.block.misc.ChromaticAercloudBlock")
    public abstract static class Chromatic {
        @Inject(method = "getCollisionShape", at = @At("HEAD"), cancellable = true, require = 0)
        private void altered_aether$ridersPass(BlockState state, BlockGetter level, BlockPos pos,
                CollisionContext context, CallbackInfoReturnable<VoxelShape> cir) {
            if (context instanceof EntityCollisionContext entityContext
                    && ShipRiders.onShip(entityContext.getEntity())) {
                cir.setReturnValue(Shapes.empty());
            }
        }

        @Inject(method = "entityInside", at = @At("HEAD"), cancellable = true, require = 0)
        private void altered_aether$ridersUntouched(BlockState state, Level level, BlockPos pos, Entity entity,
                CallbackInfo ci) {
            if (ShipRiders.onShip(entity)) {
                ci.cancel();
            }
        }
    }
}
