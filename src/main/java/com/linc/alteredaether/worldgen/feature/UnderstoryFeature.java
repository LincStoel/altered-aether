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
package com.linc.alteredaether.worldgen.feature;

import com.linc.alteredaether.config.AACommonConfig;
import com.linc.alteredaether.worldgen.grove.Understory;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Grows the young trees and ground cover between the giants in this chunk ({@link Understory}). Listed by the two
 * grove biomes in place of the stock forest's trees, once per chunk; it checks each column's biome itself.
 */
public final class UnderstoryFeature extends Feature<NoneFeatureConfiguration> {
    public UnderstoryFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        double density = AACommonConfig.UNDERSTORY.get();
        if (density <= 0) {
            return false;
        }
        ChunkPos chunk = new ChunkPos(context.origin());
        long start = System.nanoTime();
        boolean placed = Understory.place(context.level(), context.random(), chunk.getMinBlockX(),
                chunk.getMinBlockZ(), density);
        if (placed) {
            FeatureStats.placed("grove/understory");
        }
        FeatureStats.timed("grove_understory", System.nanoTime() - start);
        return placed;
    }
}
