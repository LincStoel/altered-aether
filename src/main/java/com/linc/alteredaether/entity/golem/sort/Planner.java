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

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Works out how a golem network should move items to sort its storage, from a {@link SortSnapshot}. Pure and
 * deterministic: the same snapshot always gives the same moves, so a network that has applied its moves and plans
 * again finds nothing left to do and stops.
 *
 * <p>Every inventory starts each plan with an identity read off what it holds. A <b>pinned</b> one (an item frame or
 * placard on it) belongs to its pinned items and their groups, whatever it holds; a <b>strict</b> pinned one belongs to
 * its pinned items alone, and anything else in it is sent to wherever else has room. Otherwise the group filling the
 * most of its slots is its <b>dominant</b> group; a group with only one slot in the whole network ("a singleton")
 * claims nothing.
 *
 * <p><b>Tidy</b> sends each item to the best chest for it: pinned for that item, pinned for its group, then the
 * dominant chest for its group holding the most of it. A group no chest claims takes an empty chest (the biggest
 * groups first). Singletons share one miscellaneous chest rather than each taking an empty one. An item already in
 * the best chest for it, or with nowhere better that has room, stays.
 *
 * <p><b>Compact</b> fills pinned chests with their items, merges partial stacks (into the chest holding the most of
 * that item), then empties the least-used chests into the others, one whole chest at a time, while one fits.
 *
 * <p>In both, a source inventory (a hopper, say) is emptied into storage, and nothing is moved into one. A plain
 * container is then reordered in place by {@link #orderWithin}, without moves.
 *
 * <p>A plan can be <b>focused</b> on a few inventories (see {@link Focus}): only items in those are moved, though what
 * every chest is for is still judged on the whole network. A <b>frozen</b> inventory (one in use, say) keeps what it is
 * for but nothing moves into or out of it. An item pinned strictly to two or more chests is shared out between them:
 * new ones go to the chest holding fewest, and the chests are kept within a stack of each other.
 */
public final class Planner {
    /** Moves planned at most at once; the rest wait for the next plan. */
    public static final int MAX_MOVES = 512;

    /**
     * Which inventories a plan moves items out of.
     *
     * @param sources the inventories (by index) whose items may move, or null for every one
     * @param gather  inventories whose pins just changed: the items pinned to them are pulled in from anywhere
     */
    public record Focus(@Nullable Set<Integer> sources, Set<Integer> gather) {
        /** Everything may move: a whole-network sort. */
        public static final Focus ALL = new Focus(null, Set.of());

        public boolean all() {
            return sources == null;
        }

        boolean has(int inv) {
            return sources == null || sources.contains(inv);
        }
    }

    /**
     * @param moves    the moves, in the order to make them
     * @param unplaced items a source inventory holds that no storage has room for
     * @param claims   the group each inventory was judged to be for (-1 for none), to pass back as its claim next time
     * @param stuck    inventories holding items that belong elsewhere but found no room there
     */
    public record Plan(List<Move> moves, int unplaced, int[] claims, Set<Integer> stuck) {
        public boolean isEmpty() {
            return moves.isEmpty();
        }
    }

    private Planner() {
    }

    /** A whole-network plan with no tolerance for claims. */
    public static Plan plan(SortSnapshot snapshot, SortMode mode) {
        return plan(snapshot, mode, Focus.ALL, 0);
    }

    /**
     * @param focus     which inventories' items may move
     * @param tolerance slots another group must fill beyond a chest's claimed group before tidy mode gives the chest
     *                  to it
     */
    public static Plan plan(SortSnapshot snapshot, SortMode mode, Focus focus, int tolerance) {
        Work work = new Work(snapshot, focus, tolerance);
        if (mode == SortMode.TIDY) {
            tidy(work);
        } else {
            compact(work);
        }
        return new Plan(List.copyOf(work.moves), work.unplaced, work.claims, Set.copyOf(work.stuck));
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Tidy

    private static final int SOURCE = -1, EMPTY = 0, PINNED = 1, DOMINANT = 2, MISC = 3;

    private static void tidy(Work w) {
        int n = w.invs.size();
        Map<Integer, Integer> groupSlots = w.groupSlots();
        int[] type = new int[n];
        int[] dominant = new int[n];
        List<Set<Integer>> pinGroups = new ArrayList<>(n);
        Arrays.fill(dominant, -1);
        for (int i = 0; i < n; i++) {
            SortSnapshot.Inv inv = w.invs.get(i);
            Set<Integer> groups = new HashSet<>();
            if (!inv.strict()) {
                for (int key : inv.pins()) {
                    groups.add(w.group(key));
                }
            }
            pinGroups.add(groups);
            if (inv.source()) {
                type[i] = SOURCE;
            } else if (!inv.pins().isEmpty()) {
                type[i] = PINNED;
            } else if (inv.isEmpty()) {
                type[i] = EMPTY;
            } else {
                dominant[i] = w.dominantGroup(i, groupSlots);
                type[i] = singleton(groupSlots, dominant[i]) ? MISC : DOMINANT;
            }
            w.claims[i] = dominant[i];
        }
        Map<Integer, List<Integer>> exclusive = w.exclusiveHomes();

        // groups no chest claims, biggest first, each take an empty chest
        Set<Integer> claimed = new HashSet<>();
        for (int i = 0; i < n; i++) {
            if (type[i] == PINNED) {
                claimed.addAll(pinGroups.get(i));
            } else if (type[i] == DOMINANT) {
                claimed.add(dominant[i]);
            }
        }
        List<Integer> unclaimed = new ArrayList<>();
        for (int group : groupSlots.keySet()) {
            if (!claimed.contains(group) && !singleton(groupSlots, group)) {
                unclaimed.add(group);
            }
        }
        unclaimed.sort(Comparator.<Integer>comparingInt(g -> -groupSlots.get(g)).thenComparingInt(g -> g));
        List<Integer> empties = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (type[i] == EMPTY && !w.invs.get(i).frozen()) {
                empties.add(i);
            }
        }
        Map<Integer, Integer> newHome = new HashMap<>();
        for (int group : unclaimed) {
            if (empties.isEmpty()) {
                break;
            }
            newHome.put(group, empties.remove(0));
        }

        // the miscellaneous chest: the fullest chest of singletons, or else an empty one if any singleton is astray
        int misc = -1;
        for (int i = 0; i < n; i++) {
            if (type[i] == MISC && (misc < 0 || w.needed(i) > w.needed(misc))) {
                misc = i;
            }
        }
        if (misc < 0 && !empties.isEmpty() && w.singletonAstray(type, dominant, pinGroups, groupSlots)) {
            misc = empties.get(0);
        }

        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (type[i] == SOURCE) {
                order.add(i);
            }
        }
        for (int i = 0; i < n; i++) {
            if (type[i] != SOURCE) {
                order.add(i);
            }
        }
        for (int i : order) {
            SortSnapshot.Inv inv = w.invs.get(i);
            if (inv.frozen()) {
                continue;
            }
            for (int slot = 0; slot < inv.size() && !w.full(); slot++) {
                int key = inv.key()[slot];
                if (key < 0 || !w.consider(i, key)) {
                    continue;
                }
                int group = w.group(key);
                List<Integer> homes = new ArrayList<>();
                List<Integer> shared = exclusive.get(key);
                if (shared != null) {
                    // pinned strictly to several chests: it stays in any of them (they are evened out below), and
                    // from anywhere else it goes to the one holding fewest
                    if (shared.contains(i)) {
                        continue;
                    }
                    homes.addAll(w.byFewest(shared, key));
                } else {
                    for (int j = 0; j < n; j++) {
                        if (type[j] == PINNED && w.invs.get(j).pins().contains(key)) {
                            homes.add(j);
                        }
                    }
                    List<Integer> tier = new ArrayList<>();
                    for (int j = 0; j < n; j++) {
                        if (type[j] == PINNED && !homes.contains(j) && pinGroups.get(j).contains(group)) {
                            tier.add(j);
                        }
                    }
                    homes.addAll(w.byHeld(tier, key));
                    tier.clear();
                    for (int j = 0; j < n; j++) {
                        if (type[j] == DOMINANT && dominant[j] == group) {
                            tier.add(j);
                        }
                    }
                    homes.addAll(w.byHeld(tier, key));
                    Integer fresh = newHome.get(group);
                    if (homes.isEmpty() && fresh != null) {
                        homes.add(fresh);
                    }
                    if (singleton(groupSlots, group) && misc >= 0 && !homes.contains(misc)) {
                        homes.add(misc);
                    }
                }
                // a strict chest never takes what isn't pinned to it, even as its group's dominant or misc chest
                homes.removeIf(j -> w.invs.get(j).refuses(key));
                int at = homes.indexOf(i);
                if (at == 0) {
                    continue;
                }
                List<Integer> better = at > 0 ? homes.subList(0, at) : homes;
                boolean evicting = inv.refuses(key);
                if (inv.source() || evicting) {
                    // anywhere else will do: the misc chest, then unpinned chests, then ones pinned for something else
                    better = new ArrayList<>(better);
                    if (misc >= 0 && misc != i && !better.contains(misc) && !w.invs.get(misc).refuses(key)) {
                        better.add(misc);
                    }
                    for (int pass = 0; pass < 2; pass++) {
                        for (int j = 0; j < n; j++) {
                            if (j == i || type[j] == SOURCE || better.contains(j) || w.invs.get(j).refuses(key)
                                    || (pass == 0) == (type[j] == PINNED)) {
                                continue;
                            }
                            better.add(j);
                        }
                    }
                }
                for (int j : better) {
                    if (inv.key()[slot] < 0 || w.full()) {
                        break;
                    }
                    w.place(i, slot, j, Integer.MAX_VALUE, true, true);
                }
                if (inv.key()[slot] >= 0 && !better.isEmpty()) {
                    w.stuck.add(i);
                }
                if (inv.source() && inv.key()[slot] >= 0) {
                    w.unplaced += inv.count()[slot];
                }
            }
        }

        w.balance(exclusive);
        // partial stacks of one item within a chest are merged; a plain chest does that itself (see orderWithin)
        for (int i = 0; i < n; i++) {
            if (type[i] != SOURCE && !w.invs.get(i).plain() && w.touched(i)) {
                w.mergeWithin(i);
            }
        }
    }

    private static boolean singleton(Map<Integer, Integer> groupSlots, int group) {
        return groupSlots.getOrDefault(group, 0) <= 1;
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Compact

    private static void compact(Work w) {
        int n = w.invs.size();
        for (int i = 0; i < n; i++) {
            w.claims[i] = w.invs.get(i).claim();
        }
        List<Integer> storage = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (!w.invs.get(i).source()) {
                storage.add(i);
            }
        }
        Map<Integer, List<Integer>> exclusive = w.exclusiveHomes();

        // 1. sources into storage
        for (int i = 0; i < n; i++) {
            SortSnapshot.Inv inv = w.invs.get(i);
            if (!inv.source() || inv.frozen()) {
                continue;
            }
            for (int slot = 0; slot < inv.size() && !w.full(); slot++) {
                int key = inv.key()[slot];
                if (key < 0 || !w.consider(i, key)) {
                    continue;
                }
                List<Integer> pref = w.preference(storage, key, true);
                for (int j : pref) {
                    w.place(i, slot, j, Integer.MAX_VALUE, true, false);
                }
                for (int j : pref) {
                    w.place(i, slot, j, Integer.MAX_VALUE, false, true);
                }
                if (inv.key()[slot] >= 0) {
                    w.unplaced += inv.count()[slot];
                    w.stuck.add(i);
                }
            }
        }

        // 2. pinned chests take their items
        for (int i : storage) {
            SortSnapshot.Inv inv = w.invs.get(i);
            if (inv.frozen()) {
                continue;
            }
            for (int slot = 0; slot < inv.size() && !w.full(); slot++) {
                int key = inv.key()[slot];
                if (key < 0 || !w.consider(i, key)) {
                    continue;
                }
                List<Integer> pinned = w.pinnedFor(storage, key);
                if (pinned.isEmpty() || pinned.contains(i)) {
                    continue;
                }
                for (int j : pinned) {
                    w.place(i, slot, j, Integer.MAX_VALUE, true, false);
                }
                for (int j : pinned) {
                    w.place(i, slot, j, Integer.MAX_VALUE, false, true);
                }
            }
        }

        // 2b. strict chests send away what isn't pinned to them, wherever it fits
        for (int i : storage) {
            SortSnapshot.Inv inv = w.invs.get(i);
            if (inv.frozen()) {
                continue;
            }
            for (int slot = 0; slot < inv.size() && !w.full(); slot++) {
                int key = inv.key()[slot];
                if (key < 0 || !inv.refuses(key) || !w.consider(i, key)) {
                    continue;
                }
                List<Integer> pref = w.preference(storage, key, true);
                pref.remove(Integer.valueOf(i));
                for (int j : pref) {
                    w.place(i, slot, j, Integer.MAX_VALUE, true, false);
                }
                for (int j : pref) {
                    w.place(i, slot, j, Integer.MAX_VALUE, false, true);
                }
                if (inv.key()[slot] >= 0) {
                    w.stuck.add(i);
                }
            }
        }

        // 3. partial stacks merged, into the chests that rank first for the item: every item on a whole sort, else
        // those in the focus
        Set<Integer> keys = new HashSet<>();
        for (int i = 0; i < n; i++) {
            if (w.focus.has(i)) {
                for (int k : w.invs.get(i).key()) {
                    if (k >= 0) {
                        keys.add(k);
                    }
                }
            }
        }
        for (int k = 0; k < w.keys.size(); k++) {
            if (w.gathered(k)) {
                keys.add(k);
            }
        }
        List<Integer> keyOrder = new ArrayList<>(keys);
        keyOrder.sort(null);
        for (int key : keyOrder) {
            if (w.full()) {
                break;
            }
            List<Integer> shared = exclusive.getOrDefault(key, List.of());
            List<int[]> partials = new ArrayList<>();
            for (int i : storage) {
                SortSnapshot.Inv inv = w.invs.get(i);
                // the chests one item is shared between are evened out instead (see balance)
                if (inv.frozen() || shared.contains(i)) {
                    continue;
                }
                for (int slot = 0; slot < inv.size(); slot++) {
                    if (inv.key()[slot] == key && inv.count()[slot] < w.cap(i, slot, key)) {
                        partials.add(new int[]{i, slot});
                    }
                }
            }
            if (partials.size() < 2) {
                continue;
            }
            List<Integer> pref = w.preference(storage, key, true);
            Map<Integer, Integer> rank = new HashMap<>();
            for (int r = 0; r < pref.size(); r++) {
                rank.put(pref.get(r), r);
            }
            // a strict chest it is stuck in (nowhere else had room) ranks last, so it only gives
            partials.sort(Comparator.<int[]>comparingInt(p -> rank.getOrDefault(p[0], Integer.MAX_VALUE))
                    .thenComparingInt(p -> p[1]));
            // the last-ranked give to the first-ranked; only stacks in the focus give
            for (int from = partials.size() - 1; from > 0 && !w.full(); from--) {
                int[] src = partials.get(from);
                if (!w.consider(src[0], key)) {
                    continue;
                }
                for (int to = 0; to < from && !w.full() && w.invs.get(src[0]).key()[src[1]] == key; to++) {
                    int[] dst = partials.get(to);
                    int space = w.cap(dst[0], dst[1], key) - w.invs.get(dst[0]).count()[dst[1]];
                    if (space > 0) {
                        w.transfer(src[0], src[1], dst[0], dst[1], Math.min(space, w.invs.get(src[0]).count()[src[1]]));
                    }
                }
            }
        }

        // 4. the least-used chests emptied into the rest, one whole chest at a time
        Set<Integer> freed = new HashSet<>();
        List<Integer> candidates = new ArrayList<>();
        for (int i : storage) {
            SortSnapshot.Inv inv = w.invs.get(i);
            if (inv.pins().isEmpty() && !inv.isEmpty() && !inv.frozen() && w.focus.has(i)) {
                candidates.add(i);
            }
        }
        candidates.sort(Comparator.<Integer>comparingInt(w::used).thenComparingInt(i -> -i));
        for (int c : candidates) {
            if (w.full()) {
                break;
            }
            Work trial = w.copy();
            List<Integer> others = new ArrayList<>();
            for (int i : storage) {
                // only into chests already in use: emptying one into an empty one saves nothing
                if (i != c && !freed.contains(i) && !w.invs.get(i).isEmpty() && !w.invs.get(i).frozen()) {
                    others.add(i);
                }
            }
            if (!trial.emptyInto(c, others) || trial.full()) {
                break;
            }
            w.adopt(trial);
            freed.add(c);
        }

        w.balance(exclusive);
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Reordering a plain container in place

    /**
     * {@code inv}'s contents merged into full stacks and laid out from its first slot, grouped then ordered by item,
     * or null if that is how it already is (or it isn't a plain container).
     */
    public static SortSnapshot.Inv orderWithin(SortSnapshot.Inv inv, List<SortSnapshot.Key> keys) {
        if (!inv.plain() || inv.size() == 0) {
            return null;
        }
        Map<Integer, Long> totals = new LinkedHashMap<>();
        for (int slot = 0; slot < inv.size(); slot++) {
            if (inv.key()[slot] >= 0) {
                totals.merge(inv.key()[slot], (long) inv.count()[slot], Long::sum);
            }
        }
        List<Integer> order = new ArrayList<>(totals.keySet());
        order.sort(Comparator.<Integer>comparingLong(k -> keys.get(k).order()).thenComparingInt(k -> k));
        int[] key = new int[inv.size()];
        int[] count = new int[inv.size()];
        Arrays.fill(key, -1);
        int slot = 0;
        for (int k : order) {
            long left = totals.get(k);
            while (left > 0) {
                if (slot >= inv.size()) {
                    return null;
                }
                int cap = Math.max(1, Math.min(inv.limit()[slot], keys.get(k).maxStack()));
                int n = (int) Math.min(cap, left);
                key[slot] = k;
                count[slot] = n;
                left -= n;
                slot++;
            }
        }
        if (Arrays.equals(key, inv.key()) && Arrays.equals(count, inv.count())) {
            return null;
        }
        return new SortSnapshot.Inv(inv.source(), inv.plain(), inv.limit(), key, count, inv.pins(), inv.strict(),
                inv.frozen(), inv.claim());
    }

    /** Applies {@code move} to {@code snapshot}'s slots, as the network does; false if it doesn't fit what is there. */
    public static boolean apply(SortSnapshot snapshot, Move move) {
        SortSnapshot.Inv src = snapshot.invs().get(move.srcInv());
        SortSnapshot.Inv dst = snapshot.invs().get(move.dstInv());
        if (src.key()[move.srcSlot()] != move.key() || src.count()[move.srcSlot()] < move.count()) {
            return false;
        }
        int existing = dst.key()[move.dstSlot()];
        if (existing >= 0 && existing != move.key()) {
            return false;
        }
        src.count()[move.srcSlot()] -= move.count();
        if (src.count()[move.srcSlot()] == 0) {
            src.key()[move.srcSlot()] = -1;
        }
        dst.key()[move.dstSlot()] = move.key();
        dst.count()[move.dstSlot()] += move.count();
        return true;
    }

    // ---------------------------------------------------------------------------------------------------------------

    /** A plan in progress: the slots as they will be once the moves so far are made. */
    private static final class Work {
        final List<SortSnapshot.Key> keys;
        List<SortSnapshot.Inv> invs;
        /** How many of each key each inventory held when planning began; what "holds the most" is judged on. */
        final List<Map<Integer, Integer>> held;
        final Focus focus;
        final int tolerance;
        /** Keys, and groups, pinned to the inventories being gathered for: those move from anywhere. */
        final Set<Integer> gatherKeys = new HashSet<>(), gatherGroups = new HashSet<>();
        final int[] claims;
        List<Move> moves = new ArrayList<>();
        int unplaced;
        Set<Integer> stuck = new HashSet<>();
        /** Inventories items were moved into. */
        Set<Integer> received = new HashSet<>();

        Work(SortSnapshot snapshot, Focus focus, int tolerance) {
            keys = snapshot.keys();
            invs = snapshot.copy().invs();
            this.focus = focus;
            this.tolerance = tolerance;
            claims = new int[invs.size()];
            Arrays.fill(claims, -1);
            for (int g : focus.gather()) {
                SortSnapshot.Inv inv = invs.get(g);
                for (int pin : inv.pins()) {
                    gatherKeys.add(pin);
                    if (!inv.strict()) {
                        gatherGroups.add(keys.get(pin).group());
                    }
                }
            }
            held = new ArrayList<>(invs.size());
            for (SortSnapshot.Inv inv : invs) {
                Map<Integer, Integer> counts = new HashMap<>();
                for (int slot = 0; slot < inv.size(); slot++) {
                    if (inv.key()[slot] >= 0) {
                        counts.merge(inv.key()[slot], inv.count()[slot], Integer::sum);
                    }
                }
                held.add(counts);
            }
        }

        private Work(Work other) {
            keys = other.keys;
            invs = other.invs.stream().map(SortSnapshot.Inv::copy).toList();
            held = other.held;
            focus = other.focus;
            tolerance = other.tolerance;
            gatherKeys.addAll(other.gatherKeys);
            gatherGroups.addAll(other.gatherGroups);
            claims = other.claims;
            moves = new ArrayList<>(other.moves);
            unplaced = other.unplaced;
            stuck = new HashSet<>(other.stuck);
            received = new HashSet<>(other.received);
        }

        Work copy() {
            return new Work(this);
        }

        void adopt(Work other) {
            invs = other.invs;
            moves = other.moves;
            unplaced = other.unplaced;
            stuck = other.stuck;
            received = other.received;
        }

        /** Whether the item in a slot of {@code inv} may move in this plan. */
        boolean consider(int inv, int key) {
            return focus.has(inv) || gathered(key);
        }

        /** Whether {@code key} is pinned to an inventory being gathered for. */
        boolean gathered(int key) {
            return gatherKeys.contains(key) || gatherGroups.contains(group(key));
        }

        /** Whether {@code inv} is in the focus or had items moved into it, so is worth tidying within. */
        boolean touched(int inv) {
            return focus.has(inv) || received.contains(inv);
        }

        /** How many of {@code key} {@code inv} holds now. */
        long amount(int inv, int key) {
            long total = 0;
            SortSnapshot.Inv in = invs.get(inv);
            for (int slot = 0; slot < in.size(); slot++) {
                if (in.key()[slot] == key) {
                    total += in.count()[slot];
                }
            }
            return total;
        }

        /** {@code list} ordered by how many of {@code key} each holds now, fewest first, then by index. */
        List<Integer> byFewest(List<Integer> list, int key) {
            List<Integer> sorted = new ArrayList<>(list);
            sorted.sort(Comparator.<Integer>comparingLong(i -> amount(i, key)).thenComparingInt(i -> i));
            return sorted;
        }

        /** Each key pinned strictly to two or more inventories, and those inventories. */
        Map<Integer, List<Integer>> exclusiveHomes() {
            Map<Integer, List<Integer>> homes = new HashMap<>();
            for (int i = 0; i < invs.size(); i++) {
                SortSnapshot.Inv inv = invs.get(i);
                if (inv.strict() && !inv.source()) {
                    for (int pin : inv.pins()) {
                        homes.computeIfAbsent(pin, k -> new ArrayList<>()).add(i);
                    }
                }
            }
            homes.values().removeIf(list -> list.size() < 2);
            for (List<Integer> list : homes.values()) {
                list.sort(null);
            }
            return homes;
        }

        /**
         * Evens out each item shared between strict chests, when one of them is in the focus: from the fullest into
         * the emptiest until every one is within a stack of the average.
         */
        void balance(Map<Integer, List<Integer>> exclusive) {
            List<Integer> shared = new ArrayList<>(exclusive.keySet());
            shared.sort(null);
            for (int key : shared) {
                List<Integer> homes = new ArrayList<>();
                boolean inFocus = false;
                for (int i : exclusive.get(key)) {
                    if (!invs.get(i).frozen()) {
                        homes.add(i);
                        inFocus |= focus.has(i) || received.contains(i);
                    }
                }
                if (homes.size() < 2 || !inFocus) {
                    continue;
                }
                int stack = Math.max(1, keys.get(key).maxStack());
                for (int step = 0; step < 256 && !full(); step++) {
                    long total = 0;
                    int fullest = homes.get(0), emptiest = homes.get(0);
                    for (int i : homes) {
                        long here = amount(i, key);
                        total += here;
                        if (here > amount(fullest, key)) {
                            fullest = i;
                        }
                        if (here < amount(emptiest, key)) {
                            emptiest = i;
                        }
                    }
                    double average = (double) total / homes.size();
                    long hi = amount(fullest, key), lo = amount(emptiest, key);
                    if (hi - average <= stack && average - lo <= stack) {
                        break;
                    }
                    int slot = -1;
                    SortSnapshot.Inv from = invs.get(fullest);
                    for (int s = from.size() - 1; s >= 0; s--) {
                        if (from.key()[s] == key) {
                            slot = s;
                            break;
                        }
                    }
                    int want = (int) Math.max(1, Math.min(Integer.MAX_VALUE, (hi - lo) / 2));
                    if (slot < 0 || place(fullest, slot, emptiest, want, true, true) == 0) {
                        break;
                    }
                }
            }
        }

        boolean full() {
            return moves.size() >= MAX_MOVES;
        }

        int group(int key) {
            return keys.get(key).group();
        }

        int cap(int inv, int slot, int key) {
            return Math.max(1, Math.min(invs.get(inv).limit()[slot], keys.get(key).maxStack()));
        }

        int used(int inv) {
            int used = 0;
            for (int k : invs.get(inv).key()) {
                if (k >= 0) {
                    used++;
                }
            }
            return used;
        }

        /** How many of each key {@code inv} holds. */
        Map<Integer, Long> counts(int inv) {
            Map<Integer, Long> counts = new HashMap<>();
            SortSnapshot.Inv in = invs.get(inv);
            for (int slot = 0; slot < in.size(); slot++) {
                if (in.key()[slot] >= 0) {
                    counts.merge(in.key()[slot], (long) in.count()[slot], Long::sum);
                }
            }
            return counts;
        }

        /** Slots {@code count} of {@code key} fill once merged into full stacks. */
        int need(int key, long count) {
            int stack = Math.max(1, keys.get(key).maxStack());
            return (int) ((count + stack - 1) / stack);
        }

        /** The slots {@code inv}'s contents would fill merged: the same before and after stacks are merged. */
        int needed(int inv) {
            int needed = 0;
            for (Map.Entry<Integer, Long> entry : counts(inv).entrySet()) {
                needed += need(entry.getKey(), entry.getValue());
            }
            return needed;
        }

        /** The slots each group fills across the network, merged. */
        Map<Integer, Integer> groupSlots() {
            Map<Integer, Long> totals = new HashMap<>();
            for (int i = 0; i < invs.size(); i++) {
                counts(i).forEach((key, count) -> totals.merge(key, count, Long::sum));
            }
            Map<Integer, Integer> slots = new HashMap<>();
            totals.forEach((key, count) -> slots.merge(group(key), need(key, count), Integer::sum));
            return slots;
        }

        /**
         * The group that would fill the most of {@code inv}'s slots once merged; ties go to the group with more slots
         * overall, then more items. Merged counts, so merging stacks never changes what a chest is for.
         */
        int dominantGroup(int inv, Map<Integer, Integer> groupSlots) {
            Map<Integer, Integer> slots = new HashMap<>();
            Map<Integer, Long> items = new HashMap<>();
            counts(inv).forEach((key, count) -> {
                slots.merge(group(key), need(key, count), Integer::sum);
                items.merge(group(key), count, Long::sum);
            });
            int best = -1;
            for (int g : slots.keySet()) {
                if (best < 0 || compareGroups(g, best, slots, items, groupSlots) < 0) {
                    best = g;
                }
            }
            // the group it was for keeps it while that group is still there and no other fills clearly more
            int claim = invs.get(inv).claim();
            if (claim >= 0 && claim != best && slots.containsKey(claim) && slots.get(best) < slots.get(claim) + tolerance) {
                return claim;
            }
            return best;
        }

        private static int compareGroups(int a, int b, Map<Integer, Integer> slots, Map<Integer, Long> items,
                                         Map<Integer, Integer> groupSlots) {
            int c = Integer.compare(slots.get(b), slots.get(a));
            if (c == 0) {
                c = Integer.compare(groupSlots.getOrDefault(b, 0), groupSlots.getOrDefault(a, 0));
            }
            if (c == 0) {
                c = Long.compare(items.get(b), items.get(a));
            }
            return c != 0 ? c : Integer.compare(a, b);
        }

        /** Whether some singleton sits where it doesn't belong: anywhere but a chest of singletons or one claiming it. */
        boolean singletonAstray(int[] type, int[] dominant, List<Set<Integer>> pinGroups, Map<Integer, Integer> groupSlots) {
            for (int i = 0; i < invs.size(); i++) {
                if (type[i] == MISC) {
                    continue;
                }
                for (int k : invs.get(i).key()) {
                    if (k < 0 || !singleton(groupSlots, group(k))) {
                        continue;
                    }
                    int g = group(k);
                    boolean home = type[i] == PINNED
                            ? pinGroups.get(i).contains(g) || invs.get(i).pins().contains(k)
                            : type[i] == DOMINANT && dominant[i] == g;
                    if (!home) {
                        return true;
                    }
                }
            }
            return false;
        }

        /** {@code invs} ordered by how many of {@code key} each held, most first, then by index. */
        List<Integer> byHeld(List<Integer> list, int key) {
            List<Integer> sorted = new ArrayList<>(list);
            sorted.sort(Comparator.<Integer>comparingInt(i -> -held.get(i).getOrDefault(key, 0)).thenComparingInt(i -> i));
            return sorted;
        }

        /** Storage pinned for {@code key}, then pinned (not strictly) for its group. */
        List<Integer> pinnedFor(List<Integer> storage, int key) {
            List<Integer> pinned = new ArrayList<>();
            int strict = 0;
            for (int i : storage) {
                if (invs.get(i).pins().contains(key)) {
                    pinned.add(i);
                    if (invs.get(i).strict()) {
                        strict++;
                    }
                }
            }
            if (strict >= 2) {
                // shared strictly between chests: the one holding fewest first
                pinned.sort(Comparator.<Integer>comparingInt(i -> invs.get(i).strict() ? 0 : 1)
                        .thenComparingLong(i -> invs.get(i).strict() ? amount(i, key) : 0).thenComparingInt(i -> i));
            }
            int group = group(key);
            for (int i : storage) {
                if (pinned.contains(i) || invs.get(i).strict()) {
                    continue;
                }
                for (int pin : invs.get(i).pins()) {
                    if (group(pin) == group) {
                        pinned.add(i);
                        break;
                    }
                }
            }
            return pinned;
        }

        /**
         * Where {@code key} best goes in compact mode: chests pinned for it, then chests by how many they held of it.
         * With {@code respectPins}, chests pinned for something else come last. Strict chests not pinned for it are
         * left out.
         */
        List<Integer> preference(List<Integer> storage, int key, boolean respectPins) {
            List<Integer> pref = pinnedFor(storage, key);
            List<Integer> rest = new ArrayList<>();
            List<Integer> otherPinned = new ArrayList<>();
            for (int i : storage) {
                if (pref.contains(i) || invs.get(i).refuses(key)) {
                    continue;
                }
                if (respectPins && !invs.get(i).pins().isEmpty()) {
                    otherPinned.add(i);
                } else {
                    rest.add(i);
                }
            }
            pref.addAll(byHeld(rest, key));
            pref.addAll(byHeld(otherPinned, key));
            return pref;
        }

        /**
         * Moves up to {@code max} of what is in {@code srcSlot} of {@code src} into {@code dst}: onto partial stacks of
         * the same item if {@code partials}, then into empty slots if {@code empties}. Returns how many moved.
         */
        int place(int src, int srcSlot, int dst, int max, boolean partials, boolean empties) {
            SortSnapshot.Inv from = invs.get(src);
            SortSnapshot.Inv to = invs.get(dst);
            int key = from.key()[srcSlot];
            if (key < 0 || to.source() || from.frozen() || to.frozen()) {
                return 0;
            }
            int left = Math.min(max, from.count()[srcSlot]);
            int moved = 0;
            for (int pass = 0; pass < 2 && left > 0; pass++) {
                if (pass == 0 && !partials || pass == 1 && !empties) {
                    continue;
                }
                for (int slot = 0; slot < to.size() && left > 0 && !full(); slot++) {
                    if (dst == src && slot == srcSlot) {
                        continue;
                    }
                    boolean match = pass == 0 ? to.key()[slot] == key : to.key()[slot] < 0;
                    if (!match) {
                        continue;
                    }
                    int space = cap(dst, slot, key) - to.count()[slot];
                    if (space <= 0) {
                        continue;
                    }
                    int amount = Math.min(space, left);
                    transfer(src, srcSlot, dst, slot, amount);
                    left -= amount;
                    moved += amount;
                }
            }
            return moved;
        }

        void transfer(int src, int srcSlot, int dst, int dstSlot, int amount) {
            SortSnapshot.Inv from = invs.get(src);
            SortSnapshot.Inv to = invs.get(dst);
            int key = from.key()[srcSlot];
            moves.add(new Move(src, srcSlot, dst, dstSlot, key, amount));
            if (dst != src) {
                received.add(dst);
            }
            from.count()[srcSlot] -= amount;
            if (from.count()[srcSlot] <= 0) {
                from.count()[srcSlot] = 0;
                from.key()[srcSlot] = -1;
            }
            to.key()[dstSlot] = key;
            to.count()[dstSlot] += amount;
        }

        /** Merges partial stacks of each item within {@code inv}, the later onto the earlier. */
        void mergeWithin(int inv) {
            SortSnapshot.Inv in = invs.get(inv);
            for (int a = 0; a < in.size() && !full(); a++) {
                int key = in.key()[a];
                if (key < 0 || in.count()[a] >= cap(inv, a, key)) {
                    continue;
                }
                for (int b = in.size() - 1; b > a && in.count()[a] < cap(inv, a, key) && !full(); b--) {
                    if (in.key()[b] == key && in.count()[b] < cap(inv, b, key)) {
                        int amount = Math.min(cap(inv, a, key) - in.count()[a], in.count()[b]);
                        transfer(inv, b, inv, a, amount);
                    }
                }
            }
        }

        /** Moves everything in {@code inv} into {@code others}; true if it all fit. */
        boolean emptyInto(int inv, List<Integer> others) {
            SortSnapshot.Inv in = invs.get(inv);
            for (int slot = 0; slot < in.size(); slot++) {
                int key = in.key()[slot];
                if (key < 0) {
                    continue;
                }
                List<Integer> pref = new ArrayList<>();
                for (int j : preference(others, key, true)) {
                    // a pinned chest takes only what it is pinned for
                    if (invs.get(j).pins().isEmpty() || pinnedFor(List.of(j), key).contains(j)) {
                        pref.add(j);
                    }
                }
                for (int j : pref) {
                    place(inv, slot, j, Integer.MAX_VALUE, true, false);
                }
                for (int j : pref) {
                    place(inv, slot, j, Integer.MAX_VALUE, false, true);
                }
                if (in.key()[slot] >= 0) {
                    return false;
                }
            }
            return true;
        }
    }
}
