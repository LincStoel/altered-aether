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

import com.linc.alteredaether.worldgen.AABiomes;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;

/**
 * Moss over the shrouded forest's canopy, after the Aether II's tree moss: in broad patches that follow a noise, every
 * leaf open to the sky gets a carpet of moss on top, so from above the forest reads as a furred, uneven green. Runs
 * once per chunk over its own columns.
 */
public final class CanopyMossFeature extends Feature<CanopyMossFeature.Config> {
    public record Config(BlockStateProvider moss, float cover) implements FeatureConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(i -> i.group(
                BlockStateProvider.CODEC.fieldOf("moss").forGetter(Config::moss),
                Codec.floatRange(0, 1).optionalFieldOf("cover", 0.55f).forGetter(Config::cover)
        ).apply(i, Config::new));
    }

    private static volatile SimplexNoise noise;
    private static volatile long noiseSeed;

    public CanopyMossFeature() {
        super(Config.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<Config> context) {
        WorldGenLevel level = context.level();
        SimplexNoise patches = noise(level.getSeed());
        // cover 0..1 maps to a noise threshold of 1..-1: the share of the canopy the patches take, roughly
        double threshold = 1 - 2 * context.config().cover();
        int minX = context.origin().getX() & ~15, minZ = context.origin().getZ() & ~15;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        boolean placed = false;
        for (int i = 0; i < 256; i++) {
            int x = minX + (i & 15), z = minZ + (i >> 4);
            int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
            pos.set(x, top - 1, z);
            if (!level.getBlockState(pos).is(BlockTags.LEAVES) || !level.getBlockState(pos.above()).isAir()
                    || patches.getValue(x / 24.0, z / 24.0) < threshold
                    || !level.getBiome(pos).is(AABiomes.SHROUDED_FOREST)) {
                continue;
            }
            pos.move(0, 1, 0);
            level.setBlock(pos, context.config().moss().getState(context.random(), pos), 2);
            placed = true;
        }
        return placed;
    }

    private static SimplexNoise noise(long seed) {
        SimplexNoise current = noise;
        if (current == null || noiseSeed != seed) {
            current = new SimplexNoise(new LegacyRandomSource(seed ^ 0x6D055L));
            noise = current;
            noiseSeed = seed;
        }
        return current;
    }
}
