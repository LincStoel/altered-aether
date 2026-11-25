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

import com.aetherteam.aether.item.AetherItems;
import com.linc.alteredaether.compat.CreateElectric;
import com.linc.alteredaether.compat.create.AerogelWheelBlockEntity;
import com.linc.alteredaether.config.AACommonConfig;
import com.linc.alteredaether.electric.AetherJarBlock;
import com.linc.alteredaether.electric.AetherJarBlockEntity;
import com.linc.alteredaether.electric.ElectricRegistries;
import com.linc.alteredaether.electric.SquallGeneratorBlock;
import com.linc.alteredaether.electric.SquallGeneratorHost;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;

/** The Create side of {@link CreateElectricTests}; loaded only when Create is. */
final class CreateElectricCheck {
    static void basinHeat(GameTestHelper helper) {
        BlockState generator = ElectricRegistries.SQUALL_GENERATOR.get().defaultBlockState();
        helper.assertValueEqual(BasinBlockEntity.getHeatLevelOf(generator), BlazeBurnerBlock.HeatLevel.NONE,
                "a cold generator's heat");
        helper.assertValueEqual(BasinBlockEntity.getHeatLevelOf(generator.setValue(SquallGeneratorBlock.HEAT,
                SquallGeneratorBlock.Heat.HEATED)), BlazeBurnerBlock.HeatLevel.KINDLED, "a running generator's heat");
        helper.assertValueEqual(BasinBlockEntity.getHeatLevelOf(generator.setValue(SquallGeneratorBlock.HEAT,
                SquallGeneratorBlock.Heat.SUPERHEATED)), BlazeBurnerBlock.HeatLevel.SEETHING, "a superheated one's");
        helper.succeed();
    }

    static void depot(GameTestHelper helper) {
        // one depot gets its item with the generator already lit, the other before it is fuelled
        IItemHandler lit = depot(helper, new BlockPos(3, 2, 5), true);
        IItemHandler late = depot(helper, new BlockPos(7, 2, 5), false);
        lit.insertItem(0, new ItemStack(Items.RAW_GOLD, 2), false);
        late.insertItem(0, new ItemStack(Items.RAW_IRON, 3), false);
        helper.runAfterDelay(30, () -> ((SquallGeneratorHost) helper.getBlockEntity(new BlockPos(7, 4, 5))).core()
                .insert(new ItemStack(AetherItems.AMBROSIUM_SHARD.get(), 4)));
        helper.succeedWhen(() -> {
            ItemStack first = lit.getStackInSlot(0), second = late.getStackInSlot(0);
            helper.assertTrue(first.is(Items.GOLD_INGOT) && first.getCount() == 2, "on the first depot: " + first);
            helper.assertTrue(second.is(Items.IRON_INGOT) && second.getCount() == 3, "on the second depot: " + second);
        });
    }

