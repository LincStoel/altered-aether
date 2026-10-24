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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;

import java.util.List;

/**
 * A spring set into a cliff face at an island's edge, so its water pours out over the drop: the start of a waterfall.
 * Searches down from the top of an edge column (see IslandEdge) for a block of rock with open air on exactly one side
 * and rock everywhere else, and turns it into a fluid source that ticks straight away.
 *
 * <p>The first {@code fluid} block that exists is used, and water when none does, so Deep Aether's poison can be
 * asked for without depending on it.
 */
public final class EdgeSpringFeature extends Feature<EdgeSpringFeature.Config> {
    public record Config(List<ResourceLocation> fluid, int minDepth, int maxDepth) implements FeatureConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.listOf().fieldOf("fluid").forGetter(Config::fluid),
                Codec.intRange(0, 32).optionalFieldOf("min_depth", 2).forGetter(Config::minDepth),
                Codec.intRange(0, 32).optionalFieldOf("max_depth", 6).forGetter(Config::maxDepth)
        ).apply(i, Config::new));
    }

    /** Springs placed since the server started, for the debug tools. */
    public static final java.util.concurrent.atomic.AtomicInteger PLACED = new java.util.concurrent.atomic.AtomicInteger();

    public EdgeSpringFeature() {
        super(Config.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<Config> context) {
        WorldGenLevel level = context.level();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int depth = context.config().minDepth(); depth <= context.config().maxDepth(); depth++) {
            pos.setWithOffset(context.origin(), 0, -depth, 0);
            if (!rock(level, pos) || !rock(level, pos.above()) || !rock(level, pos.below())) {
                continue;
            }
            int open = 0;
            boolean enclosed = true;
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                BlockPos side = pos.relative(direction);
                if (level.getBlockState(side).isAir()) {
                    open++;
                } else if (!rock(level, side)) {
                    enclosed = false;
                }
            }
            if (open == 1 && enclosed) {
                BlockState source = source(context.config());
                BlockPos at = pos.immutable();
                level.setBlock(at, source, Block.UPDATE_CLIENTS);
                level.scheduleTick(at, source.getFluidState().getType(), 0);
                PLACED.incrementAndGet();
                return true;
            }
        }
        return false;
    }

    private static boolean rock(WorldGenLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.isSolidRender(level, pos) && state.getFluidState().isEmpty();
    }

    private static BlockState source(Config config) {
        for (ResourceLocation id : config.fluid()) {
            Block block = BuiltInRegistries.BLOCK.getOptional(id).orElse(null);
            if (block != null && !block.defaultBlockState().getFluidState().isEmpty()) {
                return block.defaultBlockState();
            }
        }
        return Blocks.WATER.defaultBlockState();
    }
}
