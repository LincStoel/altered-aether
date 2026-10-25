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
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;

import java.util.Optional;

/**
 * A broken pillar from some forgotten hall: a shaft one or two blocks across, snapped off at a different height on
 * each column, with a ring of plinth stones at its foot and a drum or two lying where they fell. Standing and fallen
 * blocks turn to the right axis when their block has one.
 */
public final class RuinedPillarFeature extends Feature<RuinedPillarFeature.Config> {
    public record Config(BlockStateProvider blocks, IntProvider height, float wideChance, int fallen,
                         Optional<BlockStateProvider> plinth) implements FeatureConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(i -> i.group(
                BlockStateProvider.CODEC.fieldOf("blocks").forGetter(Config::blocks),
                IntProvider.codec(2, 24).fieldOf("height").forGetter(Config::height),
                Codec.floatRange(0, 1).optionalFieldOf("wide_chance", 0.4f).forGetter(Config::wideChance),
                Codec.intRange(0, 4).optionalFieldOf("fallen", 2).forGetter(Config::fallen),
                BlockStateProvider.CODEC.optionalFieldOf("plinth").forGetter(Config::plinth)
        ).apply(i, Config::new));
    }

    public RuinedPillarFeature() {
        super(Config.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<Config> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        Config config = context.config();
        BlockPos origin = context.origin();
        int width = random.nextFloat() < config.wideChance() ? 2 : 1;

        // The whole footprint has to stand on ground, or the pillar would hang off a ledge.
        for (int dx = -1; dx <= width; dx++) {
            for (int dz = -1; dz <= width; dz++) {
                if (!solid(level, origin.offset(dx, -1, dz)) && dx >= 0 && dx < width && dz >= 0 && dz < width) {
                    return false;
                }
            }
        }

        int height = config.height().sample(random);
        for (int dx = 0; dx < width; dx++) {
            for (int dz = 0; dz < width; dz++) {
                int top = Math.max(1, height - random.nextInt(Math.max(1, height / 2 + 1)));
                for (int y = 0; y < top; y++) {
                    BlockPos pos = origin.offset(dx, y, dz);
                    if (level.getBlockState(pos).isAir() || level.getBlockState(pos).canBeReplaced()) {
                        level.setBlock(pos, upright(config.blocks().getState(random, pos)), Block.UPDATE_CLIENTS);
                    }
                }
            }
        }

        if (config.plinth().isPresent()) {
            for (int dx = -1; dx <= width; dx++) {
                for (int dz = -1; dz <= width; dz++) {
                    boolean ring = dx < 0 || dz < 0 || dx >= width || dz >= width;
                    BlockPos pos = origin.offset(dx, 0, dz);
                    if (ring && random.nextFloat() < 0.6f && level.getBlockState(pos).isAir()
                            && solid(level, pos.below())) {
                        level.setBlock(pos, config.plinth().get().getState(random, pos), Block.UPDATE_CLIENTS);
                    }
                }
            }
        }

        int drums = config.fallen() == 0 ? 0 : random.nextInt(config.fallen() + 1);
        for (int i = 0; i < drums; i++) {
            fallenDrum(level, random, config, origin, width);
        }
        FeatureStats.placed("ruined_pillar");
        return true;
    }

    /** A drum of the shaft lying on its side a few blocks away. Skipped unless the ground there is level and clear. */
    private static void fallenDrum(WorldGenLevel level, RandomSource random, Config config, BlockPos origin, int width) {
        Direction direction = Direction.Plane.HORIZONTAL.getRandomDirection(random);
        int length = 2 + random.nextInt(2);
        int distance = width + 2 + random.nextInt(3);
        BlockPos start = origin.relative(direction, distance);
        for (int i = 0; i < length; i++) {
            BlockPos pos = start.relative(direction.getClockWise(), i);
            if (!level.getBlockState(pos).isAir() || !solid(level, pos.below()) || !level.ensureCanWrite(pos)) {
                return;
            }
        }
        for (int i = 0; i < length; i++) {
            BlockPos pos = start.relative(direction.getClockWise(), i);
            BlockState state = config.blocks().getState(random, pos);
            if (state.hasProperty(RotatedPillarBlock.AXIS)) {
                state = state.setValue(RotatedPillarBlock.AXIS, direction.getClockWise().getAxis());
            }
            level.setBlock(pos, state, Block.UPDATE_CLIENTS);
        }
    }

    private static BlockState upright(BlockState state) {
        return state.hasProperty(RotatedPillarBlock.AXIS) ? state.setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y) : state;
    }

    private static boolean solid(WorldGenLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return !state.isAir() && state.getFluidState().isEmpty() && !state.is(BlockTags.LEAVES);
    }
}
