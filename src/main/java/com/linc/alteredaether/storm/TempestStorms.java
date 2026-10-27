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
package com.linc.alteredaether.storm;

import com.linc.alteredaether.AATags;
import com.linc.alteredaether.AlteredAether;
import com.linc.alteredaether.config.AACommonConfig;
import com.linc.alteredaether.registry.AARegistries;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityStruckByLightningEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Lightning storms over Tempest Peaks. Every {@code check_interval} ticks, each player standing in a
 * {@code #altered_aether:has_storms} biome may draw a strike, with a chance that follows the drifting
 * {@link StormField}. A strike lands on the highest of several spots around the player, so peaks and spires draw it,
 * or now and then on the player; a lightning rod in range takes it instead, as in vanilla. Sparks crackle and thunder
 * rumbles at the spot for a moment first, then the bolt lands with an explosion and leaves a scorch.
 *
 * <p>Players also draw strikes on themselves, storm or calm: wearing overworld-metal armour or carrying an
 * overworld-metal tool in the hotbar ({@code #altered_aether:attracts_lightning}) is a good chance of a strike each roll,
 * and standing on copper ({@code #altered_aether:charged_copper}) without boots is a sure one. Either way a warning
 * crackles first, and the bolt follows the player a second later, unless they have dropped the cause by then. These
 * strikes are the bolt alone: no blast or scorch, and no rod takes them, since the copper spires all wear one.
 *
 * <p>Only loaded chunks are struck, strikes per tick are capped, and the whole system switches itself off after
 * repeated errors rather than risk the world.
 */
@EventBusSubscriber(modid = AlteredAether.MOD_ID)
public final class TempestStorms {
    private static final ResourceKey<Level> THE_AETHER =
            ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath("aether", "the_aether"));
    private static final int CANDIDATES = 6, ERROR_LIMIT = 8;

    /** Why a strike is aimed at a player, checked again as it lands. */
    private enum Cause {
        METAL, COPPER
    }

    /**
     * A strike on its way: sparks and rumbling at {@code target} until the countdown ends. One aimed at a
     * {@code victim} follows them instead, and lands only if its {@code cause} still holds.
     */
    private record PendingStrike(BlockPos target, boolean rod, int[] ticks, @Nullable ServerPlayer victim,
                                 @Nullable Cause cause) {
        PendingStrike(BlockPos target, boolean rod, int[] ticks) {
            this(target, rod, ticks, null, null);
        }
    }

    private static final List<PendingStrike> PENDING = new ArrayList<>();
    /** Bolts called down by a player's overworld metal, and that player; see {@link #onStruck}. */
    private static final Map<LightningBolt, ServerPlayer> METAL_BOLTS = new WeakHashMap<>();
    private static final ResourceLocation THUNDERSTRUCK = AlteredAether.id("thunderstruck");
    @Nullable
    private static StormField field;
    private static int errors;
    private static boolean disabled;
    /** A storm intensity to use everywhere instead of the field's, for the debug tour; negative for none. */
    private static volatile double forced = -1;

    public static void force(double intensity) {
        forced = intensity;
    }

    /** The storm intensity at a point in the Aether, for the debug tools; 0 when there is no storm field. */
    public static double intensityAt(ServerLevel level, double x, double z) {
        return field == null ? 0 : field.intensity(x, z, level.getGameTime());
    }

    @SubscribeEvent
    public static void onLevelLoad(LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level && level.dimension().equals(THE_AETHER)) {
            field = new StormField(level.getSeed());
            PENDING.clear();
            errors = 0;
            disabled = false;
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level && level.dimension().equals(THE_AETHER)) {
            field = null;
            PENDING.clear();
        }
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (disabled || field == null || !(event.getLevel() instanceof ServerLevel level)
                || !level.dimension().equals(THE_AETHER)) {
            return;
        }
        try {
            tick(level, field);
        } catch (RuntimeException e) {
            fail(e);
        }
    }

    private static void tick(ServerLevel level, StormField storms) {
        boolean enabled = AACommonConfig.STORMS_ENABLED.get();
        long time = level.getGameTime();
        advance(level);
        RandomSource random = level.getRandom();
        int interval = AACommonConfig.STORM_CHECK_INTERVAL.get();
        int budget = AACommonConfig.STORM_MAX_STRIKES_PER_TICK.get();
        for (ServerPlayer player : level.players()) {
            double force = forced;
            boolean stormy = enabled && !player.isSpectator()
                    && (force >= 0 || level.getBiome(player.blockPosition()).is(AATags.HAS_STORMS));
            double intensity = !stormy ? 0 : force >= 0 ? force : storms.intensity(player.getX(), player.getZ(), time);
            if (time % 20 == player.getId() % 20) {
                PacketDistributor.sendToPlayer(player, new StormPayload((float) intensity));
            }
            if (enabled && budget > 0 && !player.isSpectator() && !player.isCreative()
                    && level.getBiome(player.blockPosition()).is(AATags.HAS_STORMS) && !targeted(player)) {
                Cause cause = holds(level, player, Cause.COPPER) ? Cause.COPPER
                        : (time + player.getId()) % interval == 0 && holds(level, player, Cause.METAL)
                        && random.nextDouble() < AACommonConfig.STORM_METAL_STRIKE_CHANCE.get() ? Cause.METAL : null;
                if (cause != null) {
                    budget--;
                    warn(level, player, cause, random);
                    continue;
                }
            }
            if (!stormy || budget <= 0 || (time + player.getId()) % interval != 0
                    || random.nextDouble() >= AACommonConfig.STORM_STRIKE_CHANCE.get() * intensity) {
                continue;
            }
            PendingStrike strike = aim(level, player, random);
            if (strike != null) {
                budget--;
                if (strike.ticks()[0] <= 0) {
                    land(level, strike);
                } else {
                    PENDING.add(strike);
                    level.playSound(null, strike.target(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER,
                            0.7f, 0.55f + random.nextFloat() * 0.1f);
                }
            }
        }
    }

    /** Ticks down the warnings, crackling sparks over each spot, and lands the strikes whose time has come. */
    private static void advance(ServerLevel level) {
        Iterator<PendingStrike> it = PENDING.iterator();
        while (it.hasNext()) {
            PendingStrike strike = it.next();
            int left = --strike.ticks()[0];
            ServerPlayer victim = strike.victim();
            if (victim != null && (victim.isRemoved() || !victim.isAlive() || victim.level() != level)) {
                it.remove();
                continue;
            }
            BlockPos at = victim != null ? victim.blockPosition() : strike.target();
            if (!level.isLoaded(at)) {
                it.remove();
                continue;
            }
            if (left % 3 == 0) {
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.getX() + 0.5, at.getY() + 0.3, at.getZ() + 0.5,
                        5, 0.5, 0.8, 0.5, 0.05);
            }
            if (left <= 0) {
                it.remove();
                if (victim == null) {
                    land(level, strike);
                } else if (holds(level, victim, strike.cause())) {
                    strike(level, victim, strike.cause());
                }
            }
        }
    }

    /** Whether a strike is already on its way to this player. */
    private static boolean targeted(ServerPlayer player) {
        for (PendingStrike strike : PENDING) {
            if (strike.victim() == player) {
                return true;
            }
        }
        return false;
    }

    /** Whether the player is still drawing lightning for this reason. */
    private static boolean holds(ServerLevel level, ServerPlayer player, Cause cause) {
        return switch (cause) {
            case COPPER -> AACommonConfig.STORM_COPPER_STRIKES.get() && player.onGround()
                    && player.getItemBySlot(EquipmentSlot.FEET).isEmpty()
                    && level.getBlockState(player.getOnPos()).is(AATags.CHARGED_COPPER);
            case METAL -> AACommonConfig.STORM_METAL_STRIKE_CHANCE.get() > 0 && carriesMetal(player);
        };
    }

    /** Overworld metal worn, in the off hand, or anywhere on the hotbar. */
    private static boolean carriesMetal(ServerPlayer player) {
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot != EquipmentSlot.MAINHAND && slot != EquipmentSlot.BODY
                    && player.getItemBySlot(slot).is(AATags.ATTRACTS_LIGHTNING)) {
                return true;
            }
        }
        Inventory inventory = player.getInventory();
        for (int i = 0; i < Inventory.getSelectionSize(); i++) {
            if (inventory.getItem(i).is(AATags.ATTRACTS_LIGHTNING)) {
                return true;
            }
        }
        return false;
    }

    /** The warning crackle at a player, then their strike a moment later. */
    private static void warn(ServerLevel level, ServerPlayer player, Cause cause, RandomSource random) {
        level.playSound(null, player.getX(), player.getY(), player.getZ(), AARegistries.INCOMING_LIGHTNING.get(),
                SoundSource.WEATHER, 1.0f, 0.95f + random.nextFloat() * 0.1f);
        int ticks = AACommonConfig.STORM_PLAYER_WARNING_TICKS.get();
        if (ticks <= 0) {
            strike(level, player, cause);
        } else {
            PENDING.add(new PendingStrike(player.blockPosition(), false, new int[]{ticks}, player, cause));
        }
    }

    /** A bolt straight down on the player, without the blast and scorch of a storm strike. */
    private static void strike(ServerLevel level, ServerPlayer player, Cause cause) {
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt != null) {
            bolt.moveTo(player.position());
            if (cause == Cause.METAL) {
                METAL_BOLTS.put(bolt, player);
            }
            level.addFreshEntity(bolt);
        }
    }

    /** A bolt drawn by a player's overworld metal that hits them earns the Thunderstruck! advancement. */
    @SubscribeEvent
    public static void onStruck(EntityStruckByLightningEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && METAL_BOLTS.get(event.getLightning()) == player) {
            AdvancementHolder advancement = player.server.getAdvancements().get(THUNDERSTRUCK);
            if (advancement != null) {
                player.getAdvancements().award(advancement, "struck");
            }
        }
    }

    /**
     * Picks where a strike lands: usually the highest of a few spots in a ring round the player, so the peaks and the
     * spires take most of them, and now and then the player. A rod in range wins over either.
     */
    @Nullable
    private static PendingStrike aim(ServerLevel level, ServerPlayer player, RandomSource random) {
        BlockPos target = null;
        if (random.nextDouble() < AACommonConfig.STORM_DIRECT_HIT_CHANCE.get()) {
            target = player.blockPosition();
        } else {
            int min = AACommonConfig.STORM_MIN_RADIUS.get(), max = Math.max(min, AACommonConfig.STORM_MAX_RADIUS.get());
            for (int i = 0; i < CANDIDATES; i++) {
                double angle = random.nextDouble() * Mth.TWO_PI;
                double r = min + random.nextDouble() * (max - min);
                int x = Mth.floor(player.getX() + Math.cos(angle) * r), z = Mth.floor(player.getZ() + Math.sin(angle) * r);
                if (!level.isLoaded(new BlockPos(x, 0, z))) {
                    continue;
                }
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
                // Open sky all the way down: nothing to strike.
                if (y <= level.getMinBuildHeight() + 1) {
                    continue;
                }
                if (target == null || y > target.getY()) {
                    target = new BlockPos(x, y, z);
                }
            }
        }
        if (target == null) {
            return null;
        }
        boolean rod = false;
        if (AACommonConfig.STORM_LIGHTNING_RODS.get()) {
            var found = level.findLightningRod(target);
            if (found.isPresent()) {
                target = found.get();
                rod = true;
            }
        }
        return new PendingStrike(target, rod, new int[]{AACommonConfig.STORM_WARNING_TICKS.get()});
    }

    private static void land(ServerLevel level, PendingStrike strike) {
        BlockPos at = strike.target();
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt == null) {
            return;
        }
        bolt.moveTo(Vec3.atBottomCenterOf(at));
        level.addFreshEntity(bolt);
        // A rod takes the strike cleanly, as in vanilla: no blast, no scorch.
        if (strike.rod()) {
            return;
        }
        boolean griefing = !AACommonConfig.STORM_RESPECT_MOB_GRIEFING.get()
                || level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
        float power = AACommonConfig.STORM_EXPLOSION_POWER.get().floatValue();
        if (power > 0) {
            level.explode(null, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, power,
                    AACommonConfig.STORM_EXPLOSION_FIRE.get(),
                    griefing ? Level.ExplosionInteraction.TNT : Level.ExplosionInteraction.NONE);
        }
        if (griefing && AACommonConfig.STORM_SCORCH.get()) {
            scorch(level, at, level.getRandom());
        }
    }

    /** Blackens the ground round a strike: the top of natural rock and soil turns to gravel and blackstone. */
    private static void scorch(ServerLevel level, BlockPos at, RandomSource random) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                if (dx * dx + dz * dz > 9 || random.nextFloat() > 0.55f) {
                    continue;
                }
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, at.getX() + dx, at.getZ() + dz) - 1;
                if (Math.abs(y - at.getY()) > 6) {
                    continue;
                }
                pos.set(at.getX() + dx, y, at.getZ() + dz);
                BlockState state = level.getBlockState(pos);
                if (scorchable(level, pos, state)) {
                    level.setBlockAndUpdate(pos, (random.nextBoolean() ? Blocks.GRAVEL : Blocks.BLACKSTONE)
                            .defaultBlockState());
                }
            }
        }
    }

    /** Natural ground only: plain full blocks dug by hand or pickaxe, nothing with a block entity or a use. */
    private static boolean scorchable(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.isAir() || state.hasBlockEntity() || !state.isCollisionShapeFullBlock(level, pos)
                || state.is(Blocks.GRAVEL) || state.is(Blocks.BLACKSTONE)) {
            return false;
        }
        float hardness = state.getDestroySpeed(level, pos);
        return hardness >= 0 && hardness <= 3 && (state.is(BlockTags.DIRT) || state.is(BlockTags.SAND)
                || state.is(AATags.SCORCHABLE));
    }

    private static void fail(RuntimeException e) {
        errors++;
        AlteredAether.LOGGER.error("Tempest storms failed ({} of {})", errors, ERROR_LIMIT, e);
        if (errors >= ERROR_LIMIT) {
            disabled = true;
            PENDING.clear();
            AlteredAether.LOGGER.error("Tempest storms are off until the server restarts, after repeated errors.");
        }
    }

    private TempestStorms() {
    }
}
