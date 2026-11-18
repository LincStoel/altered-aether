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
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.SnowyDirtBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;

/**
 * The snowline: wherever the ground stands above it, whatever the biome, it is frosted: snow over the ground and the
 * leaves, grass turned snowy, still water frozen. The line wanders up and down a few blocks on a noise, and the snow
 * thickens with height above it. In the tall Aether it sits among the Highlands' highest islets ({@code tall_line});
 * in the stock Aether at the same height remapped.
 */
public final class SnowlineFeature extends Feature<SnowlineFeature.Config> {
    public record Config(int tallLine, int wander) implements FeatureConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("tall_line").forGetter(Config::tallLine),
                Codec.intRange(0, 32).optionalFieldOf("wander", 6).forGetter(Config::wander)
        ).apply(i, Config::new));
    }

    private static volatile SimplexNoise noise;
    private static volatile long noiseSeed;

    public SnowlineFeature() {
        super(Config.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<Config> context) {
        WorldGenLevel level = context.level();
        Config config = context.config();
        boolean tall = level.getMinBuildHeight() < 0;
        int line = tall ? config.tallLine() : (int) Math.round((config.tallLine() + 64) / 1.5);
        if (level.getMaxBuildHeight() <= line - config.wander()) {
            return false;
        }
        SimplexNoise wobble = noise(level.getSeed());
        int minX = context.origin().getX() & ~15, minZ = context.origin().getZ() & ~15;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        boolean placed = false;
        for (int i = 0; i < 256; i++) {
            int x = minX + (i & 15), z = minZ + (i >> 4);
            int top = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
            double local = line + config.wander() * wobble.getValue(x / 80.0, z / 80.0);
            if (top < local) {
                continue;
            }
            pos.set(x, top - 1, z);
            BlockState ground = level.getBlockState(pos);
            if (ground.is(Blocks.WATER) && ground.getFluidState().isSource()) {
                level.setBlock(pos, Blocks.ICE.defaultBlockState(), 2);
                placed = true;
                continue;
            }
            boolean leaves = ground.is(BlockTags.LEAVES);
            if (!leaves && !ground.isFaceSturdy(level, pos, Direction.UP)) {
                continue;
            }
            if (ground.hasProperty(SnowyDirtBlock.SNOWY)) {
                level.setBlock(pos, ground.setValue(SnowyDirtBlock.SNOWY, true), 2);
            } else if (ground.hasProperty(BlockStateProperties.SNOWY)) {
                level.setBlock(pos, ground.setValue(BlockStateProperties.SNOWY, true), 2);
            }
            // one layer at the line, a block's worth eight blocks over it
            int layers = Math.max(1, Math.min(leaves ? 2 : 8, 1 + (int) ((top - local) * 0.9)));
            pos.move(0, 1, 0);
            if (level.getBlockState(pos).isAir()) {
                level.setBlock(pos, Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, layers), 2);
                placed = true;
            }
        }
        return placed;
    }

    private static SimplexNoise noise(long seed) {
        SimplexNoise current = noise;
        if (current == null || noiseSeed != seed) {
            current = new SimplexNoise(new LegacyRandomSource(seed ^ 0x5A0711EL));
            noise = current;
            noiseSeed = seed;
        }
        return current;
    }
}
