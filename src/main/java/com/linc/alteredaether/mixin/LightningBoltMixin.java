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

import com.linc.alteredaether.electric.ElectricStrikes;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Tells {@link ElectricStrikes} where each bolt lands, just as it powers whatever rod is there: the one moment in a
 * bolt's life the game marks on the server, once per bolt.
 */
@Mixin(LightningBolt.class)
public abstract class LightningBoltMixin {
    @Inject(method = "powerLightningRod", at = @At("HEAD"))
    private void altered_aether$strike(CallbackInfo ci) {
        LightningBolt bolt = (LightningBolt) (Object) this;
        if (bolt.level() instanceof ServerLevel level) {
            // as LightningBolt.getStrikePosition: the block just under the bolt's foot
            Vec3 at = bolt.position();
            ElectricStrikes.onBolt(bolt, level, BlockPos.containing(at.x, at.y - 1.0E-6, at.z));
        }
    }
}
