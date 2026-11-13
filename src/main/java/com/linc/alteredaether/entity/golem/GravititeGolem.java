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
package com.linc.alteredaether.entity.golem;

import com.linc.alteredaether.AlteredAether;
import com.linc.alteredaether.config.AACommonConfig;
import com.linc.alteredaether.registry.AARegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.JumpControl;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/**
 * The gravitite golem: a thin construct with a big head that sits in meditation and sorts the storage linked to it by
 * telekinesis (see GolemSorter). It never wanders. It sits where it was built, its home, until a player sends it
 * somewhere else with its pebble, and if anything moves it off its home it gets up and goes back. It climbs by
 * levitating rather than jumping, and floats down slowly. Struck, it pushes everything around it away.
 */
public class GravititeGolem extends PathfinderMob {
    /** Gravitite's colours, as dust particles take them. */
    public static final Vector3f GRAVITITE = new Vector3f(0xE0 / 255F, 0x72 / 255F, 0xD7 / 255F);
    public static final Vector3f GRAVITITE_DARK = new Vector3f(0xBD / 255F, 0x42 / 255F, 0xB2 / 255F);

    public static final int SEATED = 0, CONCENTRATING = 1, MOVING = 2;

    private static final EntityDataAccessor<Byte> DATA_STATE = SynchedEntityData.defineId(GravititeGolem.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Boolean> DATA_FLOATING = SynchedEntityData.defineId(GravititeGolem.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Byte> DATA_MODE = SynchedEntityData.defineId(GravititeGolem.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Boolean> DATA_PAUSED = SynchedEntityData.defineId(GravititeGolem.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_RISING = SynchedEntityData.defineId(GravititeGolem.class, EntityDataSerializers.BOOLEAN);

    /** How fast it rises when it levitates up a step or across a gap, and the fastest it falls. */
    private static final double RISE_SPEED = 0.08, FLOAT_SPEED = 0.15, MAX_FALL = 0.12;
    /** Ticks a move may take before the golem gives up. */
    private static final int MOVE_TIMEOUT = 600;
    /** How far below its home, with nothing under it, a golem floats straight back up. */
    private static final int VOID_DROP = 16;
    /** Degrees a tick it turns, seated, to face the storage it lifts from. */
    private static final float TURN_SPEED = 9.0F;

    private static final EntityDimensions SEATED_SIZE = EntityDimensions.scalable(0.9F, 1.5F).withEyeHeight(1.2F);

    @Nullable
    private BlockPos home;
    /** Where a player sent it, and who, until it gets there or gives up. */
    @Nullable
    private BlockPos order;
    @Nullable
    private UUID orderedBy;
    /** Server: the storage it is turning to face, until it faces it. */
    @Nullable
    private BlockPos facing;
    private long lastRepel = -100_000;
    /** Ticks without ground under it, to tell a stumble from being knocked off its seat. */
    private int airborne;

    // animation, both sides
    public final AnimationState sitDownAnim = new AnimationState();
    public final AnimationState standUpAnim = new AnimationState();
    public final AnimationState repelAnim = new AnimationState();
    public final AnimationState shakeHeadAnim = new AnimationState();
    public final AnimationState linkPulseAnim = new AnimationState();
    private final List<Clip> clips = new ArrayList<>();
    private final Clip sitDown = clip(sitDownAnim, GolemTiming.SIT_DOWN_TICKS);
    private final Clip standUp = clip(standUpAnim, GolemTiming.STAND_UP_TICKS);
    private final Clip repelClip = clip(repelAnim, GolemTiming.REPEL_TICKS);
    private final Clip shakeHead = clip(shakeHeadAnim, GolemTiming.SHAKE_HEAD_TICKS);
    private final Clip linkPulse = clip(linkPulseAnim, GolemTiming.LINK_PULSE_TICKS);
    /** Smoothed weights of the held animations, now and last tick, for the model. */
    public float meditate = 1, meditateO = 1, concentrate, concentrateO, floating, floatingO;
    /** The tick its stand-up ended, so the floating cycle starts where the stand-up leaves it. */
    public int floatSince;
    /** Server: ticks left of its sit-down, before it starts work. */
    private int settling;

    /** Client only: the space this golem's flying items cover, so it isn't culled while they're on screen. */
    @Nullable
    public AABB flightBounds;

    private static final class Clip {
        final AnimationState state;
        final int length;
        int started;

        Clip(AnimationState state, int length) {
            this.state = state;
            this.length = length;
        }
    }

    private Clip clip(AnimationState state, int length) {
        Clip clip = new Clip(state, length);
        clips.add(clip);
        return clip;
    }

    public GravititeGolem(EntityType<? extends GravititeGolem> type, Level level) {
        super(type, level);
        moveControl = new GolemMoveControl(this);
        setPose(Pose.SITTING);
        refreshDimensions();
        jumpControl = new JumpControl(this) {
            @Override
            public void tick() {
                // it levitates instead (see GolemMoveControl)
            }
        };
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 12)
                .add(Attributes.MOVEMENT_SPEED, 0.2)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.FOLLOW_RANGE, 64)
                .add(Attributes.STEP_HEIGHT, 0.6);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_STATE, (byte) SEATED);
        builder.define(DATA_FLOATING, false);
        builder.define(DATA_MODE, (byte) 0);
        builder.define(DATA_PAUSED, false);
        builder.define(DATA_RISING, false);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        GroundPathNavigation navigation = new GroundPathNavigation(this, level);
        navigation.setCanFloat(true);
        return navigation;
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new MoveGoal(this));
    }

