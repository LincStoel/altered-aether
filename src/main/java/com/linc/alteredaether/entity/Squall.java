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

import com.aetherteam.aether.entity.monster.Zephyr;
import com.linc.alteredaether.AlteredAether;
import com.linc.alteredaether.registry.AARegistries;
import com.linc.alteredaether.storm.TempestStorms;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;

import javax.annotation.Nullable;
import java.util.EnumSet;

/**
 * The storm cloud's zephyr, from Tempest Peaks. It drifts and turns exactly as a zephyr does, and swells the same way
 * as it charges, but instead of blowing snowballs it rides the air above its target and calls lightning down on them:
 * a crackle of warning from the squall, sparks rising from the ground under the target, then the bolt on that spot. After each strike it
 * breaks off up and away to recharge, then drifts back down over its target for the next.
 */
public class Squall extends Zephyr {
    /** Height above the target's feet a squall keeps to; each squall picks its own within this range. */
    private static final int HOVER_MIN = 8, HOVER_MAX = 10;
    /** Horizontal drift allowed round the point overhead, so a squall wanders rather than pinning in place. */
    private static final double HOVER_JITTER = 1.5;
    /** A squall can call a strike on a target no further than this to the side of it... */
    private static final double STRIKE_REACH = 4;
    /** ...and at least this far below it. */
    private static final double STRIKE_MIN_DROP = 5;
    /** Ticks from the warning to the strike. */
    private static final int WARNING_TICKS = 35;
    /**
     * The zephyr's swelling animation runs while the charge time is between 0 and 20, so the charge time starts
     * climbing this many ticks before the strike, which lands as the cloud reaches its fullest, as a zephyr's
     * snowball leaves it.
     */
    private static final int SWELL_TICKS = 20;
    /** Ticks after a strike before the squall can warn again. */
    private static final int COOLDOWN_TICKS = 60;
    /** The first ticks of the cooldown, spent flying up and away from the target before drifting back down. */
    private static final int RETREAT_TICKS = 40;
    /**
     * Push added each tick of the retreat. The zephyr's own pushes come every few ticks and leave it slow; this
     * settles near half a block a tick, so a squall breaks off sharply and ends up some fifteen blocks out.
     */
    private static final double RETREAT_PUSH = 0.04;
    /** Spread either side of straight away from the target, so retreats don't all run along one line. */
    private static final double RETREAT_SPREAD = Math.toRadians(35);
    /** Climb per block of horizontal retreat. */
    private static final double RETREAT_CLIMB = 0.8;

    private final int hoverHeight;
    /** Unit heading of the current retreat; set at each strike. */
    private Vec3 retreat = Vec3.ZERO;

