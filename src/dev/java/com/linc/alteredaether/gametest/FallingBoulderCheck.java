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
import com.linc.alteredaether.compat.boulder.BoulderProtection;
import com.linc.alteredaether.config.AACommonConfig;
import com.mojang.authlib.GameProfile;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import java.util.UUID;
import com.linc.alteredaether.compat.boulder.FallingBoulders;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import net.minecraft.resources.ResourceLocation;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A boulder dropped onto a stone platform, built in the sky near the world's origin (Sable struggles with the GameTest
 * arena's coordinates, millions of blocks out). It must come to rest and turn back into blocks: as many blocks as it was
 * made of, counting any that were dropped as items. One that lands fast buries itself, so its blocks are counted
 * through the platform and below it too, as whatever isn't the platform's stone. Its fall is logged under "Boulder
 * check".
 */
final class FallingBoulderCheck {
    private static final int FLOOR = 200;
    /** How far below the platform a boulder that buries itself can reach. */
    private static final int SUNK = 8;
    private static final int X = 256;

    static void settles(GameTestHelper helper) {
        settles(helper, AetherBlocks.ZANITE_ORE.get().defaultBlockState(), 0.25f);
    }

    static void settles(GameTestHelper helper, net.minecraft.world.level.block.state.BlockState ore, float oreChance) {
        ServerLevel level = helper.getLevel();
        FallingBoulders.forgetAll(level);
        for (int cx = (X - 24) >> 4; cx <= (X + 24) >> 4; cx++) {
            for (int cz = -2; cz <= 1; cz++) {
                level.setChunkForced(cx, cz, true);
            }
        }
        clear(level, X - 24, X + 24, FLOOR - SUNK, FLOOR + 45, -24, 24);
        for (int x = X - 24; x <= X + 24; x++) {
            for (int z = -24; z <= 24; z++) {
                level.setBlock(new BlockPos(x, FLOOR, z), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
        BlockPos origin = new BlockPos(X, FLOOR + 30, 0);
        ServerSubLevel sub = FallingBoulders.spawn(level, origin, AetherBlocks.HOLYSTONE.get().defaultBlockState(),
                ore, oreChance, level.getRandom());
        if (sub == null) {
            helper.fail("no boulder");
            return;
        }
        int blocks = FallingBoulders.blocks(level, sub);
        log("spawned with " + blocks + " blocks, a boulder: " + FallingBoulders.isBoulder(sub));
        int[] ticks = {0};
        double[] fastest = {0};
        helper.onEachTick(() -> {
            ticks[0]++;
            if (!sub.isRemoved()) {
                fastest[0] = Math.max(fastest[0], sub.latestLinearVelocity.length());
                if (ticks[0] % 40 == 0) {
                    log("tick " + ticks[0] + ": y " + String.format("%.2f", sub.logicalPose().position().y())
                            + ", speed " + String.format("%.2f", sub.latestLinearVelocity.length()));
                }
                if (ticks[0] > 1400) {
                    clear(level, X - 24, X + 24, FLOOR - SUNK, FLOOR + 45, -24, 24);
                    release(level);
                    helper.fail("the boulder never settled");
                }
                return;
            }
            int solid = 0;
            for (BlockPos pos : BlockPos.betweenClosed(X - 24, FLOOR - SUNK, -24, X + 24, FLOOR + 40, 24)) {
                net.minecraft.world.level.block.state.BlockState there = level.getBlockState(pos);
                if (!there.isAir() && !there.is(Blocks.STONE)) {
                    solid++;
                }
            }
            int dropped = level.getEntitiesOfClass(ItemEntity.class,
                    new AABB(X - 24, FLOOR - SUNK, -24, X + 24, FLOOR + 40, 24)).size();
            log("settled after " + ticks[0] + " ticks, fastest " + String.format("%.1f", fastest[0]) + " blocks/s: "
                    + solid + " blocks in the world, " + dropped + " dropped, of " + blocks);
            clear(level, X - 24, X + 24, FLOOR - SUNK, FLOOR + 45, -24, 24);
            release(level);
            if (solid + dropped != blocks) {
                helper.fail("the boulder had " + blocks + " blocks and left " + solid + " (" + dropped + " dropped)");
            } else if (FallingBoulders.isBoulder(sub)) {
                helper.fail("a settled boulder is still tracked");
            } else {
                helper.succeed();
            }
        });
    }

    /**
     * A boulder dropped onto a layer of cold aercloud punches straight through it: it is going at least as fast below
     * the layer as it was just above it, the clouds in its path are gone, and it comes to rest on the floor beneath.
     */
    static void throughAerclouds(GameTestHelper helper, boolean slow) {
        ServerLevel level = helper.getLevel();
        FallingBoulders.forgetAll(level);
        for (int cx = (X - 24) >> 4; cx <= (X + 24) >> 4; cx++) {
            for (int cz = -2; cz <= 1; cz++) {
                level.setChunkForced(cx, cz, true);
            }
        }
        clear(level, X - 24, X + 24, FLOOR - SUNK, FLOOR + 115, -24, 24);
        for (int x = X - 24; x <= X + 24; x++) {
            for (int z = -24; z <= 24; z++) {
                level.setBlock(new BlockPos(x, FLOOR, z), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
        FallingBoulders.takeAercloudPasses();
        ServerSubLevel sub = FallingBoulders.spawn(level, new BlockPos(X, slow ? FLOOR + 32 : FLOOR + 105, 0),
                AetherBlocks.HOLYSTONE.get().defaultBlockState(), AetherBlocks.ZANITE_ORE.get().defaultBlockState(),
                0.0f, level.getRandom());
        if (sub == null) {
            helper.fail("no boulder");
            return;
        }
        // Fast: a 3-thick layer far below the drop, met at speed. Slow: a 6-thick layer a block under the boulder,
        // met at a crawl, under the speed at which a boulder ploughs through what's in its way.
        double radius = Math.cbrt(3.0 * FallingBoulders.blocks(level, sub) / (4 * Math.PI));
        int cloudTop = slow ? (int) Math.floor(sub.logicalPose().position().y() - radius) - 1 : FLOOR + 14;
        int cloudBottom = slow ? cloudTop - 5 : FLOOR + 12;
        int clouds = 0;
        for (int x = X - 24; x <= X + 24; x++) {
            for (int z = -24; z <= 24; z++) {
                for (int y = cloudBottom; y <= cloudTop; y++) {
                    level.setBlock(new BlockPos(x, y, z), AetherBlocks.COLD_AERCLOUD.get().defaultBlockState(),
                            Block.UPDATE_ALL);
                    clouds++;
                }
            }
        }
        int laid = clouds;
        int[] ticks = {0};
        double[] above = {0};
        double[] below = {-1};
        helper.onEachTick(() -> {
            ticks[0]++;
            if (!sub.isRemoved()) {
                double y = sub.logicalPose().position().y();
                double speed = sub.latestLinearVelocity.length();
                if (y > cloudTop + (slow ? 0 : 5)) {
                    above[0] = speed;
                } else if (y < cloudBottom - 4 && below[0] < 0) {
                    below[0] = speed;
                }
                if (ticks[0] > 1400) {
                    clear(level, X - 24, X + 24, FLOOR - SUNK, FLOOR + 45, -24, 24);
                    release(level);
                    helper.fail("the boulder never settled");
                }
                return;
            }
            int left = 0;
            for (BlockPos pos : BlockPos.betweenClosed(X - 24, cloudBottom, -24, X + 24, cloudTop, 24)) {
                if (level.getBlockState(pos).is(AetherBlocks.COLD_AERCLOUD.get())) {
                    left++;
                }
            }
            int resting = 0;
            for (BlockPos pos : BlockPos.betweenClosed(X - 24, cloudTop + 1, -24, X + 24, FLOOR + 40, 24)) {
                if (!level.getBlockState(pos).isAir()) {
                    resting++;
                }
            }
            int passed = FallingBoulders.takeAercloudPasses();
            log("aerclouds (" + (slow ? "slow" : "fast") + "): " + String.format("%.1f", above[0]) + " blocks/s above the layer, "
                    + String.format("%.1f", below[0]) + " below; " + (laid - left) + " of " + laid
                    + " clouds broken; " + resting + " blocks left above the layer; "
                    + passed + " contacts passed");
            clear(level, X - 24, X + 24, FLOOR - SUNK, FLOOR + 45, -24, 24);
            release(level);
            if (below[0] < 0 || resting > 0) {
                helper.fail("the boulder stopped on the clouds");
            } else if (!slow && below[0] < above[0] * 0.9 || slow && below[0] < 8) {
                helper.fail("the clouds slowed the boulder from " + above[0] + " to " + below[0]);
            } else if (left == laid) {
                helper.fail("the boulder broke no clouds");
            } else if (passed == 0) {
                helper.fail("Sable never let the boulder through a cloud (is SableAercloudPassMixin applied?)");
            } else {
                helper.succeed();
            }
        });
    }

    /** Empties a box, so a check starts from nothing and a world kept between runs carries none of an earlier one. */
    private static void clear(ServerLevel level, int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
        for (BlockPos pos : BlockPos.betweenClosed(minX, minY, minZ, maxX, maxY, maxZ)) {
            if (!level.getBlockState(pos).isAir()) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
        level.getEntitiesOfClass(ItemEntity.class, new AABB(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1))
                .forEach(net.minecraft.world.entity.Entity::discard);
    }

    /** The end sea sits at this height in the check; the ground is far below it. */
    private static final double SEA = 170;

    static void endSea(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FallingBoulders.forgetAll(level);
        registerSea(level);
        for (int cx = (X - 32) >> 4; cx <= (X + 48) >> 4; cx++) {
            for (int cz = -2; cz <= 1; cz++) {
                level.setChunkForced(cx, cz, true);
            }
        }
        clear(level, X - 24, X + 24, GROUND, FLOOR + 45, -24, 24);
        clear(level, X + 28, X + 40, 140, FLOOR + 45, -4, 8);
        // one block thick, so a boulder that fell too fast to be stopped would pass straight through it
        for (int x = X - 24; x <= X + 24; x++) {
            for (int z = -24; z <= 24; z++) {
                level.setBlock(new BlockPos(x, GROUND, z), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
        BlockPos shipBase = new BlockPos(X + 32, FLOOR + 30, 0);
        List<BlockPos> blocks = new ArrayList<>();
        for (int dx = 0; dx < 3; dx++) {
            for (int dz = 0; dz < 3; dz++) {
                BlockPos pos = shipBase.offset(dx, 0, dz);
                level.setBlock(pos, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
                blocks.add(pos);
            }
        }
        ServerSubLevel ship = SubLevelAssemblyHelper.assembleBlocks(level, shipBase, blocks,
                new BoundingBox3i(shipBase.offset(-1, -1, -1), shipBase.offset(3, 1, 3)));
        ServerSubLevel boulder = FallingBoulders.spawn(level, new BlockPos(X, FLOOR + 30, 0),
                AetherBlocks.HOLYSTONE.get().defaultBlockState(), AetherBlocks.ZANITE_ORE.get().defaultBlockState(),
                0.25f, level.getRandom());
        if (ship == null || boulder == null) {
            unregisterSea(level);
            helper.fail("could not build the ship and the boulder");
            return;
        }
        int[] ticks = {0};
        double[] lowest = {Double.MAX_VALUE};
        helper.onEachTick(() -> {
            ticks[0]++;
            if (!boulder.isRemoved()) {
                lowest[0] = Math.min(lowest[0], boulder.logicalPose().position().y());
            }
            if (ticks[0] < 600) {
                return;
            }
            double shipY = ship.logicalPose().position().y();
            log("peak physics speed " + String.format("%.1f", FallingBoulders.takePeakSpeed()) + " blocks/s");
            log("end sea at " + SEA + ": after 600 ticks the ship is at " + String.format("%.1f", shipY) + ", the boulder went down to "
                    + String.format("%.1f", lowest[0]) + " and is " + (boulder.isRemoved() ? "gone" : "still there"));
            dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level).removeSubLevel(ship,
                    dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason.REMOVED);
            unregisterSea(level);
            clear(level, X - 24, X + 24, GROUND, FLOOR + 45, -24, 24);
            for (int cx = (X - 32) >> 4; cx <= (X + 48) >> 4; cx++) {
                for (int cz = -2; cz <= 1; cz++) {
                    level.setChunkForced(cx, cz, false);
                }
            }
            if (shipY < SEA - 40) {
                helper.fail("the end sea should hold an ordinary ship up (at " + shipY + ")");
            } else if (lowest[0] > SEA - 20) {
                helper.fail("the boulder should fall through the end sea (lowest " + lowest[0] + ")");
            } else if (!boulder.isRemoved()) {
                helper.fail("the boulder should have come to rest on the ground");
            } else {
                helper.succeed();
            }
        });
    }

    /** The ground under the end sea in its check. */
    private static final int GROUND = 100;
    private static final ResourceLocation SEA_KEY_NOTE = ResourceLocation.withDefaultNamespace("overworld");

    /** Registers an end sea for the overworld with Simulated's own data holder, by reflection (no compile dependency). */
    private static void registerSea(ServerLevel level) {
        try {
            Class<?> sea = Class.forName("dev.simulated_team.simulated.content.end_sea.EndSeaPhysics");
            Object value = sea.getConstructors()[0].newInstance(SEA_KEY_NOTE, Optional.of(100), SEA, 1.0, 1.0);
            Class.forName("dev.simulated_team.simulated.content.end_sea.EndSeaPhysicsData")
                    .getMethod("addKeyWithPriority", net.minecraft.resources.ResourceKey.class, sea)
                    .invoke(null, level.dimension(), value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Simulated's end sea changed", e);
        }
    }

    @SuppressWarnings("unchecked")
    private static void unregisterSea(ServerLevel level) {
        try {
            var field = Class.forName("dev.simulated_team.simulated.content.end_sea.EndSeaPhysicsData")
                    .getDeclaredField("END_SEA_PHYSICS_DATA");
            field.setAccessible(true);
            ((java.util.Map<Object, Object>) field.get(null)).remove(level.dimension());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Simulated's end sea changed", e);
        }
    }

    private static void forceArea(ServerLevel level, boolean on) {
        for (int cx = (X - 96) >> 4; cx <= (X + 96) >> 4; cx++) {
            for (int cz = -6; cz <= 5; cz++) {
                level.setChunkForced(cx, cz, on);
            }
        }
    }

    /** A boulder that comes to rest on land nobody may build on leaves no blocks and no items. */
    static void protectedLanding(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FallingBoulders.forgetAll(level);
        release(level, true);
        clear(level, X - 24, X + 24, FLOOR, FLOOR + 45, -24, 24);
        for (int x = X - 24; x <= X + 24; x++) {
            for (int z = -24; z <= 24; z++) {
                level.setBlock(new BlockPos(x, FLOOR, z), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
        BoulderProtection.Check deny = (l, pos) -> pos.getY() < FLOOR || Math.abs(pos.getX() - X) > 40
                || Math.abs(pos.getZ()) > 40;
        BoulderProtection.add(deny);
        ServerSubLevel sub = FallingBoulders.spawn(level, new BlockPos(X, FLOOR + 30, 0),
                AetherBlocks.HOLYSTONE.get().defaultBlockState(), AetherBlocks.ZANITE_ORE.get().defaultBlockState(),
                0.25f, level.getRandom());
        if (sub == null) {
            BoulderProtection.remove(deny);
            helper.fail("no boulder");
            return;
        }
        int[] ticks = {0};
        helper.onEachTick(() -> {
            ticks[0]++;
            if (!sub.isRemoved() && ticks[0] < 1400) {
                return;
            }
            BoulderProtection.remove(deny);
            int solid = 0;
            for (BlockPos pos : BlockPos.betweenClosed(X - 24, FLOOR + 1, -24, X + 24, FLOOR + 40, 24)) {
                if (!level.getBlockState(pos).isAir()) {
                    solid++;
                }
            }
            int dropped = level.getEntitiesOfClass(ItemEntity.class,
                    new AABB(X - 24, FLOOR, -24, X + 24, FLOOR + 40, 24)).size();
            log("on protected land: settled after " + ticks[0] + " ticks, " + solid + " blocks and " + dropped + " items left");
            clear(level, X - 24, X + 24, FLOOR, FLOOR + 45, -24, 24);
            release(level, false);
            if (!sub.isRemoved()) {
                helper.fail("the boulder never came to rest");
            } else if (solid != 0 || dropped != 0) {
                helper.fail("a boulder left " + solid + " blocks and " + dropped + " items on protected land");
            } else {
                helper.succeed();
            }
        });
    }

    /** Breaking a block of a falling boulder lets it go: it is no longer tracked. */
    static void tampered(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FallingBoulders.forgetAll(level);
        release(level, true);
        clear(level, X - 24, X + 24, FLOOR, FLOOR + 45, -24, 24);
        ServerSubLevel sub = FallingBoulders.spawn(level, new BlockPos(X, FLOOR + 30, 0),
                AetherBlocks.HOLYSTONE.get().defaultBlockState(), AetherBlocks.ZANITE_ORE.get().defaultBlockState(),
                0.25f, level.getRandom());
        if (sub == null) {
            helper.fail("no boulder");
            return;
        }
        int[] ticks = {0};
        helper.onEachTick(() -> {
            ticks[0]++;
            if (ticks[0] == 5) {
                BlockPos centre = sub.getPlot().getCenterBlock();
                for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-8, -8, -8), centre.offset(8, 8, 8))) {
                    if (!level.getBlockState(pos).isAir()) {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                        log("broke a block of the boulder at " + pos.toShortString());
                        return;
                    }
                }
                helper.fail("found nothing to break");
            } else if (ticks[0] == 60) {
                boolean tracked = FallingBoulders.isBoulder(sub);
                int left = FallingBoulders.tracked(level);
                log("after the block was broken: still a boulder " + tracked + ", " + left + " tracked");
                dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level).removeSubLevel(sub,
                        dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason.REMOVED);
                release(level, false);
                if (tracked || left != 0) {
                    helper.fail("a boulder with a block broken should be let go (tracked " + tracked + ", " + left + ")");
                } else {
                    helper.succeed();
                }
            }
        });
    }

    /** The timer's own attempt, run for a player standing over the void: a spot away from them, a boulder, a fall. */
    static void natural(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FallingBoulders.forgetAll(level);
        forceArea(level, true);
        FallingBoulders.anywhere(true);
        FakePlayer player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "boulder-watcher"));
        player.moveTo(X, 100, 0);
        int before = FallingBoulders.tracked(level);
        int[] ticks = {0};
        int[] phase = {0};
        Runnable finish = () -> {
            FallingBoulders.anywhere(false);
            clear(level, X - 100, X + 100, -60, -20, -100, 100);
            forceArea(level, false);
        };
        helper.onEachTick(() -> {
            ticks[0]++;
            if (phase[0] == 0 && ticks[0] == 60) {
                if (!FallingBoulders.announceNear(level, player)) {
                    finish.run();
                    helper.fail("no spot was found for a boulder");
                    return;
                }
                log("a boulder was announced");
                phase[0] = 1;
                ticks[0] = 0;
            } else if (phase[0] == 1 && FallingBoulders.tracked(level) > before) {
                for (ServerSubLevel sub : dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level).getAllSubLevels()) {
                    if (FallingBoulders.isBoulder(sub)) {
                        double dx = sub.logicalPose().position().x() - X, dz = sub.logicalPose().position().z();
                        double away = Math.sqrt(dx * dx + dz * dz);
                        log("the boulder appeared " + ticks[0] + " ticks after the warning, " + String.format("%.1f", away)
                                + " blocks from the player, at height " + String.format("%.0f", sub.logicalPose().position().y()));
                        if (away < 20 || away > 70) {
                            finish.run();
                            helper.fail("the boulder should be 24-64 blocks from the player, not " + away);
                            return;
                        }
                    }
                }
                phase[0] = 2;
                ticks[0] = 0;
            } else if (phase[0] == 1 && ticks[0] > 200) {
                finish.run();
                helper.fail("no boulder was made after the warning");
            } else if (phase[0] == 2 && FallingBoulders.tracked(level) <= before) {
                log("the boulder was gone after " + ticks[0] + " ticks");
                finish.run();
                helper.succeed();
            } else if (phase[0] == 2 && ticks[0] > 1000) {
                finish.run();
                helper.fail("the boulder never landed or left the world");
            }
        });
    }

    private static void release(ServerLevel level, boolean on) {
        for (int cx = (X - 24) >> 4; cx <= (X + 24) >> 4; cx++) {
            for (int cz = -2; cz <= 1; cz++) {
                level.setChunkForced(cx, cz, on);
            }
        }
    }

    private static void release(ServerLevel level) {
        for (int cx = (X - 24) >> 4; cx <= (X + 24) >> 4; cx++) {
            for (int cz = -2; cz <= 1; cz++) {
                level.setChunkForced(cx, cz, false);
            }
        }
    }

    private static void log(String message) {
        AlteredAether.LOGGER.info("Boulder check: {}", message);
    }

    private FallingBoulderCheck() {
    }
}
