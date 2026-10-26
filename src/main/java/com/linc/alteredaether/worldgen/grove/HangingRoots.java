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

import com.linc.alteredaether.worldgen.AABiomes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * The roots hanging under the grove islands: from the lowest block of each column in the Skyroot Cathedral and
 * Nurselog Hollow, tufts of hanging roots and thin strands of wood that wander down into the void, thickest under the
 * giants' great roots ({@link UnderRoots}). Each chunk dresses only its own columns.
 */
public final class HangingRoots {
    private static final TagKey<Block> AERCLOUDS = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath("aether", "aerclouds"));
    /** Per-column odds at density 1: a wooden strand, or failing that a tuft; under a root, a strand. */
    private static final double STRAND = 0.07, TUFT = 0.14, UNDER_ROOT = 0.3;

    public static boolean place(WorldGenLevel level, RandomSource random, int x0, int z0, double density) {
        ChunkAccess chunk = level.getChunk(x0 >> 4, z0 >> 4);
        // Start at the lowest section with anything in it; everything under that is void.
        int bottom = Integer.MIN_VALUE;
        for (int i = 0; i < chunk.getSectionsCount(); i++) {
            if (!chunk.getSection(i).hasOnlyAir()) {
                bottom = Math.max(level.getMinBuildHeight(), chunk.getSectionYFromSectionIndex(i) << 4);
                break;
            }
        }
        if (bottom == Integer.MIN_VALUE) {
            return false;
        }
        boolean placed = false;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = x0; x < x0 + 16; x++) {
            for (int z = z0; z < z0 + 16; z++) {
                int surface = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
                if (surface <= bottom || !grove(level.getBiome(pos.set(x, surface - 1, z)))) {
                    continue;
                }
                int y = bottom;
                while (y < surface && level.getBlockState(pos.set(x, y, z)).isAir()) {
                    y++;
                }
                // Nothing hangs from the bottom of the world, or from anything that isn't the island or its wood.
                BlockState anchor = level.getBlockState(pos.set(x, y, z));
                if (y >= surface || y - 3 < level.getMinBuildHeight() || anchor.is(BlockTags.LEAVES)
                        || anchor.is(AERCLOUDS) || !anchor.getFluidState().isEmpty()
                        || !anchor.isFaceSturdy(level, pos, Direction.DOWN)) {
                    continue;
                }
                boolean wood = anchor.is(BlockTags.LOGS);
                double roll = random.nextDouble();
                if (roll < (wood ? UNDER_ROOT : STRAND) * density) {
                    strand(level, random, pos, x0, z0, x, y - 1, z, anchor, wood);
                    placed = true;
                } else if (!wood && roll < (STRAND + TUFT) * density) {
                    level.setBlock(pos.set(x, y - 1, z), Blocks.HANGING_ROOTS.defaultBlockState(), 2);
                    placed = true;
                }
            }
        }
        return placed;
    }

    private static boolean grove(Holder<Biome> biome) {
        return biome.is(AABiomes.SKYROOT_CATHEDRAL) || biome.is(AABiomes.NURSELOG_HOLLOW);
    }

    /**
     * A thin root of the anchor's own wood (skyroot under rock) hanging from (x, y, z) down: mostly short, now and
     * then long, stepping sideways now and again, and ending in a tuft. Stays within its chunk.
     */
    private static void strand(WorldGenLevel level, RandomSource random, BlockPos.MutableBlockPos pos, int x0, int z0,
                               int x, int y, int z, BlockState anchor, boolean wood) {
        Block log = wood && anchor.hasProperty(RotatedPillarBlock.AXIS) ? anchor.getBlock()
                : GrovePalette.SKYROOT.log();
        double r = random.nextDouble();
        int length = 1 + (int) (r * r * r * (wood ? 8 : 14));
        for (int i = 0; i < length; i++, y--) {
            if (!level.getBlockState(pos.set(x, y, z)).isAir() || y <= level.getMinBuildHeight()) {
                return;
            }
            level.setBlock(pos, wood(log, Direction.Axis.Y), 2);
            if (i + 1 < length && random.nextDouble() < 0.18) {
                Direction side = Direction.Plane.HORIZONTAL.getRandomDirection(random);
                int nx = x + side.getStepX(), nz = z + side.getStepZ();
                if (nx >= x0 && nx < x0 + 16 && nz >= z0 && nz < z0 + 16
                        && level.getBlockState(pos.set(nx, y, nz)).isAir()) {
                    level.setBlock(pos, wood(log, side.getAxis()), 2);
                    x = nx;
                    z = nz;
                }
            }
        }
        if (y > level.getMinBuildHeight() && level.getBlockState(pos.set(x, y, z)).isAir()) {
            level.setBlock(pos, Blocks.HANGING_ROOTS.defaultBlockState(), 2);
        }
    }

    private static BlockState wood(Block log, Direction.Axis axis) {
        return GrovePalette.natural(log.defaultBlockState().trySetValue(RotatedPillarBlock.AXIS, axis));
    }

    private HangingRoots() {
    }
}
