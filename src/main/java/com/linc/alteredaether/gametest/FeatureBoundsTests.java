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

import com.linc.alteredaether.AlteredAether;
import com.linc.alteredaether.worldgen.feature.AercloudBridgeFeature;
import com.linc.alteredaether.worldgen.feature.AercloudFeature;
import com.linc.alteredaether.worldgen.feature.BoulderFeature;
import com.linc.alteredaether.worldgen.feature.CopperSpikeFeature;
import com.linc.alteredaether.worldgen.feature.CopperVeinFeature;
import com.linc.alteredaether.worldgen.feature.RuinedPillarFeature;
import com.linc.alteredaether.worldgen.feature.SnowDriftFeature;
import com.linc.alteredaether.worldgen.feature.StrikeCraterFeature;
import com.linc.alteredaether.worldgen.feature.TotemFeature;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Every configured feature of this mod's own decoration types, placed eight times on a holystone floor: no block may
 * land more than 16 blocks out from where it was placed, the reach a feature has in worldgen before it writes into
 * chunks that aren't there. Run with {@code gradlew runGameTestServer}.
 */
@GameTestHolder(AlteredAether.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FeatureBoundsTests {
    /** The feature types to check: our decoration, but not the chunk-wide basin fill and landmark builder. */
    private static final Set<Class<?>> CHECKED = Set.of(BoulderFeature.class, CopperSpikeFeature.class,
            CopperVeinFeature.class,
            SnowDriftFeature.class, RuinedPillarFeature.class, StrikeCraterFeature.class, AercloudFeature.class,
            AercloudBridgeFeature.class, TotemFeature.class);
    private static final int REACH = 16, SCAN = 22, PLACEMENTS = 8;

    @GameTest(template = "arena", timeoutTicks = 200)
    public static void featuresStayInReach(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos centre = helper.absolutePos(new BlockPos(24, 12, 24));
        BlockState floor = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("aether:holystone")).defaultBlockState();
        List<String> failures = new ArrayList<>();
        int checked = 0;
        for (var entry : level.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE).entrySet()) {
            ResourceLocation id = entry.getKey().location();
            ConfiguredFeature<?, ?> feature = entry.getValue();
            if (!id.getNamespace().equals(AlteredAether.MOD_ID) || !CHECKED.contains(feature.feature().getClass())) {
                continue;
            }
            checked++;
            for (int i = 0; i < PLACEMENTS; i++) {
                clear(level, centre, floor);
                feature.place(level, level.getChunkSource().getGenerator(), RandomSource.create(id.hashCode() * 31L + i),
                        centre);
                BlockPos stray = stray(level, centre);
                if (stray != null) {
                    failures.add(id + " reached " + stray.subtract(centre).toShortString());
                    break;
                }
            }
        }
        clear(level, centre, floor);
        if (checked == 0) {
            helper.fail("No features of ours were found to check");
        } else if (!failures.isEmpty()) {
            helper.fail(String.join("; ", failures));
        } else {
            AlteredAether.LOGGER.info("Feature bounds: {} configured features each stayed within {} blocks", checked, REACH);
            helper.succeed();
        }
    }

    /** Air over the whole scan box, with a holystone floor one block under the centre. */
    private static void clear(ServerLevel level, BlockPos centre, BlockState floor) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = -SCAN; dx <= SCAN; dx++) {
            for (int dz = -SCAN; dz <= SCAN; dz++) {
                for (int dy = -12; dy <= 27; dy++) {
                    BlockState state = dy == -1 && Math.abs(dx) <= REACH && Math.abs(dz) <= REACH ? floor
                            : Blocks.AIR.defaultBlockState();
                    level.setBlock(pos.setWithOffset(centre, dx, dy, dz), state, Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    /** A placed block beyond the feature's reach, or null if there is none. */
    private static BlockPos stray(ServerLevel level, BlockPos centre) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = -SCAN; dx <= SCAN; dx++) {
            for (int dz = -SCAN; dz <= SCAN; dz++) {
                if (Math.abs(dx) <= REACH && Math.abs(dz) <= REACH) {
                    continue;
                }
                for (int dy = -12; dy <= 27; dy++) {
                    if (!level.getBlockState(pos.setWithOffset(centre, dx, dy, dz)).isAir()) {
                        return pos.immutable();
                    }
                }
            }
        }
        return null;
    }

    private FeatureBoundsTests() {
    }
}
