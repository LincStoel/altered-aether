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
import jdk.jfr.consumer.*;

import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Summarises a JFR recording from PerfProbe ({@code -PaaPerf=...,jfr}); see docs/worldgen-performance.md.
 * <pre>
 * java tools/perf/JfrAgg.java run-server/perf/before.jfr [mode] [top] [thread prefix|-] [only stacks containing]
 * </pre>
 * Modes: {@code cpu} (execution samples, the default), {@code park} and {@code monitor} (time waiting, by site),
 * {@code alloc} (allocation weight). The cpu report gives:
 * <ul>
 *     <li>concurrency per generation step, the mean threads busy per 100 ms: about 1 means that step runs one chunk at
 *     a time, so its cost is wall time;</li>
 *     <li>samples by thread, by generation step, by our class (inclusive), by the first and the leaf frame;</li>
 *     <li>the Aether structure whose placement probe a sample is under, our call chains, and the feature being
 *     placed.</li>
 * </ul>
 * It reads the recording directly: exporting deep stacks to JSON with {@code jfr print} runs to gigabytes.
 */
public class JfrAgg {
    static final String[][] STATUS = {
            {"ChunkStatusTasks.generateStructureStarts", "structure_starts"},
            {"ChunkStatusTasks.generateStructureReferences", "structure_refs"},
            {"ChunkStatusTasks.generateBiomes", "biomes"},
            {"ChunkStatusTasks.generateNoise", "noise"},
            {"ChunkStatusTasks.generateSurface", "surface"},
            {"ChunkStatusTasks.generateCarvers", "carvers"},
            {"ChunkStatusTasks.generateFeatures", "features"},
            {"ChunkStatusTasks.initializeLight", "light_init"},
            {"ChunkStatusTasks.light", "light"},
            {"ChunkStatusTasks.generateSpawn", "spawn"},
            {"ChunkStatusTasks.full", "full"},
    };

    record Agg(Map<String, Double> m) {
        void add(String k, double w) { m.merge(k, w, Double::sum); }
    }

