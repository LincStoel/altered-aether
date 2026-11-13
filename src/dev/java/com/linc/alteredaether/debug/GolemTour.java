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
package com.linc.alteredaether.debug;

import com.aetherteam.aether.block.AetherBlocks;
import com.linc.alteredaether.AlteredAether;
import com.linc.alteredaether.entity.golem.GolemNetworks;
import com.linc.alteredaether.entity.golem.GolemSorter;
import com.linc.alteredaether.entity.golem.GravititeGolem;
import com.linc.alteredaether.entity.golem.PebbleInfo;
import com.linc.alteredaether.item.PebbleLink;
import com.linc.alteredaether.registry.AARegistries;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * A scripted look at the gravitite golem in a running client, without anyone at the keyboard: with the system property
 * {@code altered_aether.golem_tour} set (the client run's {@code -PaaGolemTour=<label>}), it quick-plays the world
 * "tour", builds a stage of holystone in open sky over the Aether's spawn, and drives the real player through every
 * golem feature, saving {@code screenshots/golem_<label>/NN_name.png} at each moment and logging what the client sees.
 * Then it quits.
 */
public final class GolemTour {
    private static final String LABEL = System.getProperty("altered_aether.golem_tour", "");
    private static final ResourceKey<Level> THE_AETHER =
            ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath("aether", "the_aether"));
    /** High above the Aether's islands, and its airships and villages, and off to the side of spawn. */
    private static final int STAGE_Y = 285, STAGE_OFFSET = 600;

    /** The stage's origin: golem A's seat. Set on the server at login. */
    private static volatile BlockPos origin;
    private static UUID golemA, golemB, golemC;

    /**
     * One moment: what the server sets up, where the camera stands and looks (relative to the origin), how many ticks
     * after the setup to shoot, and whether the HUD shows.
     */
    record Step(String name, Consumer<ServerLevel> setup, Vec3 cam, Vec3 look, int delay, boolean gui, Runnable client) {
        Step(String name, Consumer<ServerLevel> setup, Vec3 cam, Vec3 look, int delay, boolean gui) {
            this(name, setup, cam, look, delay, gui, null);
        }
    }

    private GolemTour() {
    }

    private static boolean on() {
        return !LABEL.isBlank();
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Server: the stage

    @EventBusSubscriber(modid = AlteredAether.MOD_ID)
    public static final class Server {
        @SubscribeEvent
        public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
            if (!on() || !(event.getEntity() instanceof ServerPlayer player)) {
                return;
            }
            MinecraftServer server = player.getServer();
            ServerLevel aether = server.getLevel(THE_AETHER);
            if (aether == null) {
                AlteredAether.LOGGER.error("Golem tour: no Aether");
                return;
            }
            BlockPos spawn = aether.getSharedSpawnPos();
            BlockPos o = new BlockPos(spawn.getX() + STAGE_OFFSET, STAGE_Y, spawn.getZ());
            aether.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
            aether.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
            aether.setDayTime(6000);
            // earlier tours fill the inventory; a full one would drop the pebbles on the floor
            player.getInventory().clearContent();
            player.setGameMode(GameType.CREATIVE);
            player.getAbilities().flying = true;
            player.onUpdateAbilities();
            player.teleportTo(aether, o.getX() + 0.5, o.getY() + 6, o.getZ() + 14.5, 180, 20);
            stage(aether, o);
            origin = o;
            AlteredAether.LOGGER.info("Golem tour: stage at {}", o.toShortString());
        }

        private Server() {
        }
    }

    private static BlockPos at(int x, int y, int z) {
        return origin.offset(x, y, z);
    }

    private static void stage(ServerLevel level, BlockPos o) {
        // clear what an earlier tour left
        AABB area = new AABB(o.offset(-14, -3, -12).getCenter(), o.offset(38, 8, 34).getCenter());
        for (Entity entity : level.getEntitiesOfClass(Entity.class, area, e -> !(e instanceof ServerPlayer))) {
            entity.discard();
        }
        for (BlockPos pos : BlockPos.betweenClosed(o.offset(-14, -3, -12), o.offset(38, 8, 34))) {
            // empty containers first, or breaking them spills an earlier tour's items over the stage
            if (level.getBlockEntity(pos) instanceof net.minecraft.world.Clearable clearable) {
                clearable.clearContent();
            }
            if (!level.getBlockState(pos).isAir()) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2 | 16);
            }
        }
        var holystone = AetherBlocks.HOLYSTONE.get().defaultBlockState();
        // P1, the main floor; P2 across a gap to the east for the second golem; P3 a step up to the south; P4 past a gap
        floor(level, o, -12, 12, -9, 12, -1, holystone);
        floor(level, o, 22, 34, -8, 8, -1, holystone);
        floor(level, o, -4, 4, 13, 19, 0, holystone);
        floor(level, o, -2, 2, 25, 29, 0, holystone);

        // storage in a row behind golem A
        chest(level, o.offset(-5, 0, -4), mixedA());
        chest(level, o.offset(-3, 0, -4), mixedB());
        chest(level, o.offset(-1, 0, -4));
        chest(level, o.offset(1, 0, -4));
        chest(level, o.offset(3, 0, -4));
        frame(level, o.offset(3, 0, -4), Direction.SOUTH, new ItemStack(Items.DIAMOND));
        level.setBlockAndUpdate(o.offset(5, 0, -4), Blocks.BARREL.defaultBlockState());
        // a double chest to the west, and a chest behind a wall the camera never sees through
        level.setBlockAndUpdate(o.offset(-7, 0, 3), Blocks.CHEST.defaultBlockState().setValue(ChestBlock.TYPE, ChestType.LEFT));
        level.setBlockAndUpdate(o.offset(-6, 0, 3), Blocks.CHEST.defaultBlockState().setValue(ChestBlock.TYPE, ChestType.RIGHT));
        fill(level, o.offset(-7, 0, 3), new ItemStack(Items.OAK_LOG, 40), new ItemStack(Items.BONE, 12), new ItemStack(Items.COBBLESTONE, 64));
        chest(level, o.offset(-10, 0, -2), new ItemStack(Items.WHEAT_SEEDS, 30), new ItemStack(Items.IRON_INGOT, 10));
        for (int y = 0; y < 3; y++) {
            for (int z = -4; z <= 0; z++) {
                level.setBlockAndUpdate(o.offset(-9, y, z), holystone);
            }
        }
        // a hopper of gold for the relay, and the far chest pinned to gold
        level.setBlockAndUpdate(o.offset(8, 0, -4), Blocks.HOPPER.defaultBlockState());
        fill(level, o.offset(8, 0, -4), new ItemStack(Items.GOLD_INGOT, 24), new ItemStack(Items.GOLD_NUGGET, 30));
        chest(level, o.offset(30, 0, -4));
        frame(level, o.offset(30, 0, -4), Direction.SOUTH, new ItemStack(Items.GOLD_INGOT));

        golemA = golem(level, o, 0).getUUID();
        golemB = golem(level, o.offset(28, 0, 0), 0).getUUID();
        golemC = golem(level, o.offset(0, 0, 9), 180).getUUID();
    }

    private static void floor(ServerLevel level, BlockPos o, int x0, int x1, int z0, int z1, int y,
                              net.minecraft.world.level.block.state.BlockState state) {
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                level.setBlockAndUpdate(o.offset(x, y, z), state);
            }
        }
    }

    private static ItemStack[] mixedA() {
        List<ItemStack> stacks = new ArrayList<>();
        Item[] items = {Items.COBBLESTONE, Items.OAK_LOG, Items.IRON_INGOT, Items.DIRT, Items.COBBLESTONE, Items.DIAMOND,
                Items.WHEAT_SEEDS, Items.OAK_LOG, Items.BONE, Items.COBBLESTONE, Items.STRING, Items.DIRT,
                Items.FEATHER, Items.OAK_PLANKS, Items.COBBLESTONE, Items.EMERALD, Items.IRON_INGOT, Items.DIRT};
        for (int i = 0; i < items.length; i++) {
            stacks.add(new ItemStack(items[i], Math.min(items[i].getDefaultMaxStackSize(), 10 + i * 3)));
        }
        return stacks.toArray(ItemStack[]::new);
    }

    private static ItemStack[] mixedB() {
        return new ItemStack[]{new ItemStack(Items.OAK_LOG, 64), new ItemStack(Items.COBBLESTONE, 33),
                new ItemStack(Items.DIRT, 64), new ItemStack(Items.REDSTONE, 20), new ItemStack(Items.OAK_LOG, 17),
                new ItemStack(Items.DIAMOND, 3), new ItemStack(Items.APPLE, 6), new ItemStack(Items.DIRT, 5),
                new ItemStack(Items.GLASS, 40)};
    }

    private static void chest(ServerLevel level, BlockPos pos, ItemStack... stacks) {
        level.setBlockAndUpdate(pos, Blocks.CHEST.defaultBlockState());
        fill(level, pos, stacks);
    }

    private static void fill(ServerLevel level, BlockPos pos, ItemStack... stacks) {
        if (level.getBlockEntity(pos) instanceof BaseContainerBlockEntity container) {
            for (int i = 0; i < stacks.length && i < container.getContainerSize(); i++) {
                container.setItem(i, stacks[i]);
            }
        }
    }

    private static void frame(ServerLevel level, BlockPos on, Direction face, ItemStack item) {
        ItemFrame frame = new ItemFrame(level, on.relative(face), face);
        frame.setItem(item, false);
        level.addFreshEntity(frame);
    }

    private static GravititeGolem golem(ServerLevel level, BlockPos at, float yaw) {
        GravititeGolem golem = AARegistries.GRAVITITE_GOLEM.get().create(level);
        golem.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, yaw, 0);
        golem.setYHeadRot(yaw);
        golem.yBodyRot = yaw;
        golem.setHome(at);
        level.addFreshEntity(golem);
        return golem;
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Server: what the player does

    private static ServerPlayer player(ServerLevel level) {
        return level.getServer().getPlayerList().getPlayers().getFirst();
    }

    private static GravititeGolem golem(ServerLevel level, UUID id) {
        return (GravititeGolem) level.getEntity(id);
    }

    /** Takes {@code golem}'s pebble with an empty hand, and holds it. */
    private static void takePebble(ServerLevel level, UUID id) {
        ServerPlayer player = player(level);
        ItemStack held = player.getMainHandItem().copy();
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        if (!held.isEmpty()) {
            player.getInventory().add(held);
        }
        player.setShiftKeyDown(false);
        golem(level, id).interact(player, InteractionHand.MAIN_HAND);
        holdCarried(level, id);
    }

    /** Holds a pebble for {@code id}, made fresh (taking one from a golem is tried once, in the link step). */
    private static void hold(ServerLevel level, UUID id) {
        ServerPlayer player = player(level);
        ItemStack pebble = new ItemStack(AARegistries.STORAGE_PEBBLE.get());
        GravititeGolem golem = golem(level, id);
        pebble.set(AARegistries.PEBBLE_LINK.get(), new PebbleLink(id, level.dimension(),
                golem == null ? "Gravitite Golem" : golem.getName().getString(), false));
        ItemStack held = player.getMainHandItem();
        if (held.is(AARegistries.STORAGE_PEBBLE.get()) && held.get(AARegistries.PEBBLE_LINK.get()) != null
                && held.get(AARegistries.PEBBLE_LINK.get()).golem().equals(id)) {
            return;
        }
        player.setItemInHand(InteractionHand.MAIN_HAND, pebble);
    }

    /** Holds the pebble already carried for {@code id}, as the player would pick it from their inventory. */
    private static void holdCarried(ServerLevel level, UUID id) {
        ServerPlayer player = player(level);
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            PebbleLink link = stack.get(AARegistries.PEBBLE_LINK.get());
            if (link != null && link.golem().equals(id)) {
                ItemStack pebble = stack.copy();
                inventory.setItem(slot, ItemStack.EMPTY);
                ItemStack held = player.getMainHandItem().copy();
                player.setItemInHand(InteractionHand.MAIN_HAND, pebble);
                if (!held.isEmpty()) {
                    inventory.add(held);
                }
                return;
            }
        }
        AlteredAether.LOGGER.error("Golem tour: no pebble carried for {}", id);
    }

    private static void usePebble(ServerLevel level, BlockPos pos, Direction face, boolean sneak) {
        ServerPlayer player = player(level);
        player.setShiftKeyDown(sneak);
        player.gameMode.useItemOn(player, level, player.getMainHandItem(), InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos).relative(face, 0.5), face, pos, false));
        player.setShiftKeyDown(false);
        AlteredAether.LOGGER.info("Golem tour: pebble on {} ({}), container open: {}", pos.toShortString(),
                level.getBlockState(pos).getBlock(), player.containerMenu != player.inventoryMenu);
    }

    private static void useOnGolem(ServerLevel level, UUID id, ItemStack item, boolean sneak) {
        ServerPlayer player = player(level);
        if (item != null) {
            player.setItemInHand(InteractionHand.MAIN_HAND, item);
        }
        player.setShiftKeyDown(sneak);
        golem(level, id).interact(player, InteractionHand.MAIN_HAND);
        player.setShiftKeyDown(false);
    }

    private static void logNetwork(ServerLevel level, String when) {
        GolemNetworks networks = GolemNetworks.get(level);
        for (UUID id : new UUID[]{golemA, golemB, golemC}) {
            GolemNetworks.Net net = networks.netOf(id);
            if (net == null) {
                AlteredAether.LOGGER.info("Golem tour [{}]: golem {} has no network", when, id);
                continue;
            }
            GolemSorter.Report report = GolemSorter.report(level, net);
            GravititeGolem golem = golem(level, id);
            AlteredAether.LOGGER.info("Golem tour [{}]: golem {} net {} mode {} members {} links {} status {} moves {} "
                            + "unplaced {} away {} inUse {}; state {} paused {} home {} at {}", when, id.toString().substring(0, 8),
                    net.id.toString().substring(0, 8), net.mode(), net.members().size(), net.links().size(), report.status(),
                    report.movesLeft(), report.unplaced(), report.outOfReach(), report.inUse(),
                    golem == null ? "-" : golem.state(), golem != null && golem.isPaused(),
                    golem == null || golem.home() == null ? "-" : golem.home().toShortString(),
                    golem == null ? "-" : golem.position());
        }
    }

    /** Every item in every container on the stage, by item, sorted: the same at the start and end, or something was lost. */
    private static java.util.Map<String, Integer> totals(ServerLevel level) {
        java.util.Map<String, Integer> totals = new java.util.TreeMap<>();
        for (BlockPos pos : BlockPos.betweenClosed(at(-14, -1, -12), at(38, 3, 34))) {
            if (level.getBlockEntity(pos) instanceof BaseContainerBlockEntity container) {
                for (int slot = 0; slot < container.getContainerSize(); slot++) {
                    ItemStack stack = container.getItem(slot);
                    if (!stack.isEmpty()) {
                        totals.merge(stack.getItem().builtInRegistryHolder().key().location().getPath(), stack.getCount(), Integer::sum);
                    }
                }
            }
        }
        long loose = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                new AABB(at(-14, -3, -12).getCenter(), at(38, 8, 34).getCenter())).size();
        totals.put("~loose item entities", (int) loose);
        return totals;
    }

    private static void logContents(ServerLevel level) {
        for (BlockPos pos : new BlockPos[]{at(-5, 0, -4), at(-3, 0, -4), at(-1, 0, -4), at(1, 0, -4), at(3, 0, -4),
                at(5, 0, -4), at(-7, 0, 3), at(-6, 0, 3), at(-10, 0, -2), at(8, 0, -4), at(30, 0, -4)}) {
            if (!(level.getBlockEntity(pos) instanceof BaseContainerBlockEntity container)) {
                continue;
            }
            StringBuilder out = new StringBuilder();
            for (int slot = 0; slot < container.getContainerSize(); slot++) {
                ItemStack stack = container.getItem(slot);
                if (!stack.isEmpty()) {
                    out.append(slot).append(':').append(stack.getCount()).append(' ')
                            .append(stack.getItem().builtInRegistryHolder().key().location().getPath()).append(", ");
                }
            }
            AlteredAether.LOGGER.info("Golem tour contents {} {}: {}", level.getBlockState(pos).getBlock().getName().getString(),
                    pos.subtract(origin).toShortString(), out);
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // The script

    private static List<Step> script() {
        List<Step> steps = new ArrayList<>();
        Vec3 golemEye = new Vec3(0.5, 0.75, 0.5);
        steps.add(new Step("overview", level -> {
            // what an earlier tour left in chunks that hadn't loaded at login
            AABB area = new AABB(at(-14, -3, -12).getCenter(), at(38, 8, 34).getCenter());
            for (Entity entity : level.getEntitiesOfClass(Entity.class, area, e -> e instanceof net.minecraft.world.entity.item.ItemEntity
                    || e instanceof Zombie || e instanceof GravititeGolem g && !g.getUUID().equals(golemA)
                    && !g.getUUID().equals(golemB) && !g.getUUID().equals(golemC))) {
                entity.discard();
            }
            logNetwork(level, "start");
            AlteredAether.LOGGER.info("Golem tour totals start: {}", totals(level));
        }, new Vec3(0.5, 7, 13), new Vec3(0.5, 0, -2), 40, false));
        steps.add(new Step("meditate_front", level -> { }, new Vec3(0.5, 1.8, 3.0), golemEye, 20, false));
        steps.add(new Step("meditate_side", level -> { }, new Vec3(3.0, 1.8, 0.8), golemEye, 20, false));
        steps.add(new Step("build_placed", level -> {
            level.setBlockAndUpdate(at(-8, 0, 9), AetherBlocks.HOLYSTONE.get().defaultBlockState());
            level.setBlockAndUpdate(at(-8, 1, 9), AetherBlocks.ENCHANTED_GRAVITITE.get().defaultBlockState());
            ServerPlayer player = player(level);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.CARVED_PUMPKIN));
            BlockPos top = at(-8, 1, 9);
            player.gameMode.useItemOn(player, level, player.getMainHandItem(), InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(top).add(0, 0.5, 0), Direction.UP, top, false));
        }, new Vec3(-7.5, 2, 13.5), new Vec3(-7.5, 1.0, 9.5), 3, false));
        steps.add(new Step("build_done", level -> {
            List<GravititeGolem> built = level.getEntitiesOfClass(GravititeGolem.class, new AABB(at(-8, 0, 9)).inflate(1));
            AlteredAether.LOGGER.info("Golem tour: built {} golem(s); blocks now {} {} {}", built.size(),
                    level.getBlockState(at(-8, 0, 9)), level.getBlockState(at(-8, 1, 9)), level.getBlockState(at(-8, 2, 9)));
        }, new Vec3(-7.5, 2, 13.5), new Vec3(-7.5, 1.0, 9.5), 30, false));
        steps.add(new Step("link_glow", level -> {
            takePebble(level, golemA);
            for (BlockPos pos : new BlockPos[]{at(-5, 0, -4), at(-3, 0, -4), at(-1, 0, -4), at(1, 0, -4), at(3, 0, -4),
                    at(5, 0, -4), at(-7, 0, 3), at(-10, 0, -2)}) {
                usePebble(level, pos, Direction.UP, false);
            }
            // a furnace is refused
            level.setBlockAndUpdate(at(-1, 0, 6), Blocks.FURNACE.defaultBlockState());
            usePebble(level, at(-1, 0, 6), Direction.UP, false);
            logNetwork(level, "linked");
        }, new Vec3(0.5, 4.5, 5), new Vec3(-0.5, 0, -4), 8, true));
        steps.add(new Step("sorting_1", level -> logNetwork(level, "sorting"), new Vec3(5, 3, 2.5), new Vec3(-1.5, 0.8, -4), 30, false,
                GolemTour::logClient));
        for (int i = 0; i < 6; i++) {
            // items in the air, close, every quarter second
            steps.add(new Step("flight_" + i, level -> { }, new Vec3(1.5, 2.4, -0.5), new Vec3(-1.5, 1.2, -4), 5, false));
        }
        steps.add(new Step("sorting_2", level -> { }, new Vec3(5, 3, 2.5), new Vec3(-1.5, 0.8, -4), 12, false));
        steps.add(new Step("sorting_3", level -> { }, new Vec3(5, 3, 2.5), new Vec3(-1.5, 0.8, -4), 12, false, GolemTour::logClient));
        steps.add(new Step("sorting_close", level -> { }, new Vec3(-1, 1.8, -1.2), new Vec3(-3, 1.2, -4), 10, false));
        steps.add(new Step("sorting_golem", level -> { }, new Vec3(0.5, 1.8, 2.6), golemEye, 8, false, GolemTour::logClient));
        steps.add(new Step("concentrate_front", level -> { }, new Vec3(0.5, 1.8, 3.4), golemEye, 8, false));
        steps.add(new Step("concentrate_side", level -> { }, new Vec3(3.2, 1.8, 1.5), golemEye, 8, false));
        steps.add(new Step("show_links", level -> {
            hold(level, golemA);
            useOnGolem(level, golemA, null, false);
        }, new Vec3(8, 5, 7), new Vec3(-2, 0, -1), 10, false));
        steps.add(new Step("hints_ring", level -> hold(level, golemA), new Vec3(-6, 5, 22), new Vec3(4, 0, 12), 30, true,
                GolemTour::logClient));
        steps.add(new Step("hint_text_linked", level -> { }, new Vec3(-0.5, 2, -1.6), new Vec3(-0.5, 0.6, -3.5), 12, true));
        steps.add(new Step("hint_text_furnace", level -> { }, new Vec3(-0.5, 2, 7.6), new Vec3(-0.5, 0.6, 6.5), 12, true));
        steps.add(new Step("search", level -> {
            useOnGolem(level, golemA, new ItemStack(Items.OAK_LOG), false);
        }, new Vec3(5, 4, 5), new Vec3(-2, 0, -3), 8, true));
        steps.add(new Step("search_none", level -> {
            useOnGolem(level, golemA, new ItemStack(Items.NETHER_STAR), false);
        }, new Vec3(0.5, 1.8, 2.8), golemEye, 8, true));
        steps.add(new Step("mode_compact", level -> useOnGolem(level, golemA, ItemStack.EMPTY, true),
                new Vec3(0.5, 1.8, 2.9), golemEye, 6, true));
        steps.add(new Step("mode_tidy", level -> useOnGolem(level, golemA, ItemStack.EMPTY, true),
                new Vec3(0.5, 1.8, 2.9), golemEye, 6, true));
        steps.add(new Step("jade_golem", level -> player(level).setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY),
                new Vec3(0.5, 1.8, 2.2), new Vec3(0.5, 0.6, 0.5), 20, true));
        steps.add(new Step("jade_chest", level -> { }, new Vec3(3.5, 1.8, -2.0), new Vec3(3.5, 0.5, -3.5), 20, true));
        steps.add(new Step("pair_link", level -> {
            takePebble(level, golemB);
            useOnGolem(level, golemA, null, false);
            hold(level, golemA);
            usePebble(level, at(8, 0, -4), Direction.UP, false);
            usePebble(level, at(30, 0, -4), Direction.UP, false);
            useOnGolem(level, golemA, null, false);
            logNetwork(level, "paired");
        }, new Vec3(14, 14, 20), new Vec3(14, 0, -2), 12, true));
        steps.add(new Step("relay_1", level -> { }, new Vec3(18, 9, 12), new Vec3(18, 1, -3), 30, false));
        steps.add(new Step("relay_2", level -> { }, new Vec3(18, 9, 12), new Vec3(18, 1, -3), 15, false));
        steps.add(new Step("relay_3", level -> logNetwork(level, "relay"), new Vec3(18, 9, 12), new Vec3(18, 1, -3), 15, false));
        steps.add(new Step("repel_hit", level -> {
            Zombie zombie = EntityType.ZOMBIE.create(level);
            zombie.moveTo(at(0, 0, 9).getX() + 2.0, at(0, 0, 9).getY(), at(0, 0, 9).getZ() + 0.5, 90, 0);
            level.addFreshEntity(zombie);
            golem(level, golemC).hurt(level.damageSources().mobAttack(zombie), 1);
        }, new Vec3(0.5, 2.5, 14), new Vec3(1.0, 0.8, 9.5), 6, false));
        // the push peaks at ~6 ticks, the arms are held up to ~13, and settle by ~21
        steps.add(new Step("repel_held", level -> { }, new Vec3(0.5, 2.5, 14), new Vec3(1.0, 0.8, 9.5), 5, false));
        steps.add(new Step("repel_after", level -> { }, new Vec3(0.5, 2.5, 14), new Vec3(1.0, 0.8, 9.5), 14, false));
        steps.add(new Step("paused", level -> {
            level.getEntitiesOfClass(Zombie.class, new AABB(at(0, 0, 9)).inflate(20)).forEach(Entity::discard);
            level.setBlockAndUpdate(at(-1, 0, 9), Blocks.REDSTONE_BLOCK.defaultBlockState());
        },
                new Vec3(0.5, 1.8, 6.8), new Vec3(0.5, 0.7, 9.5), 25, false,
                () -> AlteredAether.LOGGER.info("Golem tour client: golem C paused {}", clientGolem(golemC) != null
                        && clientGolem(golemC).isPaused())));
        steps.add(new Step("send_marker", level -> {
            level.setBlockAndUpdate(at(-1, 0, 9), Blocks.AIR.defaultBlockState());
            takePebble(level, golemC);
            usePebble(level, at(0, 0, 16), Direction.UP, true);
        }, new Vec3(6, 4, 11), new Vec3(0.5, 0.8, 13), 6, true));
        // the stand-up takes 4 s before it sets off, and the sit-down 4 s more once it arrives
        Vec3 standCam = new Vec3(3.5, 2.5, 7.5), standLook = new Vec3(0.5, 1, 9.5);
        steps.add(new Step("stand_up_1", level -> { }, standCam, standLook, 20, false, GolemTour::logClient));
        steps.add(new Step("stand_up_2", level -> { }, standCam, standLook, 20, false, GolemTour::logClient));
        steps.add(new Step("stand_up_3", level -> { }, standCam, standLook, 25, false, GolemTour::logClient));
        steps.add(new Step("walk_1", level -> { }, new Vec3(6, 2.5, 12), new Vec3(0.5, 1, 12.5), 20, false, GolemTour::logClient));
        steps.add(new Step("walk_2", level -> { }, new Vec3(6, 2.5, 12), new Vec3(0.5, 1, 12.5), 14, false, GolemTour::logClient));
        steps.add(new Step("walk_3", level -> { }, new Vec3(6, 2.5, 12), new Vec3(0.5, 1, 12.5), 14, false, GolemTour::logClient));
        Vec3 sitCam = new Vec3(4, 2.5, 15), sitLook = new Vec3(0.5, 1, 16.5);
        steps.add(new Step("sit_down_1", level -> { }, sitCam, sitLook, 30, false, GolemTour::logClient));
        steps.add(new Step("sit_down_2", level -> { }, sitCam, sitLook, 30, false, GolemTour::logClient));
        steps.add(new Step("step_arrived", level -> logNetwork(level, "stepped"), new Vec3(6, 3, 15), new Vec3(0.5, 1, 16.5), 60, false));
        steps.add(new Step("float_1", level -> {
            hold(level, golemC);
            usePebble(level, at(0, 0, 27), Direction.UP, true);
        }, new Vec3(8, 4, 22), new Vec3(0.5, 1.5, 22), 100, false, GolemTour::logClient));
        steps.add(new Step("float_2", level -> { }, new Vec3(8, 4, 22), new Vec3(0.5, 1.5, 22), 25, false, GolemTour::logClient));
        steps.add(new Step("float_3", level -> { }, new Vec3(8, 4, 22), new Vec3(0.5, 1.5, 22), 25, false, GolemTour::logClient));
        steps.add(new Step("float_arrived", level -> { }, new Vec3(6, 4, 31), new Vec3(0.5, 1, 27.5), 140, false,
                GolemTour::logClient));
        steps.add(new Step("dull_pebble", level -> {
            logNetwork(level, "before kill");
            hold(level, golemC);
            golem(level, golemC).kill();
        }, new Vec3(0.5, 3, 31), new Vec3(0.5, 1, 27.5), 80, true, GolemTour::logTooltips));
        steps.add(new Step("tooltip", level -> {
            // keep the dull pebble beside the live one
            ServerPlayer player = player(level);
            player.getInventory().add(player.getMainHandItem().copy());
            hold(level, golemA);
            // the survival inventory: a creative player's opens the creative one instead
            player.setGameMode(GameType.SURVIVAL);
        }, new Vec3(0.5, 7, 13), new Vec3(0.5, 0, -2), 25, true,
                GolemTour::openInventory));
        steps.add(new Step("jei_info", level -> {
            ServerPlayer player = player(level);
            player.setGameMode(GameType.CREATIVE);
            player.getAbilities().flying = true;
            player.onUpdateAbilities();
        }, new Vec3(0.5, 7, 13), new Vec3(0.5, 0, -2), 30, true, GolemTour::showJei));
        steps.add(new Step("final_sorted", level -> {
            logNetwork(level, "final");
            logContents(level);
            AlteredAether.LOGGER.info("Golem tour totals end: {}", totals(level));
        }, new Vec3(0.5, 4.5, 4), new Vec3(-0.5, 0, -4), 20, false, () -> Minecraft.getInstance().setScreen(null)));
        steps.add(new Step("night_show_links", level -> {
            level.setDayTime(18000);
            hold(level, golemA);
            useOnGolem(level, golemA, null, false);
        }, new Vec3(14, 12, 18), new Vec3(10, 0, -2), 12, false));
        return steps;
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Client: driving the camera and shooting

    @EventBusSubscriber(modid = AlteredAether.MOD_ID, value = Dist.CLIENT)
    public static final class Client {
        private static List<Step> steps;
        private static int index = -1, waited, settled;
        /** Frames drawn from where the current shot wants the camera: a screenshot takes the last frame drawn. */
        private static int framesHere;
        private static Vec3 wanted;

        @SubscribeEvent
        public static void onRender(net.neoforged.neoforge.client.event.RenderLevelStageEvent event) {
            Vec3 want = wanted;
            if (want != null && event.getStage() == net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage.AFTER_LEVEL
                    && event.getCamera().getPosition().distanceToSqr(want) < 1.0E-4) {
                framesHere++;
            }
        }

        @SubscribeEvent
        public static void onTick(ClientTickEvent.Post event) {
            Minecraft mc = Minecraft.getInstance();
            BlockPos o = origin;
            if (!on() || o == null || mc.player == null || mc.level == null || !mc.level.dimension().equals(THE_AETHER)) {
                return;
            }
            MinecraftServer server = mc.getSingleplayerServer();
            if (steps == null) {
                steps = script();
            }
            if (index < 0) {
                // wait for the stage to be drawn before anything
                boolean ready = mc.levelRenderer.hasRenderedAllSections() && mc.level.getBlockState(o.below()).isSolid();
                settled = ready ? settled + 1 : 0;
                mc.player.moveTo(o.getX() + 0.5, o.getY() + 7, o.getZ() + 13, 180, 20);
                if (settled < 80) {
                    return;
                }
                next(mc, server);
                return;
            }
            Step step = steps.get(index);
            Vec3 cam = Vec3.atLowerCornerOf(o).add(step.cam());
            Vec3 look = Vec3.atLowerCornerOf(o).add(step.look());
            double dx = look.x - cam.x, dy = look.y - cam.y, dz = look.z - cam.z;
            float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90;
            float pitch = (float) -(Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * Mth.RAD_TO_DEG);
            // the player's eye is at the camera
            mc.player.moveTo(cam.x, cam.y - mc.player.getEyeHeight(), cam.z, yaw, pitch);
            mc.player.setYHeadRot(yaw);
            mc.player.setDeltaMovement(Vec3.ZERO);
            mc.options.hideGui = !step.gui();
            if (wanted == null || wanted.distanceToSqr(cam) > 1.0E-6) {
                wanted = cam;
                framesHere = 0;
            }
            if (++waited < step.delay()) {
                return;
            }
            if (step.client() != null) {
                step.client().run();
            }
            if (waited < step.delay() + (step.client() != null ? 3 : 0)) {
                return;
            }
            // wait for frames from here: when the game stops drawing for a moment, the last frame is from elsewhere
            if (framesHere < 3 && waited < step.delay() + 400) {
                return;
            }
            String dir = "screenshots/golem_" + LABEL;
            new java.io.File(mc.gameDirectory, dir).mkdirs();
            String file = "golem_" + LABEL + "/" + String.format("%02d_%s.png", index, step.name());
            AlteredAether.LOGGER.info("Golem tour: shooting {} from {} (wanted {}); render camera {} entity {} type {} fov {} x{}",
                    step.name(), mc.player.getEyePosition(), cam, mc.gameRenderer.getMainCamera().getPosition(),
                    mc.getCameraEntity() == null ? "-" : mc.getCameraEntity().getType().builtInRegistryHolder().key().location(),
                    mc.options.getCameraType(), mc.options.fov().get(), mc.player.getFieldOfViewModifier());
            Screenshot.grab(mc.gameDirectory, file, mc.getMainRenderTarget(),
                    message -> AlteredAether.LOGGER.info("Golem tour: {}", message.getString()));
            next(mc, server);
        }

        private static void next(Minecraft mc, MinecraftServer server) {
            index++;
            waited = 0;
            if (index >= steps.size()) {
                AlteredAether.LOGGER.info("Golem tour: done");
                origin = null;
                mc.stop();
                return;
            }
            Step step = steps.get(index);
            AlteredAether.LOGGER.info("Golem tour: step {} {}", index, step.name());
            if (server != null) {
                server.execute(() -> {
                    ServerLevel aether = server.getLevel(THE_AETHER);
                    if (aether != null) {
                        try {
                            step.setup().accept(aether);
                        } catch (RuntimeException e) {
                            AlteredAether.LOGGER.error("Golem tour: step {} failed", step.name(), e);
                        }
                    }
                });
            }
        }

        private Client() {
        }
    }

    private static GravititeGolem clientGolem(UUID id) {
        Minecraft mc = Minecraft.getInstance();
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof GravititeGolem golem && golem.getUUID().equals(id)) {
                return golem;
            }
        }
        return null;
    }

    private static void logClient() {
        Minecraft mc = Minecraft.getInstance();
        for (UUID id : new UUID[]{golemA, golemB, golemC}) {
            GravititeGolem golem = clientGolem(id);
            if (golem == null) {
                continue;
            }
            AlteredAether.LOGGER.info("Golem tour client: {} state {} floating {} rising {} paused {} pose {} meditate {} "
                            + "concentrate {} floating {} flightBounds {} at {}", id.toString().substring(0, 8),
                    golem.state(), golem.isFloating(), golem.isRising(), golem.isPaused(), golem.getPose(),
                    golem.meditate, golem.concentrate, golem.floating, golem.flightBounds != null,
                    golem.position());
        }
        AlteredAether.LOGGER.info("Golem tour client: hums playing {}",
                com.linc.alteredaether.client.golem.GolemClient.Events.hums());
        ItemStack held = mc.player.getMainHandItem();
        PebbleLink link = held.get(AARegistries.PEBBLE_LINK.get());
        if (link != null) {
            PebbleInfo.Info info = PebbleInfo.get(link.golem());
            AlteredAether.LOGGER.info("Golem tour client: pebble info {}", info == null ? "none" : info.name() + " mode "
                    + info.mode() + " linked " + info.linked() + "/" + info.maxLinks() + " homes " + info.homes().size()
                    + " links " + info.links().size() + " distance " + info.distance());
        }
    }

    private static void logTooltips() {
        Minecraft mc = Minecraft.getInstance();
        var inventory = mc.player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(AARegistries.STORAGE_PEBBLE.get())) {
                List<String> lines = stack.getTooltipLines(Item.TooltipContext.of(mc.level), mc.player, TooltipFlag.NORMAL)
                        .stream().map(c -> c.getString()).toList();
                AlteredAether.LOGGER.info("Golem tour client: pebble slot {} dead {}: {}", slot,
                        stack.get(AARegistries.PEBBLE_LINK.get()).dead(), lines);
            }
        }
    }

    /** The inventory, with the mouse over the held pebble, so its tooltip shows. */
    private static void openInventory() {
        Minecraft mc = Minecraft.getInstance();
        logTooltips();
        InventoryScreen screen = new InventoryScreen(mc.player);
        mc.setScreen(screen);
        int selected = mc.player.getInventory().selected;
        int left = (screen.width - 176) / 2, top = (screen.height - 166) / 2;
        double scale = mc.getWindow().getGuiScale();
        GLFW.glfwSetCursorPos(mc.getWindow().getWindow(), (left + 8 + selected * 18 + 8) * scale, (top + 142 + 8) * scale);
    }

    private static void showJei() {
        if (ModList.get().isLoaded("jei")) {
            GolemTourJei.showPebble();
        } else {
            AlteredAether.LOGGER.info("Golem tour: no JEI");
        }
    }
}
