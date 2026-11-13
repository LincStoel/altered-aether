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

import com.linc.alteredaether.entity.golem.GolemNetworks;
import com.linc.alteredaether.entity.golem.GolemSorter;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ComparatorBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A comparator facing the block a gravitite golem sits on reads how full the golem's whole network is, as it would read
 * one chest (see GolemSorter.signal). The network tells comparators there when its contents change.
 */
@Mixin(ComparatorBlock.class)
public abstract class ComparatorInputMixin {
    @Inject(method = "getInputSignal", at = @At("RETURN"), cancellable = true)
    private void altered_aether$readGolemNetwork(Level level, BlockPos pos, BlockState state, CallbackInfoReturnable<Integer> cir) {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        GolemNetworks.Net net = GolemNetworks.get(server).netAtFloor(pos.relative(state.getValue(ComparatorBlock.FACING)));
        if (net != null) {
            cir.setReturnValue(GolemSorter.signal(server, net));
        }
    }
}
