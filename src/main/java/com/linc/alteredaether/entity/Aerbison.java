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

import com.aetherteam.aether.entity.passive.AetherAnimal;
import com.linc.alteredaether.AATags;
import com.linc.alteredaether.AlteredAether;
import com.linc.alteredaether.entity.aerbison.AerbisonGrazeGoal;
import com.linc.alteredaether.entity.aerbison.AerbisonNavigation;
import com.linc.alteredaether.entity.aerbison.AerbisonTiming;
import com.linc.alteredaether.entity.aerbison.GustStompGoal;
import com.linc.alteredaether.entity.aerbison.HerdDefense;
import com.linc.alteredaether.entity.aerbison.HornTossAttackGoal;
import com.linc.alteredaether.entity.aerbison.Stampede;
import com.linc.alteredaether.entity.aerbison.StampedeGoal;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import com.linc.alteredaether.registry.AARegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowParentGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.DifficultyInstance;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * The aerbison: a shaggy bison of the Aether's open plains, its mane a bank of cloud with the zephyr's square spirals
 * worked into it. Herds graze together. It is wind-touched: stamping the ground throws it up a ledge
 * (see {@link GustStompGoal}), its mane spreads like a canopy to sail down from any height, and the herd can stampede
 * (see entity/aerbison/Stampede). The animation state lives here, on both sides, and the model only reads it.
 */
public class Aerbison extends AetherAnimal {
    /** What the bison is doing, for the animation and for the herd's logic; synced. */
    public enum State {
        IDLE, WINDUP, RUN, GLIDE, STAGGER, DEFENDING;

        private static final State[] VALUES = values();

        static State of(byte id) {
            return id >= 0 && id < VALUES.length ? VALUES[id] : IDLE;
        }
    }

