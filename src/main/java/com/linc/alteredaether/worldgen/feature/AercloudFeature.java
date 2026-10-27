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
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;

/**
 * A cloud of aercloud blocks in the open sky. A {@code puff} is a flattened, lumpy ball; a {@code streak} is a long
 * ribbon that drifts and bobs, laid along the wind when {@code wind_aligned} (the same wind that shapes the dunes).
 * Only air is replaced, so a cloud sits around terrain instead of cutting into it. The size is capped so a cloud stays
 * within the area a feature may write to.
 */
public final class AercloudFeature extends Feature<AercloudFeature.Config> {
    public enum Shape implements StringRepresentable {
        PUFF("puff"), STREAK("streak");

        public static final Codec<Shape> CODEC = StringRepresentable.fromEnum(Shape::values);
        private final String name;

        Shape(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    public record Config(BlockStateProvider block, Shape shape, IntProvider size, IntProvider thickness,
                         boolean windAligned) implements FeatureConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(i -> i.group(
                BlockStateProvider.CODEC.fieldOf("block").forGetter(Config::block),
                Shape.CODEC.fieldOf("shape").forGetter(Config::shape),
                IntProvider.codec(2, 26).fieldOf("size").forGetter(Config::size),
                IntProvider.codec(1, 5).optionalFieldOf("thickness", net.minecraft.util.valueproviders.ConstantInt.of(2))
                        .forGetter(Config::thickness),
                Codec.BOOL.optionalFieldOf("wind_aligned", true).forGetter(Config::windAligned)
        ).apply(i, Config::new));
    }

    public AercloudFeature() {
        super(Config.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<Config> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        Config config = context.config();
        BlockPos origin = context.origin();
        int size = config.size().sample(random);
        int thickness = config.thickness().sample(random);
        int placed = config.shape() == Shape.PUFF
                ? puff(level, random, config, origin, Math.min(size, 6), thickness)
                : streak(level, random, config, origin, Math.min(size, 26), thickness);
        if (placed > 0) {
            FeatureStats.placed("aercloud");
        }
        return placed > 0;
    }

    private static int puff(WorldGenLevel level, RandomSource random, Config config, BlockPos origin, int radius,
                            int thickness) {
        double flat = Math.max(0.35, thickness / (double) radius);
        int count = 0;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dy = -thickness; dy <= thickness; dy++) {
                    double distance = (dx * dx + dz * dz) / (double) (radius * radius) + dy * dy / (flat * radius * flat * radius);
                    if (distance + (random.nextDouble() - 0.5) * 0.35 < 1) {
                        count += put(level, config, random, origin.offset(dx, dy, dz));
                    }
                }
            }
        }
        return count;
    }

    private static int streak(WorldGenLevel level, RandomSource random, Config config, BlockPos origin, int length,
                              int thickness) {
        double angle = config.windAligned()
                ? Math.toRadians(AACommonConfig.DUNE_WIND_DIRECTION.get() + (random.nextDouble() - 0.5) * 40)
                : random.nextDouble() * Math.PI * 2;
        double windX = Math.sin(angle), windZ = -Math.cos(angle);
        double bob = random.nextDouble() * 6, drift = random.nextDouble() * 6;
        int half = length / 2;
        int count = 0;
        for (int step = -half; step <= half; step++) {
            double along = step / (double) Math.max(1, half);
            // the ribbon swells in the middle and thins to nothing at both ends
            double swell = Math.sqrt(Math.max(0, 1 - along * along));
            double sway = Math.sin(step * 0.35 + drift) * 1.6;
            int cx = origin.getX() + (int) Math.round(windX * step - windZ * sway);
            int cz = origin.getZ() + (int) Math.round(windZ * step + windX * sway);
            int cy = origin.getY() + (int) Math.round(Math.sin(step * 0.3 + bob));
            int width = 1 + (int) Math.round(swell * 2);
            int height = Math.max(1, (int) Math.round(thickness * swell));
            for (int w = -width; w <= width; w++) {
                for (int h = 0; h < height; h++) {
                    if (random.nextFloat() < 0.18f && Math.abs(w) == width) {
                        continue;
                    }
                    count += put(level, config, random, new BlockPos(cx + (int) Math.round(-windZ * w), cy + h,
                            cz + (int) Math.round(windX * w)));
                }
            }
        }
        return count;
    }

    private static int put(WorldGenLevel level, Config config, RandomSource random, BlockPos pos) {
        if (WriteBounds.writable(level, pos) && level.getBlockState(pos).isAir()) {
            level.setBlock(pos, config.block().getState(random, pos), Block.UPDATE_CLIENTS);
            return 1;
        }
        return 0;
    }
}
