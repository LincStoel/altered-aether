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

import com.aetherteam.aether.entity.passive.FlyingCow;
import com.aetherteam.aether.entity.passive.MountableAnimal;
import com.aetherteam.aether.entity.passive.Phyg;
import com.linc.alteredaether.AlteredAether;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.gameevent.GameEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Crouching and using an empty hand on a saddled phyg or flying cow takes its saddle back. The Aether only lets a saddle
 * come off by killing the mount.
 */
@EventBusSubscriber(modid = AlteredAether.MOD_ID)
public final class MountUnsaddling {
    private static final ResourceLocation TAKE_A_LOAD_OFF = AlteredAether.id("take_a_load_off");

    @SubscribeEvent
    public static void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getTarget() instanceof MountableAnimal mount) || !(mount instanceof Phyg || mount instanceof FlyingCow)) {
            return;
        }
        Player player = event.getEntity();
        if (!player.isSecondaryUseActive() || !event.getItemStack().isEmpty() || !mount.isAlive()
                || !mount.isSaddled() || mount.isVehicle()) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide));
        if (event.getLevel().isClientSide) {
            return;
        }
        mount.setSaddled(false);
        player.setItemInHand(event.getHand(), new ItemStack(Items.SADDLE));
        mount.playSound(SoundEvents.HORSE_SADDLE, 0.5F, 0.8F);
        mount.gameEvent(GameEvent.UNEQUIP, player);
        if (player instanceof ServerPlayer serverPlayer) {
            AdvancementHolder advancement = serverPlayer.server.getAdvancements().get(TAKE_A_LOAD_OFF);
            if (advancement != null) {
                serverPlayer.getAdvancements().award(advancement, "unsaddled");
            }
        }
    }

    private MountUnsaddling() {
    }
}
