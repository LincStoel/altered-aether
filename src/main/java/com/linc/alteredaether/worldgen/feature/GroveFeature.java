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

import com.linc.alteredaether.worldgen.grove.Giant;
import com.linc.alteredaether.worldgen.grove.GroveBuilder;
import com.linc.alteredaether.worldgen.grove.GroveField;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Builds the giant trees, and the roots and bridges joining them, that reach into this chunk, in this chunk's own
 * columns only, like the landmarks. Runs with the raw generation, before all other decoration, so everything after
 * it treats the giants as ground.
 */
public final class GroveFeature extends Feature<NoneFeatureConfiguration> {
    public GroveFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        GroveField field = GroveField.get(context.level().getLevel().dimension());
        if (field == null) {
            return false;
        }
        ChunkPos chunk = new ChunkPos(context.origin());
        boolean placed = false;
        for (Giant giant : field.touching(chunk.getMinBlockX(), chunk.getMinBlockZ(), chunk.getMaxBlockX(),
                chunk.getMaxBlockZ())) {
            GroveBuilder.build(context.level(), chunk.getMinBlockX(), chunk.getMinBlockZ(), chunk.getMaxBlockX(),
                    chunk.getMaxBlockZ(), giant);
            FeatureStats.placed("grove/" + giant.type().id());
            placed = true;
        }
        return placed;
    }
}