    /** A depot with a generator face down two blocks over it, fuelled now or not; its item handler. */
    private static IItemHandler depot(GameTestHelper helper, BlockPos depot, boolean fuelled) {
        helper.setBlock(depot, BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("create", "depot")));
        BlockPos generator = depot.above(2);
        helper.setBlock(generator, ElectricRegistries.SQUALL_GENERATOR.get().defaultBlockState()
                .setValue(SquallGeneratorBlock.FACING, Direction.DOWN));
        if (fuelled) {
            ((SquallGeneratorHost) helper.getBlockEntity(generator)).core()
                    .insert(new ItemStack(AetherItems.AMBROSIUM_SHARD.get(), 4));
        }
        IItemHandler held = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, helper.absolutePos(depot),
                Direction.UP);
        helper.assertTrue(held != null, "the depot has no item handler");
        return held;
    }

    static void wheel(GameTestHelper helper) {
        BlockPos wheelPos = frame(helper);
        AetherJarBlockEntity west = jar(helper, wheelPos, -1), east = jar(helper, wheelPos, 1);
        int start = west.energy() + east.energy();
        int[] ran = {0};
        helper.onEachTick(() -> {
            if (wheel(helper, wheelPos).running()) {
                ran[0]++;
            }
        });
        helper.runAfterDelay(120, () -> {
            AerogelWheelBlockEntity wheel = wheel(helper, wheelPos);
            helper.assertTrue(wheel.running(), "the wheel isn't turning: " + wheel.stop());
            helper.assertValueEqual(Math.round(wheel.getGeneratedSpeed()), 8, "its speed");
            float capacity = wheel.calculateAddedStressCapacity();
            helper.assertValueEqual(Math.round(capacity), AACommonConfig.WHEEL_STRESS_PER_RPM.get(),
                    "its stress capacity per RPM");
            int spent = start - west.energy() - east.energy();
            int expected = AerogelWheelBlockEntity.drain(8) * ran[0];
            helper.assertTrue(Math.abs(spent - expected) <= AerogelWheelBlockEntity.drain(8) * 2,
                    "it drew " + spent + " FE over " + ran[0] + " ticks, not " + expected);
            int westSpent = AetherJarBlockEntity.capacity() - west.energy();
            int eastSpent = AetherJarBlockEntity.capacity() - east.energy();
            helper.assertTrue(Math.abs(westSpent - eastSpent) <= ran[0] + 2, "the jars drained unevenly: " + westSpent
                    + " and " + eastSpent);
            helper.setBlock(wheelPos.north(), Blocks.REDSTONE_BLOCK);
        });
        helper.runAfterDelay(160, () -> {
            AerogelWheelBlockEntity wheel = wheel(helper, wheelPos);
            helper.assertFalse(wheel.running(), "a powered wheel still turns");
            helper.assertValueEqual(wheel.stop(), AerogelWheelBlockEntity.Stop.REDSTONE, "why it stopped");
            helper.succeed();
        });
    }

    static void wheelDrained(GameTestHelper helper) {
        BlockPos wheelPos = frame(helper);
        AetherJarBlockEntity west = jar(helper, wheelPos, -1);
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(wheel(helper, wheelPos).running(), "the wheel never started");
            west.draw(west.energy(), false);
        });
        helper.runAfterDelay(80, () -> {
            AerogelWheelBlockEntity wheel = wheel(helper, wheelPos);
            helper.assertFalse(wheel.running(), "the wheel turns on one jar");
            helper.assertValueEqual(wheel.stop(), AerogelWheelBlockEntity.Stop.DRAINED, "why it stopped");
            helper.assertValueEqual(Math.round(wheel.getGeneratedSpeed()), 0, "its speed");
            // and a wheel with a rod missing has no frame
            helper.setBlock(wheelPos.east(2), Blocks.AIR);
        });
        helper.runAfterDelay(120, () -> {
            helper.assertValueEqual(wheel(helper, wheelPos).stop(), AerogelWheelBlockEntity.Stop.FRAME,
                    "a wheel missing a rod");
            helper.succeed();
        });
    }

    /**
     * The rods alone, two out either side, with their jars turned: the west one hanging over its rod, top down, the east
     * one beside its rod on the far side, top west. Both drain as the wheel turns.
     */
    static void wheelTurnedJars(GameTestHelper helper) {
        BlockPos wheelPos = new BlockPos(6, 3, 6);
        BlockPos westRod = wheelPos.west(2), eastRod = wheelPos.east(2);
        helper.setBlock(westRod.below(), Blocks.STONE);
        helper.setBlock(eastRod.below(), Blocks.STONE);
        helper.setBlock(westRod, Blocks.LIGHTNING_ROD);
        helper.setBlock(eastRod, Blocks.LIGHTNING_ROD);
        AetherJarBlockEntity west = turnedJar(helper, westRod.above(), Direction.DOWN);
        AetherJarBlockEntity east = turnedJar(helper, eastRod.east(), Direction.WEST);
        helper.setBlock(wheelPos, CreateElectric.AEROGEL_WHEEL.get());
        helper.runAfterDelay(60, () -> {
            AerogelWheelBlockEntity wheel = wheel(helper, wheelPos);
            helper.assertTrue(wheel.running(), "the wheel isn't turning: " + wheel.stop());
            helper.assertTrue(west.energy() < AetherJarBlockEntity.capacity(), "the hanging jar wasn't drawn on");
            helper.assertTrue(east.energy() < AetherJarBlockEntity.capacity(), "the sideways jar wasn't drawn on");
            helper.succeed();
        });
    }

    private static AetherJarBlockEntity turnedJar(GameTestHelper helper, BlockPos pos, Direction facing) {
        helper.setBlock(pos, ElectricRegistries.AETHER_JAR.get().defaultBlockState().setValue(AetherJarBlock.FACING, facing));
        AetherJarBlockEntity jar = (AetherJarBlockEntity) helper.getBlockEntity(pos);
        jar.charge(AetherJarBlockEntity.capacity());
        return jar;
    }

    /** Charged jars on stone two out either side along X, rods on them, the wheel between the tips. */
    private static BlockPos frame(GameTestHelper helper) {
        BlockPos wheel = new BlockPos(6, 3, 6);
        for (int side : new int[]{-1, 1}) {
            BlockPos jar = wheel.east(2 * side).below();
            helper.setBlock(jar.below(), Blocks.STONE);
            helper.setBlock(jar, ElectricRegistries.AETHER_JAR.get());
            helper.setBlock(jar.above(), Blocks.LIGHTNING_ROD);
            ((AetherJarBlockEntity) helper.getBlockEntity(jar)).charge(AetherJarBlockEntity.capacity());
        }
        helper.setBlock(wheel, CreateElectric.AEROGEL_WHEEL.get());
        return wheel;
    }

    private static AetherJarBlockEntity jar(GameTestHelper helper, BlockPos wheel, int side) {
        return (AetherJarBlockEntity) helper.getBlockEntity(wheel.east(2 * side).below());
    }

    private static AerogelWheelBlockEntity wheel(GameTestHelper helper, BlockPos pos) {
        return (AerogelWheelBlockEntity) helper.getBlockEntity(pos);
    }

    private CreateElectricCheck() {
    }
}
