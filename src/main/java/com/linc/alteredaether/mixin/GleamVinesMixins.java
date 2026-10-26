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

import com.linc.alteredaether.block.GleamVines;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** The two places vanilla names its cave vine blocks outright, taught gleam vines (see {@link GleamVines}). */
public final class GleamVinesMixins {
    /** A fox eating from gleam vines picks a gleamberry, not a glow berry. */
    @Mixin(targets = "net.minecraft.world.entity.animal.Fox$FoxEatBerriesGoal")
    public abstract static class FoxPick {
        @WrapOperation(method = "pickGlowBerry", at = @At(value = "INVOKE",
                target = "Lnet/minecraft/world/level/block/CaveVines;use(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/InteractionResult;"))
        private InteractionResult altered_aether$pickGleamberry(Entity entity, BlockState state, Level level, BlockPos pos,
                                                                Operation<InteractionResult> original) {
            return GleamVines.is(state) ? GleamVines.pick(entity, state, level, pos) : original.call(entity, state, level, pos);
        }
    }

    /** Pollinating bees grow gleam vines as they do cave vines. */
    @Mixin(targets = "net.minecraft.world.entity.animal.Bee$BeeGrowCropGoal")
    public abstract static class BeeGrow {
        @WrapOperation(method = "tick", require = 2, at = @At(value = "INVOKE",
                target = "Lnet/minecraft/world/level/block/state/BlockState;is(Lnet/minecraft/world/level/block/Block;)Z"))
        private boolean altered_aether$growGleamVines(BlockState state, Block block, Operation<Boolean> original) {
            return original.call(state, block) || GleamVines.standsInFor(state, block);
        }
    }

    private GleamVinesMixins() {
    }
}
