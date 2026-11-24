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

import com.linc.alteredaether.compat.CreateFunnels;
import com.linc.alteredaether.compat.QuarkCrates;
import com.linc.alteredaether.entity.golem.group.GolemPaths;
import com.linc.alteredaether.entity.golem.sort.SortSnapshot;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
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
        /** Create filters pinned to it, which pin whatever they let through; and the keys pinned outright. */
        List<ItemStack> filters = List.of();
        Set<Integer> basePins = Set.of();
    }

    /** What each Create filter (by its hash) lets through of the keys numbered so far. */
    private final Map<Integer, FilterMatch> filterMatches = new HashMap<>();

    private static final class FilterMatch {
        final Set<Integer> keys = new HashSet<>();
        /** Keys below this have been tested. */
        int checked;
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
            filterMatches.clear();
        }
    }

    /**
     * The inventory as a plan should see it: with what its filters let through of every item known by now pinned,
     * and a sink as one slot for each item it takes, holding as many as it has room for now.
     */
    SortSnapshot.Inv view(ServerLevel level, BlockPos pos, Entry entry) {
        if (entry.filters.isEmpty()) {
            return entry.inv;
        }
        Set<Integer> pins = new HashSet<>(entry.basePins);
        pins.addAll(filterKeys(level, entry.filters));
        if (entry.inv.kind() == SortSnapshot.Kind.SINK) {
            IItemHandler handler = GolemStorage.handler(level, pos);
            if (handler != null) {
                entry.inv = sinkInv(handler, pins);
            }
            return entry.inv;
        }
        if (!pins.equals(entry.inv.pins())) {
            entry.inv = entry.inv.withPins(Set.copyOf(pins));
        }
        return entry.inv;
    }

    /**
     * Ranks the pins of each key two or more strict inventories share by how narrow they are, so the narrowest win it
     * (see {@link SortSnapshot.Inv#ranks}): a key pinned outright ranks 1, one pinned by Create filters ranks as the
     * fewest items any of those filters lets through. Only filters that share a key are counted.
     *
     * @param invs    the snapshot's inventories, replaced in place by ranked copies
     * @param invKeys each inventory's key, as {@link #get} takes it
     * @return true while a filter's breadth is still being counted: those keys are shared by all their chests for now
     */
    boolean rankShared(List<SortSnapshot.Inv> invs, List<Long> invKeys) {
        Map<Integer, List<Integer>> homes = new HashMap<>();
        boolean filtered = false;
        for (int i = 0; i < invs.size(); i++) {
            SortSnapshot.Inv inv = invs.get(i);
            if (!inv.strict() || inv.source() || inv.kind() == SortSnapshot.Kind.SINK) {
                continue;
            }
            Entry entry = entries.get(invKeys.get(i));
            filtered |= entry != null && !entry.filters.isEmpty();
            for (int pin : inv.pins()) {
                homes.computeIfAbsent(pin, k -> new ArrayList<>(2)).add(i);
            }
        }
        if (!filtered) {
            return false;
        }
        boolean counting = false;
        Map<Integer, Map<Integer, Integer>> ranks = new HashMap<>();
        for (Map.Entry<Integer, List<Integer>> shared : homes.entrySet()) {
            if (shared.getValue().size() < 2) {
                continue;
            }
            int key = shared.getKey();
            for (int i : shared.getValue()) {
                Entry entry = entries.get(invKeys.get(i));
                if (entry == null || entry.filters.isEmpty() || entry.basePins.contains(key)) {
                    continue;
                }
                int best = Integer.MAX_VALUE;
                for (ItemStack filter : entry.filters) {
                    FilterMatch match = filterMatches.get(ItemStack.hashItemAndComponents(filter));
                    if (match == null || !match.keys.contains(key)) {
                        continue;
                    }
                    int breadth = FilterBreadth.breadth(filter);
                    if (breadth < 0) {
                        best = SortSnapshot.Inv.COUNTING;
                        counting = true;
                        break;
                    }
                    best = Math.min(best, breadth);
                }
                if (best != Integer.MAX_VALUE && best != 1) {
                    ranks.computeIfAbsent(i, k -> new HashMap<>()).put(key, best);
                }
            }
        }
        ranks.forEach((i, ranked) -> invs.set(i, invs.get(i).withRanks(Map.copyOf(ranked))));
        return counting;
    }

    /** The keys {@code filters} let through: those they name, and every key numbered so far that passes them. */
    private Set<Integer> filterKeys(ServerLevel level, List<ItemStack> filters) {
        Set<Integer> out = new HashSet<>();
        for (ItemStack filter : filters) {
            int hash = ItemStack.hashItemAndComponents(filter);
            FilterMatch match = filterMatches.get(hash);
            if (match == null) {
                match = new FilterMatch();
                filterMatches.put(hash, match);
                for (ItemStack listed : CreateFunnels.listed(filter)) {
                    match.keys.add(keyOf(listed));
                }
            }
            while (match.checked < keys.size()) {
                int k = match.checked++;
                if (CreateFunnels.accepts(level, filter, items.get(k))) {
                    match.keys.add(k);
                }
            }
            out.addAll(match.keys);
        }
        return out;
    }

    /** A sink as a plan sees it: a slot for each key it takes, as big as the room it has for that key. */
    private SortSnapshot.Inv sinkInv(IItemHandler handler, Set<Integer> pins) {
        List<Integer> taken = new ArrayList<>(pins);
        taken.sort(null);
        int n = taken.size();
        int[] limit = new int[n], key = new int[n], count = new int[n], only = new int[n];
        Arrays.fill(key, -1);
        for (int i = 0; i < n; i++) {
            int k = taken.get(i);
            only[i] = k;
            int stack = Math.max(1, keys.get(k).maxStack());
            ItemStack left = handler.insertItem(0, items.get(k).copyWithCount(stack), true);
            limit[i] = stack - left.getCount();
        }
        return new SortSnapshot.Inv(SortSnapshot.Kind.SINK, false, limit, key, count, only, Integer.MAX_VALUE,
                Set.copyOf(pins), true, false, -1);
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
        BlockState state = level.getBlockState(pos);
        boolean locked = GolemStorage.locked(state);
        int pinsHash = (pinned.strict() ? 1 : 0) + (locked ? 2 : 0);
        for (ItemStack pin : pinned.items()) {
            pinsHash = pinsHash * 31 + ItemStack.hashItemAndComponents(pin);
        }
        for (ItemStack filter : pinned.filters()) {
            pinsHash = pinsHash * 37 + ItemStack.hashItemAndComponents(filter);
        }
        SortSnapshot.Kind kind = kind(role);
        Entry old = entries.get(key);
        if (old != null && old.contentHash == contentHash && old.pinsHash == pinsHash && old.inv.kind() == kind) {
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
        int[] only = new int[n];
        Arrays.fill(only, SortSnapshot.ANY);
        if (kind == SortSnapshot.Kind.BULK || kind == SortSnapshot.Kind.POOLED) {
            for (int slot = 0; slot < n; slot++) {
                // a void upgrade's slot, or an empty slot of a locked drawer, takes nothing the golem should send
                if (GolemStorage.voidSlot(handler, slot) || locked && slotKey[slot] < 0) {
                    only[slot] = SortSnapshot.NONE;
                }
            }
        }
        int capacity = kind == SortSnapshot.Kind.MISC ? QuarkCrates.capacity(state) : Integer.MAX_VALUE;
        entry.filters = List.copyOf(pinned.filters());
        entry.basePins = Set.copyOf(pins);
        if (kind == SortSnapshot.Kind.SINK) {
            // what the machine holds tells when it wants more; the plan sees only room for what it takes
            entry.inv = sinkInv(handler, entry.filters.isEmpty() ? pins : filterKeys(level, entry.filters));
        } else {
            if (!entry.filters.isEmpty()) {
                pins.addAll(filterKeys(level, entry.filters));
            }
            entry.inv = new SortSnapshot.Inv(kind, plain, limit, slotKey, count, only, capacity, pins, pinned.strict(),
                    false, -1);
        }
        entries.put(key, entry);
        if (old == null) {
            return new Seen(Change.NEW, false);
        }
        boolean contents = old.contentHash != contentHash, pinsChanged = old.pinsHash != pinsHash;
        Change change = contents && pinsChanged ? Change.BOTH : pinsChanged ? Change.PINS : Change.CONTENTS;
        return new Seen(change, entry.total < old.total);
    }

    /** The part an inventory of {@code role} plays in a plan. */
    static SortSnapshot.Kind kind(GolemStorage.Role role) {
        return switch (role) {
            case SOURCE -> SortSnapshot.Kind.SOURCE;
            case BULK -> SortSnapshot.Kind.BULK;
            case POOLED -> SortSnapshot.Kind.POOLED;
            case MISC -> SortSnapshot.Kind.MISC;
            case SINK -> SortSnapshot.Kind.SINK;
            default -> SortSnapshot.Kind.STORAGE;
        };
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
