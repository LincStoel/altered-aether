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
package com.linc.alteredaether.worldgen.grove;

import com.linc.alteredaether.config.AACommonConfig;
import com.linc.alteredaether.worldgen.skirt.CloudSkirts;
import com.linc.alteredaether.worldgen.skirt.SkirtGrid;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * The cloud round the Grandfather's neck: a wide lens of cold aercloud on the bare stretch of trunk under the high
 * crown, its top {@link #UNDER_CROWN} blocks below where the crown spreads, {@link #THICK} blocks thick at the trunk
 * and tapering out, sinking a little as it goes, to a billowy edge {@link #SHORT_OF_RING} blocks short of the ring of
 * children. Where it meets the lower canopy it fills only the air between the leaves.
 */
public final class NeckCloud {
    /** Blocks under the high crown's spread that the cloud's top sits, at the trunk. */
    public static final int UNDER_CROWN = 3;
    /** How far short of the nearest child tree the cloud reaches, before its edge wanders. */
    public static final double SHORT_OF_RING = 6;
    /** Thickness at the trunk. */
    public static final double THICK = 7;
    /** How far the cloud's middle sinks from the trunk to its edge. */
    public static final double DROOP = 4;
    /** How far the edge wanders in and out, as a share of the reach. */
    public static final double WANDER = 0.12;

    /** Lays the cloud in the given block columns, which a chunk may write. Returns how many blocks were placed. */
    public static int place(WorldGenLevel level, int minX, int minZ, int maxX, int maxZ, Giant giant) {
        List<BlockPos> neck = giant.points("neck"), reachAt = giant.points("neck_reach");
        if (neck.isEmpty() || reachAt.isEmpty() || !AACommonConfig.CLOUD_SKIRTS.get()) {
            return 0;
        }
        BlockPos centre = neck.getFirst();
        double reach = reachAt.getFirst().getX() - centre.getX();
        double outer = reach * (1 + WANDER);
        int cx = centre.getX(), cz = centre.getZ(), top = centre.getY();
        long seed = centre.asLong() * 0x9E3779B97F4A7C15L;
        BlockState cloud = CloudSkirts.cloud();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int placed = 0;
        int x0 = Math.max(minX, (int) Math.floor(cx - outer)), x1 = Math.min(maxX, (int) Math.ceil(cx + outer));
        int z0 = Math.max(minZ, (int) Math.floor(cz - outer)), z1 = Math.min(maxZ, (int) Math.ceil(cz + outer));
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                double d = Math.hypot(x + 0.5 - cx, z + 0.5 - cz);
                double edge = reach * (1 + WANDER * SkirtGrid.fbm(seed, x / 28.0, z / 28.0));
                double lens = SkirtGrid.smoothstep(1 - d / edge);
                if (lens <= 0) {
                    continue;
                }
                double middle = top - THICK / 2 - DROOP * SkirtGrid.smoothstep(d / edge);
                double half = THICK / 2 * lens;
                double high = middle + half + SkirtGrid.billowTop(seed, x, z) * lens;
                double low = middle - half + SkirtGrid.billowBottom(seed, x, z) * lens;
                int count = SkirtGrid.layers(low, high), lowest = SkirtGrid.lowest(low, high);
                for (int y = lowest; y < lowest + count; y++) {
                    pos.set(x, y, z);
                    if (!level.isOutsideBuildHeight(y) && level.getBlockState(pos).isAir()) {
                        level.setBlock(pos, cloud, Block.UPDATE_CLIENTS);
                        placed++;
                    }
                }
            }
        }
        return placed;
    }

    private NeckCloud() {
    }
}
