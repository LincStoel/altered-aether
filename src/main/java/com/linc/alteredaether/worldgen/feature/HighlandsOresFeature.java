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
import com.linc.alteredaether.config.AACommonConfig;
import com.linc.alteredaether.worldgen.boulder.BoulderOres;
import com.linc.alteredaether.worldgen.tall.TallRemap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.structure.templatesystem.TagMatchTest;
import net.neoforged.fml.ModList;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

/**
 * One ore vein in the Highlands' holystone, of any ore the Aether has: each placement picks one of the ores the
 * dimension's biomes place (read from their generation settings, so it covers the Aether's own, Deep Aether's, Aether
 * Overworld Ores' and whatever else is loaded), weighted so the precious ones are rarer, and places it at twice the
 * size of that ore's usual vein. Only in the tall Aether, where ground above y 140 is an islet's.
 */
public final class HighlandsOresFeature extends Feature<NoneFeatureConfiguration> {
    /** How much bigger than its usual vein an ore's Highlands vein is. */
    private static final int SIZE_FACTOR = 2;
    private static final String AOO = "aether_overworld_ores";
    private static final TagKey<Block> HOLYSTONE =
            TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("aether", "holystone"));

    private record Vein(OreConfiguration config, int weight) {
    }

    private record Table(List<Vein> veins, int total) {
    }

    private static final Map<BiomeSource, Table> TABLES = new WeakHashMap<>();

    public HighlandsOresFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        BlockPos origin = context.origin();
        if (!TallRemap.isTallAether(context.chunkGenerator(), level.getMinBuildHeight())) {
            return false;
        }
        // Most of the Highlands is sky: skip a position whose section holds no blocks at all.
        ChunkAccess chunk = level.getChunk(origin);
        int section = chunk.getSectionIndex(origin.getY());
        if (section < 0 || section >= chunk.getSectionsCount() || chunk.getSection(section).hasOnlyAir()) {
            return false;
        }
        Table table = table(context.chunkGenerator().getBiomeSource());
        if (table.total() == 0) {
            return false;
        }
        RandomSource random = context.random();
        int roll = random.nextInt(table.total());
        Vein pick = table.veins().get(0);
        for (Vein vein : table.veins()) {
            roll -= vein.weight();
            if (roll < 0) {
                pick = vein;
                break;
            }
        }
        boolean placed = Feature.ORE.place(new FeaturePlaceContext<>(Optional.empty(), level, context.chunkGenerator(),
                random, origin, pick.config()));
        if (placed) {
            FeatureStats.placed("highlands_ores");
        }
        return placed;
    }

    private static Table table(BiomeSource source) {
        synchronized (TABLES) {
            return TABLES.computeIfAbsent(source, HighlandsOresFeature::scan);
        }
    }

    /** Every ore the dimension's biomes place, each at its biggest usual vein, doubled and set into holystone. */
    private static Table scan(BiomeSource source) {
        Map<Block, OreConfiguration> biggest = new LinkedHashMap<>();
        Map<Block, BlockState> states = new LinkedHashMap<>();
        for (Holder<Biome> biome : source.possibleBiomes()) {
            for (HolderSet<PlacedFeature> step : biome.value().getGenerationSettings().features()) {
                for (Holder<PlacedFeature> placed : step) {
                    placed.value().getFeatures().forEach(feature -> collect(feature, biggest, states));
                }
            }
        }
        List<Vein> veins = new ArrayList<>();
        int total = 0;
        for (Map.Entry<Block, OreConfiguration> entry : biggest.entrySet()) {
            OreConfiguration usual = entry.getValue();
            OreConfiguration doubled = new OreConfiguration(
                    List.of(OreConfiguration.target(new TagMatchTest(HOLYSTONE), states.get(entry.getKey()))),
                    usual.size * SIZE_FACTOR, usual.discardChanceOnAirExposure);
            int weight = entry.getKey() == AetherBlocks.GRAVITITE_ORE.get() ? 1 : BoulderOres.weight(entry.getKey());
            veins.add(new Vein(doubled, weight));
            total += weight;
        }
        return new Table(List.copyOf(veins), total);
    }

    /**
     * The holystone form of an ore another mod sets in stone or deepslate (Create's zinc, Oreganized's lead and silver,
     * the Tempest Peaks' copper), when Aether Overworld Ores has one, so every vein in the islets reads as the Aether's.
     */
    private static Block holystone(Block block) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
        if (id.getNamespace().equals("aether") || id.getNamespace().equals(AOO) || !id.getPath().endsWith("_ore")
                || !ModList.get().isLoaded(AOO) || !AACommonConfig.compat(AOO)) {
            return block;
        }
        String path = id.getPath().startsWith("deepslate_") ? id.getPath().substring("deepslate_".length()) : id.getPath();
        return BuiltInRegistries.BLOCK.getOptional(ResourceLocation.fromNamespaceAndPath(AOO, "holystone_" + path))
                .orElse(block);
    }

    private static void collect(ConfiguredFeature<?, ?> feature, Map<Block, OreConfiguration> biggest,
                                Map<Block, BlockState> states) {
        if (!(feature.config() instanceof OreConfiguration ore)) {
            return;
        }
        for (OreConfiguration.TargetBlockState target : ore.targetStates) {
            Block original = target.state.getBlock();
            Block block = holystone(original);
            if (!BoulderOres.anyOre(block)) {
                continue;
            }
            OreConfiguration seen = biggest.get(block);
            if (seen == null || ore.size > seen.size) {
                biggest.put(block, ore);
                states.put(block, block == original ? target.state : block.defaultBlockState());
            }
        }
    }
}
