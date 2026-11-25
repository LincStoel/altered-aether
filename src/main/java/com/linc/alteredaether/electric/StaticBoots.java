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
package com.linc.alteredaether.electric;

import com.linc.alteredaether.AlteredAether;
import com.linc.alteredaether.config.AACommonConfig;
import io.wispforest.accessories.api.AccessoriesAPI;
import io.wispforest.accessories.api.AccessoriesCapability;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.entity.living.LivingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * What the static in Static Boots does to their wearer, besides the knockback resistance on the item itself
 * ({@link StaticBootsItem}): it pulls them down. They sink {@code static_boots_fall} times as fast through the air,
 * gliders and all, and a jump boosted past a plain one (gravitite armour, Jump Boost) keeps only
 * {@code static_boots_jump} of its height, though never less than a plain jump. A blue aercloud doesn't bounce them
 * (see mixin/BlueAercloudMixin).
 *
 * <p>Players move on their own client, so the sinking is done there, on the local player; the jump event fires on
 * whichever side ran the jump.
 */
@EventBusSubscriber(modid = AlteredAether.MOD_ID)
public final class StaticBoots {
    public static boolean wearing(LivingEntity entity) {
        AccessoriesCapability accessories = AccessoriesCapability.get(entity);
        return accessories != null && accessories.isEquipped(ElectricRegistries.STATIC_BOOTS.get());
    }

    /**
     * The vertical speed of a jump made at {@code speed} once the boots have had their say: its height cut to
     * {@code share} (height goes with the square of the speed), but never below a plain jump at {@code plain}.
     */
    public static double jumpSpeed(double speed, double plain, double share) {
        if (speed <= plain) {
            return speed;
        }
        return Math.max(plain, speed * Math.sqrt(share));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onJump(LivingEvent.LivingJumpEvent event) {
        LivingEntity entity = event.getEntity();
        if (!wearing(entity)) {
            return;
        }
        Vec3 motion = entity.getDeltaMovement();
        double plain = entity.getAttributeBaseValue(Attributes.JUMP_STRENGTH) * blockJumpFactor(entity);
        double speed = jumpSpeed(motion.y, plain, AACommonConfig.STATIC_BOOTS_JUMP.get());
        if (speed < motion.y) {
            entity.setDeltaMovement(motion.x, speed, motion.z);
        }
    }

    /** As Entity.getBlockJumpFactor: the block the entity is in, or else the one under it (honey's half). */
    private static double blockJumpFactor(LivingEntity entity) {
        float in = entity.level().getBlockState(entity.blockPosition()).getBlock().getJumpFactor();
        float under = entity.level().getBlockState(entity.getBlockPosBelowThatAffectsMyMovement()).getBlock()
                .getJumpFactor();
        return in == 1.0F ? under : in;
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (!player.level().isClientSide || !player.isLocalPlayer() || !sinks(player)) {
            return;
        }
        double drop = player.getY() - player.yo;
        double extra = AACommonConfig.STATIC_BOOTS_FALL.get() - 1;
        if (drop < 0 && extra > 0 && wearing(player)) {
            // a share more of this tick's fall, moved rather than added to the speed, so it never compounds
            player.move(MoverType.SELF, new Vec3(0, drop * extra, 0));
        }
    }

    /** Falling or gliding freely: not flying, swimming, elytra-gliding, climbing or riding. */
    public static boolean sinks(Player player) {
        return !player.onGround() && !player.getAbilities().flying && !player.isInWater() && !player.isInLava()
                && !player.isFallFlying() && !player.onClimbable() && !player.isPassenger() && !player.isSpectator();
    }

    @EventBusSubscriber(modid = AlteredAether.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        @SubscribeEvent
        public static void onSetup(FMLCommonSetupEvent event) {
            event.enqueueWork(() -> AccessoriesAPI.registerAccessory(ElectricRegistries.STATIC_BOOTS.get(),
                    ElectricRegistries.STATIC_BOOTS.get()));
        }

        private Setup() {
        }
    }

    private StaticBoots() {
    }
}
