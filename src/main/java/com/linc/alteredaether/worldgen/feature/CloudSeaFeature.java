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

import com.linc.alteredaether.worldgen.sea.CloudSea;
import com.linc.alteredaether.worldgen.sea.CloudSeaMath;
import com.linc.alteredaether.worldgen.skirt.CloudSkirts;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * The sheet of cold aercloud over the tall Aether's cloud seas ({@link CloudSea}), laid in this chunk's own columns.
 * It fills only air, and leaves walled-in air alone (a cave in a root, a dungeon), as the cloud skirts do. Falling water
 * needs nothing from it: aercloud is no solid block to water, which washes its own channel down through.
 */
public final class CloudSeaFeature extends Feature<NoneFeatureConfiguration> {
    private static final int CELLS = 16 / CloudSea.STEP;

    public CloudSeaFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        CloudSea sea = CloudSea.get(level.getLevel().dimension());
        if (sea == null) {
            return false;
        }
        ChunkPos chunk = new ChunkPos(context.origin());
        long start = System.nanoTime();
        int placed = place(level, sea, chunk.getMinBlockX(), chunk.getMinBlockZ());
        FeatureStats.timed("cloud_sea", System.nanoTime() - start);
        if (placed > 0) {
            FeatureStats.placed("cloud_sea");
        }
        return placed > 0;
    }

    /** Lays the sheet in one chunk. Returns how many blocks were placed. */
    public static int place(WorldGenLevel level, CloudSea sea, int x0, int z0) {
        // The weight is broad and smooth: read it on a coarse grid and interpolate.
        double[] grid = new double[(CELLS + 1) * (CELLS + 1)];
        boolean any = false;
        for (int j = 0; j <= CELLS; j++) {
            for (int i = 0; i <= CELLS; i++) {
                double w = sea.weight(x0 + i * CloudSea.STEP, z0 + j * CloudSea.STEP);
                grid[j * (CELLS + 1) + i] = w;
                any |= w >= CloudSeaMath.MIN_WEIGHT;
            }
        }
        if (!any) {
            return 0;
        }
        BlockState cloud = CloudSkirts.cloud();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int[] span = new int[2];
        int placed = 0;
        for (int dz = 0; dz < 16; dz++) {
            for (int dx = 0; dx < 16; dx++) {
                int x = x0 + dx, z = z0 + dz;
                if (!sea.span(x, z, weight(grid, dx, dz), span)) {
                    continue;
                }
                for (int y = Math.max(span[0], level.getMinBuildHeight()); y <= span[1]; y++) {
                    pos.set(x, y, z);
                    if (!level.getBlockState(pos).isAir()
                            || !CloudSkirts.open(level, pos) && !CloudSkirts.opensNearby(level, pos)) {
                        continue;
                    }
                    level.setBlock(pos, cloud, Block.UPDATE_CLIENTS);
                    placed++;
                }
            }
        }
        return placed;
    }

    private static double weight(double[] grid, int dx, int dz) {
        int i = dx / CloudSea.STEP, j = dz / CloudSea.STEP;
        double fx = (dx % CloudSea.STEP) / (double) CloudSea.STEP, fz = (dz % CloudSea.STEP) / (double) CloudSea.STEP;
        int row = CELLS + 1;
        double a = grid[j * row + i], b = grid[j * row + i + 1];
        double c = grid[(j + 1) * row + i], d = grid[(j + 1) * row + i + 1];
        return (a + (b - a) * fx) * (1 - fz) + (c + (d - c) * fx) * fz;
    }
}
