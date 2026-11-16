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

import com.aetherteam.aether.block.miscellaneous.FloatingBlock;
import com.linc.alteredaether.compat.SableLift;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * On a Sable ship, the Aether's floating blocks (gravitite ore, powered enchanted gravitite, the raw gravitite block)
 * stay where they are and lift the ship, instead of falling upward out of it with open air above. In the world they
 * float as the Aether has them. Applied only when Sable is loaded (see AAMixinPlugin).
 */
@Mixin(FloatingBlock.class)
public abstract class SableFloatingBlockMixin {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void altered_aether$stayOnShip(BlockState state, ServerLevel level, BlockPos pos, RandomSource random,
                                           CallbackInfo ci) {
        if (SableLift.onShip(level, pos)) {
            ci.cancel();
        }
    }
}
