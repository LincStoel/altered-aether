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
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Rears and stamps to throw itself up a ledge. When the path's next step is two to five blocks up and the bison is at
 * its foot, it stops, rears, and on the stamp a gust throws it up and toward that step, flinging anything close to it
 * aside. Rises of one block are just walked; the navigation (see {@link AerbisonNavigation}) plans routes up the
 * taller ones.
 */
public class GustStompGoal extends Goal {
    /** The rise below which a step is walked or jumped instead. */
    private static final double MIN_RISE = 1.7;
    private static final int COOLDOWN = 40;
    private static final double PUSH_RADIUS = 2.5;
    private static final double PUSH = 0.6;

    private final Aerbison bison;
    private int tick;
    private Vec3 step = Vec3.ZERO;
    private Vec3 destination = Vec3.ZERO;

    public GustStompGoal(Aerbison bison) {
        this.bison = bison;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        if (bison.stompCooldown > 0 || !bison.onGround() || bison.getState() != Aerbison.State.IDLE || bison.isBaby()) {
            return false;
        }
        Path path = bison.getNavigation().getPath();
        if (path == null || path.isDone()) {
            return false;
        }
        Node next = path.getNextNode();
        double rise = next.y - bison.getY();
        if (rise < MIN_RISE || rise > Aerbison.STOMP_REACH + 0.5) {
            return false;
        }
        double dx = next.x + 0.5 - bison.getX(), dz = next.z + 0.5 - bison.getZ();
        return dx * dx + dz * dz < 2.5 * 2.5;
    }

    @Override
    public void start() {
        Path path = bison.getNavigation().getPath();
        Node next = path.getNextNode();
        step = new Vec3(next.x + 0.5, next.y, next.z + 0.5);
        net.minecraft.core.BlockPos target = path.getTarget();
        destination = Vec3.atBottomCenterOf(target);
        tick = 0;
        bison.getNavigation().stop();
        bison.play(AerbisonTiming.EVENT_STOMP);
    }

    @Override
    public boolean canContinueToUse() {
        return tick < AerbisonTiming.STOMP_TICKS;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        tick++;
        bison.getLookControl().setLookAt(step.x, step.y + 1, step.z);
        if (tick < AerbisonTiming.STOMP_LAUNCH_TICK) {
            bison.setDeltaMovement(0, bison.getDeltaMovement().y, 0);
        } else if (tick == AerbisonTiming.STOMP_LAUNCH_TICK) {
            launch();
        } else if (tick == AerbisonTiming.STOMP_LAUNCH_TICK + 3) {
            bison.getNavigation().moveTo(destination.x, destination.y, destination.z, 1.0);
        }
    }

    @Override
    public void stop() {
        bison.stompCooldown = COOLDOWN;
    }

    private void launch() {
        double rise = Math.max(0, step.y - bison.getY());
        Vec3 horizontal = new Vec3(step.x - bison.getX(), 0, step.z - bison.getZ());
        Vec3 dir = horizontal.lengthSqr() < 1.0E-4 ? Vec3.ZERO : horizontal.normalize();
        double push = 0.22 + (rise > 3 ? 0.05 : 0);
        bison.setDeltaMovement(dir.x * push, launchVelocity(rise), dir.z * push);
        bison.hasImpulse = true;
        bison.playSound(AARegistries.AERBISON_STOMP.get(), 1.4F, 0.9F + bison.getRandom().nextFloat() * 0.2F);
        if (bison.level() instanceof ServerLevel level) {
            for (int i = 0; i < 24; i++) {
                double angle = i * Math.PI * 2 / 24;
                level.sendParticles(ParticleTypes.CLOUD, bison.getX() + Math.cos(angle) * 1.2, bison.getY() + 0.1,
                        bison.getZ() + Math.sin(angle) * 1.2, 0, Math.cos(angle), 0.05, Math.sin(angle), 0.35);
            }
            for (LivingEntity other : level.getEntitiesOfClass(LivingEntity.class,
                    bison.getBoundingBox().inflate(PUSH_RADIUS), e -> e != bison && !(e instanceof Aerbison))) {
                Vec3 away = new Vec3(other.getX() - bison.getX(), 0, other.getZ() - bison.getZ());
                away = away.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : away.normalize();
                other.push(away.x * PUSH, 0.2, away.z * PUSH);
                other.hurtMarked = true;
            }
        }
    }

    /** The smallest takeoff speed whose arc, under gravity and drag, peaks 0.6 above {@code rise}. */
    static double launchVelocity(double rise) {
        for (double v = 0.42; v < 1.4; v += 0.02) {
            double height = 0, speed = v;
            while (speed > 0) {
                height += speed;
                speed = (speed - 0.08) * 0.98;
            }
            if (height >= rise + 0.6) {
                return v;
            }
        }
        return 1.4;
    }
}
