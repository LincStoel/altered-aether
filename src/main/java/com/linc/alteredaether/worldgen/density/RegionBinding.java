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
package com.linc.alteredaether.worldgen.density;

import com.linc.alteredaether.AlteredAether;
import com.linc.alteredaether.config.AACommonConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import com.linc.alteredaether.worldgen.basin.BasinField;
import com.linc.alteredaether.worldgen.TerrainProbe;
import com.linc.alteredaether.worldgen.grove.BreakField;
import com.linc.alteredaether.worldgen.grove.GroveField;
import com.linc.alteredaether.worldgen.landmark.LandmarkField;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import terrablender.worldgen.IExtendedParameterList;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Hands each world's TerraBlender region map to the {@link RegionMask}s in its noise router. Runs on level load,
 * before any chunk of that level is generated. The parameter list is bound even if TerraBlender hasn't populated it
 * yet; the mask checks {@code isInitialized()} on every lookup.
 */
@EventBusSubscriber(modid = AlteredAether.MOD_ID)
public final class RegionBinding {
    private static final Map<ResourceKey<Level>, RegionMask.Solver> SOLVERS = new ConcurrentHashMap<>();

    /** The region mask at a point in a loaded level, for debugging; 0 where the level has no mask. */
    public static double maskAt(ServerLevel level, int x, int z) {
        RegionMask.Solver solver = SOLVERS.get(level.dimension());
        return solver == null ? 0 : solver.sample(x, z);
    }

    @SubscribeEvent
    public static void onLevelLoad(LevelEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        Set<RegionMask.Binding> bindings = Collections.newSetFromMap(new IdentityHashMap<>());
        List<BasinCarve> carves = new ArrayList<>();
        List<Atoll> atolls = new ArrayList<>();
        Set<VoronoiFracture.Binding> fractures = Collections.newSetFromMap(new IdentityHashMap<>());
        List<GroveBreak> breaks = new ArrayList<>();
        RandomState randomState = level.getChunkSource().randomState();
        randomState.router().mapAll(new DensityFunction.Visitor() {
            @Override
            public DensityFunction apply(DensityFunction function) {
                if (function instanceof RegionMask mask && mask.binding() != null) {
                    bindings.add(mask.binding());
                } else if (function instanceof BasinCarve carve && carve.binding() != null) {
                    carves.add(carve);
                } else if (function instanceof Atoll atoll && atoll.binding() != null) {
                    atolls.add(atoll);
                } else if (function instanceof VoronoiFracture fracture && fracture.binding() != null) {
                    fractures.add(fracture.binding());
                } else if (function instanceof GroveBreak groveBreak && groveBreak.binding() != null) {
                    breaks.add(groveBreak);
                }
                return function;
            }
        });
        if (bindings.isEmpty()) {
            return;
        }
        BiomeSource source = level.getChunkSource().getGenerator().getBiomeSource();
        if (source instanceof MultiNoiseBiomeSource multiNoise
                && multiNoise.parameters() instanceof IExtendedParameterList<?> parameters) {
            RegionMask.Solver solver = new RegionMask.Solver(parameters, AACommonConfig.REGION_FALLOFF.get());
            bindings.forEach(binding -> binding.bind(solver));
            SOLVERS.put(level.dimension(), solver);
            AlteredAether.LOGGER.info("Bound Altered Aether's region mask for {}", level.dimension().location());
            bindFields(level, randomState, source, carves, atolls, fractures, breaks);
        } else {
            AlteredAether.LOGGER.warn("{} uses Altered Aether's region mask but its biome source has no TerraBlender "
                    + "regions; our landforms are disabled there.", level.dimension().location());
        }
    }

    /**
     * Builds the world's basin and landmark fields from the carve, atoll and fracture functions in its router. The
     * carve's argument, as wired here, is the exact uninterpolated terrain before any basin is cut, which is what
     * sites are checked against.
     */
    private static void bindFields(ServerLevel level, RandomState randomState, BiomeSource source,
                                   List<BasinCarve> carves, List<Atoll> atolls, Set<VoronoiFracture.Binding> fractures,
                                   List<GroveBreak> breaks) {
        if (carves.isEmpty() && atolls.isEmpty() && fractures.isEmpty()
                || !(level.getChunkSource().getGenerator() instanceof NoiseBasedChunkGenerator generator)) {
            return;
        }
        NoiseSettings noise = generator.generatorSettings().value().noiseSettings();
        BasinCarve carve = carves.isEmpty() ? null : carves.getFirst();
        Atoll atoll = atolls.isEmpty() ? null : atolls.getFirst();
        TerrainProbe probe = new TerrainProbe(source, randomState.sampler(), carve == null ? null : carve.argument(),
                atoll == null ? null : atoll.weight(), noise.minY(), noise.minY() + noise.height());
        double vertical = carve != null ? carve.verticalScale() : atoll != null ? atoll.verticalScale() : 1;
        BasinField basins = null;
        if (carve != null || atoll != null) {
            // The atolls read their height off the land as it is before any of them is laid over it.
            TerrainProbe hosts = atoll == null ? null : new TerrainProbe(source, randomState.sampler(), atoll.argument(),
                    atoll.weight(), noise.minY(), noise.minY() + noise.height());
            BasinField field = new BasinField(level.getSeed(), probe, hosts, vertical,
                    atoll == null ? null : new BasinField.AtollSettings(atoll.surface(), atoll.verticalScale()));
            carves.forEach(c -> c.binding().bind(field));
            atolls.forEach(a -> a.binding().bind(field));
            BasinField.put(level.dimension(), field);
            basins = field;
        }
        // The grove breaks, checked against the unbroken grove islands: their own function's argument.
        BreakField breakField = null;
        if (!breaks.isEmpty()) {
            GroveBreak first = breaks.getFirst();
            breakField = new BreakField(level.getSeed(), new TerrainProbe(source, randomState.sampler(),
                    first.argument(), atoll == null ? null : atoll.weight(), noise.minY(), noise.minY() + noise.height()),
                    first.verticalScale());
            BreakField bound = breakField;
            breaks.forEach(b -> b.binding().bind(bound));
        }
        LandmarkField landmarks = new LandmarkField(level.getSeed(), probe, basins, vertical, level.getMaxBuildHeight());
        fractures.forEach(f -> f.bind(landmarks));
        LandmarkField.put(level.dimension(), landmarks);
        GroveField.put(level.dimension(), new GroveField(level.getSeed(), probe, landmarks, breakField, vertical,
                level.getMaxBuildHeight()));
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            SOLVERS.remove(level.dimension());
            BasinField.put(level.dimension(), null);
            LandmarkField.put(level.dimension(), null);
            GroveField.put(level.dimension(), null);
        }
    }

    private RegionBinding() {
    }
}
