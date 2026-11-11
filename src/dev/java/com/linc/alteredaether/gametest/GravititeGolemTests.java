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
import com.linc.alteredaether.entity.golem.GolemMode;
import com.linc.alteredaether.entity.golem.GolemNetworks;
import com.linc.alteredaether.entity.golem.GolemSorter;
import com.linc.alteredaether.entity.golem.GolemStorage;
import com.linc.alteredaether.entity.golem.GravititeGolem;
import com.linc.alteredaether.registry.AARegistries;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/**
 * Gravitite golems: built from their blocks, linked with their pebble (storage doesn't open), sorting without losing
 * or making an item, safe when an item is taken mid-flight, emptying hoppers, refusing machines, following pins,
 * linking golems within twice their reach, pushing attackers off, resting under redstone, going home, and dropping
 * their blocks.
 */
@GameTestHolder(AlteredAether.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GravititeGolemTests {
    private static final BlockPos HOME = new BlockPos(16, 2, 16);

    private GravititeGolemTests() {
    }

    /** A stone floor under the middle of the arena. */
    private static void floor(GameTestHelper helper) {
        for (int x = 1; x < 40; x++) {
            for (int z = 1; z < 40; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            }
        }
    }

    private static GravititeGolem golem(GameTestHelper helper, BlockPos at) {
        return helper.spawn(AARegistries.GRAVITITE_GOLEM.get(), Vec3.atBottomCenterOf(at));
    }

    private static FakePlayer player(GameTestHelper helper, String name) {
        FakePlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
        player.moveTo(helper.absoluteVec(new Vec3(14, 2, 14)));
        helper.getLevel().addNewPlayer(player);
        return player;
    }

    private static void leave(GameTestHelper helper, FakePlayer player) {
        helper.getLevel().removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
    }

    private static BaseContainerBlockEntity chest(GameTestHelper helper, BlockPos pos, ItemStack... stacks) {
        helper.setBlock(pos, Blocks.CHEST);
        BaseContainerBlockEntity chest = (BaseContainerBlockEntity) helper.getBlockEntity(pos);
        for (int i = 0; i < stacks.length; i++) {
            chest.setItem(i, stacks[i]);
        }
        return chest;
    }

    private static int count(BaseContainerBlockEntity container, Item item) {
        int n = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            if (container.getItem(slot).is(item)) {
                n += container.getItem(slot).getCount();
            }
        }
        return n;
    }

    /** Takes the golem's pebble with an empty hand and holds it. */
    private static ItemStack takePebble(GravititeGolem golem, FakePlayer player) {
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        golem.interact(player, InteractionHand.MAIN_HAND);
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(AARegistries.STORAGE_PEBBLE.get())) {
                ItemStack pebble = stack.copy();
                player.getInventory().setItem(slot, ItemStack.EMPTY);
                player.setItemInHand(InteractionHand.MAIN_HAND, pebble);
                return pebble;
            }
        }
        throw new IllegalStateException("the golem gave no pebble");
    }

    /** Uses what the player holds on {@code pos} the way a right click does. */
    private static void use(GameTestHelper helper, FakePlayer player, BlockPos pos, Direction face) {
        BlockPos abs = helper.absolutePos(pos);
        player.gameMode.useItemOn(player, helper.getLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(abs).relative(face, 0.5), face, abs, false));
    }

    private static GolemNetworks.Net net(GameTestHelper helper, GravititeGolem golem) {
        GolemNetworks.Net net = GolemNetworks.get(helper.getLevel()).netOf(golem.getUUID());
        helper.assertTrue(net != null, "the golem has no network");
        return net;
    }

    @GameTest(template = "arena", timeoutTicks = 100)
    public static void placingTheHeadBuildsAGolem(GameTestHelper helper) {
        floor(helper);
        BlockPos base = new BlockPos(10, 2, 10);
        helper.setBlock(base, AetherBlocks.HOLYSTONE.get());
        helper.setBlock(base.above(), AetherBlocks.ENCHANTED_GRAVITITE.get());
        FakePlayer player = player(helper, "golem-builder");
        ItemStack pumpkin = new ItemStack(Items.CARVED_PUMPKIN);
        player.setItemInHand(InteractionHand.MAIN_HAND, pumpkin);
        BlockPos top = helper.absolutePos(base.above());
        pumpkin.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(top).add(0, 0.5, 0), Direction.UP, top, false)));
        helper.startSequence().thenWaitUntil(() -> {
            helper.assertEntityPresent(AARegistries.GRAVITITE_GOLEM.get(), base);
            helper.assertBlockPresent(Blocks.AIR, base);
            helper.assertBlockPresent(Blocks.AIR, base.above());
            helper.assertBlockPresent(Blocks.AIR, base.above(2));
            GravititeGolem golem = helper.getLevel().getEntitiesOfClass(GravititeGolem.class,
                    new net.minecraft.world.phys.AABB(helper.absolutePos(base)).inflate(1)).get(0);
            helper.assertTrue(helper.absolutePos(base).equals(golem.home()), "home isn't where it was built");
        }).thenExecute(() -> leave(helper, player)).thenSucceed();
    }

    @GameTest(template = "arena", timeoutTicks = 600)
    public static void linksWithoutOpeningAndSortsTidily(GameTestHelper helper) {
        floor(helper);
        GravititeGolem golem = golem(helper, HOME);
        BlockPos a = new BlockPos(12, 2, 12), b = new BlockPos(20, 2, 12), c = new BlockPos(12, 2, 20);
        BaseContainerBlockEntity mixed = chest(helper, a, new ItemStack(Items.COBBLESTONE, 64), new ItemStack(Items.COBBLESTONE, 64),
                new ItemStack(Items.COBBLESTONE, 64), new ItemStack(Items.OAK_LOG, 64), new ItemStack(Items.OAK_LOG, 30));
        BaseContainerBlockEntity empty1 = chest(helper, b);
        BaseContainerBlockEntity empty2 = chest(helper, c);
        FakePlayer player = player(helper, "golem-linker");
        helper.startSequence().thenIdle(5).thenExecute(() -> {
            takePebble(golem, player);
            for (BlockPos pos : new BlockPos[]{a, b, c}) {
                use(helper, player, pos, Direction.UP);
                helper.assertTrue(player.containerMenu == player.inventoryMenu, "the pebble opened the chest");
            }
            helper.assertTrue(net(helper, golem).links().size() == 3, "not all three linked");
        }).thenWaitUntil(() -> {
            int cobble = count(mixed, Items.COBBLESTONE) + count(empty1, Items.COBBLESTONE) + count(empty2, Items.COBBLESTONE);
            int logs = count(mixed, Items.OAK_LOG) + count(empty1, Items.OAK_LOG) + count(empty2, Items.OAK_LOG);
            helper.assertTrue(cobble == 192 && logs == 94, "items made or lost: " + cobble + " cobble, " + logs + " logs");
            helper.assertTrue(count(mixed, Items.OAK_LOG) == 0, "logs still mixed in with the cobblestone");
            helper.assertTrue(count(empty1, Items.OAK_LOG) == 94 || count(empty2, Items.OAK_LOG) == 94, "logs not together");
            helper.assertTrue(GolemSorter.report(helper.getLevel(), net(helper, golem)).status() == GolemSorter.Status.SORTED,
                    "not done yet");
        }).thenExecute(() -> leave(helper, player)).thenSucceed();
    }

    @GameTest(template = "arena", timeoutTicks = 600)
    public static void compactPacksIntoFewerChests(GameTestHelper helper) {
        floor(helper);
        GravititeGolem golem = golem(helper, HOME);
        BlockPos a = new BlockPos(12, 2, 12), b = new BlockPos(20, 2, 12);
        BaseContainerBlockEntity first = chest(helper, a, new ItemStack(Items.COBBLESTONE, 20), new ItemStack(Items.OAK_LOG, 5));
        BaseContainerBlockEntity second = chest(helper, b, new ItemStack(Items.COBBLESTONE, 30), new ItemStack(Items.OAK_LOG, 40));
        helper.startSequence().thenIdle(5).thenExecute(() -> {
            ServerLevel level = helper.getLevel();
            GolemNetworks networks = GolemNetworks.get(level);
            GolemNetworks.Net net = net(helper, golem);
            networks.setMode(net, GolemMode.COMPACT);
            for (BlockPos pos : new BlockPos[]{a, b}) {
                networks.toggleLink(net, helper.absolutePos(pos), null, p -> GolemStorage.inventoryKey(level, p));
            }
        }).thenWaitUntil(() -> {
            int used = (first.isEmpty() ? 0 : 1) + (second.isEmpty() ? 0 : 1);
            helper.assertTrue(used == 1, "still spread over two chests");
            helper.assertTrue(count(first, Items.COBBLESTONE) + count(second, Items.COBBLESTONE) == 50, "cobble made or lost");
            helper.assertTrue(count(first, Items.OAK_LOG) + count(second, Items.OAK_LOG) == 45, "logs made or lost");
        }).thenSucceed();
    }

    @GameTest(template = "arena", timeoutTicks = 600)
    public static void takingTheItemMidFlightLosesNothing(GameTestHelper helper) {
        floor(helper);
        GravititeGolem golem = golem(helper, HOME);
        BlockPos a = new BlockPos(12, 2, 12), b = new BlockPos(20, 2, 12);
        BaseContainerBlockEntity source = chest(helper, a, new ItemStack(Items.COBBLESTONE, 64), new ItemStack(Items.COBBLESTONE, 64),
                new ItemStack(Items.DIAMOND, 7));
        BaseContainerBlockEntity target = chest(helper, b, new ItemStack(Items.DIAMOND, 64), new ItemStack(Items.DIAMOND, 64));
        boolean[] taken = {false};
        helper.startSequence().thenIdle(5).thenExecute(() -> {
            ServerLevel level = helper.getLevel();
            GolemNetworks networks = GolemNetworks.get(level);
            GolemNetworks.Net net = net(helper, golem);
            for (BlockPos pos : new BlockPos[]{a, b}) {
                networks.toggleLink(net, helper.absolutePos(pos), null, p -> GolemStorage.inventoryKey(level, p));
            }
        }).thenWaitUntil(() -> {
            // the diamonds are in the air (the golem is concentrating) but still in their chest
            helper.assertTrue(golem.state() == GravititeGolem.CONCENTRATING, "no flight yet");
            helper.assertTrue(count(source, Items.DIAMOND) == 7, "the diamonds left before landing");
        }).thenExecute(() -> {
            source.setItem(2, ItemStack.EMPTY);
            taken[0] = true;
        }).thenIdle(60).thenExecute(() -> {
            helper.assertTrue(count(target, Items.DIAMOND) == 128, "diamonds appeared from nowhere: " + count(target, Items.DIAMOND));
            helper.assertTrue(count(source, Items.DIAMOND) == 0, "diamonds came back");
            helper.assertTrue(helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                    new net.minecraft.world.phys.AABB(helper.absolutePos(a)).inflate(4)).isEmpty(), "something was dropped");
        }).thenSucceed();
    }

    @GameTest(template = "arena", timeoutTicks = 600)
    public static void hoppersAreEmptiedAndFurnacesRefused(GameTestHelper helper) {
        floor(helper);
        GravititeGolem golem = golem(helper, HOME);
        BlockPos hopperPos = new BlockPos(12, 3, 12), chestPos = new BlockPos(20, 2, 12), furnacePos = new BlockPos(12, 2, 20);
        helper.setBlock(hopperPos, Blocks.HOPPER);
        BaseContainerBlockEntity hopper = (BaseContainerBlockEntity) helper.getBlockEntity(hopperPos);
        hopper.setItem(0, new ItemStack(Items.IRON_INGOT, 12));
        BaseContainerBlockEntity chest = chest(helper, chestPos, new ItemStack(Items.IRON_INGOT, 50));
        helper.setBlock(furnacePos, Blocks.FURNACE);
        FakePlayer player = player(helper, "golem-hopper");
        helper.startSequence().thenIdle(5).thenExecute(() -> {
            ServerLevel level = helper.getLevel();
            helper.assertTrue(GolemStorage.role(level, helper.absolutePos(furnacePos)) == GolemStorage.Role.NONE, "a furnace is linkable");
            helper.assertTrue(GolemStorage.role(level, helper.absolutePos(hopperPos)) == GolemStorage.Role.SOURCE, "a hopper isn't empty-only");
            takePebble(golem, player);
            use(helper, player, hopperPos, Direction.UP);
            use(helper, player, chestPos, Direction.UP);
            use(helper, player, furnacePos, Direction.UP);
            helper.assertTrue(player.containerMenu == player.inventoryMenu, "the pebble opened something");
            helper.assertTrue(net(helper, golem).links().size() == 2, "the furnace was linked, or something else wasn't");
        }).thenWaitUntil(() -> {
            helper.assertTrue(hopper.isEmpty(), "the hopper still holds items");
            helper.assertTrue(count(chest, Items.IRON_INGOT) == 62, "ingots made or lost");
        }).thenExecute(() -> leave(helper, player)).thenSucceed();
    }

    @GameTest(template = "arena", timeoutTicks = 600)
    public static void anItemFramePinsAChest(GameTestHelper helper) {
        floor(helper);
        GravititeGolem golem = golem(helper, HOME);
        BlockPos a = new BlockPos(12, 2, 12), b = new BlockPos(20, 2, 12), c = new BlockPos(12, 2, 20);
        BaseContainerBlockEntity mixed = chest(helper, a, new ItemStack(Items.COBBLESTONE, 64), new ItemStack(Items.COBBLESTONE, 64),
                new ItemStack(Items.OAK_LOG, 64), new ItemStack(Items.OAK_LOG, 64));
        BaseContainerBlockEntity first = chest(helper, b);
        BaseContainerBlockEntity pinned = chest(helper, c);
        ItemFrame frame = new ItemFrame(helper.getLevel(), helper.absolutePos(c.north()), Direction.NORTH);
        frame.setItem(new ItemStack(Items.OAK_LOG), false);
        helper.getLevel().addFreshEntity(frame);
        helper.startSequence().thenIdle(5).thenExecute(() -> {
            ServerLevel level = helper.getLevel();
            GolemNetworks networks = GolemNetworks.get(level);
            GolemNetworks.Net net = net(helper, golem);
            for (BlockPos pos : new BlockPos[]{a, b, c}) {
                networks.toggleLink(net, helper.absolutePos(pos), null, p -> GolemStorage.inventoryKey(level, p));
            }
        }).thenWaitUntil(() -> {
            helper.assertTrue(count(pinned, Items.OAK_LOG) == 128, "the logs didn't go to the pinned chest");
            helper.assertTrue(count(first, Items.OAK_LOG) == 0 && count(mixed, Items.OAK_LOG) == 0, "logs left elsewhere");
        }).thenSucceed();
    }

    @GameTest(template = "arena", timeoutTicks = 200)
    public static void golemsLinkWithinTwiceTheirReach(GameTestHelper helper) {
        floor(helper);
        GravititeGolem first = golem(helper, new BlockPos(4, 2, 4));
        GravititeGolem second = golem(helper, new BlockPos(30, 2, 4));
        GravititeGolem third = golem(helper, new BlockPos(4, 2, 30));
        helper.startSequence().thenIdle(5).thenExecute(() -> {
            GolemNetworks networks = GolemNetworks.get(helper.getLevel());
            GolemNetworks.Net a = net(helper, first), b = net(helper, second), c = net(helper, third);
            helper.assertTrue(networks.canMerge(a, b, first.home(), second.home()) == GolemNetworks.GolemLinkResult.LINKED,
                    "26 blocks apart should link");
            networks.merge(a, b);
            helper.assertTrue(networks.netOf(second.getUUID()) == a, "not merged");
            helper.assertTrue(networks.canMerge(a, c, first.home(), third.home()) == GolemNetworks.GolemLinkResult.TOO_MANY,
                    "a third golem joined a network of two");
            GolemNetworks.Net alone = networks.netOf(third.getUUID());
            helper.assertTrue(networks.canMerge(alone, a, third.home().offset(40, 0, 0), first.home())
                    == GolemNetworks.GolemLinkResult.TOO_FAR, "golems 40+ blocks apart linked");
            networks.split(second.getUUID());
            helper.assertTrue(networks.netOf(second.getUUID()) != a, "not split again");
        }).thenSucceed();
    }

    /** Two linked golems 28 blocks apart pass a hopper's items to a chest only the far one reaches. */
    @GameTest(template = "arena", timeoutTicks = 600)
    public static void linkedGolemsRelayAcrossTheirSpan(GameTestHelper helper) {
        floor(helper);
        for (int x = 40; x < 47; x++) {
            for (int z = 1; z < 40; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            }
        }
        GravititeGolem near = golem(helper, new BlockPos(6, 2, 16));
        GravititeGolem far = golem(helper, new BlockPos(34, 2, 16));
        BlockPos hopperPos = new BlockPos(2, 3, 16), chestPos = new BlockPos(44, 2, 16);
        helper.setBlock(hopperPos, Blocks.HOPPER);
        BaseContainerBlockEntity hopper = (BaseContainerBlockEntity) helper.getBlockEntity(hopperPos);
        hopper.setItem(0, new ItemStack(Items.GOLD_INGOT, 9));
        BaseContainerBlockEntity chest = chest(helper, chestPos);
        helper.startSequence().thenIdle(5).thenExecute(() -> {
            ServerLevel level = helper.getLevel();
            GolemNetworks networks = GolemNetworks.get(level);
            GolemNetworks.Net net = net(helper, near);
            helper.assertTrue(networks.canMerge(net, net(helper, far), near.home(), far.home())
                    == GolemNetworks.GolemLinkResult.LINKED, "the golems can't link");
            networks.merge(net, net(helper, far));
            for (BlockPos pos : new BlockPos[]{hopperPos, chestPos}) {
                helper.assertTrue(networks.toggleLink(net, helper.absolutePos(pos), null,
                        p -> GolemStorage.inventoryKey(level, p)) == GolemNetworks.LinkResult.LINKED, "couldn't link " + pos);
            }
        }).thenWaitUntil(() -> {
            helper.assertTrue(hopper.isEmpty(), "the hopper still holds its gold");
            helper.assertTrue(count(chest, Items.GOLD_INGOT) == 9, "the gold didn't all arrive");
        }).thenSucceed();
    }

    /** A double chest is one link, and unlinking either half unlinks both. */
    @GameTest(template = "arena", timeoutTicks = 100)
    public static void aDoubleChestIsOneLink(GameTestHelper helper) {
        floor(helper);
        GravititeGolem golem = golem(helper, HOME);
        BlockPos left = new BlockPos(12, 2, 12), right = left.east();
        // facing north, the left half is the west one: it joins to the east, the right half to the west
        helper.setBlock(left, Blocks.CHEST.defaultBlockState().setValue(net.minecraft.world.level.block.ChestBlock.TYPE,
                net.minecraft.world.level.block.state.properties.ChestType.LEFT));
        helper.setBlock(right, Blocks.CHEST.defaultBlockState().setValue(net.minecraft.world.level.block.ChestBlock.TYPE,
                net.minecraft.world.level.block.state.properties.ChestType.RIGHT));
        helper.startSequence().thenIdle(5).thenExecute(() -> {
            ServerLevel level = helper.getLevel();
            GolemNetworks networks = GolemNetworks.get(level);
            GolemNetworks.Net net = net(helper, golem);
            BlockPos abs = helper.absolutePos(left);
            BlockPos partner = GolemStorage.partner(level, abs);
            helper.assertTrue(helper.absolutePos(right).equals(partner), "the halves don't pair: " + partner);
            networks.toggleLink(net, abs, partner, p -> GolemStorage.inventoryKey(level, p));
            helper.assertTrue(GolemNetworks.inventories(net, p -> GolemStorage.inventoryKey(level, p)) == 1,
                    "a double chest counted twice");
            BlockPos other = helper.absolutePos(right);
            helper.assertTrue(networks.toggleLink(net, other, GolemStorage.partner(level, other),
                    p -> GolemStorage.inventoryKey(level, p)) == GolemNetworks.LinkResult.UNLINKED, "the other half isn't linked");
            helper.assertTrue(net.links().isEmpty(), "a half is still linked");
        }).thenSucceed();
    }

    @GameTest(template = "arena", timeoutTicks = 200)
    public static void struckItPushesAttackersAway(GameTestHelper helper) {
        floor(helper);
        GravititeGolem golem = golem(helper, HOME);
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, Vec3.atBottomCenterOf(HOME.east(2)));
        zombie.setNoAi(true);
        helper.startSequence().thenIdle(5).thenExecute(() -> {
            golem.hurt(helper.getLevel().damageSources().mobAttack(zombie), 1);
            helper.assertTrue(zombie.getDeltaMovement().x > 0.2, "the zombie wasn't pushed away: " + zombie.getDeltaMovement());
            zombie.setDeltaMovement(Vec3.ZERO);
            golem.hurt(helper.getLevel().damageSources().mobAttack(zombie), 1);
            helper.assertTrue(zombie.getDeltaMovement().lengthSqr() < 1.0E-4, "pushed again within the cooldown");
            helper.assertTrue(zombie.getTarget() != golem, "the zombie took the golem as a target");
        }).thenSucceed();
    }

    /** Sneaking with the pebble on the top of a block sends the golem there, and that becomes its home. */
    @GameTest(template = "arena", timeoutTicks = 600)
    public static void aSneakingPebbleClickSendsTheGolem(GameTestHelper helper) {
        floor(helper);
        GravititeGolem golem = golem(helper, HOME);
        FakePlayer player = player(helper, "golem-sender");
        BlockPos target = HOME.offset(-6, 0, 2);
        helper.startSequence().thenIdle(5).thenExecute(() -> {
            takePebble(golem, player);
            player.setShiftKeyDown(true);
            use(helper, player, target.below(), Direction.UP);
            player.setShiftKeyDown(false);
            helper.assertTrue(helper.absolutePos(target).equals(golem.order()), "the golem wasn't sent: " + golem.order());
        }).thenWaitUntil(() -> {
            helper.assertTrue(golem.isSeated() && golem.order() == null, "still on its way");
            helper.assertTrue(helper.absolutePos(target).equals(golem.home()), "home didn't move");
        }).thenExecute(() -> leave(helper, player)).thenSucceed();
    }

    /** Players are pushed more gently than mobs, so no one is flung off an island. */
    @GameTest(template = "arena", timeoutTicks = 200)
    public static void playersArePushedGently(GameTestHelper helper) {
        floor(helper);
        GravititeGolem golem = golem(helper, HOME);
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, Vec3.atBottomCenterOf(HOME.east(2)));
        zombie.setNoAi(true);
        FakePlayer player = player(helper, "golem-pushed");
        helper.startSequence().thenIdle(5).thenExecute(() -> {
            player.moveTo(helper.absoluteVec(Vec3.atBottomCenterOf(HOME.west(2))));
            player.setDeltaMovement(Vec3.ZERO);
            golem.hurt(helper.getLevel().damageSources().mobAttack(zombie), 1);
            double mob = zombie.getDeltaMovement().horizontalDistance();
            double them = player.getDeltaMovement().horizontalDistance();
            helper.assertTrue(them > 0, "the player wasn't pushed at all");
            helper.assertTrue(them < mob, "the player was pushed as hard as the zombie: " + them + " vs " + mob);
        }).thenExecute(() -> leave(helper, player)).thenSucceed();
    }

    @GameTest(template = "arena", timeoutTicks = 200)
    public static void redstoneMakesItRest(GameTestHelper helper) {
        floor(helper);
        GravititeGolem golem = golem(helper, HOME);
        helper.startSequence().thenIdle(5).thenExecute(() -> helper.setBlock(HOME.east(), Blocks.REDSTONE_BLOCK))
                .thenWaitUntil(() -> helper.assertTrue(golem.isPaused(), "not resting"))
                .thenExecute(() -> helper.setBlock(HOME.east(), Blocks.AIR))
                .thenWaitUntil(() -> helper.assertTrue(!golem.isPaused(), "still resting"))
                .thenSucceed();
    }

    @GameTest(template = "arena", timeoutTicks = 600)
    public static void aMovedGolemGoesHome(GameTestHelper helper) {
        floor(helper);
        GravititeGolem golem = golem(helper, HOME);
        helper.startSequence().thenIdle(10).thenExecute(() -> {
            Vec3 away = helper.absoluteVec(Vec3.atBottomCenterOf(HOME.offset(4, 0, 3)));
            golem.teleportTo(away.x, away.y, away.z);
        }).thenWaitUntil(() -> {
            helper.assertTrue(golem.isSeated(), "still walking");
            helper.assertTrue(golem.blockPosition().equals(helper.absolutePos(HOME)), "not back home: " + golem.blockPosition());
        }).thenExecute(() -> golem.sendTo(helper.absolutePos(HOME.offset(-5, 0, 0)), null))
                .thenWaitUntil(() -> {
                    helper.assertTrue(golem.isSeated() && golem.order() == null, "still on its way");
                    helper.assertTrue(helper.absolutePos(HOME.offset(-5, 0, 0)).equals(golem.home()), "home didn't move");
                }).thenSucceed();
    }

    @GameTest(template = "arena", timeoutTicks = 100)
    public static void itDropsItsHolystone(GameTestHelper helper) {
        floor(helper);
        GravititeGolem golem = golem(helper, HOME);
        UUID id = golem.getUUID();
        helper.startSequence().thenIdle(5).thenExecute(golem::kill)
                .thenWaitUntil(() -> helper.assertItemEntityPresent(AetherBlocks.HOLYSTONE.get().asItem(), HOME, 3))
                // removed once its death is over, and its network with it
                .thenWaitUntil(() -> helper.assertTrue(GolemNetworks.get(helper.getLevel()).netOf(id) == null,
                        "its network outlived it"))
                .thenSucceed();
    }
}
