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
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.content.redstone.thresholdSwitch.ThresholdSwitchBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.inventory.InvManipulationBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import javax.annotation.Nullable;

/**
 * Create's threshold switch facing the block a gravitite golem sits on reads the golem's whole network, as one
 * inventory (see GolemSorter.networkView). The block has no inventory of its own, so Create finds none there; this
 * answers for it. Applied only with Create (see AAMixinPlugin), and it fails quietly if Create changes these methods:
 * the Create GameTests are what notice.
 */
@Mixin(value = ThresholdSwitchBlockEntity.class, remap = false)
public abstract class CreateThresholdSwitchMixin {
    @Shadow
    private BlockPos getTargetPos() {
        throw new AssertionError();
    }

    @Unique
    @Nullable
    private GolemNetworks.Net altered_aether$network() {
        if (!(((BlockEntity) (Object) this).getLevel() instanceof ServerLevel level)) {
            return null;
        }
        return GolemNetworks.get(level).netAtFloor(getTargetPos());
    }

    @WrapOperation(method = "updateCurrentLevel", require = 0, at = @At(value = "INVOKE",
            target = "Lcom/simibubi/create/foundation/blockEntity/behaviour/inventory/InvManipulationBehaviour;hasInventory()Z"))
    private boolean altered_aether$hasNetwork(InvManipulationBehaviour behaviour, Operation<Boolean> original) {
        return altered_aether$network() != null || original.call(behaviour);
    }

    @WrapOperation(method = "updateCurrentLevel", require = 0, at = @At(value = "INVOKE",
            target = "Lcom/simibubi/create/foundation/blockEntity/behaviour/inventory/InvManipulationBehaviour;getInventory()Ljava/lang/Object;"))
    private Object altered_aether$networkInventory(InvManipulationBehaviour behaviour, Operation<Object> original) {
        GolemNetworks.Net net = altered_aether$network();
        if (net != null && ((BlockEntity) (Object) this).getLevel() instanceof ServerLevel level) {
            return GolemSorter.networkView(level, net);
        }
        return original.call(behaviour);
    }
}