    // ---------------------------------------------------------------------------------------------------------------
    // State

    public int state() {
        return entityData.get(DATA_STATE);
    }

    public void setState(int state) {
        int old = state();
        if (old == state) {
            return;
        }
        entityData.set(DATA_STATE, (byte) state);
        if (old == MOVING) {
            settling = GolemTiming.SIT_DOWN_TICKS;
            event(GolemTiming.EVENT_SIT_DOWN);
        } else if (state == MOVING) {
            event(GolemTiming.EVENT_STAND_UP);
        }
    }

    public boolean isSeated() {
        return state() != MOVING;
    }

    public boolean isFloating() {
        return entityData.get(DATA_FLOATING);
    }

    void setFloating(boolean floating) {
        entityData.set(DATA_FLOATING, floating);
        setNoGravity(floating);
    }

    public GolemMode mode() {
        return GolemMode.byId(entityData.get(DATA_MODE));
    }

    public void setModeShown(GolemMode mode) {
        entityData.set(DATA_MODE, (byte) mode.ordinal());
    }

    /** Levitating up a step: gravity is off until it is level with the step. */
    public boolean isRising() {
        return entityData.get(DATA_RISING);
    }

    void setRising(boolean rising) {
        if (rising != isRising()) {
            entityData.set(DATA_RISING, rising);
        }
    }

    public boolean isPaused() {
        return entityData.get(DATA_PAUSED);
    }

    @Nullable
    public BlockPos home() {
        return home;
    }

    /** Whether it sits on its home (or as near it as can be sat), ready to work. */
    public boolean isWorking() {
        return isSeated() && settling == 0 && home != null && !isPaused() && onGround() && closeTo(sitSpot(), 1.0);
    }

    private boolean closeTo(BlockPos spot, double reach) {
        double dx = getX() - (spot.getX() + 0.5), dz = getZ() - (spot.getZ() + 0.5);
        return dx * dx + dz * dz <= reach * reach && Math.abs(getY() - spot.getY()) <= 1.0;
    }

