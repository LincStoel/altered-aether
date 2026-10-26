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
import com.linc.alteredaether.block.GleamVines;
import com.linc.alteredaether.registry.AARegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.MoveToBlockGoal;
import net.minecraft.world.entity.animal.Fox;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.CaveVines;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Gleam vines must behave as vanilla's cave vines do: grow from a ceiling into their stem block, give up a gleamberry
 * (not a glow berry) to a player or a fox, glow only with berries, burn, and sit in the same tags. Unlike them, they
 * hang from leaves.
 */
@GameTestHolder(AlteredAether.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GleamVinesTests {
    @GameTest(template = "arena")
    public static void gleamVinesActLikeCaveVines(GameTestHelper helper) throws ReflectiveOperationException {
        ServerLevel level = helper.getLevel();
        BlockPos ceiling = helper.absolutePos(new BlockPos(24, 12, 24));
        BlockPos head = ceiling.below();
        level.setBlockAndUpdate(ceiling, Blocks.STONE.defaultBlockState());
        BlockState berries = AARegistries.GLEAM_VINES.get().defaultBlockState().setValue(CaveVines.BERRIES, true);
        level.setBlockAndUpdate(head, berries);

        if (berries.getLightEmission() != 14 || berries.setValue(CaveVines.BERRIES, false).getLightEmission() != 0) {
            helper.fail("Gleam vines should glow at 14 with berries and not at all without");
            return;
        }

        // Bone meal brings berries out; random ticks grow the head down, turning the old head into stem.
        BlockState bare = berries.setValue(CaveVines.BERRIES, false);
        level.setBlockAndUpdate(head, bare);
        ((BonemealableBlock) bare.getBlock()).performBonemeal(level, level.random, head, bare);
        if (!level.getBlockState(head).getValue(CaveVines.BERRIES)) {
            helper.fail("Bone meal should bring out gleamberries");
            return;
        }
        for (int i = 0; i < 500 && level.getBlockState(head).is(AARegistries.GLEAM_VINES.get()); i++) {
            level.getBlockState(head).randomTick(level, head, level.random);
        }
        if (!level.getBlockState(head).is(AARegistries.GLEAM_VINES_PLANT.get())
                || !level.getBlockState(head.below()).is(AARegistries.GLEAM_VINES.get())) {
            helper.fail("Growing should leave gleam vine stem above a gleam vine tip, found "
                    + level.getBlockState(head) + " over " + level.getBlockState(head.below()));
            return;
        }

        // Picked by hand...
        level.setBlockAndUpdate(head, AARegistries.GLEAM_VINES_PLANT.get().defaultBlockState().setValue(CaveVines.BERRIES, true));
        GleamVines.pick(null, level.getBlockState(head), level, head);
        if (level.getBlockState(head).getValue(CaveVines.BERRIES) || countBerries(level, head) != 1) {
            helper.fail("Picking gleam vines should drop one gleamberry and leave them bare");
            return;
        }
        level.getEntitiesOfClass(ItemEntity.class, new AABB(head).inflate(3)).forEach(ItemEntity::discard);

        // ...and by a fox, through vanilla's goal (see GleamVinesMixins).
        BlockPos tip = head.below();
        level.setBlockAndUpdate(tip, AARegistries.GLEAM_VINES.get().defaultBlockState().setValue(CaveVines.BERRIES, true));
        Fox fox = helper.spawn(EntityType.FOX, helper.relativePos(tip.below(2)));
        Class<?> goalClass = Class.forName("net.minecraft.world.entity.animal.Fox$FoxEatBerriesGoal");
        Constructor<?> constructor = goalClass.getDeclaredConstructor(Fox.class, double.class, int.class, int.class);
        constructor.setAccessible(true);
        Object goal = constructor.newInstance(fox, 1.2, 12, 1);
        Field blockPos = MoveToBlockGoal.class.getDeclaredField("blockPos");
        blockPos.setAccessible(true);
        blockPos.set(goal, tip);
        Method pick = goalClass.getDeclaredMethod("pickGlowBerry", BlockState.class);
        pick.setAccessible(true);
        pick.invoke(goal, level.getBlockState(tip));
        boolean foxGotOne = countBerries(level, tip) == 1 || fox.getMainHandItem().is(AARegistries.GLEAMBERRIES.get());
        fox.discard();
        if (level.getBlockState(tip).getValue(CaveVines.BERRIES) || !foxGotOne) {
            helper.fail("A fox picking gleam vines should get a gleamberry");
            return;
        }

        // Loading the bees' goal applies its mixin, which would fail loudly if its target had moved.
        Class.forName("net.minecraft.world.entity.animal.Bee$BeeGrowCropGoal");

        BlockState stem = AARegistries.GLEAM_VINES_PLANT.get().defaultBlockState();
        if (stem.getFlammability(level, head, Direction.UP) != Blocks.CAVE_VINES_PLANT.defaultBlockState()
                .getFlammability(level, head, Direction.UP)) {
            helper.fail("Gleam vines should burn like cave vines");
            return;
        }
        if (!stem.is(BlockTags.CLIMBABLE) || !stem.is(BlockTags.CAVE_VINES) || !stem.is(BlockTags.BEE_GROWABLES)
                || !stem.is(BlockTags.MINEABLE_WITH_AXE) || !stem.is(BlockTags.SWORD_EFFICIENT)
                || !stem.is(BlockTags.MOSS_REPLACEABLE)
                || !AARegistries.GLEAMBERRIES.get().getDefaultInstance().is(ItemTags.FOX_FOOD)) {
            helper.fail("Gleam vines and gleamberries should share cave vines' and glow berries' tags");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "arena")
    public static void gleamVinesHangFromLeaves(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos canopy = helper.absolutePos(new BlockPos(20, 12, 20));
        BlockPos below = canopy.below();
        level.setBlockAndUpdate(canopy, Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true));
        if (!AARegistries.GLEAM_VINES.get().defaultBlockState().canSurvive(level, below)
                || !AARegistries.GLEAM_VINES_PLANT.get().defaultBlockState().canSurvive(level, below)) {
            helper.fail("Gleam vines should hang from leaves");
            return;
        }
        if (Blocks.CAVE_VINES.defaultBlockState().canSurvive(level, below)) {
            helper.fail("Cave vines should still not hang from leaves");
            return;
        }
        helper.succeed();
    }

    private static int countBerries(ServerLevel level, BlockPos pos) {
        return level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(3),
                item -> item.getItem().is(AARegistries.GLEAMBERRIES.get())).stream()
                .mapToInt(item -> item.getItem().getCount()).sum();
    }

    private GleamVinesTests() {
    }
}
