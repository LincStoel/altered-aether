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
import com.linc.alteredaether.worldgen.basin.BasinField;
import com.linc.alteredaether.worldgen.basin.BasinSite;
import com.linc.alteredaether.worldgen.density.RegionBinding;
import com.linc.alteredaether.worldgen.landmark.LandmarkField;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.synth.BlendedNoise;
import net.minecraft.world.level.levelgen.LegacyRandomSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Samples a world's Aether without generating chunks: which biome sits where, how much of the area our region
 * covers, and what the terrain column looks like under each biome. Landforms show up as differences between biomes:
 * crags and peaks reach higher and fill more of their column, the plateau is high and flat, the rift is more often
 * empty.
 */
public final class WorldgenReport {
    private static final class Stats {
        int samples;
        int inRegion;
        double maskSum;
        int columnsWithLand;
        long solidBlocks;
        long topSum;
        int topMin = Integer.MAX_VALUE;
        int topMax = Integer.MIN_VALUE;
        int probeLand;
        long probeTopSum;
        long probeSolid;
    }

    /**
     * @param radius blocks from the origin to sample out to
     * @param step   blocks between samples
     */
    public static List<String> run(ServerLevel level, int originX, int originZ, int radius, int step) {
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        RandomState random = level.getChunkSource().randomState();
        Map<String, Stats> byBiome = new TreeMap<>();
        Stats all = new Stats();
        int minY = level.getMinBuildHeight();
        DensityFunction probe = probe(level, random, "terrain/crag_tier");

        for (int x = originX - radius; x <= originX + radius; x += step) {
            for (int z = originZ - radius; z <= originZ + radius; z += step) {
                Holder<Biome> biome = generator.getBiomeSource().getNoiseBiome(QuartPos.fromBlock(x),
                        QuartPos.fromBlock(64), QuartPos.fromBlock(z), random.sampler());
                String id = biome.unwrapKey().map(ResourceKey::location).map(Object::toString).orElse("?");
                Stats stats = byBiome.computeIfAbsent(id, k -> new Stats());
                double mask = RegionBinding.maskAt(level, x, z);

                NoiseColumn column = generator.getBaseColumn(x, z, level, random);
                int solid = 0, top = Integer.MIN_VALUE;
                for (int y = minY; y < minY + level.getHeight(); y++) {
                    if (!column.getBlock(y).isAir() && column.getBlock(y).getFluidState().isEmpty()) {
                        solid++;
                        top = y;
                    }
                }
                int probeSolid = 0, probeTop = Integer.MIN_VALUE;
                if (probe != null) {
                    for (int y = minY; y < minY + level.getHeight(); y += 2) {
                        if (probe.compute(new DensityFunction.SinglePointContext(x, y, z)) > 0) {
                            probeSolid += 2;
                            probeTop = y;
                        }
                    }
                }
                for (Stats s : new Stats[]{stats, all}) {
                    if (probeSolid > 0) {
                        s.probeLand++;
                        s.probeSolid += probeSolid;
                        s.probeTopSum += probeTop;
                    }
                    s.samples++;
                    s.maskSum += mask;
                    if (mask > 0) {
                        s.inRegion++;
                    }
                    if (solid > 0) {
                        s.columnsWithLand++;
                        s.solidBlocks += solid;
                        s.topSum += top;
                        s.topMin = Math.min(s.topMin, top);
                        s.topMax = Math.max(s.topMax, top);
                    }
                }
            }
        }

        List<String> lines = new ArrayList<>();
        lines.add(String.format("Worldgen report for %s around (%d, %d), radius %d, step %d: %d samples, %.1f%% in "
                        + "Altered Aether's region", level.dimension().location(), originX, originZ, radius, step,
                all.samples, 100.0 * all.inRegion / all.samples));
        lines.add(String.format("%-44s %7s %8s %5s %7s %9s %13s   %s", "biome", "share", "inRegion", "mask", "land",
                "solid/col", "top avg[min,max]", "crag tier alone: land solid/col top"));
        byBiome.forEach((id, s) -> lines.add(String.format("%-44s %6.2f%% %7.0f%% %5.2f %6.0f%% %9.1f %6.0f [%d,%d]   %5.0f%% %5.1f %4.0f",
                id, 100.0 * s.samples / all.samples, 100.0 * s.inRegion / s.samples, s.maskSum / s.samples,
                100.0 * s.columnsWithLand / s.samples,
                s.columnsWithLand == 0 ? 0 : (double) s.solidBlocks / s.columnsWithLand,
                s.columnsWithLand == 0 ? 0 : (double) s.topSum / s.columnsWithLand,
                s.columnsWithLand == 0 ? 0 : s.topMin, s.columnsWithLand == 0 ? 0 : s.topMax,
                100.0 * s.probeLand / s.samples, s.probeLand == 0 ? 0 : (double) s.probeSolid / s.probeLand,
                s.probeLand == 0 ? 0 : (double) s.probeTopSum / s.probeLand)));
        String climate = System.getProperty("altered_aether.report.climate", "");
        if (!climate.isEmpty()) {
            climateDump(level, originX, originZ, radius, Math.max(1, step / 4), java.nio.file.Path.of(climate), lines);
        }
        basinSurvey(level, originX, originZ, radius, lines);
        landmarkSurvey(level, originX, originZ, radius, lines);
        if (Boolean.getBoolean("altered_aether.report.structures")) {
            structureHeights(level, originX, originZ, lines);
        }
        lines.forEach(AlteredAether.LOGGER::info);
        return lines;
    }

