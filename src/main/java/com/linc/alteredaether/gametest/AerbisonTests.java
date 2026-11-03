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
import com.linc.alteredaether.config.AACommonConfig;
import com.linc.alteredaether.entity.Aerbison;
import com.linc.alteredaether.entity.aerbison.GustStompGoal;
import com.linc.alteredaether.entity.aerbison.Stampede;
import com.linc.alteredaether.entity.aerbison.StampedePlanner;
import com.linc.alteredaether.registry.AARegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * Aerbison: the stampede's run, hits and trampling, the gust stomp up a ledge, the sail down, and quicksoil. The arena
 * is 48 blocks across, so a stampede here runs a short course; the config's run length is cut down for them.
 */
@GameTestHolder(AlteredAether.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AerbisonTests {
    private static final double START_X = 6, START_Z = 24;

    /** A leader and two followers run the short course, rise, glide down and land unhurt, and the stampede ends. */
    @GameTest(template = "arena", timeoutTicks = 400)
    public static void stampedeRunsAndLands(GameTestHelper helper) {
        shortCourse(10, 2);
        int floor = floor(helper, START_X, START_Z);
        Aerbison leader = bison(helper, START_X, floor, START_Z);
        Aerbison left = bison(helper, START_X - 3, floor, START_Z - 2.5);
        Aerbison right = bison(helper, START_X - 3, floor, START_Z + 2.5);
        double[] topY = {0};
        float health = leader.getHealth();
        Stampede.start(helper.getLevel(), leader, List.of(left, right), new StampedePlanner.Plan(1, 0, 10));
        helper.onEachTick(() -> topY[0] = Math.max(topY[0], leader.getY()));
        helper.succeedWhen(() -> {
            for (Aerbison b : List.of(leader, left, right)) {
                helper.assertTrue(b.isAlive() && b.stampede == null && b.onGround() && !b.isNoGravity()
                        && b.getState() == Aerbison.State.IDLE, "a bison hasn't landed: " + b.getState());
                helper.assertTrue(b.getHealth() == b.getMaxHealth(), "a bison was hurt");
            }
            helper.assertTrue(leader.getX() - helper.absoluteVec(new Vec3(START_X, 0, 0)).x >= 9, "the leader ran only "
                    + (leader.getX() - helper.absoluteVec(new Vec3(START_X, 0, 0)).x));
            helper.assertTrue(topY[0] > helper.absoluteVec(new Vec3(0, floor, 0)).y + 0.5, "it never rose");
            helper.assertTrue(health == leader.getHealth(), "the leader was hurt");
            restoreCourse();
        });
    }

    /** Walled in on every side, the planner finds no heading; with a gap it does. */
    @GameTest(template = "arena")
    public static void noRoomNoStampede(GameTestHelper helper) {
        int floor = floor(helper, 24, 24);
        for (int dx = -9; dx <= 9; dx++) {
            for (int dz = -9; dz <= 9; dz++) {
                double ring = Math.hypot(dx, dz);
                if (ring >= 7 && ring <= 8.5) {
                    for (int y = 0; y < 12; y++) {
                        helper.getLevel().setBlock(helper.absolutePos(new BlockPos(24 + dx, floor + y, 24 + dz)),
                                Blocks.STONE.defaultBlockState(), 3);
                    }
                }
            }
        }
        Aerbison leader = bison(helper, 24, floor, 24);
        // within one tick, so the other tests' settings can't come between
        shortCourse(10, 2);
        helper.assertTrue(StampedePlanner.plan(helper.getLevel(), leader, 3, null) == null,
                "a herd walled in should have no plan");
        // half the ring comes down: every heading into that side is clear
        for (int dx = 3; dx <= 9; dx++) {
            for (int dz = -9; dz <= 9; dz++) {
                for (int y = 0; y < 12; y++) {
                    helper.getLevel().setBlock(helper.absolutePos(new BlockPos(24 + dx, floor + y, 24 + dz)),
                            Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
        boolean planned = StampedePlanner.plan(helper.getLevel(), leader, 3, null) != null;
        restoreCourse();
        helper.assertTrue(planned, "a half-open ring should give a plan out of it");
        helper.succeed();
    }

    /** What is in the way is thrown up and given slow falling, once. */
    @GameTest(template = "arena", timeoutTicks = 300)
    public static void stampedeThrowsWhatItHits(GameTestHelper helper) {
        shortCourse(14, 2);
        int floor = floor(helper, START_X, START_Z);
        Aerbison leader = bison(helper, START_X, floor, START_Z);
        Pig pig = helper.spawn(EntityType.PIG, new Vec3(16, floor, START_Z));
        pig.setPersistenceRequired();
        pig.goalSelector.removeAllGoals(goal -> true);
        double base = pig.getY();
        double[] top = {base};
        boolean[] slow = {false};
        Stampede.start(helper.getLevel(), leader, List.of(), new StampedePlanner.Plan(1, 0, 14));
        helper.onEachTick(() -> {
            top[0] = Math.max(top[0], pig.getY());
            slow[0] |= pig.hasEffect(MobEffects.SLOW_FALLING);
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(top[0] - base > 1.5, "the pig only rose " + (top[0] - base));
            helper.assertTrue(slow[0], "the pig should have been given slow falling");
            helper.assertTrue(pig.getHealth() < pig.getMaxHealth(), "the pig should have been hurt");
            restoreCourse();
        });
    }

    /** Flowers and glass are trampled, stone is not; with mobGriefing off nothing breaks. */
    @GameTest(template = "arena", timeoutTicks = 300)
    public static void stampedeTramplesFragileBlocks(GameTestHelper helper) {
        tramplingTest(helper, true);
    }

    @GameTest(template = "arena", timeoutTicks = 300, batch = "griefing_off")
    public static void stampedeRespectsMobGriefing(GameTestHelper helper) {
        tramplingTest(helper, false);
    }

    private static void tramplingTest(GameTestHelper helper, boolean griefing) {
        shortCourse(14, 2);
        int floor = floor(helper, START_X, START_Z);
        boolean previous = helper.getLevel().getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
        helper.getLevel().getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(griefing, helper.getLevel().getServer());
        BlockPos flower = helper.absolutePos(new BlockPos(12, floor, 24));
        BlockPos glass = helper.absolutePos(new BlockPos(14, floor, 24));
        BlockPos glassUp = glass.above();
        BlockPos stone = helper.absolutePos(new BlockPos(12, floor, 19));
        helper.getLevel().setBlock(flower, Blocks.POPPY.defaultBlockState(), 3);
        helper.getLevel().setBlock(glass, Blocks.GLASS.defaultBlockState(), 3);
        helper.getLevel().setBlock(glassUp, Blocks.GLASS.defaultBlockState(), 3);
        helper.getLevel().setBlock(stone, Blocks.STONE.defaultBlockState(), 3);
        Aerbison leader = bison(helper, START_X, floor, START_Z);
        Stampede.start(helper.getLevel(), leader, List.of(), new StampedePlanner.Plan(1, 0, 14));
        helper.succeedWhen(() -> {
            helper.assertTrue(leader.stampede == null, "still running");
            boolean broken = helper.getLevel().getBlockState(flower).isAir() && helper.getLevel().getBlockState(glass).isAir()
                    && helper.getLevel().getBlockState(glassUp).isAir();
            helper.assertTrue(broken == griefing, griefing ? "the flower and glass should be trampled"
                    : "nothing should break with mobGriefing off");
            helper.assertTrue(helper.getLevel().getBlockState(stone).is(Blocks.STONE), "the stone should stand");
            helper.getLevel().getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(previous, helper.getLevel().getServer());
            restoreCourse();
        });
    }

    /** Stopped by a wall, the leader drops out dazed. */
    @GameTest(template = "arena", timeoutTicks = 300)
    public static void wallStaggersALeader(GameTestHelper helper) {
        shortCourse(25, 2);
        int floor = floor(helper, START_X, START_Z);
        for (int z = 16; z <= 32; z++) {
            for (int y = 0; y < 8; y++) {
                helper.getLevel().setBlock(helper.absolutePos(new BlockPos(16, floor + y, z)), Blocks.STONE.defaultBlockState(), 3);
            }
        }
        Aerbison leader = bison(helper, START_X, floor, START_Z);
        Stampede.start(helper.getLevel(), leader, List.of(), new StampedePlanner.Plan(1, 0, 25));
        boolean[] staggered = {false};
        helper.onEachTick(() -> staggered[0] |= leader.isStaggering());
        helper.succeedWhen(() -> {
            helper.assertTrue(staggered[0], "the leader never staggered");
            helper.assertTrue(leader.stampede == null && !leader.isNoGravity(), "it should have left the stampede");
            restoreCourse();
        });
    }

    /** An idle bison the run passes joins it, and runs with it. */
    @GameTest(template = "arena", timeoutTicks = 300)
    public static void idleBisonJoin(GameTestHelper helper) {
        shortCourse(20, 2);
        int floor = floor(helper, START_X, START_Z);
        Aerbison leader = bison(helper, START_X, floor, START_Z);
        Aerbison bystander = bison(helper, 16, floor, START_Z + 4);
        boolean[] joined = {false};
        Stampede.start(helper.getLevel(), leader, List.of(), new StampedePlanner.Plan(1, 0, 20));
        helper.onEachTick(() -> joined[0] |= bystander.stampede != null);
        helper.succeedWhen(() -> {
            helper.assertTrue(joined[0], "the bystander never joined");
            helper.assertTrue(bystander.stampede == null && bystander.onGround(), "the bystander hasn't landed");
            restoreCourse();
        });
    }

    /** Dropped from a height, a bison sails down at a drift and lands unhurt. */
    @GameTest(template = "arena", timeoutTicks = 600)
    public static void maneSailSavesAFall(GameTestHelper helper) {
        Aerbison bison = bison(helper, 24, 30, 24);
        double[] slowest = {0};
        helper.onEachTick(() -> slowest[0] = Math.min(slowest[0], bison.getDeltaMovement().y));
        helper.succeedWhen(() -> {
            helper.assertTrue(bison.onGround() && bison.isAlive(), "it hasn't landed");
            helper.assertTrue(bison.getHealth() == bison.getMaxHealth(), "the fall hurt it");
            helper.assertTrue(slowest[0] > -0.6, "it fell at " + slowest[0] + " a tick");
        });
    }

    /** Two blocks up and more is out of a jump's reach; the stomp throws the bison up a three-block ledge. */
    @GameTest(template = "arena", timeoutTicks = 600)
    public static void gustStompClimbsALedge(GameTestHelper helper) {
        int floor = floor(helper, 12, 24);
        for (int x = 20; x <= 40; x++) {
            for (int z = 12; z <= 36; z++) {
                for (int y = 0; y < 3; y++) {
                    helper.getLevel().setBlock(helper.absolutePos(new BlockPos(x, floor + y, z)), Blocks.STONE.defaultBlockState(), 3);
                }
            }
        }
        Aerbison bison = bison(helper, 14, floor, 24);
        bison.goalSelector.removeAllGoals(goal -> true);
        bison.goalSelector.addGoal(2, new GustStompGoal(bison));
        BlockPos target = helper.absolutePos(new BlockPos(28, floor + 3, 24));
        bison.getNavigation().moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, 1.0);
        helper.onEachTick(() -> {
            if (bison.getNavigation().isDone() && bison.onGround() && bison.getX() < target.getX() - 1.5) {
                bison.getNavigation().moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, 1.0);
            }
        });
        helper.succeedWhen(() -> helper.assertTrue(bison.onGround() && bison.getY() >= target.getY() - 0.1
                        && bison.getX() >= helper.absolutePos(new BlockPos(20, 0, 0)).getX() + 1,
                "the bison is at " + bison.position() + ", not on the ledge"));
    }

    /** On quicksoil an aerbison keeps plain ground's friction; a pig gets quicksoil's. */
    @GameTest(template = "arena")
    public static void quicksoilIsNotSlippery(GameTestHelper helper) {
        int floor = floor(helper, 24, 24);
        BlockPos pos = helper.absolutePos(new BlockPos(24, floor - 1, 24));
        helper.getLevel().setBlock(pos, AetherBlocks.QUICKSOIL.get().defaultBlockState(), 3);
        Aerbison bison = bison(helper, 24, floor, 24);
        Pig pig = helper.spawn(EntityType.PIG, new Vec3(26, floor, 24));
        pig.setPersistenceRequired();
        float bisonFriction = helper.getLevel().getBlockState(pos).getFriction(helper.getLevel(), pos, bison);
        float pigFriction = helper.getLevel().getBlockState(pos).getFriction(helper.getLevel(), pos, pig);
        helper.assertTrue(bisonFriction < 0.7F, "the bison's friction on quicksoil is " + bisonFriction);
        helper.assertTrue(pigFriction > 1.0F, "a pig's friction on quicksoil is " + pigFriction);
        helper.succeed();
    }

    private static void shortCourse(int run, int climb) {
        AACommonConfig.AERBISON_RUN_MIN.set(run);
        AACommonConfig.AERBISON_RUN_MAX.set(run);
        AACommonConfig.AERBISON_MAX_CLIMB.set(climb);
    }

    private static void restoreCourse() {
        AACommonConfig.AERBISON_RUN_MIN.set(32);
        AACommonConfig.AERBISON_RUN_MAX.set(48);
        AACommonConfig.AERBISON_MAX_CLIMB.set(8);
    }

    /** The first free block above the arena's floor at a relative column; the arena has a roof, so not the heightmap. */
    private static int floor(GameTestHelper helper, double x, double z) {
        for (int y = 0; y < 40; y++) {
            BlockPos pos = new BlockPos((int) x, y, (int) z);
            if (helper.getBlockState(pos).isAir() && !helper.getBlockState(pos.below()).isAir()) {
                return y;
            }
        }
        throw new IllegalStateException("no floor at " + x + ", " + z);
    }

    private static Aerbison bison(GameTestHelper helper, double x, double y, double z) {
        Aerbison bison = helper.spawn(AARegistries.AERBISON.get(), new Vec3(x, y, z));
        bison.setPersistenceRequired();
        return bison;
    }

    private AerbisonTests() {
    }
}
