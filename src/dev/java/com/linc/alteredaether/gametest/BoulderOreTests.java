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
package com.linc.alteredaether.gametest;

import com.aetherteam.aether.block.AetherBlocks;
import com.linc.alteredaether.AlteredAether;
import com.linc.alteredaether.worldgen.boulder.BoulderOres;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * What a boulder carries (see BoulderOres), on hand-built tables, since the Aether isn't there to scan: never an ore
 * its own biome has, other biomes' ores most of the time with the precious ones rarer, the Aether's own two
 * (ambrosium and zanite; gravitite is left out) always possible, and with nothing from elsewhere only those two.
 */
@GameTestHolder(AlteredAether.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BoulderOreTests {
    private static final ResourceKey<Biome> HOME = ResourceKey.create(Registries.BIOME, AlteredAether.id("test_home"));
    private static final ResourceKey<Biome> AWAY = ResourceKey.create(Registries.BIOME, AlteredAether.id("test_away"));
    private static final int SAMPLES = 6000;

    @GameTest(template = "arena")
    public static void oresAreOtherBiomesOres(GameTestHelper helper) {
        BoulderOres.Table table = new BoulderOres.Table(
                Map.of(HOME, Set.of(Blocks.IRON_ORE), AWAY, Set.of(Blocks.GOLD_ORE, Blocks.DIAMOND_ORE)),
                Set.of(Blocks.IRON_ORE, Blocks.GOLD_ORE, Blocks.DIAMOND_ORE));
        Map<Block, Integer> seen = tally(table, HOME);
        if (seen.containsKey(Blocks.IRON_ORE)) {
            helper.fail("a boulder landing where iron is native carried iron");
        } else if (seen.getOrDefault(Blocks.GOLD_ORE, 0) <= seen.getOrDefault(Blocks.DIAMOND_ORE, 0)
                || seen.getOrDefault(Blocks.DIAMOND_ORE, 0) == 0) {
            helper.fail("gold should be commoner than diamond, and diamond possible: " + seen);
        } else if (stock(seen) <= 0 || stock(seen) > SAMPLES * 0.45) {
            helper.fail("the Aether's own ores should be possible but not most of them: " + seen);
        } else {
            helper.succeed();
        }
    }

    @GameTest(template = "arena")
    public static void withNothingFromElsewhereOnlyTheAethersOwn(GameTestHelper helper) {
        BoulderOres.Table table = new BoulderOres.Table(Map.of(HOME, Set.of(Blocks.IRON_ORE)), Set.of(Blocks.IRON_ORE));
        Map<Block, Integer> seen = tally(table, HOME);
        if (stock(seen) != SAMPLES) {
            helper.fail("with no other biome's ore only the Aether's two are possible: " + seen);
        } else if (seen.size() != 2) {
            helper.fail("both of the Aether's ores should turn up: " + seen);
        } else {
            helper.succeed();
        }
    }

    @GameTest(template = "arena")
    public static void stonesAreMostlyHolystone(GameTestHelper helper) {
        RandomSource random = RandomSource.create(7);
        Map<Block, Integer> seen = new HashMap<>();
        for (int i = 0; i < SAMPLES; i++) {
            seen.merge(BoulderOres.pickStone(random), 1, Integer::sum);
        }
        int holystone = seen.getOrDefault(AetherBlocks.HOLYSTONE.get(), 0);
        int icestone = seen.getOrDefault(AetherBlocks.ICESTONE.get(), 0);
        if (holystone < SAMPLES * 0.6) {
            helper.fail("most boulders should be holystone: " + seen);
        } else if (icestone == 0 || icestone > holystone) {
            helper.fail("icestone should be possible but rarer: " + seen);
        } else {
            helper.succeed();
        }
    }

    private static Map<Block, Integer> tally(BoulderOres.Table table, ResourceKey<Biome> biome) {
        RandomSource random = RandomSource.create(11);
        Map<Block, Integer> seen = new HashMap<>();
        for (int i = 0; i < SAMPLES; i++) {
            seen.merge(BoulderOres.pickOre(table, biome, random), 1, Integer::sum);
        }
        return seen;
    }

    private static int stock(Map<Block, Integer> seen) {
        return seen.getOrDefault(AetherBlocks.AMBROSIUM_ORE.get(), 0) + seen.getOrDefault(AetherBlocks.ZANITE_ORE.get(), 0)
                + seen.getOrDefault(AetherBlocks.GRAVITITE_ORE.get(), 0);
    }

    private BoulderOreTests() {
    }
}
