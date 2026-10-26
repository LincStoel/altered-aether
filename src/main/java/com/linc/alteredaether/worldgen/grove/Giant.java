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

import com.linc.alteredaether.worldgen.grove.shape.Bounds;
import com.linc.alteredaether.worldgen.grove.shape.Carve;
import com.linc.alteredaether.worldgen.grove.shape.LeafCluster;
import com.linc.alteredaether.worldgen.grove.shape.Part;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * One giant tree, or the link between two, as a finished plan: its wood, the spaces cut out of it and its foliage,
 * all pure shapes, so any chunk can build its own share of it with no seams ({@link GroveBuilder}).
 *
 * @param x           the tree's centre
 * @param z           the tree's centre
 * @param base        the ground the tree stands on
 * @param footRadius  how far its trunk reaches at the ground, where links attach
 * @param crown       the top of its crown, where a canopy bridge may meet it, or MIN_VALUE without one
 * @param points      spots for decoration and finding things, by kind
 * @param mire        the swamp lake round its foot as it planned it, before its neighbours are known, or null
 */
public record Giant(GiantType type, int x, int z, int base, double footRadius, int crown, List<Part> wood,
                    List<Carve> carves, List<LeafCluster> leaves, List<Prop> props, List<PathStroke> paths,
                    Map<String, List<BlockPos>> points, GrovePalette palette, Bounds bounds, long salt,
                    @Nullable Mire mire) {
    /** How far the plan reaches from its centre, horizontally. */
    public double extent() {
        return Math.max(Math.max(x - bounds.minX(), bounds.maxX() - x), Math.max(z - bounds.minZ(), bounds.maxZ() - z));
    }

    public List<BlockPos> points(String kind) {
        return points.getOrDefault(kind, List.of());
    }
}
