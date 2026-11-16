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

import com.aetherteam.aether.block.AetherBlocks;
import com.linc.alteredaether.worldgen.boulder.BoulderOres;
import com.linc.alteredaether.worldgen.boulder.BoulderShape;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

import java.util.Map;

/**
 * A boulder that fell a long time ago and is lodged in the ground: half buried, its stone mossed over, its ore (chosen
 * as for a boulder that falls now, see {@link BoulderOres}) still inside. These are what worlds without Sable get of the
 * falling boulders, and what tells a player with Sable what the ones that fall leave behind.
 *
 * <p>It overwrites soil and stone where it sinks, as a boulder that landed hard would have, and never anything else.
 */
public final class AncientBoulderFeature extends Feature<NoneFeatureConfiguration> {
    private static final TagKey<Block> HOLYSTONE =
            TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("aether", "holystone"));
    private static final float ORE_CHANCE = 0.14f;
    private static final float MOSS_CHANCE = 0.6f;

    public AncientBoulderFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        BlockPos origin = context.origin();
        BlockState ground = level.getBlockState(origin.below());
        if (ground.isAir() || !ground.getFluidState().isEmpty() || !level.getBlockState(origin).isAir()) {
            return false;
        }

        int radius = 2 + random.nextInt(2) + (random.nextInt(8) == 0 ? 1 : 0);
        double height = BoulderShape.height(radius, 0.8);
        BlockPos center = origin.below((int) Math.round(height * 0.1));
        Block stone = BoulderOres.pickStone(random);
        BlockState stoneState = stone.defaultBlockState();
        BlockState mossy = AetherBlocks.MOSSY_HOLYSTONE.get().defaultBlockState();
        double reach = Math.min(15, radius * 2 + 2);
        Map<BlockPos, BlockState> shape = BoulderShape.carve(random, BoulderShape.lobes(random, radius, height, 0.8),
                center, origin, reach, radius, at -> lodgeable(level, at), at -> stoneState);
        if (shape.size() < 8) {
            return false;
        }

        var table = BoulderOres.table(context.chunkGenerator().getBiomeSource());
        Block ore = BoulderOres.pickOre(table, level.getBiome(origin).unwrapKey().orElse(null), random);
        boolean buriedOnly = ore == AetherBlocks.GRAVITITE_ORE.get();
        for (Map.Entry<BlockPos, BlockState> entry : shape.entrySet()) {
            BlockPos pos = entry.getKey();
            BlockState state = entry.getValue();
            if (random.nextFloat() < ORE_CHANCE && (!buriedOnly || BoulderShape.enclosed(shape, pos))) {
                state = ore.defaultBlockState();
            } else if (stone == AetherBlocks.HOLYSTONE.get() && !shape.containsKey(pos.above())
                    && random.nextFloat() < MOSS_CHANCE) {
                state = mossy;
            }
            level.setBlock(pos, state, Block.UPDATE_CLIENTS);
        }
        FeatureStats.placed("ancient_boulder");
        FeatureStats.placed("ancient_boulder/ore/" + net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(ore).getPath());
        FeatureStats.placed("ancient_boulder/stone/" + net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(stone).getPath());
        return true;
    }

    /** Where it may be: open air and what grows in it, and the soil and stone it sinks into. */
    private static boolean lodgeable(WorldGenLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || state.canBeReplaced() && state.getFluidState().isEmpty()) {
            return true;
        }
        return state.getFluidState().isEmpty() && (state.is(BlockTags.DIRT) || state.is(HOLYSTONE)
                || state.is(BlockTags.SAND) || state.is(BlockTags.BASE_STONE_OVERWORLD));
    }
}
