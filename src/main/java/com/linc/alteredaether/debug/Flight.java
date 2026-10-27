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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Comparator;

/**
 * {@code /altered_aether fly <x0> <z0> <x1> <z1> <speed> [radius]}: a stand-in for a player flying across the Aether,
 * for profiling exploration without a client. A ticket of the given chunk radius moves along the line at {@code speed}
 * blocks a second and loads chunks the way a player's view does, off the server thread; every ten seconds it logs the
 * tick times, the chunks generated and how far the loaded area lags behind.
 */
@EventBusSubscriber(modid = AlteredAether.MOD_ID)
public final class Flight {
    private static final TicketType<ChunkPos> TICKET =
            TicketType.create("altered_aether_flight", Comparator.comparingLong(ChunkPos::toLong));

    private static Flight active;
    private static volatile int generated;

    private final ServerLevel level;
    private final double x0, z0, x1, z1, speed;
    private final int radius;
    private final double length;
    private long tick;
    private ChunkPos at;
    private long tickStart, windowNanos, windowMax, totalNanos, totalMax;
    private int windowTicks, windowGenerated, lagging, samples;

    private Flight(ServerLevel level, double x0, double z0, double x1, double z1, double speed, int radius) {
        this.level = level;
        this.x0 = x0;
        this.z0 = z0;
        this.x1 = x1;
        this.z1 = z1;
        this.speed = speed;
        this.radius = radius;
        this.length = Math.hypot(x1 - x0, z1 - z0);
    }

    static boolean start(ServerLevel level, double x0, double z0, double x1, double z1, double speed, int radius) {
        if (active != null) {
            return false;
        }
        active = new Flight(level, x0, z0, x1, z1, speed, radius);
        generated = 0;
        AlteredAether.LOGGER.info("Flight: {} blocks at {} blocks/s, radius {}", (int) active.length, speed, radius);
        return true;
    }

    @SubscribeEvent
    static void onChunkLoad(ChunkEvent.Load event) {
        if (active != null && event.isNewChunk() && event.getLevel() == active.level) {
            generated++;
        }
    }

    @SubscribeEvent
    static void onTickStart(ServerTickEvent.Pre event) {
        if (active != null) {
            active.tickStart = System.nanoTime();
        }
    }

    @SubscribeEvent
    static void onTickEnd(ServerTickEvent.Post event) {
        if (active != null) {
            active.tick();
        }
    }

    private void tick() {
        long nanos = System.nanoTime() - tickStart;
        windowNanos += nanos;
        totalNanos += nanos;
        windowMax = Math.max(windowMax, nanos);
        totalMax = Math.max(totalMax, nanos);
        windowTicks++;
        double travelled = Math.min(length, speed * tick / 20.0);
        double t = length == 0 ? 1 : travelled / length;
        ChunkPos next = new ChunkPos((int) Math.floor(x0 + (x1 - x0) * t) >> 4, (int) Math.floor(z0 + (z1 - z0) * t) >> 4);
        if (!next.equals(at)) {
            level.getChunkSource().addRegionTicket(TICKET, next, radius, next);
            if (at != null) {
                level.getChunkSource().removeRegionTicket(TICKET, at, radius, at);
            }
            at = next;
        }
        if (tick % 200 == 199) {
            // how many chunks a player would see missing within half the view
            int half = Math.max(2, radius / 2), missing = 0;
            for (int x = -half; x <= half; x++) {
                for (int z = -half; z <= half; z++) {
                    if (level.getChunkSource().getChunkNow(at.x + x, at.z + z) == null) {
                        missing++;
                    }
                }
            }
            lagging += missing;
            samples++;
            int made = generated;
            AlteredAether.LOGGER.info("Flight: at {} {} | mspt avg {} max {} | {} new chunks | {} missing near",
                    at.getMiddleBlockX(), at.getMiddleBlockZ(), String.format("%.1f", windowNanos / 1e6 / windowTicks),
                    String.format("%.1f", windowMax / 1e6), made - windowGenerated, missing);
            windowGenerated = made;
            windowNanos = windowMax = 0;
            windowTicks = 0;
        }
        tick++;
        if (travelled >= length) {
            level.getChunkSource().removeRegionTicket(TICKET, at, radius, at);
            AlteredAether.LOGGER.info("Flight done: {} ticks, mspt avg {} max {}, {} new chunks, {} missing near on average",
                    tick, String.format("%.1f", totalNanos / 1e6 / tick), String.format("%.1f", totalMax / 1e6),
                    generated, samples == 0 ? 0 : String.format("%.1f", lagging / (double) samples));
            active = null;
        }
    }
}
