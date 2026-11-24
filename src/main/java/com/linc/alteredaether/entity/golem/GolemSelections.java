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
package com.linc.alteredaether.entity.golem;

import com.linc.alteredaether.AlteredAether;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * What each player has picked out in the Golem Chest Network screen, per network: up to one item for each dye colour,
 * the first in the network's own colour. Each picked item makes the storage holding it glow in its colour for
 * {@link #GLOW_TICKS}, fading; the glow leaves a chest as soon as the item does. Picks last until cleared (kept while
 * the server runs, across closing and reopening the screen, which lights them again).
 */
@EventBusSubscriber(modid = AlteredAether.MOD_ID)
public final class GolemSelections {
    /** The most items picked at once: one for each dye colour. */
    public static final int MAX = DyeColor.values().length;
    public static final int GLOW_TICKS = 600;
    /** The colours after the network's own, in an order that keeps neighbours apart. */
    private static final DyeColor[] ORDER = {DyeColor.RED, DyeColor.LIME, DyeColor.BLUE, DyeColor.YELLOW,
            DyeColor.MAGENTA, DyeColor.CYAN, DyeColor.ORANGE, DyeColor.PURPLE, DyeColor.GREEN, DyeColor.PINK,
            DyeColor.LIGHT_BLUE, DyeColor.BROWN, DyeColor.WHITE, DyeColor.LIGHT_GRAY, DyeColor.GRAY, DyeColor.BLACK};
    /** Ticks between checks of where glowing items are while storage keeps changing. */
    private static final int CHECK_INTERVAL = 5;

    private static final Map<UUID, Map<UUID, Selection>> ALL = new HashMap<>();

    private GolemSelections() {
    }

    /** One picked item, its colour, and the game tick its glow ends. */
    public record Pick(ItemStack item, DyeColor color, long glowUntil) {
    }

    /** A player's picks in one network, and what was last sent of their glow. */
    static final class Selection {
        final UUID net;
        ResourceKey<Level> dimension;
        final List<Pick> picks = new ArrayList<>();
        int checkedVersion = Integer.MIN_VALUE;
        long checkedTick = Long.MIN_VALUE;
        boolean dirty = true;
        List<List<BlockPos>> sent = List.of();

        Selection(UUID net, ResourceKey<Level> dimension) {
            this.net = net;
            this.dimension = dimension;
        }

        boolean glowing(long now) {
            for (Pick pick : picks) {
                if (pick.glowUntil() > now) {
                    return true;
                }
            }
            return false;
        }
    }

    /** {@code player}'s picks in {@code net}. */
    public static List<Pick> picks(ServerPlayer player, UUID net) {
        Map<UUID, Selection> byNet = ALL.get(player.getUUID());
        Selection selection = byNet == null ? null : byNet.get(net);
        return selection == null ? List.of() : List.copyOf(selection.picks);
    }

    private static Selection selection(ServerPlayer player, GolemNetworks.Net net, ServerLevel level) {
        Selection selection = ALL.computeIfAbsent(player.getUUID(), k -> new HashMap<>())
                .computeIfAbsent(net.id, id -> new Selection(id, level.dimension()));
        selection.dimension = level.dimension();
        return selection;
    }

    /** The screen was opened: every pick glows again for the full time. */
    static void opened(ServerPlayer player, GolemNetworks.Net net, ServerLevel level) {
        Selection selection = selection(player, net, level);
        long until = level.getGameTime() + GLOW_TICKS;
        selection.picks.replaceAll(pick -> new Pick(pick.item(), pick.color(), until));
        selection.dirty = true;
    }

    /** Picks {@code item}, or drops it if it was picked. Returns false if it couldn't be picked: all colours taken. */
    public static boolean toggle(ServerPlayer player, GolemNetworks.Net net, ServerLevel level, ItemStack item) {
        Selection selection = selection(player, net, level);
        for (Iterator<Pick> it = selection.picks.iterator(); it.hasNext(); ) {
            if (ItemStack.isSameItemSameComponents(it.next().item(), item)) {
                it.remove();
                selection.dirty = true;
                return true;
            }
        }
        if (selection.picks.size() >= MAX || item.isEmpty()) {
            return false;
        }
        add(selection, net, item, level.getGameTime() + GLOW_TICKS);
        selection.dirty = true;
        return true;
    }

