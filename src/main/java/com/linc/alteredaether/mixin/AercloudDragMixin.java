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

import com.aetherteam.aether.block.natural.AercloudBlock;
import com.linc.alteredaether.AATags;
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
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Zephyrs and aerwhales steer like ghasts: before each move they test the path for any collision at all, and an
 * aercloud's thin floor counts, so one that drifts into a cloud finds every path blocked and hangs there. The cloud
 * also all but stops anything sinking through it and marks it as on the ground, which hands a flier the ground's
 * friction. Fliers in {@link AATags#IGNORES_AERCLOUD_DRAG} are spared both: a cloud only collides with them from
 * above, so they still land on its top but pass through its sides and underside, and it doesn't slow them. A blue
 * aercloud still bounces them, since its bounce comes before this.
 */
@Mixin(AercloudBlock.class)
public abstract class AercloudDragMixin {
    @Inject(method = "getCollisionShape", at = @At("RETURN"), cancellable = true)
    private void altered_aether$onlyFromAbove(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context,
            CallbackInfoReturnable<VoxelShape> cir) {
        if (context instanceof EntityCollisionContext entityContext && entityContext.getEntity() instanceof Entity entity
                && entity.getType().is(AATags.IGNORES_AERCLOUD_DRAG)
                && !cir.getReturnValue().isEmpty() && !context.isAbove(cir.getReturnValue(), pos, false)) {
            cir.setReturnValue(Shapes.empty());
        }
    }

    @Inject(method = "entityInside", at = @At("HEAD"), cancellable = true)
    private void altered_aether$skipDrag(BlockState state, Level level, BlockPos pos, Entity entity, CallbackInfo ci) {
        if (entity.getType().is(AATags.IGNORES_AERCLOUD_DRAG)) {
            entity.resetFallDistance();
            ci.cancel();
        }
    }
}
