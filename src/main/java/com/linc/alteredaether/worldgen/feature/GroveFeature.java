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
import com.linc.alteredaether.worldgen.grove.MireBuilder;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

import java.util.List;

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
        int x0 = chunk.getMinBlockX(), z0 = chunk.getMinBlockZ(), x1 = chunk.getMaxBlockX(), z1 = chunk.getMaxBlockZ();
        long start = System.nanoTime();
        List<Giant> giants = field.touching(x0, z0, x1, z1);
        // The seal looks a block past the chunk's edge, so it needs the lakes there too.
        MireBuilder.Waters waters = new MireBuilder.Waters(field.miresTouching(x0 - 1, z0 - 1, x1 + 1, z1 + 1));
        long planned = System.nanoTime();
        // The lakes first, so the giants' roots are built into the water; then trails on the bare ground; then the
        // wood; then what stands on it and hangs from it; and last the lakes' growth, and the seal round them.
        if (!waters.isEmpty()) {
            MireBuilder.dig(context.level(), x0, z0, x1, z1, waters);
        }
        long dug = System.nanoTime();
        for (Giant giant : giants) {
            GroveBuilder.paint(context.level(), x0, z0, x1, z1, giant);
        }
        for (Giant giant : giants) {
            GroveBuilder.build(context.level(), x0, z0, x1, z1, giant, waters);
            FeatureStats.placed("grove/" + giant.type().id());
        }
        for (Giant giant : giants) {
            GroveBuilder.furnish(context.level(), x0, z0, x1, z1, giant);
            GroveBuilder.hangVines(context.level(), x0, z0, x1, z1, giant);
        }
        long built = System.nanoTime();
        if (!waters.isEmpty()) {
            MireBuilder.dress(context.level(), x0, z0, x1, z1, waters);
            MireBuilder.seal(context.level(), x0, z0, x1, z1, waters);
            FeatureStats.placed("grove/mire");
        }
        FeatureStats.timed("grove_plan", planned - start);
        FeatureStats.timed("grove_mire", dug - planned + System.nanoTime() - built);
        FeatureStats.timed("grove_build", built - dug);
        return !giants.isEmpty() || !waters.isEmpty();
    }
}
