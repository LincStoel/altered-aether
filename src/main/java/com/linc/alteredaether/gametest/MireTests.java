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
import com.linc.alteredaether.worldgen.grove.Mire;
import com.linc.alteredaether.worldgen.grove.MireBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.MangroveRootsBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * A swamp lake must hold its water however the chunks it spans happen to be generated: built tile by tile in a
 * shuffled order, with a dip in its bank and a hole beside it, every block of its water is set ticking, and none may
 * flow. Its giant's islet must stay dry, and roots must stand in the water.
 */
@GameTestHolder(AlteredAether.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MireTests {
    private static final int SCAN = 22, DEPTH = 8, ABOVE = 12, TILE = 8;

    @GameTest(template = "arena", timeoutTicks = 200)
    public static void mireHoldsItsWater(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos centre = helper.absolutePos(new BlockPos(24, 2, 24));
        int cx = centre.getX(), cz = centre.getZ(), ground = centre.getY() + DEPTH - 1;
        clear(level, centre);
        Block grass = block("aether:aether_grass_block", Blocks.GRASS_BLOCK);
        Block dirt = block("aether:aether_dirt", Blocks.DIRT);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = -SCAN; dx <= SCAN; dx++) {
            for (int dz = -SCAN; dz <= SCAN; dz++) {
                for (int y = centre.getY(); y <= ground; y++) {
                    level.setBlock(pos.set(cx + dx, y, cz + dz), (y == ground ? grass : y > ground - 3 ? dirt
                            : Blocks.STONE).defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
        }
        // A dip in the bank, just outside the shore, and a pit beside the water further round.
        for (int y = ground - 2; y <= ground; y++) {
            level.setBlock(pos.set(cx + 12, y, cz), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            level.setBlock(pos.set(cx + 12, y, cz + 1), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            level.setBlock(pos.set(cx, y, cz - 13), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        Mire mire = new Mire(cx, cz, ground - 1, 10, 2.5, 0.7, 2.1,
                List.of(new Mire.Islet(cx, cz, 3.5, 1.3, ground)), 0x5EEDL);
        MireBuilder.Waters waters = new MireBuilder.Waters(List.of(mire));

        // Tiles in a shuffled order, each dug, dressed and sealed on its own, as chunks are.
        List<int[]> tiles = new ArrayList<>();
        for (int tx = -SCAN; tx <= SCAN; tx += TILE) {
            for (int tz = -SCAN; tz <= SCAN; tz += TILE) {
                tiles.add(new int[]{cx + tx, cz + tz});
            }
        }
        net.minecraft.Util.shuffle(tiles, RandomSource.create(42));
        for (int[] t : tiles) {
            int x1 = Math.min(t[0] + TILE - 1, cx + SCAN), z1 = Math.min(t[1] + TILE - 1, cz + SCAN);
            MireBuilder.dig(level, t[0], t[1], x1, z1, waters);
            MireBuilder.dress(level, t[0], t[1], x1, z1, waters);
            MireBuilder.seal(level, t[0], t[1], x1, z1, waters);
        }

        int water = 0, roots = 0;
        for (int dx = -SCAN; dx <= SCAN; dx++) {
            for (int dz = -SCAN; dz <= SCAN; dz++) {
                for (int y = centre.getY(); y <= ground + 2; y++) {
                    BlockState state = level.getBlockState(pos.set(cx + dx, y, cz + dz));
                    if (!state.getFluidState().isEmpty()) {
                        water++;
                        level.scheduleTick(pos.immutable(), Fluids.WATER, 1);
                    }
                    if (state.getBlock() instanceof MangroveRootsBlock) {
                        roots++;
                    }
                }
            }
        }
        if (!level.getBlockState(new BlockPos(cx, ground, cz)).getFluidState().isEmpty()
                || level.getBlockState(new BlockPos(cx, ground + 1, cz)).is(Blocks.WATER)) {
            helper.fail("The islet at the lake's heart is under water");
            return;
        }
        if (water < 100 || roots == 0) {
            helper.fail("The lake holds " + water + " blocks of water and " + roots + " of roots");
            return;
        }
        int held = water, rooted = roots;
        helper.runAfterDelay(40, () -> {
            BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
            for (int dx = -SCAN; dx <= SCAN; dx++) {
                for (int dz = -SCAN; dz <= SCAN; dz++) {
                    for (int y = centre.getY(); y <= ground + 2; y++) {
                        var fluid = level.getFluidState(at.set(cx + dx, y, cz + dz));
                        if (!fluid.isEmpty() && !fluid.isSource()) {
                            String where = at.toShortString();
                            clear(level, centre);
                            helper.fail("Mire water flows at " + where);
                            return;
                        }
                    }
                }
            }
            clear(level, centre);
            AlteredAether.LOGGER.info("Mire: {} blocks of water and {} of roots, none flowing", held, rooted);
            helper.succeed();
        });
    }

    private static Block block(String id, Block fallback) {
        return BuiltInRegistries.BLOCK.getOptional(ResourceLocation.parse(id)).orElse(fallback);
    }

    private static void clear(ServerLevel level, BlockPos centre) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = -SCAN - 1; dx <= SCAN + 1; dx++) {
            for (int dz = -SCAN - 1; dz <= SCAN + 1; dz++) {
                for (int dy = 0; dy <= DEPTH + ABOVE; dy++) {
                    level.setBlock(pos.setWithOffset(centre, dx, dy, dz), Blocks.AIR.defaultBlockState(),
                            Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    private MireTests() {
    }
}
