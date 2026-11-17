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

import com.linc.alteredaether.worldgen.skirt.CloudSkirts;
import com.linc.alteredaether.worldgen.skirt.SkirtField;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * The cloud skirts round the low islands of the tall Aether, laid in this chunk's own columns ({@link CloudSkirts}).
 * Runs in every Aether chunk, last of all, after the cloud sea. Worlds without a
 * {@link SkirtField} (the stock-height Aether) get none.
 */
public final class CloudSkirtFeature extends Feature<NoneFeatureConfiguration> {
    public CloudSkirtFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        SkirtField field = CloudSkirts.field(context.level());
        if (field == null) {
            return false;
        }
        ChunkPos chunk = new ChunkPos(context.origin());
        long start = System.nanoTime();
        int placed = CloudSkirts.place(context.level(), field, chunk.getMinBlockX(), chunk.getMinBlockZ());
        FeatureStats.timed("cloud_skirt", System.nanoTime() - start);
        if (placed > 0) {
            FeatureStats.placed("cloud_skirt");
        }
        return placed > 0;
    }
}
