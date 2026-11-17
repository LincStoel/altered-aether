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
package com.linc.alteredaether.entity.golem.sort;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * What a golem network's storage holds, with nothing of Minecraft in it, for {@link Planner}. Items are numbered
 * {@link Key}s; each inventory is a row of slots, each holding one key (or -1) and a count.
 *
 * <p>Items sit in a tree of {@link Node}s (dimension, category, material group, material type), node 0 its root. Tidy
 * mode gives a chest to a node, as fine-grained as the storage allows; what no finer node holds belongs to the root,
 * the miscellaneous items.
 *
 * @param keys  every item seen (and every item pinned), by key number
 * @param invs  the inventories, in a fixed order; a move names them by their index here
 * @param nodes the family tree, root first; every node's parent comes before it
 */
public record SortSnapshot(List<Key> keys, List<Inv> invs, List<Node> nodes) {
    /** The root of the family tree: the miscellaneous items, what no finer family is kept for. */
    public static final int ROOT = 0;

    /** A slot that takes any item. */
    public static final int ANY = -1;
    /** A slot that takes nothing: a locked or void drawer slot. */
    public static final int NONE = -2;

    /**
     * One kind of item.
     *
     * @param maxStack how many fit in a stack
     * @param node     the finest family it belongs to, an index into {@link #nodes}
     * @param order    where it sorts within a chest: family first, then the item, so a chest reads in families
     */
    public record Key(int maxStack, int node, long order) {
    }

    /**
     * One family of items.
     *
     * @param parent the family it is part of, or -1 for the root
     * @param name   its name, unique among its siblings
     */
    public record Node(int parent, String name) {
    }

    /** What part an inventory plays. */
    public enum Kind {
        /** A chest, barrel, crate: filled and emptied. */
        STORAGE,
        /** Only emptied, never filled (a hopper, a depot). */
        SOURCE,
        /** Only filled, with exactly the items its slots name, never emptied (a brass funnel into a machine). */
        SINK,
        /** Bulk storage, a drawer: each slot holds lots of one item, and is that item's home. */
        BULK,
        /** A compacting drawer: its slots are tiers of one pool, so it only ever takes in, and isn't counted. */
        POOLED,
        /** Storage for the miscellaneous items first of all (a Quark crate). */
        MISC
    }

