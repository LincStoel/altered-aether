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
package com.linc.alteredaether.worldgen.boulder;

import com.aetherteam.aether.block.AetherBlocks;
import com.linc.alteredaether.AATags;
import com.linc.alteredaether.config.AACommonConfig;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.Tags;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * What a boulder is made of: a stone, and one ore buried through it.
 *
 * <p>The ore is meant to be out of place. A biome's ores are what its own features place (read from the generation
 * settings, so it covers what our biome modifiers and other worldgen mods add, and needs no list). A boulder landing in
 * a biome carries one of the three Aether ores, which every biome has, or, more often, an ore that some other Aether
 * biome has and this one doesn't. With no other ore in the Aether at all (no Aether Overworld Ores, no Deep Aether) it
 * is always one of the three.
 *
 * <p>Ores are only ever read from the dimension's own biomes, so the overworld's never turn up.
 */
public final class BoulderOres {
    /** The chance that a boulder carries an ore from another biome, when there is one. */
    private static final double FOREIGN_CHANCE = 0.7;
    /** Boulders are mostly holystone; the rest, with Deep Aether, split its stones. */
    private static final double ICESTONE_SHARE = 0.15;
    private static final double DEEP_AETHER_SHARE = 0.15;
    private static final List<String> DEEP_AETHER_STONES =
            List.of("deep_aether:aseterite", "deep_aether:clorite", "deep_aether:raw_clorite");

    /** One generator's ores: what each biome has, and every ore any of them has. */
    public record Table(Map<ResourceKey<Biome>, Set<Block>> owned, Set<Block> all) {
    }

    private static final Map<BiomeSource, Table> TABLES = new WeakHashMap<>();

    /** The ores of a dimension's biomes, worked out once. Safe from worldgen threads. */
    public static Table table(BiomeSource source) {
        synchronized (TABLES) {
            return TABLES.computeIfAbsent(source, BoulderOres::scan);
        }
    }

    private static Table scan(BiomeSource source) {
        Map<ResourceKey<Biome>, Set<Block>> byBiome = new HashMap<>();
        Set<Block> all = new LinkedHashSet<>();
        for (Holder<Biome> biome : source.possibleBiomes()) {
            Set<Block> ores = new HashSet<>();
            for (HolderSet<PlacedFeature> step : biome.value().getGenerationSettings().features()) {
                for (Holder<PlacedFeature> placed : step) {
                    placed.value().getFeatures().forEach(feature -> collect(feature, ores));
                }
            }
            biome.unwrapKey().ifPresent(key -> byBiome.put(key, ores));
            all.addAll(ores);
        }
        return new Table(byBiome, all);
    }

    private static void collect(ConfiguredFeature<?, ?> feature, Set<Block> ores) {
        if (feature.config() instanceof OreConfiguration ore) {
            for (OreConfiguration.TargetBlockState target : ore.targetStates) {
                Block block = holystone(target.state.getBlock());
                if (isOre(block)) {
                    ores.add(block);
                }
            }
        }
    }

    /**
     * Tempest Peaks' copper is the overworld's deepslate copper ore; a boulder carries Aether Overworld Ores' holystone
     * copper in its place when that is there, as every other ore in a boulder is a holystone one.
     */
    private static Block holystone(Block block) {
        if (block == Blocks.DEEPSLATE_COPPER_ORE) {
            return BuiltInRegistries.BLOCK.getOptional(ResourceLocation.fromNamespaceAndPath("aether_overworld_ores",
                    "holystone_copper_ore")).orElse(block);
        }
        return block;
    }

    private static boolean isOre(Block block) {
        BlockState state = block.defaultBlockState();
        return (state.is(Tags.Blocks.ORES) || BuiltInRegistries.BLOCK.getKey(block).getPath().endsWith("_ore"))
                && block != AetherBlocks.GRAVITITE_ORE.get() && !state.is(AATags.BOULDER_ORE_BLACKLIST);
    }

    /** The Aether's own ores, in every biome, and how common each is in a boulder. Gravitite is left out of boulders. */
    private static Block stockOre(RandomSource random) {
        return random.nextInt(7) < 4 ? AetherBlocks.AMBROSIUM_ORE.get() : AetherBlocks.ZANITE_ORE.get();
    }

    /**
     * The ore for a boulder landing in {@code biome}: usually one the biome doesn't have, if any other biome does,
     * weighted so the precious ones are rarer; otherwise one of the Aether's own.
     */
    public static Block pickOre(Table table, ResourceKey<Biome> biome, RandomSource random) {
        Set<Block> own = table.owned().getOrDefault(biome, Set.of());
        List<Block> foreign = new ArrayList<>();
        for (Block block : table.all()) {
            if (!own.contains(block) && !isStock(block)) {
                foreign.add(block);
            }
        }
        if (foreign.isEmpty() || random.nextDouble() >= FOREIGN_CHANCE) {
            return stockOre(random);
        }
        int total = 0;
        for (Block block : foreign) {
            total += weight(block);
        }
        int roll = random.nextInt(total);
        for (Block block : foreign) {
            roll -= weight(block);
            if (roll < 0) {
                return block;
            }
        }
        return foreign.get(0);
    }

    private static boolean isStock(Block block) {
        return block == AetherBlocks.AMBROSIUM_ORE.get() || block == AetherBlocks.ZANITE_ORE.get()
                || block == AetherBlocks.GRAVITITE_ORE.get();
    }

    /** Rarer ores weigh less, found by name since the pool is whatever the loaded mods add. */
    private static int weight(Block block) {
        String name = BuiltInRegistries.BLOCK.getKey(block).getPath();
        if (name.contains("diamond") || name.contains("emerald") || name.contains("uranium")) {
            return 1;
        }
        if (name.contains("gold") || name.contains("lapis") || name.contains("redstone") || name.contains("silver")
                || name.contains("sulfur") || name.contains("skyjade")) {
            return 2;
        }
        return 3;
    }

    /** The stone for a boulder: mostly holystone, some icestone, and with Deep Aether some of its own stones. */
    public static Block pickStone(RandomSource random) {
        double roll = random.nextDouble();
        if (roll < ICESTONE_SHARE) {
            return AetherBlocks.ICESTONE.get();
        }
        if (roll < ICESTONE_SHARE + DEEP_AETHER_SHARE && deepAether()) {
            ResourceLocation id = ResourceLocation.parse(DEEP_AETHER_STONES.get(random.nextInt(DEEP_AETHER_STONES.size())));
            return BuiltInRegistries.BLOCK.getOptional(id).orElse(AetherBlocks.HOLYSTONE.get());
        }
        return AetherBlocks.HOLYSTONE.get();
    }

    private static boolean deepAether() {
        return ModList.get().isLoaded("deep_aether") && AACommonConfig.compat("deep_aether");
    }

    private BoulderOres() {
    }
}
