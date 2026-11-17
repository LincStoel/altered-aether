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

import com.linc.alteredaether.compat.SableLift;
import dev.ryanhcode.sable.physics.floating_block.FloatingBlockController;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Every block Sable lifts on a sub-level passes through its controller's queue; enchanted gravitite is held back here
 * and registered by {@link SableLift} only while it is powered. Applied only when Sable is loaded (see AAMixinPlugin).
 */
@Mixin(value = FloatingBlockController.class, remap = false)
public abstract class SableGravititeLiftMixin implements SableLift.Tracked {
    @Shadow
    @Final
    private ServerSubLevel subLevel;

    // Created on first use, not by initialisers: Sable can register blocks on a controller (loading a stored sub-level)
    // before fields added by a mixin have been set.
    @Unique
    private LongSet altered_aether$known;
    @Unique
    private LongSet altered_aether$lifting;

    @Inject(method = "queueAddFloatingBlock", at = @At("HEAD"), cancellable = true)
    private void altered_aether$holdBackGravitite(BlockState state, BlockPos pos, CallbackInfo ci) {
        if (!SableLift.allowAdd(this, state, pos)) {
            ci.cancel();
        }
    }

    @Inject(method = "queueRemoveFloatingBlock", at = @At("HEAD"), cancellable = true)
    private void altered_aether$releaseGravitite(BlockState state, BlockPos pos, CallbackInfo ci) {
        if (!SableLift.allowRemove(this, state, pos)) {
            ci.cancel();
        }
    }

    @Override
    public synchronized LongSet altered_aether$known() {
        if (altered_aether$known == null) {
            altered_aether$known = SableLift.newSet();
        }
        return altered_aether$known;
    }

    @Override
    public synchronized LongSet altered_aether$lifting() {
        if (altered_aether$lifting == null) {
            altered_aether$lifting = SableLift.newSet();
        }
        return altered_aether$lifting;
    }

    @Override
    public ServerLevel altered_aether$level() {
        return subLevel.getLevel();
    }
}
