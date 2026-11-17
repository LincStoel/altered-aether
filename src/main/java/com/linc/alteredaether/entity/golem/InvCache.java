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

import com.linc.alteredaether.entity.golem.group.GolemPaths;
import com.linc.alteredaether.entity.golem.sort.SortSnapshot;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What a network last saw in each of its inventories, so a plan, a scan or a comparator doesn't read every slot of
 * every chest each time: an inventory is read in full only when its signature (a hash of its contents and of what is
 * pinned to it) has changed. Item keys are numbered once and kept as long as the network's sorting is, so what was
 * read stays valid between plans.
 */
final class InvCache {
    /** Keys kept before the numbering starts over, so endless one-off items can't grow it for ever. */
    private static final int MAX_KEYS = 4096;

    final List<SortSnapshot.Key> keys = new ArrayList<>();
    final List<ItemStack> items = new ArrayList<>();
    /** The family tree the keys sit in, root first; families are numbered as they are first met. */
    final List<SortSnapshot.Node> nodes = new ArrayList<>(List.of(new SortSnapshot.Node(-1, "misc")));
    private final Map<ItemKey, Integer> index = new HashMap<>();
    private final Map<String, Integer> nodeIndex = new HashMap<>(Map.of("misc", SortSnapshot.ROOT));
    private final Map<Long, Entry> entries = new HashMap<>();

    /** One inventory as last read. */
    static final class Entry {
        int contentHash, pinsHash;
        /** Its slots, pins and whether it is strict; neither frozen nor claiming (the snapshot adds those). */
        SortSnapshot.Inv inv;
        long total;
        /** Each slot's fill (count over what fits), summed, and the slots: for comparators. */
        double fill;
        int slots;
    }

    /** What reading an inventory found. */
    enum Change { NONE, NEW, CONTENTS, PINS, BOTH }

    record Seen(Change change, boolean fewer) {
        boolean changed() {
            return change != Change.NONE && change != Change.NEW;
        }

        boolean pins() {
            return change == Change.PINS || change == Change.BOTH;
        }
    }

    private static final Seen UNCHANGED = new Seen(Change.NONE, false);

    @Nullable
    Entry get(long key) {
        return entries.get(key);
    }

    void forget(long key) {
        entries.remove(key);
    }

    Set<Long> known() {
        return new HashSet<>(entries.keySet());
    }

    /** Starts the numbering over once it grows too long; every inventory is read afresh. */
    void trim() {
        if (keys.size() > MAX_KEYS) {
            keys.clear();
            items.clear();
            index.clear();
            entries.clear();
            nodes.subList(1, nodes.size()).clear();
            nodeIndex.keySet().retainAll(Set.of("misc"));
        }
    }

    /**
     * Reads the inventory at {@code pos} (as {@code key}) if its signature changed since last time, and says what
     * changed: nothing, its contents, its pins, or that this is the first look (a baseline, which is no change).
     */
    Seen read(ServerLevel level, BlockPos pos, long key, GolemStorage.Role role) {
        IItemHandler handler = GolemStorage.handler(level, pos);
        if (handler == null) {
            return UNCHANGED;
        }
        int contentHash = GolemStorage.contentHash(handler);
        GolemStorage.Pins pinned = GolemStorage.pins(level, pos);
        int pinsHash = pinned.strict() ? 1 : 0;
        for (ItemStack pin : pinned.items()) {
            pinsHash = pinsHash * 31 + ItemStack.hashItemAndComponents(pin);
        }
        Entry old = entries.get(key);
        if (old != null && old.contentHash == contentHash && old.pinsHash == pinsHash
                && old.inv.source() == (role == GolemStorage.Role.SOURCE)) {
            return UNCHANGED;
        }
        Entry entry = new Entry();
        entry.contentHash = contentHash;
        entry.pinsHash = pinsHash;
        int n = handler.getSlots();
        int[] limit = new int[n], slotKey = new int[n], count = new int[n];
        for (int slot = 0; slot < n; slot++) {
            limit[slot] = handler.getSlotLimit(slot);
            ItemStack stack = handler.getStackInSlot(slot);
            if (stack.isEmpty()) {
                slotKey[slot] = -1;
            } else {
                slotKey[slot] = keyOf(stack);
                count[slot] = stack.getCount();
                entry.total += stack.getCount();
                entry.fill += stack.getCount() / (double) Math.max(1, Math.min(limit[slot], stack.getMaxStackSize()));
            }
        }
        entry.slots = n;
        Set<Integer> pins = new HashSet<>();
        for (ItemStack pin : pinned.items()) {
            pins.add(keyOf(pin));
        }
        boolean plain = GolemStorage.plainContainer(level, pos) != null;
        entry.inv = new SortSnapshot.Inv(role == GolemStorage.Role.SOURCE, plain, limit, slotKey, count, pins,
                pinned.strict());
        entries.put(key, entry);
        if (old == null) {
            return new Seen(Change.NEW, false);
        }
        boolean contents = old.contentHash != contentHash, pinsChanged = old.pinsHash != pinsHash;
        Change change = contents && pinsChanged ? Change.BOTH : pinsChanged ? Change.PINS : Change.CONTENTS;
        return new Seen(change, entry.total < old.total);
    }

    /** The key for {@code stack}, numbering it if it is new. */
    int keyOf(ItemStack stack) {
        ItemKey key = new ItemKey(stack);
        Integer known = index.get(key);
        if (known != null) {
            return known;
        }
        int id = keys.size();
        GolemPaths.ItemPath path = GolemPaths.path(stack);
        keys.add(new SortSnapshot.Key(stack.getMaxStackSize(), node(path.names()), GolemPaths.order(stack, path)));
        items.add(stack.copyWithCount(1));
        index.put(new ItemKey(items.get(id)), id);
        return id;
    }

    /** The family {@code names} spells out from the root down, numbering it (and those above it) if it is new. */
    int node(List<String> names) {
        int parent = SortSnapshot.ROOT;
        StringBuilder path = new StringBuilder();
        for (String raw : names) {
            String name = raw.replace('/', '.');
            path.append(path.isEmpty() ? "" : "/").append(name);
            String at = path.toString();
            Integer known = nodeIndex.get(at);
            if (known == null) {
                known = nodes.size();
                nodes.add(new SortSnapshot.Node(parent, name));
                nodeIndex.put(at, known);
            }
            parent = known;
        }
        return parent;
    }

    /** The family a saved claim names ({@link SortSnapshot#path}). */
    int node(String path) {
        Integer known = nodeIndex.get(path);
        return known != null ? known : node(List.of(path.split("/")));
    }

    /** An item without its count, compared and hashed as stacks are merged. */
    private static final class ItemKey {
        final ItemStack stack;
        final int hash;

        ItemKey(ItemStack stack) {
            this.stack = stack;
            this.hash = ItemStack.hashItemAndComponents(stack);
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof ItemKey other && ItemStack.isSameItemSameComponents(stack, other.stack);
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }
}