    /**
     * One inventory: a chest, a barrel, a double chest seen as one.
     *
     * @param kind     what part it plays
     * @param plain    true for a vanilla-style container whose slots are all alike, which the golem may reorder in
     *                 place (see {@link Planner#orderWithin})
     * @param limit    the most each slot holds, whatever the item (for bulk storage: of a 64-stack item, if empty)
     * @param key      the key in each slot, -1 when empty
     * @param count    how many are in each slot
     * @param only     the one key each empty slot takes, or {@link #ANY} or {@link #NONE}
     * @param capacity the most items it holds in all (a crate), else {@link Integer#MAX_VALUE}
     * @param pins     keys pinned to this inventory by an item frame, placard or filter on it
     * @param strict   true when it takes only its pinned items, not their families, and sends everything else away
     *                 (a glow item frame or a placard pinned them)
     * @param frozen   true when it can't be used just now (a player has it open, it is walled in, no golem reaches
     *                 it): it still says what it is for, but nothing moves into or out of it
     * @param claim    the family it was for last time it was planned, or -1; it keeps that family until another fills
     *                 clearly more of it (see {@link Planner#plan(SortSnapshot, SortMode, Planner.Focus, Planner.Settings)})
     */
    public record Inv(Kind kind, boolean plain, int[] limit, int[] key, int[] count, int[] only, int capacity,
                      Set<Integer> pins, boolean strict, boolean frozen, int claim) {
        /** An inventory whose pins, if any, take their families too. */
        public Inv(boolean source, boolean plain, int[] limit, int[] key, int[] count, Set<Integer> pins) {
            this(source, plain, limit, key, count, pins, false);
        }

        public Inv(boolean source, boolean plain, int[] limit, int[] key, int[] count, Set<Integer> pins, boolean strict) {
            this(source ? Kind.SOURCE : Kind.STORAGE, plain, limit, key, count, any(key.length), Integer.MAX_VALUE, pins,
                    strict, false, -1);
        }

        private static int[] any(int size) {
            int[] only = new int[size];
            Arrays.fill(only, ANY);
            return only;
        }

        public boolean source() {
            return kind == Kind.SOURCE;
        }

        /** Whether this is strict and {@code key} isn't pinned to it, so it has no place here. */
        public boolean refuses(int key) {
            return strict && !pins.contains(key);
        }

        public int size() {
            return key.length;
        }

        /** How many items it holds in all. */
        public long total() {
            long total = 0;
            for (int slot = 0; slot < key.length; slot++) {
                if (key[slot] >= 0) {
                    total += count[slot];
                }
            }
            return total;
        }

        public Inv copy() {
            return new Inv(kind, plain, limit.clone(), key.clone(), count.clone(), only.clone(), capacity, pins, strict,
                    frozen, claim);
        }

        /** The same inventory, frozen or not, claiming {@code claim}. */
        public Inv with(boolean frozen, int claim) {
            return new Inv(kind, plain, limit, key, count, only, capacity, pins, strict, frozen, claim);
        }

        /** The same inventory with other pins. */
        public Inv withPins(Set<Integer> pins) {
            return new Inv(kind, plain, limit, key, count, only, capacity, pins, strict, frozen, claim);
        }

        /** The same inventory playing another part, with each empty slot taking {@code only} and a total cap. */
        public Inv as(Kind kind, int[] only, int capacity) {
            return new Inv(kind, plain, limit, key, count, only, capacity, pins, strict, frozen, claim);
        }

        public boolean isEmpty() {
            for (int k : key) {
                if (k >= 0) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Inv other && kind == other.kind && plain == other.plain
                    && Arrays.equals(limit, other.limit) && Arrays.equals(key, other.key)
                    && Arrays.equals(count, other.count) && Arrays.equals(only, other.only)
                    && capacity == other.capacity && pins.equals(other.pins) && strict == other.strict
                    && frozen == other.frozen && claim == other.claim;
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(key) * 31 + Arrays.hashCode(count);
        }
    }

    /**
     * A snapshot whose keys name flat families by number rather than nodes: family {@code g} becomes node {@code g + 1},
     * a child of the root.
     */
    public SortSnapshot(List<Key> keys, List<Inv> invs) {
        this(flatKeys(keys), invs, flatNodes(keys));
    }

    private static List<Key> flatKeys(List<Key> keys) {
        List<Key> out = new ArrayList<>(keys.size());
        for (Key key : keys) {
            out.add(new Key(key.maxStack(), key.node() + 1, key.order()));
        }
        return out;
    }

    private static List<Node> flatNodes(List<Key> keys) {
        Set<Integer> groups = new TreeSet<>();
        for (Key key : keys) {
            groups.add(key.node());
        }
        int max = groups.isEmpty() ? -1 : ((TreeSet<Integer>) groups).last();
        List<Node> nodes = new ArrayList<>(max + 2);
        nodes.add(new Node(-1, "misc"));
        for (int g = 0; g <= max; g++) {
            nodes.add(new Node(ROOT, "g" + g));
        }
        return nodes;
    }

    /** The node's name with its ancestors', root excluded, joined by {@code /}: what a claim is saved as. */
    public String path(int node) {
        if (node <= ROOT) {
            return node == ROOT ? "misc" : "";
        }
        StringBuilder out = new StringBuilder();
        for (int n = node; n > ROOT; n = nodes.get(n).parent()) {
            out.insert(0, out.isEmpty() ? nodes.get(n).name() : nodes.get(n).name() + "/");
        }
        return out.toString();
    }

    /** The node a {@link #path} names, or -1. */
    public int byPath(String path) {
        if (path.equals("misc")) {
            return ROOT;
        }
        for (int n = 1; n < nodes.size(); n++) {
            if (path(n).equals(path)) {
                return n;
            }
        }
        return -1;
    }

    /** A copy whose slots can be changed without touching this one. */
    public SortSnapshot copy() {
        return new SortSnapshot(keys, invs.stream().map(Inv::copy).toList(), nodes);
    }
}
