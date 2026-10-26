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

import com.linc.alteredaether.worldgen.grove.shape.Carve;
import com.linc.alteredaether.worldgen.grove.shape.LeafCluster;
import com.linc.alteredaether.worldgen.grove.shape.Part;
import com.linc.alteredaether.worldgen.basin.BasinField;
import com.linc.alteredaether.worldgen.grove.shape.Bounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds one giant's share of a block rectangle, usually a chunk. Each block is decided from the plan's shapes alone:
 * wood where a part claims it and no carve does, air where a carve clears it, leaves in the air a foliage clump claims.
 * Every chunk reaches the same answer for the same block, so a giant comes together across chunks with no seams.
 */
public final class GroveBuilder {
    /** Taxicab reach past which no leaf is placed: leaves further than six steps from a log would decay. */
    private static final double LEAF_REACH = 5.6;
    /** Wood within this far of a carve's surface is stripped: the heartwood inside a hollow. */
    private static final double STRIP_DEPTH = 1.5;
    private static final Block DIRT_PATH = BuiltInRegistries.BLOCK
            .getOptional(ResourceLocation.parse("aether:aether_dirt_path")).orElse(Blocks.DIRT_PATH);

    /**
     * Treads a giant's trails into the ground. Runs before any giant in the chunk is built, so the surface it reads is
     * still the bare ground.
     */
    public static void paint(WorldGenLevel level, int minX, int minZ, int maxX, int maxZ, Giant giant) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (PathStroke path : giant.paths()) {
            Bounds b = path.bounds();
            for (int x = Math.max(minX, b.minX()); x <= Math.min(maxX, b.maxX()); x++) {
                for (int z = Math.max(minZ, b.minZ()); z <= Math.min(maxZ, b.maxZ()); z++) {
                    // A ragged edge, and on a forgotten trail, gaps where the grass has come back.
                    double edge = path.halfWidth() * (0.75 + 0.5 * hash(path.salt(), x, 0, z));
                    if (path.distance(x, z) > edge || hash(path.salt(), x, 1, z) >= path.coverage()) {
                        continue;
                    }
                    pos.set(x, level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1, z);
                    BlockState top = level.getBlockState(pos);
                    if (!top.is(BlockTags.DIRT) && !top.isAir() && top.canBeReplaced()) {
                        // A plant on the surface: the ground is the block beneath it.
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                        pos.move(0, -1, 0);
                        top = level.getBlockState(pos);
                    }
                    if (top.is(BlockTags.DIRT)) {
                        level.setBlock(pos, DIRT_PATH.defaultBlockState(), 2);
                    }
                }
            }
        }
    }

    /** Places a giant's props: lamps, lanterns, camps, chests, growth. Runs after every giant in the chunk is built. */
    public static void furnish(WorldGenLevel level, int minX, int minZ, int maxX, int maxZ, Giant giant) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (Prop prop : giant.props()) {
            if (prop.x() < minX || prop.x() > maxX || prop.z() < minZ || prop.z() > maxZ) {
                continue;
            }
            int y = switch (prop.anchor()) {
                case AT -> prop.y();
                case GROUND, TOPSOIL -> top(level, pos, prop);
                case HANG -> underside(level, pos, prop);
            };
            if (y == Integer.MIN_VALUE) {
                continue;
            }
            for (int i = 0; i < prop.blocks().size(); i++) {
                int at = prop.anchor() == Prop.Anchor.HANG ? y - i : y + i;
                pos.set(prop.x(), at, prop.z());
                BlockState here = level.getBlockState(pos);
                boolean soil = prop.anchor() == Prop.Anchor.TOPSOIL && i == 0;
                if (!soil && !here.isAir() && !here.canBeReplaced()) {
                    break;
                }
                level.setBlock(pos, prop.blocks().get(i), 2);
                if (i == 0 && prop.loot() != null) {
                    RandomizableContainer.setBlockEntityLootTable(level,
                            RandomSource.create(giant.salt() ^ pos.asLong()), pos, prop.loot());
                }
            }
        }
    }

    /** Where a standing prop goes: just above the first solid top below, or for topsoil, the top itself. */
    private static int top(WorldGenLevel level, BlockPos.MutableBlockPos pos, Prop prop) {
        for (int y = prop.y() + Prop.REACH; y >= prop.y() - Prop.REACH; y--) {
            BlockState state = level.getBlockState(pos.set(prop.x(), y, prop.z()));
            if (state.isAir() || state.canBeReplaced() || state.getBlock() instanceof LeavesBlock) {
                continue;
            }
            BlockState above = level.getBlockState(pos.set(prop.x(), y + 1, prop.z()));
            if (!above.isAir() && !above.canBeReplaced()) {
                return Integer.MIN_VALUE;
            }
            return prop.anchor() == Prop.Anchor.TOPSOIL ? y : y + 1;
        }
        return Integer.MIN_VALUE;
    }

    /** Where a hanging prop's first block goes: just below the first solid underside above. */
    private static int underside(WorldGenLevel level, BlockPos.MutableBlockPos pos, Prop prop) {
        for (int y = prop.y(); y <= prop.y() + Prop.REACH; y++) {
            BlockState state = level.getBlockState(pos.set(prop.x(), y, prop.z()));
            if (!state.isAir() && !state.canBeReplaced() && !(state.getBlock() instanceof LeavesBlock)) {
                return y == prop.y() ? Integer.MIN_VALUE : y - 1;
            }
        }
        return Integer.MIN_VALUE;
    }

    /** A fixed value in [0, 1) for a block. */
    static double hash(long salt, int x, int y, int z) {
        long h = BasinField.mix(salt ^ BasinField.mix(x * 0x9E3779B97F4A7C15L ^ y * 0xC2B2AE3D27D4EB4FL ^ z));
        return (h >>> 11) * 0x1.0p-53;
    }

    public static void build(WorldGenLevel level, int minX, int minZ, int maxX, int maxZ, Giant giant) {
        int x0 = Math.max(minX, giant.bounds().minX()), x1 = Math.min(maxX, giant.bounds().maxX());
        int z0 = Math.max(minZ, giant.bounds().minZ()), z1 = Math.min(maxZ, giant.bounds().maxZ());
        List<Part> wood = new ArrayList<>();
        List<Carve> carves = new ArrayList<>();
        List<LeafCluster> leaves = new ArrayList<>();
        double[] leaf = new double[2];
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        GrovePalette palette = giant.palette();
        for (int bx = x0; bx <= x1; bx++) {
            for (int bz = z0; bz <= z1; bz++) {
                int top = Integer.MIN_VALUE, bottom = Integer.MAX_VALUE;
                wood.clear();
                carves.clear();
                leaves.clear();
                for (Part part : giant.wood()) {
                    if (part.bounds().column(bx, bz)) {
                        wood.add(part);
                        top = Math.max(top, part.bounds().maxY());
                        bottom = Math.min(bottom, part.bounds().minY());
                    }
                }
                for (Carve carve : giant.carves()) {
                    if (carve.bounds().column(bx, bz)) {
                        carves.add(carve);
                        top = Math.max(top, carve.bounds().maxY());
                        bottom = Math.min(bottom, carve.bounds().minY());
                    }
                }
                for (LeafCluster cluster : giant.leaves()) {
                    if (cluster.bounds().column(bx, bz)) {
                        leaves.add(cluster);
                        top = Math.max(top, cluster.bounds().maxY());
                        bottom = Math.min(bottom, cluster.bounds().minY());
                    }
                }
                if (top == Integer.MIN_VALUE) {
                    continue;
                }
                top = Math.min(top, level.getMaxBuildHeight() - 1);
                bottom = Math.max(bottom, level.getMinBuildHeight());
                for (int y = top; y >= bottom; y--) {
                    double best = -Double.MAX_VALUE, second = -Double.MAX_VALUE;
                    Part first = null, runnerUp = null, heart = null;
                    for (Part part : wood) {
                        if (!part.bounds().height(y)) {
                            continue;
                        }
                        double m = part.margin(bx, y, bz);
                        if (m > 0 && part.role() == Part.Role.HEART) {
                            heart = part;
                        }
                        if (m > best) {
                            second = best;
                            runnerUp = first;
                            best = m;
                            first = part;
                        } else if (m > second) {
                            second = m;
                            runnerUp = part;
                        }
                    }
                    double cut = -Double.MAX_VALUE;
                    Carve carve = null;
                    for (Carve c : carves) {
                        if (c.bounds().height(y)) {
                            double m = c.margin(bx, y, bz);
                            if (m > cut) {
                                cut = m;
                                carve = c;
                            }
                        }
                    }
                    pos.set(bx, y, bz);
                    // Heartwood shows wherever it reaches, however deep inside the rest of the tree it lies.
                    if (heart != null) {
                        first = heart;
                        runnerUp = null;
                    }
                    if (best > 0 && cut <= 0) {
                        if (y < first.foot()) {
                            BlockState here = level.getBlockState(pos);
                            if (!here.isAir() && !here.canBeReplaced()) {
                                continue;
                            }
                        }
                        level.setBlock(pos, material(palette, first, best, runnerUp, second, cut, bx, y, bz), 2);
                        continue;
                    }
                    if (cut > 0) {
                        if (y >= carve.floor() && !level.getBlockState(pos).isAir()) {
                            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                        }
                        continue;
                    }
                    double clump = -Double.MAX_VALUE, reach = Double.MAX_VALUE;
                    for (LeafCluster cluster : leaves) {
                        if (cluster.bounds().height(y) && cluster.margin(bx, y, bz, leaf) > 0) {
                            clump = Math.max(clump, leaf[0]);
                            reach = Math.min(reach, leaf[1]);
                        }
                    }
                    if (clump > 0 && reach <= LEAF_REACH && level.getBlockState(pos).isAir()) {
                        level.setBlock(pos, palette.leaves((int) Math.ceil(Math.max(1, reach))), 2);
                    }
                }
            }
        }
    }

    private static BlockState material(GrovePalette palette, Part part, double first, Part runnerUp, double second,
                                       double cut, int x, int y, int z) {
        Direction.Axis axis = part.axis(x, y, z);
        // Where two pieces of different grain meet, neither grain shows: a knot of all-bark wood.
        if (axis != null && runnerUp != null && second > 0 && runnerUp.axis(x, y, z) != axis) {
            axis = null;
        }
        boolean laidBare = cut > -STRIP_DEPTH;
        return switch (part.role()) {
            case HEART -> palette.heart(axis);
            case STRIPPED -> palette.stripped(axis);
            case BARK -> laidBare ? palette.stripped(null) : palette.bark();
            case LOG -> laidBare ? palette.stripped(axis) : axis == null ? palette.bark() : palette.log(axis);
            // Rings a block and a half apart; the outermost is bark.
            case RINGS -> Math.floorMod((int) Math.floor(first / 1.5), 2) == 1 ? palette.stripped(axis)
                    : axis == null ? palette.bark() : palette.log(axis);
            case SOIL -> palette.soil();
        };
    }

    private GroveBuilder() {
    }
}
