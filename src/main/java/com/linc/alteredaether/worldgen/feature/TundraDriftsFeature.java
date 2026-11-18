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

import com.aetherteam.aether.block.AetherBlocks;
import com.linc.alteredaether.config.AACommonConfig;
import com.linc.alteredaether.worldgen.density.BiomeEdgeField;
import com.linc.alteredaether.worldgen.region.BiomeGroups;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;

/**
 * The Snow Tundra's drifts and the trees buried in them, one chunk at a time. The drifts ({@link DriftShape}) are ridges
 * of snow across the prevailing wind, up to 26 blocks tall, with curled lips leaning out over their lee faces; their
 * surface is finished with snow layers so it rises smoothly instead of in steps. Before the snow, skyroot trees (now and
 * then a golden oak) are grown from the ground under where it will lie, so the drifts bury them: mostly their crowns
 * poke out, sometimes only a tip. Every value comes from the world seed and the column, so drifts run on across chunk
 * borders without a seam. The drifts fade out over the last 24 blocks before the tundra ends.
 */
public final class TundraDriftsFeature extends Feature<NoneFeatureConfiguration> {
    /** Buried trees: one chance in each cell of this grid. */
    private static final int TREE_CELL = 18;
    private static final double TREE_CHANCE = 0.55, GOLDEN_CHANCE = 0.06;
    /** Drift heights, before the fade at the tundra's edge. */
    private static final double MIN_PEAK = 5, MAX_PEAK = 26;
    /** Blocks over which the drifts fade out toward the tundra's edge. */
    private static final double FADE = 24;

    private record Noises(long seed, SimplexNoise wind, SimplexNoise peak, SimplexNoise warp) {
    }

    private static volatile Noises noises;

    public TundraDriftsFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        BiomeEdgeField field = BiomeEdgeField.get(level.getLevel().dimension());
        if (field == null) {
            FeatureStats.placed("tundra_drifts/unbound");
            return false;
        }
        BiomeEdgeField.Solver tundra = field.solver(BiomeGroups.TUNDRA, 32);
        Noises n = noises(level.getSeed());
        int minX = context.origin().getX() & ~15, minZ = context.origin().getZ() & ~15;

