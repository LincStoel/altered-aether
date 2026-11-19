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
import com.linc.alteredaether.worldgen.feature.FeatureStats;
import com.linc.alteredaether.worldgen.landmark.StackField;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

import javax.management.ObjectName;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Measures Aether worldgen for the checks in {@code docs/worldgen-performance.md}. Run the dev server with
 * {@code -PaaPerf=<steps>} (comma-separated), {@code -PaaPerfTag=<name>} and, for a fresh world, {@code -PaaWorld=<name>};
 * it runs the steps off the server thread, writes to {@code run-server/perf/}, logs one {@code PERF} line per step and
 * stops the server. The steps run in this order:
 * <ul>
 *     <li>{@code fingerprint}: hashes the noise columns ({@code getBaseColumn}) over four fixed areas, round the
 *     nearest plateau and round its sea stacks. Equal hashes mean the same terrain; it needs no fresh world.</li>
 *     <li>{@code bench}: generates the same 484 chunks (4 spots, radius 5) and logs wall and worker-CPU ms per chunk,
 *     GC and the {@code ms/} feature timings. Needs a fresh world each time.</li>
 *     <li>{@code chunkhash}: after the bench, a hash and the block counts of each of its chunks, in
 *     {@code <tag>-chunks.txt}. Decorations that cross chunk borders vary between runs, so compare the counts of the
 *     blocks a change touches ({@code tools/perf/compare.py}).</li>
 *     <li>{@code fly}: {@code /altered_aether fly} along {@code -PaaPerfFly} (default {@code -3000 500 3000 500 40 10}),
 *     logging chunks missing near it and the longest tick.</li>
 *     <li>{@code spark}: a spark profile round the flight, saved to file (never uploaded), when spark is installed.</li>
 *     <li>{@code jfr}: Java Flight Recorder over the bench and the flight, in {@code <tag>.jfr} and
 *     {@code <tag>-fly.jfr}; {@code tools/perf/JfrAgg.java} reads them.</li>
 * </ul>
 */
