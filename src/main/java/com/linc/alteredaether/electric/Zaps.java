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

import com.linc.alteredaether.AATags;
import com.linc.alteredaether.AlteredAether;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * A shock from live copper, a Squall Generator or a storm aercloud. It hurts for a set amount (electrocution passes
 * armour) and throws the target back the way it came, or away from the source if it was standing still. Leather and
 * gem boots ({@code #altered_aether:insulating_boots}) keep their wearer out of the circuit, as they keep a player off
 * charged copper in a storm (see storm/TempestStorms).
 */
public final class Zaps {
    public static final ResourceKey<DamageType> ELECTROCUTION =
            ResourceKey.create(Registries.DAMAGE_TYPE, AlteredAether.id("electrocution"));
    /** How hard a shock throws its target, before knockback resistance. */
    private static final double KNOCKBACK = 0.5;
    /** Horizontal travel per tick below which a target counts as standing still. */
    private static final double STILL = 1.0E-3;

    /** Whether the boots on {@code entity}'s feet keep it out of the circuit. */
    public static boolean insulated(LivingEntity entity) {
        return entity.getItemBySlot(EquipmentSlot.FEET).is(AATags.INSULATING_BOOTS);
    }

    /**
     * Shocks {@code target} for {@code damage} from a source at {@code from}. Returns whether it was hurt: not if it is
     * insulated, dead, or still flinching from the last hit.
     */
    public static boolean zap(ServerLevel level, LivingEntity target, Vec3 from, float damage) {
        if (!target.isAlive() || insulated(target) || damage <= 0) {
            return false;
        }
        if (!target.hurt(level.damageSources().source(ELECTROCUTION), damage)) {
            return false;
        }
        // back the way it came; players' own motion lives on the client, so it is read from their last move
        double dx = target.getX() - target.xo, dz = target.getZ() - target.zo;
        if (dx * dx + dz * dz < STILL * STILL) {
            dx = from.x - target.getX();
            dz = from.z - target.getZ();
            if (dx * dx + dz * dz < STILL * STILL) {
                dx = level.random.nextDouble() - 0.5;
                dz = level.random.nextDouble() - 0.5;
            }
        }
        // knockback pushes along (-dx, -dz): against the motion, or away from the source
        target.knockback(KNOCKBACK, dx, dz);
        target.hurtMarked = true;
        sparks(level, target.position().add(0, target.getBbHeight() * 0.5, 0), 8);
        return true;
    }

    /** A burst of sparks and a crackle at {@code at}. */
    public static void sparks(ServerLevel level, Vec3 at, int count) {
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, count, 0.25, 0.25, 0.25, 0.15);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.COPPER_BULB_TURN_ON, SoundSource.BLOCKS, 0.6F,
                1.6F + level.random.nextFloat() * 0.4F);
    }

    private Zaps() {
    }
}
