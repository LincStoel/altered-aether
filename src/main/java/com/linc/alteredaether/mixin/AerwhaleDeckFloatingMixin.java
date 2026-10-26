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

import com.linc.alteredaether.entity.AerwhaleDeck;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A player standing on an aerwhale's back has only sky around them, and the server's check for floating looks only at
 * blocks, so on a server that doesn't allow flight it would kick them after four seconds. A whale's deck counts as
 * something to stand on.
 *
 * <p>The reach below the feet is generous: the server lays every deck to fit the Aether's current model, while a
 * client drawing the b1.7.3 model stands its player on a higher one.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class AerwhaleDeckFloatingMixin {
    private static final double DECK_REACH = 2.5;

    @Inject(method = "noBlocksAround", at = @At("RETURN"), cancellable = true)
    private void altered_aether$standingOnAerwhale(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && AerwhaleDeck.anyUnder(entity, entity.getBoundingBox().expandTowards(0, -DECK_REACH, 0))) {
            cir.setReturnValue(false);
        }
    }
}
