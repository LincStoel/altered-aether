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
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

import java.util.ArrayList;
import java.util.Map;
import java.util.TreeMap;
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
            String[] parts = bench.split(",");
            benchmark(event.getServer(), Integer.parseInt(parts[0].trim()), parts.length > 1 ? parts[1].trim() : null);
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
    private static void benchmark(MinecraftServer server, int radius, String biome) {
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
            BlockPos origin = new BlockPos((2000 + (int) (System.currentTimeMillis() / 1000 % 1000) * 64) * 16, 64, 0);
            if (biome != null) {
                ResourceLocation id = ResourceLocation.parse(biome);
                var found = aether.findClosestBiome3d(b -> b.is(id), origin, 12000, 32, 64);
                if (found == null) {
                    AlteredAether.LOGGER.error("Worldgen benchmark: no {} within 12000 blocks", biome);
                    return;
                }
                origin = found.getFirst();
                AlteredAether.LOGGER.info("Worldgen benchmark: centred on {} at {}", biome, origin.toShortString());
            }
            ChunkPos centre = new ChunkPos(origin);
            List<ChunkPos> positions = new ArrayList<>();
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    positions.add(new ChunkPos(centre.x + x, centre.z + z));
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
            server.submit(() -> surfaceTally(aether, positions)).join();
            server.submit(() -> positions.forEach(p -> aether.setChunkForced(p.x, p.z, false))).join();
        }, Util.backgroundExecutor()).whenCompleteAsync((done, error) -> {
            if (error != null) {
                AlteredAether.LOGGER.error("Worldgen benchmark failed", error);
            }
            server.halt(false);
        }, server);
    }

    /** The topmost block of every column in the given chunks, counted per biome: a check on the surface rules. */
    private static void surfaceTally(ServerLevel level, List<ChunkPos> positions) {
        Map<String, Map<String, Integer>> byBiome = new TreeMap<>();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (ChunkPos chunk : positions) {
            for (int x = chunk.getMinBlockX(); x <= chunk.getMaxBlockX(); x++) {
                for (int z = chunk.getMinBlockZ(); z <= chunk.getMaxBlockZ(); z++) {
                    int top = level.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z) - 1;
                    if (top <= level.getMinBuildHeight()) {
                        continue;
                    }
                    cursor.set(x, top, z);
                    String biome = level.getBiome(cursor).unwrapKey().map(k -> k.location().toString()).orElse("?");
                    String block = BuiltInRegistries.BLOCK.getKey(level.getBlockState(cursor).getBlock()).toString();
                    byBiome.computeIfAbsent(biome, k -> new TreeMap<>()).merge(block, 1, Integer::sum);
                    int dx = Math.abs(level.getHeight(Heightmap.Types.OCEAN_FLOOR, x + 2, z) - 1 - top);
                    int dz = Math.abs(level.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z + 2) - 1 - top);
                    byBiome.get(biome).merge(dx >= 3 || dz >= 3 ? "~steep" : "~flat", 1, Integer::sum);
                }
            }
        }
        byBiome.forEach((biome, blocks) -> {
            int total = blocks.entrySet().stream().filter(e -> !e.getKey().startsWith("~"))
                    .mapToInt(Map.Entry::getValue).sum();
            StringBuilder line = new StringBuilder(String.format("Surface %-42s", biome));
            blocks.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).limit(7).forEach(e ->
                    line.append(String.format(" %s %.0f%%", e.getKey().replace("minecraft:", ""),
                            100.0 * e.getValue() / total)));
            AlteredAether.LOGGER.info(line.toString());
        });
    }

    private DebugCommands() {
    }
}
