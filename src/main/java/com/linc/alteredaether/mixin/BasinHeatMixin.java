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

import com.linc.alteredaether.compat.CreateCompat;
import com.linc.alteredaether.config.AACommonConfig;
import com.linc.alteredaether.electric.SquallGeneratorBlock;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A basin over a running Squall Generator is heated as over a lit blaze burner, superheated while a storm or sterling
 * aercloud lasts. Basins only: steam engines still want fire. Applied only with Create (see AAMixinPlugin).
 */
@Mixin(value = BasinBlockEntity.class, remap = false)
public abstract class BasinHeatMixin {
    @Inject(method = "getHeatLevelOf", at = @At("HEAD"), cancellable = true)
    private static void altered_aether$squallHeat(BlockState state,
                                                  CallbackInfoReturnable<BlazeBurnerBlock.HeatLevel> cir) {
        if (state.getBlock() instanceof SquallGeneratorBlock && AACommonConfig.compat(CreateCompat.MOD)) {
            cir.setReturnValue(switch (state.getValue(SquallGeneratorBlock.HEAT)) {
                case NONE -> BlazeBurnerBlock.HeatLevel.NONE;
                case HEATED -> BlazeBurnerBlock.HeatLevel.KINDLED;
                case SUPERHEATED -> BlazeBurnerBlock.HeatLevel.SEETHING;
            });
        }
    }
}
