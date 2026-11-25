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
import com.linc.alteredaether.electric.AetherJarBlock;
import com.linc.alteredaether.electric.AetherJarBlockEntity;
import com.linc.alteredaether.electric.AetherJarItem;
import com.linc.alteredaether.electric.ElectricRegistries;
import com.linc.alteredaether.electric.SquallCapture;
import com.linc.alteredaether.electric.SquallGeneratorBlock;
import com.linc.alteredaether.electric.SquallGeneratorHost;
import com.linc.alteredaether.electric.StaticBoots;
import com.linc.alteredaether.entity.Squall;
import com.linc.alteredaether.registry.AARegistries;
import com.mojang.authlib.GameProfile;
import io.wispforest.accessories.api.AccessoriesCapability;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightningRodBlock;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

/**
 * The Tempest Peaks electricity without Create: the Aether Jar's charge and its live copper, the Squall Generator's
 * fuel, jar charging, shocks and flash blasting, catching a squall both ways, the storm aercloud and the Static Boots.
 */
@GameTestHolder(AlteredAether.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ElectricTests {
    // -- the jar --

    @GameTest(template = "arena", timeoutTicks = 100)
    public static void lightningOnARodChargesTheJarUnderIt(GameTestHelper helper) {
        BlockPos jarPos = floor(helper, new BlockPos(5, 2, 5));
        helper.setBlock(jarPos.above(), Blocks.LIGHTNING_ROD);
        AetherJarBlockEntity jar = jar(helper, jarPos);
        // a rod takes a bolt at the block over it, as ServerLevel.findLightningRod aims it
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(helper.getLevel());
        bolt.moveTo(Vec3.atBottomCenterOf(helper.absolutePos(jarPos.above(2))));
        helper.getLevel().addFreshEntity(bolt);
        helper.succeedWhen(() -> helper.assertValueEqual(jar.energy(), AACommonConfig.JAR_STRIKE_CHARGE.get(),
                "the jar's charge after one strike"));
    }

    @GameTest(template = "arena")
    public static void fourStrikesFillAJar(GameTestHelper helper) {
        BlockPos jarPos = floor(helper, new BlockPos(5, 2, 5));
        AetherJarBlockEntity jar = jar(helper, jarPos);
        for (int i = 0; i < 4; i++) {
            jar.strike();
        }
        helper.assertValueEqual(jar.energy(), AetherJarBlockEntity.capacity(), "a jar after four strikes");
        jar.strike();
        helper.assertValueEqual(jar.energy(), AetherJarBlockEntity.capacity(), "a full jar struck again");
        helper.succeed();
    }

    @GameTest(template = "arena")
    public static void cablesPutPowerInAtTheBottomAndTakeItOutAtTheTop(GameTestHelper helper) {
        BlockPos jarPos = floor(helper, new BlockPos(5, 2, 5));
        AetherJarBlockEntity jar = jar(helper, jarPos, Direction.EAST);
        jar.charge(10_000);
        BlockPos at = helper.absolutePos(jarPos);
        for (Direction side : new Direction[]{Direction.UP, Direction.DOWN, Direction.NORTH}) {
            helper.assertTrue(helper.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK, at, side) == null,
                    "a jar on its side has energy on its " + side + " face");
        }
        IEnergyStorage top = helper.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK, at, Direction.EAST);
        IEnergyStorage bottom = helper.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK, at, Direction.WEST);
        helper.assertTrue(top != null && bottom != null, "no energy on the jar's top or bottom");
        helper.assertValueEqual(top.receiveEnergy(1_000, false), 0, "FE in at the top");
        helper.assertValueEqual(bottom.extractEnergy(1_000, false), 0, "FE out at the bottom");
        int first = top.extractEnergy(1_000_000, false);
        int second = top.extractEnergy(1_000, false);
        helper.assertValueEqual(first, Math.min(10_000, AACommonConfig.JAR_OUTPUT.get()), "FE out in one go");
        helper.assertValueEqual(second, 0, "FE out a second time in the same tick");
        helper.assertTrue(bottom.receiveEnergy(1_000, false) > 0, "the bottom took no FE in");
        helper.succeed();
    }

    @GameTest(template = "arena", timeoutTicks = 100)
    public static void aJarPushesOutOfItsTopIntoTheNextJarsBottom(GameTestHelper helper) {
        BlockPos first = floor(helper, new BlockPos(5, 2, 5));
        floor(helper, new BlockPos(6, 2, 5));
        AetherJarBlockEntity from = jar(helper, first, Direction.EAST);
        AetherJarBlockEntity to = jar(helper, first.east(), Direction.EAST);
        from.charge(AetherJarBlockEntity.capacity());
        helper.succeedWhen(() -> helper.assertTrue(to.energy() > 0, "the second jar got nothing"));
    }

    @GameTest(template = "arena", timeoutTicks = 100)
    public static void theCreativeJarIsEndless(GameTestHelper helper) {
        BlockPos jarPos = floor(helper, new BlockPos(5, 2, 5));
        helper.setBlock(jarPos, ElectricRegistries.CREATIVE_AETHER_JAR.get());
        AetherJarBlockEntity creative = (AetherJarBlockEntity) helper.getBlockEntity(jarPos);
        AetherJarBlockEntity above = jar(helper, jarPos.above(), Direction.UP);
        IEnergyStorage top = helper.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK,
                helper.absolutePos(jarPos), Direction.UP);
        helper.assertValueEqual(top.extractEnergy(5_000_000, false), 5_000_000, "FE out of a creative jar");
        helper.assertValueEqual(top.extractEnergy(5_000_000, false), 5_000_000, "FE out of it again");
        helper.assertValueEqual(creative.draw(1_000_000, false), 1_000_000, "FE drawn directly");
        helper.assertValueEqual(creative.energy(), AetherJarBlockEntity.capacity(), "the creative jar's charge");
        helper.succeedWhen(() -> helper.assertTrue(above.energy() > 0, "the jar on it got nothing"));
    }

    @GameTest(template = "arena", timeoutTicks = 400)
    public static void aLiveRodOnAJarShocksWhatTouchesIt(GameTestHelper helper) {
        BlockPos jarPos = floor(helper, new BlockPos(5, 2, 5));
        jar(helper, jarPos, Direction.UP).charge(AetherJarBlockEntity.capacity());
        BlockPos rod = jarPos.above();
        helper.setBlock(rod, Blocks.LIGHTNING_ROD);
        // a pen round the rod's top, so the pig stays on it
        for (Direction side : Direction.Plane.HORIZONTAL) {
            helper.setBlock(rod.above().relative(side), Blocks.GLASS);
            helper.setBlock(rod.above(2).relative(side), Blocks.GLASS);
        }
        Pig pig = helper.spawn(EntityType.PIG, Vec3.atBottomCenterOf(rod.above()));
        pig.setPersistenceRequired();
        float health = pig.getHealth();
        helper.succeedWhen(() -> helper.assertTrue(pig.getHealth() < health, "the pig on the live rod wasn't shocked"));
    }

    @GameTest(template = "arena", timeoutTicks = 40)
    public static void aBrokenJarKeepsItsCharge(GameTestHelper helper) {
        BlockPos jarPos = floor(helper, new BlockPos(5, 2, 5));
        jar(helper, jarPos).charge(123_456);
        helper.getLevel().destroyBlock(helper.absolutePos(jarPos), true);
        helper.succeedWhen(() -> {
            List<ItemEntity> drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                    new AABB(helper.absolutePos(jarPos)).inflate(2));
            helper.assertTrue(!drops.isEmpty(), "the jar dropped nothing");
            helper.assertValueEqual(AetherJarItem.energy(drops.get(0).getItem()), 123_456, "the dropped jar's charge");
        });
    }

    @GameTest(template = "arena", timeoutTicks = 100)
    public static void aJarOnCopperElectrifiesUpToTheCap(GameTestHelper helper) {
        int cap = AACommonConfig.COPPER_MAX_BLOCKS.get();
        for (int x = 0; x < 40; x++) {
            for (int z = 0; z < 3; z++) {
                helper.setBlock(new BlockPos(2 + x, 2, 2 + z), Blocks.CUT_COPPER);
            }
        }
        BlockPos jarPos = new BlockPos(2, 3, 2);
        helper.setBlock(jarPos, ElectricRegistries.AETHER_JAR.get());
        AetherJarBlockEntity jar = jar(helper, jarPos);
        jar.charge(AetherJarBlockEntity.capacity());
        helper.succeedWhen(() -> helper.assertValueEqual(jar.liveCopper(), cap, "copper blocks live"));
    }

    @GameTest(template = "arena", timeoutTicks = 200)
    public static void liveCopperShocksWhatStandsOnIt(GameTestHelper helper) {
        for (int x = 0; x < 5; x++) {
            helper.setBlock(new BlockPos(2 + x, 2, 2), Blocks.COPPER_BLOCK);
        }
        BlockPos jarPos = new BlockPos(2, 3, 2);
        helper.setBlock(jarPos, ElectricRegistries.AETHER_JAR.get());
        AetherJarBlockEntity jar = jar(helper, jarPos);
        jar.charge(AetherJarBlockEntity.capacity());
        Pig pig = helper.spawn(EntityType.PIG, new Vec3(6.5, 3, 2.5));
        pig.setPersistenceRequired();
        float health = pig.getHealth();
        FakePlayer insulated = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "insulated"));
        insulated.moveTo(helper.absoluteVec(new Vec3(4.5, 3, 2.5)));
        insulated.setOnGround(true);
        insulated.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.LEATHER_BOOTS));
        helper.getLevel().addNewPlayer(insulated);
        float playerHealth = insulated.getHealth();
        helper.succeedWhen(() -> {
            helper.assertTrue(pig.getHealth() < health, "the pig on live copper wasn't shocked");
            helper.assertValueEqual(insulated.getHealth(), playerHealth, "the player in leather boots' health");
            helper.assertTrue(jar.energy() < AetherJarBlockEntity.capacity(), "the copper drew nothing");
            helper.getLevel().removePlayerImmediately(insulated, Entity.RemovalReason.DISCARDED);
        });
    }

    // -- the generator --

    @GameTest(template = "arena", timeoutTicks = 200)
    public static void aGeneratorChargesAJarThroughARodOnItsTop(GameTestHelper helper) {
        // facing north at a rod four out, the rod on the top of a jar lying on its side beyond it
        BlockPos generatorPos = generator(helper, floor(helper, new BlockPos(5, 2, 10)), Direction.NORTH);
        BlockPos rod = generatorPos.north(4);
        floor(helper, rod.below());
        helper.setBlock(rod, Blocks.LIGHTNING_ROD);
        floor(helper, rod.north().below());
        AetherJarBlockEntity jar = jar(helper, rod.north(), Direction.SOUTH);
        // a jar behind the generator, as it used to be charged, now gets nothing
        floor(helper, generatorPos.south().below());
        AetherJarBlockEntity behind = jar(helper, generatorPos.south(), Direction.NORTH);
        host(helper, generatorPos).core().insert(new ItemStack(com.aetherteam.aether.item.AetherItems.AMBROSIUM_SHARD.get()));
        int perShock = com.linc.alteredaether.electric.SquallGeneratorCore.chargePerShock();
        helper.assertValueEqual(perShock, (int) Math.round(AACommonConfig.GENERATOR_ZAP_INTERVAL.get()
                * AACommonConfig.GENERATOR_FE_COST.get() * 0.75), "FE a shock puts in");
        helper.runAfterDelay(101, () -> {
            helper.assertTrue(jar.energy() >= 4 * perShock && jar.energy() <= 5 * perShock
                    && jar.energy() % perShock == 0, "the jar's charge: " + jar.energy());
            helper.assertValueEqual(behind.energy(), 0, "the charge of the jar behind it");
            int burn = host(helper, generatorPos).core().burnTime();
            helper.assertTrue(burn >= 1600 - 103 && burn <= 1600 - 98, "ambrosium left: " + burn);
            helper.assertTrue(helper.getBlockState(generatorPos).getValue(SquallGeneratorBlock.LIT), "it isn't lit");
            helper.assertValueEqual(helper.getBlockState(generatorPos).getValue(SquallGeneratorBlock.HEAT),
                    SquallGeneratorBlock.Heat.HEATED, "its heat");
            helper.succeed();
        });
    }

    @GameTest(template = "arena", timeoutTicks = 100)
    public static void aGeneratorScrapesCopperAndPulsesRedstone(GameTestHelper helper) {
        BlockPos generatorPos = generator(helper, floor(helper, new BlockPos(5, 2, 5)), Direction.NORTH);
        BlockPos copper = generatorPos.north();
        helper.setBlock(copper, Blocks.OXIDIZED_COPPER);
        BlockPos lamp = floor(helper, new BlockPos(8, 2, 5));
        BlockPos second = generator(helper, lamp.south(), Direction.NORTH);
        helper.setBlock(lamp, Blocks.REDSTONE_LAMP);
        fuel(helper, generatorPos);
        fuel(helper, second);
        boolean[] lit = {false};
        helper.onEachTick(() -> lit[0] |= helper.getBlockState(lamp).getValue(RedstoneLampBlock.LIT));
        helper.succeedWhen(() -> {
            helper.assertBlockPresent(Blocks.WEATHERED_COPPER, copper);
            helper.assertTrue(lit[0], "the lamp it faces never lit");
        });
    }

    @GameTest(template = "arena", timeoutTicks = 100)
    public static void aGeneratorFlashBlastsWhatLiesInFrontOfIt(GameTestHelper helper) {
        BlockPos generatorPos = generator(helper, floor(helper, new BlockPos(5, 2, 5)), Direction.NORTH);
        floor(helper, new BlockPos(5, 2, 4));
        fuel(helper, generatorPos);
        BlockPos front = generatorPos.north();
        ItemEntity iron = new ItemEntity(helper.getLevel(), 0, 0, 0, new ItemStack(Items.RAW_IRON, 3));
        iron.moveTo(Vec3.atBottomCenterOf(helper.absolutePos(front)));
        iron.setDeltaMovement(Vec3.ZERO);
        helper.getLevel().addFreshEntity(iron);
        ItemEntity sand = new ItemEntity(helper.getLevel(), 0, 0, 0, new ItemStack(Items.SAND, 2));
        sand.moveTo(Vec3.atBottomCenterOf(helper.absolutePos(front)));
        sand.setDeltaMovement(Vec3.ZERO);
        helper.getLevel().addFreshEntity(sand);
        helper.succeedWhen(() -> {
            helper.assertItemEntityCountIs(Items.IRON_INGOT, front, 1.5, 3);
            helper.assertItemEntityCountIs(Items.GLASS, front, 1.5, 2);
        });
    }

    @GameTest(template = "arena", timeoutTicks = 100)
    public static void aGeneratorShocksTwoBlocksOutButNoFurther(GameTestHelper helper) {
        BlockPos near = generator(helper, floor(helper, new BlockPos(3, 2, 8)), Direction.NORTH);
        BlockPos far = generator(helper, floor(helper, new BlockPos(7, 2, 8)), Direction.NORTH);
        helper.setBlock(near.north(2), Blocks.OXIDIZED_COPPER);
        helper.setBlock(far.north(3), Blocks.OXIDIZED_COPPER);
        fuel(helper, near);
        fuel(helper, far);
        // both shock on the same tick, so once the near copper is scraped the far one has been shocked at too
        helper.succeedWhen(() -> {
            helper.assertFalse(helper.getBlockState(near.north(2)).is(Blocks.OXIDIZED_COPPER), "copper two out unscraped");
            helper.assertBlockPresent(Blocks.OXIDIZED_COPPER, far.north(3));
        });
    }

    @GameTest(template = "arena", timeoutTicks = 100)
    public static void aGeneratorArcsToALightningRodEightBlocksOut(GameTestHelper helper) {
        BlockPos generatorPos = generator(helper, floor(helper, new BlockPos(5, 2, 10)), Direction.NORTH);
        BlockPos rod = generatorPos.north(8);
        helper.setBlock(rod, Blocks.LIGHTNING_ROD);
        Pig pig = helper.spawn(EntityType.PIG, Vec3.atBottomCenterOf(generatorPos.north(6)));
        pig.setPersistenceRequired();
        pig.setNoAi(true);
        pig.setNoGravity(true);
        float health = pig.getHealth();
        fuel(helper, generatorPos);
        boolean[] powered = {false};
        helper.onEachTick(() -> powered[0] |= helper.getBlockState(rod).getValue(LightningRodBlock.POWERED));
        helper.succeedWhen(() -> {
            helper.assertTrue(pig.getHealth() < health, "the pig between the generator and the rod wasn't shocked");
            helper.assertTrue(powered[0], "the rod was never powered");
        });
    }

    // -- catching a squall --

    @GameTest(template = "arena", timeoutTicks = 100)
    public static void aSquallsBoltBesideAnInsulatorCatchesIt(GameTestHelper helper) {
        BlockPos bowl = floor(helper, new BlockPos(10, 2, 10));
        helper.setBlock(bowl, ElectricRegistries.SQUALL_INSULATOR.get());
        Squall squall = helper.spawn(AARegistries.SQUALL.get(), new Vec3(10.5, 14, 10.5));
        squall.setPersistenceRequired();
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(helper.getLevel());
        // on the ground beside the bowl, where a target standing next to it would be struck
        floor(helper, new BlockPos(11, 2, 10));
        bolt.moveTo(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(11, 3, 10))));
        SquallCapture.called(bolt, squall);
        helper.getLevel().addFreshEntity(bolt);
        helper.succeedWhen(() -> {
            helper.assertBlockPresent(ElectricRegistries.SQUALL_GENERATOR.get(), bowl);
            helper.assertTrue(squall.isRemoved(), "the squall is still out");
        });
    }

    @GameTest(template = "arena", timeoutTicks = 100)
    public static void aSpentSquallIsCaughtByHand(GameTestHelper helper) {
        floor(helper, new BlockPos(10, 2, 10));
        Squall squall = helper.spawn(AARegistries.SQUALL.get(), new Vec3(10.5, 6, 10.5));
        squall.setPersistenceRequired();
        FakePlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "catcher"));
        player.moveTo(helper.absoluteVec(new Vec3(12.5, 3, 10.5)));
        helper.getLevel().addNewPlayer(player);
        ItemStack bowl = new ItemStack(ElectricRegistries.SQUALL_INSULATOR_ITEM.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, bowl);
        helper.assertFalse(bowl.interactLivingEntity(player, squall, InteractionHand.MAIN_HAND).consumesAction(),
                "a fresh squall was caught");
        squall.setHealth(squall.getMaxHealth() * 0.2F);
        helper.succeedWhen(() -> {
            helper.assertTrue(squall.isSpent(), "the worn-down squall isn't spent");
            player.getMainHandItem().interactLivingEntity(player, squall, InteractionHand.MAIN_HAND);
            helper.assertTrue(squall.isRemoved(), "the spent squall wasn't caught");
            helper.assertTrue(player.getMainHandItem().is(ElectricRegistries.SQUALL_GENERATOR_ITEM.get()),
                    "the bowl in hand didn't become a generator");
            helper.getLevel().removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
        });
    }

    // -- storm aercloud and boots --

    @GameTest(template = "arena", timeoutTicks = 400)
    public static void aStormAercloudShocksWhatIsInIt(GameTestHelper helper) {
        floor(helper, new BlockPos(5, 2, 5));
        helper.setBlock(new BlockPos(5, 3, 5), ElectricRegistries.STORM_AERCLOUD.get());
        // walled in, or it wanders off the one cloud (a pig without AI never moves, so a block never sees it)
        for (Direction side : Direction.Plane.HORIZONTAL) {
            helper.setBlock(new BlockPos(5, 3, 5).relative(side), Blocks.GLASS);
            helper.setBlock(new BlockPos(5, 4, 5).relative(side), Blocks.GLASS);
        }
        Pig pig = helper.spawn(EntityType.PIG, new Vec3(5.5, 3.2, 5.5));
        pig.setPersistenceRequired();
        float health = pig.getHealth();
        helper.succeedWhen(() -> helper.assertTrue(pig.getHealth() < health, "the pig in the cloud wasn't shocked"));
    }

    @GameTest(template = "arena")
    public static void staticBootsCapBoostedJumpsOnly(GameTestHelper helper) {
        helper.assertValueEqual(StaticBoots.jumpSpeed(0.42, 0.42, 0.5), 0.42, "a plain jump");
        helper.assertTrue(Math.abs(StaticBoots.jumpSpeed(0.62, 0.42, 0.5) - 0.62 * Math.sqrt(0.5)) < 1.0E-9,
                "a boosted jump, halved in height");
        helper.assertValueEqual(StaticBoots.jumpSpeed(0.5, 0.42, 0.5), 0.42, "a small boost, kept at a plain jump");
        helper.succeed();
    }

    @GameTest(template = "arena", timeoutTicks = 40)
    public static void staticBootsResistKnockbackAndStopTheBounce(GameTestHelper helper) {
        BlockPos cloud = floor(helper, new BlockPos(5, 2, 5));
        helper.setBlock(cloud, AetherBlocks.BLUE_AERCLOUD.get());
        FakePlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "grounded"));
        player.moveTo(helper.absoluteVec(Vec3.atBottomCenterOf(cloud)));
        helper.getLevel().addNewPlayer(player);
        BlockState blue = helper.getBlockState(cloud);
        player.setDeltaMovement(0, -0.5, 0);
        blue.entityInside(helper.getLevel(), helper.absolutePos(cloud), player);
        helper.assertTrue(player.getDeltaMovement().y > 1, "a blue aercloud didn't bounce a bare-footed player");
        AccessoriesCapability.get(player).attemptToEquipAccessory(new ItemStack(ElectricRegistries.STATIC_BOOTS.get()));
        helper.succeedWhen(() -> {
            helper.assertTrue(StaticBoots.wearing(player), "the boots aren't on");
            helper.assertTrue(Math.abs(player.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE)
                    - AACommonConfig.STATIC_BOOTS_KNOCKBACK.get()) < 1.0E-6, "knockback resistance with the boots");
            player.setDeltaMovement(0, -0.5, 0);
            blue.entityInside(helper.getLevel(), helper.absolutePos(cloud), player);
            helper.assertTrue(player.getDeltaMovement().y <= 0, "a blue aercloud bounced a player in Static Boots");
            helper.getLevel().removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
        });
    }

    // -- helpers --

    /** Stone under {@code pos}, which is returned one up: where to stand a block on the floor. */
    private static BlockPos floor(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, Blocks.STONE);
        return pos.above();
    }

    private static AetherJarBlockEntity jar(GameTestHelper helper, BlockPos pos, Direction facing) {
        helper.setBlock(pos, ElectricRegistries.AETHER_JAR.get().defaultBlockState().setValue(AetherJarBlock.FACING, facing));
        return (AetherJarBlockEntity) helper.getBlockEntity(pos);
    }

    private static AetherJarBlockEntity jar(GameTestHelper helper, BlockPos pos) {
        if (!helper.getBlockState(pos).is(ElectricRegistries.AETHER_JAR.get())) {
            helper.setBlock(pos, ElectricRegistries.AETHER_JAR.get());
        }
        return (AetherJarBlockEntity) helper.getBlockEntity(pos);
    }

    private static BlockPos generator(GameTestHelper helper, BlockPos pos, Direction facing) {
        helper.setBlock(pos, ElectricRegistries.SQUALL_GENERATOR.get().defaultBlockState()
                .setValue(SquallGeneratorBlock.FACING, facing));
        return pos;
    }

    private static SquallGeneratorHost host(GameTestHelper helper, BlockPos pos) {
        return (SquallGeneratorHost) helper.getBlockEntity(pos);
    }

    private static void fuel(GameTestHelper helper, BlockPos pos) {
        host(helper, pos).core().insert(new ItemStack(com.aetherteam.aether.item.AetherItems.AMBROSIUM_SHARD.get(), 4));
    }

    private ElectricTests() {
    }
}
