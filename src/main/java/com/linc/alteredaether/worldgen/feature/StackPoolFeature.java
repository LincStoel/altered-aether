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

import com.linc.alteredaether.worldgen.landmark.StackField;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Fills the dish on top of a plateau sea stack that holds a pool ({@link StackField}): water up to a block under the
 * rim, wherever the dish is open and there is rock beneath. Runs in every Aether chunk, over its own columns.
 */
public final class StackPoolFeature extends Feature<NoneFeatureConfiguration> {
    public StackPoolFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        StackField field = StackField.get(level.getLevel().dimension());
        if (field == null) {
            return false;
        }
        int minX = context.origin().getX() & ~15, minZ = context.origin().getZ() & ~15;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        boolean placed = false;
        for (StackField.Stack stack : field.touching(minX, minZ, minX + 15, minZ + 15)) {
            if (stack.pond() <= 0) {
                continue;
            }
            for (int x = minX; x <= minX + 15; x++) {
                for (int z = minZ; z <= minZ + 15; z++) {
                    double dx = x + 0.5 - stack.x(), dz = z + 0.5 - stack.z();
                    if (dx * dx + dz * dz >= (stack.radius() - 2.5) * (stack.radius() - 2.5)) {
                        continue;
                    }
                    int floor = stack.top() - stack.pond();
                    if (!level.getBlockState(pos.set(x, floor, z)).isSolid()) {
                        continue;
                    }
                    for (int y = floor + 1; y < stack.top(); y++) {
                        if (level.getBlockState(pos.set(x, y, z)).isAir()) {
                            level.setBlock(pos, Blocks.WATER.defaultBlockState(), Block.UPDATE_CLIENTS);
                            placed = true;
                        }
                    }
                }
            }
        }
        if (placed) {
            FeatureStats.placed("stack_pool");
        }
        return placed;
    }
}
