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
import com.linc.alteredaether.debug.WorldRender;
import com.linc.alteredaether.worldgen.ruin.RuinPlan;
import com.linc.alteredaether.worldgen.ruin.Ruins;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Every kind of ruin must stay within its reach of its centre, and must come out the same whether it is built whole or
 * in tiles in any order, as the chunks of a landmark build it. On a gentle slope, so stones anchored at their own
 * centres are tested too. Each kind's first sample is rendered to {@code renders/}.
 */
@GameTestHolder(AlteredAether.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RuinTests {
    private static final int SCAN = Ruins.REACH + 1, DEPTH = 6, ABOVE = 22, TILE = 7, SAMPLES = 3;

    @GameTest(template = "arena", timeoutTicks = 200)
    public static void ruinsHoldTogether(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos centre = helper.absolutePos(new BlockPos(24, 2, 24));
        int total = 0;
        for (Ruins.Kind kind : Ruins.Kind.values()) {
            for (int sample = 0; sample < SAMPLES; sample++) {
                long seed = 0xA11EL * 31 + kind.ordinal() * 7L + sample;
                RuinPlan plan = Ruins.draw(kind, RandomSource.create(seed), centre.getX(), centre.getZ());
                if (plan.pieces().isEmpty()) {
                    fail(helper, centre, kind.id() + " " + sample + " is empty");
                    return;
                }
                if (plan.bounds().minX() < centre.getX() - Ruins.REACH || plan.bounds().maxX() > centre.getX() + Ruins.REACH
                        || plan.bounds().minZ() < centre.getZ() - Ruins.REACH
                        || plan.bounds().maxZ() > centre.getZ() + Ruins.REACH) {
                    fail(helper, centre, kind.id() + " " + sample + " reaches past " + Ruins.REACH + ": " + plan.bounds());
                    return;
                }
                // The bare slope, as a landmark reads it from the terrain: building one tile never moves another's
                // ground.
                RuinPlan.Ground slope = (x, z) -> top(centre, x);
                ground(level, centre);
                plan.place(level, centre.getX() - SCAN, centre.getZ() - SCAN, centre.getX() + SCAN,
                        centre.getZ() + SCAN, slope);
                List<BlockState> whole = snapshot(level, centre);
                if (sample == 0) {
                    WorldRender.render(level, "ruin_" + kind.id(), centre.getX() - SCAN, centre.getZ() - SCAN,
                            centre.getX() + SCAN, centre.getZ() + SCAN, centre.getY(), centre.getY() + DEPTH + ABOVE, 6);
                }
                ground(level, centre);
                List<int[]> tiles = new ArrayList<>();
                for (int tx = -SCAN; tx <= SCAN; tx += TILE) {
                    for (int tz = -SCAN; tz <= SCAN; tz += TILE) {
                        tiles.add(new int[]{centre.getX() + tx, centre.getZ() + tz});
                    }
                }
                net.minecraft.Util.shuffle(tiles, RandomSource.create(seed));
                for (int[] t : tiles) {
                    plan.place(level, t[0], t[1], Math.min(t[0] + TILE - 1, centre.getX() + SCAN),
                            Math.min(t[1] + TILE - 1, centre.getZ() + SCAN), slope);
                }
                List<BlockState> tiled = snapshot(level, centre);
                int placed = 0;
                for (int i = 0; i < whole.size(); i++) {
                    if (whole.get(i) != tiled.get(i)) {
                        fail(helper, centre, kind.id() + " " + sample + " differs when built in tiles");
                        return;
                    }
                    if (!whole.get(i).isAir() && !whole.get(i).is(Blocks.DIRT) && !whole.get(i).is(Blocks.GRASS_BLOCK)) {
                        placed++;
                    }
                }
                if (placed < 20) {
                    fail(helper, centre, kind.id() + " " + sample + " placed only " + placed + " blocks");
                    return;
                }
                total += placed;
            }
        }
        clear(level, centre);
        AlteredAether.LOGGER.info("Ruins: {} blocks over {} samples, the same whole or tiled", total,
                SAMPLES * Ruins.Kind.values().length);
        helper.succeed();
    }

    /** A gentle slope of dirt under grass, rising a block every six eastward. */
    private static void ground(ServerLevel level, BlockPos centre) {
        clear(level, centre);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = -SCAN; dx <= SCAN; dx++) {
            int top = top(centre, centre.getX() + dx);
            for (int dz = -SCAN; dz <= SCAN; dz++) {
                for (int y = centre.getY(); y <= top; y++) {
                    level.setBlock(pos.set(centre.getX() + dx, y, centre.getZ() + dz),
                            (y == top ? Blocks.GRASS_BLOCK : Blocks.DIRT).defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    private static int top(BlockPos centre, int x) {
        return centre.getY() + DEPTH - 3 + Math.floorDiv(x - centre.getX() + SCAN, 6);
    }

    private static List<BlockState> snapshot(ServerLevel level, BlockPos centre) {
        List<BlockState> states = new ArrayList<>();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = -SCAN; dx <= SCAN; dx++) {
            for (int dz = -SCAN; dz <= SCAN; dz++) {
                for (int dy = 0; dy <= DEPTH + ABOVE; dy++) {
                    states.add(level.getBlockState(pos.setWithOffset(centre, dx, dy, dz)));
                }
            }
        }
        return states;
    }

    private static void fail(GameTestHelper helper, BlockPos centre, String message) {
        clear(helper.getLevel(), centre);
        helper.fail(message);
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

    private RuinTests() {
    }
}
