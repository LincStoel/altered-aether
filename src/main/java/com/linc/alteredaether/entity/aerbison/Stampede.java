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

import com.linc.alteredaether.AATags;
import com.linc.alteredaether.config.AACommonConfig;
import com.linc.alteredaether.entity.Aerbison;
import com.linc.alteredaether.registry.AARegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * One stampede, from the leader's wind-up to the last bison landing. The leader runs a heading, rising on the gust and
 * gliding down as planned (see {@link StampedePlanner}); the rest follow in a wedge, taking slots behind and to the
 * sides of it. Idle bison the run passes join. Everything in the way is thrown up and out, fragile blocks are
 * trampled, and a bison that is stopped by a wall drops out dazed. Server only and not saved: after a reload the bison
 * just stand about again. Driven by {@link StampedeManager}.
 */
public final class Stampede {
    private static final int WINDUP = AerbisonTiming.WINDUP_TICKS;
    private static final double SLOT_SPACING_BACK = 2.6, SLOT_SPACING_SIDE = 1.8, MAX_LATERAL = 4.5;
    private static final double JOIN_LATERAL = 6, JOIN_AHEAD = 8;
    private static final double CATCH_UP = 0.25, MAX_CORRECTION = 0.2;

    private enum Phase { WINDUP, RUN, GLIDE }

    private static final class Member {
        final Aerbison bison;
        int slot;
        /** Tick of the stampede this bison starts running at; joiners wait for it. */
        int activeAt;
        int runTicks;
        int blocked;
        boolean roused;

        Member(Aerbison bison, int activeAt) {
            this.bison = bison;
            this.activeAt = activeAt;
        }
    }

    final ServerLevel level;
    private final List<Member> members = new ArrayList<>();
    private final Set<UUID> hit = new HashSet<>();
    private final double hx, hz;
    private final int runLength;
    private final int maxClimb = AACommonConfig.AERBISON_MAX_CLIMB.get();
    private final float yaw;
    private Member leader;
    private Phase phase = Phase.WINDUP;
    private int tick;
    private int glideTicks;
    private double startX, startZ, takeoffY;
    private Vec3 velocity = Vec3.ZERO;
    private boolean finished;

    private Stampede(ServerLevel level, Aerbison leader, List<Aerbison> followers, StampedePlanner.Plan plan) {
        this.level = level;
        this.hx = plan.hx();
        this.hz = plan.hz();
        this.runLength = plan.runLength();
        this.yaw = (float) (Mth.atan2(-hx, hz) * Mth.RAD_TO_DEG);
        this.leader = add(leader, 0);
        for (Aerbison follower : followers) {
            if (members.size() < AACommonConfig.AERBISON_STAMPEDE_MAX_SIZE.get()) {
                add(follower, level.getRandom().nextInt(7));
            }
        }
        reslot();
    }

    /** Starts a stampede; the leader winds up and the rest rouse. */
    public static Stampede start(ServerLevel level, Aerbison leader, List<Aerbison> followers, StampedePlanner.Plan plan) {
        Stampede stampede = new Stampede(level, leader, followers, plan);
        leader.play(AerbisonTiming.EVENT_WINDUP);
        leader.playSound(AARegistries.AERBISON_SNORT.get(), 1.6F, 0.8F);
        StampedeManager.add(stampede);
        return stampede;
    }

    private Member add(Aerbison bison, int delay) {
        Member member = new Member(bison, delay);
        members.add(member);
        bison.stampede = this;
        bison.getNavigation().stop();
        bison.setState(Aerbison.State.WINDUP);
        if (bison.isLying()) {
            member.activeAt += 24;
        }
        return member;
    }

    /** Wedge slots: rows of two behind the leader, babies at the back. */
    private void reslot() {
        List<Member> ordered = new ArrayList<>(members);
        ordered.remove(leader);
        ordered.sort((a, b) -> Boolean.compare(a.bison.isBaby(), b.bison.isBaby()));
        leader.slot = 0;
        for (int i = 0; i < ordered.size(); i++) {
            ordered.get(i).slot = i + 1;
        }
    }

    boolean finished() {
        return finished;
    }

    int size() {
        return members.size();
    }

    boolean isWindingUp() {
        return phase == Phase.WINDUP;
    }

    void tick() {
        members.removeIf(m -> m.bison.isRemoved() || !m.bison.isAlive() || m.bison.stampede != this);
        if (members.isEmpty()) {
            end();
            return;
        }
        if (!members.contains(leader) || !leader.bison.isAlive()) {
            promote();
        }
        tick++;
        switch (phase) {
            case WINDUP -> windup();
            case RUN -> run();
            case GLIDE -> glide();
        }
        if (members.isEmpty()) {
            end();
        }
    }

    private void promote() {
        Member best = null;
        double bestAhead = -Double.MAX_VALUE;
        for (Member member : members) {
            double ahead = (member.bison.getX() - startX) * hx + (member.bison.getZ() - startZ) * hz;
            if (ahead > bestAhead) {
                bestAhead = ahead;
                best = member;
            }
        }
        leader = best;
        reslot();
    }

