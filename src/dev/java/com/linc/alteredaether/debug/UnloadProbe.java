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
package com.linc.alteredaether.debug;

import com.linc.alteredaether.AlteredAether;
import com.linc.alteredaether.fix.UnloadRetries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Comparator;

/**
 * Reproduces the chunk unload hang headlessly (see UnloadRetries / ChunkUnloadRetryMixin): with the system property
 * {@code altered_aether.unload} set (gradle {@code -PaaUnload=<rounds>[,<base x>|auto[,<A ticks>[,<sync gap ticks>]]]} on runServer), each round loads a
 * patch of never-generated ground at A, drops it while it is still generating, loads another at B and times how long
 * B's middle takes to be ready, the way a long teleport or a portal does to a player. Rounds go Aether to Aether, Aether
 * to Overworld and Overworld to Aether in turn. A round over a minute counts as stalled. Then it halts the server, which
 * hangs at "Saving worlds" if unloads are still stuck. Lines start {@code UNLOAD}. Run with
 * {@code fixes.defer_stuck_unloads} off to see the hang, on to see the fix.
 */
@EventBusSubscriber(modid = AlteredAether.MOD_ID)
public final class UnloadProbe {
    private static final ResourceKey<Level> THE_AETHER =
            ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath("aether", "the_aether"));
    private static final TicketType<ChunkPos> TICKET =
            TicketType.create("altered_aether_unload_probe", Comparator.comparingLong(ChunkPos::toLong));
    /** How far round A and B the tickets load, and how far round B must be ready. */
    private static final int LOAD_RADIUS = 10, READY_RADIUS = 4;
    private static final long STALL_NANOS = 60_000_000_000L;
    /** Chunks between one patch and the next, so every patch is fresh ground. */
    private static final int STEP = 256;

    private static int rounds, round = -1, ticks, stalls;
    /** How long A generates before it's dropped, in ticks. */
    private static int aTicks = 30;
    /**
     * Ticks between generating B's middle on the server thread at once (the way a portal search or building something
     * there does, with tickets that lapse a tick later) and the lasting ticket at B arriving; -1 for no such load.
     */
    private static int syncGap = -1;
    private static int gapTicks;
    private static int baseChunk;
    private static long startNanos, worstNanos;
    private static ServerLevel loadedLevel;
    private static ChunkPos loadedAt;
    private static boolean waitingOnB;

    private UnloadProbe() {
    }

    @SubscribeEvent
    public static void onStarted(ServerStartedEvent event) {
        String spec = System.getProperty("altered_aether.unload", "");
        if (spec.isBlank()) {
            return;
        }
        String[] parts = spec.split(",");
        rounds = Integer.parseInt(parts[0].trim());
        // fresh ground each run unless told where: somewhere along a line far out on +x
        int baseX = parts.length > 1 && !parts[1].isBlank() && !parts[1].trim().equals("auto")
                ? Integer.parseInt(parts[1].trim())
                : 100_000 + (int) (System.currentTimeMillis() / 1000 % 400) * 50_000;
        baseChunk = baseX >> 4;
        aTicks = parts.length > 2 ? Integer.parseInt(parts[2].trim()) : 30;
        syncGap = parts.length > 3 ? Integer.parseInt(parts[3].trim()) : -1;
        AlteredAether.LOGGER.info("UNLOAD {} rounds from x {}, A for {} ticks{}, defer_stuck_unloads {}", rounds, baseX,
                aTicks, syncGap < 0 ? "" : ", B loaded at once " + syncGap + " ticks before its ticket",
                UnloadRetries.enabled());
        round = 0;
        ticks = 0;
        waitingOnB = false;
    }

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post event) {
        if (round < 0) {
            return;
        }
        MinecraftServer server = event.getServer();
        if (round >= rounds) {
            release();
            round = -1;
            AlteredAether.LOGGER.info("UNLOAD done: {} of {} rounds stalled, slowest {} ms, longest wait to unload {} ms;"
                    + " halting", stalls, rounds, worstNanos / 1_000_000, UnloadRetries.longestWait() / 1_000_000);
            server.halt(false);
            return;
        }
        ServerLevel aether = server.getLevel(THE_AETHER);
        ServerLevel overworld = server.overworld();
        if (aether == null) {
            AlteredAether.LOGGER.error("UNLOAD the Aether isn't loaded");
            round = -1;
            server.halt(false);
            return;
        }
        // round kinds: Aether -> Aether, Aether -> Overworld, Overworld -> Aether
        ServerLevel from = round % 3 == 2 ? overworld : aether;
        ServerLevel to = round % 3 == 1 ? overworld : aether;
        ChunkPos a = new ChunkPos(baseChunk + round * 2 * STEP, 0);
        ChunkPos b = new ChunkPos(baseChunk + (round * 2 + 1) * STEP, 0);
        if (!waitingOnB) {
            if (ticks == 0) {
                UnloadRetries.reset();
                load(from, a);
            }
            if (ticks < aTicks) {
                ticks++;
                return;
            }
            if (ticks == aTicks) {
                release();
                startNanos = System.nanoTime();
                if (syncGap >= 0) {
                    for (int dx = -2; dx <= 2; dx++) {
                        for (int dz = -2; dz <= 2; dz++) {
                            to.getChunk(b.x + dx, b.z + dz);
                        }
                    }
                }
                gapTicks = 0;
            }
            if (ticks >= aTicks && gapTicks++ >= Math.max(syncGap, 0)) {
                load(to, b);
                waitingOnB = true;
            }
            ticks++;
            return;
        }
        long took = System.nanoTime() - startNanos;
        boolean ready = ready(to, b);
        if (!ready && took < STALL_NANOS) {
            return;
        }
        worstNanos = Math.max(worstNanos, took);
        if (!ready) {
            stalls++;
        }
        AlteredAether.LOGGER.info("UNLOAD round {} ({} -> {}): B {} in {} ms; unload retries {}, longest wait {} ms",
                round, from.dimension().location().getPath(), to.dimension().location().getPath(),
                ready ? "ready" : "STALLED", took / 1_000_000, UnloadRetries.retries(),
                UnloadRetries.longestWait() / 1_000_000);
        release();
        round++;
        ticks = 0;
        waitingOnB = false;
    }

    private static void load(ServerLevel level, ChunkPos at) {
        level.getChunkSource().addRegionTicket(TICKET, at, LOAD_RADIUS, at);
        loadedLevel = level;
        loadedAt = at;
    }

    private static void release() {
        if (loadedLevel != null) {
            loadedLevel.getChunkSource().removeRegionTicket(TICKET, loadedAt, LOAD_RADIUS, loadedAt);
            loadedLevel = null;
        }
    }

    /** Whether every chunk within READY_RADIUS of {@code at} is loaded in full. */
    private static boolean ready(ServerLevel level, ChunkPos at) {
        for (int dx = -READY_RADIUS; dx <= READY_RADIUS; dx++) {
            for (int dz = -READY_RADIUS; dz <= READY_RADIUS; dz++) {
                if (level.getChunkSource().getChunkNow(at.x + dx, at.z + dz) == null) {
                    return false;
                }
            }
        }
        return true;
    }
}