    public static void main(String[] a) throws Exception {
        Path file = Path.of(a[0]);
        String mode = a.length > 1 ? a[1] : "cpu";
        int top = a.length > 2 ? Integer.parseInt(a[2]) : 25;
        String tpre = a.length > 3 && !a[3].equals("-") ? a[3] : null;
        String focus = a.length > 4 ? a[4] : null;
        String event = switch (mode) {
            case "park" -> "jdk.ThreadPark";
            case "monitor" -> "jdk.JavaMonitorEnter";
            case "alloc" -> "jdk.ObjectAllocationSample";
            default -> "jdk.ExecutionSample";
        };
        Map<String, Agg> aggs = new LinkedHashMap<>();
        for (String n : List.of("threads", "status", "own_incl", "first_own", "own_self", "leaf", "status_x_own", "vanilla_hot", "structure", "chain", "feature"))
            aggs.put(n, new Agg(new HashMap<>()));
        double total = 0;
        // concurrency: per 100 ms window and step, the distinct threads sampled in it
        Map<String, Map<Long, Set<String>>> conc = new HashMap<>();
        try (RecordingFile rf = new RecordingFile(file)) {
            while (rf.hasMoreEvents()) {
                RecordedEvent e = rf.readEvent();
                if (!e.getEventType().getName().equals(event)) continue;
                RecordedThread th = e.hasField("sampledThread") ? e.getThread("sampledThread") : e.getThread();
                String tname = th == null ? "?" : String.valueOf(th.getJavaName());
                if (tpre != null && !tname.startsWith(tpre)) continue;
                double w = switch (mode) {
                    case "park", "monitor" -> e.getDuration().toNanos() / 1e6;
                    case "alloc" -> e.getLong("weight") / 1e6;
                    default -> 1;
                };
                RecordedStackTrace st = e.getStackTrace();
                List<String> fs = new ArrayList<>();
                if (st != null) for (RecordedFrame f : st.getFrames())
                    fs.add(f.getMethod().getType().getName() + "." + f.getMethod().getName());
                if (focus != null && fs.stream().noneMatch(s -> s.contains(focus))) continue;
                total += w;
                aggs.get("threads").add(tname.replaceAll("[-#]?\\d+$", ""), w);
                if (fs.isEmpty()) continue;
                String status = "other";
                outer:
                for (int i = fs.size() - 1; i >= 0; i--)
                    for (String[] s : STATUS) if (fs.get(i).endsWith(s[0])) { status = s[1]; break outer; }
                aggs.get("status").add(status, w);
                conc.computeIfAbsent(status, k -> new HashMap<>())
                        .computeIfAbsent(e.getStartTime().toEpochMilli() / 100, k -> new HashSet<>()).add(tname);
                aggs.get("leaf").add(fs.get(0), w);
                Set<String> seen = new HashSet<>();
                for (String f : fs) if (f.startsWith("com.linc")) {
                    String c = f.substring(0, f.lastIndexOf('.'));
                    c = c.substring(c.lastIndexOf('.') + 1);
                    if (seen.add(c)) aggs.get("own_incl").add(c, w);
                }
                String own = fs.stream().filter(s -> s.startsWith("com.linc")).findFirst().orElse(null);
                if (own != null) {
                    aggs.get("first_own").add(shortName(own), w);
                    aggs.get("status_x_own").add(status + " | " + shortName(own), w);
                } else {
                    // nearest non-JDK frame for samples with no own code
                    String v = fs.stream().filter(s -> !s.startsWith("java.") && !s.startsWith("jdk.") && !s.startsWith("sun.")
                            && !s.startsWith("com.google.common")).findFirst().orElse(fs.get(0));
                    aggs.get("vanilla_hot").add(status + " | " + shortName(v), w);
                }
                if (fs.get(0).startsWith("com.linc")) aggs.get("own_self").add(shortName(fs.get(0)), w);
                // the structure whose placement check this is
                for (int i = 0; i < fs.size(); i++) {
                    if (fs.get(i).endsWith(".findGenerationPoint") && !fs.get(i).startsWith("net.minecraft.world.level.levelgen.structure.Structure.")) {
                        aggs.get("structure").add(status + " | " + shortName(fs.get(i)), w);
                        break;
                    }
                }
                // the placed feature being placed
                for (int i = fs.size() - 1; i >= 0; i--) {
                    if (fs.get(i).endsWith("ConfiguredFeature.place")) {
                        String callee = "?";
                        for (int j = i - 1; j >= 0; j--) {
                            String g = fs.get(j);
                            if (g.endsWith(".place") && !g.startsWith("net.minecraft.world.level.levelgen.feature.Feature.")) {
                                callee = shortName(g);
                                break;
                            }
                        }
                        aggs.get("feature").add(status + " | " + callee, w);
                        break;
                    }
                }
                // own call chain, root to leaf, first three distinct classes
                List<String> chain = new ArrayList<>();
                for (int i = fs.size() - 1; i >= 0 && chain.size() < 4; i--) {
                    String f = fs.get(i);
                    if (!f.startsWith("com.linc")) continue;
                    String c = f.substring(0, f.lastIndexOf('.'));
                    c = c.substring(c.lastIndexOf('.') + 1);
                    if (c.matches("[0-9]+") || c.contains("$$")) continue;
                    if (chain.isEmpty() || !chain.get(chain.size() - 1).equals(c)) chain.add(c);
                }
                if (!chain.isEmpty()) aggs.get("chain").add(status + " | " + String.join(">", chain), w);
            }
        }
        System.out.printf("== %s %s total=%.0f %s%n", file.getFileName(), event, total, mode.equals("cpu") ? "samples" : "ms");
        System.out.println("-- concurrency (step: windows with samples, mean / max distinct threads per 100 ms window)");
        conc.forEach((k, v) -> System.out.printf("   %-16s windows=%5d mean=%.2f max=%d%n", k, v.size(),
                v.values().stream().mapToInt(Set::size).average().orElse(0),
                v.values().stream().mapToInt(Set::size).max().orElse(0)));
        for (var en : aggs.entrySet()) {
            System.out.println("-- " + en.getKey());
            final double t = total;
            en.getValue().m().entrySet().stream().sorted(Map.Entry.<String, Double>comparingByValue().reversed()).limit(top)
                    .forEach(x -> System.out.printf("%9.0f %5.1f%%  %s%n", x.getValue(), 100 * x.getValue() / Math.max(1, t), x.getKey()));
        }
    }

    static String shortName(String f) {
        int dot = f.lastIndexOf('.');
        String cls = f.substring(0, dot);
        return cls.substring(cls.lastIndexOf('.') + 1) + "." + f.substring(dot + 1);
    }
}
