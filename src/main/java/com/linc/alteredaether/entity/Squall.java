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
 * a crackle of warning from the squall, sparks gathering at the target's feet, then the bolt.
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
    /**
     * Ticks from the warning to the strike. The zephyr's swelling animation runs while the charge time is between 0
     * and 20, so a strike at 20 lands as the cloud reaches its fullest, as a zephyr's snowball leaves it.
     */
    private static final int WARNING_TICKS = 20;
    /** The aim follows the target until this many ticks before the strike, then holds. */
    private static final int AIM_LOCK_TICKS = 8;
    /** Ticks after a strike before the squall can warn again. */
    private static final int COOLDOWN_TICKS = 60;

    private final int hoverHeight;

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

    /**
     * Steers to a point above the target. The zephyr's move control flies in short pushes and gives up on a point it
     * can't reach in a straight line, so the point is set again every few ticks. The point stays below the build
     * limit, where a zephyr is discarded, so a target flying near the top of the world doesn't make the squall vanish.
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
        public void tick() {
            LivingEntity target = squall.getTarget();
            if (target == null || --retarget > 0) {
                return;
            }
            retarget = RETARGET_TICKS;
            double dx = (squall.getRandom().nextDouble() * 2 - 1) * HOVER_JITTER;
            double dz = (squall.getRandom().nextDouble() * 2 - 1) * HOVER_JITTER;
            double ceiling = squall.level().getMaxBuildHeight() - squall.getBbHeight() - 2;
            squall.getMoveControl().setWantedPosition(target.getX() + dx,
                    Math.min(target.getY() + squall.hoverHeight, ceiling), target.getZ() + dz, 1.0);
        }
    }

    /**
     * Charges and strikes, reusing the zephyr's charge time so the cloud swells as it does for a snowball. Once in
     * position over a target it can see, the squall crackles a warning and sparks gather at the target's feet; the
     * bolt follows {@value #WARNING_TICKS} ticks later. The aim tracks the target until the last
     * {@value #AIM_LOCK_TICKS} ticks, so it can be dodged only by a target already moving when it locks.
     */
    static final class CallLightningGoal extends Goal {
        private final Squall squall;
        @Nullable
        private Vec3 aim;

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
        }

        @Override
        public void stop() {
            squall.setChargeTime(0);
            aim = null;
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
            } else if (charge == 0) {
                if (inPosition(target)) {
                    aim = target.position();
                    squall.playSound(AARegistries.INCOMING_LIGHTNING.get(), 2.0F,
                            0.95F + squall.getRandom().nextFloat() * 0.1F);
                    squall.setChargeTime(1);
                }
            } else {
                charge++;
                if (charge <= WARNING_TICKS - AIM_LOCK_TICKS || aim == null) {
                    aim = target.position();
                }
                TempestStorms.warningSparks(level, aim, WARNING_TICKS - charge);
                if (charge >= WARNING_TICKS) {
                    strike(level, aim);
                    aim = null;
                    squall.setChargeTime(-COOLDOWN_TICKS);
                } else {
                    squall.setChargeTime(charge);
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