    static void clear(ServerPlayer player, GolemNetworks.Net net, ServerLevel level) {
        Selection selection = selection(player, net, level);
        selection.picks.clear();
        selection.dirty = true;
    }

    /** Replaces the picks with {@code items} (the first {@link #MAX} different ones). */
    public static void replace(ServerPlayer player, GolemNetworks.Net net, ServerLevel level, List<ItemStack> items) {
        Selection selection = selection(player, net, level);
        selection.picks.clear();
        long until = level.getGameTime() + GLOW_TICKS;
        for (ItemStack item : items) {
            if (selection.picks.size() >= MAX) {
                break;
            }
            if (!item.isEmpty() && selection.picks.stream().noneMatch(p -> ItemStack.isSameItemSameComponents(p.item(), item))) {
                add(selection, net, item, until);
            }
        }
        selection.dirty = true;
    }

    private static void add(Selection selection, GolemNetworks.Net net, ItemStack item, long until) {
        Set<DyeColor> taken = new LinkedHashSet<>();
        selection.picks.forEach(pick -> taken.add(pick.color()));
        selection.picks.add(new Pick(item.copyWithCount(1), firstFree(net.color(), taken), until));
    }

    /** The first colour not taken: the network's own, then {@link #ORDER}. */
    public static DyeColor firstFree(DyeColor own, Set<DyeColor> taken) {
        if (!taken.contains(own)) {
            return own;
        }
        for (DyeColor color : ORDER) {
            if (color != own && !taken.contains(color)) {
                return color;
            }
        }
        return own;
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (ALL.isEmpty()) {
            return;
        }
        MinecraftServer server = event.getServer();
        for (Iterator<Map.Entry<UUID, Map<UUID, Selection>>> players = ALL.entrySet().iterator(); players.hasNext(); ) {
            Map.Entry<UUID, Map<UUID, Selection>> entry = players.next();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                continue;
            }
            for (Selection selection : entry.getValue().values()) {
                update(server, player, selection);
            }
        }
    }

    /** Sends where each glowing pick is now, if that changed. */
    private static void update(MinecraftServer server, ServerPlayer player, Selection selection) {
        ServerLevel level = server.getLevel(selection.dimension);
        if (level == null) {
            return;
        }
        long now = level.getGameTime();
        boolean glowing = selection.glowing(now);
        if (!glowing && !selection.dirty) {
            return;
        }
        GolemNetworks.Net net = GolemNetworks.get(level).net(selection.net);
        int version = net == null ? -1 : GolemSorter.contentsVersion(level, net.id);
        boolean due = selection.dirty || version != selection.checkedVersion && now - selection.checkedTick >= CHECK_INTERVAL;
        if (!due) {
            return;
        }
        selection.checkedVersion = version;
        selection.checkedTick = now;
        boolean forced = selection.dirty;
        selection.dirty = false;
        List<ItemStack> items = new ArrayList<>();
        for (Pick pick : selection.picks) {
            items.add(pick.item());
        }
        List<List<BlockPos>> where = net == null || !player.level().dimension().equals(selection.dimension)
                ? emptyLists(items.size()) : GolemNetworkView.holding(level, net, items);
        if (!forced && where.equals(selection.sent)) {
            return;
        }
        selection.sent = where;
        List<GolemPayloads.SelectionGlow.Lit> lit = new ArrayList<>();
        for (int i = 0; i < selection.picks.size(); i++) {
            Pick pick = selection.picks.get(i);
            int left = (int) Math.max(0, pick.glowUntil() - now);
            if (left > 0 && !where.get(i).isEmpty()) {
                lit.add(new GolemPayloads.SelectionGlow.Lit(pick.color(), left, where.get(i)));
            }
        }
        PacketDistributor.sendToPlayer(player, new GolemPayloads.SelectionGlow(selection.net, lit));
    }

    private static List<List<BlockPos>> emptyLists(int n) {
        List<List<BlockPos>> lists = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            lists.add(List.of());
        }
        return lists;
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        ALL.clear();
    }
}
