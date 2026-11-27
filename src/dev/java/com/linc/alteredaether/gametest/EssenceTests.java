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
import com.aetherteam.aether.item.AetherItems;
import com.linc.alteredaether.AlteredAether;
import com.linc.alteredaether.electric.AetherJarBlock;
import com.linc.alteredaether.electric.AetherJarBlockEntity;
import com.linc.alteredaether.electric.AetherJarItem;
import com.linc.alteredaether.electric.ElectricRegistries;
import com.linc.alteredaether.essence.CapstoneGem;
import com.linc.alteredaether.essence.EssenceCapstoneBlock;
import com.linc.alteredaether.essence.EssenceCapstoneBlockEntity;
import com.linc.alteredaether.essence.EssenceKind;
import com.linc.alteredaether.essence.EssenceOrbBlockEntity;
import com.linc.alteredaether.essence.EssencePlinthBlockEntity;
import com.linc.alteredaether.essence.EssenceRegistries;
import com.linc.alteredaether.essence.QuintessencePhialItem;
import com.linc.alteredaether.registry.AARegistries;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/**
 * Essence sources: what counts as a capstone of the circle, rod jars charging at the full flow each, the plinth filling
 * one jar at a time and infusing, the phial keeping its source, the orb staying put, hoppers on the plinth, and the
 * circle's healing.
 */
