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

import com.linc.alteredaether.config.AACommonConfig;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;

/**
 * A wind-sculpted mound of snow: an ellipse stretched along the wind, tallest a little toward the lee side, built of
 * the body block with snow layers feathered over its top so the edge has no step. Follows the ground, so it drapes
 * over slopes. Length is capped so the whole drift stays within the area a feature may write to.
 */
public final class SnowDriftFeature extends Feature<SnowDriftFeature.Config> {
    private static final int MAX_REACH = 14;

    public record Config(BlockStateProvider block, IntProvider radius, IntProvider height, float elongation,
                         boolean windAligned) implements FeatureConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(i -> i.group(
                BlockStateProvider.CODEC.fieldOf("block").forGetter(Config::block),
                IntProvider.codec(2, 9).fieldOf("radius").forGetter(Config::radius),
                IntProvider.codec(1, 8).fieldOf("height").forGetter(Config::height),
                Codec.floatRange(1, 3).optionalFieldOf("elongation", 1.8f).forGetter(Config::elongation),
                Codec.BOOL.optionalFieldOf("wind_aligned", true).forGetter(Config::windAligned)
        ).apply(i, Config::new));
    }

    public SnowDriftFeature() {
        super(Config.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<Config> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        Config config = context.config();
        BlockPos origin = context.origin();

        double across = config.radius().sample(random);
        double along = Math.min(MAX_REACH, across * config.elongation());
        double peak = config.height().sample(random);
        double angle = config.windAligned()
                ? Math.toRadians(AACommonConfig.DUNE_WIND_DIRECTION.get() + (random.nextDouble() - 0.5) * 30)
                : random.nextDouble() * Math.PI * 2;
        // direction of the wind on the XZ plane: clockwise from north, and north is -Z
        double windX = Math.sin(angle), windZ = -Math.cos(angle);

        int extent = (int) Math.ceil(Math.max(along, across));
        boolean placed = false;
        for (int dx = -extent; dx <= extent; dx++) {
            for (int dz = -extent; dz <= extent; dz++) {
                double u = (dx * windX + dz * windZ) / along;
                double v = (-dx * windZ + dz * windX) / across;
                // the crest sits toward the lee side (positive u), so the windward face is the gentle one
                double d2 = (u - 0.18) * (u - 0.18) + v * v;
                if (d2 >= 1) {
                    continue;
                }
                double drift = peak * Math.pow(1 - d2, 1.4) + (random.nextDouble() - 0.5) * 0.35;
                if (drift <= 0.12) {
                    continue;
                }
                int x = origin.getX() + dx, z = origin.getZ() + dz;
                if (!WriteBounds.reachable(level, x, z)) {
                    continue;
                }
                int surface = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
                BlockPos ground = new BlockPos(x, surface - 1, z);
                BlockState below = level.getBlockState(ground);
                if (surface <= level.getMinBuildHeight() || below.isAir() || !below.getFluidState().isEmpty()
                        || below.is(BlockTags.LEAVES) || Math.abs(surface - origin.getY()) > 6
                        || !WriteBounds.writable(level, ground)) {
                    continue;
                }
                int full = (int) Math.floor(drift);
                BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos(x, surface, z);
                for (int i = 0; i < full; i++) {
                    if (replaceable(level, cursor)) {
                        level.setBlock(cursor, config.block().getState(random, cursor), Block.UPDATE_CLIENTS);
                        placed = true;
                    }
                    cursor.move(0, 1, 0);
                }
                int layers = (int) Math.round((drift - full) * 8);
                if (layers >= 1 && replaceable(level, cursor)) {
                    level.setBlock(cursor, Blocks.SNOW.defaultBlockState()
                            .setValue(SnowLayerBlock.LAYERS, Math.min(7, layers)), Block.UPDATE_CLIENTS);
                    placed = true;
                }
            }
        }
        if (placed) {
            FeatureStats.placed("snow_drift");
        }
        return placed;
    }

    private static boolean replaceable(WorldGenLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.isAir() || state.canBeReplaced() && state.getFluidState().isEmpty();
    }
}
