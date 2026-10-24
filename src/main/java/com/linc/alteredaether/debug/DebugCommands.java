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
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.Util;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * {@code /altered_aether report [radius] [step]} prints a {@link WorldgenReport} for the Aether around the caller.
 * Setting the system property {@code altered_aether.report} to {@code radius,step} runs one around the origin when the
 * server starts and then stops the server, so a dev run can check worldgen headlessly.
 */
@EventBusSubscriber(modid = AlteredAether.MOD_ID)
public final class DebugCommands {
    private static final ResourceKey<Level> THE_AETHER =
            ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath("aether", "the_aether"));

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal(AlteredAether.MOD_ID)
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("report")
                        .executes(ctx -> report(ctx, 2048, 32))
                        .then(Commands.argument("radius", IntegerArgumentType.integer(16, 32768))
                                .executes(ctx -> report(ctx, IntegerArgumentType.getInteger(ctx, "radius"), 32))
                                .then(Commands.argument("step", IntegerArgumentType.integer(1, 1024))
                                        .executes(ctx -> report(ctx, IntegerArgumentType.getInteger(ctx, "radius"),
                                                IntegerArgumentType.getInteger(ctx, "step")))))));
    }

    private static int report(CommandContext<CommandSourceStack> ctx, int radius, int step) {
        CommandSourceStack source = ctx.getSource();
        ServerLevel aether = source.getServer().getLevel(THE_AETHER);
        if (aether == null) {
            source.sendFailure(Component.literal("The Aether isn't loaded."));
            return 0;
        }
        int x = (int) source.getPosition().x, z = (int) source.getPosition().z;
        source.sendSuccess(() -> Component.literal("Sampling the Aether..."), false);
        // Off the server thread: a large report takes longer than the watchdog allows a tick.
        CompletableFuture.supplyAsync(() -> WorldgenReport.run(aether, x, z, radius, step), Util.backgroundExecutor())
                .thenAcceptAsync(lines -> lines.forEach(line -> source.sendSuccess(() -> Component.literal(line),
                        false)), source.getServer());
        return 1;
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        String bench = System.getProperty("altered_aether.bench", "");
        if (!bench.isBlank()) {
            benchmark(event.getServer(), Integer.parseInt(bench.trim()));
        }
        String spec = System.getProperty("altered_aether.report", "");
        if (spec.isBlank()) {
            return;
        }
        MinecraftServer server = event.getServer();
        String[] parts = spec.split(",");
        int radius = Integer.parseInt(parts[0].trim());
        int step = parts.length > 1 ? Integer.parseInt(parts[1].trim()) : 32;
        ServerLevel aether = server.getLevel(THE_AETHER);
        if (aether == null) {
            AlteredAether.LOGGER.error("Worldgen report: the Aether isn't loaded");
            server.halt(false);
            return;
        }
        CompletableFuture.runAsync(() -> WorldgenReport.run(aether, 0, 0, radius, step), Util.backgroundExecutor())
                .whenCompleteAsync((done, error) -> {
                    if (error != null) {
                        AlteredAether.LOGGER.error("Worldgen report failed", error);
                    }
                    server.halt(false);
                }, server);
    }

    /**
     * Generates every Aether chunk within {@code radius} chunks of the origin, logs how long it took, then stops
     * the server. All chunks are requested at once, so the time reflects worldgen throughput across the worker
     * threads, as when a player explores.
     */
    private static void benchmark(MinecraftServer server, int radius) {
        ServerLevel aether = server.getLevel(THE_AETHER);
        if (aether == null) {
            AlteredAether.LOGGER.error("Worldgen benchmark: the Aether isn't loaded");
            server.halt(false);
            return;
        }
        // Requested from a background thread: on the server thread, getChunkFuture blocks until the chunk is done,
        // which would stall the very thread that drives chunk generation.
        CompletableFuture.runAsync(() -> {
            // Far from any chunk generated by an earlier run, so every chunk here is generated fresh.
            int origin = 2000 + (int) (System.currentTimeMillis() / 1000 % 1000) * 64;
            List<ChunkPos> positions = new ArrayList<>();
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    positions.add(new ChunkPos(origin + x, z));
                }
            }
            long start = System.nanoTime();
            // Forced tickets keep each chunk loaded until it finishes; a plain request's ticket can expire first.
            server.submit(() -> positions.forEach(p -> aether.setChunkForced(p.x, p.z, true))).join();
            List<CompletableFuture<ChunkResult<ChunkAccess>>> chunks = new ArrayList<>();
            for (ChunkPos p : positions) {
                chunks.add(aether.getChunkSource().getChunkFuture(p.x, p.z, ChunkStatus.FULL, true));
            }
            CompletableFuture.allOf(chunks.toArray(CompletableFuture[]::new)).join();
            double seconds = (System.nanoTime() - start) / 1e9;
            long generated = chunks.stream().filter(f -> f.join().isSuccess()).count();
            AlteredAether.LOGGER.info("Worldgen benchmark: {} of {} Aether chunks generated in {} s ({} ms per chunk)",
                    generated, chunks.size(), String.format("%.2f", seconds),
                    String.format("%.1f", seconds * 1000 / Math.max(1, generated)));
            server.submit(() -> positions.forEach(p -> aether.setChunkForced(p.x, p.z, false))).join();
        }, Util.backgroundExecutor()).whenCompleteAsync((done, error) -> {
            if (error != null) {
                AlteredAether.LOGGER.error("Worldgen benchmark failed", error);
            }
            server.halt(false);
        }, server);
    }

    private DebugCommands() {
    }
}
