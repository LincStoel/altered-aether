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
import com.linc.alteredaether.entity.golem.ThrownPebble;
import com.linc.alteredaether.item.PebbleLink;
import com.linc.alteredaether.item.StoragePebbleItem;
import com.linc.alteredaether.registry.AARegistries;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ComparatorBlock;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.ComparatorBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Gravitite golems: built from their blocks (handing the builder a pebble), linked with their pebble (storage doesn't
 * open), sorting what changed after a delay without losing or making an item, safe when an item is taken mid-flight,
 * emptying hoppers, refusing machines and walled-in storage, following pins, networking by colour within reach up to
 * a cap, pushing attackers off, resting under redstone, going home, healing at home, and dropping their blocks.
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

    private static ItemStack[] stacks(Item item, int count) {
        ItemStack[] stacks = new ItemStack[count];
        for (int i = 0; i < count; i++) {
            stacks[i] = new ItemStack(item, 64);
        }
        return stacks;
    }

    /**
     * Several runs of stacks one after another. Tidy gives a family a chest of its own only if it fills a quarter of
     * one (7 slots of a chest), so tests that sort two families apart give each at least that many.
     */
    private static ItemStack[] join(ItemStack[]... parts) {
        List<ItemStack> out = new ArrayList<>();
        for (ItemStack[] part : parts) {
            out.addAll(List.of(part));
        }
        return out.toArray(ItemStack[]::new);
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

    private static int pebbles(FakePlayer player) {
        int n = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (player.getInventory().getItem(slot).is(AARegistries.STORAGE_PEBBLE.get())) {
                n += player.getInventory().getItem(slot).getCount();
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

    /** Links storage to the golem's network as its pebble would. */
    private static void link(GameTestHelper helper, GravititeGolem golem, BlockPos... positions) {
        ServerLevel level = helper.getLevel();
        GolemNetworks networks = GolemNetworks.get(level);
        GolemNetworks.Net net = net(helper, golem);
        List<BlockPos> linked = new ArrayList<>();
        for (BlockPos pos : positions) {
            BlockPos abs = helper.absolutePos(pos);
            BlockPos partner = GolemStorage.partner(level, abs);
            helper.assertTrue(networks.link(net, abs, partner, p -> GolemStorage.inventoryKey(level, p))
                    == GolemNetworks.LinkResult.LINKED, "couldn't link " + pos);
            linked.add(abs);
            if (partner != null) {
                linked.add(partner);
            }
        }
        GolemSorter.linked(level, net, linked);
    }

    private static GolemSorter.Report report(GameTestHelper helper, GravititeGolem golem) {
        return GolemSorter.report(helper.getLevel(), net(helper, golem));
    }

    /** Waits until the golem's network has nothing to sort. */
    private static void assertSettled(GameTestHelper helper, GravititeGolem golem) {
        GolemSorter.Report report = report(helper, golem);
        helper.assertTrue(!report.waiting() && report.status() == GolemSorter.Status.SORTED, "not done yet: " + report);
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Building and pebbles

    @GameTest(template = "arena", timeoutTicks = 200)
    public static void placingTheHeadBuildsAGolemAndHandsAPebble(GameTestHelper helper) {
        floor(helper);
        BlockPos base = new BlockPos(10, 2, 10), second = new BlockPos(16, 2, 10);
        for (BlockPos at : new BlockPos[]{base, second}) {
            helper.setBlock(at, AetherBlocks.HOLYSTONE.get());
            helper.setBlock(at.above(), AetherBlocks.ENCHANTED_GRAVITITE.get());
        }
        FakePlayer player = player(helper, "golem-builder");
        place(helper, player, base);
        helper.startSequence().thenWaitUntil(() -> {
            helper.assertEntityPresent(AARegistries.GRAVITITE_GOLEM.get(), base);
            helper.assertBlockPresent(Blocks.AIR, base);
            helper.assertBlockPresent(Blocks.AIR, base.above());
            helper.assertBlockPresent(Blocks.AIR, base.above(2));
            GravititeGolem golem = helper.getLevel().getEntitiesOfClass(GravititeGolem.class,
                    new AABB(helper.absolutePos(base)).inflate(1)).get(0);
            helper.assertTrue(helper.absolutePos(base).equals(golem.home()), "home isn't where it was built");
            helper.assertTrue(pebbles(player) == 1, "the builder wasn't handed one pebble: " + pebbles(player));
        }).thenExecute(() -> place(helper, player, second)).thenWaitUntil(() -> {
            helper.assertEntityPresent(AARegistries.GRAVITITE_GOLEM.get(), second);
            // the second golem joins the first's network, whose pebble the builder already has
            helper.assertTrue(pebbles(player) == 1, "a second pebble for the same network: " + pebbles(player));
        }).thenExecute(() -> leave(helper, player)).thenSucceed();
    }

    private static void place(GameTestHelper helper, FakePlayer player, BlockPos base) {
        ItemStack pumpkin = new ItemStack(Items.CARVED_PUMPKIN);
        player.setItemInHand(InteractionHand.MAIN_HAND, pumpkin);
        BlockPos top = helper.absolutePos(base.above());
        pumpkin.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(top).add(0, 0.5, 0), Direction.UP, top, false)));
    }

    /** A pebble is active within reach of its network and dark out of it, but never dead while the network lives. */
    @GameTest(template = "arena", timeoutTicks = 100)
    public static void aPebbleGoesDarkOutOfReach(GameTestHelper helper) {
        floor(helper);
        GravititeGolem golem = golem(helper, HOME);
        FakePlayer player = player(helper, "golem-wanderer");
        ServerLevel level = helper.getLevel();
        helper.startSequence().thenIdle(5).thenExecute(() -> {
            ItemStack pebble = takePebble(golem, player);
            PebbleLink link = pebble.get(AARegistries.PEBBLE_LINK.get());
            BlockPos near = player.blockPosition();
            helper.assertTrue(StoragePebbleItem.active(level, link, near), "the pebble is dark beside its golem");
            helper.assertTrue(!StoragePebbleItem.active(level, link, near.offset(1000, 0, 0)), "it glows far away");
            player.moveTo(Vec3.atCenterOf(near.offset(1000, 0, 0)));
            player.tickCount = 0;
            pebble.inventoryTick(level, player, 0, true);
            helper.assertTrue(!pebble.get(AARegistries.PEBBLE_LINK.get()).dead(), "the pebble went dead, not dark");
            leave(helper, player);
        }).thenSucceed();
    }

    /** A pebble from a golem that dies lives on with its network, and dies with the network. */
    @GameTest(template = "arena", timeoutTicks = 200)
    public static void aPebbleOutlivesItsGolem(GameTestHelper helper) {
        floor(helper);
        GravititeGolem first = golem(helper, new BlockPos(12, 2, 16));
        GravititeGolem second = golem(helper, new BlockPos(22, 2, 16));
        FakePlayer player = player(helper, "golem-mourner");
        ServerLevel level = helper.getLevel();
        ItemStack[] pebble = new ItemStack[1];
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(net(helper, first) == net(helper, second),
                "the golems didn't network")).thenExecute(() -> {
            pebble[0] = takePebble(first, player);
            first.kill();
        }).thenWaitUntil(() -> helper.assertTrue(first.isRemoved(), "not gone yet")).thenExecute(() -> {
            player.tickCount = 0;
            pebble[0].inventoryTick(level, player, 0, true);
            PebbleLink link = pebble[0].get(AARegistries.PEBBLE_LINK.get());
            helper.assertTrue(!link.dead(), "the pebble died with its golem though its network lives");
            helper.assertTrue(net(helper, second).id.equals(link.network()), "the pebble lost its network");
            second.kill();
        }).thenWaitUntil(() -> helper.assertTrue(second.isRemoved(), "not gone yet")).thenExecute(() -> {
            player.tickCount = 0;
            pebble[0].inventoryTick(level, player, 0, true);
            helper.assertTrue(pebble[0].get(AARegistries.PEBBLE_LINK.get()).dead(), "the pebble outlived its network");
            leave(helper, player);
        }).thenSucceed();
    }

    /** Used on nothing the pebble is thrown and used up; it falls upwards, and bursts on leaving the world's height. */
    @GameTest(template = "arena", timeoutTicks = 200)
    public static void aThrownPebbleFallsUpAndBursts(GameTestHelper helper) {
        floor(helper);
        GravititeGolem golem = golem(helper, HOME);
        FakePlayer player = player(helper, "golem-thrower");
        ServerLevel level = helper.getLevel();
        ThrownPebble[] thrown = new ThrownPebble[2];
        double[] startY = new double[1];
        helper.startSequence().thenIdle(5).thenExecute(() -> {
            ItemStack pebble = takePebble(golem, player);
            pebble.use(level, player, InteractionHand.MAIN_HAND);
            helper.assertTrue(player.getMainHandItem().isEmpty(), "throwing didn't use the pebble up");
            List<ThrownPebble> flying = level.getEntitiesOfClass(ThrownPebble.class, player.getBoundingBox().inflate(4));
            helper.assertTrue(flying.size() == 1, "no pebble was thrown");
            thrown[0] = flying.get(0);
            helper.assertTrue(!thrown[0].isInert(), "a pebble thrown beside its golem is inert");
            helper.assertTrue(thrown[0].getItem().get(AARegistries.PEBBLE_LINK.get()) != null, "the thrown pebble lost its link");
            thrown[0].setDeltaMovement(Vec3.ZERO);
            thrown[0].setPos(thrown[0].getX(), level.getMaxBuildHeight() + 0.5, thrown[0].getZ());
            thrown[1] = helper.spawn(AARegistries.THROWN_PEBBLE.get(), new Vec3(10.5, 3, 10.5));
            startY[0] = thrown[1].getY();
        }).thenIdle(8).thenExecute(() -> {
            helper.assertTrue(thrown[0].isRemoved(), "a pebble above the world's height didn't burst");
            helper.assertTrue(thrown[1].isRemoved() || thrown[1].getY() > startY[0] + 0.5,
                    "a pebble left in the air didn't fall upwards: " + (thrown[1].getY() - startY[0]));
            thrown[1].discard();
            leave(helper, player);
        }).thenSucceed();
    }

    @GameTest(template = "arena", timeoutTicks = 100)
    public static void anInertPebbleFalls(GameTestHelper helper) {
        ThrownPebble pebble = helper.spawn(AARegistries.THROWN_PEBBLE.get(), new Vec3(10.5, 20, 10.5));
        pebble.setInert(true);
        double start = pebble.getY();
        helper.startSequence().thenIdle(8).thenExecute(() -> {
            helper.assertTrue(pebble.isRemoved() || pebble.getY() < start - 0.5,
                    "an inert pebble didn't fall: " + (pebble.getY() - start));
            pebble.discard();
        }).thenSucceed();
    }

    /** A pebble thrown at its own golem just shatters: throwing does nothing but that. */
    @GameTest(template = "arena", timeoutTicks = 100)
    public static void aPebbleThrownAtItsGolemShatters(GameTestHelper helper) {
        floor(helper);
        GravititeGolem golem = golem(helper, HOME);
        chest(helper, HOME.offset(3, 0, 0));
        FakePlayer player = player(helper, "golem-pitcher");
        ThrownPebble[] thrown = new ThrownPebble[1];
        helper.startSequence().thenIdle(5).thenExecute(() -> {
            ItemStack pebble = takePebble(golem, player);
            ThrownPebble entity = new ThrownPebble(helper.getLevel(), player);
            entity.setItem(pebble);
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            Vec3 above = golem.position().add(0, golem.getBbHeight() + 0.5, 0);
            entity.moveTo(above.x, above.y, above.z);
            entity.setDeltaMovement(0, -1, 0);
            helper.getLevel().addFreshEntity(entity);
            thrown[0] = entity;
        }).thenIdle(5).thenExecute(() -> {
            helper.assertTrue(thrown[0].isRemoved(), "the pebble didn't shatter on its golem");
            helper.assertTrue(net(helper, golem).links().isEmpty(), "a thrown pebble linked something");
            helper.assertTrue(pebbles(player) == 0, "the pebble came back");
            leave(helper, player);
        }).thenSucceed();
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Linking

    @GameTest(template = "arena", timeoutTicks = 600)
    public static void linksWithoutOpeningAndSortsTidily(GameTestHelper helper) {
        floor(helper);
        GravititeGolem golem = golem(helper, HOME);
        BlockPos a = new BlockPos(12, 2, 12), b = new BlockPos(20, 2, 12), c = new BlockPos(12, 2, 20);
        BaseContainerBlockEntity mixed = chest(helper, a, join(stacks(Items.COBBLESTONE, 10), stacks(Items.OAK_LOG, 7),
                new ItemStack[]{new ItemStack(Items.OAK_LOG, 30)}));
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
            helper.assertTrue(cobble == 640 && logs == 478, "items made or lost: " + cobble + " cobble, " + logs + " logs");
            helper.assertTrue(count(mixed, Items.OAK_LOG) == 0, "logs still mixed in with the cobblestone");
            helper.assertTrue(count(empty1, Items.OAK_LOG) == 478 || count(empty2, Items.OAK_LOG) == 478, "logs not together");
            assertSettled(helper, golem);
        }).thenExecute(() -> leave(helper, player)).thenSucceed();
    }

    /**
     * Spruce strewn over three chests with enchanted gravitite and odds and ends (as a player found it) comes together:
     * stairs, planks and stripped logs are all spruce, whatever their shape.
     */
    @GameTest(template = "arena", timeoutTicks = 900)
    public static void spruceStrewnOverChestsComesTogether(GameTestHelper helper) {
        floor(helper);
        GravititeGolem golem = golem(helper, HOME);
        Item gravitite = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("aether", "enchanted_gravitite"));
        BlockPos a = new BlockPos(12, 2, 12), b = new BlockPos(20, 2, 12), c = new BlockPos(12, 2, 20);
        BaseContainerBlockEntity first = chest(helper, a, new ItemStack(Items.SPRUCE_STAIRS, 64),
                new ItemStack(Items.SPRUCE_STAIRS, 64), new ItemStack(Items.SPRUCE_PLANKS, 64),
                new ItemStack(Items.STRIPPED_SPRUCE_LOG, 32), new ItemStack(gravitite, 16));
        BaseContainerBlockEntity second = chest(helper, b, stacks(Items.STRIPPED_SPRUCE_LOG, 3));
        BaseContainerBlockEntity third = chest(helper, c, new ItemStack(Items.SPRUCE_PLANKS, 40),
                new ItemStack(Items.OAK_PLANKS, 3), new ItemStack(Items.ANDESITE, 5), new ItemStack(Items.IRON_INGOT, 7));
        List<Item> spruce = List.of(Items.SPRUCE_STAIRS, Items.SPRUCE_PLANKS, Items.STRIPPED_SPRUCE_LOG);
        helper.startSequence().thenIdle(5).thenExecute(() -> link(helper, golem, a, b, c)).thenWaitUntil(() -> {
            assertSettled(helper, golem);
            BaseContainerBlockEntity home = null;
            for (BaseContainerBlockEntity chest : List.of(first, second, third)) {
                if (count(chest, Items.SPRUCE_STAIRS) > 0) {
                    home = chest;
                }
            }
            helper.assertTrue(home != null, "the stairs went missing");
            helper.assertTrue(count(home, Items.SPRUCE_STAIRS) == 128 && count(home, Items.SPRUCE_PLANKS) == 104
                    && count(home, Items.STRIPPED_SPRUCE_LOG) == 224, "the spruce isn't together");
            for (int slot = 0; slot < home.getContainerSize(); slot++) {
                ItemStack stack = home.getItem(slot);
                helper.assertTrue(stack.isEmpty() || spruce.contains(stack.getItem()), "the spruce chest holds " + stack);
            }
        }).thenSucceed();
    }

    /** Its pebble used on the golem links the storage around it (not bookshelves or furnaces); crouching unlinks it. */
    @GameTest(template = "arena", timeoutTicks = 200)
    public static void aPebbleUsedOnItsGolemLinksAllAround(GameTestHelper helper) {
        floor(helper);
        GravititeGolem golem = golem(helper, HOME);
        chest(helper, HOME.offset(3, 0, 0));
        chest(helper, HOME.offset(-3, 0, 2));
        helper.setBlock(HOME.offset(0, 0, 4), Blocks.BARREL);
        helper.setBlock(HOME.offset(2, 0, -3), Blocks.CHISELED_BOOKSHELF);
        helper.setBlock(HOME.offset(-2, 0, -3), Blocks.FURNACE);
        FakePlayer player = player(helper, "golem-sweeper");
        helper.startSequence().thenIdle(5).thenExecute(() -> {
            takePebble(golem, player);
            golem.interact(player, InteractionHand.MAIN_HAND);
            GolemNetworks.Net net = net(helper, golem);
            helper.assertTrue(net.links().size() == 3, "linked " + net.links().size() + " instead of the two chests and the barrel");
            helper.assertTrue(!net.links().contains(helper.absolutePos(HOME.offset(2, 0, -3))), "swept up a bookshelf");
            player.setShiftKeyDown(true);
            golem.interact(player, InteractionHand.MAIN_HAND);
            player.setShiftKeyDown(false);
            helper.assertTrue(net(helper, golem).links().isEmpty(), "crouching didn't unlink it all");
            leave(helper, player);
        }).thenSucceed();
    }

    /** Storage linked to one network can't be linked to another, by pebble or by link-all. */
    @GameTest(template = "arena", timeoutTicks = 100)
    public static void anInventoryCantJoinASecondNetwork(GameTestHelper helper) {
        floor(helper);
        GravititeGolem first = golem(helper, new BlockPos(12, 2, 16));
        GravititeGolem second = golem(helper, new BlockPos(20, 2, 16));
        BlockPos shared = new BlockPos(16, 2, 16);
        chest(helper, shared);
        FakePlayer player = player(helper, "golem-rival");
        helper.startSequence().thenIdle(5).thenExecute(() -> second.setColor(DyeColor.BLUE, null))
                .thenIdle(25).thenExecute(() -> {
                    helper.assertTrue(net(helper, first) != net(helper, second), "golems of two colours networked");
                    link(helper, first, shared);
                    ServerLevel level = helper.getLevel();
                    BlockPos abs = helper.absolutePos(shared);
                    helper.assertTrue(GolemNetworks.get(level).link(net(helper, second), abs, null,
                            p -> GolemStorage.inventoryKey(level, p)) == GolemNetworks.LinkResult.TAKEN, "linked twice");
                    takePebble(second, player);
                    second.interact(player, InteractionHand.MAIN_HAND);
                    helper.assertTrue(net(helper, second).links().isEmpty(), "link-all took the other network's chest");
                    leave(helper, player);
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

    // ---------------------------------------------------------------------------------------------------------------
    // Sorting what changed

    /** A stack put in one chest moves after the delay, not before, and nothing else is stirred. */
    @GameTest(template = "arena", timeoutTicks = 900)
    public static void aStackAddedToOneChestMovesAfterTheDelay(GameTestHelper helper) {
        floor(helper);
        GravititeGolem golem = golem(helper, HOME);
        BlockPos a = new BlockPos(12, 2, 12), b = new BlockPos(20, 2, 12), c = new BlockPos(12, 2, 20);
        BaseContainerBlockEntity stone = chest(helper, a, stacks(Items.COBBLESTONE, 8));
        BaseContainerBlockEntity logs = chest(helper, b, stacks(Items.OAK_LOG, 8));
        BaseContainerBlockEntity other = chest(helper, c, stacks(Items.DIAMOND, 8));
        List<ItemStack> before = new ArrayList<>();
        helper.startSequence().thenIdle(5).thenExecute(() -> link(helper, golem, a, b, c))
                .thenWaitUntil(() -> assertSettled(helper, golem))
                .thenExecute(() -> {
                    for (int slot = 0; slot < other.getContainerSize(); slot++) {
                        before.add(other.getItem(slot).copy());
                    }
                    logs.setItem(20, new ItemStack(Items.COBBLESTONE, 64));
                })
                .thenIdle(40).thenExecute(() -> helper.assertTrue(count(logs, Items.COBBLESTONE) == 64,
                        "the stack moved before the delay"))
                .thenWaitUntil(() -> {
                    helper.assertTrue(count(logs, Items.COBBLESTONE) == 0, "the stack never moved");
                    helper.assertTrue(count(stone, Items.COBBLESTONE) == 576, "the stack didn't join the cobblestone");
                })
                .thenExecute(() -> {
                    for (int slot = 0; slot < other.getContainerSize(); slot++) {
                        helper.assertTrue(ItemStack.matches(before.get(slot), other.getItem(slot)), "an unchanged chest was stirred");
                    }
                    helper.assertTrue(count(logs, Items.OAK_LOG) == 512, "the logs moved");
                }).thenSucceed();
    }

    /** Linking a container sorts the network once; unlinking one sorts nothing. */
    @GameTest(template = "arena", timeoutTicks = 600)
    public static void linkingSortsOnceAndUnlinkingSortsNothing(GameTestHelper helper) {
        floor(helper);
        GravititeGolem golem = golem(helper, HOME);
        BlockPos a = new BlockPos(12, 2, 12), b = new BlockPos(20, 2, 12);
        // the cobblestone is scattered through its chest: a sort lays it out from the first slot
        BaseContainerBlockEntity scattered = chest(helper, a);
        scattered.setItem(4, new ItemStack(Items.COBBLESTONE, 64));
        scattered.setItem(13, new ItemStack(Items.COBBLESTONE, 64));
        chest(helper, b, new ItemStack(Items.OAK_LOG, 64));
        helper.startSequence().thenIdle(5).thenExecute(() -> {
            link(helper, golem, a, b);
            helper.assertTrue(report(helper, golem).waiting(), "linking asked for no sort");
        }).thenWaitUntil(() -> {
            helper.assertTrue(scattered.getItem(0).is(Items.COBBLESTONE) && scattered.getItem(1).is(Items.COBBLESTONE),
                    "the network wasn't sorted after linking");
            assertSettled(helper, golem);
        }).thenExecute(() -> {
            ServerLevel level = helper.getLevel();
            BlockPos abs = helper.absolutePos(b);
            GolemNetworks.get(level).unlink(net(helper, golem), abs, null);
            GolemSorter.unlinked(level, net(helper, golem), List.of(abs));
        }).thenIdle(5).thenExecute(() -> helper.assertTrue(!report(helper, golem).waiting(), "unlinking asked for a sort"))
                .thenSucceed();
    }

    /** A chest held open isn't sorted until it is closed. */
    @GameTest(template = "arena", timeoutTicks = 900)
    public static void aChestHeldOpenWaitsUntilClosed(GameTestHelper helper) {
        floor(helper);
        GravititeGolem golem = golem(helper, HOME);
        BlockPos a = new BlockPos(12, 2, 12), b = new BlockPos(20, 2, 12);
        BaseContainerBlockEntity stone = chest(helper, a, stacks(Items.COBBLESTONE, 8));
        BaseContainerBlockEntity logs = chest(helper, b, stacks(Items.OAK_LOG, 8));
        FakePlayer player = player(helper, "golem-browser");
        helper.startSequence().thenIdle(5).thenExecute(() -> link(helper, golem, a, b))
                .thenWaitUntil(() -> assertSettled(helper, golem))
                .thenExecute(() -> {
                    // a fake player opens no menu through openMenu, so it is given the chest's menu as opening does
                    player.moveTo(helper.absoluteVec(Vec3.atBottomCenterOf(b.north())));
                    player.containerMenu = net.minecraft.world.inventory.ChestMenu.threeRows(1, player.getInventory(), logs);
                    helper.assertTrue(GolemStorage.inUse(helper.getLevel(), helper.absolutePos(b)), "the chest isn't open");
                    logs.setItem(10, new ItemStack(Items.COBBLESTONE, 32));
                })
                .thenIdle(200).thenExecute(() -> {
                    helper.assertTrue(count(logs, Items.COBBLESTONE) == 32, "sorted while held open");
                    // closed without the close event: the golem notices on its next look
                    player.containerMenu.removed(player);
                    player.containerMenu = player.inventoryMenu;
                })
                .thenWaitUntil(() -> {
                    helper.assertTrue(count(logs, Items.COBBLESTONE) == 0, "not sorted once closed");
                    helper.assertTrue(count(stone, Items.COBBLESTONE) == 544, "items made or lost");
                }).thenExecute(() -> leave(helper, player)).thenSucceed();
    }

    /** A redstone pulse (off after on) sorts everything, including what changed while it rested. */
    @GameTest(template = "arena", timeoutTicks = 900)
    public static void aRedstonePulseSortsEverything(GameTestHelper helper) {
        floor(helper);
        GravititeGolem golem = golem(helper, HOME);
        BlockPos a = new BlockPos(12, 2, 12), b = new BlockPos(20, 2, 12);
        BaseContainerBlockEntity stone = chest(helper, a, stacks(Items.COBBLESTONE, 8));
        BaseContainerBlockEntity logs = chest(helper, b, stacks(Items.OAK_LOG, 8));
        helper.startSequence().thenIdle(5).thenExecute(() -> link(helper, golem, a, b))
                .thenWaitUntil(() -> assertSettled(helper, golem))
                .thenExecute(() -> helper.setBlock(HOME.east(), Blocks.REDSTONE_BLOCK))
                .thenWaitUntil(() -> helper.assertTrue(golem.isPaused(), "not resting"))
                .thenExecute(() -> stone.setItem(20, new ItemStack(Items.OAK_LOG, 64)))
                .thenIdle(100).thenExecute(() -> {
                    helper.assertTrue(count(stone, Items.OAK_LOG) == 64, "sorted while resting");
                    helper.setBlock(HOME.east(), Blocks.AIR);
                })
                .thenWaitUntil(() -> {
                    helper.assertTrue(count(stone, Items.OAK_LOG) == 0, "the pulse sorted nothing");
                    helper.assertTrue(count(logs, Items.OAK_LOG) == 576, "items made or lost");
                }).thenSucceed();
    }

    /** Of two golems that reach a chest, the nearer lifts from it. */
    @GameTest(template = "arena", timeoutTicks = 600)
    public static void theClosestGolemLifts(GameTestHelper helper) {
        floor(helper);
        GravititeGolem far = golem(helper, new BlockPos(10, 2, 16));
        GravititeGolem near = golem(helper, new BlockPos(22, 2, 16));
        BlockPos src = new BlockPos(20, 2, 18), dst = new BlockPos(24, 2, 18);
        chest(helper, src, new ItemStack(Items.COBBLESTONE, 64), new ItemStack(Items.OAK_LOG, 64), new ItemStack(Items.OAK_LOG, 64));
        chest(helper, dst, new ItemStack(Items.OAK_LOG, 64), new ItemStack(Items.OAK_LOG, 64), new ItemStack(Items.OAK_LOG, 64));
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(net(helper, far) == net(helper, near), "not one network"))
                .thenExecute(() -> link(helper, near, src, dst))
                .thenWaitUntil(() -> {
                    helper.assertTrue(near.state() == GravititeGolem.CONCENTRATING, "the near golem isn't lifting");
                    helper.assertTrue(far.state() != GravititeGolem.CONCENTRATING, "the far golem lifted");
                }).thenSucceed();
    }

    /** A barrel walled in on every side is reported blocked and never used. */
    @GameTest(template = "arena", timeoutTicks = 600)
    public static void anEncasedBarrelIsBlockedAndNeverUsed(GameTestHelper helper) {
        floor(helper);
        GravititeGolem golem = golem(helper, HOME);
        BlockPos barrelPos = new BlockPos(12, 2, 12), chestPos = new BlockPos(20, 2, 12);
        helper.setBlock(barrelPos, Blocks.BARREL);
        BaseContainerBlockEntity barrel = (BaseContainerBlockEntity) helper.getBlockEntity(barrelPos);
        barrel.setItem(0, new ItemStack(Items.OAK_LOG, 64));
        barrel.setItem(1, new ItemStack(Items.COBBLESTONE, 64));
        for (Direction side : Direction.values()) {
            helper.setBlock(barrelPos.relative(side), Blocks.STONE);
        }
        chest(helper, chestPos, new ItemStack(Items.OAK_LOG, 64), new ItemStack(Items.COBBLESTONE, 64));
        helper.startSequence().thenIdle(5).thenExecute(() -> link(helper, golem, barrelPos, chestPos))
                .thenWaitUntil(() -> {
                    assertSettled(helper, golem);
                    helper.assertTrue(report(helper, golem).blocked() == 1, "the barrel isn't reported blocked: " + report(helper, golem));
                })
                .thenExecute(() -> helper.assertTrue(count(barrel, Items.OAK_LOG) == 64 && count(barrel, Items.COBBLESTONE) == 64,
                        "the encased barrel was used")).thenSucceed();
    }

    /** A chest behind a wall, with a face open to the air, is still used: there is no line-of-sight rule. */
    @GameTest(template = "arena", timeoutTicks = 600)
    public static void aChestBehindAWallIsStillUsed(GameTestHelper helper) {
        floor(helper);
        GravititeGolem golem = golem(helper, HOME);
        BlockPos hidden = new BlockPos(16, 2, 24), open = new BlockPos(20, 2, 12);
        for (int x = 8; x <= 24; x++) {
            for (int y = 2; y <= 6; y++) {
                helper.setBlock(new BlockPos(x, y, 22), Blocks.STONE);
            }
        }
        BaseContainerBlockEntity behind = chest(helper, hidden, join(stacks(Items.OAK_LOG, 1), stacks(Items.COBBLESTONE, 8)));
        BaseContainerBlockEntity front = chest(helper, open, stacks(Items.OAK_LOG, 7));
        helper.startSequence().thenIdle(5).thenExecute(() -> link(helper, golem, hidden, open))
                .thenWaitUntil(() -> {
                    helper.assertTrue(count(behind, Items.OAK_LOG) == 0, "the chest behind the wall wasn't used");
                    helper.assertTrue(count(front, Items.OAK_LOG) == 512, "logs made or lost");
                }).thenSucceed();
    }

    /** Several flights fill different slots of one chest at once. */
    @GameTest(template = "arena", timeoutTicks = 600)
    public static void flightsFillDifferentSlotsOfOneChestAtOnce(GameTestHelper helper) {
        floor(helper);
        GravititeGolem golem = golem(helper, HOME);
        BlockPos src = new BlockPos(12, 2, 12), dst = new BlockPos(20, 2, 12);
        chest(helper, src, new ItemStack(Items.OAK_LOG, 64), new ItemStack(Items.COBBLESTONE, 64), new ItemStack(Items.COBBLESTONE, 64),
                new ItemStack(Items.COBBLESTONE, 64));
        chest(helper, dst);
        ItemFrame frame = new ItemFrame(helper.getLevel(), helper.absolutePos(dst.north()), Direction.NORTH);
        frame.setItem(new ItemStack(Items.COBBLESTONE), false);
        helper.getLevel().addFreshEntity(frame);
        BlockPos target = helper.absolutePos(dst);
        helper.startSequence().thenIdle(5).thenExecute(() -> link(helper, golem, src, dst))
                .thenWaitUntil(() -> {
                    long into = report(helper, golem).flightTargets().stream().filter(target::equals).count();
                    helper.assertTrue(into >= 2, "flights into the chest went one at a time");
                }).thenSucceed();
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Earlier sorting checks, now started by linking

    @GameTest(template = "arena", timeoutTicks = 600)
    public static void compactPacksIntoFewerChests(GameTestHelper helper) {
        floor(helper);
        GravititeGolem golem = golem(helper, HOME);
        BlockPos a = new BlockPos(12, 2, 12), b = new BlockPos(20, 2, 12);
        BaseContainerBlockEntity first = chest(helper, a, new ItemStack(Items.COBBLESTONE, 20), new ItemStack(Items.OAK_LOG, 5));
        BaseContainerBlockEntity second = chest(helper, b, new ItemStack(Items.COBBLESTONE, 30), new ItemStack(Items.OAK_LOG, 40));
        helper.startSequence().thenIdle(5).thenExecute(() -> {
            GolemNetworks.get(helper.getLevel()).setMode(net(helper, golem), GolemMode.COMPACT);
            link(helper, golem, a, b);
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
        BaseContainerBlockEntity source = chest(helper, a, join(new ItemStack[]{new ItemStack(Items.DIAMOND, 7)},
                stacks(Items.COBBLESTONE, 8)));
        BaseContainerBlockEntity target = chest(helper, b, stacks(Items.DIAMOND, 7));
        helper.startSequence().thenIdle(5).thenExecute(() -> link(helper, golem, a, b)).thenWaitUntil(() -> {
            // the diamonds are in the air (the golem is concentrating) but still in their chest
            helper.assertTrue(golem.state() == GravititeGolem.CONCENTRATING, "no flight yet");
            helper.assertTrue(count(source, Items.DIAMOND) == 7, "the diamonds left before landing");
        }).thenExecute(() -> source.setItem(0, ItemStack.EMPTY)).thenIdle(80).thenExecute(() -> {
            helper.assertTrue(count(target, Items.DIAMOND) == 448, "diamonds appeared from nowhere: " + count(target, Items.DIAMOND));
            helper.assertTrue(count(source, Items.DIAMOND) == 0, "diamonds came back");
            helper.assertTrue(helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                    new AABB(helper.absolutePos(a)).inflate(4)).isEmpty(), "something was dropped");
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
        helper.startSequence().thenIdle(5).thenExecute(() -> link(helper, golem, a, b, c)).thenWaitUntil(() -> {
            helper.assertTrue(count(pinned, Items.OAK_LOG) == 128, "the logs didn't go to the pinned chest");
            helper.assertTrue(count(first, Items.OAK_LOG) == 0 && count(mixed, Items.OAK_LOG) == 0, "logs left elsewhere");
        }).thenSucceed();
    }

    /** A comparator behind the block a golem sits on reads how full its network's storage is. */
    @GameTest(template = "arena", timeoutTicks = 300)
    public static void aComparatorReadsTheNetwork(GameTestHelper helper) {
        floor(helper);
        GravititeGolem golem = golem(helper, HOME);
        BlockPos a = new BlockPos(12, 2, 12);
        chest(helper, a, stacks(Items.COBBLESTONE, 14));
        BlockPos comparator = HOME.below().east();
        helper.setBlock(comparator.below(), Blocks.STONE);
        helper.setBlock(comparator, Blocks.COMPARATOR.defaultBlockState().setValue(ComparatorBlock.FACING, Direction.WEST));
        helper.startSequence().thenIdle(5).thenExecute(() -> link(helper, golem, a)).thenWaitUntil(() -> {
            // 14 of 27 slots full: as a comparator reads that chest itself
            int expected = (int) Math.floor(14 / 27.0 * 14) + 1;
            helper.assertTrue(GolemSorter.signal(helper.getLevel(), net(helper, golem)) == expected, "the network signal is off");
            ComparatorBlockEntity be = (ComparatorBlockEntity) helper.getBlockEntity(comparator);
            helper.assertTrue(be.getOutputSignal() == expected, "the comparator reads " + be.getOutputSignal() + ", not " + expected);
        }).thenSucceed();
    }

    /** With Create, a stockpile switch facing the block a golem sits on counts its whole network's storage. */
    @GameTest(template = "arena", timeoutTicks = 300)
    public static void aThresholdSwitchReadsTheNetwork(GameTestHelper helper) {
        if (!net.neoforged.fml.ModList.get().isLoaded("create")) {
            helper.succeed();
            return;
        }
        floor(helper);
        GravititeGolem golem = golem(helper, HOME);
        BlockPos a = new BlockPos(12, 2, 12), b = new BlockPos(20, 2, 12);
        chest(helper, a, stacks(Items.COBBLESTONE, 10));
        chest(helper, b, stacks(Items.OAK_LOG, 4));
        BlockPos sensor = HOME.below().east();
        helper.setBlock(sensor.below(), Blocks.STONE);
        CreateGolemChecks.placeSwitch(helper, sensor);
        helper.startSequence().thenIdle(5).thenExecute(() -> link(helper, golem, a, b))
                .thenWaitUntil(() -> CreateGolemChecks.assertReads(helper, sensor, 14 * 64, 2 * 27 * 64)).thenSucceed();
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Networks by colour

    /** Golems of one colour within reach work as one network; dyeing one another colour takes it out. */
    @GameTest(template = "arena", timeoutTicks = 300)
    public static void sameColourGolemsWithinReachNetwork(GameTestHelper helper) {
        floor(helper);
        GravititeGolem a = golem(helper, new BlockPos(8, 2, 16));
        GravititeGolem b = golem(helper, new BlockPos(20, 2, 16));
        GravititeGolem c = golem(helper, new BlockPos(32, 2, 16));
        GravititeGolem d = golem(helper, new BlockPos(14, 2, 30));
        helper.startSequence().thenIdle(5).thenExecute(() -> d.setColor(DyeColor.BLUE, null))
                .thenWaitUntil(() -> {
                    helper.assertTrue(net(helper, a) == net(helper, b) && net(helper, b) == net(helper, c),
                            "a chain of golems in reach of each other isn't one network");
                    helper.assertTrue(net(helper, d) != net(helper, a), "a blue golem joined the magenta ones");
                })
                .thenExecute(() -> d.setColor(GravititeGolem.DEFAULT_COLOR, null))
                .thenExecute(() -> helper.assertTrue(net(helper, d) == net(helper, a), "dyed back, it didn't rejoin"))
                .thenExecute(() -> b.setColor(DyeColor.RED, null))
                .thenExecute(() -> {
                    helper.assertTrue(net(helper, a) != net(helper, c), "taking the middle golem out didn't split them");
                    helper.assertTrue(net(helper, a) == net(helper, d), "the golems still in reach split");
                }).thenSucceed();
    }

    /** A golem that would take a network past its cap is refused and rests, and gets in once there's room. */
    @GameTest(template = "arena", timeoutTicks = 400)
    public static void aGolemPastTheCapIsRefused(GameTestHelper helper) {
        floor(helper);
        int cap = com.linc.alteredaether.config.AACommonConfig.GOLEM_MAX_NETWORK.get();
        List<GravititeGolem> golems = new ArrayList<>();
        for (int i = 0; i < cap; i++) {
            golems.add(golem(helper, new BlockPos(12 + (i % 3) * 3, 2, 12 + (i / 3) * 3)));
        }
        GravititeGolem[] extra = new GravititeGolem[1];
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(net(helper, golems.get(0)).members().size() == cap,
                "the first golems aren't one network")).thenExecute(() -> extra[0] = golem(helper, new BlockPos(24, 2, 18)))
                .thenWaitUntil(() -> {
                    helper.assertTrue(extra[0].isRefused(), "a golem past the cap wasn't refused");
                    helper.assertTrue(!extra[0].isWorking(), "a refused golem works");
                    helper.assertTrue(net(helper, extra[0]).members().size() == 1, "a refused golem isn't alone");
                })
                .thenExecute(() -> golems.get(0).kill())
                .thenWaitUntil(() -> {
                    helper.assertTrue(!extra[0].isRefused(), "there's room now, but it's still refused");
                    helper.assertTrue(net(helper, extra[0]) == net(helper, golems.get(1)), "it didn't join");
                }).thenSucceed();
    }

    /** The middle golem of three dying splits the network, and each pebble follows the golem it came from. */
    @GameTest(template = "arena", timeoutTicks = 300)
    public static void aMiddleGolemDyingSplitsTheNetwork(GameTestHelper helper) {
        floor(helper);
        GravititeGolem a = golem(helper, new BlockPos(8, 2, 16));
        GravititeGolem b = golem(helper, new BlockPos(20, 2, 16));
        GravititeGolem c = golem(helper, new BlockPos(32, 2, 16));
        FakePlayer player = player(helper, "golem-splitter");
        ServerLevel level = helper.getLevel();
        ItemStack[] pebbles = new ItemStack[2];
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(net(helper, a) == net(helper, c), "not one network"))
                .thenExecute(() -> {
                    pebbles[0] = takePebble(a, player);
                    player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                    player.getInventory().clearContent();
                    pebbles[1] = takePebble(c, player);
                    b.kill();
                })
                .thenWaitUntil(() -> helper.assertTrue(b.isRemoved(), "not gone yet"))
                .thenExecute(() -> {
                    helper.assertTrue(net(helper, a) != net(helper, c), "the network didn't split");
                    for (ItemStack pebble : pebbles) {
                        player.tickCount = 0;
                        pebble.inventoryTick(level, player, 0, false);
                    }
                    helper.assertTrue(net(helper, a).id.equals(pebbles[0].get(AARegistries.PEBBLE_LINK.get()).network()),
                            "a's pebble didn't follow a");
                    helper.assertTrue(net(helper, c).id.equals(pebbles[1].get(AARegistries.PEBBLE_LINK.get()).network()),
                            "c's pebble didn't follow c");
                    leave(helper, player);
                }).thenSucceed();
    }

    /** Two networked golems 14 blocks apart pass a hopper's items to a chest only the far one reaches. */
    @GameTest(template = "arena", timeoutTicks = 600)
    public static void networkedGolemsRelay(GameTestHelper helper) {
        floor(helper);
        GravititeGolem near = golem(helper, new BlockPos(8, 2, 16));
        GravititeGolem far = golem(helper, new BlockPos(22, 2, 16));
        BlockPos hopperPos = new BlockPos(2, 3, 16), chestPos = new BlockPos(36, 2, 16);
        helper.setBlock(hopperPos, Blocks.HOPPER);
        BaseContainerBlockEntity hopper = (BaseContainerBlockEntity) helper.getBlockEntity(hopperPos);
        hopper.setItem(0, new ItemStack(Items.GOLD_INGOT, 9));
        BaseContainerBlockEntity chest = chest(helper, chestPos);
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(net(helper, near) == net(helper, far), "not one network"))
                .thenExecute(() -> link(helper, near, hopperPos, chestPos))
                .thenWaitUntil(() -> {
                    helper.assertTrue(hopper.isEmpty(), "the hopper still holds its gold");
                    helper.assertTrue(count(chest, Items.GOLD_INGOT) == 9, "the gold didn't all arrive");
                }).thenSucceed();
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Combat, healing, moving

    @GameTest(template = "arena", timeoutTicks = 200)
    public static void struckItPushesAttackersAwayAndUp(GameTestHelper helper) {
        floor(helper);
        GravititeGolem golem = golem(helper, HOME);
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, Vec3.atBottomCenterOf(HOME.east(2)));
        zombie.setNoAi(true);
        helper.startSequence().thenIdle(5).thenExecute(() -> {
            golem.hurt(helper.getLevel().damageSources().mobAttack(zombie), 1);
            helper.assertTrue(zombie.getDeltaMovement().x > 0.2, "the zombie wasn't pushed away: " + zombie.getDeltaMovement());
            helper.assertTrue(zombie.getDeltaMovement().y > 0.2, "the zombie wasn't lifted: " + zombie.getDeltaMovement());
            helper.assertTrue(zombie.hasEffect(MobEffects.LEVITATION), "the zombie isn't levitating");
            zombie.setDeltaMovement(Vec3.ZERO);
            golem.hurt(helper.getLevel().damageSources().mobAttack(zombie), 1);
            helper.assertTrue(zombie.getDeltaMovement().lengthSqr() < 1.0E-4, "pushed again within the cooldown");
        }).thenSucceed();
    }

    /** Players are pushed more gently than mobs, so no one is flung off an island; both float a moment. */
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
            helper.assertTrue(player.hasEffect(MobEffects.LEVITATION), "the player isn't levitating");
        }).thenExecute(() -> leave(helper, player)).thenSucceed();
    }

    /** A zombie with its wits about it never takes a golem for a target. */
    @GameTest(template = "arena", timeoutTicks = 200)
    public static void aZombieNeverTargetsTheGolem(GameTestHelper helper) {
        floor(helper);
        GravititeGolem golem = golem(helper, HOME);
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, Vec3.atBottomCenterOf(HOME.east(3)));
        helper.assertTrue(!golem.canBeSeenAsEnemy(), "the golem can be seen as an enemy");
        helper.onEachTick(() -> helper.assertTrue(zombie.getTarget() != golem, "the zombie took the golem as a target"));
        helper.startSequence().thenIdle(100).thenSucceed();
    }

    /** A hurt golem sitting at home heals by itself; an ambrosium shard only asks where shards are kept. */
    @GameTest(template = "arena", timeoutTicks = 400)
    public static void aHurtGolemHealsAtHome(GameTestHelper helper) {
        floor(helper);
        GravititeGolem golem = golem(helper, HOME);
        FakePlayer player = player(helper, "golem-medic");
        helper.startSequence().thenIdle(5).thenExecute(() -> {
            golem.setHealth(6);
            player.setItemInHand(InteractionHand.MAIN_HAND,
                    new ItemStack(com.aetherteam.aether.item.AetherItems.AMBROSIUM_SHARD.get(), 4));
            golem.interact(player, InteractionHand.MAIN_HAND);
            helper.assertTrue(golem.getHealth() == 6, "an ambrosium shard healed it");
            helper.assertTrue(player.getMainHandItem().getCount() == 4, "the shard was used up");
        }).thenWaitUntil(() -> helper.assertTrue(golem.getHealth() > 6, "it didn't heal at home"))
                .thenExecute(() -> leave(helper, player)).thenSucceed();
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

    /** Sneaking with the pebble on the top of a block sends the golem there; on the top of a chest it links instead. */
    @GameTest(template = "arena", timeoutTicks = 600)
    public static void aSneakingPebbleClickSendsTheGolem(GameTestHelper helper) {
        floor(helper);
        GravititeGolem golem = golem(helper, HOME);
        FakePlayer player = player(helper, "golem-sender");
        BlockPos target = HOME.offset(-6, 0, 2), storage = HOME.offset(3, 0, -3);
        chest(helper, storage);
        helper.startSequence().thenIdle(5).thenExecute(() -> {
            takePebble(golem, player);
            player.setShiftKeyDown(true);
            use(helper, player, storage, Direction.UP);
            helper.assertTrue(golem.order() == null, "sneaking on a chest sent the golem");
            helper.assertTrue(net(helper, golem).links().size() == 1, "sneaking on a chest didn't link it");
            use(helper, player, target.below(), Direction.UP);
            player.setShiftKeyDown(false);
            helper.assertTrue(helper.absolutePos(target).equals(golem.order()), "the golem wasn't sent: " + golem.order());
        }).thenWaitUntil(() -> {
            helper.assertTrue(golem.isSeated() && golem.order() == null, "still on its way");
            helper.assertTrue(helper.absolutePos(target).equals(golem.home()), "home didn't move");
        }).thenExecute(() -> leave(helper, player)).thenSucceed();
    }

    /** While it lifts an item out of a chest the golem turns to face that chest. */
    @GameTest(template = "arena", timeoutTicks = 600)
    public static void itFacesTheChestItLiftsFrom(GameTestHelper helper) {
        floor(helper);
        GravititeGolem golem = golem(helper, HOME);
        BlockPos a = new BlockPos(12, 2, 12), b = new BlockPos(20, 2, 12);
        chest(helper, a, join(stacks(Items.COBBLESTONE, 8), stacks(Items.OAK_LOG, 8)));
        chest(helper, b);
        helper.startSequence().thenIdle(5).thenExecute(() -> {
            golem.setYRot(-90);
            link(helper, golem, a, b);
        }).thenWaitUntil(() -> {
            // the chest is 4 west and 4 north of it
            helper.assertTrue(golem.state() == GravititeGolem.CONCENTRATING, "not lifting yet");
            float off = Mth.wrapDegrees(golem.getYRot() - 135);
            helper.assertTrue(Math.abs(off) < 2 && Math.abs(Mth.wrapDegrees(golem.yBodyRot - 135)) < 12,
                    "the golem isn't facing the chest it lifts from: " + golem.getYRot());
        }).thenSucceed();
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

    /** Sent onto a block a step higher, it levitates up and sits there. */
    @GameTest(template = "arena", timeoutTicks = 600)
    public static void itClimbsAStep(GameTestHelper helper) {
        floor(helper);
        for (int x = 12; x <= 20; x++) {
            for (int z = 22; z <= 28; z++) {
                helper.setBlock(new BlockPos(x, 2, z), Blocks.STONE);
            }
        }
        GravititeGolem golem = golem(helper, HOME);
        BlockPos target = new BlockPos(16, 3, 25);
        helper.startSequence().thenIdle(5).thenExecute(() -> golem.sendTo(helper.absolutePos(target), null))
                .thenWaitUntil(() -> {
                    helper.assertTrue(golem.isSeated() && golem.order() == null, "still on its way");
                    helper.assertTrue(helper.absolutePos(target).equals(golem.home()), "didn't get up the step: " + golem.position());
                }).thenSucceed();
    }

    /** Sent across a gap to another island, it floats over and sits there. */
    @GameTest(template = "arena", timeoutTicks = 800)
    public static void itFloatsAcrossAGap(GameTestHelper helper) {
        for (int x = 10; x <= 22; x++) {
            for (int z = 10; z <= 20; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            }
            for (int z = 26; z <= 32; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            }
        }
        GravititeGolem golem = golem(helper, HOME);
        BlockPos target = new BlockPos(16, 2, 29);
        helper.startSequence().thenIdle(5).thenExecute(() -> golem.sendTo(helper.absolutePos(target), null))
                .thenWaitUntil(() -> {
                    helper.assertTrue(golem.isSeated() && golem.order() == null, "still on its way");
                    helper.assertTrue(helper.absolutePos(target).equals(golem.home()), "didn't cross the gap: " + golem.position());
                }).thenSucceed();
    }

    /** Sent somewhere new halfway up a step, it changes course: over the gap beyond, and sits there. */
    @GameTest(template = "arena", timeoutTicks = 1000)
    public static void aNewOrderMidClimbIsFollowed(GameTestHelper helper) {
        floor(helper);
        for (int x = 12; x <= 20; x++) {
            for (int z = 22; z <= 27; z++) {
                helper.setBlock(new BlockPos(x, 2, z), Blocks.STONE);
            }
            for (int z = 33; z <= 37; z++) {
                helper.setBlock(new BlockPos(x, 2, z), Blocks.STONE);
            }
            for (int z = 28; z <= 32; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.AIR);
            }
        }
        GravititeGolem golem = golem(helper, HOME);
        BlockPos step = new BlockPos(16, 3, 25), far = new BlockPos(16, 3, 35);
        helper.startSequence().thenIdle(5).thenExecute(() -> golem.sendTo(helper.absolutePos(step), null))
                .thenWaitUntil(() -> helper.assertTrue(golem.isRising(), "not climbing yet"))
                .thenExecute(() -> golem.sendTo(helper.absolutePos(far), null))
                .thenWaitUntil(() -> {
                    helper.assertTrue(golem.isSeated() && golem.order() == null, "still on its way");
                    helper.assertTrue(helper.absolutePos(far).equals(golem.home()), "didn't get there: " + golem.position());
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