    private void face(Aerbison bison) {
        bison.setYRot(yaw);
        bison.yBodyRot = yaw;
        bison.yHeadRot = yaw;
    }

    private void windup() {
        for (Member member : members) {
            face(member.bison);
            member.bison.setDeltaMovement(0, member.bison.getDeltaMovement().y, 0);
            if (!member.roused && member != leader && tick >= member.activeAt) {
                member.roused = true;
                member.bison.play(AerbisonTiming.EVENT_ROUSE);
            }
        }
        if (tick == WINDUP - 4) {
            leader.bison.playSound(AARegistries.AERBISON_BELLOW.get(), 2.0F, 0.9F);
        }
        if (tick >= WINDUP) {
            phase = Phase.RUN;
            takeoffY = leader.bison.getY();
            startX = leader.bison.getX();
            startZ = leader.bison.getZ();
            for (Member member : members) {
                member.activeAt = Math.max(member.activeAt, 0);
                if (tick >= member.activeAt) {
                    begin(member);
                }
            }
        }
    }

    private void begin(Member member) {
        member.bison.setState(Aerbison.State.RUN);
        member.bison.setNoGravity(true);
        member.runTicks = 0;
    }

    private double distance() {
        return (leader.bison.getX() - startX) * hx + (leader.bison.getZ() - startZ) * hz;
    }

    private void run() {
        Aerbison lead = leader.bison;
        double s = distance();
        if (s >= runLength) {
            phase = Phase.GLIDE;
            glideTicks = 0;
            for (Member member : members) {
                if (member.bison.getState() == Aerbison.State.RUN) {
                    member.bison.setState(Aerbison.State.GLIDE);
                }
            }
            return;
        }
        double target = StampedeMath.desiredY(level, lead.getX(), lead.getZ(), hx, hz, lead.getY(), takeoffY, s, maxClimb);
        if (Double.isNaN(target)) {
            // the next chunk isn't loaded: come down here
            phase = Phase.GLIDE;
            glideTicks = 0;
            return;
        }
        double vy = Mth.clamp((target - lead.getY()) * 0.5, -0.4, 0.5);
        velocity = new Vec3(hx * StampedeMath.RUN_SPEED, vy, hz * StampedeMath.RUN_SPEED);
        if (tick % 5 == 0) {
            scanForJoiners();
        }
        drive(true);
    }

    private void glide() {
        double speed = StampedeMath.glideSpeed(glideTicks++);
        velocity = new Vec3(hx * speed, -StampedeMath.SINK, hz * speed);
        drive(false);
        for (Member member : new ArrayList<>(members)) {
            Aerbison bison = member.bison;
            if (member.runTicks > 3 && bison.onGround()) {
                land(member);
            }
        }
    }

    private void land(Member member) {
        Aerbison bison = member.bison;
        bison.setNoGravity(false);
        bison.setDeltaMovement(0, 0, 0);
        bison.setState(Aerbison.State.IDLE);
        bison.stampede = null;
        bison.stampedeCooldown = AACommonConfig.AERBISON_STAMPEDE_COOLDOWN.get() * 20;
        bison.land();
        members.remove(member);
    }

    /** Moves every active member: the leader on the plan, the rest toward their slot, then hits and tramples. */
    private void drive(boolean running) {
        Vec3 lead = leader.bison.position();
        double rx = -hz, rz = hx;
        for (Member member : new ArrayList<>(members)) {
            Aerbison bison = member.bison;
            if (running && bison.getState() == Aerbison.State.WINDUP) {
                if (tick >= member.activeAt) {
                    begin(member);
                } else {
                    continue;
                }
            }
            if (bison.getState() != Aerbison.State.RUN && bison.getState() != Aerbison.State.GLIDE) {
                continue;
            }
            member.runTicks++;
            face(bison);
            if (member == leader) {
                bison.setDeltaMovement(velocity);
            } else {
                int row = (member.slot + 1) / 2;
                double side = (member.slot % 2 == 1 ? -1 : 1) * Math.min(row * SLOT_SPACING_SIDE, MAX_LATERAL);
                double back = row * SLOT_SPACING_BACK;
                Vec3 slot = new Vec3(lead.x - hx * back + rx * side, lead.y, lead.z - hz * back + rz * side);
                Vec3 correction = slot.subtract(bison.position()).scale(CATCH_UP);
                if (correction.length() > MAX_CORRECTION) {
                    correction = correction.normalize().scale(MAX_CORRECTION);
                }
                bison.setDeltaMovement(velocity.add(correction));
            }
            if (running && member.runTicks > 10) {
                double along = (bison.getX() - bison.xo) * hx + (bison.getZ() - bison.zo) * hz;
                member.blocked = along < 0.18 ? member.blocked + 1 : 0;
                if (member.blocked >= 3) {
                    stagger(member);
                    continue;
                }
            }
            hitAhead(bison);
            if (AACommonConfig.AERBISON_TRAMPLE.get() && EventHooks.canEntityGrief(level, bison)) {
                trample(bison);
            }
            if (tick % 2 == 0) {
                dust(bison);
            }
        }
    }