    /** Where it sits: its home, or the nearest place it can sit if something now fills its home. */
    BlockPos sitSpot() {
        if (home == null) {
            return blockPosition();
        }
        if (canStand(home)) {
            return home;
        }
        BlockPos best = home;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(home.offset(-3, -3, -3), home.offset(3, 3, 3))) {
            double dist = pos.distSqr(home);
            if (dist < bestDist && canStand(pos)) {
                best = pos.immutable();
                bestDist = dist;
            }
        }
        return best;
    }

    /** Whether there's ground under {@code pos} and room for a standing golem on it. */
    public boolean canStand(BlockPos pos) {
        Level level = level();
        if (!level.isLoaded(pos) || level.getBlockState(pos.below()).getCollisionShape(level, pos.below()).isEmpty()) {
            return false;
        }
        AABB box = getType().getDimensions().makeBoundingBox(Vec3.atBottomCenterOf(pos));
        return level.noCollision(this, box);
    }

    /** Sets where it sits from now on. */
    public void setHome(BlockPos pos) {
        home = pos.immutable();
        if (level() instanceof ServerLevel server) {
            GolemNetworks.get(server).join(getUUID(), home, getName().getString());
        }
    }

    /** Sends it to {@code pos}, which becomes its home when it arrives; {@code by} is told if it can't. */
    public void sendTo(BlockPos pos, @Nullable Player by) {
        order = pos.immutable();
        orderedBy = by == null ? null : by.getUUID();
        GolemSorter.cancelFlights(this);
    }

    @Nullable
    public BlockPos order() {
        return order;
    }

    /** Plays a one-shot animation on every client watching (and here). */
    public void event(byte event) {
        level().broadcastEntityEvent(this, event);
    }

    public void shakeHead() {
        event(GolemTiming.EVENT_SHAKE_HEAD);
        playSound(AARegistries.GOLEM_SHAKE.get(), 0.6F, 0.9F + random.nextFloat() * 0.2F);
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Ticking

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && level() instanceof ServerLevel server) {
            serverTick(server);
        }
        animationTick();
    }

    private void serverTick(ServerLevel server) {
        if (home == null) {
            setHome(blockPosition());
        }
        if (tickCount % 20 == 0) {
            GolemNetworks.Net net = GolemNetworks.get(server).join(getUUID(), home, getName().getString());
            setModeShown(net.mode());
        }
        if (tickCount % 10 == 0) {
            BlockPos pos = blockPosition();
            boolean powered = server.hasNeighborSignal(pos) || server.hasNeighborSignal(pos.below());
            if (powered != isPaused()) {
                entityData.set(DATA_PAUSED, powered);
                if (powered) {
                    GolemSorter.cancelFlights(this);
                }
            }
        }
        airborne = onGround() || isFloating() ? 0 : airborne + 1;
        if (settling > 0) {
            settling--;
        }
        turnToFace();
    }

    /** Has it turn, where it sits, to face the storage at {@code pos}: the one it is lifting an item out of. */
    public void face(BlockPos pos) {
        facing = pos.immutable();
    }

    private void turnToFace() {
        if (facing == null) {
            return;
        }
        double dx = facing.getX() + 0.5 - getX(), dz = facing.getZ() + 0.5 - getZ();
        if (!isSeated() || dx * dx + dz * dz < 0.01) {
            facing = null;
            return;
        }
        float wanted = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
        float left = Mth.wrapDegrees(wanted - getYRot());
        float yaw = getYRot() + Mth.clamp(left, -TURN_SPEED, TURN_SPEED);
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
        if (Math.abs(left) <= TURN_SPEED) {
            facing = null;
        }
    }

    @Override
    public void aiStep() {
        super.aiStep();
        // gravitite: it floats down rather than falls
        Vec3 motion = getDeltaMovement();
        if (!onGround() && !isNoGravity() && motion.y < -MAX_FALL) {
            setDeltaMovement(motion.x, -MAX_FALL, motion.z);
        }
        resetFallDistance();
        if (level().isClientSide && (isFloating() || isRising()) && random.nextInt(3) == 0) {
            level().addParticle(new DustParticleOptions(GRAVITITE, 0.8F), getRandomX(0.4), getY() + 0.05,
                    getRandomZ(0.4), 0, -0.02, 0);
        }
    }

    private void animationTick() {
        for (Clip clip : clips) {
            if (clip.state.isStarted() && tickCount - clip.started > clip.length) {
                clip.state.stop();
                if (clip == standUp) {
                    floatSince = tickCount;
                }
            }
        }
        meditateO = meditate;
        concentrateO = concentrate;
        floatingO = floating;
        int state = state();
        // up and about it always floats; the stand-up and sit-down carry it between that and sitting
        meditate = approach(meditate, state == SEATED ? 1 : 0, 0.08F);
        concentrate = approach(concentrate, state == CONCENTRATING ? 1 : 0, 0.08F);
        floating = approach(floating, state == MOVING ? 1 : 0, 0.15F);
    }

    private static float approach(float value, float target, float step) {
        return value < target ? Math.min(target, value + step) : Math.max(target, value - step);
    }

    @Override
    public void handleEntityEvent(byte id) {
        switch (id) {
            case GolemTiming.EVENT_SIT_DOWN -> start(sitDown);
            case GolemTiming.EVENT_STAND_UP -> start(standUp);
            case GolemTiming.EVENT_REPEL -> start(repelClip);
            case GolemTiming.EVENT_SHAKE_HEAD -> start(shakeHead);
            case GolemTiming.EVENT_LINK_PULSE -> start(linkPulse);
            default -> super.handleEntityEvent(id);
        }
    }

    private void start(Clip clip) {
        // one transition at a time: each carries the whole pose
        if (clip == sitDown) {
            standUp.state.stop();
        } else if (clip == standUp) {
            sitDown.state.stop();
        }
        clip.state.start(tickCount);
        clip.started = tickCount;
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_STATE.equals(key)) {
            setPose(isSeated() ? Pose.SITTING : Pose.STANDING);
            refreshDimensions();
        }
    }

    @Override
    protected EntityDimensions getDefaultDimensions(Pose pose) {
        return pose == Pose.SITTING ? SEATED_SIZE : super.getDefaultDimensions(pose);
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        AABB box = super.getBoundingBoxForCulling();
        return flightBounds == null ? box : box.minmax(flightBounds);
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Staying put

    @Override
    public boolean isPushable() {
        return !isSeated() && super.isPushable();
    }

    @Override
    public void push(Entity entity) {
        if (!isSeated()) {
            super.push(entity);
        }
    }

    @Override
    public void push(double x, double y, double z) {
        if (!isSeated()) {
            super.push(x, y, z);
        }
    }

    @Override
    public boolean isPushedByFluid() {
        return !isSeated();
    }

    @Override
    public void move(MoverType type, Vec3 movement) {
        if (isSeated() && (type == MoverType.PISTON || type == MoverType.SHULKER || type == MoverType.SHULKER_BOX)) {
            return;
        }
        super.move(type, movement);
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    @Override
    public boolean startRiding(Entity vehicle, boolean force) {
        return false;
    }

    @Override
    public boolean canChangeDimensions(Level from, Level to) {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean requiresCustomPersistence() {
        return true;
    }

    @Override
    public int getMaxHeadYRot() {
        return isSeated() ? 10 : 40;
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Toughness

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypes.IN_WALL) || source.is(DamageTypes.CACTUS)
                || source.is(DamageTypes.FLY_INTO_WALL) || source.is(DamageTypes.SWEET_BERRY_BUSH)
                || super.isInvulnerableTo(source);
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hurt = super.hurt(source, amount);
        if (hurt && source.getEntity() != null && level() instanceof ServerLevel server) {
            long now = server.getGameTime();
            if (now - lastRepel >= AACommonConfig.GOLEM_REPEL_COOLDOWN.get()) {
                lastRepel = now;
                repel(server);
            }
        }
        return hurt;
    }

    /** Pushes every living thing near it away, mobs hard and players gently, with a flare of gravitite. */
    void repel(ServerLevel server) {
        double radius = AACommonConfig.GOLEM_REPEL_RADIUS.get();
        if (radius <= 0) {
            return;
        }
        for (LivingEntity other : server.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(radius),
                e -> e != this && e.isAlive() && !(e instanceof GravititeGolem) && !e.isSpectator())) {
            if (other.distanceToSqr(this) > radius * radius) {
                continue;
            }
            double strength = other instanceof Player ? AACommonConfig.GOLEM_REPEL_PLAYERS.get()
                    : AACommonConfig.GOLEM_REPEL_MOBS.get();
            double dx = getX() - other.getX(), dz = getZ() - other.getZ();
            if (dx * dx + dz * dz < 1.0E-4) {
                double angle = random.nextDouble() * Math.PI * 2;
                dx = Math.cos(angle);
                dz = Math.sin(angle);
            }
            other.knockback(strength, dx, dz);
            other.hurtMarked = true;
        }
        for (int i = 0; i < 24; i++) {
            double angle = i * Math.PI * 2 / 24;
            server.sendParticles(new DustParticleOptions(GRAVITITE, 1.2F), getX() + Math.cos(angle) * 0.6,
                    getY() + 0.6, getZ() + Math.sin(angle) * 0.6, 1, Math.cos(angle), 0.05, Math.sin(angle), 0.3);
        }
        playSound(AARegistries.GOLEM_REPEL.get(), 1.0F, 1.0F);
        event(GolemTiming.EVENT_REPEL);
    }

    /** An ambrosium shard heals it. */
    boolean heal(Player player, net.minecraft.world.item.ItemStack stack) {
        if (getHealth() >= getMaxHealth()) {
            return false;
        }
        heal(AACommonConfig.GOLEM_HEAL.get());
        stack.consume(1, player);
        if (level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.HEART, getX(), getY() + getBbHeight(), getZ(), 3, 0.3, 0.2, 0.3, 0);
        }
        playSound(SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0F, 1.2F);
        return true;
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide && level() instanceof ServerLevel server
                && (reason == RemovalReason.KILLED || reason == RemovalReason.DISCARDED)) {
            GolemSorter.cancelFlights(this);
            GolemNetworks.get(server).leave(getUUID());
        }
        super.remove(reason);
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Interaction

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        return GolemInteractions.interact(this, player, hand);
    }

    @Override
    public void setCustomName(@Nullable Component name) {
        super.setCustomName(name);
        if (home != null && level() instanceof ServerLevel server) {
            GolemNetworks.get(server).join(getUUID(), home, getName().getString());
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Sounds

    @Nullable
    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.AMETHYST_BLOCK_HIT;
    }

    @Nullable
    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.AMETHYST_CLUSTER_BREAK;
    }

    @Override
    protected void playStepSound(BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        playSound(SoundEvents.STONE_STEP, 0.3F, 1.4F);
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Saving

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType type,
                                        @Nullable SpawnGroupData data) {
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, type, data);
        setHome(blockPosition());
        return result;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (home != null) {
            tag.put("home", NbtUtils.writeBlockPos(home));
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        NbtUtils.readBlockPos(tag, "home").ifPresent(pos -> home = pos);
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Movement

    /**
     * Steering like vanilla's, without jumping: a step up is climbed by turning gravity off and rising until level
     * with it.
     */
    static final class GolemMoveControl extends MoveControl {
        private final GravititeGolem golem;

        GolemMoveControl(GravititeGolem golem) {
            super(golem);
            this.golem = golem;
        }

        @Override
        public void tick() {
            if (golem.isFloating()) {
                // the float steers itself; a climb it was part-way through is over
                golem.setRising(false);
                operation = Operation.WAIT;
                mob.setZza(0);
                return;
            }
            if (operation != Operation.MOVE_TO) {
                mob.setZza(0);
                stopRising();
                return;
            }
            operation = Operation.WAIT;
            double dx = wantedX - mob.getX(), dy = wantedY - mob.getY(), dz = wantedZ - mob.getZ();
            double flat = dx * dx + dz * dz;
            if (flat + dy * dy < 2.5E-7) {
                mob.setZza(0);
                return;
            }
            float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
            mob.setYRot(rotlerp(mob.getYRot(), yaw, 20.0F));
            float speed = (float) (speedModifier * mob.getAttributeValue(Attributes.MOVEMENT_SPEED));
            boolean climb = dy > 0.55 || mob.horizontalCollision && dy > -0.5 && flat < 2.25;
            if (climb) {
                golem.setRising(true);
                mob.setNoGravity(true);
                Vec3 motion = mob.getDeltaMovement();
                mob.setDeltaMovement(motion.x * 0.5, RISE_SPEED, motion.z * 0.5);
                mob.setSpeed(speed * 0.4F);
            } else {
                if (dy <= 0.05 || mob.onGround()) {
                    stopRising();
                }
                mob.setSpeed(speed);
            }
        }

        private void stopRising() {
            if (golem.isRising()) {
                golem.setRising(false);
                if (!golem.isFloating()) {
                    mob.setNoGravity(false);
                }
            }
        }
    }

    /**
     * Goes where it was sent, or back home if something moved it: walking (and levitating up steps) where there's a
     * path, floating straight across where there isn't, then sitting down. A golem that can't get somewhere gives up
     * after half a minute, shakes its head and goes back home.
     */
    static final class MoveGoal extends Goal {
        private final GravititeGolem golem;
        private BlockPos target = BlockPos.ZERO;
        private int ticks, standing, stuck, retry;
        private boolean floating, done;
        /** The height a float crosses at: a block above the higher of where it set off and where it's going. */
        private double cruise;
        /** Ticks to wait before trying to get home again after failing to. */
        private int rest;

        MoveGoal(GravititeGolem golem) {
            this.golem = golem;
            setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP, Flag.LOOK));
        }

        private boolean displaced() {
            if (golem.home == null || golem.isPaused() && golem.order == null) {
                return false;
            }
            if (golem.order != null) {
                return true;
            }
            BlockPos spot = golem.sitSpot();
            return !golem.closeTo(spot, 1.0) || golem.airborne > 10;
        }

        @Override
        public boolean canUse() {
            if (rest > 0) {
                rest--;
                return golem.order != null;
            }
            return displaced();
        }

        @Override
        public boolean canContinueToUse() {
            return golem.isAlive() && !done;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void start() {
            ticks = 0;
            standing = golem.isSeated() ? GolemTiming.STAND_UP_TICKS : 0;
            stuck = 0;
            retry = 0;
            floating = false;
            done = false;
            golem.setState(MOVING);
            target = currentTarget();
            log("start", "to " + target.toShortString());
        }

        private void log(String what, String detail) {
            AlteredAether.LOGGER.debug("Golem move {} {}: {} at {} (order {}, home {})", golem.getUUID().toString().substring(0, 8),
                    what, detail, golem.position(), golem.order, golem.home);
        }

        private BlockPos currentTarget() {
            return golem.order != null ? golem.order : golem.sitSpot();
        }

        @Override
        public void stop() {
            golem.getNavigation().stop();
            endFloat();
            golem.setRising(false);
            golem.setNoGravity(false);
        }

        @Override
        public void tick() {
            if (standing > 0) {
                standing--;
                if (standing == 0) {
                    route();
                }
                return;
            }
            ticks++;
            BlockPos now = currentTarget();
            if (!now.equals(target)) {
                target = now;
                ticks = 0;
                route();
            }
            if (arrived()) {
                arrive();
                return;
            }
            if (ticks > MOVE_TIMEOUT || tooFar()) {
                log("give up", ticks > MOVE_TIMEOUT ? "took too long" : "too far");
                fail();
                return;
            }
            if (floating) {
                steerFloat();
            } else if (golem.getNavigation().isDone()) {
                if (++retry > 3) {
                    startFloat();
                } else {
                    route();
                }
            }
            // a golem fallen far below home with nothing under it floats straight back up
            if (!floating && golem.home != null && golem.getY() < golem.home.getY() - VOID_DROP && noGroundBelow()) {
                startFloat();
            }
        }

        private boolean tooFar() {
            return golem.distanceToSqr(Vec3.atBottomCenterOf(target)) > Math.pow(AACommonConfig.GOLEM_MAX_MOVE.get() + 8, 2)
                    && golem.order != null;
        }

        private boolean noGroundBelow() {
            BlockPos.MutableBlockPos pos = golem.blockPosition().mutable();
            for (int i = 0; i < 64 && pos.getY() > golem.level().getMinBuildHeight(); i++) {
                pos.move(0, -1, 0);
                if (!golem.level().getBlockState(pos).getCollisionShape(golem.level(), pos).isEmpty()) {
                    return false;
                }
            }
            return true;
        }

        private void route() {
            Path path = golem.getNavigation().createPath(target, 0);
            log("route", path == null ? "no path" : path.canReach() ? "walk " + path.getNodeCount() + " nodes" : "path falls short");
            if (path != null && path.canReach()) {
                endFloat();
                golem.getNavigation().moveTo(path, 1.0);
            } else {
                startFloat();
            }
        }

        private void startFloat() {
            if (!floating) {
                floating = true;
                stuck = 0;
                golem.getNavigation().stop();
                golem.setFloating(true);
                cruise = Math.max(target.getY(), golem.getY()) + 1.0;
            }
        }

        private void endFloat() {
            if (floating) {
                floating = false;
                golem.setFloating(false);
            }
        }

        /** Up to clear height, across, then down onto the spot. */
        private void steerFloat() {
            Vec3 goal = Vec3.atBottomCenterOf(target);
            double dx = goal.x - golem.getX(), dz = goal.z - golem.getZ();
            double flat = Math.sqrt(dx * dx + dz * dz);
            if (flat <= 0.35) {
                // over the spot: gravitite's slow fall sets it down
                golem.setFloating(false);
                golem.setDeltaMovement(golem.getDeltaMovement().multiply(0.5, 1, 0.5));
                return;
            }
            Vec3 aim = golem.getY() < cruise - 0.3
                    // climb first, mostly straight up
                    ? new Vec3(golem.getX() + dx * 0.15, cruise, golem.getZ() + dz * 0.15)
                    : new Vec3(goal.x, cruise, goal.z);
            Vec3 step = aim.subtract(golem.position());
            double length = step.length();
            if (length > 1.0E-3) {
                step = step.scale(Math.min(FLOAT_SPEED, length) / length);
            }
            golem.setFloating(true);
            golem.setDeltaMovement(step);
            golem.setYRot(rotlerp(golem.getYRot(), (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F, 10));
            golem.yBodyRot = golem.getYRot();
            stuck = golem.horizontalCollision || golem.verticalCollision && step.y > 0 ? stuck + 1 : 0;
            if (stuck > 40) {
                log("give up", "stuck floating");
                fail();
            }
        }

        private static float rotlerp(float from, float to, float max) {
            float delta = Mth.clamp(Mth.wrapDegrees(to - from), -max, max);
            return from + delta;
        }

        private boolean arrived() {
            return golem.onGround() && golem.closeTo(target, 0.6) && Math.abs(golem.getY() - target.getY()) < 0.7;
        }

        private void arrive() {
            log("arrive", target.toShortString());
            if (golem.order != null && golem.order.equals(target)) {
                golem.setHome(target);
                golem.order = null;
                golem.orderedBy = null;
            }
            sitDown();
        }

        private void sitDown() {
            golem.getNavigation().stop();
            endFloat();
            golem.setDeltaMovement(Vec3.ZERO);
            golem.moveTo(target.getX() + 0.5, golem.getY(), target.getZ() + 0.5, golem.getYRot(), golem.getXRot());
            golem.setState(SEATED);
            golem.airborne = 0;
            done = true;
        }

        private void fail() {
            golem.shakeHead();
            if (golem.order != null) {
                Player player = golem.orderedBy == null ? null : golem.level().getPlayerByUUID(golem.orderedBy);
                if (player instanceof ServerPlayer serverPlayer) {
                    serverPlayer.displayClientMessage(Component.translatable(
                            "message." + AlteredAether.MOD_ID + ".golem.cant_reach", golem.getName()), true);
                }
                golem.order = null;
                golem.orderedBy = null;
                ticks = 0;
                retry = 0;
                target = currentTarget();
                route();
                return;
            }
            // can't get home: sit where it is for a minute, then try again
            endFloat();
            target = golem.blockPosition();
            rest = 1200;
            sitDown();
        }
    }

    @EventBusSubscriber(modid = AlteredAether.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
    public static final class AttributeRegistration {
        @SubscribeEvent
        public static void register(EntityAttributeCreationEvent event) {
            event.put(AARegistries.GRAVITITE_GOLEM.get(), createAttributes().build());
        }

        private AttributeRegistration() {
        }
    }
}
