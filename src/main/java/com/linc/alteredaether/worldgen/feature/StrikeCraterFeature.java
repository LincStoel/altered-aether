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
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;

/**
 * Where lightning has hit the ground for ages: a shallow bowl with a fused floor, glassy aerogel threaded through it
 * like fulgurite, a raised lip on the rim, and scorched streaks running outward from it. The bowl follows the ground
 * around it, so it digs into slopes instead of floating over them.
 */
public final class StrikeCraterFeature extends Feature<StrikeCraterFeature.Config> {
    public record Config(IntProvider radius, IntProvider depth, BlockStateProvider liner, BlockStateProvider glass,
                         float glassChance) implements FeatureConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(i -> i.group(
                IntProvider.codec(2, 7).fieldOf("radius").forGetter(Config::radius),
                IntProvider.codec(1, 5).fieldOf("depth").forGetter(Config::depth),
                BlockStateProvider.CODEC.fieldOf("liner").forGetter(Config::liner),
                BlockStateProvider.CODEC.fieldOf("glass").forGetter(Config::glass),
                Codec.floatRange(0, 1).optionalFieldOf("glass_chance", 0.18f).forGetter(Config::glassChance)
        ).apply(i, Config::new));
    }

    public StrikeCraterFeature() {
        super(Config.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<Config> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        Config config = context.config();
        BlockPos origin = context.origin();
        double radius = config.radius().sample(random);
        int depth = config.depth().sample(random);
        double streakReach = Math.min(13, radius * 2.2);
        double wobble = random.nextDouble() * 10;
        double streakPhase = random.nextDouble() * Math.PI * 2;
        int streaks = 3 + random.nextInt(4);

        int extent = (int) Math.ceil(streakReach);
        boolean placed = false;
        for (int dx = -extent; dx <= extent; dx++) {
            for (int dz = -extent; dz <= extent; dz++) {
                double distance = Math.sqrt(dx * dx + dz * dz);
                double angle = Math.atan2(dz, dx);
                // an uneven outline: the radius drifts with the angle
                double edge = radius * (1 + 0.18 * Math.sin(angle * 3 + wobble));
                int x = origin.getX() + dx, z = origin.getZ() + dz;
                if (!WriteBounds.reachable(level, x, z)) {
                    continue;
                }
                int surface = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
                BlockPos ground = new BlockPos(x, surface - 1, z);
                BlockState top = level.getBlockState(ground);
                if (surface <= level.getMinBuildHeight() || !solid(top) || Math.abs(surface - origin.getY()) > 5
                        || !WriteBounds.writable(level, ground)) {
                    continue;
                }
                double d = distance / edge;
                if (d <= 1) {
                    int floor = surface - 1 - (int) Math.round(depth * (1 - d * d));
                    for (int y = surface - 1; y > floor; y--) {
                        BlockPos pos = new BlockPos(x, y, z);
                        if (solid(level.getBlockState(pos))) {
                            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                        }
                    }
                    BlockPos bottom = new BlockPos(x, floor, z);
                    level.setBlock(bottom, random.nextFloat() < config.glassChance()
                            ? config.glass().getState(random, bottom) : config.liner().getState(random, bottom),
                            Block.UPDATE_CLIENTS);
                    placed = true;
                } else if (d <= 1.25 && random.nextFloat() < 0.55f) {
                    BlockPos lip = new BlockPos(x, surface, z);
                    if (level.getBlockState(lip).isAir()) {
                        level.setBlock(lip, config.liner().getState(random, lip), Block.UPDATE_CLIENTS);
                    }
                } else if (d > 1.25 && distance <= streakReach && onStreak(angle, streaks, streakPhase)
                        && random.nextFloat() < 0.65f * (1 - distance / streakReach) + 0.1f) {
                    level.setBlock(ground, config.liner().getState(random, ground), Block.UPDATE_CLIENTS);
                }
            }
        }
        if (placed) {
            FeatureStats.placed("strike_crater");
        }
        return placed;
    }

    /** Whether a direction lies close to one of the streaks fanning out from the crater. */
    private static boolean onStreak(double angle, int streaks, double phase) {
        double step = Math.PI * 2 / streaks;
        double offset = Math.abs(((angle - phase) % step + step) % step - step / 2);
        return step / 2 - offset < 0.14;
    }

    private static boolean solid(BlockState state) {
        return !state.isAir() && state.getFluidState().isEmpty() && !state.is(BlockTags.LEAVES)
                && !state.is(BlockTags.LOGS);
    }
}
