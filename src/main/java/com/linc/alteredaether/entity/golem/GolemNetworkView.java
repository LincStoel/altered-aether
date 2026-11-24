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

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Nameable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What a golem network's storage holds, for the Golem Chest Network screen: every loaded linked inventory but machines
 * fed through funnels, nearest the golem first, and each kind of item in them (stacks that differ in any data are
 * kinds apart) with how many there are in all and in each inventory. A double chest is one inventory.
 */
public final class GolemNetworkView {
    private GolemNetworkView() {
    }

    /**
     * One inventory: where it is (one of its blocks), what to call it, its block as an item, and what it is kept for:
     * the family tidy mode last gave it (a path such as {@code overworld/blocks/wood/spruce}, or empty), or the item
     * pinned to it (or empty).
     */
    public record Chest(BlockPos pos, Component name, ItemStack icon, String family, String pinned) {
    }

    /**
     * One kind of item: a single of it, how many there are, and the inventories holding it (indices into the chest
     * list, in its order) with how many each holds.
     */
    public record Entry(ItemStack item, long total, int[] chests, long[] counts) {
        public Entry {
            item = item.copyWithCount(1);
        }
    }

    public record Contents(List<Chest> chests, List<Entry> entries) {
        public static final Contents EMPTY = new Contents(List.of(), List.of());

        static Contents decode(RegistryFriendlyByteBuf buf) {
            int chestCount = buf.readVarInt();
            List<Chest> chests = new ArrayList<>(chestCount);
            for (int i = 0; i < chestCount; i++) {
                chests.add(new Chest(buf.readBlockPos(), ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf),
                        ItemStack.OPTIONAL_STREAM_CODEC.decode(buf), buf.readUtf(), buf.readUtf()));
            }
            int entryCount = buf.readVarInt();
            List<Entry> entries = new ArrayList<>(entryCount);
            for (int i = 0; i < entryCount; i++) {
                ItemStack item = ItemStack.STREAM_CODEC.decode(buf);
                long total = buf.readVarLong();
                int n = buf.readVarInt();
                int[] where = new int[n];
                long[] counts = new long[n];
                for (int j = 0; j < n; j++) {
                    where[j] = buf.readVarInt();
                    counts[j] = buf.readVarLong();
                }
                entries.add(new Entry(item, total, where, counts));
            }
            return new Contents(chests, entries);
        }