        // Each column's ground and the drift's height over it, read before anything is built.
        int[] ground = new int[256];
        double[] peak = new double[256], along = new double[256];
        boolean any = false;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int i = 0; i < 256; i++) {
            int x = minX + (i & 15), z = minZ + (i >> 4);
            double fade = Mth.clamp(tundra.inside(x, z, 1) / FADE, 0, 1);
            ground[i] = groundAt(level, pos, x, z);
            if (fade <= 0 || ground[i] == Integer.MIN_VALUE) {
                continue;
            }
            // The wind: the configured prevailing direction, swinging up to 35 degrees either way over a few hundred
            // blocks; north is -Z.
            double angle = Math.toRadians(AACommonConfig.DUNE_WIND_DIRECTION.get() + 35 * n.wind.getValue(x / 700.0, z / 700.0));
            double windX = Math.sin(angle), windZ = -Math.cos(angle);
            // Position along the wind, bent a little so ridges meander instead of running ruler-straight.
            along[i] = x * windX + z * windZ + 9 * n.warp.getValue(x / 90.0, z / 90.0);
            double size = 0.5 + 0.5 * n.peak.getValue(x / 140.0, z / 140.0);
            peak[i] = fade * Mth.lerp(size * size, MIN_PEAK, MAX_PEAK);
            any = true;
        }
        if (!any) {
            FeatureStats.placed("tundra_drifts/none");
            return false;
        }
        FeatureStats.placed("tundra_drifts");

        buryTrees(level, n, minX, minZ, ground, tundra, pos);

        BlockState snow = Blocks.SNOW_BLOCK.defaultBlockState();
        for (int i = 0; i < 256; i++) {
            if (peak[i] <= 0) {
                continue;
            }
            int x = minX + (i & 15), z = minZ + (i >> 4), top = ground[i];
            int reach = (int) Math.ceil(peak[i]) + 1;
            for (int above = 1; above <= reach; above++) {
                if (DriftShape.inside(along[i], above, peak[i])) {
                    pos.set(x, ground[i] + above, z);
                    if (level.getBlockState(pos).isAir()) {
                        level.setBlock(pos, snow, 2);
                    }
                    top = Math.max(top, ground[i] + above);
                }
            }
            finish(level, pos, x, z, top, ground[i], along[i], peak[i]);
        }
        return true;
    }

    /**
     * Snow layers over the column's top: as many eighths as the drift's surface rises into the block above, so the
     * drift slopes smoothly; a thin covering on anything else left showing, such as a buried tree's crown.
     */
    private static void finish(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z, int top, int ground,
                               double along, double peak) {
        int surface = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
        pos.set(x, surface, z);
        if (!level.getBlockState(pos).isAir() || !level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(),
                net.minecraft.core.Direction.UP) && !level.getBlockState(pos.below()).is(BlockTags.LEAVES)) {
            return;
        }
        int layers;
        if (surface - 1 == top) {
            double rise = DriftShape.depth(along, surface - ground, peak) + 1;
            layers = Mth.clamp((int) Math.round(rise * 8), 0, 7);
        } else {
            layers = level.getBlockState(pos.below()).is(BlockTags.LEAVES) ? 1 + (int) (Math.abs(x * 31 + z * 17) % 2) : 0;
        }
        if (layers > 0) {
            FeatureStats.placed("tundra_drifts/layer");
            level.setBlock(pos, Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, layers), 2);
        }
    }

    /** The trees whose cells' sites fall in this chunk, grown from the ground before the snow comes. */
    private static void buryTrees(WorldGenLevel level, Noises n, int minX, int minZ, int[] ground,
                                  BiomeEdgeField.Solver tundra, BlockPos.MutableBlockPos pos) {
        for (int cx = Math.floorDiv(minX, TREE_CELL); cx <= Math.floorDiv(minX + 15, TREE_CELL); cx++) {
            for (int cz = Math.floorDiv(minZ, TREE_CELL); cz <= Math.floorDiv(minZ + 15, TREE_CELL); cz++) {
                WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(0));
                random.setLargeFeatureWithSalt(n.seed, cx, cz, 0x7D17F7);
                int x = cx * TREE_CELL + random.nextInt(TREE_CELL), z = cz * TREE_CELL + random.nextInt(TREE_CELL);
                if (random.nextDouble() >= TREE_CHANCE || x < minX || x > minX + 15 || z < minZ || z > minZ + 15
                        || tundra.inside(x, z, 1) < FADE * 0.6) {
                    continue;
                }
                int base = ground[(x - minX) + ((z - minZ) << 4)];
                if (base == Integer.MIN_VALUE) {
                    continue;
                }
                tree(level, random, pos, x, base, z, random.nextDouble() < GOLDEN_CHANCE);
                FeatureStats.placed("tundra_drifts/tree");
            }
        }
    }

    /** A plain skyroot (or golden oak): a trunk 5 to 9 tall and a round crown, leaves kept from decaying. */
    private static void tree(WorldGenLevel level, RandomSource random, BlockPos.MutableBlockPos pos, int x, int base,
                             int z, boolean golden) {
        BlockState log = (golden ? AetherBlocks.GOLDEN_OAK_LOG : AetherBlocks.SKYROOT_LOG).get().defaultBlockState();
        BlockState leaves = (golden ? AetherBlocks.GOLDEN_OAK_LEAVES : AetherBlocks.SKYROOT_LEAVES).get().defaultBlockState();
        if (leaves.hasProperty(LeavesBlock.PERSISTENT)) {
            leaves = leaves.setValue(LeavesBlock.PERSISTENT, true);
        }
        int height = 5 + random.nextInt(5);
        double radius = 2 + random.nextDouble() * 1.2;
        int crown = base + height - 1;
        int r = (int) Math.ceil(radius);
        for (int dy = -r; dy <= r; dy++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    double d = dx * dx + dz * dz + dy * dy * 1.6;
                    if (d <= radius * radius && random.nextFloat() > 0.08f) {
                        pos.set(x + dx, crown + dy, z + dz);
                        BlockState at = level.getBlockState(pos);
                        if (at.isAir() || at.is(Blocks.SNOW_BLOCK) || at.is(Blocks.SNOW)) {
                            level.setBlock(pos, leaves, 2);
                        }
                    }
                }
            }
        }
        for (int y = base + 1; y <= crown; y++) {
            level.setBlock(pos.set(x, y, z), log, 2);
        }
    }

    /**
     * The top of the rock in a column, looking past all snow (the tundra's own covering and any drift), trees and
     * anything else not part of the ground, so every chunk reads the same ground whatever was built first.
     */
    private static int groundAt(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z) {
        int y = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
        int floor = level.getMinBuildHeight();
        for (; y >= floor; y--) {
            BlockState state = level.getBlockState(pos.set(x, y, z));
            if (state.isAir() || state.is(Blocks.SNOW) || state.is(Blocks.SNOW_BLOCK) || state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS) || !state.getFluidState().isEmpty()
                    || !state.isSolid()) {
                continue;
            }
            return y;
        }
        return Integer.MIN_VALUE;
    }

    private static Noises noises(long seed) {
        Noises current = noises;
        if (current == null || current.seed != seed) {
            current = new Noises(seed,
                    new SimplexNoise(new LegacyRandomSource(seed ^ 0x51D7A11L)),
                    new SimplexNoise(new LegacyRandomSource(seed ^ 0x9EA4B2L)),
                    new SimplexNoise(new LegacyRandomSource(seed ^ 0x3C0FFEEL)));
            noises = current;
        }
        return current;
    }
}
