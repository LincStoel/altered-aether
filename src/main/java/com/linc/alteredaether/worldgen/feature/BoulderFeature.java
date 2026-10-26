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
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A lumpy boulder: two or three overlapping ellipsoids with a noisy surface, so no two look alike. Sits on the ground
 * at the origin, sunk into it by {@code burial}, squashed vertically by {@code squash}; when it hangs over a slope or
 * an edge, the open underside is filled down to the ground so it never floats. With {@code floating} it is debris
 * instead: nothing is filled below it and nothing needs to be under it, and {@code scatter} lone blocks hang in a
 * loose shell around it, the grit of the break. Its blocks only replace air and replaceable blocks, so it can't carve
 * into the ground it rests on.
 *
 * <p>The radius is capped at 10 so a boulder stays within the area a feature may write to.
 */
public final class BoulderFeature extends Feature<BoulderFeature.Config> {
    private static final int MAX_RADIUS = 10;
    private static final int MAX_FILL = 6;

    public record Config(BlockStateProvider state, IntProvider radius, float squash, float burial,
                         Optional<BlockStateProvider> topCover, boolean floating, int scatter)
            implements FeatureConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(i -> i.group(
                BlockStateProvider.CODEC.fieldOf("state").forGetter(Config::state),
                IntProvider.codec(1, MAX_RADIUS).fieldOf("radius").forGetter(Config::radius),
                Codec.floatRange(0.2f, 1.5f).optionalFieldOf("squash", 0.75f).forGetter(Config::squash),
                Codec.floatRange(0, 0.8f).optionalFieldOf("burial", 0.25f).forGetter(Config::burial),
                BlockStateProvider.CODEC.optionalFieldOf("top_cover").forGetter(Config::topCover),
                Codec.BOOL.optionalFieldOf("floating", false).forGetter(Config::floating),
                Codec.intRange(0, 64).optionalFieldOf("scatter", 0).forGetter(Config::scatter)
        ).apply(i, Config::new));
    }

    public BoulderFeature() {
        super(Config.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<Config> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        Config config = context.config();
        BlockPos origin = context.origin();
        if (!config.floating()) {
            BlockState ground = level.getBlockState(origin.below());
            if (ground.isAir() || !ground.getFluidState().isEmpty() || !level.getBlockState(origin).isAir()) {
                return false;
            }
        }

        int radius = config.radius().sample(random);
        double height = Math.max(1.2, radius * config.squash());
        BlockPos center = origin.below((int) Math.round(height * config.burial()))
                .above(config.floating() ? 0 : (int) Math.round(height * 0.5));

        List<double[]> lobes = new ArrayList<>();
        lobes.add(new double[]{0, 0, 0, radius, height});
        for (int i = random.nextInt(3) + 1; i > 0 && radius > 1; i--) {
            double angle = random.nextDouble() * Math.PI * 2;
            double offset = radius * (0.35 + random.nextDouble() * 0.45);
            double lobeRadius = radius * (0.5 + random.nextDouble() * 0.4);
            lobes.add(new double[]{Math.cos(angle) * offset, (random.nextDouble() - 0.5) * height * 0.4,
                    Math.sin(angle) * offset, lobeRadius, Math.max(1, lobeRadius * config.squash())});
        }

        Map<BlockPos, BlockState> shape = new HashMap<>();
        double reach = Math.min(15, radius * 2 + 2);
        for (double[] lobe : lobes) {
            int extent = (int) Math.ceil(lobe[3]) + 1;
            int vertical = (int) Math.ceil(lobe[4]) + 1;
            BlockPos lobeCenter = center.offset((int) Math.round(lobe[0]), (int) Math.round(lobe[1]),
                    (int) Math.round(lobe[2]));
            for (int dx = -extent; dx <= extent; dx++) {
                for (int dz = -extent; dz <= extent; dz++) {
                    for (int dy = -vertical; dy <= vertical; dy++) {
                        double distance = (dx * dx) / (lobe[3] * lobe[3]) + (dy * dy) / (lobe[4] * lobe[4])
                                + (dz * dz) / (lobe[3] * lobe[3]);
                        // A rough surface: the threshold wobbles per block, more on larger boulders.
                        if (distance > 1.0 - random.nextDouble() * Math.min(0.35, 0.08 * radius)) {
                            continue;
                        }
                        BlockPos at = lobeCenter.offset(dx, dy, dz);
                        if (Math.abs(at.getX() - origin.getX()) <= reach && Math.abs(at.getZ() - origin.getZ()) <= reach
                                && replaceable(level, at)) {
                            shape.put(at, config.state().getState(random, at));
                        }
                    }
                }
            }
        }
        if (shape.isEmpty()) {
            return false;
        }

        if (!config.floating()) {
            fillUnderside(level, random, config, shape);
        } else {
            scatter(level, random, config, center, radius, height, origin, reach, shape);
        }
        boolean cover = config.topCover().isPresent();
        for (Map.Entry<BlockPos, BlockState> entry : shape.entrySet()) {
            BlockState state = entry.getValue();
            if (cover && !shape.containsKey(entry.getKey().above()) && random.nextFloat() < 0.65f) {
                state = config.topCover().get().getState(random, entry.getKey());
            }
            level.setBlock(entry.getKey(), state, Block.UPDATE_CLIENTS);
        }
        FeatureStats.placed(config.floating() ? "debris" : "boulder");
        return true;
    }

    /**
     * Lone blocks around floating debris: each a random bearing out from the centre, between just clear of the
     * boulder and two and a half times its radius, thinning with distance.
     */
    private static void scatter(WorldGenLevel level, RandomSource random, Config config, BlockPos center, int radius,
                                double height, BlockPos origin, double reach, Map<BlockPos, BlockState> shape) {
        for (int i = 0; i < config.scatter(); i++) {
            double yaw = random.nextDouble() * Math.PI * 2, pitch = (random.nextDouble() - 0.5) * Math.PI;
            double distance = radius * (1.2 + 1.3 * random.nextDouble() * random.nextDouble()) + 1;
            BlockPos at = center.offset((int) Math.round(Math.cos(yaw) * Math.cos(pitch) * distance),
                    (int) Math.round(Math.sin(pitch) * distance * Math.max(0.6, height / radius)),
                    (int) Math.round(Math.sin(yaw) * Math.cos(pitch) * distance));
            if (Math.abs(at.getX() - origin.getX()) <= reach && Math.abs(at.getZ() - origin.getZ()) <= reach
                    && level.getBlockState(at).isAir()) {
                shape.putIfAbsent(at, config.state().getState(random, at));
            }
        }
    }

    /** Extends every column that ends over open air down to solid ground, so a boulder on a slope has no gap under it. */
    private static void fillUnderside(WorldGenLevel level, RandomSource random, Config config,
                                      Map<BlockPos, BlockState> shape) {
        Map<BlockPos, BlockState> bottoms = new HashMap<>();
        for (Map.Entry<BlockPos, BlockState> entry : shape.entrySet()) {
            BlockPos below = entry.getKey().below();
            if (!shape.containsKey(below)) {
                bottoms.put(entry.getKey(), entry.getValue());
            }
        }
        for (Map.Entry<BlockPos, BlockState> entry : bottoms.entrySet()) {
            BlockPos.MutableBlockPos cursor = entry.getKey().mutable().move(0, -1, 0);
            List<BlockPos> column = new ArrayList<>();
            int steps = 0;
            while (steps++ < MAX_FILL && replaceable(level, cursor) && !shape.containsKey(cursor)) {
                column.add(cursor.immutable());
                cursor.move(0, -1, 0);
            }
            if (steps <= MAX_FILL && !level.getBlockState(cursor).isAir()) {
                for (BlockPos pos : column) {
                    shape.put(pos, config.state().getState(random, pos));
                }
            }
        }
    }

    private static boolean replaceable(WorldGenLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.isAir() || state.canBeReplaced() && state.getFluidState().isEmpty();
    }
}
