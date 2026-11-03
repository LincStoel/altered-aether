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

import com.linc.alteredaether.config.AACommonConfig;
import com.linc.alteredaether.entity.Aerbison;
import com.linc.alteredaether.registry.AARegistries;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.List;

/**
 * Holds a bison to its stampede, taking its movement over from every other goal; and, for an idle adult in a herd,
 * the chance of starting one: now and then, with enough adults close together, none of them resting from a stampede,
 * and a heading that passes the planner's test. With none, the bison paws, snorts, and angry particles rise over it.
 */
public class StampedeGoal extends Goal {
    private static final double HERD_RADIUS = 16;
    private final Aerbison bison;
    @Nullable
    private StampedePlanner.Plan plan;
    private List<Aerbison> herd = List.of();

    public StampedeGoal(Aerbison bison) {
        this.bison = bison;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        if (bison.stampede != null) {
            return true;
        }
        if (!(bison.level() instanceof ServerLevel level) || bison.isBaby() || bison.stampedeCooldown > 0
                || bison.getState() != Aerbison.State.IDLE || !bison.onGround() || bison.getTarget() != null) {
            return false;
        }
        double minutes = AACommonConfig.AERBISON_STAMPEDE_INTERVAL_MINUTES.get();
        if (minutes <= 0) {
            return false;
        }
        herd = nearbyAdults(level, bison);
        if (herd.size() < AACommonConfig.AERBISON_STAMPEDE_MIN_ADULTS.get()) {
            return false;
        }
        // goals are checked every other tick; the chance is per herd, shared among its members
        double chance = 1.0 / (minutes * 60 * 10 * herd.size());
        if (bison.getRandom().nextDouble() >= chance) {
            return false;
        }
        for (Aerbison member : herd) {
            if (member.stampedeCooldown > 0 || member.stampede != null) {
                return false;
            }
        }
        plan = StampedePlanner.plan(level, bison, herd.size(), null);
        if (plan == null) {
            refuse(level);
            return false;
        }
        return true;
    }

    private void refuse(ServerLevel level) {
        int cooldown = AACommonConfig.AERBISON_STAMPEDE_FAILED_COOLDOWN.get() * 20;
        for (Aerbison member : herd) {
            member.stampedeCooldown = Math.max(member.stampedeCooldown, cooldown);
        }
        bison.play(AerbisonTiming.EVENT_REFUSE);
        bison.playSound(AARegistries.AERBISON_SNORT.get(), 1.4F, 0.7F);
        level.sendParticles(ParticleTypes.ANGRY_VILLAGER, bison.getX(), bison.getY() + 2.6, bison.getZ(), 3, 0.4, 0.2, 0.4, 0);
    }

    @Override
    public boolean canContinueToUse() {
        return bison.stampede != null;
    }

    @Override
    public void start() {
        if (bison.stampede == null && plan != null && bison.level() instanceof ServerLevel level) {
            List<Aerbison> followers = herd.stream().filter(b -> b != bison).toList();
            Stampede.start(level, bison, followers, plan);
        }
        plan = null;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        bison.getNavigation().stop();
    }

    static List<Aerbison> nearbyAdults(ServerLevel level, Aerbison center) {
        AABB box = center.getBoundingBox().inflate(HERD_RADIUS, 6, HERD_RADIUS);
        return level.getEntitiesOfClass(Aerbison.class, box, b -> b.isAlive() && !b.isBaby()
                && (b.getState() == Aerbison.State.IDLE || b.getState() == Aerbison.State.LYING || b == center));
    }
}
