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

import com.aetherteam.aether.entity.AetherEntityTypes;
import com.aetherteam.aether.entity.passive.Aerwhale;
import com.aetherteam.aether.item.AetherItems;
import com.linc.alteredaether.AATags;
import com.linc.alteredaether.config.AACommonConfig;
import com.linc.alteredaether.menu.AerwhaleInventoryMenu;
import com.linc.alteredaether.registry.AARegistries;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerListener;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.HasCustomInventoryScreen;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.Saddleable;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.PartEntity;

import javax.annotation.Nullable;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/**
 * An aerwhale that belongs to a player, or a calf that may yet. A wild Aether aerwhale turns into one when blue berries
 * tame it (see {@link AerwhaleTaming}). It extends the Aether's own aerwhale, so it keeps that mob's model, textures,
 * sounds and synced pitch, and adds what a horse has: an owner, a saddle and a saddlebag in an inventory screen, riding
 * with or without the saddle, a charged dive in place of a jump, the stay/wander/follow commands, and calves that grow
 * up, bred with enchanted berries or spawned from a spawn egg.
 *
 * <p>A saddled adult seats four, anyone at all, and the first aboard steers. Unsaddled, it carries one rider and goes
 * nowhere, as an unsaddled horse does. Its back is a deck players can walk on while it's saddled, made of
 * {@link AerwhaleDeck} slabs laid along the body, and whatever stands there is carried along as it flies and turns. The
 * slabs only stand in for the shape of its back: the whale still collides with blocks through its 3×3 hitbox, so its
 * back can pass into terrain.
 *
 * <p>The rider's client flies it, the way vanilla mounts move: the server takes its position from the rider's vehicle
 * packets. It has no gravity, so riding it doesn't trip the server's "floating a vehicle" kick.
 *
 * <p>A calf from a wild parent has no owner. It can be tamed with blue berries like its parents; if it grows up wild, it
 * turns into the Aether's wild aerwhale.
 *
 * <p>Deep Aether has aerwhales of its own: a patch to the Aether's aerwhale that adds a saddle, a chest and two seats.
 * Only one of the two is ever in play (see {@link AerwhaleMode}). With ours, that patch is switched off and Deep
 * Aether's Aerwhale Saddle is just another saddle; with Deep Aether's, a tamed aerwhale turns back into a wild one.
 */
