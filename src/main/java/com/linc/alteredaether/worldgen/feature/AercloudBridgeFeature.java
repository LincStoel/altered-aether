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

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A strand of cloud arching from one island across the gap to the next, so there is a reason to cross. Meant to be
 * placed at an island edge (see IslandEdge): it looks outward in each direction in random order and builds a bridge
 * toward the first one where ground of about the same height lies between {@code min_length} and {@code max_length}
 * blocks away. If nothing is in reach it places nothing, so bridges never end in mid-air. The bridge arches a little
 * and sags toward its ends, and is two blocks wide in the middle.
 *
 * <p>{@code max_length} stops at 15 so the far end stays within the area a feature may write to.
 */
public final class AercloudBridgeFeature extends Feature<AercloudBridgeFeature.Config> {
    public record Config(BlockStateProvider block, int minLength, int maxLength) implements FeatureConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(i -> i.group(
                BlockStateProvider.CODEC.fieldOf("block").forGetter(Config::block),
                Codec.intRange(4, 15).optionalFieldOf("min_length", 6).forGetter(Config::minLength),
                Codec.intRange(5, 15).optionalFieldOf("max_length", 15).forGetter(Config::maxLength)
        ).apply(i, Config::new));
    }

    public AercloudBridgeFeature() {
        super(Config.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<Config> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        Config config = context.config();
        BlockPos start = context.origin().above();

        List<Direction> directions = new ArrayList<>(Direction.Plane.HORIZONTAL.stream().toList());
        Collections.shuffle(directions, new java.util.Random(random.nextLong()));
        for (Direction direction : directions) {
            for (int length = config.maxLength(); length >= config.minLength(); length--) {
                int ex = start.getX() + direction.getStepX() * length, ez = start.getZ() + direction.getStepZ() * length;
                int landing = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, ex, ez);
                if (landing <= level.getMinBuildHeight() || Math.abs(landing - start.getY()) > 8
                        || !level.ensureCanWrite(new BlockPos(ex, landing, ez))) {
                    continue;
                }
                // the span between must be open sky at the middle, or this is not a gap
                int mx = start.getX() + direction.getStepX() * (length / 2), mz = start.getZ() + direction.getStepZ() * (length / 2);
                if (level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, mx, mz) > start.getY() - 3) {
                    continue;
                }
                build(level, random, config, start, direction, length, landing);
                FeatureStats.placed("aercloud_bridge");
                return true;
            }
        }
        return false;
    }

    private static void build(WorldGenLevel level, RandomSource random, Config config, BlockPos start,
                              Direction direction, int length, int landing) {
        double arch = 1 + random.nextInt(3);
        Direction side = direction.getClockWise();
        for (int step = 0; step <= length; step++) {
            double t = step / (double) length;
            double y = start.getY() + (landing - start.getY()) * t + Math.sin(t * Math.PI) * arch;
            int width = t > 0.15 && t < 0.85 ? 1 : 0;
            for (int w = -width; w <= width; w++) {
                BlockPos pos = start.relative(direction, step).relative(side, w).atY((int) Math.round(y));
                if (level.getBlockState(pos).isAir() && level.ensureCanWrite(pos)) {
                    level.setBlock(pos, config.block().getState(random, pos), Block.UPDATE_CLIENTS);
                    // a thicker core down the middle
                    if (w == 0 && step % 3 != 0 && level.getBlockState(pos.below()).isAir()) {
                        level.setBlock(pos.below(), config.block().getState(random, pos), Block.UPDATE_CLIENTS);
                    }
                }
            }
        }
    }
}