    /**
     * Writes the climate (t, h, c, e, w) at every in-region sample to a CSV, at a finer step than the column survey
     * since it generates nothing. For sizing BiomeTable's windows against the real distribution.
     */
    private static void climateDump(ServerLevel level, int originX, int originZ, int radius, int step,
                                    java.nio.file.Path out, List<String> lines) {
        var sampler = level.getChunkSource().randomState().sampler();
        StringBuilder csv = new StringBuilder("t,h,c,e,w\n");
        int rows = 0;
        for (int x = originX - radius; x <= originX + radius; x += step) {
            for (int z = originZ - radius; z <= originZ + radius; z += step) {
                if (RegionBinding.maskAt(level, x, z) <= 0) {
                    continue;
                }
                var p = sampler.sample(QuartPos.fromBlock(x), QuartPos.fromBlock(64), QuartPos.fromBlock(z));
                csv.append(String.format(java.util.Locale.ROOT, "%.4f,%.4f,%.4f,%.4f,%.4f%n",
                        Climate.unquantizeCoord(p.temperature()), Climate.unquantizeCoord(p.humidity()),
                        Climate.unquantizeCoord(p.continentalness()), Climate.unquantizeCoord(p.erosion()),
                        Climate.unquantizeCoord(p.weirdness())));
                rows++;
            }
        }
        try {
            java.nio.file.Files.writeString(out, csv);
            lines.add(String.format("Climate: %d in-region samples at step %d written to %s", rows, step,
                    out.toAbsolutePath()));
        } catch (java.io.IOException e) {
            lines.add("Climate: could not write " + out + ": " + e);
        }
    }

