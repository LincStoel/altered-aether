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
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;

import java.util.Optional;

/**
 * A stack of carved totems all facing the same way, mixing the kinds within one column, on any solid ground (Deep
 * Aether's own totem feature insists on aether grass by identity, which no grove has any more). An optional ring of
 * plinth stones at the foot, partly broken, makes it read as a shrine rather than a stray column.
 */
public final class TotemFeature extends Feature<TotemFeature.Config> {
    public record Config(BlockStateProvider blocks, IntProvider height, Optional<BlockStateProvider> plinth,
                         float ringChance) implements FeatureConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(i -> i.group(
                BlockStateProvider.CODEC.fieldOf("blocks").forGetter(Config::blocks),
                IntProvider.codec(1, 8).fieldOf("height").forGetter(Config::height),
                BlockStateProvider.CODEC.optionalFieldOf("plinth").forGetter(Config::plinth),
                Codec.floatRange(0, 1).optionalFieldOf("ring_chance", 0.5f).forGetter(Config::ringChance)
        ).apply(i, Config::new));
    }

    public TotemFeature() {
        super(Config.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<Config> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        Config config = context.config();
        BlockPos origin = context.origin();
        BlockState ground = level.getBlockState(origin.below());
        if (!level.getBlockState(origin).isAir() || ground.isAir() || !ground.getFluidState().isEmpty()
                || ground.is(BlockTags.LEAVES)) {
            return false;
        }

        Direction facing = Direction.Plane.HORIZONTAL.getRandomDirection(random);
        int height = config.height().sample(random);
        for (int y = 0; y < height; y++) {
            BlockPos pos = origin.above(y);
            if (!level.getBlockState(pos).isAir()) {
                break;
            }
            BlockState state = config.blocks().getState(random, pos);
            if (state.hasProperty(HorizontalDirectionalBlock.FACING)) {
                state = state.setValue(HorizontalDirectionalBlock.FACING, facing);
            }
            level.setBlock(pos, state, Block.UPDATE_CLIENTS);
        }

        if (config.plinth().isPresent() && random.nextFloat() < config.ringChance()) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos pos = origin.offset(dx, 0, dz);
                    if ((dx != 0 || dz != 0) && random.nextFloat() < 0.7f && level.getBlockState(pos).isAir()
                            && !level.getBlockState(pos.below()).isAir()) {
                        level.setBlock(pos, config.plinth().get().getState(random, pos), Block.UPDATE_CLIENTS);
                    }
                }
            }
        }
        FeatureStats.placed("totem");
        return true;
    }
}
