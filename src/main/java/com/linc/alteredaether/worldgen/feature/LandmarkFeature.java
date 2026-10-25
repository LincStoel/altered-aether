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

import com.linc.alteredaether.worldgen.landmark.Landmark;
import com.linc.alteredaether.worldgen.landmark.LandmarkField;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Builds the landmarks that reach into this chunk, in this chunk's own columns only, as the basin fill does: every
 * chunk works from the same {@link LandmarkField} sites, so a landmark that spans many chunks, or rises far above the
 * noise band, is put together piece by piece with no seams. Runs first of all the decoration, so everything after it
 * (ores, trees, snow) treats the landmarks as ground.
 */
public final class LandmarkFeature extends Feature<NoneFeatureConfiguration> {
    public LandmarkFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        LandmarkField field = LandmarkField.get(context.level().getLevel().dimension());
        if (field == null) {
            return false;
        }
        ChunkPos chunk = new ChunkPos(context.origin());
        boolean placed = false;
        for (Landmark site : field.touching(chunk.getMinBlockX(), chunk.getMinBlockZ(), chunk.getMaxBlockX(),
                chunk.getMaxBlockZ())) {
            site.build(context.level(), chunk);
            FeatureStats.placed("landmark/" + site.type().id());
            placed = true;
        }
        return placed;
    }
}
