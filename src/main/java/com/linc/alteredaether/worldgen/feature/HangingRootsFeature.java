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
import com.linc.alteredaether.worldgen.grove.HangingRoots;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Hangs roots under the grove islands, in this chunk's own columns ({@link HangingRoots}). Runs in every Aether chunk
 * after the giants are built, so it finds their great roots too, and picks out the grove columns itself.
 */
public final class HangingRootsFeature extends Feature<NoneFeatureConfiguration> {
    public HangingRootsFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        double density = AACommonConfig.HANGING_ROOTS.get();
        if (density <= 0) {
            return false;
        }
        ChunkPos chunk = new ChunkPos(context.origin());
        long start = System.nanoTime();
        boolean placed = HangingRoots.place(context.level(), context.random(), chunk.getMinBlockX(),
                chunk.getMinBlockZ(), density);
        FeatureStats.timed("grove_hanging_roots", System.nanoTime() - start);
        return placed;
    }
}
