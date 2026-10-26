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
package com.linc.alteredaether.entity;

import com.linc.alteredaether.AlteredAether;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.player.PlayerFlyableFallEvent;

/**
 * A player landing on the back of their own aerwhale is taken aboard, without the fall hurting. A player lands on the
 * deck as on a block, so the server hears of it as a fall when their client reports touching down.
 */
@EventBusSubscriber(modid = AlteredAether.MOD_ID)
public final class AerwhaleLanding {
    @SubscribeEvent
    public static void onFall(LivingFallEvent event) {
        if (event.getEntity() instanceof Player player && land(player, event.getDistance())) {
            event.setCanceled(true);
        }
    }

    /** The same for players who can fly, such as in creative mode, whose falls never hurt. */
    @SubscribeEvent
    public static void onFlyableFall(PlayerFlyableFallEvent event) {
        land(event.getEntity(), event.getDistance());
    }

    private static boolean land(Player player, float distance) {
        if (player.level().isClientSide || player.isPassenger()) {
            return false;
        }
        for (TamedAerwhale whale : player.level().getEntitiesOfClass(TamedAerwhale.class,
                player.getBoundingBox().inflate(4, 6, 4))) {
            if (whale.tryLandingMount(player, distance)) {
                return true;
            }
        }
        return false;
    }

    private AerwhaleLanding() {
    }
}
