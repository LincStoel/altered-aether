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

import com.aetherteam.aether.entity.AetherEntityTypes;
import com.aetherteam.aether.entity.passive.Aerwhale;
import com.aetherteam.aether.item.AetherItems;
import com.linc.alteredaether.AlteredAether;
import com.mojang.authlib.GameProfile;
import com.linc.alteredaether.entity.AerwhaleCommand;
import com.linc.alteredaether.entity.AerwhaleEject;
import com.linc.alteredaether.entity.AerwhaleMode;
import com.linc.alteredaether.entity.TamedAerwhale;
import com.linc.alteredaether.registry.AARegistries;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Taming, commands, saddling, riding, the deck, breeding and saving of tamed aerwhales. */
@GameTestHolder(AlteredAether.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AerwhaleTests {
    private static final Vec3 SPOT = new Vec3(24, 20, 24);
    private static final ResourceLocation DEEP_AETHER_SADDLE =
            ResourceLocation.fromNamespaceAndPath("deep_aether", "aerwhale_saddle");

    @GameTest(template = "arena")
    public static void blueBerriesTameWildAerwhales(GameTestHelper helper) {
        if (deepAetherWhales(helper)) {
            return;
        }
        Aerwhale wild = helper.spawn(AetherEntityTypes.AERWHALE.get(), SPOT);
        wild.setNoAi(true);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(AetherItems.BLUE_BERRY.get(), 64));
        // a third of the berries tame, so 64 tries all failing is a one in 10^11 chance
        while (wild.isAlive() && !player.getMainHandItem().isEmpty()) {
            player.interactOn(wild, InteractionHand.MAIN_HAND);
        }
        List<TamedAerwhale> tamed = tamedNear(helper);
        helper.assertTrue(!wild.isAlive(), "the wild aerwhale should have been replaced");
        helper.assertTrue(tamed.size() == 1, "expected one tamed aerwhale, found " + tamed.size());
        helper.assertTrue(tamed.getFirst().isOwnedBy(player), "the tamer should own it");
        helper.assertTrue(tamed.getFirst().command() == AerwhaleCommand.FOLLOW, "a new tame should follow its tamer");
        helper.assertTrue(player.getMainHandItem().getCount() < 64, "taming should use berries");
        helper.succeed();
    }

    @GameTest(template = "arena")
    public static void tamingCountsForBestFriendsForever(GameTestHelper helper) {
        if (deepAetherWhales(helper)) {
            return;
        }
        // a bare server player: NeoForge's fake players never earn advancements, and the mock one can't log in
        ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "aerwhale-tamer"), ClientInformation.createDefault());
        AdvancementHolder advancement = helper.getLevel().getServer().getAdvancements()
                .get(ResourceLocation.withDefaultNamespace("husbandry/tame_an_animal"));
        helper.assertTrue(advancement != null, "vanilla's tame_an_animal advancement should exist");
        tamed(helper, player);
        helper.assertTrue(player.getAdvancements().getOrStartProgress(advancement).isDone(),
                "taming an aerwhale should award Best Friends Forever");
        helper.succeed();
    }

    @GameTest(template = "arena")
    public static void sneakingCyclesCommands(GameTestHelper helper) {
        if (deepAetherWhales(helper)) {
            return;
        }
        Player owner = helper.makeMockPlayer(GameType.SURVIVAL);
        TamedAerwhale whale = tamed(helper, owner);
        owner.setShiftKeyDown(true);
        AerwhaleCommand[] expected = {AerwhaleCommand.STAY, AerwhaleCommand.WANDER, AerwhaleCommand.FOLLOW};
        for (AerwhaleCommand command : expected) {
            owner.interactOn(whale, InteractionHand.MAIN_HAND);
            helper.assertTrue(whale.command() == command, "expected " + command + ", got " + whale.command());
        }
        Player stranger = helper.makeMockPlayer(GameType.SURVIVAL);
        stranger.setShiftKeyDown(true);
        stranger.interactOn(whale, InteractionHand.MAIN_HAND);
        helper.assertTrue(whale.command() == AerwhaleCommand.FOLLOW, "only the owner gives commands");
        helper.succeed();
    }

    @GameTest(template = "arena", timeoutTicks = 300)
    public static void followingAerwhalesApproachTheirOwner(GameTestHelper helper) {
        if (deepAetherWhales(helper)) {
            return;
        }
        Player owner = ownerInLevel(helper, new Vec3(4, 2, 4));
        TamedAerwhale whale = tamed(helper, owner);
        // close enough that it flies rather than teleports
        whale.setPos(helper.absoluteVec(new Vec3(20, 4, 20)));
        whale.setCommand(AerwhaleCommand.FOLLOW);
        double start = whale.distanceTo(owner);
        helper.succeedWhen(() -> {
            helper.assertTrue(whale.distanceTo(owner) < start - 8,
                    "should close in on its owner: " + start + " -> " + whale.distanceTo(owner) + " at " + whale.position());
            removeFromLevel(helper, owner);
        });
    }

    @GameTest(template = "arena", timeoutTicks = 100)
    public static void followingAerwhalesTeleportWhenLeftBehind(GameTestHelper helper) {
        if (deepAetherWhales(helper)) {
            return;
        }
        Player owner = ownerInLevel(helper, new Vec3(4, 2, 4));
        TamedAerwhale whale = tamed(helper, owner);
        whale.setPos(helper.absoluteVec(new Vec3(44, 30, 44)));
        whale.setCommand(AerwhaleCommand.FOLLOW);
        helper.succeedWhen(() -> {
            helper.assertTrue(whale.distanceTo(owner) < 14, "should have teleported to its owner, "
                    + whale.distanceTo(owner) + " away");
            removeFromLevel(helper, owner);
        });
    }

    @GameTest(template = "arena", timeoutTicks = 300)
    public static void stayingAerwhalesCircleHome(GameTestHelper helper) {
        if (deepAetherWhales(helper)) {
            return;
        }
        Player owner = helper.makeMockPlayer(GameType.SURVIVAL);
        TamedAerwhale whale = tamed(helper, owner);
        whale.setCommand(AerwhaleCommand.STAY);
        Vec3 home = whale.position();
        List<Vec3> seen = new ArrayList<>();
        helper.onEachTick(() -> seen.add(whale.position()));
        helper.runAfterDelay(240, () -> {
            double far = seen.stream().mapToDouble(at -> horizontal(at, home)).max().orElse(0);
            double spread = seen.stream().mapToDouble(at -> horizontal(at, seen.getLast())).max().orElse(0);
            helper.assertTrue(far < 16, "should keep close to home, strayed " + far);
            helper.assertTrue(far > 7, "should circle wide of home, kept within " + far);
            helper.assertTrue(spread > 6, "should circle rather than sit still, moved " + spread);
            helper.succeed();
        });
    }

    @GameTest(template = "arena", timeoutTicks = 200)
    public static void leapingOffLaunchesTheRiderAndTheWhaleCoasts(GameTestHelper helper) {
        if (deepAetherWhales(helper)) {
            return;
        }
        // a fake player can never ride, so this owner is the game test's mock
        Player owner = helper.makeMockPlayer(GameType.SURVIVAL);
        TamedAerwhale whale = tamed(helper, owner);
        whale.equipSaddle(new ItemStack(Items.SADDLE), null);
        whale.setCommand(AerwhaleCommand.STAY);
        helper.assertTrue(owner.startRiding(whale), "the owner should be aboard");
        Vec3 seat = owner.position();
        whale.eject(owner, 1, new Vec3(0.5, 0, 0));
        helper.assertFalse(owner.isPassenger(), "should have leapt off");
        helper.assertTrue(owner.position().distanceTo(seat) < 0.01, "should leave from the seat, not the deck");
        double launch = AerwhaleEject.launchSpeed(AerwhaleEject.MAX_HEIGHT, owner.getGravity());
        Vec3 thrown = owner.getDeltaMovement();
        helper.assertTrue(Math.abs(thrown.y - launch) < 1.0E-6 && Math.abs(thrown.x - 0.5) < 1.0E-6,
                "should be thrown up at the whale's speed, got " + thrown);
        helper.assertTrue(whale.coasting() && Math.abs(whale.getDeltaMovement().x - 0.5) < 1.0E-6,
                "the whale should coast on, got " + whale.getDeltaMovement());
        double startX = whale.getX();
        helper.runAfterDelay(10, () -> helper.assertTrue(whale.getX() - startX > 2,
                "the whale should keep going, moved " + (whale.getX() - startX)));
        helper.runAfterDelay(AerwhaleEject.COAST_TICKS + 5, () -> {
            helper.assertFalse(whale.coasting(), "should be done coasting");
            helper.assertTrue(whale.command() == AerwhaleCommand.FOLLOW, "should follow its owner, got " + whale.command());
            helper.succeed();
        });
    }

    @GameTest(template = "arena", timeoutTicks = 100)
    public static void leapingOffHandsTheReinsOn(GameTestHelper helper) {
        if (deepAetherWhales(helper)) {
            return;
        }
        Player owner = helper.makeMockPlayer(GameType.SURVIVAL);
        TamedAerwhale whale = tamed(helper, owner);
        whale.equipSaddle(new ItemStack(Items.SADDLE), null);
        whale.setCommand(AerwhaleCommand.STAY);
        Player second = helper.makeMockPlayer(GameType.SURVIVAL);
        owner.interactOn(whale, InteractionHand.MAIN_HAND);
        second.interactOn(whale, InteractionHand.MAIN_HAND);
        whale.eject(owner, 0, new Vec3(0.3, 0, 0));
        helper.assertTrue(whale.getControllingPassenger() == second, "the next aboard should take the reins");
        helper.assertFalse(whale.coasting(), "with a new pilot it shouldn't coast");
        helper.runAfterDelay(AerwhaleEject.COAST_TICKS + 5, () -> {
            helper.assertTrue(whale.command() == AerwhaleCommand.STAY, "its orders shouldn't change, got " + whale.command());
            helper.succeed();
        });
    }

    @GameTest(template = "arena")
    public static void landingOnYourAerwhaleMountsIt(GameTestHelper helper) {
        if (deepAetherWhales(helper)) {
            return;
        }
        // a fake player can never ride, so this owner is the game test's mock
        Player owner = helper.makeMockPlayer(GameType.SURVIVAL);
        TamedAerwhale whale = tamed(helper, owner);
        whale.equipSaddle(new ItemStack(Items.SADDLE), null);
        whale.setNoAi(true);
        helper.runAfterDelay(2, () -> {
            AABB slab = whale.getParts()[1].getBoundingBox();
            Vec3 top = new Vec3(slab.getCenter().x, slab.maxY, slab.getCenter().z);
            Player stranger = helper.makeMockPlayer(GameType.SURVIVAL);
            stranger.moveTo(top);
            helper.assertFalse(whale.tryLandingMount(stranger, 5), "only its owner is taken aboard");
            owner.moveTo(top);
            helper.assertFalse(whale.tryLandingMount(owner, 1), "a hop on the deck isn't a landing");
            helper.assertTrue(whale.tryLandingMount(owner, 5), "landing on its back should mount it");
            helper.assertTrue(owner.getVehicle() == whale, "the owner should be riding");
            owner.stopRiding();
            helper.succeed();
        });
    }

    @GameTest(template = "arena")
    public static void aerwhalesKeepAboveTheVoid(GameTestHelper helper) {
        if (deepAetherWhales(helper)) {
            return;
        }
        Player owner = helper.makeMockPlayer(GameType.SURVIVAL);
        TamedAerwhale whale = tamed(helper, owner);
        whale.getMoveControl().setWantedPosition(whale.getX(), helper.getLevel().getMinBuildHeight(), whale.getZ(), 1);
        helper.assertTrue(whale.getMoveControl().getWantedY() >= whale.floorY(),
                "should never aim below " + whale.floorY() + ", aimed at " + whale.getMoveControl().getWantedY());
        helper.succeed();
    }

    @GameTest(template = "arena")
    public static void gummySwetsHealAerwhales(GameTestHelper helper) {
        if (deepAetherWhales(helper)) {
            return;
        }
        Player owner = helper.makeMockPlayer(GameType.SURVIVAL);
        TamedAerwhale whale = tamed(helper, owner);
        whale.setNoAi(true);
        float max = whale.getMaxHealth();
        whale.setHealth(max * 0.1F);
        Player stranger = helper.makeMockPlayer(GameType.SURVIVAL);
        stranger.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(AetherItems.BLUE_GUMMY_SWET.get()));
        stranger.interactOn(whale, InteractionHand.MAIN_HAND);
        helper.assertTrue(Math.abs(whale.getHealth() - max * 0.1F) < 0.01, "a stranger can't feed it");

        owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(AetherItems.BLUE_GUMMY_SWET.get(), 2));
        owner.interactOn(whale, InteractionHand.MAIN_HAND);
        helper.assertTrue(Math.abs(whale.getHealth() - max * 0.35F) < 0.01,
                "a blue swet should heal a quarter, at " + whale.getHealth() / max);
        owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(AetherItems.GOLDEN_GUMMY_SWET.get()));
        owner.interactOn(whale, InteractionHand.MAIN_HAND);
        helper.assertTrue(Math.abs(whale.getHealth() - max * 0.85F) < 0.01,
                "a golden swet should heal half, at " + whale.getHealth() / max);
        whale.setHealth(max);
        owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(AetherItems.GOLDEN_GUMMY_SWET.get()));
        owner.interactOn(whale, InteractionHand.MAIN_HAND);
        helper.assertTrue(owner.getMainHandItem().getCount() == 1, "a healthy whale shouldn't use up a swet");
        helper.succeed();
    }

    @GameTest(template = "arena", timeoutTicks = 400)
    public static void gummySwetsTemptFromAfar(GameTestHelper helper) {
        if (deepAetherWhales(helper)) {
            return;
        }
        Player owner = ownerInLevel(helper, new Vec3(4, 2, 4));
        owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(AetherItems.GOLDEN_GUMMY_SWET.get()));
        TamedAerwhale whale = tamed(helper, owner);
        whale.setPos(helper.absoluteVec(new Vec3(22, 10, 22)));
        whale.setCommand(AerwhaleCommand.STAY);
        double start = whale.distanceTo(owner);
        helper.succeedWhen(() -> {
            helper.assertTrue(whale.distanceTo(owner) < 10,
                    "should come to the swet even when told to stay: " + start + " -> " + whale.distanceTo(owner));
            removeFromLevel(helper, owner);
        });
    }

    private static double horizontal(Vec3 a, Vec3 b) {
        return Math.sqrt((a.x - b.x) * (a.x - b.x) + (a.z - b.z) * (a.z - b.z));
    }

    @GameTest(template = "arena", timeoutTicks = 300)
    public static void wanderingAerwhalesMove(GameTestHelper helper) {
        if (deepAetherWhales(helper)) {
            return;
        }
        Player owner = helper.makeMockPlayer(GameType.SURVIVAL);
        TamedAerwhale whale = tamed(helper, owner);
        whale.setPos(helper.absoluteVec(new Vec3(24, 3, 24)));
        whale.setCommand(AerwhaleCommand.WANDER);
        Vec3 start = whale.position();
        helper.succeedWhen(() -> helper.assertTrue(whale.position().distanceTo(start) > 6,
                "should cruise off; moved " + whale.position().distanceTo(start)));
    }

    @GameTest(template = "arena")
    public static void saddledAerwhalesCarryAnyone(GameTestHelper helper) {
        if (deepAetherWhales(helper)) {
            return;
        }
        Player owner = helper.makeMockPlayer(GameType.SURVIVAL);
        TamedAerwhale whale = tamed(helper, owner);
        Player stranger = helper.makeMockPlayer(GameType.SURVIVAL);
        stranger.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.SADDLE));
        stranger.interactOn(whale, InteractionHand.MAIN_HAND);
        helper.assertFalse(whale.isSaddled(), "a stranger can't saddle someone else's aerwhale");

        owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.SADDLE));
        owner.interactOn(whale, InteractionHand.MAIN_HAND);
        helper.assertTrue(whale.isSaddled(), "the owner's saddle should go on");
        helper.assertTrue(owner.getMainHandItem().isEmpty(), "the saddle should be used up");

        stranger.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        stranger.interactOn(whale, InteractionHand.MAIN_HAND);
        helper.assertTrue(stranger.getVehicle() == whale, "anyone can ride a saddled aerwhale");
        owner.interactOn(whale, InteractionHand.MAIN_HAND);
        helper.assertTrue(owner.getVehicle() == whale, "the owner should be riding too");
        helper.assertTrue(whale.getControllingPassenger() == stranger, "the first aboard should steer");
        helper.succeed();
    }

    @GameTest(template = "arena")
    public static void unsaddledAerwhalesCarryOneWithoutReins(GameTestHelper helper) {
        if (deepAetherWhales(helper)) {
            return;
        }
        Player owner = helper.makeMockPlayer(GameType.SURVIVAL);
        TamedAerwhale whale = tamed(helper, owner);
        owner.interactOn(whale, InteractionHand.MAIN_HAND);
        helper.assertTrue(owner.getVehicle() == whale, "an unsaddled aerwhale can be ridden bareback");
        helper.assertTrue(whale.getControllingPassenger() == null, "but not steered");
        Player second = helper.makeMockPlayer(GameType.SURVIVAL);
        second.interactOn(whale, InteractionHand.MAIN_HAND);
        helper.assertFalse(second.isPassenger(), "bareback, it carries only one");
        helper.assertFalse(whale.canJump(), "nor dives");
        whale.inventory().setItem(TamedAerwhale.SADDLE_SLOT, new ItemStack(Items.SADDLE));
        helper.assertTrue(whale.getControllingPassenger() == owner, "a saddle gives the rider the reins");
        helper.succeed();
    }

    @GameTest(template = "arena")
    public static void fourRidersAtMost(GameTestHelper helper) {
        if (deepAetherWhales(helper)) {
            return;
        }
        Player owner = helper.makeMockPlayer(GameType.SURVIVAL);
        TamedAerwhale whale = tamed(helper, owner);
        whale.equipSaddle(new ItemStack(Items.SADDLE), null);
        List<Player> riders = List.of(owner, helper.makeMockPlayer(GameType.SURVIVAL),
                helper.makeMockPlayer(GameType.SURVIVAL), helper.makeMockPlayer(GameType.SURVIVAL));
        for (Player rider : riders) {
            rider.interactOn(whale, InteractionHand.MAIN_HAND);
            helper.assertTrue(rider.getVehicle() == whale, "seat " + riders.indexOf(rider) + " should take a rider");
        }
        Player fifth = helper.makeMockPlayer(GameType.SURVIVAL);
        fifth.interactOn(whale, InteractionHand.MAIN_HAND);
        helper.assertFalse(fifth.isPassenger(), "there are only four seats");
        helper.assertTrue(whale.getControllingPassenger() == owner, "the first aboard should steer");
        owner.stopRiding();
        helper.assertTrue(whale.getControllingPassenger() == riders.get(1), "the next aboard should take over");
        helper.succeed();
    }

    @GameTest(template = "arena")
    public static void saddledBacksAreSolid(GameTestHelper helper) {
        if (deepAetherWhales(helper)) {
            return;
        }
        Player owner = helper.makeMockPlayer(GameType.SURVIVAL);
        TamedAerwhale saddled = tamed(helper, owner);
        saddled.equipSaddle(new ItemStack(Items.SADDLE), null);
        TamedAerwhale bare = tamed(helper, owner);
        bare.setPos(helper.absoluteVec(SPOT.add(-12, 0, 0)));
        bare.setCommand(AerwhaleCommand.STAY);
        helper.runAfterDelay(2, () -> {
            ArmorStand onSaddled = standOnDeck(helper, saddled, 1.5);
            ArmorStand onBare = standOnDeck(helper, bare, 1.5);
            double top = deckTop(saddled);
            helper.succeedWhen(() -> {
                helper.assertTrue(onSaddled.onGround() && Math.abs(onSaddled.getY() - top) < 0.1,
                        "should rest on the saddled back at " + top + ", at " + onSaddled.getY());
                helper.assertTrue(onBare.getY() < deckTop(bare) - 2, "should fall through an unsaddled back");
            });
        });
    }

    @GameTest(template = "arena", timeoutTicks = 200)
    public static void deckCarriesWhatStandsOnIt(GameTestHelper helper) {
        if (deepAetherWhales(helper)) {
            return;
        }
        Player owner = helper.makeMockPlayer(GameType.SURVIVAL);
        TamedAerwhale whale = tamed(helper, owner);
        whale.equipSaddle(new ItemStack(Items.SADDLE), null);
        whale.setNoAi(true);
        helper.runAfterDelay(2, () -> {
            ArmorStand stand = standOnDeck(helper, whale, 0.1);
            helper.runAfterDelay(10, () -> {
                helper.assertTrue(stand.onGround(), "should have settled on the deck");
                Vec3 offset = stand.position().subtract(whale.position());
                Vec3 start = whale.position();
                for (int tick = 1; tick <= 20; tick++) {
                    helper.runAfterDelay(tick, () -> whale.setPos(whale.position().add(0.25, 0.05, 0.1)));
                }
                helper.runAfterDelay(30, () -> {
                    helper.assertTrue(whale.distanceToSqr(start) > 25, "the whale should have moved");
                    Vec3 now = stand.position().subtract(whale.position());
                    helper.assertTrue(now.distanceTo(offset) < 0.2,
                            "should keep its place on the deck: " + offset + " became " + now);
                    helper.succeed();
                });
            });
        });
    }

    /** An armor stand dropped onto the middle of {@code whale}'s second deck slab from {@code height} above it. */
    private static ArmorStand standOnDeck(GameTestHelper helper, TamedAerwhale whale, double height) {
        AABB slab = whale.getParts()[1].getBoundingBox();
        ArmorStand stand = new ArmorStand(helper.getLevel(), slab.getCenter().x, slab.maxY + height, slab.getCenter().z);
        helper.getLevel().addFreshEntity(stand);
        return stand;
    }

    private static double deckTop(TamedAerwhale whale) {
        return whale.getParts()[1].getBoundingBox().maxY;
    }

    @GameTest(template = "arena", timeoutTicks = 600)
    public static void enchantedBerriesBreedCalves(GameTestHelper helper) {
        if (deepAetherWhales(helper)) {
            return;
        }
        Player owner = helper.makeMockPlayer(GameType.SURVIVAL);
        TamedAerwhale first = tamed(helper, owner);
        TamedAerwhale second = tamed(helper, owner);
        second.setPos(helper.absoluteVec(SPOT.add(8, 0, 0)));
        ItemStack food = breedingFood();
        for (TamedAerwhale whale : List.of(first, second)) {
            owner.setItemInHand(InteractionHand.MAIN_HAND, food.copy());
            owner.interactOn(whale, InteractionHand.MAIN_HAND);
            helper.assertTrue(whale.isInLove(), food + " should put it in love");
        }
        helper.succeedWhen(() -> {
            List<TamedAerwhale> calves = tamedNear(helper).stream().filter(TamedAerwhale::isBaby).toList();
            helper.assertTrue(calves.size() == 1, "waiting for a calf; parents " + first.distanceTo(second)
                    + " apart, in love " + first.isInLove() + "/" + second.isInLove() + ", whales " + tamedNear(helper).size()
                    + ", at " + first.position() + " / " + second.position());
            TamedAerwhale calf = calves.getFirst();
            helper.assertTrue(calf.isOwnedBy(owner), "the calf should belong to the breeder");
            helper.assertTrue(calf.getBbWidth() < first.getBbWidth(), "a calf should be smaller");
            helper.assertFalse(first.isInLove() || second.isInLove(), "the parents should be done courting");
            owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.SADDLE));
            owner.interactOn(calf, InteractionHand.MAIN_HAND);
            helper.assertFalse(calf.isSaddled(), "calves can't be saddled");
        });
    }

    @GameTest(template = "arena")
    public static void tamedAerwhalesSaveAndLoad(GameTestHelper helper) {
        if (deepAetherWhales(helper)) {
            return;
        }
        Player owner = helper.makeMockPlayer(GameType.SURVIVAL);
        TamedAerwhale whale = tamed(helper, owner);
        whale.equipSaddle(new ItemStack(Items.SADDLE), null);
        whale.setCommand(AerwhaleCommand.WANDER);
        owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.CHEST));
        owner.interactOn(whale, InteractionHand.MAIN_HAND);
        whale.inventory().setItem(5, new ItemStack(Items.DIAMOND, 7));
        CompoundTag tag = whale.saveWithoutId(new CompoundTag());
        TamedAerwhale copy = AARegistries.TAMED_AERWHALE.get().create(helper.getLevel());
        helper.assertTrue(copy != null, "should create");
        copy.load(tag);
        helper.assertTrue(copy.isOwnedBy(owner), "owner should survive saving");
        helper.assertTrue(copy.isSaddled(), "saddle should survive saving");
        helper.assertTrue(copy.command() == AerwhaleCommand.WANDER, "command should survive saving");
        helper.assertTrue(copy.hasChest(), "the saddlebag should survive saving");
        helper.assertTrue(copy.inventory().getItem(5).is(Items.DIAMOND) && copy.inventory().getItem(5).getCount() == 7,
                "the saddlebag's contents should survive saving, got " + copy.inventory().getItem(5));
        helper.assertTrue(copy.isNoGravity(), "it must stay weightless, or riders get kicked for flying");
        helper.succeed();
    }

    /** Before the saddle had a slot, a saddled whale saved a flag and the saddle's id. */
    @GameTest(template = "arena")
    public static void oldSaddlesLoadIntoTheSaddleSlot(GameTestHelper helper) {
        if (deepAetherWhales(helper)) {
            return;
        }
        Player owner = helper.makeMockPlayer(GameType.SURVIVAL);
        TamedAerwhale whale = tamed(helper, owner);
        CompoundTag tag = whale.saveWithoutId(new CompoundTag());
        tag.putBoolean("Saddled", true);
        tag.putString("Saddle", "minecraft:saddle");
        whale.load(tag);
        helper.assertTrue(whale.isSaddled(), "an old saddle should still be on");
        helper.assertTrue(whale.inventory().getItem(TamedAerwhale.SADDLE_SLOT).is(Items.SADDLE),
                "and in the saddle slot");
        whale.inventory().setItem(TamedAerwhale.SADDLE_SLOT, ItemStack.EMPTY);
        helper.assertFalse(whale.isSaddled(), "emptying the slot should unsaddle it");
        helper.succeed();
    }

    @GameTest(template = "arena")
    public static void chestsAndTheirContentsComeBackWhenAerwhalesDie(GameTestHelper helper) {
        if (deepAetherWhales(helper)) {
            return;
        }
        Player owner = helper.makeMockPlayer(GameType.SURVIVAL);
        TamedAerwhale whale = tamed(helper, owner);
        Player stranger = helper.makeMockPlayer(GameType.SURVIVAL);
        stranger.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.CHEST));
        stranger.interactOn(whale, InteractionHand.MAIN_HAND);
        helper.assertFalse(whale.hasChest(), "a stranger can't give someone else's aerwhale a chest");
        owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.CHEST));
        owner.interactOn(whale, InteractionHand.MAIN_HAND);
        helper.assertTrue(whale.hasChest(), "the owner's chest should go on");
        helper.assertTrue(owner.getMainHandItem().isEmpty(), "the chest should be used up");
        whale.inventory().setItem(TamedAerwhale.INVENTORY_SIZE - 1, new ItemStack(Items.DIAMOND, 3));
        whale.kill();
        List<ItemStack> drops = helper.getLevel()
                .getEntitiesOfClass(ItemEntity.class, whale.getBoundingBox().inflate(4)).stream()
                .map(ItemEntity::getItem).toList();
        helper.assertTrue(drops.stream().anyMatch(stack -> stack.is(Items.CHEST)), "the chest should drop, got " + drops);
        helper.assertTrue(drops.stream().anyMatch(stack -> stack.is(Items.DIAMOND) && stack.getCount() == 3),
                "the saddlebag's contents should drop, got " + drops);
        helper.succeed();
    }

    @GameTest(template = "arena")
    public static void spawnEggsMakeCalves(GameTestHelper helper) {
        if (deepAetherWhales(helper)) {
            return;
        }
        Aerwhale wild = helper.spawn(AetherEntityTypes.AERWHALE.get(), SPOT);
        wild.setNoAi(true);
        Player owner = helper.makeMockPlayer(GameType.SURVIVAL);
        TamedAerwhale tamed = tamed(helper, owner);
        tamed.setPos(helper.absoluteVec(SPOT.add(-12, 0, 0)));
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(AetherItems.AERWHALE_SPAWN_EGG.get(), 2));
        player.interactOn(wild, InteractionHand.MAIN_HAND);
        player.interactOn(tamed, InteractionHand.MAIN_HAND);
        List<TamedAerwhale> calves = tamedNear(helper).stream().filter(TamedAerwhale::isBaby).toList();
        helper.assertTrue(calves.size() == 2, "expected two calves, found " + calves.size());
        helper.assertTrue(calves.stream().anyMatch(calf -> calf.getOwnerUUID() == null), "a wild parent's calf is wild");
        helper.assertTrue(calves.stream().anyMatch(calf -> calf.isOwnedBy(owner)),
                "a tamed parent's calf belongs to its owner");
        helper.assertTrue(player.getMainHandItem().isEmpty(), "each calf should use an egg");
        helper.succeed();
    }

    @GameTest(template = "arena", timeoutTicks = 100)
    public static void wildCalvesGrowUpWild(GameTestHelper helper) {
        if (deepAetherWhales(helper)) {
            return;
        }
        TamedAerwhale calf = AARegistries.TAMED_AERWHALE.get().create(helper.getLevel());
        helper.assertTrue(calf != null, "should create");
        CompoundTag tag = calf.saveWithoutId(new CompoundTag());
        tag.putInt("Age", -5);
        calf.load(tag);
        calf.moveTo(helper.absoluteVec(SPOT));
        // a wild calf may despawn like any wild aerwhale, and the test server has no player nearby to keep it
        calf.setPersistenceRequired();
        helper.getLevel().addFreshEntity(calf);
        helper.assertTrue(calf.isBaby(), "should start as a calf");
        helper.succeedWhen(() -> {
            helper.assertFalse(calf.isAlive(), "the calf should have grown up");
            List<Aerwhale> grown = helper.getLevel().getEntitiesOfClass(Aerwhale.class, calf.getBoundingBox().inflate(6),
                    whale -> !(whale instanceof TamedAerwhale));
            helper.assertTrue(grown.size() == 1, "it should be a wild aerwhale now, found " + grown);
        });
    }

    @GameTest(template = "arena", timeoutTicks = 300)
    public static void blueBerriesTemptWildAerwhales(GameTestHelper helper) {
        if (deepAetherWhales(helper)) {
            return;
        }
        Player player = ownerInLevel(helper, new Vec3(4, 2, 4));
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(AetherItems.BLUE_BERRY.get()));
        Aerwhale wild = helper.spawn(AetherEntityTypes.AERWHALE.get(), new Vec3(13, 8, 13));
        double start = wild.distanceTo(player);
        helper.succeedWhen(() -> {
            helper.assertTrue(wild.distanceTo(player) < 10,
                    "should come to the berries: " + start + " -> " + wild.distanceTo(player));
            removeFromLevel(helper, player);
        });
    }

    @GameTest(template = "arena")
    public static void saddlesComeBackWhenAerwhalesDie(GameTestHelper helper) {
        if (deepAetherWhales(helper)) {
            return;
        }
        Player owner = helper.makeMockPlayer(GameType.SURVIVAL);
        TamedAerwhale whale = tamed(helper, owner);
        owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.SADDLE));
        owner.interactOn(whale, InteractionHand.MAIN_HAND);
        whale.kill();
        List<ItemEntity> drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, whale.getBoundingBox().inflate(4));
        long saddles = drops.stream().filter(drop -> TamedAerwhale.isSaddleItem(drop.getItem())).count();
        helper.assertTrue(saddles == 1, "expected one saddle back, got " + saddles);
        helper.assertTrue(drops.stream().anyMatch(drop -> drop.getItem().is(Items.SADDLE)), "it should be the saddle it wore");
        helper.succeed();
    }

    /** Deep Aether's Aerwhale Saddle fits a tamed whale too, and comes back as itself. */
    @GameTest(template = "arena")
    public static void deepAetherSaddlesFitTamedAerwhales(GameTestHelper helper) {
        if (!ModList.get().isLoaded("deep_aether") || deepAetherWhales(helper)) {
            helper.succeed();
            return;
        }
        Player owner = helper.makeMockPlayer(GameType.SURVIVAL);
        TamedAerwhale whale = tamed(helper, owner);
        Item saddle = BuiltInRegistries.ITEM.get(DEEP_AETHER_SADDLE);
        owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(saddle));
        owner.interactOn(whale, InteractionHand.MAIN_HAND);
        helper.assertTrue(whale.isSaddled(), "Deep Aether's saddle should go on");
        owner.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        owner.interactOn(whale, InteractionHand.MAIN_HAND);
        helper.assertTrue(owner.getVehicle() == whale, "the owner should be riding");
        Player stranger = helper.makeMockPlayer(GameType.SURVIVAL);
        helper.assertTrue(stranger.startRiding(whale), "a second rider should fit");
        stranger.stopRiding();
        owner.stopRiding();
        whale.kill();
        List<ItemEntity> drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, whale.getBoundingBox().inflate(4));
        helper.assertTrue(drops.size() == 1 && drops.getFirst().getItem().is(saddle),
                "expected Deep Aether's saddle back alone, got " + drops.stream().map(ItemEntity::getItem).toList());
        helper.succeed();
    }

    /** With our aerwhales in play, Deep Aether's patch to the Aether's aerwhale is gone. */
    @GameTest(template = "arena")
    public static void deepAetherAerwhalesAreSwitchedOff(GameTestHelper helper) throws ReflectiveOperationException {
        if (!AerwhaleMode.deepAetherCancelled()) {
            helper.succeed();
            return;
        }
        Class<?> saddleable = Class.forName("io.github.razordevs.deep_aether.entity.AerwhaleSaddleable");
        helper.assertFalse(saddleable.isAssignableFrom(Aerwhale.class), "Deep Aether's aerwhale mixin still applied");
        helper.assertFalse(Container.class.isAssignableFrom(Aerwhale.class), "Deep Aether's aerwhale chest still applied");
        helper.succeed();
    }

    /** An aerwhale saved with Deep Aether's saddle and chest drops both once Deep Aether's aerwhales are off. */
    @GameTest(template = "arena")
    public static void deepAetherCargoDropsOnLoad(GameTestHelper helper) {
        if (!AerwhaleMode.deepAetherCancelled()) {
            helper.succeed();
            return;
        }
        Aerwhale whale = helper.spawn(AetherEntityTypes.AERWHALE.get(), SPOT);
        whale.setNoAi(true);
        CompoundTag tag = whale.saveWithoutId(new CompoundTag());
        tag.putBoolean("isSaddled", true);
        NonNullList<ItemStack> chest = NonNullList.withSize(27, ItemStack.EMPTY);
        chest.set(4, new ItemStack(Items.DIAMOND, 5));
        ContainerHelper.saveAllItems(tag, chest, helper.getLevel().registryAccess());
        whale.load(tag);
        helper.succeedWhen(() -> {
            List<ItemStack> drops = helper.getLevel()
                    .getEntitiesOfClass(ItemEntity.class, whale.getBoundingBox().inflate(4)).stream()
                    .map(ItemEntity::getItem).toList();
            helper.assertTrue(drops.stream().anyMatch(stack -> stack.is(Items.DIAMOND) && stack.getCount() == 5),
                    "the chest's diamonds should drop, got " + drops);
            helper.assertTrue(drops.stream().anyMatch(TamedAerwhale::isSaddleItem), "the saddle should drop, got " + drops);
        });
    }

    /** With Deep Aether's aerwhales in play, a tamed one turns wild on its next tick and keeps its saddle as theirs. */
    @GameTest(template = "arena")
    public static void tamedAerwhalesTurnWildUnderDeepAether(GameTestHelper helper) {
        if (!AerwhaleMode.deepAether()) {
            helper.succeed();
            return;
        }
        TamedAerwhale whale = AARegistries.TAMED_AERWHALE.get().create(helper.getLevel());
        helper.assertTrue(whale != null, "should create");
        whale.moveTo(helper.absoluteVec(SPOT));
        whale.equipSaddle(new ItemStack(Items.SADDLE), null);
        helper.getLevel().addFreshEntity(whale);
        helper.succeedWhen(() -> {
            helper.assertFalse(whale.isAlive(), "the tamed aerwhale should be gone");
            List<Aerwhale> wild = helper.getLevel().getEntitiesOfClass(Aerwhale.class, whale.getBoundingBox().inflate(4));
            helper.assertTrue(wild.size() == 1 && !(wild.getFirst() instanceof TamedAerwhale),
                    "expected one wild aerwhale, got " + wild);
            helper.assertTrue(deepAetherSaddled(wild.getFirst()), "it should keep its saddle");
        });
    }

    private static boolean deepAetherSaddled(Aerwhale whale) {
        try {
            return whale.getClass().getMethod("isSaddled").invoke(whale) instanceof Boolean saddled && saddled;
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    /** Whether Deep Aether's aerwhales are in play, which turns ours wild; a test of ours then just passes. */
    private static boolean deepAetherWhales(GameTestHelper helper) {
        if (AerwhaleMode.deepAether()) {
            helper.succeed();
        }
        return AerwhaleMode.deepAether();
    }

    private static TamedAerwhale tamed(GameTestHelper helper, Player owner) {
        Aerwhale wild = helper.spawn(AetherEntityTypes.AERWHALE.get(), SPOT);
        TamedAerwhale whale = TamedAerwhale.tame(wild, owner);
        helper.assertTrue(whale != null, "taming should succeed");
        return whale;
    }

    /**
     * A player the level knows about, so a whale can find it as its owner. The game test's own mock server player logs
     * in through the player list, where other mods' login packets fail on its fake connection; a fake player added to
     * the level directly skips that.
     */
    private static Player ownerInLevel(GameTestHelper helper, Vec3 at) {
        FakePlayer player = FakePlayerFactory.get(helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "aerwhale-owner"));
        player.moveTo(helper.absoluteVec(at));
        helper.getLevel().addNewPlayer(player);
        return player;
    }

    private static void removeFromLevel(GameTestHelper helper, Player player) {
        if (player instanceof FakePlayer fake) {
            helper.getLevel().removePlayerImmediately(fake, Entity.RemovalReason.DISCARDED);
        }
    }

    private static List<TamedAerwhale> tamedNear(GameTestHelper helper) {
        AABB box = new AABB(helper.absoluteVec(Vec3.ZERO), helper.absoluteVec(new Vec3(48, 40, 48)));
        return helper.getLevel().getEntitiesOfClass(TamedAerwhale.class, box);
    }

    private static ItemStack breedingFood() {
        return new ItemStack(AetherItems.ENCHANTED_BERRY.get());
    }

    private AerwhaleTests() {
    }
}
