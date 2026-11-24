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
import com.linc.alteredaether.compat.CreateFunnels;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * How broad each Create filter pinned to storage is: how many of the game's items it lets through. When two strict
 * chests both pin an item, the narrower pin wins it (see {@link com.linc.alteredaether.entity.golem.sort.Planner}).
 *
 * <p>A filter is counted only when asked for, which happens only when it overlaps another chest's pin, and the count
 * is spread over server ticks at a small budget each, so even a pack with tens of thousands of items never stalls a
 * tick. Counts are kept until the server stops.
 */
@EventBusSubscriber(modid = AlteredAether.MOD_ID)
public final class FilterBreadth {
    /** Time spent counting per server tick at most. */
    private static final long BUDGET_NANOS = 500_000;

    private static final Map<Integer, List<Count>> COUNTS = new HashMap<>();
    private static final Deque<Count> PENDING = new ArrayDeque<>();
    private static List<Item> items = List.of();

    private static final class Count {
        final ItemStack filter;
        /** Items tested so far, and how many of those passed. */
        int next, passed;
        boolean done;

        Count(ItemStack filter) {
            this.filter = filter;
        }
    }

    private FilterBreadth() {
    }

    /**
     * How many items {@code filter} lets through (at least 1), or -1 while that is still being counted; asking starts
     * the count.
     */
    public static int breadth(ItemStack filter) {
        int hash = ItemStack.hashItemAndComponents(filter);
        List<Count> same = COUNTS.computeIfAbsent(hash, h -> new ArrayList<>(1));
        for (Count count : same) {
            if (ItemStack.isSameItemSameComponents(count.filter, filter)) {
                return count.done ? Math.max(1, count.passed) : -1;
            }
        }
        Count count = new Count(filter.copyWithCount(1));
        same.add(count);
        PENDING.add(count);
        return -1;
    }

    /** Whether no count is under way. */
    public static boolean idle() {
        return PENDING.isEmpty();
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Pre event) {
        if (PENDING.isEmpty()) {
            return;
        }
        MinecraftServer server = event.getServer();
        if (items.isEmpty()) {
            List<Item> all = new ArrayList<>(BuiltInRegistries.ITEM.size());
            for (Item item : BuiltInRegistries.ITEM) {
                if (item != Items.AIR) {
                    all.add(item);
                }
            }
            items = all;
        }
        long deadline = System.nanoTime() + BUDGET_NANOS;
        while (!PENDING.isEmpty()) {
            Count count = PENDING.peek();
            Predicate<ItemStack> passes = CreateFunnels.matcher(server.overworld(), count.filter);
            while (count.next < items.size()) {
                if (passes.test(items.get(count.next++).getDefaultInstance())) {
                    count.passed++;
                }
                if ((count.next & 63) == 0 && System.nanoTime() > deadline) {
                    return;
                }
            }
            count.done = true;
            PENDING.poll();
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        COUNTS.clear();
        PENDING.clear();
        items = List.of();
    }
}
