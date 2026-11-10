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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * A spring stump, built in tiles as chunks would build it: its pool must brim with still water, every log the pool's
 * water touches must be stripped with its grain lying level, as water running toward a notch leaves it, and once the
 * water is let run, it must spill from the notches down the flanks.
 */
@GameTestHolder(AlteredAether.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SpringStumpTests {
    private static final int SCAN = 26, DEPTH = 4, ABOVE = 24, TILE = 16;

    @GameTest(template = "arena", timeoutTicks = 300)
    public static void springBrimsAndRuns(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos centre = helper.absolutePos(new BlockPos(24, 2, 24));
        int cx = centre.getX(), cz = centre.getZ(), ground = centre.getY() + DEPTH - 1;
        clear(level, centre);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = -SCAN; dx <= SCAN; dx++) {
            for (int dz = -SCAN; dz <= SCAN; dz++) {
                for (int y = centre.getY(); y <= ground; y++) {
                    level.setBlock(pos.set(cx + dx, y, cz + dz),
                            (y == ground ? Blocks.GRASS_BLOCK : Blocks.DIRT).defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
        }
        Giant stump = GroveSamples.springStump(RandomSource.create(0x5B12L), cx, ground, cz);
        BlockPos spring = stump.points("spring").getFirst();
        for (int tx = -SCAN; tx <= SCAN; tx += TILE) {
            for (int tz = -SCAN; tz <= SCAN; tz += TILE) {
                GroveBuilder.build(level, cx + tx, cz + tz, Math.min(cx + tx + TILE - 1, cx + SCAN),
                        Math.min(cz + tz + TILE - 1, cz + SCAN), stump);
            }
        }
        int water = spring.getY();
        int pooled = 0;
        for (int dx = -SCAN; dx <= SCAN; dx++) {
            for (int dz = -SCAN; dz <= SCAN; dz++) {
                for (int y = ground; y <= water; y++) {
                    pos.set(cx + dx, y, cz + dz);
                    BlockState state = level.getBlockState(pos);
                    if (!state.is(Blocks.WATER) || y < water - 4 || Math.abs(dx) > 9 || Math.abs(dz) > 9) {
                        continue;
                    }
                    pooled++;
                    for (Direction d : Direction.values()) {
                        BlockState next = level.getBlockState(pos.relative(d));
                        if (!next.is(BlockTags.LOGS)) {
                            continue;
                        }
                        String id = BuiltInRegistries.BLOCK.getKey(next.getBlock()).getPath();
                        if (!id.contains("stripped")) {
                            fail(helper, centre, "bark wood touches the pool at " + pos.relative(d).toShortString()
                                    + ": " + next);
                            return;
                        }
                        // The pool's floor: the water drifts across it toward a notch, so its grain lies level.
                        if (d == Direction.DOWN && Math.hypot(pos.getX() - cx, pos.getZ() - cz) < 4
                                && next.hasProperty(RotatedPillarBlock.AXIS)
                                && next.getValue(RotatedPillarBlock.AXIS) == Direction.Axis.Y) {
                            fail(helper, centre, "upright grain in the pool's floor at " + pos.relative(d).toShortString());
                            return;
                        }
                    }
                }
            }
        }
        if (pooled < 40 || !level.getBlockState(new BlockPos(cx, water, cz)).is(Blocks.WATER)) {
            fail(helper, centre, "The pool holds only " + pooled + " blocks of water");
            return;
        }
        int held = pooled;
        helper.runAfterDelay(120, () -> {
            // Water running down the flanks: flowing water beside the stump, below the pool.
            int running = 0;
            BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
            for (int dx = -SCAN; dx <= SCAN; dx++) {
                for (int dz = -SCAN; dz <= SCAN; dz++) {
                    for (int y = ground; y < water; y++) {
                        var fluid = level.getFluidState(at.set(cx + dx, y, cz + dz));
                        if (!fluid.isEmpty() && !fluid.isSource()) {
                            running++;
                        }
                    }
                }
            }
            if (running < 6) {
                fail(helper, centre, "Only " + running + " blocks of water run down from the spring");
                return;
            }
            clear(level, centre);
            AlteredAether.LOGGER.info("Spring stump: {} blocks of still water in the pool, {} running", held, running);
            helper.succeed();
        });
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

    private SpringStumpTests() {
    }
}
