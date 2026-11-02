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

import com.aetherteam.aether.entity.AetherEntityTypes;
import com.aetherteam.aether.entity.passive.Aerwhale;
import com.linc.alteredaether.AlteredAether;
import com.linc.alteredaether.config.AACommonConfig;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.player.PlayerFlyableFallEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * A player landing on the back of their own aerwhale, or of one with no owner, is taken aboard, without the fall
 * hurting; so is one landing on a bareback whale, which has no deck to stand on, or a wild one, which starts being
 * broken in. A player lands on the
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

    /**
     * Whales with no deck have no floor to land on, so a player falling onto one would drop straight through. Each
     * tick, a falling player who passed through the top of a bareback whale this tick is taken aboard: the way back on
     * after being bucked off. A wild one starts being broken in, as if climbed on.
     */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (!player.level().isClientSide && !player.onGround() && !player.isPassenger() && !player.isSpectator()
                && !player.getAbilities().flying && player.getY() < player.yo
                && player.fallDistance >= TamedAerwhale.LANDING_FALL) {
            catchFalling(player);
        }
    }

    /** Takes {@code player} aboard a deckless aerwhale they have just fallen onto, if any. Returns whether it did. */
    public static boolean catchFalling(Player player) {
        AABB box = player.getBoundingBox();
        for (Aerwhale whale : player.level().getEntitiesOfClass(Aerwhale.class, box.inflate(4, 6, 4))) {
            if (whale.isVehicle() || AerwhaleMode.deepAether() || !whale.isAlive()) {
                continue;
            }
            AABB body = whale.getBoundingBox();
            // the feet crossed the top of its back this tick, with some slack as the server hears of a client's movement late
            boolean crossed = player.yo >= body.maxY - 0.5 && player.getY() <= body.maxY + 0.5;
            if (!crossed || !body.inflate(0.5, 0, 0.5).intersects(box.minX, body.minY, box.minZ, box.maxX, body.maxY, box.maxZ)) {
                continue;
            }
            if (whale instanceof TamedAerwhale tamed) {
                if (tamed.tryBackLanding(player)) {
                    return true;
                }
            } else if (whale.getType() == AetherEntityTypes.AERWHALE.get() && AACommonConfig.AERWHALE_TAMING.get()
                    && TamedAerwhale.startBreaking(whale, player) != null) {
                player.resetFallDistance();
                return true;
            }
        }
        return false;
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
