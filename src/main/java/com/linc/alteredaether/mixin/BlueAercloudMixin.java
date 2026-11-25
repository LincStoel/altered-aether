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

import com.aetherteam.aether.block.natural.BlueAercloudBlock;
import com.linc.alteredaether.electric.StaticBoots;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Static Boots hold their wearer down, so a blue aercloud doesn't throw them up: it only catches their fall, as any
 * aercloud does.
 */
@Mixin(BlueAercloudBlock.class)
public abstract class BlueAercloudMixin {
    @Inject(method = "entityInside", at = @At("HEAD"), cancellable = true)
    private void altered_aether$noBounce(BlockState state, Level level, BlockPos pos, Entity entity, CallbackInfo ci) {
        if (entity instanceof LivingEntity living && StaticBoots.wearing(living)) {
            entity.resetFallDistance();
            Vec3 motion = entity.getDeltaMovement();
            if (motion.y < 0) {
                // as AercloudBlock.entityInside: all but stopped
                entity.setDeltaMovement(motion.multiply(1, 0.005, 1));
            }
            ci.cancel();
        }
    }
}