    private static final EntityDataAccessor<Byte> DATA_STATE =
            SynchedEntityData.defineId(Aerbison.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Boolean> DATA_SAILING =
            SynchedEntityData.defineId(Aerbison.class, EntityDataSerializers.BOOLEAN);

    /** Falling faster than this (blocks a tick) spreads the mane. */
    private static final double SAIL_START_SPEED = 0.3;
    /** A sailing bison sinks no faster than this. */
    private static final double SAIL_SPEED = 0.16;
    /** How far a bison will plan to drop, since it sails down safely. */
    private static final int MAX_FALL = 16;
    /** A rise, in blocks, that the path planner accepts: the gust stomp's reach. */
    public static final int STOMP_REACH = 5;

    public final AnimationState grazeAnim = new AnimationState();
    public final AnimationState headShakeAnim = new AnimationState();
    public final AnimationState windupAnim = new AnimationState();
    public final AnimationState rouseAnim = new AnimationState();
    public final AnimationState stompAnim = new AnimationState();
    public final AnimationState hornTossAnim = new AnimationState();
    public final AnimationState staggerAnim = new AnimationState();
    public final AnimationState refuseAnim = new AnimationState();

    private static final class Clip {
        final AnimationState state;
        final int length;
        int started = -1;

        Clip(AnimationState state, int length) {
            this.state = state;
            this.length = length;
        }
    }

    private final List<Clip> clips = new ArrayList<>();
    private final Clip graze = clip(grazeAnim, AerbisonTiming.GRAZE_TICKS);
    private final Clip headShake = clip(headShakeAnim, AerbisonTiming.HEAD_SHAKE_TICKS);
    private final Clip windup = clip(windupAnim, AerbisonTiming.WINDUP_TICKS);
    private final Clip rouse = clip(rouseAnim, AerbisonTiming.ROUSE_TICKS);
    private final Clip stomp = clip(stompAnim, AerbisonTiming.STOMP_TICKS);
    private final Clip hornToss = clip(hornTossAnim, AerbisonTiming.HORN_TOSS_TICKS);
    private final Clip stagger = clip(staggerAnim, AerbisonTiming.STAGGER_TICKS);
    private final Clip refuse = clip(refuseAnim, AerbisonTiming.REFUSE_TICKS);

    /** Smoothed pose weights, advanced every tick on both sides; the model blends by them. */
    public float walk, walkO, gallop, gallopO, charge, chargeO, sail, sailO, idle, idleO, thud, thudO;
    /**
     * Limb-swing speed (4 x blocks a tick) past which it gallops instead of walking, and below which it drops back.
     * A stroll is about 0.29, following or tempted about 0.43, panicking about 0.9.
     */
    private static final float GALLOP_STRIDE = 0.6F, WALK_STRIDE = 0.48F;
    /** Limb-swing speed below which it's standing still. */
    private static final float STILL_STRIDE = 0.05F;
    /** Whether it's moving fast enough to gallop, with some hysteresis so it doesn't flicker between the two. */
    private boolean hurrying;

    /** Ticks until the gust stomp can be used again. */
    public int stompCooldown;
    /** The stampede this bison is part of, if any; server only. */
    @Nullable
    public Stampede stampede;
    /** Ticks before this bison can start a random stampede again. */
    public int stampedeCooldown;
    /** Ticks before the herd will answer an attack with another stampede. */
    public int defendLockout;
    /** Ticks left of being dazed. */
    public int staggerTicks;

    /** True while the path planner is allowed to count a rise of up to {@link #STOMP_REACH} blocks as passable. */
    public boolean planningClimb;

    public Aerbison(EntityType<? extends Aerbison> type, Level level) {
        super(type, level);
        setPathfindingMalus(net.minecraft.world.level.pathfinder.PathType.DANGER_FIRE, 16.0F);
    }

    private Clip clip(AnimationState state, int length) {
        Clip clip = new Clip(state, length);
        clips.add(clip);
        return clip;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Animal.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 40)
                .add(Attributes.MOVEMENT_SPEED, 0.2)
                .add(Attributes.ATTACK_DAMAGE, 7)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.6)
                .add(Attributes.FOLLOW_RANGE, 24)
                .add(Attributes.STEP_HEIGHT, 1.0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new StampedeGoal(this));
        goalSelector.addGoal(2, new GustStompGoal(this));
        goalSelector.addGoal(3, new HornTossAttackGoal(this));
        goalSelector.addGoal(3, new PanicGoal(this, 1.6) {
            @Override
            public boolean canUse() {
                return isBaby() && super.canUse();
            }
        });
        goalSelector.addGoal(4, new BreedGoal(this, 1.0));
        goalSelector.addGoal(5, new TemptGoal(this, 1.1, Ingredient.of(AATags.AERBISON_FOOD), false));
        goalSelector.addGoal(6, new FollowParentGoal(this, 1.1));
        goalSelector.addGoal(7, new AerbisonGrazeGoal(this));
        goalSelector.addGoal(9, new WaterAvoidingRandomStrollGoal(this, 0.9));
        goalSelector.addGoal(10, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(11, new RandomLookAroundGoal(this));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_STATE, (byte) 0);
        builder.define(DATA_SAILING, false);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        return new AerbisonNavigation(this, level);
    }

    @Override
    public float maxUpStep() {
        return planningClimb ? STOMP_REACH : super.maxUpStep();
    }

    @Override
    public int getMaxFallDistance() {
        return MAX_FALL;
    }

    public State getState() {
        return State.of(entityData.get(DATA_STATE));
    }

    public void setState(State state) {
        entityData.set(DATA_STATE, (byte) state.ordinal());
    }

    public boolean isStaggering() {
        return getState() == State.STAGGER;
    }

    public boolean isSailing() {
        return entityData.get(DATA_SAILING);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (key.equals(DATA_STATE) && level().isClientSide) {
            if (getState() == State.STAGGER) {
                start(stagger);
            } else {
                stagger.state.stop();
                stagger.started = -1;
            }
        }
    }

    private void start(Clip clip) {
        clip.state.start(tickCount);
        clip.started = tickCount;
    }

    /** Tells every client to play one of the one-shots, and plays it here as well. */
    public void play(byte event) {
        level().broadcastEntityEvent(this, event);
    }

    @Override
    public void handleEntityEvent(byte id) {
        switch (id) {
            case AerbisonTiming.EVENT_GRAZE -> start(graze);
            case AerbisonTiming.EVENT_HEAD_SHAKE -> start(headShake);
            case AerbisonTiming.EVENT_WINDUP -> start(windup);
            case AerbisonTiming.EVENT_ROUSE -> start(rouse);
            case AerbisonTiming.EVENT_STOMP -> start(stomp);
            case AerbisonTiming.EVENT_HORN_TOSS -> start(hornToss);
            case AerbisonTiming.EVENT_REFUSE -> start(refuse);
            case AerbisonTiming.EVENT_LAND -> {
                thud = 1;
                thudO = 1;
            }
            default -> super.handleEntityEvent(id);
        }
    }

    @Override
    public void tick() {
        super.tick();
        for (Clip clip : clips) {
            if (clip.started >= 0 && tickCount - clip.started >= clip.length) {
                clip.state.stop();
                clip.started = -1;
            }
        }
        State state = getState();
        walkO = walk;
        gallopO = gallop;
        chargeO = charge;
        sailO = sail;
        idleO = idle;
        thudO = thud;
        boolean moving = getDeltaMovement().horizontalDistanceSqr() > 0.01;
        float stride = walkAnimation.speed();
        if (stride > GALLOP_STRIDE) {
            hurrying = true;
        } else if (stride < WALK_STRIDE) {
            hurrying = false;
        }
        boolean striding = stride >= STILL_STRIDE;
        // the walk and gallop play at their own pace, so the gait changes with speed rather than the cycle speeding up
        walk = approach(walk, striding ? 1 : 0, 0.15F);
        gallop = approach(gallop, state == State.RUN || state == State.DEFENDING && moving || hurrying && striding ? 1 : 0, 0.1F);
        charge = approach(charge, state == State.RUN || state == State.DEFENDING ? 1 : 0, 0.1F);
        sail = approach(sail, isSailing() || state == State.GLIDE ? 1 : 0, 0.15F);
        // the idle loop is for standing about with nothing else going on
        boolean standing = state == State.IDLE && !isSailing() && !striding
                && clips.stream().noneMatch(clip -> clip.started >= 0);
        idle = approach(idle, standing ? 1 : 0, 0.05F);
        thud = Math.max(0, thud - 0.34F);
        if (stompCooldown > 0) {
            stompCooldown--;
        }
        if (!level().isClientSide) {
            serverTick(state);
        }
        if (level().isClientSide && isSailing() && tickCount % 3 == 0) {
            level().addParticle(ParticleTypes.CLOUD, getX() + (random.nextDouble() - 0.5) * 1.6, getY() + 1.7,
                    getZ() + (random.nextDouble() - 0.5) * 1.6, 0, -0.02, 0);
        }
    }

    private void serverTick(State state) {
        if (stampedeCooldown > 0) {
            stampedeCooldown--;
        }
        if (defendLockout > 0) {
            defendLockout--;
        }
        if (stampede == null) {
            // anything a stampede left behind, such as after a reload mid-run
            if (isNoGravity()) {
                setNoGravity(false);
            }
            if (state == State.RUN || state == State.GLIDE || state == State.WINDUP) {
                setState(State.IDLE);
            }
        }
        if (state == State.STAGGER) {
            getNavigation().stop();
            setDeltaMovement(getDeltaMovement().multiply(0, 1, 0));
            if (--staggerTicks <= 0) {
                setState(State.IDLE);
                play(AerbisonTiming.EVENT_HEAD_SHAKE);
            }
        }
    }

    /** Stopped dead by a wall: reels for a couple of seconds. */
    public void beginStagger() {
        setState(State.STAGGER);
        staggerTicks = AerbisonTiming.STAGGER_TICKS;
        playSound(AARegistries.AERBISON_STUN.get(), 1.5F, 0.9F);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hurt = super.hurt(source, amount);
        if (hurt && !level().isClientSide && source.getEntity() instanceof LivingEntity attacker && attacker != this) {
            HerdDefense.onHurt(this, attacker);
        }
        return hurt;
    }

    private static float approach(float value, float target, float step) {
        return value < target ? Math.min(target, value + step) : Math.max(target, value - step);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!level().isClientSide) {
            serverSailing();
        }
    }

    /** The mane spreads when the bison falls far enough, and holds its sinking to a drift. */
    private void serverSailing() {
        State state = getState();
        boolean controlled = state == State.RUN || state == State.GLIDE;
        boolean wasSailing = isSailing();
        boolean sailing = wasSailing;
        if (onGround() || isInWaterOrBubble() || isInLava() || controlled) {
            sailing = false;
        } else if (!wasSailing && getDeltaMovement().y < -SAIL_START_SPEED && fallDistance > 1.5F) {
            sailing = true;
        }
        if (sailing != wasSailing) {
            entityData.set(DATA_SAILING, sailing);
            if (!sailing && onGround() && wasSailing) {
                land();
            }
        }
        if (sailing) {
            var motion = getDeltaMovement();
            if (motion.y < -SAIL_SPEED) {
                setDeltaMovement(motion.x * 0.98, -SAIL_SPEED, motion.z * 0.98);
            }
            resetFallDistance();
        }
    }

    /** Lands with a thud and a ring of dust. */
    public void land() {
        play(AerbisonTiming.EVENT_LAND);
        playSound(AARegistries.AERBISON_LAND.get(), 1.0F, 0.9F + random.nextFloat() * 0.2F);
        if (level() instanceof ServerLevel server) {
            BlockState below = level().getBlockState(blockPosition().below());
            if (!below.isAir()) {
                server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, below), getX(), getY() + 0.1, getZ(),
                        24, 0.9, 0.05, 0.9, 0.1);
            }
            server.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 0.2, getZ(), 10, 0.9, 0.05, 0.9, 0.04);
        }
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        // a sailing bison has landed softly, and a stamping one has been thrown by its own gust
        return false;
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (getState() == State.IDLE && !isBaby() && random.nextInt(20 * 75) == 0) {
            play(AerbisonTiming.EVENT_HEAD_SHAKE);
        }
    }

    @Override
    public boolean isFood(ItemStack stack) {
        return stack.is(AATags.AERBISON_FOOD);
    }

    @Nullable
    @Override
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
        return AARegistries.AERBISON.get().create(level);
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason,
                                        @Nullable SpawnGroupData data) {
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, reason, data);
        // a fifth of a spawned herd are calves
        if ((reason == MobSpawnType.NATURAL || reason == MobSpawnType.CHUNK_GENERATION) && getAge() == 0
                && random.nextFloat() < 0.2F) {
            setAge(-24000);
        }
        return result;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return AARegistries.AERBISON_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return AARegistries.AERBISON_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return AARegistries.AERBISON_DEATH.get();
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(AARegistries.AERBISON_STEP.get(), 0.25F, 1.0F);
    }

    /** Spawn rule: Aether grass, soil or quicksoil underfoot, in daylight. */
    public static boolean checkSpawnRules(EntityType<Aerbison> type, ServerLevelAccessor level, MobSpawnType reason,
                                          BlockPos pos, net.minecraft.util.RandomSource random) {
        return level.getBlockState(pos.below()).is(AATags.AERBISON_SPAWNABLE_ON) && level.getRawBrightness(pos, 0) > 8;
    }

    @EventBusSubscriber(modid = AlteredAether.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
    public static final class AttributeRegistration {
        @SubscribeEvent
        public static void register(EntityAttributeCreationEvent event) {
            event.put(AARegistries.AERBISON.get(), Aerbison.createAttributes().build());
        }

        private AttributeRegistration() {
        }
    }
}