@GameTestHolder(AlteredAether.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EssenceTests {
    private static final BlockPos ORB = new BlockPos(24, 10, 24);

    @GameTest(template = "arena", timeoutTicks = 100)
    public static void onlyGemmedCapstonesInSightCount(GameTestHelper helper) {
        EssenceOrbBlockEntity orb = orb(helper, ORB, EssenceKind.CHANNEL);
        capstone(helper, ORB.north(4), true);                 // N, gemmed
        capstone(helper, ORB.offset(1, 0, -1).offset(1, 0, -1), true); // NE two out, gemmed
        capstone(helper, ORB.east(3), true);                  // E, gemmed
        capstone(helper, ORB.south(6), false);                // S, no gem
        capstone(helper, ORB.west(5), true);                  // W, gemmed but walled off
        helper.setBlock(ORB.west(2), Blocks.STONE);
        capstone(helper, ORB.offset(-5, 0, -5), true);        // NW five out: too far
        capstone(helper, ORB.offset(3, 1, 3), true);          // SE but a block high
        helper.runAfterDelay(45, () -> {
            helper.assertValueEqual(orb.gems(), 0b0000_0111, "the gemmed capstones seen");
            // N, NE and E have no gemmed partner opposite (S is bare, SW empty, W walled off): none of them works
            helper.assertValueEqual(orb.pairedGems(), 0, "the working pairs");
            helper.assertValueEqual(orb.capture(), 0.0, "the capture");
            helper.assertTrue(orb.capstone(4) != null, "the ungemmed southern capstone isn't part of the circle");
            helper.succeed();
        });
    }

    @GameTest(template = "arena", timeoutTicks = 100)
    public static void capstonesWorkInOppositePairs(GameTestHelper helper) {
        EssenceOrbBlockEntity orb = orb(helper, ORB, EssenceKind.CHANNEL);
        capstone(helper, ORB.north(4), true);                 // N, paired with S four out
        capstone(helper, ORB.south(4), true);                 // S
        capstone(helper, ORB.east(3), true);                  // E, alone: W is bare
        capstone(helper, ORB.west(3), false);                 // W, no gem
        capstone(helper, ORB.offset(2, 0, -2), true);         // NE, alone: nothing at SW
        capstone(helper, ORB.offset(3, 0, 3), true);          // SE three out, paired with NW three out
        capstone(helper, ORB.offset(-3, 0, -3), true);        // NW (a pair at its own distance)
        capstone(helper, ORB.offset(-2, 0, 2), true);         // SW two out: NE is two out too, so they pair
        helper.runAfterDelay(45, () -> {
            helper.assertValueEqual(orb.gems(), 0b1111_1111 & ~(1 << 6), "the gemmed capstones seen");
            helper.assertValueEqual(orb.pairedGems(), 0b1011_1011, "the working pairs");
            helper.assertValueEqual(orb.capture(), 6 / 8.0, "the capture");
            helper.succeed();
        });
    }

    @GameTest(template = "arena", timeoutTicks = 100)
    public static void pairedCapstonesMustStandTheSameDistanceOut(GameTestHelper helper) {
        EssenceOrbBlockEntity orb = orb(helper, ORB, EssenceKind.CHANNEL);
        capstone(helper, ORB.north(4), true);                 // N four out
        capstone(helper, ORB.south(5), true);                 // S five out: no match
        capstone(helper, ORB.east(6), true);                  // E six out
        capstone(helper, ORB.west(6), true);                  // W six out: a pair
        capstone(helper, ORB.offset(2, 0, -2), true);         // NE two out
        capstone(helper, ORB.offset(-4, 0, 4), true);         // SW four out: no match
        helper.runAfterDelay(45, () -> {
            helper.assertValueEqual(orb.gems(), 0b0111_0111, "the gemmed capstones seen");
            helper.assertValueEqual(orb.pairedGems(), 0b0100_0100, "the working pair");
            helper.assertValueEqual(orb.capture(), 2 / 8.0, "the capture");
            helper.succeed();
        });
    }

    @GameTest(template = "arena", timeoutTicks = 200)
    public static void everyRodJarChargesAtTheFullFlow(GameTestHelper helper) {
        orb(helper, ORB, EssenceKind.CHANNEL);
        circle(helper, ORB);
        AetherJarBlockEntity near = rodJar(helper, ORB.offset(2, -3, 0));
        AetherJarBlockEntity far = rodJar(helper, ORB.offset(-4, -6, 4));
        AetherJarBlockEntity outside = rodJar(helper, ORB.offset(7, -3, 0));
        AetherJarBlockEntity tooLow = rodJar(helper, ORB.offset(0, -8, 2));
        AetherJarBlockEntity bare = jar(helper, ORB.offset(-2, -2, 0), Direction.UP);
        helper.runAfterDelay(120, () -> {
            helper.assertTrue(near.energy() > 0, "the near jar has no charge");
            helper.assertValueEqual(far.energy(), near.energy(), "the far jar's charge against the near one's");
            helper.assertValueEqual(outside.energy(), 0, "the charge of a jar seven out");
            helper.assertValueEqual(tooLow.energy(), 0, "the charge of a jar eight down");
            helper.assertValueEqual(bare.energy(), 0, "the charge of a jar without a rod");
            helper.succeed();
        });
    }

    @GameTest(template = "arena", timeoutTicks = 300)
    public static void thePlinthFillsOneJarAtATime(GameTestHelper helper) {
        orb(helper, ORB, EssenceKind.WELLSPRING);
        circle(helper, ORB);
        EssencePlinthBlockEntity plinth = plinth(helper, ORB.below(4));
        ItemStack jars = new ItemStack(ElectricRegistries.AETHER_JAR_ITEM.get(), 2);
        jars.set(ElectricRegistries.STORED_ENERGY.get(), AetherJarBlockEntity.capacity() - 200);
        helper.assertTrue(plinth.insert(jars, false).isEmpty(), "the plinth took the jars");
        helper.assertValueEqual(plinth.work().getCount(), 1, "jars worked at once");
        helper.assertValueEqual(plinth.input().getCount(), 1, "jars waiting");
        helper.succeedWhen(() -> {
            helper.assertValueEqual(plinth.output().getCount(), 2, "full jars");
            helper.assertValueEqual(AetherJarItem.energy(plinth.output()), AetherJarBlockEntity.capacity(),
                    "their charge");
            helper.assertTrue(plinth.work().isEmpty() && plinth.input().isEmpty(), "jars left behind");
        });
    }

    @GameTest(template = "arena", timeoutTicks = 400)
    public static void aCircleInfusesHolystoneIntoCelezite(GameTestHelper helper) {
        EssenceOrbBlockEntity orb = orb(helper, ORB, EssenceKind.CHANNEL);
        orb.setGrade(0.99);
        circle(helper, ORB);
        EssencePlinthBlockEntity plinth = plinth(helper, ORB.below(2));
        plinth.insert(new ItemStack(AetherBlocks.HOLYSTONE.get(), 3), false);
        helper.succeedWhen(() -> {
            helper.assertTrue(plinth.output().is(AARegistries.CELEZITE.natural.block().get().asItem()),
                    "the output: " + plinth.output());
            helper.assertValueEqual(plinth.output().getCount(), 3, "celezite made");
        });
    }

    @GameTest(template = "arena", timeoutTicks = 40)
    public static void infusionCostsWhatTheRecipeSays(GameTestHelper helper) {
        EssencePlinthBlockEntity plinth = plinth(helper, new BlockPos(10, 2, 10));
        plinth.insert(new ItemStack(AetherBlocks.HOLYSTONE.get(), 2), false);
        plinth.feed(1_999, EssenceKind.CHANNEL);
        helper.assertTrue(plinth.output().isEmpty(), "holystone infused short of its cost");
        plinth.feed(1, EssenceKind.CHANNEL);
        helper.assertValueEqual(plinth.output().getCount(), 1, "celezite after the full cost");
        // a phial remembers where it was drawn
        EssencePlinthBlockEntity bottles = plinth(helper, new BlockPos(14, 2, 10));
        bottles.insert(new ItemStack(Items.GLASS_BOTTLE), false);
        bottles.feed(100_000, EssenceKind.WELLSPRING);
        helper.assertTrue(bottles.output().is(EssenceRegistries.QUINTESSENCE_PHIAL.get()), "no phial: " + bottles.output());
        helper.assertValueEqual(QuintessencePhialItem.source(bottles.output()), EssenceKind.WELLSPRING, "its source");
        // the plinth turns away what it can't work
        helper.assertTrue(!plinth.accepts(new ItemStack(Items.DIRT)), "the plinth took dirt");
        helper.succeed();
    }

    @GameTest(template = "arena", timeoutTicks = 100)
    public static void hoppersFeedAndEmptyThePlinth(GameTestHelper helper) {
        BlockPos plinthPos = new BlockPos(10, 3, 10);
        helper.setBlock(plinthPos.below(), Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN));
        EssencePlinthBlockEntity plinth = plinth(helper, plinthPos);
        helper.setBlock(plinthPos.above(), Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN));
        ((HopperBlockEntity) helper.getBlockEntity(plinthPos.above())).setItem(0,
                new ItemStack(AetherBlocks.HOLYSTONE.get(), 2));
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(!plinth.work().isEmpty(), "the hopper put nothing in");
            plinth.feed(2_000, EssenceKind.CHANNEL);
        });
        helper.runAfterDelay(60, () -> {
            HopperBlockEntity below = (HopperBlockEntity) helper.getBlockEntity(plinthPos.below());
            boolean got = false;
            for (int i = 0; i < below.getContainerSize(); i++) {
                got |= below.getItem(i).is(AARegistries.CELEZITE.natural.block().get().asItem());
            }
            helper.assertTrue(got, "the hopper below took no celezite");
            helper.succeed();
        });
    }

    @GameTest(template = "arena", timeoutTicks = 60)
    public static void theOrbCannotBeMovedOrBroken(GameTestHelper helper) {
        BlockPos at = new BlockPos(10, 3, 10);
        helper.setBlock(at.below(), Blocks.STONE);
        orb(helper, at, EssenceKind.WELLSPRING);
        helper.assertTrue(helper.getBlockState(at).getDestroySpeed(helper.getLevel(), helper.absolutePos(at)) < 0,
                "the orb can be broken");
        helper.setBlock(at.west(), Blocks.PISTON.defaultBlockState().setValue(DirectionalBlock.FACING, Direction.EAST));
        helper.setBlock(at.west(2), Blocks.REDSTONE_BLOCK);
        helper.runAfterDelay(20, () -> {
            helper.assertBlockPresent(EssenceRegistries.ESSENCE_WELLSPRING.get(), at);
            helper.assertTrue(!helper.getBlockState(at.west()).getValue(PistonBaseBlock.EXTENDED),
                    "the piston pushed the orb");
            helper.succeed();
        });
    }

    @GameTest(template = "arena", timeoutTicks = 200)
    public static void theCircleHealsPlayersInIt(GameTestHelper helper) {
        orb(helper, ORB, EssenceKind.WELLSPRING);
        circle(helper, ORB);
        helper.setBlock(ORB.below(3), Blocks.STONE);
        FakePlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "pilgrim"));
        player.moveTo(helper.absoluteVec(new Vec3(ORB.getX() + 0.5, ORB.getY() - 2, ORB.getZ() + 0.5)));
        helper.getLevel().addNewPlayer(player);
        player.setHealth(10);
        helper.runAfterDelay(150, () -> {
            float health = player.getHealth();
            helper.getLevel().removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
            helper.assertTrue(health > 10, "the circle healed nothing: " + health);
            helper.succeed();
        });
    }

    // -- helpers --

    private static EssenceOrbBlockEntity orb(GameTestHelper helper, BlockPos pos, EssenceKind kind) {
        helper.setBlock(pos, kind == EssenceKind.CHANNEL ? EssenceRegistries.ESSENCE_CHANNEL.get()
                : EssenceRegistries.ESSENCE_WELLSPRING.get());
        return (EssenceOrbBlockEntity) helper.getBlockEntity(pos);
    }

    /** A capstone at {@code pos}, with a zanite gem if {@code gem}. */
    private static void capstone(GameTestHelper helper, BlockPos pos, boolean gem) {
        helper.setBlock(pos, EssenceRegistries.ESSENCE_CAPSTONE.get().defaultBlockState()
                .setValue(EssenceCapstoneBlock.GEM, gem ? CapstoneGem.ZANITE : CapstoneGem.NONE));
        if (gem) {
            ((EssenceCapstoneBlockEntity) helper.getBlockEntity(pos))
                    .setGem(new ItemStack(AetherItems.ZANITE_GEMSTONE.get()));
        }
    }

    /** All eight capstones, gemmed: four out on the cardinals, three each way on the diagonals. */
    private static void circle(GameTestHelper helper, BlockPos orb) {
        for (int point = 0; point < 8; point++) {
            int r = (point & 1) == 0 ? 4 : 3;
            capstone(helper, orb.offset(EssenceOrbBlockEntity.DX[point] * r, 0, EssenceOrbBlockEntity.DZ[point] * r),
                    true);
        }
    }

    private static EssencePlinthBlockEntity plinth(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, EssenceRegistries.ESSENCE_PLINTH.get());
        return (EssencePlinthBlockEntity) helper.getBlockEntity(pos);
    }

    private static AetherJarBlockEntity jar(GameTestHelper helper, BlockPos pos, Direction facing) {
        helper.setBlock(pos, ElectricRegistries.AETHER_JAR.get().defaultBlockState().setValue(AetherJarBlock.FACING, facing));
        return (AetherJarBlockEntity) helper.getBlockEntity(pos);
    }

    /** An upright jar with a lightning rod on its top. */
    private static AetherJarBlockEntity rodJar(GameTestHelper helper, BlockPos pos) {
        AetherJarBlockEntity jar = jar(helper, pos, Direction.UP);
        helper.setBlock(pos.above(), Blocks.LIGHTNING_ROD);
        return jar;
    }

    private EssenceTests() {
    }
}
