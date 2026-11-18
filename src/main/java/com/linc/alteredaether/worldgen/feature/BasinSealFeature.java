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

import com.linc.alteredaether.worldgen.basin.BasinField;
import com.linc.alteredaether.worldgen.basin.BasinSite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Plugs whatever has been cut under a basin's water since {@link BasinFillFeature} filled it. The Aether's dungeons are
 * placed at the very last step of decoration, long after the fill, and their rooms can open under a lake's bed or
 * through its bank. This runs at that same step, after the structures, and fills every open block under or beside the
 * basin's water with the basin's own stone, so nothing drains away when the water first ticks. Only this chunk's
 * columns are written.
 */
public final class BasinSealFeature extends Feature<NoneFeatureConfiguration> {
    private static final Direction[] SIDES = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};

    public BasinSealFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        BasinField field = BasinField.get(level.getLevel().dimension());
        if (field == null) {
            return false;
        }
        ChunkPos chunk = new ChunkPos(context.origin());
        boolean placed = false;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (BasinSite site : field.sitesTouching(chunk.getMinBlockX(), chunk.getMinBlockZ(), chunk.getMaxBlockX(),
                chunk.getMaxBlockZ())) {
            BlockState seal = BasinFillFeature.seal(site.type());
            int water = site.waterLevel(), bottom = water - site.maxDepth() - 4;
            for (int x = chunk.getMinBlockX(); x <= chunk.getMaxBlockX(); x++) {
                for (int z = chunk.getMinBlockZ(); z <= chunk.getMaxBlockZ(); z++) {
                    if (site.reach(x, z) > 1.3) {
                        continue;
                    }
                    // Top down, so a block sealed here no longer counts as open beside the one under it.
                    for (int y = water; y >= bottom; y--) {
                        if (level.getBlockState(pos.set(x, y, z)).isAir() && besideWater(level, site, x, y, z)) {
                            level.setBlock(pos.set(x, y, z), seal, Block.UPDATE_CLIENTS);
                            placed = true;
                        }
                    }
                }
            }
        }
        return placed;
    }

    /** Whether the basin's own water lies over this block or beside it. */
    private static boolean besideWater(WorldGenLevel level, BasinSite site, int x, int y, int z) {
        if (site.inFootprint(x, z) && !level.getFluidState(new BlockPos(x, y + 1, z)).isEmpty()) {
            return true;
        }
        for (Direction side : SIDES) {
            int nx = x + side.getStepX(), nz = z + side.getStepZ();
            if (site.inFootprint(nx, nz) && !level.getFluidState(new BlockPos(nx, y, nz)).isEmpty()) {
                return true;
            }
        }
        return false;
    }
}
