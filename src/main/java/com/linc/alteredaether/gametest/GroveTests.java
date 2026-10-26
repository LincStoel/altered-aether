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
import com.linc.alteredaether.worldgen.grove.Giant;
import com.linc.alteredaether.worldgen.grove.GroveBuilder;
import com.linc.alteredaether.worldgen.grove.GroveSamples;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

/**
 * The giants' canopies must hold on their own: every leaf a crown places has to be within six steps of a log through
 * other leaves, counted the way the game counts, or it would decay as soon as anything near it changed. Crowns are
 * drawn the same way for every kind of giant, so a sample of them stands for all. Natural wood must also keep the
 * Aether's doubled drops.
 */
@GameTestHolder(AlteredAether.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GroveTests {
    private static final int SCAN = 22, BELOW = 4, ABOVE = 27, SAMPLES = 8;

    @GameTest(template = "arena", timeoutTicks = 400)
    public static void canopiesHoldOn(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos centre = helper.absolutePos(new BlockPos(24, 4, 24));
        int leaves = 0;
        for (int i = 0; i < SAMPLES; i++) {
            clear(level, centre);
            RandomSource random = RandomSource.create(0x6B0E5L * 31 + i);
            Giant crown = GroveSamples.crown(random, centre.getX(), centre.getY() + BELOW, centre.getZ(),
                    6 + i % 4);
            GroveBuilder.build(level, centre.getX() - SCAN, centre.getZ() - SCAN, centre.getX() + SCAN,
                    centre.getZ() + SCAN, crown);
            String problem = check(level, centre);
            if (problem.startsWith("!")) {
                clear(level, centre);
                helper.fail("crown " + i + ": " + problem.substring(1));
                return;
            }
            leaves += Integer.parseInt(problem);
        }
        clear(level, centre);
        if (leaves == 0) {
            helper.fail("The sample crowns placed no leaves");
            return;
        }
        AlteredAether.LOGGER.info("Grove canopies: {} leaves in {} crowns, all within reach of a log", leaves, SAMPLES);
        helper.succeed();
    }

    /** The number of leaves, or a problem prefixed with '!'. */
    private static String check(ServerLevel level, BlockPos centre) {
        Map<BlockPos, Integer> steps = new HashMap<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        int leafCount = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = -SCAN; dx <= SCAN; dx++) {
            for (int dz = -SCAN; dz <= SCAN; dz++) {
                for (int dy = 0; dy <= BELOW + ABOVE; dy++) {
                    BlockState state = level.getBlockState(pos.setWithOffset(centre, dx, dy, dz));
                    if (state.is(BlockTags.LOGS)) {
                        if (!doubled(state)) {
                            return "!natural wood without double drops at " + pos.toShortString() + ": " + state;
                        }
                        steps.put(pos.immutable(), 0);
                        queue.add(pos.immutable());
                    } else if (state.getBlock() instanceof LeavesBlock) {
                        leafCount++;
                        if (state.getValue(LeavesBlock.PERSISTENT)) {
                            return "!persistent leaves at " + pos.toShortString();
                        }
                    }
                }
            }
        }
        // Breadth-first from every log through leaves, six steps at most, as LeavesBlock counts distance.
        while (!queue.isEmpty()) {
            BlockPos at = queue.poll();
            int d = steps.get(at);
            if (d >= LeavesBlock.DECAY_DISTANCE - 1) {
                continue;
            }
            for (Direction direction : Direction.values()) {
                BlockPos next = at.relative(direction);
                if (!steps.containsKey(next) && level.getBlockState(next).getBlock() instanceof LeavesBlock) {
                    steps.put(next, d + 1);
                    queue.add(next);
                }
            }
        }
        for (int dx = -SCAN; dx <= SCAN; dx++) {
            for (int dz = -SCAN; dz <= SCAN; dz++) {
                for (int dy = 0; dy <= BELOW + ABOVE; dy++) {
                    pos.setWithOffset(centre, dx, dy, dz);
                    if (level.getBlockState(pos).getBlock() instanceof LeavesBlock && !steps.containsKey(pos)) {
                        return "!leaves more than six steps from any log at " + pos.toShortString();
                    }
                }
            }
        }
        return Integer.toString(leafCount);
    }

    private static boolean doubled(BlockState state) {
        for (Property<?> property : state.getProperties()) {
            if (property.getName().equals("double_drops") && property instanceof BooleanProperty flag) {
                return state.getValue(flag);
            }
        }
        return true;
    }

    private static void clear(ServerLevel level, BlockPos centre) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = -SCAN; dx <= SCAN; dx++) {
            for (int dz = -SCAN; dz <= SCAN; dz++) {
                for (int dy = 0; dy <= BELOW + ABOVE; dy++) {
                    level.setBlock(pos.setWithOffset(centre, dx, dy, dz), Blocks.AIR.defaultBlockState(),
                            Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    private GroveTests() {
    }
}