    public Squall(EntityType<? extends Squall> type, Level level) {
        super(type, level);
        hoverHeight = HOVER_MIN + getRandom().nextInt(HOVER_MAX - HOVER_MIN + 1);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(4, new HoverAboveTargetGoal(this));
        goalSelector.addGoal(5, new Zephyr.RandomFloatAroundGoal(this));
        goalSelector.addGoal(7, new Zephyr.ZephyrLookGoal(this));
        goalSelector.addGoal(7, new CallLightningGoal(this));
        targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, true, false));
    }

    /** Lightning is a squall's own; its bolts, and the storms', pass through it. */
    @Override
    public void thunderHit(ServerLevel level, LightningBolt lightning) {
    }

    private boolean retreating() {
        return getChargeTime() < -(COOLDOWN_TICKS - RETREAT_TICKS);
    }

    /** Heads up and away from {@code target}, within {@link #RETREAT_SPREAD} of straight away. */
    private void startRetreat(LivingEntity target) {
        double dx = getX() - target.getX(), dz = getZ() - target.getZ();
        double angle = dx * dx + dz * dz < 0.25 ? getRandom().nextDouble() * Math.PI * 2 : Math.atan2(dz, dx);
        angle += (getRandom().nextDouble() * 2 - 1) * RETREAT_SPREAD;
        retreat = new Vec3(Math.cos(angle), RETREAT_CLIMB, Math.sin(angle)).normalize();
    }

    /**
     * Steers to a point above the target. The zephyr's move control flies in short pushes and gives up on a point it
     * can't reach in a straight line, so the point is set again every few ticks. The point stays below the build
     * limit, where a zephyr is discarded, so a target flying near the top of the world doesn't make the squall vanish.
     * Just after a strike it breaks off instead, flying up and away along its retreat heading, then comes back down.
     */
    static final class HoverAboveTargetGoal extends Goal {
        private static final int RETARGET_TICKS = 10;
        private final Squall squall;
        private int retarget;

        HoverAboveTargetGoal(Squall squall) {
            this.squall = squall;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = squall.getTarget();
            return target != null && target.isAlive();
        }

        @Override
        public void start() {
            retarget = 0;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity target = squall.getTarget();
            if (target == null) {
                return;
            }
            double ceiling = squall.level().getMaxBuildHeight() - squall.getBbHeight() - 2;
            if (squall.retreating()) {
                Vec3 push = squall.retreat.scale(RETREAT_PUSH);
                if (squall.getY() >= ceiling) {
                    push = push.multiply(1, 0, 1);
                }
                squall.setDeltaMovement(squall.getDeltaMovement().add(push));
                // Keep the move control off its last hover point, which would pull the squall back.
                squall.getMoveControl().setWantedPosition(squall.getX() + push.x * 100,
                        Math.min(squall.getY() + push.y * 100, ceiling), squall.getZ() + push.z * 100, 1.0);
                retarget = 0;
                return;
            }
            if (--retarget > 0) {
                return;
            }
            retarget = RETARGET_TICKS;
            double dx = (squall.getRandom().nextDouble() * 2 - 1) * HOVER_JITTER;
            double dz = (squall.getRandom().nextDouble() * 2 - 1) * HOVER_JITTER;
            squall.getMoveControl().setWantedPosition(target.getX() + dx,
                    Math.min(target.getY() + squall.hoverHeight, ceiling), target.getZ() + dz, 1.0);
        }
    }

    /**
     * Charges and strikes, reusing the zephyr's charge time so the cloud swells as it does for a snowball. Once in
     * position over a target it can see, the squall crackles a warning and sparks rise from the ground under the
     * target; the bolt lands there {@value #WARNING_TICKS} ticks later, the cloud swelling over the last
     * {@value #SWELL_TICKS}. The spot is fixed at the warning, so a target can dodge by moving off it.
     */
    static final class CallLightningGoal extends Goal {
        private final Squall squall;
        @Nullable
        private Vec3 aim;
        /** Ticks since the warning, or -1 when not charging. */
        private int warned = -1;

        CallLightningGoal(Squall squall) {
            this.squall = squall;
        }

        @Override
        public boolean canUse() {
            return squall.getTarget() != null;
        }

        @Override
        public void start() {
            squall.setChargeTime(0);
            aim = null;
            warned = -1;
        }

        @Override
        public void stop() {
            squall.setChargeTime(0);
            aim = null;
            warned = -1;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity target = squall.getTarget();
            if (target == null || !(squall.level() instanceof ServerLevel level)) {
                return;
            }
            int charge = squall.getChargeTime();
            if (charge < 0) {
                squall.setChargeTime(charge + 1);
            } else if (warned < 0) {
                if (inPosition(target)) {
                    aim = Vec3.atBottomCenterOf(TempestStorms.strikeSpot(level, target));
                    squall.playSound(AARegistries.INCOMING_LIGHTNING.get(), 2.0F,
                            0.95F + squall.getRandom().nextFloat() * 0.1F);
                    warned = 0;
                }
            } else {
                warned++;
                if (aim == null) {
                    aim = Vec3.atBottomCenterOf(TempestStorms.strikeSpot(level, target));
                }
                TempestStorms.warningSparks(level, aim, WARNING_TICKS - warned, WARNING_TICKS);
                if (warned >= WARNING_TICKS) {
                    strike(level, aim);
                    aim = null;
                    warned = -1;
                    squall.setChargeTime(-COOLDOWN_TICKS);
                    squall.startRetreat(target);
                } else {
                    squall.setChargeTime(Math.max(0, warned - (WARNING_TICKS - SWELL_TICKS)));
                }
            }
        }

        /** Close enough overhead, and with a clear line down to the target. */
        private boolean inPosition(LivingEntity target) {
            double dx = target.getX() - squall.getX(), dz = target.getZ() - squall.getZ();
            return dx * dx + dz * dz <= STRIKE_REACH * STRIKE_REACH
                    && squall.getY() - target.getY() >= STRIKE_MIN_DROP
                    && squall.hasLineOfSight(target);
        }

        private static void strike(ServerLevel level, Vec3 at) {
            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
            if (bolt != null) {
                bolt.moveTo(at);
                level.addFreshEntity(bolt);
            }
        }
    }

    @EventBusSubscriber(modid = AlteredAether.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
    public static final class AttributeRegistration {
        @SubscribeEvent
        public static void register(EntityAttributeCreationEvent event) {
            event.put(AARegistries.SQUALL.get(), Zephyr.createMobAttributes().build());
        }

        private AttributeRegistration() {
        }
    }
}