@EventBusSubscriber(modid = AlteredAether.MOD_ID)
public final class PerfProbe {
    private static final ResourceKey<Level> THE_AETHER =
            ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath("aether", "the_aether"));
    private static final ResourceLocation PLATEAU = AlteredAether.id("aether_plateau");
    /** The benchmark's spots, far enough apart that their areas don't touch. */
    private static final int[][] SPOTS = {{-2000, -4000}, {2500, -3500}, {-4000, 4500}, {4500, 5000}};
    private static final int RADIUS = 5;
    private static final Path OUT = Path.of("perf");

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        String spec = System.getProperty("altered_aether.perf", "");
        if (spec.isBlank()) {
            return;
        }
        List<String> steps = List.of(spec.split(","));
        String tag = System.getProperty("altered_aether.perf.tag", "perf");
        String flight = System.getProperty("altered_aether.perf.fly", "");
        MinecraftServer server = event.getServer();
        Thread thread = new Thread(() -> {
            try {
                ServerLevel aether = server.getLevel(THE_AETHER);
                if (aether == null) {
                    AlteredAether.LOGGER.error("PERF: the Aether isn't loaded");
                    return;
                }
                Files.createDirectories(OUT);
                boolean jfr = steps.contains("jfr");
                if (jfr) {
                    // Deep density-function stacks need more than JFR's 64 frames; this must precede the recording.
                    diagnostic("jfrConfigure", "stackdepth=256");
                }
                if (steps.contains("fingerprint")) {
                    fingerprint(aether, line -> AlteredAether.LOGGER.info("PERF {}", line), tag);
                }
                if (steps.contains("bench")) {
                    bench(server, aether, tag, jfr, steps.contains("chunkhash"));
                }
                if (steps.contains("fly")) {
                    fly(server, tag, flight.isBlank() ? "-3000 500 3000 500 40 10" : flight, jfr,
                            steps.contains("spark") && ModList.get().isLoaded("spark"));
                }
            } catch (Throwable t) {
                AlteredAether.LOGGER.error("PERF failed", t);
            } finally {
                AlteredAether.LOGGER.info("PERF done tag={}", tag);
                server.execute(() -> server.halt(false));
            }
        }, "Altered Aether perf probe");
        thread.setDaemon(true);
        thread.start();
    }

    // ------------------------------------------------------------------------------------------ fingerprint --

    /**
     * Hashes the noise columns over the fixed areas, round the nearest plateau and round up to four of its sea stacks,
     * and reports one line with the overall hash and one per area. Blocks hash by their state's name, so the hash
     * doesn't depend on registry order (other mods change that).
     */
    static void fingerprint(ServerLevel aether, Consumer<String> report, String tag) throws Exception {
        long start = System.nanoTime();
        var generator = aether.getChunkSource().getGenerator();
        var random = aether.getChunkSource().randomState();
        Map<BlockState, Integer> names = new IdentityHashMap<>();
        List<int[]> areas = new ArrayList<>();
        for (int[] spot : SPOTS) {
            areas.add(new int[]{spot[0], spot[1], 64, 2});
        }
        var plateau = aether.findClosestBiome3d(biome -> biome.is(PLATEAU), BlockPos.ZERO, 6400, 32, 64);
        int stacks = 0;
        if (plateau != null) {
            BlockPos p = plateau.getFirst();
            areas.add(new int[]{p.getX(), p.getZ(), 128, 2});
            StackField field = StackField.get(THE_AETHER);
            if (field != null) {
                for (StackField.Stack stack : field.touching(p.getX() - 400, p.getZ() - 400, p.getX() + 400,
                        p.getZ() + 400)) {
                    if (stacks++ == 4) {
                        break;
                    }
                    areas.add(new int[]{stack.x(), stack.z(), 24, 1});
                }
            }
        }
        long all = 1125899906842597L;
        int columns = 0;
        StringBuilder perArea = new StringBuilder();
        for (int[] area : areas) {
            long hash = 1125899906842597L;
            for (int x = area[0] - area[2] / 2; x < area[0] + area[2] / 2; x += area[3]) {
                for (int z = area[1] - area[2] / 2; z < area[1] + area[2] / 2; z += area[3]) {
                    NoiseColumn column = generator.getBaseColumn(x, z, aether, random);
                    for (int y = aether.getMinBuildHeight(); y < aether.getMaxBuildHeight(); y++) {
                        hash = 31 * hash + names.computeIfAbsent(column.getBlock(y), s -> s.toString().hashCode());
                    }
                    columns++;
                }
            }
            perArea.append(String.format(" %d:%d=%016x", area[0], area[1], hash));
            all = 31 * all + hash;
        }
        String line = String.format("fingerprint tag=%s hash=%016x columns=%d stacks=%d ms=%d areas:%s", tag, all,
                columns, Math.min(stacks, 4), (System.nanoTime() - start) / 1_000_000, perArea);
        Files.createDirectories(OUT);
        Files.writeString(OUT.resolve(tag + "-fingerprint.txt"), line + System.lineSeparator());
        report.accept(line);
    }

    // ---------------------------------------------------------------------------------------------- bench --

    private static void bench(MinecraftServer server, ServerLevel aether, String tag, boolean jfr, boolean chunkHash)
            throws Exception {
        Set<ChunkPos> area = new LinkedHashSet<>();
        for (int[] spot : SPOTS) {
            ChunkPos centre = new ChunkPos(new BlockPos(spot[0], 64, spot[1]));
            for (int x = -RADIUS; x <= RADIUS; x++) {
                for (int z = -RADIUS; z <= RADIUS; z++) {
                    area.add(new ChunkPos(centre.x + x, centre.z + z));
                }
            }
        }
        List<ChunkPos> positions = new ArrayList<>(area);
        if (jfr) {
            diagnostic("jfrStart", "name=perf", "settings=profile");
        }
        CpuMeter meter = new CpuMeter();
        meter.start();
        long gc = gcMillis();
        long start = System.nanoTime();
        // Tickets rather than requests, which can expire before the chunk is done (see DebugCommands.benchmark).
        server.submit(() -> positions.forEach(p -> aether.getChunkSource().addRegionTicket(TicketType.FORCED, p, 2, p)))
                .join();
        List<CompletableFuture<ChunkResult<ChunkAccess>>> chunks = new ArrayList<>();
        for (ChunkPos p : positions) {
            chunks.add(aether.getChunkSource().getChunkFuture(p.x, p.z, ChunkStatus.FULL, true));
        }
        CompletableFuture.allOf(chunks.toArray(CompletableFuture[]::new)).join();
        double wall = (System.nanoTime() - start) / 1e6;
        long cpu = meter.stop();
        gc = gcMillis() - gc;
        if (jfr) {
            diagnostic("jfrStop", "name=perf", "filename=" + OUT.resolve(tag + ".jfr").toAbsolutePath());
        }
        long made = chunks.stream().filter(f -> f.join().isSuccess()).count();
        StringBuilder timings = new StringBuilder();
        FeatureStats.snapshot().forEach((key, value) -> {
            if (key.startsWith("ms/")) {
                timings.append(' ').append(key).append('=').append(value);
            }
        });
        AlteredAether.LOGGER.info("PERF bench tag={} chunks={} wall_ms={} wall_ms_per_chunk={} worker_cpu_ms={} "
                        + "worker_cpu_ms_per_chunk={} gc_ms={} workers={} timings:{}", tag, made, String.format("%.0f", wall),
                String.format("%.2f", wall / Math.max(1, made)), cpu, String.format("%.2f", cpu / (double) Math.max(1, made)),
                gc, meter.threads(), timings);
        if (chunkHash) {
            chunkHash(server, aether, positions, tag);
        }
        server.submit(() -> positions.forEach(p -> aether.getChunkSource().removeRegionTicket(TicketType.FORCED, p, 2,
                p))).join();
    }

    /** Each bench chunk's hash and the count of each block in it, one line a chunk, to {@code <tag>-chunks.txt}. */
    private static void chunkHash(MinecraftServer server, ServerLevel aether, List<ChunkPos> positions, String tag)
            throws Exception {
        Map<BlockState, Integer> names = new IdentityHashMap<>();
        StringBuilder out = new StringBuilder();
        long[] all = {1125899906842597L};
        server.submit(() -> {
            for (ChunkPos p : positions) {
                ChunkAccess chunk = aether.getChunk(p.x, p.z);
                long hash = 1125899906842597L;
                Map<String, Integer> counts = new TreeMap<>();
                for (var section : chunk.getSections()) {
                    for (int y = 0; y < 16; y++) {
                        for (int z = 0; z < 16; z++) {
                            for (int x = 0; x < 16; x++) {
                                BlockState state = section.getBlockState(x, y, z);
                                hash = 31 * hash + names.computeIfAbsent(state, s -> s.toString().hashCode());
                                if (!state.isAir()) {
                                    counts.merge(state.getBlock().getDescriptionId(), 1, Integer::sum);
                                }
                            }
                        }
                    }
                }
                out.append(p.x).append(' ').append(p.z).append(' ').append(String.format("%016x", hash)).append(' ')
                        .append(counts).append(System.lineSeparator());
                all[0] = 31 * all[0] + hash;
            }
        }).join();
        Files.writeString(OUT.resolve(tag + "-chunks.txt"), out);
        AlteredAether.LOGGER.info("PERF chunkhash tag={} hash={} chunks={}", tag, String.format("%016x", all[0]),
                positions.size());
    }

    // ------------------------------------------------------------------------------------------------ fly --

    private static void fly(MinecraftServer server, String tag, String route, boolean jfr, boolean spark)
            throws Exception {
        if (jfr) {
            diagnostic("jfrStart", "name=fly", "settings=profile");
        }
        if (spark) {
            // Stopped with --save-to-file below: a profile that ends on its own timeout is uploaded.
            command(server, "spark profiler start --thread * --interval 4");
        }
        CpuMeter meter = new CpuMeter();
        meter.start();
        long start = System.nanoTime();
        command(server, "execute in aether:the_aether run altered_aether fly " + route);
        Thread.sleep(2000);
        while (Flight.flying()) {
            Thread.sleep(500);
        }
        long cpu = meter.stop();
        AlteredAether.LOGGER.info("PERF fly tag={} wall_s={} worker_cpu_ms={} (see 'Flight done' for chunks missing)",
                tag, String.format("%.1f", (System.nanoTime() - start) / 1e9), cpu);
        if (spark) {
            command(server, "spark profiler stop --save-to-file");
            // spark writes the file on its own threads; give it time before the server stops.
            Thread.sleep(15000);
        }
        if (jfr) {
            diagnostic("jfrStop", "name=fly", "filename=" + OUT.resolve(tag + "-fly.jfr").toAbsolutePath());
        }
    }

    private static void command(MinecraftServer server, String command) {
        server.submit(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command))
                .join();
    }

    // -------------------------------------------------------------------------------------------- helpers --

    /** Runs a diagnostic command (as jcmd would) in this JVM. */
    private static void diagnostic(String operation, String... arguments) throws Exception {
        Object result = ManagementFactory.getPlatformMBeanServer().invoke(
                new ObjectName("com.sun.management:type=DiagnosticCommand"), operation, new Object[]{arguments},
                new String[]{String[].class.getName()});
        AlteredAether.LOGGER.info("PERF {} {}: {}", operation, String.join(" ", arguments),
                String.valueOf(result).trim().replace('\n', ' '));
    }

    private static long gcMillis() {
        long total = 0;
        for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
            total += Math.max(0, bean.getCollectionTime());
        }
        return total;
    }

    /**
     * CPU time spent by the worldgen worker threads ({@code Worker-Main-*}), polled so threads that retire during the
     * run still count. Wall time alone varies a lot on a busy machine; worker CPU is steadier.
     */
    private static final class CpuMeter {
        private final ThreadMXBean threads = ManagementFactory.getThreadMXBean();
        private final Map<Long, Long> base = new HashMap<>(), last = new HashMap<>();
        private volatile boolean running;
        private Thread poller;

        void start() {
            sample(true);
            running = true;
            poller = new Thread(() -> {
                while (running) {
                    sample(false);
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                        return;
                    }
                }
            }, "Altered Aether perf CPU meter");
            poller.setDaemon(true);
            poller.start();
        }

        long stop() throws InterruptedException {
            running = false;
            poller.join();
            sample(false);
            long total = 0;
            for (var entry : last.entrySet()) {
                total += entry.getValue() - base.getOrDefault(entry.getKey(), 0L);
            }
            return total / 1_000_000;
        }

        int threads() {
            return last.size();
        }

        private synchronized void sample(boolean baseline) {
            for (Thread thread : Thread.getAllStackTraces().keySet()) {
                if (!thread.getName().startsWith("Worker-Main")) {
                    continue;
                }
                long cpu = threads.getThreadCpuTime(thread.threadId());
                if (cpu < 0) {
                    continue;
                }
                if (baseline) {
                    base.put(thread.threadId(), cpu);
                } else if (!base.containsKey(thread.threadId()) && !last.containsKey(thread.threadId())) {
                    base.put(thread.threadId(), 0L);
                }
                last.put(thread.threadId(), cpu);
            }
        }
    }

    private PerfProbe() {
    }
}
