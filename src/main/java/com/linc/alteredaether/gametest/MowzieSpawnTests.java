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
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Mowzie's own spawn rules shut its mobs out of the Aether; ours let them back in (see SpawnPlacementEvents). */
@GameTestHolder(AlteredAether.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MowzieSpawnTests {
    private static final ResourceKey<Level> THE_AETHER = ResourceKey.create(Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath("aether", "the_aether"));

    @GameTest(template = "arena")
    public static void nagasSpawnOnAetherGround(GameTestHelper helper) {
        var naga = BuiltInRegistries.ENTITY_TYPE.getOptional(ResourceLocation.parse("mowziesmobs:naga"));
        ServerLevel aether = helper.getLevel().getServer().getLevel(THE_AETHER);
        if (naga.isEmpty() || aether == null) {
            helper.succeed();
            return;
        }
        EntityType<?> type = naga.get();
        RandomSource random = RandomSource.create(0x9A6AL);
        // Far out and just under the build limit, so nothing above hides the sky.
        BlockPos ground = new BlockPos(30_000, aether.getMaxBuildHeight() - 6, 30_000);
        ChunkPos chunk = new ChunkPos(ground);
        aether.setChunkForced(chunk.x, chunk.z, true);
        try {
            for (int y = 1; y <= 4; y++) {
                aether.setBlock(ground.above(y), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
            BlockPos spawn = ground.above();
            aether.setBlock(ground, BuiltInRegistries.BLOCK.get(ResourceLocation.parse("aether:aether_grass_block"))
                    .defaultBlockState(), Block.UPDATE_CLIENTS);
            helper.assertTrue(SpawnPlacements.checkSpawnRules(type, aether, MobSpawnType.NATURAL, spawn, random),
                    "naga refused on Aether grass");
            aether.setBlock(ground, BuiltInRegistries.BLOCK.get(ResourceLocation.parse("aether:holystone"))
                    .defaultBlockState(), Block.UPDATE_CLIENTS);
            helper.assertTrue(SpawnPlacements.checkSpawnRules(type, aether, MobSpawnType.NATURAL, spawn, random),
                    "naga refused on holystone");
            aether.setBlock(ground, Blocks.GLASS.defaultBlockState(), Block.UPDATE_CLIENTS);
            helper.assertFalse(SpawnPlacements.checkSpawnRules(type, aether, MobSpawnType.NATURAL, spawn, random),
                    "naga allowed on glass");
        } finally {
            aether.setChunkForced(chunk.x, chunk.z, false);
        }
        helper.succeed();
    }

    private MowzieSpawnTests() {
    }
}
