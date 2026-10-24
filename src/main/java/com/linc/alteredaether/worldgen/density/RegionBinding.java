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
import net.minecraft.world.level.levelgen.DensityFunction;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import terrablender.worldgen.IExtendedParameterList;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.Collections;
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
        level.getChunkSource().randomState().router().mapAll(new DensityFunction.Visitor() {
            @Override
            public DensityFunction apply(DensityFunction function) {
                if (function instanceof RegionMask mask && mask.binding() != null) {
                    bindings.add(mask.binding());
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
        } else {
            AlteredAether.LOGGER.warn("{} uses Altered Aether's region mask but its biome source has no TerraBlender "
                    + "regions; our landforms are disabled there.", level.dimension().location());
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            SOLVERS.remove(level.dimension());
        }
    }

    private RegionBinding() {
    }
}
