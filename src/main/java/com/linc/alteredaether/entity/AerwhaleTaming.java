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
import com.linc.alteredaether.registry.AARegistries;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * The wild aerwhale's side of taming: it follows a player holding blue berries, and feeding it them tames it now and
 * then, turning it into a {@link TamedAerwhale}. A spawn egg used on any aerwhale, wild or tamed, makes a calf. The
 * Aether's aerwhale has no interaction of its own to hook, so this listens for the player's interaction instead. Off
 * while Deep Aether's aerwhales are in play (see {@link AerwhaleMode}).
 */
@EventBusSubscriber(modid = AlteredAether.MOD_ID)
public final class AerwhaleTaming {
    @SubscribeEvent
    public static void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (AerwhaleMode.deepAether() || !(event.getTarget() instanceof Aerwhale whale) || !whale.isAlive()) {
            return;
        }
        ItemStack stack = event.getItemStack();
        if (TamedAerwhale.isCalfEgg(stack)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide));
            if (event.getLevel() instanceof ServerLevel level) {
                TamedAerwhale.spawnCalf(level, whale, event.getEntity(), stack);
            }
            return;
        }
        // exactly the Aether's wild aerwhale: a tamed one is a different entity type with its own interactions
        if (whale.getType() != AetherEntityTypes.AERWHALE.get() || whale.isVehicle()) {
            return;
        }
        double chance = AACommonConfig.AERWHALE_TAME_CHANCE.get();
        if (!TamedAerwhale.isTamingFood(stack) || chance <= 0) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide));
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        Player player = event.getEntity();
        stack.consume(1, player);
        if (level.random.nextDouble() < chance) {
            TamedAerwhale.tame(whale, player);
        } else {
            level.sendParticles(ParticleTypes.SMOKE, whale.getX(), whale.getY() + whale.getBbHeight() * 0.9, whale.getZ(),
                    7, whale.getBbWidth() * 0.5, whale.getBbHeight() * 0.25, whale.getBbWidth() * 0.5, 0.02);
        }
    }

    /** Wild aerwhales come to blue berries. */
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide && !AerwhaleMode.deepAether()
                && event.getEntity().getType() == AetherEntityTypes.AERWHALE.get()
                && event.getEntity() instanceof Aerwhale wild) {
            wild.goalSelector.addGoal(0, new AerwhaleTemptGoal(wild));
        }
    }

    @EventBusSubscriber(modid = AlteredAether.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
    public static final class AttributeRegistration {
        @SubscribeEvent
        public static void register(EntityAttributeCreationEvent event) {
            event.put(AARegistries.TAMED_AERWHALE.get(), Aerwhale.createMobAttributes()
                    .add(Attributes.FLYING_SPEED, TamedAerwhale.FLYING_SPEED).build());
        }

        private AttributeRegistration() {
        }
    }

    private AerwhaleTaming() {
    }
}
