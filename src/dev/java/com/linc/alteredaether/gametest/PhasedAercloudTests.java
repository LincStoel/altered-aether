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

import com.aetherteam.aether.block.AetherBlockStateProperties;
import com.aetherteam.aether.block.AetherBlocks;
import com.linc.alteredaether.AlteredAether;
import com.linc.alteredaether.block.PhasedAercloudBlock;
import com.linc.alteredaether.compat.cloud.AercloudPhasing;
import com.linc.alteredaether.registry.AARegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * The phased aercloud on its own, without Sable (see block/PhasedAercloudBlock): it stands in for the cloud it was,
 * fades back in a step at a time, and turns back into that cloud with its double drops kept.
 */
@GameTestHolder(AlteredAether.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PhasedAercloudTests {
    private static BlockState doubledCold() {
        return AetherBlocks.COLD_AERCLOUD.get().defaultBlockState().setValue(AetherBlockStateProperties.DOUBLE_DROPS,
                true);
    }

    /** A phased cloud is the cloud it was to anyone not on a ship: its shape and its drops. */
    @GameTest(template = "arena")
    public static void phasedCloudStandsInForItsCloud(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(16, 3, 16));
        helper.getLevel().setBlock(pos, doubledCold(), Block.UPDATE_ALL);
        Pig pig = helper.spawn(EntityType.PIG, new Vec3(16, 6, 16));
        VoxelShape cold = helper.getLevel().getBlockState(pos).getCollisionShape(helper.getLevel(), pos,
                CollisionContext.of(pig));
        helper.assertTrue(AercloudPhasing.phase(helper.getLevel(), pos), "a cold aercloud didn't phase");
        BlockState phased = helper.getLevel().getBlockState(pos);
        helper.assertTrue(phased.is(AARegistries.PHASED_AERCLOUD.get()), "the cloud isn't phased");
        helper.assertTrue(phased.getValue(PhasedAercloudBlock.VARIANT) == PhasedAercloudBlock.Variant.COLD,
                "a cold aercloud phased as " + phased.getValue(PhasedAercloudBlock.VARIANT));
        helper.assertTrue(phased.getValue(PhasedAercloudBlock.DOUBLE_DROPS), "the phased cloud lost its double drops");
        VoxelShape shape = phased.getCollisionShape(helper.getLevel(), pos, CollisionContext.of(pig));
        helper.assertFalse(shape.isEmpty(), "a pig would fall through a phased cloud");
        helper.assertTrue(shape.toAabbs().equals(cold.toAabbs()), "a phased cloud's shape isn't its cloud's");
        List<ItemStack> drops = Block.getDrops(phased, helper.getLevel(), pos, null);
        helper.assertTrue(!drops.isEmpty() && drops.get(0).is(AetherBlocks.COLD_AERCLOUD.get().asItem()),
                "a phased cloud drops " + drops + ", not its cloud");
        helper.assertFalse(AercloudPhasing.phase(helper.getLevel(), helper.absolutePos(new BlockPos(16, 1, 16))),
                "something that isn't a cloud phased");
        helper.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        helper.succeed();
    }

    /** Touching a phased cloud again starts it from the faintest; one already at the faintest is left as it is. */
    @GameTest(template = "arena")
    public static void phasedCloudRephasesFromTheFaintest(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(16, 3, 16));
        helper.getLevel().setBlock(pos, doubledCold(), Block.UPDATE_ALL);
        AercloudPhasing.phase(helper.getLevel(), pos);
        helper.assertFalse(AercloudPhasing.phase(helper.getLevel(), pos), "a cloud at the faintest changed");
        BlockState phased = helper.getLevel().getBlockState(pos);
        helper.getLevel().setBlock(pos, phased.setValue(PhasedAercloudBlock.FADE, 4), Block.UPDATE_ALL);
        helper.assertTrue(AercloudPhasing.phase(helper.getLevel(), pos), "a fading cloud wasn't phased again");
        helper.assertTrue(helper.getLevel().getBlockState(pos).getValue(PhasedAercloudBlock.FADE) == 0,
                "a cloud phased again isn't at the faintest");
        helper.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        helper.succeed();
    }

    /** A phased cloud steps up one at a time and is its cloud again, double drops and all, after the configured time. */
    @GameTest(template = "arena", timeoutTicks = 1000)
    public static void phasedCloudFadesBack(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(16, 3, 16));
        helper.getLevel().setBlock(pos, doubledCold(), Block.UPDATE_ALL);
        AercloudPhasing.phase(helper.getLevel(), pos);
        int[] ticks = {0};
        int[] fade = {0};
        int step = PhasedAercloudBlock.step();
        int least = step * PhasedAercloudBlock.MAX_FADE + step / 2;
        int most = step * (PhasedAercloudBlock.MAX_FADE + 2) + 5;
        helper.onEachTick(() -> {
            ticks[0]++;
            BlockState state = helper.getLevel().getBlockState(pos);
            if (state.is(AARegistries.PHASED_AERCLOUD.get())) {
                int now = state.getValue(PhasedAercloudBlock.FADE);
                if (now != fade[0] && now != fade[0] + 1) {
                    helper.fail("the cloud jumped from step " + fade[0] + " to " + now);
                }
                fade[0] = now;
                if (ticks[0] > most) {
                    helper.fail("the cloud is still phased after " + ticks[0] + " ticks");
                }
                return;
            }
            helper.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            if (!state.is(AetherBlocks.COLD_AERCLOUD.get())) {
                helper.fail("the cloud came back as " + state);
            } else if (!state.getValue(AetherBlockStateProperties.DOUBLE_DROPS)) {
                helper.fail("the cloud came back without its double drops");
            } else if (fade[0] != PhasedAercloudBlock.MAX_FADE) {
                helper.fail("the cloud came back from step " + fade[0]);
            } else if (ticks[0] < least) {
                helper.fail("the cloud came back after only " + ticks[0] + " ticks");
            } else {
                helper.succeed();
            }
        });
    }

    private PhasedAercloudTests() {
    }
}
