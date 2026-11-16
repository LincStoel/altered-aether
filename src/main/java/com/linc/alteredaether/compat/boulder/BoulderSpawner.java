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
package com.linc.alteredaether.compat.boulder;

import com.linc.alteredaether.AlteredAether;
import com.linc.alteredaether.config.AACommonConfig;
import com.linc.alteredaether.worldgen.boulder.BoulderOres;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Lets boulders go now and then near players in the Aether: each player has a timer of about the configured interval,
 * and when it runs out a spot is chosen at some distance from them, anywhere that is loaded,
 * clear of structures and of claimed land (see {@link BoulderProtection}). A couple of seconds before the boulder is
 * made at the top of the world there is a rumble and grit falls, so the people nearby have a chance to look up.
 */
final class BoulderSpawner {
    private static final ResourceKey<Level> THE_AETHER =
            ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath("aether", "the_aether"));
    /** How far from a player a boulder may be dropped, in blocks. */
    private static final int MIN_DISTANCE = 24;
    private static final int MAX_DISTANCE = 64;
    /** Nobody may stand this close to the spot (flat distance), so it never falls on someone unwarned. */
    private static final int KEEP_CLEAR = 16;
    /** From the warning to the boulder being made. */
    private static final int WARNING_TICKS = 50;
    /** How soon to try again when a spot couldn't be found or there were already too many. */
    private static final int RETRY_TICKS = 20 * 30;
    /** A boulder counts toward the cap for this long after it starts. */
    private static final int RECENT_TICKS = 20 * 60 * 5;
    /** How much of a boulder is ore. */
    private static final float ORE_CHANCE = 0.2f;

    /** Set by the checks, which run in a dimension that is not the Aether. */
    static volatile boolean anywhere;

    private record Pending(ServerLevel level, BlockPos origin, BlockPos ground, long at) {
    }

    /** When each player is next due a boulder, in game time. */
    private static final Map<UUID, Long> NEXT = new HashMap<>();
    private static final List<Pending> PENDING = new ArrayList<>();

    static void register() {
        NeoForge.EVENT_BUS.addListener(BoulderSpawner::onLevelTick);
    }

    private static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !anywhere && !level.dimension().equals(THE_AETHER)) {
            return;
        }
        long now = level.getGameTime();
        for (Iterator<Pending> it = PENDING.iterator(); it.hasNext(); ) {
            Pending pending = it.next();
            if (pending.level != level) {
                continue;
            }
            if (now >= pending.at) {
                it.remove();
                drop(pending);
            } else if (now % 4 == 0) {
                grit(level, pending.origin);
            }
        }
        double minutes = AACommonConfig.BOULDER_INTERVAL_MINUTES.get();
        if (minutes <= 0 || now % 20 != 0 || !FallingBoulders.active()) {
            return;
        }
        RandomSource random = level.getRandom();
        long interval = (long) (minutes * 60 * 20);
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator()) {
                continue;
            }
            Long due = NEXT.get(player.getUUID());
            if (due == null) {
                // not at once on arriving: somewhere in the first part of an interval
                NEXT.put(player.getUUID(), now + (long) (interval * (0.25 + random.nextDouble() * 0.75)));
                continue;
            }
            if (now < due) {
                continue;
            }
            boolean sent = attempt(level, player, random);
            NEXT.put(player.getUUID(), now + (sent ? (long) (interval * (0.5 + random.nextDouble())) : RETRY_TICKS));
        }
    }

    /** Finds a spot near the player and announces a boulder there; false if none would do. */
    static boolean attempt(ServerLevel level, ServerPlayer player, RandomSource random) {
        int recent = BoulderRecords.get(level).recent(level.getGameTime(), RECENT_TICKS) + countPending(level);
        if (recent >= AACommonConfig.BOULDER_MAX_ACTIVE.get()) {
            AlteredAether.LOGGER.info("Falling boulders: not announcing for {}: {} already active or pending",
                    player.getName().getString(), recent);
            return false;
        }
        int[] why = new int[5];
        for (int tries = 0; tries < 12; tries++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double distance = MIN_DISTANCE + random.nextDouble() * (MAX_DISTANCE - MIN_DISTANCE);
            int x = (int) Math.floor(player.getX() + Math.cos(angle) * distance);
            int z = (int) Math.floor(player.getZ() + Math.sin(angle) * distance);
            if (!loaded(level, x, z)) {
                why[0]++;
                continue;
            }
            int surface = Math.max(level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z),
                    level.getMinBuildHeight() + 1);
            BlockPos landing = new BlockPos(x, surface, z);
            if (nearPlayer(level, x, z)) {
                why[2]++;
                continue;
            }
            if (!BoulderProtection.allowedAround(level, landing)) {
                why[3]++;
                continue;
            }
            BlockPos origin = new BlockPos(x, level.getMaxBuildHeight() - 24, z);
            if (!level.getBlockState(origin).isAir()) {
                why[4]++;
                continue;
            }
            PENDING.add(new Pending(level, origin, landing, level.getGameTime() + WARNING_TICKS));
            rumble(level, origin);
            AlteredAether.LOGGER.info("Falling boulders: announced one at {} for {}", origin, player.getName().getString());
            return true;
        }
        AlteredAether.LOGGER.info("Falling boulders: no spot near {} in 12 tries (chunks not loaded {}, "
                + "too close to a player {}, structure {}, sky not clear {})", player.getName().getString(), why[0],
                why[2], why[3], why[4]);
        return false;
    }

    private static int countPending(ServerLevel level) {
        int count = 0;
        for (Pending pending : PENDING) {
            if (pending.level == level) {
                count++;
            }
        }
        return count;
    }

    /** The chunk and its neighbours are loaded, so the fall and the landing are in loaded land. */
    private static boolean loaded(ServerLevel level, int x, int z) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (level.getChunkSource().getChunkNow((x >> 4) + dx, (z >> 4) + dz) == null) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean nearPlayer(ServerLevel level, int x, int z) {
        for (ServerPlayer player : level.players()) {
            double dx = player.getX() - x, dz = player.getZ() - z;
            if (dx * dx + dz * dz < KEEP_CLEAR * KEEP_CLEAR) {
                return true;
            }
        }
        return false;
    }

    private static void drop(Pending pending) {
        ServerLevel level = pending.level;
        RandomSource random = level.getRandom();
        if (BoulderRecords.get(level).recent(level.getGameTime(), RECENT_TICKS) >= AACommonConfig.BOULDER_MAX_ACTIVE.get()
                || !BoulderProtection.allowedAround(level, pending.ground)) {
            AlteredAether.LOGGER.info("Falling boulders: dropped the one announced at {}: cap or protection", pending.origin);
            return;
        }
        var table = BoulderOres.table(level.getChunkSource().getGenerator().getBiomeSource());
        BlockPos biomeAt = pending.ground.above();
        var biome = level.getBiome(biomeAt).unwrapKey().orElse(null);
        BlockState ore = (biome == null ? BoulderOres.pickOre(table, null, random)
                : BoulderOres.pickOre(table, biome, random)).defaultBlockState();
        BlockState stone = BoulderOres.pickStone(random).defaultBlockState();
        if (FallingBoulders.spawn(level, pending.origin, stone, ore, ORE_CHANCE, random) == null) {
            AlteredAether.LOGGER.info("Falling boulders: nothing could be made at {}", pending.origin);
        }
    }

    /** A low rumble heard over a long way, from the sky where the boulder will appear. */
    private static void rumble(ServerLevel level, BlockPos origin) {
        level.playSound(null, origin.getX() + 0.5, origin.getY(), origin.getZ() + 0.5, SoundEvents.AMBIENT_BASALT_DELTAS_ADDITIONS.value(),
                SoundSource.AMBIENT, 8.0f, 0.5f);
    }

    /** Grit shaken loose and falling from where the boulder is about to appear. */
    private static void grit(ServerLevel level, BlockPos origin) {
        level.sendParticles(ParticleTypes.ASH, origin.getX() + 0.5, origin.getY() - 2, origin.getZ() + 0.5, 12, 3.0, 2.0,
                3.0, 0.02);
        level.sendParticles(ParticleTypes.CLOUD, origin.getX() + 0.5, origin.getY() - 4, origin.getZ() + 0.5, 2, 2.0, 1.0,
                2.0, 0.01);
    }

    private BoulderSpawner() {
    }
}