    private void stagger(Member member) {
        Aerbison bison = member.bison;
        bison.setNoGravity(false);
        bison.stampede = null;
        bison.stampedeCooldown = AACommonConfig.AERBISON_STAMPEDE_COOLDOWN.get() * 20;
        bison.beginStagger();
        members.remove(member);
    }

    private void dust(Aerbison bison) {
        Vec3 behind = bison.position().add(-hx * 1.6, 0.3, -hz * 1.6);
        BlockPos below = bison.blockPosition().below();
        BlockState state = level.getBlockState(below);
        if (bison.onGround() && !state.isAir()) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), behind.x, behind.y, behind.z, 6, 0.6,
                    0.15, 0.6, 0.05);
        }
        level.sendParticles(ParticleTypes.CLOUD, behind.x, behind.y + 0.2, behind.z, 2, 0.5, 0.2, 0.5, 0.02);
    }

    private void hitAhead(Aerbison bison) {
        AABB box = bison.getBoundingBox().expandTowards(hx * 0.6, 0, hz * 0.6).inflate(0.2);
        double rx = -hz, rz = hx;
        for (LivingEntity other : level.getEntitiesOfClass(LivingEntity.class, box, e -> hittable(e, bison))) {
            hit.add(other.getUUID());
            other.hurt(level.damageSources().mobAttack(bison), AACommonConfig.AERBISON_KNOCKBACK_DAMAGE.get().floatValue());
            double side = Math.signum((other.getX() - bison.getX()) * rx + (other.getZ() - bison.getZ()) * rz);
            double horizontal = AACommonConfig.AERBISON_KNOCKBACK_HORIZONTAL.get();
            other.setDeltaMovement(hx * horizontal + rx * side * 0.5, AACommonConfig.AERBISON_KNOCKBACK_VERTICAL.get(),
                    hz * horizontal + rz * side * 0.5);
            other.hurtMarked = true;
            int slowFall = AACommonConfig.AERBISON_SLOW_FALL_TICKS.get();
            if (slowFall > 0) {
                other.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, slowFall, 0, false, false, true));
            }
        }
    }

    private boolean hittable(LivingEntity entity, Aerbison source) {
        if (entity == source || entity instanceof Aerbison || !entity.isAlive() || entity.isSpectator()
                || hit.contains(entity.getUUID()) || entity.isPassenger()) {
            return false;
        }
        return !(entity instanceof Player player && (player.isCreative() || player.isSpectator()));
    }

    private void trample(Aerbison bison) {
        AABB box = bison.getBoundingBox().expandTowards(hx * 0.8, 0, hz * 0.8).inflate(0.15, 0, 0.15).expandTowards(0, 0.5, 0);
        int broken = 0;
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ),
                BlockPos.containing(box.maxX, box.maxY, box.maxZ))) {
            BlockState state = level.getBlockState(pos);
            if (state.isAir() || !state.is(AATags.AERBISON_TRAMPLES)) {
                continue;
            }
            BlockPos below = pos.below();
            BlockState ground = level.getBlockState(below);
            if (level.destroyBlock(pos.immutable(), true, bison) && ground.getBlock() instanceof FarmBlock
                    && state.is(net.minecraft.tags.BlockTags.CROPS)) {
                FarmBlock.turnToDirt(bison, ground, level, below);
            }
            if (++broken >= 48) {
                break;
            }
        }
    }

    /** Idle bison near the line of the run, close ahead of the leader, join in. */
    private void scanForJoiners() {
        if (members.size() >= AACommonConfig.AERBISON_STAMPEDE_MAX_SIZE.get()) {
            return;
        }
        Aerbison lead = leader.bison;
        AABB box = lead.getBoundingBox().inflate(JOIN_AHEAD + 4, 6, JOIN_AHEAD + 4);
        for (Aerbison other : level.getEntitiesOfClass(Aerbison.class, box, b -> b.stampede == null && b.isAlive()
                && (b.getState() == Aerbison.State.IDLE || b.getState() == Aerbison.State.LYING))) {
            double dx = other.getX() - lead.getX(), dz = other.getZ() - lead.getZ();
            double along = dx * hx + dz * hz;
            double across = Math.abs(dx * -hz + dz * hx);
            if (across > JOIN_LATERAL || along > JOIN_AHEAD || along < -12) {
                continue;
            }
            Member joiner = add(other, tick + (other.isLying() ? 24 : level.getRandom().nextInt(6)));
            other.play(AerbisonTiming.EVENT_ROUSE);
            joiner.roused = true;
            reslot();
            if (members.size() >= AACommonConfig.AERBISON_STAMPEDE_MAX_SIZE.get()) {
                return;
            }
        }
    }

    private void end() {
        finished = true;
        for (Member member : members) {
            Aerbison bison = member.bison;
            bison.setNoGravity(false);
            bison.stampede = null;
        }
        members.clear();
    }
}
