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
package com.linc.alteredaether.loot;

import com.linc.alteredaether.AlteredAether;
import com.linc.alteredaether.worldgen.landmark.LandmarkField;
import com.linc.alteredaether.worldgen.landmark.LandmarkType;
import net.minecraft.Util;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runs the searches a rumour map would make ahead of time, off the server thread. A rumour map looks for a landmark up
 * to {@link RumourMapFunction#DEFAULT_RADIUS} blocks away while a chest's loot is rolled, and on ground nobody has been
 * near, finding which grid cells hold a landmark means probing the terrain: hundreds of milliseconds, a stall. So once
 * a second, each player in the Aether who has moved into a new grid cell of a landmark type has that type's search run
 * from where they are, on a background thread, and likewise the archwood groves' biome searches (with Ars Nouveau) for
 * each new {@link RumourMapFunction#BIOME_CELL} cell. The answers land in {@link LandmarkField}'s cell cache and the
 * function's own biome cache, and the search a chest near them makes is then all cache hits. A chest opened before
 * that's done just searches as before.
 */
@EventBusSubscriber(modid = AlteredAether.MOD_ID)
public final class RumourWarmup {
    private static final ResourceKey<Level> THE_AETHER =
            ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath("aether", "the_aether"));
    private static final int INTERVAL = 20;
    /**
     * Searches run at once at most. They share the pool chunks generate on, so a crowd of players crossing cells at
     * once waits its turn rather than holding up world generation.
     */
    private static final int MAX_RUNNING = 2;
    private static final LandmarkType[] TYPES = LandmarkType.values();

    /** Per player, the grid cell each type was last searched from, packed; touched only on the server thread. */
    private static final Map<UUID, long[]> SEARCHED = new HashMap<>();
    /** The (player, type) searches running now. */
    private static final Set<Search> RUNNING = ConcurrentHashMap.newKeySet();

    /** One kind of search: a landmark type's, by its ordinal, or the groves', {@link #GROVES}. */
    private record Search(UUID player, int kind) {
    }

    /** The kind for the archwood groves' biome searches, after the landmark types. */
    private static final int GROVES = TYPES.length;

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !level.dimension().equals(THE_AETHER)) {
            return;
        }
        LandmarkField field = LandmarkField.get(THE_AETHER);
        if (field == null) {
            return;
        }
        long time = level.getGameTime();
        for (ServerPlayer player : level.players()) {
            if ((time + player.getId()) % INTERVAL != 0) {
                continue;
            }
            warm(level, field, player.getUUID(), player.getBlockX(), player.getBlockZ());
        }
    }

    /**
     * Starts the searches from (x, z) that haven't been made yet for this player, as many as there's room for; the
     * rest wait for the next call. Called once a second per player, and by the dev tools with a made-up player.
     */
    public static void warm(ServerLevel level, LandmarkField field, UUID player, int x, int z) {
        long[] searched = SEARCHED.computeIfAbsent(player, id -> {
            long[] none = new long[GROVES + 1];
            Arrays.fill(none, Long.MIN_VALUE);
            return none;
        });
        List<ResourceLocation> groves = groves(level);
        for (int kind = 0; kind <= GROVES; kind++) {
            int cell = cell(field, groves, kind);
            if (cell <= 0) {
                continue;
            }
            long here = cellKey(x, z, cell);
            Search search = new Search(player, kind);
            if (searched[kind] == here || RUNNING.contains(search)) {
                continue;
            }
            if (RUNNING.size() >= MAX_RUNNING) {
                // the rest wait for the next pass, still marked unsearched
                return;
            }
            searched[kind] = here;
            RUNNING.add(search);
            Runnable task = kind == GROVES
                    ? () -> groves.forEach(biome -> RumourMapFunction.nearestBiome(level, biome, x, z,
                            RumourMapFunction.DEFAULT_RADIUS))
                    : nearest(field, TYPES[kind], x, z);
            String what = kind == GROVES ? "archwood groves" : TYPES[kind].id();
            CompletableFuture.runAsync(task, Util.backgroundExecutor())
                    .whenComplete((done, error) -> {
                        RUNNING.remove(search);
                        if (error != null) {
                            AlteredAether.LOGGER.warn("Couldn't look ahead for rumours of {} near {}, {}", what, x, z,
                                    error);
                        }
                    });
        }
    }

    private static Runnable nearest(LandmarkField field, LandmarkType type, int x, int z) {
        return () -> RumourMapFunction.nearest(field, type, x, z, RumourMapFunction.DEFAULT_RADIUS);
    }

    /** The archwood groves this world has: none without Ars Nouveau. */
    private static List<ResourceLocation> groves(ServerLevel level) {
        return RumourMapFunction.ARCHWOOD_GROVES.stream().filter(biome -> RumourMapFunction.registered(level, biome))
                .toList();
    }

    /** The grid a kind of search is made on, in blocks; 0 if there's nothing of that kind to look for. */
    private static int cell(LandmarkField field, List<ResourceLocation> groves, int kind) {
        if (kind == GROVES) {
            return groves.isEmpty() ? 0 : RumourMapFunction.BIOME_CELL;
        }
        return field.gridCell(TYPES[kind]);
    }

    /** The grid cell holding (x, z), packed into a long. */
    private static long cellKey(int x, int z, int cell) {
        return ((long) Math.floorDiv(x, cell) << 32) ^ (Math.floorDiv(z, cell) & 0xFFFFFFFFL);
    }

    /** Whether every search from (x, z) has been made for the player and none is still running; for the dev tools. */
    public static boolean settled(ServerLevel level, LandmarkField field, UUID player, int x, int z) {
        long[] searched = SEARCHED.get(player);
        if (searched == null) {
            return false;
        }
        List<ResourceLocation> groves = groves(level);
        for (int kind = 0; kind <= GROVES; kind++) {
            int cell = cell(field, groves, kind);
            if (cell > 0 && (searched[kind] != cellKey(x, z, cell) || RUNNING.contains(new Search(player, kind)))) {
                return false;
            }
        }
        return true;
    }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        SEARCHED.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        SEARCHED.clear();
        RumourMapFunction.forgetBiomeSearches();
    }

    private RumourWarmup() {
    }
}