public class TamedAerwhale extends Aerwhale
        implements OwnableEntity, Saddleable, HasCustomInventoryScreen, ContainerListener {
    private static final EntityDataAccessor<Optional<UUID>> DATA_OWNER =
            SynchedEntityData.defineId(TamedAerwhale.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Boolean> DATA_SADDLED =
            SynchedEntityData.defineId(TamedAerwhale.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_CHEST =
            SynchedEntityData.defineId(TamedAerwhale.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_BABY =
            SynchedEntityData.defineId(TamedAerwhale.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Byte> DATA_COMMAND =
            SynchedEntityData.defineId(TamedAerwhale.class, EntityDataSerializers.BYTE);

    /** Its flying speed, three quarters of the wild aerwhale's: a tamed whale is in no hurry. */
    public static final double FLYING_SPEED = 0.15;
    /** Top cruising speed under a rider, in blocks per tick (12 m/s, about a horse's). */
    private static final double RIDDEN_SPEED = 0.6;
    /** Speed along the arc of a dive. */
    private static final double DIVE_SPEED = RIDDEN_SPEED * 1.5;
    /** Ticks between dives, counted from the start of one. */
    private static final int DIVE_COOLDOWN = 40;
    /**
     * How a ridden whale comes round to where its rider looks: it gathers turning speed toward a fraction of the angle
     * left each tick, up to a few degrees, so it's slow to start, eases into the turn and swings a little past.
     */
    private static final float TURN_GAIN = 0.15F;
    private static final float TURN_EASE = 0.2F;
    /** How much of its speed it keeps while anyone stands on its back. */
    private static final double DECK_SPEED = 0.3;
    /** How far above the bottom of the world it keeps, and how far above that it starts pulling up. */
    private static final double FLOOR_HEIGHT = 10;
    private static final double FLOOR_MARGIN = 16;
    /** The pitch it climbs at when it's down at the floor. */
    private static final float FLOOR_CLIMB_PITCH = 20;
    /** How far its owner must fall onto its back to be taken aboard: more than a jump on the deck. */
    private static final float LANDING_FALL = 1.5F;
    /** How far a gummy swet draws a whale to its owner. */
    private static final double SWET_RANGE = 32;
    private static final float MAX_RIDDEN_PITCH = 60;
    private static final float MAX_WANDERING_PITCH = 40;
    /** How far it pitches while anything stands on its back, so the deck stays a floor rather than a slide. */
    private static final float MAX_DECK_PITCH = 15;
    /** How far a staying whale circles from where it was told to stay, and how far ahead on the circle it aims. */
    private static final double STAY_RADIUS = 10;
    private static final double STAY_LEAD = Math.toRadians(50);
    /**
     * When a following whale sets off after its owner and when it settles, in blocks: a wolf's 10 and 2, widened for
     * a body three blocks across.
     */
    private static final double FOLLOW_START = 12;
    private static final double FOLLOW_STOP = 6;
    /**
     * The slowest it swims, in blocks a tick (a block a second). Like a shark it never holds still: with nowhere to go
     * it swims slowly on, and held back below this, by its rider or by something in the way, it sinks.
     */
    public static final double MIN_SWIM_SPEED = 0.05;
    /** How fast it sinks at most when it's making no headway at all. */
    private static final double SINK_SPEED = 0.08;
    /** The tightest circle it turns in when it isn't ridden, in blocks: too big to pivot on the spot. */
    public static final double MIN_TURN_RADIUS = 4;
    /** How far beyond its body someone can be and still count as overhead: on its back, or about to land there. */
    private static final double OVERHEAD_REACH = 3;
    /**
     * The saddle's seats, the pilot's first: on the broad back of the Aether's model, and higher on its b1.7.3 model,
     * which floats above its hitbox. They match the seat pads of {@code AerwhaleSaddleLayer}.
     */
    private static final BodyPoint[] SEATS = {
            new BodyPoint(0, 3.32, 1.63), new BodyPoint(-0.69, 2.77, -0.6), new BodyPoint(0.69, 2.77, -0.6),
            new BodyPoint(0, 2.59, -2.0)};
    private static final BodyPoint[] CLASSIC_SEATS = {
            new BodyPoint(-0.19, 5.0, 0), new BodyPoint(-0.875, 4.88, -1.375), new BodyPoint(0.5, 4.88, -1.375),
            new BodyPoint(-0.19, 4.88, 1.25)};
    /**
     * Where the deck's slabs lie, with {@code up} the top of each: the saddle blanket over the head, the lower middle,
     * then the tail sloping away. The b1.7.3 model's back is one flat box, so its table repeats slabs.
     */
    private static final BodyPoint[] DECK = {
            new BodyPoint(0, 3.0, 2.6), new BodyPoint(0, 2.95, 1.2), new BodyPoint(0, 2.55, -0.4),
            new BodyPoint(0, 2.4, -1.8), new BodyPoint(0, 2.1, -3.2)};
    private static final BodyPoint[] CLASSIC_DECK = {
            new BodyPoint(-0.19, 4.7, 0.375), new BodyPoint(-0.19, 4.7, -0.625), new BodyPoint(-0.19, 4.7, -0.625),
            new BodyPoint(-0.19, 4.7, -0.625), new BodyPoint(-0.19, 4.7, -0.875)};
    private static final float[] DECK_WIDTHS = {2.75F, 2.75F, 2.75F, 2.75F, 2.25F};
    private static final int LOVE_TICKS = 600;
    private static final ResourceLocation DEEP_AETHER_SADDLE =
            ResourceLocation.fromNamespaceAndPath("deep_aether", "aerwhale_saddle");

    /** The saddle's slot, then the saddlebag's, as in a mule's inventory. */
    public static final int SADDLE_SLOT = 0;
    public static final int SADDLEBAG_SIZE = 15;
    public static final int INVENTORY_SIZE = 1 + SADDLEBAG_SIZE;

    /** Whether the Aether draws aerwhales with their b1.7.3 model. Set by the client from the Aether's config. */
    public static BooleanSupplier legacyModel = () -> false;

    /** On a client, the whale whose deck its own player stands on, if any. */
    @Nullable
    private static TamedAerwhale localDeck;

    private final SimpleContainer inventory = new SimpleContainer(INVENTORY_SIZE);

    // Server state.
    /** Below zero a calf growing up, above zero a parent's breeding cooldown, as in vanilla's AgeableMob. */
    private int age;
    private int loveTicks;
    @Nullable
    private UUID loveCause;
    /** Where it was told to stay. */
    @Nullable
    private BlockPos home;

    // Flight under a rider, which only the rider's client runs.
    private boolean wasPiloting;
    private float pilotPitch;
    private float pilotPitchO;
    private float pilotYaw;
    private double cruise;
    /** The dive under way, where it began, its heading and how far along it is. */
    @Nullable
    private AerwhaleDive dive;
    private Vec3 diveStart = Vec3.ZERO;
    private float diveYaw;
    private double diveDistance;
    /** How fast it is going along the dive, gathering speed from what it was flying at. */
    private double diveSpeed;
    /** How fast it's sinking for want of headway, wherever it's flown from. */
    private double sink;
    /** Kept on both sides: the server to refuse a dive too soon, the client to refuse one in the dive key. */
    private int diveCooldown;
    /** How fast it's turning and pitching under the rider, in degrees a tick. */
    private float yawSpeed;
    private float pitchSpeed;

    // Leaping off, on the server.
    /** The rider leaping off right now, who leaves from their seat rather than onto the deck. */
    @Nullable
    private Entity ejecting;
    /** A player who just leapt off, and how fast they leapt, to tell their client on the next tick. */
    @Nullable
    private ServerPlayer thrown;
    private Vec3 throwMotion = Vec3.ZERO;
    /** Ticks left coasting on after its pilot leapt off, before it turns to follow its owner. */
    private int coast;

    // On a client that isn't flying it, its pitch and yaw eased toward what the server last sent, so the turns of a
    // whale someone stands on are as smooth as its movement.
    private boolean smoothed;
    private float smoothYaw;
    private float smoothPitch;
    private float smoothPitchO;
    private float syncedYaw;
    private float syncedPitch;
    private int smoothSteps;

    // The deck, laid on both sides.
    private final AerwhaleDeck[] deck = new AerwhaleDeck[DECK.length];
    /** Where the whale was when the deck was last laid, to carry what stands on it by as much as it moved since. */
    @Nullable
    private Vec3 deckPos;
    private float deckYaw;
    private float deckPitch;
    /** Whether anything stands on the deck, which keeps the whale near level. */
    private boolean deckLoaded;

    public TamedAerwhale(EntityType<? extends TamedAerwhale> type, Level level) {
        super(type, level);
        // the Aether's own steering, kept for cruising the way a wild aerwhale does
        this.moveControl = new WhaleMoveControl(moveControl);
        setNoGravity(true);
        inventory.addListener(this);
        for (int i = 0; i < deck.length; i++) {
            deck[i] = new AerwhaleDeck(this, DECK_WIDTHS[i]);
        }
        // the slabs take the ids after the whale's, as the ender dragon's parts do
        setId(ENTITY_COUNTER.getAndAdd(deck.length + 1) + 1);
    }

    @Override
    public void registerGoals() {
        // replaces the Aether's endless random cruising, which only wanderers and calves keep
        goalSelector.addGoal(0, new SwetTemptGoal());
        goalSelector.addGoal(1, new BreedGoal());
        goalSelector.addGoal(2, new FollowAdultGoal());
        goalSelector.addGoal(3, new CommandGoal());
        goalSelector.addGoal(4, new CruiseGoal());
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_OWNER, Optional.empty());
        builder.define(DATA_SADDLED, false);
        builder.define(DATA_CHEST, false);
        builder.define(DATA_BABY, false);
        builder.define(DATA_COMMAND, (byte) AerwhaleCommand.FOLLOW.ordinal());
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_BABY.equals(key)) {
            refreshDimensions();
        }
    }

    /** Turns a wild aerwhale into a tamed one owned by {@code owner}, keeping its place, heading and health. */
    @Nullable
    public static TamedAerwhale tame(Aerwhale wild, Player owner) {
        float pitch = wild.getXRotData();
        float yaw = wild.getYRotData();
        float health = wild.getHealth();
        TamedAerwhale tamed = wild.convertTo(AARegistries.TAMED_AERWHALE.get(), false);
        if (tamed == null) {
            return null;
        }
        tamed.setOwnerUUID(owner.getUUID());
        tamed.setXRotData(pitch);
        tamed.setXRotOData(pitch);
        tamed.setYRotData(yaw);
        tamed.setHealth(health);
        tamed.setCommand(AerwhaleCommand.FOLLOW);
        tamed.particles(ParticleTypes.HEART, 7);
        awardTaming(owner, tamed);
        return tamed;
    }

    /** Counts as taming an animal for vanilla's advancements (Best Friends Forever), which only see {@code Animal}s. */
    private static void awardTaming(Player player, TamedAerwhale whale) {
        if (player instanceof ServerPlayer serverPlayer) {
            LootContext context = EntityPredicate.createContext(serverPlayer, whale);
            CriteriaTriggers.TAME_ANIMAL.trigger(serverPlayer, instance -> instance.matches(context));
        }
    }

    /**
     * A calf spawned from a spawn egg used on {@code parent}, wild or tamed, the way vanilla's eggs make babies of the
     * animals they're used on. It belongs to the parent's owner, if any, and takes the egg's name.
     */
    public static void spawnCalf(ServerLevel level, Aerwhale parent, Player player, ItemStack egg) {
        TamedAerwhale calf = AARegistries.TAMED_AERWHALE.get().create(level);
        if (calf == null) {
            return;
        }
        UUID owner = parent instanceof TamedAerwhale tamed ? tamed.getOwnerUUID() : null;
        calf.setOwnerUUID(owner);
        calf.setAge(-AACommonConfig.AERWHALE_GROWTH_TIME.get());
        calf.moveTo(parent.getX(), parent.getY(), parent.getZ(), parent.getYRot(), 0);
        calf.setYRotData(parent.getYRot());
        calf.setCustomName(egg.get(DataComponents.CUSTOM_NAME));
        level.addFreshEntity(calf);
        calf.particles(ParticleTypes.HAPPY_VILLAGER, 6);
        egg.consume(1, player);
    }

    public static boolean isCalfEgg(ItemStack stack) {
        return stack.is(AetherItems.AERWHALE_SPAWN_EGG.get());
    }

    // ---- ownership, saddle, chest, age ----

    @Nullable
    @Override
    public UUID getOwnerUUID() {
        return entityData.get(DATA_OWNER).orElse(null);
    }

    public void setOwnerUUID(@Nullable UUID owner) {
        entityData.set(DATA_OWNER, Optional.ofNullable(owner));
    }

    public boolean isOwnedBy(LivingEntity entity) {
        return entity.getUUID().equals(getOwnerUUID());
    }

    @Override
    public boolean isSaddleable() {
        return isAlive() && !isBaby();
    }

    @Override
    public void equipSaddle(ItemStack stack, @Nullable SoundSource source) {
        // the sound comes from containerChanged
        inventory.setItem(SADDLE_SLOT, stack.isEmpty() ? new ItemStack(Items.SADDLE) : stack.copyWithCount(1));
    }

    /** Its saddle and saddlebag, which its inventory screen shows. */
    public Container inventory() {
        return inventory;
    }

    /** Keeps the synced saddle in step with the saddle slot, whoever changed it. */
    @Override
    public void containerChanged(Container container) {
        if (level().isClientSide) {
            return;
        }
        boolean was = isSaddled();
        boolean saddled = !inventory.getItem(SADDLE_SLOT).isEmpty();
        entityData.set(DATA_SADDLED, saddled);
        if (saddled && !was && tickCount > 20) {
            level().playSound(null, this, getSaddleSoundEvent(), SoundSource.NEUTRAL, 0.5F, 1.0F);
        }
        if (!saddled && was) {
            // an unsaddled whale carries only one
            List<Entity> riders = getPassengers();
            for (Entity rider : List.copyOf(riders.subList(Math.min(1, riders.size()), riders.size()))) {
                rider.stopRiding();
            }
        }
    }

    public boolean hasChest() {
        return entityData.get(DATA_CHEST);
    }

    /** Gives it a saddlebag, or takes it away along with whatever was in it. */
    public void setChest(boolean chest) {
        if (!chest) {
            for (int i = 1; i < INVENTORY_SIZE; i++) {
                ItemStack stack = inventory.removeItemNoUpdate(i);
                if (!stack.isEmpty() && !level().isClientSide) {
                    spawnAtLocation(stack);
                }
            }
        }
        entityData.set(DATA_CHEST, chest);
    }

    /**
     * Turns this back into the Aether's wild aerwhale, once Deep Aether's aerwhales have taken over. A saddle stays on as
     * Deep Aether's, through the method its patch adds to every aerwhale; the saddlebag and its contents drop.
     */
    private void untame() {
        boolean saddled = isSaddled();
        ItemStack worn = inventory.getItem(SADDLE_SLOT).copy();
        if (hasChest()) {
            setChest(false);
            spawnAtLocation(Items.CHEST);
        }
        Aerwhale wild = convertTo(AetherEntityTypes.AERWHALE.get(), false);
        if (wild == null) {
            return;
        }
        wild.setXRotData(getXRotData());
        wild.setYRotData(getYRotData());
        wild.setHealth(getHealth());
        wild.setPersistenceRequired();
        if (saddled) {
            try {
                wild.getClass().getMethod("equipSaddle", SoundSource.class).invoke(wild, (Object) null);
            } catch (ReflectiveOperationException e) {
                wild.spawnAtLocation(worn.isEmpty() ? new ItemStack(Items.SADDLE) : worn);
            }
        }
    }

    /** An ownerless calf that grows up becomes the Aether's wild aerwhale. */
    private void growUpWild() {
        Aerwhale wild = convertTo(AetherEntityTypes.AERWHALE.get(), false);
        if (wild != null) {
            wild.setXRotData(getXRotData());
            wild.setYRotData(getYRotData());
        }
    }

    public static boolean isSaddleItem(ItemStack stack) {
        return stack.is(Items.SADDLE)
                || !stack.isEmpty() && BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(DEEP_AETHER_SADDLE);
    }

    @Override
    public boolean isSaddled() {
        return entityData.get(DATA_SADDLED);
    }

    /** Whether its back can be stood on: a saddled adult's can. */
    public boolean hasDeck() {
        return isSaddled() && !isBaby() && isAlive();
    }

    @Override
    public boolean isBaby() {
        return entityData.get(DATA_BABY);
    }

    @Override
    public void setBaby(boolean baby) {
        entityData.set(DATA_BABY, baby);
    }

    private void setAge(int age) {
        this.age = age;
        setBaby(age < 0);
    }

    public AerwhaleCommand command() {
        return AerwhaleCommand.byId(entityData.get(DATA_COMMAND));
    }

    /** Gives a command, anchoring stay to where the whale is now. */
    public void setCommand(AerwhaleCommand command) {
        entityData.set(DATA_COMMAND, (byte) command.ordinal());
        home = blockPosition();
        halt();
    }

    public boolean isInLove() {
        return loveTicks > 0;
    }

    private boolean canFallInLove() {
        return age == 0 && !isInLove() && !isBaby();
    }

    private void setInLove(Player player) {
        loveTicks = LOVE_TICKS;
        loveCause = player.getUUID();
        particles(ParticleTypes.HEART, 7);
    }

    private void resetLove() {
        loveTicks = 0;
        loveCause = null;
    }

    public static boolean isTamingFood(ItemStack stack) {
        return stack.is(AATags.AERWHALE_TAMING_FOOD);
    }

    public static boolean isBreedingFood(ItemStack stack) {
        return stack.is(AATags.AERWHALE_BREEDING_FOOD);
    }

    /** How much of its health a gummy swet restores: a quarter for a blue one, half for a golden one. */
    public static float swetHealing(ItemStack stack) {
        if (stack.is(AetherItems.BLUE_GUMMY_SWET.get())) {
            return 0.25F;
        }
        return stack.is(AetherItems.GOLDEN_GUMMY_SWET.get()) ? 0.5F : 0;
    }

    // ---- interaction ----

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        boolean client = level().isClientSide;
        if (getOwnerUUID() == null) {
            // a wild calf: tamed like its parents, and nothing else until then
            if (!isTamingFood(stack)) {
                return super.mobInteract(player, hand);
            }
            if (!client) {
                stack.consume(1, player);
                if (random.nextDouble() < AACommonConfig.AERWHALE_TAME_CHANCE.get()) {
                    setOwnerUUID(player.getUUID());
                    setCommand(AerwhaleCommand.FOLLOW);
                    particles(ParticleTypes.HEART, 7);
                    awardTaming(player, this);
                } else {
                    particles(ParticleTypes.SMOKE, 7);
                }
            }
            return InteractionResult.sidedSuccess(client);
        }

        if (!isOwnedBy(player)) {
            if (player.isSecondaryUseActive() || isTamingFood(stack) || isBreedingFood(stack) || swetHealing(stack) > 0
                    || isSaddleItem(stack) || stack.is(Items.CHEST)) {
                if (!client) {
                    player.displayClientMessage(Component.translatable("message.altered_aether.aerwhale.not_owner"), true);
                }
                return InteractionResult.sidedSuccess(client);
            }
            // anyone may come aboard
            return isBaby() ? super.mobInteract(player, hand) : board(player);
        }

        if (player.isSecondaryUseActive()) {
            if (!client) {
                setCommand(command().next());
                player.displayClientMessage(Component.translatable(command().translationKey(), getDisplayName()), true);
            }
            return InteractionResult.sidedSuccess(client);
        }

        float swet = swetHealing(stack);
        if (swet > 0 && getHealth() < getMaxHealth()) {
            if (!client) {
                stack.consume(1, player);
                heal(getMaxHealth() * swet);
                particles(ParticleTypes.HAPPY_VILLAGER, 6);
            }
            return InteractionResult.sidedSuccess(client);
        }

        if (isBreedingFood(stack)) {
            // Only the server knows its age and love, so the client can't tell which of these applies.
            if (client) {
                return InteractionResult.CONSUME;
            }
            if (isBaby()) {
                stack.consume(1, player);
                setAge(age + Math.max(1, -age / 10));
                particles(ParticleTypes.HAPPY_VILLAGER, 6);
                return InteractionResult.SUCCESS;
            }
            if (canFallInLove()) {
                stack.consume(1, player);
                setInLove(player);
                return InteractionResult.SUCCESS;
            }
            if (getHealth() < getMaxHealth()) {
                stack.consume(1, player);
                heal(8);
                particles(ParticleTypes.HAPPY_VILLAGER, 6);
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        }

        if (isTamingFood(stack) && getHealth() < getMaxHealth()) {
            if (!client) {
                stack.consume(1, player);
                heal(4);
                particles(ParticleTypes.HAPPY_VILLAGER, 4);
            }
            return InteractionResult.sidedSuccess(client);
        }

        if (isSaddleItem(stack) && isSaddleable() && !isSaddled()) {
            // both saddles go on here; Deep Aether's item only knows its own aerwhales
            if (!client) {
                equipSaddle(stack.split(1), SoundSource.NEUTRAL);
                gameEvent(GameEvent.EQUIP, player);
            }
            return InteractionResult.sidedSuccess(client);
        }

        if (stack.is(Items.CHEST) && !hasChest() && !isBaby()) {
            if (!client) {
                setChest(true);
                playSound(SoundEvents.DONKEY_CHEST, 1.0F, (random.nextFloat() - random.nextFloat()) * 0.2F + 1.0F);
                stack.consume(1, player);
            }
            return InteractionResult.sidedSuccess(client);
        }

        if (!isBaby()) {
            return board(player);
        }
        return super.mobInteract(player, hand);
    }

    private InteractionResult board(Player player) {
        if (!canAddPassenger(player)) {
            return InteractionResult.PASS;
        }
        if (!level().isClientSide) {
            player.setYRot(getYRot());
            player.startRiding(this);
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    /**
     * Its inventory screen, opened with the inventory key while aboard. Only its owner can reach the saddle and
     * saddlebag; anyone else riding it sees just their own inventory.
     */
    @Override
    public void openCustomInventoryScreen(Player player) {
        if (player instanceof ServerPlayer server && !isBaby() && (isOwnedBy(player) || hasPassenger(player))) {
            server.openMenu(new SimpleMenuProvider(
                            (id, playerInventory, opener) -> new AerwhaleInventoryMenu(id, playerInventory, this),
                            getDisplayName()),
                    buffer -> buffer.writeVarInt(getId()));
        }
    }

    // ---- ticking ----

    @Override
    public void tick() {
        if (AerwhaleMode.deepAether() && !level().isClientSide) {
            untame();
            return;
        }
        if (diveCooldown > 0) {
            diveCooldown--;
        }
        boolean piloting = piloting();
        if (piloting && !wasPiloting) {
            pilotPitch = super.getXRotData();
            pilotYaw = getYRot();
            cruise = getDeltaMovement().length();
            yawSpeed = 0;
            pitchSpeed = 0;
        }
        if (level().isClientSide) {
            smoothRotation(piloting);
        }
        if (!piloting) {
            dive = null;
        }
        wasPiloting = piloting;
        pilotPitchO = pilotPitch;
        super.tick();
        moveDeck();
    }

    @Override
    public void aiStep() {
        if (!level().isClientSide) {
            if (thrown != null) {
                // The tracker has now sent the dismount, and the teleport that comes with it, which stops the player
                // on their client; only now does the throw stick.
                if (!thrown.isRemoved() && !thrown.isPassenger()) {
                    thrown.setDeltaMovement(throwMotion);
                    thrown.connection.send(new ClientboundSetEntityMotionPacket(thrown));
                }
                thrown = null;
            }
            if (getControllingPassenger() != null) {
                // The rider's vehicle packets set the rotation. The Aether's aiStep copies the synced pitch and yaw
                // back over it, so store it there first; that also shows other players where the whale points.
                setXRotData(getXRot());
                setYRotData(getYRot());
            }
            if (age < 0) {
                setAge(age + 1);
                if (age == 0 && getOwnerUUID() == null) {
                    growUpWild();
                    return;
                }
            } else if (age > 0) {
                age--;
            }
            if (loveTicks > 0 && --loveTicks % 10 == 0) {
                particles(ParticleTypes.HEART, 1);
            }
            if (loveTicks == 0) {
                loveCause = null;
            }
            if (coast > 0 && --coast == 0 && getControllingPassenger() == null) {
                // nobody came back aboard: it goes looking for its owner
                setCommand(AerwhaleCommand.FOLLOW);
            }
        }
        super.aiStep();
    }

    /**
     * Eases the pitch and yaw this client shows toward the synced values, a third of the way each tick as vanilla
     * eases positions, rather than jumping as each update arrives. A client flying the whale has its own.
     */
    private void smoothRotation(boolean piloting) {
        float yaw = super.getYRotData();
        float pitch = super.getXRotData();
        smoothPitchO = smoothPitch;
        if (piloting || !smoothed) {
            smoothYaw = piloting ? pilotYaw : yaw;
            smoothPitch = piloting ? pilotPitch : pitch;
            smoothPitchO = smoothPitch;
            smoothSteps = 0;
            smoothed = true;
        } else {
            if (yaw != syncedYaw || pitch != syncedPitch) {
                smoothSteps = 3;
            }
            if (smoothSteps > 0) {
                smoothYaw += Mth.wrapDegrees(yaw - smoothYaw) / smoothSteps;
                smoothPitch += (pitch - smoothPitch) / smoothSteps;
                smoothSteps--;
            }
        }
        syncedYaw = yaw;
        syncedPitch = pitch;
    }

    /** Whether this client shows the eased rotation rather than the synced one. */
    private boolean smoothing() {
        return level().isClientSide && smoothed && !piloting();
    }

    /** Whether this client is flying the whale. Its rotation then comes from {@link #fly}, not the synced data. */
    private boolean piloting() {
        return level().isClientSide && getControllingPassenger() instanceof Player rider && rider.isLocalPlayer();
    }

    @Override
    public float getXRotOData() {
        if (piloting()) {
            return pilotPitchO;
        }
        return smoothing() ? smoothPitchO : super.getXRotOData();
    }

    @Override
    public float getXRotData() {
        if (piloting()) {
            return pilotPitch;
        }
        return smoothing() ? smoothPitch : super.getXRotData();
    }

    @Override
    public float getYRotData() {
        if (piloting()) {
            return pilotYaw;
        }
        return smoothing() ? smoothYaw : super.getYRotData();
    }

    // ---- riding ----

    /** Only a saddle gives the rider the reins; bareback, they're just along for the ride. */
    @Nullable
    @Override
    public LivingEntity getControllingPassenger() {
        if (isSaddled() && getFirstPassenger() instanceof Player rider) {
            return rider;
        }
        return super.getControllingPassenger();
    }

    @Override
    protected Vec3 getRiddenInput(Player rider, Vec3 input) {
        return new Vec3(rider.xxa, 0, rider.zza);
    }

    @Override
    protected void tickRidden(Player rider, Vec3 input) {
        super.tickRidden(rider, input);
        if (piloting() && dive == null) {
            // A big animal is slow to come round, and turns slower still at speed.
            float maxTurn = (float) Mth.lerp(Math.min(1, cruise / RIDDEN_SPEED), 4.0, 2.0);
            float wanted = Mth.clamp(Mth.wrapDegrees(rider.getYRot() - pilotYaw) * TURN_GAIN, -maxTurn, maxTurn);
            yawSpeed += (wanted - yawSpeed) * TURN_EASE;
            pilotYaw += yawSpeed;
            setYRot(pilotYaw);
            setYBodyRot(pilotYaw);
            setYHeadRot(pilotYaw);
        }
    }

    /**
     * Never the Aether's travel, which steers by any player aboard: the rider's client flies a saddled whale, and
     * everything else drifts on whatever motion its move control gave it.
     */
    @Override
    public void travel(Vec3 input) {
        if (getControllingPassenger() instanceof Player rider && isControlledByLocalInstance()) {
            fly(rider, input);
        } else {
            if (isControlledByLocalInstance()) {
                Vec3 from = position();
                move(MoverType.SELF, getDeltaMovement());
                setDeltaMovement(getDeltaMovement().scale(0.91));
                sinkWithoutHeadway(from);
            }
            calculateEntityAnimation(false);
        }
    }

    /**
     * Like a shark it needs headway to stay up: when it moves less than it swims at its slowest, braked or held up
     * against something, it sinks, gathering speed gently, though never below the floor above the void. Moving
     * straight up or down is no headway.
     */
    private void sinkWithoutHeadway(Vec3 from) {
        double headway = position().subtract(from).horizontalDistance();
        double wanted = isNoAi() ? 0 : SINK_SPEED * Mth.clamp(1 - headway / (MIN_SWIM_SPEED * 0.75), 0, 1);
        sink += (wanted - sink) * 0.05;
        double drop = Math.min(sink, getY() - floorY());
        if (drop > 1.0E-4) {
            move(MoverType.SELF, new Vec3(0, -drop, 0));
        }
    }

    /**
     * Flight under a rider: forward speeds up toward the cruising speed, back brakes, letting go glides down to the
     * slowest it swims. The whale pitches toward where the rider looks, which is how it climbs and descends, and
     * sidesteps with strafe. Braked below its slowest it starts to sink.
     */
    private void fly(Player rider, Vec3 input) {
        if (dive != null) {
            diveStep();
            return;
        }
        float maxPitch = deckLoaded ? MAX_DECK_PITCH : MAX_RIDDEN_PITCH;
        // near the bottom of the world it lifts its nose whatever the rider wants, and levels out above the floor
        double floor = floorY();
        float minPitch = (float) Mth.clampedMap(getY(), floor + FLOOR_MARGIN, floor, -maxPitch, FLOOR_CLIMB_PITCH);
        float pitchTarget = Mth.clamp(-rider.getXRot(), minPitch, Math.max(minPitch, maxPitch));
        float wantedPitch = Mth.clamp((pitchTarget - pilotPitch) * TURN_GAIN, -3.0F, 3.0F);
        pitchSpeed += (wantedPitch - pitchSpeed) * TURN_EASE;
        pilotPitch = Mth.clamp(pilotPitch + pitchSpeed, -MAX_RIDDEN_PITCH, MAX_RIDDEN_PITCH);
        // it goes gently with anyone standing on its back
        double top = deckLoaded ? RIDDEN_SPEED * DECK_SPEED : RIDDEN_SPEED;
        if (input.z > 0) {
            cruise += (top * input.z - cruise) * 0.04;
        } else if (input.z < 0) {
            cruise = Math.max(0, cruise - 0.03);
        } else if (cruise > MIN_SWIM_SPEED) {
            cruise = Math.max(MIN_SWIM_SPEED, cruise * 0.99);
        } else {
            cruise += (MIN_SWIM_SPEED - cruise) * 0.04;
        }
        if (cruise > top) {
            cruise = Math.max(top, cruise - 0.01);
        }
        double yaw = pilotYaw * Mth.DEG_TO_RAD;
        double pitch = pilotPitch * Mth.DEG_TO_RAD;
        Vec3 heading = new Vec3(-Math.sin(yaw) * Math.cos(pitch), Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch));
        Vec3 target = heading.scale(cruise).add(Math.cos(yaw) * input.x * 0.15, 0, Math.sin(yaw) * input.x * 0.15);
        if (getY() < floor) {
            // below the floor it rises back up to it, even when it isn't going anywhere
            target = new Vec3(target.x, Math.max(target.y, Math.min(0.3, (floor - getY()) * 0.1)), target.z);
        }
        Vec3 motion = getDeltaMovement().lerp(target, 0.2);
        setDeltaMovement(motion);
        Vec3 from = position();
        move(MoverType.SELF, motion);
        if (horizontalCollision) {
            cruise *= 0.5;
        }
        sinkWithoutHeadway(from);
        setXRot(pilotPitch);
        calculateEntityAnimation(false);
    }

    /**
     * One tick along the dive's arc, straight ahead and gathering speed up to half again the cruising speed, nose
     * following the curve. Running into anything ends it early.
     */
    private void diveStep() {
        double slope = dive.slope(diveDistance);
        diveSpeed = Math.min(DIVE_SPEED, diveSpeed + 0.03);
        diveDistance += diveSpeed / Math.sqrt(1 + slope * slope);
        double yaw = diveYaw * Mth.DEG_TO_RAD;
        Vec3 target = diveStart.add(-Math.sin(yaw) * diveDistance, dive.height(diveDistance), Math.cos(yaw) * diveDistance);
        Vec3 motion = target.subtract(position());
        setDeltaMovement(motion);
        move(MoverType.SELF, motion);
        pilotPitch = Mth.clamp(dive.pitch(diveDistance), -MAX_RIDDEN_PITCH, MAX_RIDDEN_PITCH);
        setXRot(pilotPitch);
        if (dive.done(diveDistance) || horizontalCollision || verticalCollision) {
            dive = null;
            cruise = RIDDEN_SPEED;
            yawSpeed = 0;
            pitchSpeed = 0;
        }
        calculateEntityAnimation(false);
    }

    /** A dive needs the reins, a clear deck and a breath between dives. */
    public boolean canDive() {
        return isSaddled() && !deckLoaded && dive == null && diveCooldown == 0;
    }

    /**
     * The rider let go of a charged dive, {@code power} 0 to 100 as on a horse's jump bar: the client that flies the
     * whale starts it.
     */
    public void startDive(int power) {
        if (power <= 0 || !piloting() || !canDive()) {
            return;
        }
        // no deeper than the floor above the void
        double depth = Math.min(AerwhaleDive.depthFor(power), getY() - floorY());
        if (depth < 1) {
            return;
        }
        // it leaves at the angle it already flies at, with the speed it had
        dive = AerwhaleDive.of(depth, pilotPitch);
        diveSpeed = cruise;
        diveStart = position();
        diveYaw = pilotYaw;
        diveDistance = 0;
        diveCooldown = DIVE_COOLDOWN;
    }

    /** The server hears of its pilot's dive: a deep call, and no other dive until it's had a breath. */
    public void diveCalled(Player rider, int power) {
        if (level().isClientSide || getControllingPassenger() != rider || power <= 0 || !canDive()) {
            return;
        }
        diveCooldown = DIVE_COOLDOWN;
        playSound(getAmbientSound(), getSoundVolume(), 0.6F);
    }

    /**
     * {@code rider} leaps off: they spring straight up from their seat, {@code charge} (0 to 1) of the way to the
     * highest leap, keeping the speed {@code velocity} the whale had as their client saw it. If they were flying it and
     * nobody else aboard takes the reins, it coasts on as it was going, long enough for them to come down on its back
     * again, then turns to follow its owner.
     */
    public void eject(Player rider, float charge, Vec3 velocity) {
        if (level().isClientSide || !hasPassenger(rider)) {
            return;
        }
        if (!Double.isFinite(velocity.lengthSqr())) {
            velocity = Vec3.ZERO;
        } else if (velocity.length() > AerwhaleEject.MAX_INHERITED_SPEED) {
            velocity = velocity.normalize().scale(AerwhaleEject.MAX_INHERITED_SPEED);
        }
        boolean pilot = getControllingPassenger() == rider;
        ejecting = rider;
        try {
            rider.stopRiding();
        } finally {
            ejecting = null;
        }
        double launch = AerwhaleEject.launchSpeed(AerwhaleEject.heightFor(charge), rider.getGravity());
        rider.setDeltaMovement(velocity.add(0, launch, 0));
        rider.resetFallDistance();
        if (rider instanceof ServerPlayer player) {
            // A player's client places them, so the throw has to reach it. Not yet, though: the tracker sends the
            // dismount at the start of the next tick along with a teleport to where the server has them, which stops
            // them dead on their client, so the throw goes out after it, from this whale's next tick.
            thrown = player;
            throwMotion = rider.getDeltaMovement();
        }
        if (pilot) {
            // whoever takes the reins next carries on at this speed
            setDeltaMovement(velocity);
            hurtMarked = true;
            if (getControllingPassenger() == null) {
                coast = AerwhaleEject.COAST_TICKS;
            }
        }
        playSound(getAmbientSound(), getSoundVolume() * 0.5F, 1.4F);
    }

    /** Whether it's coasting on after its pilot leapt off. */
    public boolean coasting() {
        return coast > 0;
    }

    /**
     * Takes its owner aboard when they land on its back from higher than a jump, unless they're sneaking: the way back
     * on after leaping off. Returns whether they're now riding.
     */
    public boolean tryLandingMount(Player player, float fallDistance) {
        if (level().isClientSide || !hasDeck() || !isOwnedBy(player) || player.isPassenger() || player.isShiftKeyDown()
                || player.isSpectator() || fallDistance < LANDING_FALL || !canAddPassenger(player)) {
            return false;
        }
        // generous, since the client that landed saw the whale a tick or two behind where it is now
        AABB box = player.getBoundingBox();
        AABB feet = new AABB(box.minX, box.minY - 0.6, box.minZ, box.maxX, box.minY + 0.3, box.maxZ);
        for (AerwhaleDeck slab : deck) {
            if (slab.getBoundingBox().inflate(1.0, 0, 1.0).intersects(feet)) {
                return player.startRiding(this);
            }
        }
        return false;
    }

    /** The lowest it flies: a little above the bottom of the world, over the void. */
    public double floorY() {
        return level().getMinBuildHeight() + FLOOR_HEIGHT;
    }

    /** Ticks until it can dive again. */
    public int diveCooldown() {
        return diveCooldown;
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return !isBaby() && getPassengers().size() < (isSaddled() ? SEATS.length : 1);
    }

    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scale) {
        BodyPoint[] seats = legacyModel.getAsBoolean() ? CLASSIC_SEATS : SEATS;
        BodyPoint seat = seats[Mth.clamp(getPassengers().indexOf(passenger), 0, seats.length - 1)];
        return toWorld(seat, yBodyRot, getXRotData());
    }

    /** A rider getting off steps onto the deck beside their seat, if there's room, rather than into the sky. */
    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        if (passenger == ejecting) {
            return passenger.position();
        }
        if (hasDeck()) {
            Vec3 best = null;
            for (AerwhaleDeck slab : deck) {
                AABB top = slab.getBoundingBox();
                // a hair above the slab, or the slab itself counts as being in the way
                Vec3 spot = new Vec3(Mth.clamp(passenger.getX(), top.minX + 0.4, top.maxX - 0.4), top.maxY + 0.01,
                        Mth.clamp(passenger.getZ(), top.minZ + 0.4, top.maxZ - 0.4));
                if ((best == null || passenger.distanceToSqr(spot) < passenger.distanceToSqr(best))
                        && level().noCollision(passenger, passenger.getDimensions(Pose.STANDING).makeBoundingBox(spot))) {
                    best = spot;
                }
            }
            if (best != null) {
                return best;
            }
        }
        return super.getDismountLocationForPassenger(passenger);
    }

    // ---- the deck ----

    @Override
    public boolean isMultipartEntity() {
        return true;
    }

    @Override
    public PartEntity<?>[] getParts() {
        return deck;
    }

    @Override
    public void setId(int id) {
        super.setId(id);
        for (int i = 0; i < deck.length; i++) {
            deck[i].setId(id + i + 1);
        }
    }

    @Override
    public boolean canCollideWith(Entity entity) {
        return !(entity instanceof AerwhaleDeck slab && slab.getParent() == this) && super.canCollideWith(entity);
    }

    // What stands on the deck is inside the hitbox, and mustn't shove the whale around or be shoved off.
    @Override
    public boolean isPushable() {
        return !hasDeck() && super.isPushable();
    }

    @Override
    protected void pushEntities() {
        if (!hasDeck()) {
            super.pushEntities();
        }
    }

    /**
     * Lays the deck along the body where the whale is now, and carries whatever stood on it by as much as the whale
     * moved, turned and pitched since the last tick. Each side carries only what it moves itself: the client its own
     * player, the server everything but players, whose clients place them.
     */
    private void moveDeck() {
        List<Entity> load = hasDeck() ? standingOnDeck() : List.of();
        deckLoaded = !load.isEmpty();
        Vec3 pos = position();
        float yaw = yBodyRot;
        float pitch = getXRotData();
        BodyPoint[] slabs = legacyModel.getAsBoolean() ? CLASSIC_DECK : DECK;
        for (int i = 0; i < deck.length; i++) {
            BodyPoint slab = slabs[i];
            Vec3 centre = pos.add(toWorld(
                    new BodyPoint(slab.side(), slab.up() - AerwhaleDeck.THICKNESS / 2, slab.forward()), yaw, pitch));
            deck[i].setPos(centre.x, centre.y - AerwhaleDeck.THICKNESS / 2, centre.z);
        }
        boolean carryingLocalPlayer = false;
        if (deckPos != null) {
            float turn = Mth.wrapDegrees(yaw - deckYaw);
            for (Entity entity : load) {
                if (level().isClientSide ? !(entity instanceof Player player && player.isLocalPlayer())
                        : entity instanceof Player) {
                    continue;
                }
                Vec3 offset = toWorld(toBody(entity.position().subtract(deckPos), deckYaw, deckPitch), yaw, pitch);
                Vec3 move = pos.add(offset).subtract(entity.position());
                if (move.lengthSqr() > 1.0E-8) {
                    entity.move(MoverType.SHULKER, move);
                    // lifting it off the deck mustn't count as leaving it
                    entity.setOnGround(true);
                }
                // The local player's view turns with the whale every frame instead (see deckUnderLocalPlayer): their
                // camera shows their yaw as it is, not eased between ticks, so turning it here would step it.
                if (turn != 0 && !(entity instanceof Player)) {
                    entity.setYRot(entity.getYRot() + turn);
                    entity.setYHeadRot(entity.getYHeadRot() + turn);
                    if (entity instanceof LivingEntity living) {
                        living.setYBodyRot(living.yBodyRot + turn);
                    }
                }
                if (entity instanceof Player player) {
                    // a player's own way across the deck turns with it, and they stop short rather than slide off
                    Vec3 motion = player.getDeltaMovement().yRot(-turn * Mth.DEG_TO_RAD);
                    if (player.xxa == 0 && player.zza == 0) {
                        motion = new Vec3(motion.x * 0.5, motion.y, motion.z * 0.5);
                    }
                    player.setDeltaMovement(motion);
                    carryingLocalPlayer = true;
                }
            }
        }
        if (level().isClientSide) {
            if (carryingLocalPlayer) {
                localDeck = this;
            } else if (localDeck == this) {
                localDeck = null;
            }
        }
        deckPos = pos;
        deckYaw = yaw;
        deckPitch = pitch;
    }

    /** The whale this client's own player stands on the deck of, as of its last tick, or null. */
    @Nullable
    public static TamedAerwhale deckUnderLocalPlayer() {
        if (localDeck != null && localDeck.isRemoved()) {
            localDeck = null;
        }
        return localDeck;
    }

    /** What rests on the deck as it lies now, before it moves: anything on the ground with a slab under its feet. */
    private List<Entity> standingOnDeck() {
        AABB area = deck[0].getBoundingBox();
        for (AerwhaleDeck slab : deck) {
            area = area.minmax(slab.getBoundingBox());
        }
        return level().getEntities(this, area.inflate(0, 0.2, 0), entity -> {
            if (entity instanceof PartEntity<?> || entity instanceof TamedAerwhale || entity.isPassenger()
                    || entity.noPhysics || !entity.onGround()) {
                return false;
            }
            AABB box = entity.getBoundingBox();
            AABB feet = new AABB(box.minX, box.minY - 0.1, box.minZ, box.maxX, box.minY + 0.05, box.maxZ);
            for (AerwhaleDeck slab : deck) {
                if (slab.getBoundingBox().intersects(feet)) {
                    return true;
                }
            }
            return false;
        });
    }

    /** A point on the body, in blocks to the side of, above and forward of the whale's origin before it turns. */
    private record BodyPoint(double side, double up, double forward) {
    }

    /** Where {@code point} is, from the origin, on a whale turned to {@code yaw} and pitched to {@code pitch}. */
    private static Vec3 toWorld(BodyPoint point, float yaw, float pitch) {
        // the renderer pitches the model about its origin, so points on the body swing with it
        double p = pitch * Mth.DEG_TO_RAD;
        double y = yaw * Mth.DEG_TO_RAD;
        double along = point.forward() * Math.cos(p) - point.up() * Math.sin(p);
        double height = point.forward() * Math.sin(p) + point.up() * Math.cos(p);
        return new Vec3(-Math.sin(y) * along + Math.cos(y) * point.side(), height,
                Math.cos(y) * along + Math.sin(y) * point.side());
    }

    /** The inverse of {@link #toWorld}. */
    private static BodyPoint toBody(Vec3 offset, float yaw, float pitch) {
        double p = pitch * Mth.DEG_TO_RAD;
        double y = yaw * Mth.DEG_TO_RAD;
        double side = offset.x * Math.cos(y) + offset.z * Math.sin(y);
        double along = -offset.x * Math.sin(y) + offset.z * Math.cos(y);
        return new BodyPoint(side, -along * Math.sin(p) + offset.y * Math.cos(p),
                along * Math.cos(p) + offset.y * Math.sin(p));
    }

    @Override
    protected void addPassenger(Entity passenger) {
        super.addPassenger(passenger);
        if (!level().isClientSide) {
            // whatever it was doing, it stops to carry someone
            coast = 0;
            halt();
        }
    }

    @Override
    protected void removePassenger(Entity passenger) {
        super.removePassenger(passenger);
        if (!level().isClientSide) {
            // a whale left in midair stays where its rider got off, not back where it was first told
            home = blockPosition();
            halt();
        }
    }

    // ---- life and death ----

    /** A wild calf comes and goes like a wild aerwhale; one with an owner stays. */
    @Override
    public boolean removeWhenFarAway(double distance) {
        return getOwnerUUID() == null && super.removeWhenFarAway(distance);
    }

    @Override
    public boolean requiresCustomPersistence() {
        return getOwnerUUID() != null || super.requiresCustomPersistence();
    }

    @Override
    protected void dropEquipment() {
        super.dropEquipment();
        for (int i = 0; i < INVENTORY_SIZE; i++) {
            ItemStack stack = inventory.removeItemNoUpdate(i);
            if (!stack.isEmpty()) {
                spawnAtLocation(stack);
            }
        }
        if (hasChest()) {
            spawnAtLocation(Items.CHEST);
            entityData.set(DATA_CHEST, false);
        }
    }

    @Override
    public void die(DamageSource source) {
        Component message = getCombatTracker().getDeathMessage();
        super.die(source);
        if (dead && !level().isClientSide && level().getGameRules().getBoolean(GameRules.RULE_SHOWDEATHMESSAGES)
                && getOwner() instanceof ServerPlayer owner) {
            owner.sendSystemMessage(message);
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        UUID owner = getOwnerUUID();
        if (owner != null) {
            tag.putUUID("Owner", owner);
        }
        ItemStack saddle = inventory.getItem(SADDLE_SLOT);
        if (!saddle.isEmpty()) {
            tag.put("SaddleItem", saddle.save(registryAccess()));
        }
        tag.putBoolean("Chest", hasChest());
        // not "Items": Deep Aether's cargo, which DeepAetherWhaleCargoMixin drops from every aerwhale, is kept there
        ListTag bag = new ListTag();
        for (int i = 1; i < INVENTORY_SIZE; i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty()) {
                CompoundTag slot = new CompoundTag();
                slot.putByte("Slot", (byte) (i - 1));
                bag.add(stack.save(registryAccess(), slot));
            }
        }
        tag.put("Saddlebag", bag);
        tag.putInt("Age", age);
        tag.putString("Command", command().name());
        if (home != null) {
            tag.put("Home", NbtUtils.writeBlockPos(home));
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setOwnerUUID(tag.hasUUID("Owner") ? tag.getUUID("Owner") : null);
        if (tag.contains("SaddleItem", Tag.TAG_COMPOUND)) {
            inventory.setItem(SADDLE_SLOT, ItemStack.parse(registryAccess(), tag.getCompound("SaddleItem"))
                    .orElse(ItemStack.EMPTY));
        } else if (tag.getBoolean("Saddled")) {
            // saved before the saddle had a slot; a saddle from a mod that's since been removed comes back plain
            Item saddle = BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(tag.getString("Saddle")));
            inventory.setItem(SADDLE_SLOT, new ItemStack(saddle == Items.AIR ? Items.SADDLE : saddle));
        } else {
            inventory.setItem(SADDLE_SLOT, ItemStack.EMPTY);
        }
        entityData.set(DATA_CHEST, tag.getBoolean("Chest"));
        ListTag bag = tag.getList("Saddlebag", Tag.TAG_COMPOUND);
        for (int i = 0; i < bag.size(); i++) {
            CompoundTag slot = bag.getCompound(i);
            int index = (slot.getByte("Slot") & 255) + 1;
            if (index < INVENTORY_SIZE) {
                inventory.setItem(index, ItemStack.parse(registryAccess(), slot).orElse(ItemStack.EMPTY));
            }
        }
        setAge(tag.getInt("Age"));
        entityData.set(DATA_COMMAND, (byte) AerwhaleCommand.byName(tag.getString("Command")).ordinal());
        home = NbtUtils.readBlockPos(tag, "Home").orElse(null);
        // whales saved before they were slowed kept the wild speed among their attributes
        getAttribute(Attributes.FLYING_SPEED).setBaseValue(FLYING_SPEED);
    }

    // ---- helpers ----

    private void particles(ParticleOptions type, int count) {
        if (level() instanceof ServerLevel level) {
            level.sendParticles(type, getX(), getY() + getBbHeight() * 0.9, getZ(), count,
                    getBbWidth() * 0.5, getBbHeight() * 0.25, getBbWidth() * 0.5, 0.02);
        }
    }

    private WhaleMoveControl control() {
        return (WhaleMoveControl) moveControl;
    }

    private void flyTo(double x, double y, double z, double speed) {
        control().cruising = false;
        control().setWantedPosition(x, y, z, speed);
    }

    private void halt() {
        if (moveControl instanceof WhaleMoveControl control) {
            control.halt();
        }
    }

    /**
     * Swims about {@code target} at its slowest, {@code height} above it, circling lazily since it can't stop. With
     * {@code target} overhead it holds its course instead, rather than spinning round underneath them.
     */
    private void loiter(Entity target, double height) {
        if (overhead(this, target)) {
            halt();
        } else {
            flyTo(target.getX(), target.getY() + height, target.getZ(), 0);
        }
    }

    /** The most an aerwhale going at {@code speed} turns in a tick, in degrees, to circle no tighter than it can. */
    public static float maxTurn(double speed) {
        return (float) (Math.max(speed, MIN_SWIM_SPEED) / MIN_TURN_RADIUS * Mth.RAD_TO_DEG);
    }

    /**
     * Whether {@code entity} is just above {@code whale}: on its back, or about to land there. A whale following them
     * would only turn about under their feet, so it pays them no mind.
     */
    public static boolean overhead(Entity whale, Entity entity) {
        AABB box = whale.getBoundingBox();
        double dx = entity.getX() - whale.getX();
        double dz = entity.getZ() - whale.getZ();
        double reach = whale.getBbWidth() / 2 + OVERHEAD_REACH;
        return entity.getY() > box.getCenter().y && entity.getY() < box.maxY + OVERHEAD_REACH
                && dx * dx + dz * dz < reach * reach;
    }

    private boolean teleportNear(LivingEntity owner) {
        for (int i = 0; i < 12; i++) {
            double x = owner.getX() + random.nextInt(13) - 6;
            double y = Math.max(owner.getY() + 3 + random.nextInt(6), floorY());
            double z = owner.getZ() + random.nextInt(13) - 6;
            if (!level().hasChunkAt(BlockPos.containing(x, y, z))) {
                continue;
            }
            AABB box = getBoundingBox().move(x - getX(), y - getY(), z - getZ());
            if (level().noCollision(this, box) && !level().containsAnyLiquid(box)) {
                moveTo(x, y, z, getYRot(), getXRot());
                halt();
                return true;
            }
        }
        return false;
    }

    private void breed(TamedAerwhale partner) {
        if (!(level() instanceof ServerLevel level)) {
            return;
        }
        TamedAerwhale calf = AARegistries.TAMED_AERWHALE.get().create(level);
        if (calf == null) {
            return;
        }
        UUID owner = loveCause != null ? loveCause : getOwnerUUID();
        Player breeder = loveCause != null ? level.getPlayerByUUID(loveCause) : null;
        Vec3 middle = position().add(partner.position()).scale(0.5);
        calf.setOwnerUUID(owner);
        calf.setAge(-AACommonConfig.AERWHALE_GROWTH_TIME.get());
        calf.moveTo(middle.x, middle.y, middle.z, getYRot(), 0);
        calf.setYRotData(getYRot());
        // what it'll do once it's grown; a calf keeps to its parents until then
        calf.setCommand(command());
        level.addFreshEntity(calf);

        int cooldown = AACommonConfig.AERWHALE_BREEDING_COOLDOWN.get();
        setAge(cooldown);
        partner.setAge(cooldown);
        resetLove();
        partner.resetLove();
        calf.particles(ParticleTypes.HEART, 7);
        if (breeder != null) {
            breeder.awardStat(Stats.ANIMALS_BRED);
        }
        if (level.getGameRules().getBoolean(GameRules.RULE_DOMOBLOOT)) {
            level.addFreshEntity(new ExperienceOrb(level, middle.x, middle.y, middle.z, random.nextInt(7) + 1));
        }
    }

    /**
     * Flies toward its wanted position the way the Aether's aerwhale does, turning and pitching gradually, but at the
     * speed it's asked for and easing off as it arrives. It never turns tighter than {@link #MIN_TURN_RADIUS}, and never
     * goes slower than {@link #MIN_SWIM_SPEED}: with nowhere to go it swims slowly on, level. While {@link #cruising} it
     * steers exactly as a wild aerwhale does instead, through the Aether's own move control.
     */
    private final class WhaleMoveControl extends MoveControl {
        private final MoveControl wild;
        /** Whether the wild aerwhale's steering is in charge. Set by the cruise goal, cleared by any other order. */
        boolean cruising;
        private double currentSpeed;

        WhaleMoveControl(MoveControl wild) {
            super(TamedAerwhale.this);
            this.wild = wild;
        }

        void halt() {
            operation = Operation.WAIT;
            cruising = false;
        }

        @Override
        public void setWantedPosition(double x, double y, double z, double speed) {
            // never down into the void
            y = Math.max(y, floorY());
            super.setWantedPosition(x, y, z, speed);
            wild.setWantedPosition(x, y, z, speed);
        }

        @Override
        public void tick() {
            TamedAerwhale whale = TamedAerwhale.this;
            if (whale.getControllingPassenger() != null) {
                currentSpeed = whale.getDeltaMovement().length();
                return;
            }
            if (coast > 0) {
                // a coasting whale keeps going the way its pilot left it, slowing no further than it swims
                Vec3 motion = whale.getDeltaMovement();
                if (motion.length() < MIN_SWIM_SPEED) {
                    whale.setDeltaMovement(heading(whale.getYRot(), whale.getXRotData()).scale(MIN_SWIM_SPEED));
                }
                currentSpeed = whale.getDeltaMovement().length();
                return;
            }
            if (cruising && operation == Operation.MOVE_TO && !whale.isVehicle()) {
                wild.tick();
                if (!wild.hasWanted()) {
                    operation = Operation.WAIT;
                }
                if (deckLoaded) {
                    // the wild steering sets its speed afresh each tick, so this doesn't compound
                    Vec3 motion = whale.getDeltaMovement();
                    double slowest = MIN_SWIM_SPEED / Math.max(motion.length(), 1.0E-4);
                    whale.setDeltaMovement(motion.scale(Math.max(DECK_SPEED, slowest)));
                    float pitch = Mth.clamp(whale.getXRot(), -MAX_DECK_PITCH, MAX_DECK_PITCH);
                    whale.setXRot(pitch);
                    whale.setXRotData(pitch);
                }
                currentSpeed = whale.getDeltaMovement().length();
                return;
            }
            float maxPitch = deckLoaded ? MAX_DECK_PITCH : MAX_WANDERING_PITCH;
            // with nowhere to go it swims straight on, levelling out, at its slowest
            float yawTarget = whale.getYRot();
            float pitchTarget = 0;
            double wanted = MIN_SWIM_SPEED;
            if (operation == Operation.MOVE_TO) {
                double dx = wantedX - whale.getX();
                double dy = wantedY - whale.getY();
                double dz = wantedZ - whale.getZ();
                double horizontal = Math.sqrt(dx * dx + dz * dz);
                double distance = Math.sqrt(horizontal * horizontal + dy * dy);
                if (distance < 1.5) {
                    operation = Operation.WAIT;
                } else {
                    yawTarget = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
                    pitchTarget = Mth.clamp((float) (Mth.atan2(dy, horizontal) * Mth.RAD_TO_DEG), -maxPitch, maxPitch);
                    // slowing down to arrive
                    wanted = Math.max(MIN_SWIM_SPEED, whale.getAttributeValue(Attributes.FLYING_SPEED) * speedModifier
                            * Mth.clamp(distance / 8, 0.25, 1) * (deckLoaded ? DECK_SPEED : 1));
                }
            }
            currentSpeed += Mth.clamp(wanted - currentSpeed, -0.02, 0.01);
            currentSpeed = Math.max(currentSpeed, MIN_SWIM_SPEED);
            float turn = Math.min(3.0F, maxTurn(currentSpeed));
            Vec3 ahead = heading(whale.getYRot(), 0);
            if (blocked(ahead)) {
                // rise over whatever is in the way, or if it's walled in above too, slow right down and turn away
                if (blocked(new Vec3(ahead.x, 1, ahead.z).normalize())) {
                    float side = Mth.degreesDifference(whale.getYRot(), yawTarget) < 0 ? -1 : 1;
                    yawTarget = whale.getYRot() + side * 90;
                    currentSpeed = Math.max(MIN_SWIM_SPEED, currentSpeed - 0.02);
                    turn = 3.0F;
                } else {
                    pitchTarget = maxPitch;
                }
            }
            float yaw = Mth.approachDegrees(whale.getYRot(), yawTarget, turn);
            float pitch = Mth.approach(whale.getXRotData(), pitchTarget, 1.5F);

            whale.setDeltaMovement(heading(yaw, pitch).scale(currentSpeed));
            whale.setYRot(yaw);
            whale.setYRotData(yaw);
            whale.setYBodyRot(yaw);
            whale.setYHeadRot(yaw);
            whale.setXRot(pitch);
            whale.setXRotData(pitch);
        }

        private static Vec3 heading(float yaw, float pitch) {
            double yawRad = yaw * Mth.DEG_TO_RAD;
            double pitchRad = pitch * Mth.DEG_TO_RAD;
            return new Vec3(-Math.sin(yawRad) * Math.cos(pitchRad), Math.sin(pitchRad), Math.cos(yawRad) * Math.cos(pitchRad));
        }

        /**
         * Whether flying a few blocks along {@code direction} would hit something. The box is a little smaller than the
         * whale, so skimming past a block isn't a collision.
         */
        private boolean blocked(Vec3 direction) {
            AABB box = getBoundingBox().deflate(0.4);
            for (int i = 1; i <= 3; i++) {
                box = box.move(direction);
                if (!level().noCollision(TamedAerwhale.this, box)) {
                    return true;
                }
            }
            return false;
        }
    }

    /** Stay and follow, for an adult that isn't ridden. Wandering is the {@link CruiseGoal}'s. */
    private final class CommandGoal extends Goal {
        /** Whether a follower is on its way to its owner rather than waiting near them. */
        private boolean following;
        private int repath;

        CommandGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return !isVehicle() && !isBaby() && command() != AerwhaleCommand.WANDER;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void stop() {
            following = false;
            repath = 0;
        }

        @Override
        public void tick() {
            if (home == null) {
                home = blockPosition();
            }
            switch (command()) {
                case STAY -> stay();
                case FOLLOW -> follow();
                default -> {
                }
            }
        }

        /** Circles lazily round where it was told to stay, aiming a little ahead on the circle so it never stops. */
        private void stay() {
            Vec3 centre = Vec3.atBottomCenterOf(home);
            double dx = getX() - centre.x;
            double dz = getZ() - centre.z;
            if (dx * dx + dz * dz > (STAY_RADIUS + 6) * (STAY_RADIUS + 6) || Math.abs(getY() - centre.y) > 6) {
                flyTo(centre.x, centre.y, centre.z, 0.6);
                return;
            }
            double angle = Math.atan2(dz, dx) + STAY_LEAD;
            flyTo(centre.x + Math.cos(angle) * STAY_RADIUS, centre.y, centre.z + Math.sin(angle) * STAY_RADIUS, 1.0);
        }

        /**
         * As a wolf follows: sets off once its owner is far enough away, settles to circling about them when close
         * again, and catches up by teleporting when left far behind. Its owner on its back is close enough.
         */
        private void follow() {
            LivingEntity owner = getOwner();
            if (owner == null || owner.level() != level() || owner.isSpectator()) {
                if (following) {
                    halt();
                    following = false;
                }
                return;
            }
            double distance = distanceTo(owner);
            int teleport = AACommonConfig.AERWHALE_FOLLOW_TELEPORT.get();
            if (teleport > 0 && distance > teleport && !isLeashed() && teleportNear(owner)) {
                following = false;
                return;
            }
            if (!following && distance > FOLLOW_START) {
                following = true;
                repath = 0;
            } else if (following && (distance < FOLLOW_STOP || overhead(TamedAerwhale.this, owner))) {
                following = false;
            }
            if (following) {
                if (--repath <= 0) {
                    repath = 10;
                    flyTo(owner.getX(), owner.getY() + 3, owner.getZ(), Mth.clamp(distance / FOLLOW_START, 1, 2));
                }
            } else {
                loiter(owner, 3);
            }
        }
    }

    /**
     * Comes to its owner from up to {@link #SWET_RANGE} away while they hold a gummy swet, whatever it was told to do,
     * and circles slowly about them to be fed. Once the swet is put away it goes back to its orders.
     */
    private final class SwetTemptGoal extends Goal {
        @Nullable
        private Player owner;
        /** Whether it has come close and is circling them, rather than on its way. */
        private boolean near;
        private int repath;

        SwetTemptGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            owner = getOwner() instanceof Player player && tempts(player, SWET_RANGE) ? player : null;
            return owner != null;
        }

        @Override
        public boolean canContinueToUse() {
            return owner != null && owner.isAlive() && tempts(owner, SWET_RANGE * 1.5);
        }

        private boolean tempts(Player player, double range) {
            return !isVehicle() && player.level() == level() && !player.isSpectator()
                    && distanceToSqr(player) < range * range
                    && (swetHealing(player.getMainHandItem()) > 0 || swetHealing(player.getOffhandItem()) > 0);
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void start() {
            near = false;
            repath = 0;
        }

        @Override
        public void stop() {
            owner = null;
            halt();
        }

        @Override
        public void tick() {
            double distance = distanceTo(owner);
            if (near && distance > FOLLOW_STOP + 3) {
                near = false;
                repath = 0;
            } else if (!near && (distance < FOLLOW_STOP || overhead(TamedAerwhale.this, owner))) {
                near = true;
            }
            if (near) {
                loiter(owner, 3);
            } else if (--repath <= 0) {
                repath = 10;
                flyTo(owner.getX(), owner.getY() + 3, owner.getZ(), Mth.clamp(distance / FOLLOW_START, 1, 2));
            }
        }
    }

    /**
     * Cruises at random as a wild aerwhale does, through the Aether's own goal and steering: what a wanderer does,
     * and a calf when it isn't keeping up with an adult.
     */
    private final class CruiseGoal extends Goal {
        private final Goal course = new Aerwhale.SetTravelCourseGoal(TamedAerwhale.this);

        CruiseGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return !isVehicle() && (isBaby() || command() == AerwhaleCommand.WANDER)
                    && (!control().cruising || course.canUse());
        }

        @Override
        public boolean canContinueToUse() {
            return false;
        }

        @Override
        public void start() {
            control().cruising = true;
            course.start();
        }
    }

    /** A calf keeps close to an adult aerwhale, wild or tamed, preferring one of its own owner's. */
    private final class FollowAdultGoal extends Goal {
        @Nullable
        private Aerwhale adult;
        private int repath;

        FollowAdultGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (!isBaby() || isVehicle()) {
                return false;
            }
            UUID owner = getOwnerUUID();
            adult = level().getEntitiesOfClass(Aerwhale.class, getBoundingBox().inflate(16, 8, 16),
                            whale -> whale != TamedAerwhale.this && whale.isAlive() && !whale.isBaby())
                    .stream().min(Comparator.comparingDouble(whale -> distanceToSqr(whale)
                            + (owner != null && whale instanceof TamedAerwhale tamed && owner.equals(tamed.getOwnerUUID())
                            ? 0 : 1.0E4))).orElse(null);
            return adult != null && distanceToSqr(adult) > 64;
        }

        @Override
        public boolean canContinueToUse() {
            return isBaby() && !isVehicle() && adult != null && adult.isAlive() && distanceToSqr(adult) > 25
                    && distanceToSqr(adult) < 32 * 32;
        }

        @Override
        public void start() {
            repath = 0;
        }

        @Override
        public void stop() {
            adult = null;
            halt();
        }

        @Override
        public void tick() {
            if (--repath <= 0) {
                repath = 5;
                flyTo(adult.getX(), adult.getY(), adult.getZ(), 1.5);
            }
        }
    }

    /** Two whales in love fly together, circle each other for a few seconds, and a calf appears between them. */
    private final class BreedGoal extends Goal {
        @Nullable
        private TamedAerwhale partner;
        /** Ticks spent on this courtship, to give up on a partner it can't reach. */
        private int time;
        /** Ticks spent close to the partner. */
        private int together;

        BreedGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (!isInLove() || isVehicle()) {
                return false;
            }
            partner = level().getEntitiesOfClass(TamedAerwhale.class, getBoundingBox().inflate(24),
                            whale -> whale != TamedAerwhale.this && whale.isInLove() && !whale.isBaby())
                    .stream().min(Comparator.comparingDouble(TamedAerwhale.this::distanceToSqr)).orElse(null);
            return partner != null;
        }

        @Override
        public boolean canContinueToUse() {
            return partner != null && partner.isAlive() && partner.isInLove() && isInLove() && !isVehicle()
                    && time < 400;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void stop() {
            partner = null;
            time = 0;
            together = 0;
        }

        @Override
        public void tick() {
            time++;
            if (distanceToSqr(partner) > 36) {
                flyTo(partner.getX(), partner.getY(), partner.getZ(), 1.0);
                return;
            }
            loiter(partner, 0);
            // both parents run this goal; whichever gets there first breeds and ends the other's courtship
            if (++together >= 60 && isInLove() && partner.isInLove()) {
                breed(partner);
            }
        }
    }
}