    /**
     * The nearest of each Aether and Deep Aether structure, with its bounding box against the ground under its
     * centre: a check that the tall-Aether height remap puts structures where they belong. Only structure starts
     * are generated, never whole chunks.
     */
    private static void structureHeights(ServerLevel level, int originX, int originZ, List<String> lines) {
        var registry = level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE);
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        RandomState random = level.getChunkSource().randomState();
        lines.add(String.format("%-44s %-22s %-14s %s", "structure", "at", "box y", "ground under centre"));
        registry.holders().filter(h -> {
            String ns = h.key().location().getNamespace();
            return ns.equals("aether") || ns.equals("deep_aether") || ns.equals("altered_aether");
        }).sorted(java.util.Comparator.comparing(h -> h.key().location().toString())).forEach(holder -> {
            var found = generator.findNearestMapStructure(level, net.minecraft.core.HolderSet.direct(holder),
                    new net.minecraft.core.BlockPos(originX, 64, originZ), 160, false);
            String id = holder.key().location().toString();
            if (found == null) {
                lines.add(String.format("%-44s none within 160 chunks", id));
                return;
            }
            var pos = found.getFirst();
            var chunk = level.getChunk(pos.getX() >> 4, pos.getZ() >> 4,
                    net.minecraft.world.level.chunk.status.ChunkStatus.STRUCTURE_STARTS);
            var start = chunk.getStartForStructure(holder.value());
            if (start == null || !start.isValid()) {
                lines.add(String.format("%-44s %-22s no start", id, pos.toShortString()));
                return;
            }
            var box = start.getBoundingBox();
            int cx = box.getCenter().getX(), cz = box.getCenter().getZ();
            NoiseColumn column = generator.getBaseColumn(cx, cz, level, random);
            List<String> ground = new ArrayList<>();
            boolean wasSolid = false;
            for (int y = level.getMaxBuildHeight() - 1; y >= level.getMinBuildHeight(); y--) {
                boolean solid = !column.getBlock(y).isAir();
                if (solid && !wasSolid) {
                    ground.add(String.valueOf(y));
                }
                wasSolid = solid;
            }
            lines.add(String.format("%-44s %-22s %-14s tops %s", id, cx + "," + cz, box.minY() + ".." + box.maxY(),
                    ground.isEmpty() ? "void" : String.join(" ", ground.subList(0, Math.min(4, ground.size())))));
        });
    }

    /** Every basin and atoll site over the area, validated without generating anything, and why candidates failed. */
    private static void basinSurvey(ServerLevel level, int originX, int originZ, int radius, List<String> lines) {
        BasinField field = BasinField.get(level.dimension());
        if (field == null) {
            lines.add("Basins: no basin field in this world");
            return;
        }
        List<BasinSite> sites = field.sitesTouching(originX - radius, originZ - radius, originX + radius,
                originZ + radius);
        Map<String, List<BasinSite>> byType = new TreeMap<>();
        // Ring sites (a river round an open middle) are listed apart from the lagoons of their type.
        sites.forEach(site -> byType.computeIfAbsent(site.type().name().toLowerCase() + (site.isRiver() ? "_ring" : ""),
                k -> new ArrayList<>()).add(site));
        lines.add(String.format("Basins: %d sites; rejected %s", sites.size(), field.rejections()));
        byType.forEach((type, list) -> lines.add(String.format("  %-16s %4d  radius avg %.1f  water level avg %.0f  e.g. %s",
                type, list.size(), list.stream().mapToDouble(BasinSite::radius).average().orElse(0),
                list.stream().mapToInt(BasinSite::waterLevel).average().orElse(0),
                list.stream().limit(3).map(site -> site.x() + "," + site.z()).toList())));
    }

    /** Every landmark over the area by type, with how each turned out where that varies. */
    private static void landmarkSurvey(ServerLevel level, int originX, int originZ, int radius, List<String> lines) {
        LandmarkField field = LandmarkField.get(level.dimension());
        if (field == null) {
            lines.add("Landmarks: no landmark field in this world");
            return;
        }
        Map<String, Map<String, Integer>> byType = new TreeMap<>();
        field.touching(originX - radius, originZ - radius, originX + radius, originZ + radius).forEach(site ->
                byType.computeIfAbsent(site.type().id(), k -> new TreeMap<>()).merge(site.detail(), 1, Integer::sum));
        lines.add("Landmarks:");
        byType.forEach((type, details) -> lines.add(String.format("  %-16s %4d  %s", type,
                details.values().stream().mapToInt(Integer::intValue).sum(), details.keySet().equals(java.util.Set.of(""))
                        ? "" : details)));
    }

    /**
     * One of our terrain density functions on its own, wired to this world's noises, for comparing a landform's raw
     * shape with what the blended terrain makes of it. Blended noises get a fixed seed; the shape statistics don't
     * depend on it.
     */
    private static DensityFunction probe(ServerLevel level, RandomState random, String name) {
        return level.registryAccess().registryOrThrow(Registries.DENSITY_FUNCTION)
                .getOptional(AlteredAether.id(name))
                .map(function -> function.mapAll(new DensityFunction.Visitor() {
                    @Override
                    public DensityFunction apply(DensityFunction f) {
                        return f instanceof BlendedNoise blended
                                ? blended.withNewRandom(new LegacyRandomSource(level.getSeed())) : f;
                    }

                    @Override
                    public DensityFunction.NoiseHolder visitNoise(DensityFunction.NoiseHolder holder) {
                        return holder.noiseData().unwrapKey()
                                .map(key -> new DensityFunction.NoiseHolder(holder.noiseData(),
                                        random.getOrCreateNoise(key)))
                                .orElse(holder);
                    }
                }))
                .orElse(null);
    }

    private WorldgenReport() {
    }
}