        void encode(RegistryFriendlyByteBuf buf) {
            buf.writeVarInt(chests.size());
            for (Chest chest : chests) {
                buf.writeBlockPos(chest.pos());
                ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, chest.name());
                ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, chest.icon());
                buf.writeUtf(chest.family());
                buf.writeUtf(chest.pinned());
            }
            buf.writeVarInt(entries.size());
            for (Entry entry : entries) {
                ItemStack.STREAM_CODEC.encode(buf, entry.item());
                buf.writeVarLong(entry.total());
                buf.writeVarInt(entry.chests().length);
                for (int j = 0; j < entry.chests().length; j++) {
                    buf.writeVarInt(entry.chests()[j]);
                    buf.writeVarLong(entry.counts()[j]);
                }
            }
        }
    }

    /** An item without its count, compared and hashed as stacks are merged. */
    public static final class StackKey {
        final ItemStack stack;
        private final int hash;

        public StackKey(ItemStack stack) {
            this.stack = stack;
            this.hash = ItemStack.hashItemAndComponents(stack);
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof StackKey other && ItemStack.isSameItemSameComponents(stack, other.stack);
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }

    /** A loaded linked inventory, with one of its blocks, its part, and its handler. */
    private record Found(BlockPos pos, long key, GolemStorage.Role role, IItemHandler handler) {
    }

    /** {@code net}'s loaded linked storage, machines left out, a double chest once, nearest {@code from} first. */
    private static List<Found> storage(ServerLevel level, GolemNetworks.Net net, BlockPos from) {
        List<Found> found = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (BlockPos pos : net.links()) {
            if (!level.isLoaded(pos)) {
                continue;
            }
            GolemStorage.Role role = GolemStorage.role(level, pos);
            if (role == GolemStorage.Role.NONE || role == GolemStorage.Role.SINK) {
                continue;
            }
            long key = GolemStorage.inventoryKey(level, pos);
            if (!seen.add(key)) {
                continue;
            }
            IItemHandler handler = GolemStorage.handler(level, pos);
            if (handler != null) {
                found.add(new Found(pos.immutable(), key, role, handler));
            }
        }
        found.sort(Comparator.comparingDouble(f -> f.pos().distSqr(from)));
        return found;
    }

    /** What {@code net}'s storage holds now, its inventories ordered nearest {@code from} first. */
    public static Contents build(ServerLevel level, GolemNetworks.Net net, BlockPos from) {
        List<Found> storage = storage(level, net, from);
        List<Chest> chests = new ArrayList<>(storage.size());
        Map<StackKey, Tally> tallies = new LinkedHashMap<>();
        Map<StackKey, Long> here = new LinkedHashMap<>();
        for (int c = 0; c < storage.size(); c++) {
            Found found = storage.get(c);
            chests.add(chest(level, net, found));
            here.clear();
            IItemHandler handler = found.handler();
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                ItemStack stack = handler.getStackInSlot(slot);
                if (!stack.isEmpty()) {
                    here.merge(new StackKey(stack), (long) stack.getCount(), Long::sum);
                }
            }
            for (Map.Entry<StackKey, Long> held : here.entrySet()) {
                tallies.computeIfAbsent(held.getKey(), k -> new Tally()).add(c, held.getValue());
            }
        }
        List<Entry> entries = new ArrayList<>(tallies.size());
        tallies.forEach((key, tally) -> entries.add(tally.entry(key.stack)));
        return new Contents(chests, entries);
    }

    /**
     * Where {@code net}'s storage holds each of {@code items}: for each, every block of each inventory holding it (both
     * halves of a double chest), in the order given; empty where none is held.
     */
    public static List<List<BlockPos>> holding(ServerLevel level, GolemNetworks.Net net, List<ItemStack> items) {
        List<List<BlockPos>> where = new ArrayList<>(items.size());
        for (int i = 0; i < items.size(); i++) {
            where.add(new ArrayList<>());
        }
        if (items.isEmpty()) {
            return where;
        }
        Map<StackKey, Integer> wanted = new LinkedHashMap<>();
        for (int i = 0; i < items.size(); i++) {
            wanted.putIfAbsent(new StackKey(items.get(i)), i);
        }
        for (Found found : storage(level, net, BlockPos.ZERO)) {
            boolean[] has = new boolean[items.size()];
            IItemHandler handler = found.handler();
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                ItemStack stack = handler.getStackInSlot(slot);
                if (!stack.isEmpty()) {
                    Integer index = wanted.get(new StackKey(stack));
                    if (index != null) {
                        has[index] = true;
                    }
                }
            }
            for (int i = 0; i < has.length; i++) {
                if (has[i]) {
                    where.get(i).add(found.pos());
                    BlockPos other = GolemStorage.partner(level, found.pos());
                    if (other != null) {
                        where.get(i).add(other);
                    }
                }
            }
        }
        return where;
    }

    private static Chest chest(ServerLevel level, GolemNetworks.Net net, Found found) {
        BlockPos pos = found.pos();
        GolemStorage.Pins pins = GolemStorage.pins(level, pos);
        String pinned = "";
        if (!pins.isEmpty()) {
            ItemStack first = pins.items().isEmpty() ? pins.filters().getFirst() : pins.items().getFirst();
            pinned = first.getHoverName().getString();
        }
        // the family tidy mode keeps it for, as last planned (as Jade shows it)
        String claim = net.claims().get(found.key());
        String family = claim != null && pins.isEmpty()
                && (found.role() == GolemStorage.Role.STORAGE || found.role() == GolemStorage.Role.MISC) ? claim : "";
        return new Chest(pos, name(level, pos), new ItemStack(level.getBlockState(pos).getBlock().asItem()), family, pinned);
    }

    /** What to call the inventory at {@code pos}: its custom name, or its block's name. */
    private static Component name(ServerLevel level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof Nameable nameable && nameable.hasCustomName() && nameable.getCustomName() != null) {
            return nameable.getCustomName();
        }
        BlockPos other = GolemStorage.partner(level, pos);
        if (other != null && level.getBlockEntity(other) instanceof Nameable nameable && nameable.hasCustomName()
                && nameable.getCustomName() != null) {
            return nameable.getCustomName();
        }
        return level.getBlockState(pos).getBlock().getName();
    }

    /** How many of one kind of item each inventory holds, inventories in order. */
    private static final class Tally {
        int[] chests = new int[2];
        long[] counts = new long[2];
        int size;
        long total;

        void add(int chest, long count) {
            if (size == chests.length) {
                chests = Arrays.copyOf(chests, size * 2);
                counts = Arrays.copyOf(counts, size * 2);
            }
            chests[size] = chest;
            counts[size++] = count;
            total += count;
        }

        Entry entry(ItemStack item) {
            return new Entry(item, total, Arrays.copyOf(chests, size), Arrays.copyOf(counts, size));
        }
    }
}
