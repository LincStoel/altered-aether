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
package com.linc.alteredaether.entity.aerbison;

import com.linc.alteredaether.entity.Aerbison;
import com.linc.alteredaether.registry.AARegistries;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * An adult's melee: it charges its target head down and, in reach, dips its head and hooks up; the hit lands as the
 * head comes up, throwing the target up and away.
 */
public class HornTossAttackGoal extends Goal {
    private static final double REACH = 2.9;
    private static final int COOLDOWN = 30;
    private final Aerbison bison;
    private int tossTick = -1;
    private int cooldown;
    private int repath;

    public HornTossAttackGoal(Aerbison bison) {
        this.bison = bison;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    private boolean valid(LivingEntity target) {
        return target != null && target.isAlive() && !(target instanceof Player p && (p.isCreative() || p.isSpectator()));
    }

    @Override
    public boolean canUse() {
        return !bison.isBaby() && bison.stampede == null && bison.getState() != Aerbison.State.STAGGER
                && valid(bison.getTarget());
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity target = bison.getTarget();
        return valid(target) && bison.stampede == null && bison.distanceToSqr(target) < 28 * 28
                && bison.getState() != Aerbison.State.STAGGER;
    }

    @Override
    public void start() {
        bison.setState(Aerbison.State.DEFENDING);
        tossTick = -1;
        repath = 0;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        LivingEntity target = bison.getTarget();
        if (target == null) {
            return;
        }
        bison.getLookControl().setLookAt(target, 30, 30);
        cooldown = Math.max(0, cooldown - 1);
        double reach = REACH + target.getBbWidth() / 2;
        boolean near = bison.distanceToSqr(target.getX(), target.getY(), target.getZ()) <= reach * reach;
        if (tossTick >= 0) {
            bison.getNavigation().stop();
            tossTick++;
            if (tossTick == AerbisonTiming.HORN_TOSS_HIT_TICK) {
                hit(target, reach + 1);
            }
            if (tossTick >= AerbisonTiming.HORN_TOSS_TICKS) {
                tossTick = -1;
                cooldown = COOLDOWN;
            }
            return;
        }
        if (near && cooldown == 0) {
            tossTick = 0;
            bison.play(AerbisonTiming.EVENT_HORN_TOSS);
        } else if (--repath <= 0) {
            repath = 8;
            bison.getNavigation().moveTo(target, 1.35);
        }
    }

    private void hit(LivingEntity target, double reach) {
        bison.playSound(AARegistries.AERBISON_HORN_TOSS.get(), 1.2F, 0.9F);
        if (bison.distanceToSqr(target) > reach * reach) {
            return;
        }
        float damage = (float) bison.getAttributeValue(Attributes.ATTACK_DAMAGE);
        if (target.hurt(bison.level().damageSources().mobAttack(bison), damage)) {
            Vec3 away = new Vec3(target.getX() - bison.getX(), 0, target.getZ() - bison.getZ());
            away = away.lengthSqr() < 1.0E-4 ? Vec3.ZERO : away.normalize();
            target.setDeltaMovement(away.x * 0.9, 0.6, away.z * 0.9);
            target.hurtMarked = true;
        }
    }

    @Override
    public void stop() {
        bison.getNavigation().stop();
        if (bison.getState() == Aerbison.State.DEFENDING) {
            bison.setState(Aerbison.State.IDLE);
        }
        bison.setTarget(null);
        tossTick = -1;
    }
}
