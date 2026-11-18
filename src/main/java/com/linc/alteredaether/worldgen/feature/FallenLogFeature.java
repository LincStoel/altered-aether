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
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;

import java.util.ArrayList;
import java.util.List;

/**
 * A small fallen log lying along the ground, between the giants: a few blocks of log on their side, following the
 * ground a step up or down, with moss over parts of its back. It gives up rather than bridge a gap or bury itself.
 */
public final class FallenLogFeature extends Feature<FallenLogFeature.Config> {
    public record Config(BlockStateProvider log, BlockStateProvider moss, IntProvider length, float mossChance)
            implements FeatureConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(i -> i.group(
                BlockStateProvider.CODEC.fieldOf("log").forGetter(Config::log),
                BlockStateProvider.CODEC.fieldOf("moss").forGetter(Config::moss),
                IntProvider.codec(2, 12).fieldOf("length").forGetter(Config::length),
                Codec.floatRange(0, 1).optionalFieldOf("moss_chance", 0.4f).forGetter(Config::mossChance)
        ).apply(i, Config::new));
    }

    public FallenLogFeature() {
        super(Config.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<Config> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        Config config = context.config();
        Direction along = random.nextBoolean() ? Direction.EAST : Direction.SOUTH;
        int length = config.length().sample(random);
        // find the log's path first: a block of air over solid ground at each step, a step up or down at most
        List<BlockPos> path = new ArrayList<>(length);
        BlockPos.MutableBlockPos pos = context.origin().mutable();
        for (int i = 0; i < length; i++) {
            if (!level.getBlockState(pos).isAir()) {
                pos.move(Direction.UP);
            } else if (level.getBlockState(pos.below()).isAir()) {
                pos.move(Direction.DOWN);
            }
            if (!level.getBlockState(pos).isAir() || !level.getBlockState(pos.below()).isSolid()) {
                return false;
            }
            path.add(pos.immutable());
            pos.move(along);
        }
        FeatureStats.placed("fallen_log");
        for (BlockPos at : path) {
            BlockState log = config.log().getState(random, at);
            if (log.hasProperty(RotatedPillarBlock.AXIS)) {
                log = log.setValue(RotatedPillarBlock.AXIS, along.getAxis());
            }
            level.setBlock(at, log, 2);
            if (random.nextFloat() < config.mossChance() && level.getBlockState(at.above()).isAir()) {
                level.setBlock(at.above(), config.moss().getState(random, at.above()), 2);
            }
        }
        return true;
    }
}
