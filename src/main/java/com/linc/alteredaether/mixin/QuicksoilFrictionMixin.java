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

import com.aetherteam.aether.block.natural.QuicksoilBlock;
import com.linc.alteredaether.AATags;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Quicksoil's slipperiness and its speed are one thing: a friction above one. Entities in
 * {@link AATags#SURE_FOOTED} (the aerbison) get plain ground's friction on it instead, so they neither slide nor speed up.
 */
@Mixin(QuicksoilBlock.class)
public abstract class QuicksoilFrictionMixin {
    @Inject(method = "getFriction", at = @At("HEAD"), cancellable = true)
    private void altered_aether$sureFooted(BlockState state, LevelReader level, BlockPos pos, Entity entity,
                                           CallbackInfoReturnable<Float> cir) {
        if (entity != null && entity.getType().is(AATags.SURE_FOOTED)) {
            cir.setReturnValue(0.6F);
        }
    }
}
