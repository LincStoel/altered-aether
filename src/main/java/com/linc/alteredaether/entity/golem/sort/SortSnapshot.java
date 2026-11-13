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

import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * What a golem network's storage holds, with nothing of Minecraft in it, for {@link Planner}. Items are numbered
 * {@link Key}s; each inventory is a row of slots, each holding one key (or -1) and a count.
 *
 * @param keys every item seen (and every item pinned), by key number
 * @param invs the inventories, in a fixed order; a move names them by their index here
 */
public record SortSnapshot(List<Key> keys, List<Inv> invs) {
    /**
     * One kind of item.
     *
     * @param maxStack how many fit in a stack
     * @param group    the family it is sorted with (ingots, logs, tools...); numbered by the caller
     * @param order    where it sorts within a chest: group first, then the item, so a chest reads in families
     */
    public record Key(int maxStack, int group, long order) {
    }

    /**
     * One inventory: a chest, a barrel, a double chest seen as one.
     *
     * @param source true for one the golem only empties (a hopper, a depot), never fills
     * @param plain  true for a vanilla-style container whose slots are all alike, which the golem may reorder in
     *               place (see {@link Planner#orderWithin})
     * @param limit  the most each slot holds, whatever the item
     * @param key    the key in each slot, -1 when empty
     * @param count  how many are in each slot
     * @param pins   keys pinned to this inventory by an item frame or placard on it
     * @param strict true when it takes only its pinned items, not their families, and sends everything else away
     *               (a glow item frame or a placard pinned them)
     * @param frozen true when it can't be used just now (a player has it open, it is walled in, no golem reaches it):
     *               it still says what it is for, but nothing moves into or out of it
     * @param claim  the group it was for last time it was planned, or -1; it keeps that group until another fills
     *               clearly more of it (see {@link Planner#plan(SortSnapshot, SortMode, Planner.Focus, int)})
     */
    public record Inv(boolean source, boolean plain, int[] limit, int[] key, int[] count, Set<Integer> pins,
                      boolean strict, boolean frozen, int claim) {
        /** An inventory whose pins, if any, take their families too. */
        public Inv(boolean source, boolean plain, int[] limit, int[] key, int[] count, Set<Integer> pins) {
            this(source, plain, limit, key, count, pins, false);
        }

        public Inv(boolean source, boolean plain, int[] limit, int[] key, int[] count, Set<Integer> pins, boolean strict) {
            this(source, plain, limit, key, count, pins, strict, false, -1);
        }

        /** Whether this is strict and {@code key} isn't pinned to it, so it has no place here. */
        public boolean refuses(int key) {
            return strict && !pins.contains(key);
        }

        public int size() {
            return key.length;
        }

        public Inv copy() {
            return new Inv(source, plain, limit.clone(), key.clone(), count.clone(), pins, strict, frozen, claim);
        }

        /** The same inventory, frozen or not, claiming {@code claim}. */
        public Inv with(boolean frozen, int claim) {
            return new Inv(source, plain, limit, key, count, pins, strict, frozen, claim);
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
            return o instanceof Inv other && source == other.source && plain == other.plain
                    && Arrays.equals(limit, other.limit) && Arrays.equals(key, other.key)
                    && Arrays.equals(count, other.count) && pins.equals(other.pins) && strict == other.strict
                    && frozen == other.frozen && claim == other.claim;
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(key) * 31 + Arrays.hashCode(count);
        }
    }

    /** A copy whose slots can be changed without touching this one. */
    public SortSnapshot copy() {
        return new SortSnapshot(keys, invs.stream().map(Inv::copy).toList());
    }
}
