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

import com.linc.alteredaether.entity.golem.sort.SortSnapshot.Kind;

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
 * <p><b>Bulk storage</b> (a drawer) is first of all the home of whatever each of its slots holds; an empty slot is
 * given the item filling the most stacks that has no drawer yet. A compacting drawer ({@link Kind#POOLED}) only ever
 * takes in.
 *
 * <p><b>Tidy</b> works out which families get a chest ({@link Grouping}), as fine-grained as there are chests for,
 * then reads what every chest is for. A <b>pinned</b> one (an item frame, placard or filter on it) belongs to its
 * pinned items and their families, whatever it holds; a <b>strict</b> pinned one belongs to its pinned items alone,
 * and anything else in it is sent to wherever else has room. A crate ({@link Kind#MISC}) is for the miscellaneous
 * items or for the equipment ({@link #CRATE_CATEGORY}), never both: the equipment takes the crates it needs first (see
 * {@link Grouping}), and its chests give it up for them. Otherwise the family filling the most of a chest's slots is its <b>dominant</b> family. Each item goes to the
 * best home it has: pinned for it, its drawer, pinned for its family, then the dominant chest for its family holding
 * the most of it. A family no chest is for takes empty chests (the biggest first). An item with no home at all goes to
 * the crates, then the chests of miscellaneous items, and if none has room it is reported stuck. When the crates
 * can't hold all the odds and ends, the unstackable ones have them first: stackable ones that don't fit beside those
 * take the chests of miscellaneous items before the crates.
 *
 * <p>Only a few of an item ({@link #MISC_MAX_ITEMS} at most) are ever miscellaneous. Any more, and an item whose family
 * has no group counts towards the group nearest it in the family tree, so goes wherever that family does; and when its
 * family's chests have no room, the next nearest family's take it, then the next, before the crates.
 *
 * <p><b>Compact</b> fills pinned chests and drawers with their items, merges partial stacks (into the chest holding the
 * most of that item), then empties the least-used chests into the others, one whole chest at a time, while one fits.
 *
 * <p>In both, a source inventory (a hopper, say) is emptied into storage, nothing is moved into one, and a sink (a brass
 * funnel into a machine) is topped up with the items its slots name. A plain container is then reordered in place by
 * {@link #orderWithin}, without moves.
 *
 * <p>A plan can be <b>focused</b> on a few inventories (see {@link Focus}): only items in those are moved, though what
 * every chest is for is still judged on the whole network. An item that moves out of the focus is followed, though:
 * the rest of it, wherever it is on the network, is moved home with it (but not the rest of its family, and not for
 * topping up a sink). A <b>frozen</b> inventory (one in use, say) keeps what it is
 * for but nothing moves into or out of it. An item pinned strictly to two or more chests is shared out between them:
 * new ones go to the chest holding fewest, and the chests are kept within a stack of each other.
 */
public final class Planner {
    /** Moves planned at most at once; the rest wait for the next plan. */
    public static final int MAX_MOVES = 512;
    /** Times at most a focused plan is made again to follow the items it moved. */
    private static final int FOLLOW_PASSES = 3;
    /** The fewest slots a family fills to be given a chest that would otherwise stay empty: two stacks. */
    private static final int SPARE_FILL = 2;
    /**
     * The most of one item that goes in with the miscellaneous items: any more and it goes to the family nearest its
     * own instead, crate or no crate.
     */
    public static final int MISC_MAX_ITEMS = 16;
    /**
     * The category (a child of the root, as {@code GolemPaths} names it) a crate may be for instead of the odds and
     * ends, with all its families.
     */
    public static final String CRATE_CATEGORY = "equipment";
    /** The group of the {@link #CRATE_CATEGORY} (curios and totems) that takes the crates before the rest of it. */
    public static final String CRATE_FIRST = "accessories";

    /**
     * Which inventories a plan moves items out of.
     *
     * @param sources the inventories (by index) whose items may move, or null for every one
     * @param gather  inventories whose pins just changed (or a sink that wants more): the items pinned to them are
     *                pulled in from anywhere
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
     * How a plan judges.
     *
     * @param tolerance     slots another family must fill beyond a chest's claimed family before tidy mode gives the
     *                      chest to it
     * @param minFill       the share of a chest a family must fill to get one of its own (at least two slots)
     * @param bulkMinStacks the fewest stacks of an item worth giving an empty drawer slot
     */
    public record Settings(int tolerance, double minFill, int bulkMinStacks) {
        public static final Settings DEFAULT = new Settings(0, 0, 2);
    }

    /**
     * @param moves    the moves, in the order to make them
     * @param unplaced items a source inventory holds that no storage has room for
     * @param claims   the family each inventory was judged to be for (-1 for none), to pass back as its claim next time
     * @param stuck    inventories holding items that belong elsewhere but found no room there
     */
    public record Plan(List<Move> moves, int unplaced, int[] claims, Set<Integer> stuck) {
        public boolean isEmpty() {
            return moves.isEmpty();
        }
    }

    private Planner() {
    }

    /** A whole-network plan with the default settings. */
    public static Plan plan(SortSnapshot snapshot, SortMode mode) {
        return plan(snapshot, mode, Focus.ALL, Settings.DEFAULT);
    }

    public static Plan plan(SortSnapshot snapshot, SortMode mode, Focus focus, int tolerance) {
        return plan(snapshot, mode, focus, new Settings(tolerance, 0, 2));
    }

    public static Plan plan(SortSnapshot snapshot, SortMode mode, Focus focus, Settings settings) {
        return planRound(snapshot, mode, focus, settings, false).plan();
    }

    /** A plan, and whether a focused one was made a whole one because the layout shifted. */
    public record Round(Plan plan, boolean escalated) {
    }

    /**
     * What GolemSorter.round does: a focused plan, or a whole one when the layout has shifted ({@link #layoutChanged}).
     * Tidy's layout is worked out once and shared by the focused plan, its follow passes and the escalation.
     */
    public static Round planRound(SortSnapshot snapshot, SortMode mode, Focus focus, Settings settings,
                                  boolean escalate) {
        if (mode != SortMode.TIDY) {
            Plan plan = planFollowing(snapshot, mode, focus, settings);
            if (escalate && !focus.all() && layoutChanged(snapshot, plan)) {
                return new Round(planFollowing(snapshot, mode, new Focus(null, focus.gather()), settings), true);
            }
            return new Round(plan, false);
        }
        Work base = new Work(snapshot, focus, settings);
        base.assignBulk();
        Layout layout = layout(base);
        boolean escalated = false;
        if (escalate && !focus.all() && changed(snapshot, base.claims)) {
            focus = new Focus(null, focus.gather());
            escalated = true;
        }
        Set<Integer> follow = new HashSet<>();
        Plan plan = pass(base, layout, focus, follow);
        for (int pass = 0; pass < FOLLOW_PASSES && !focus.all(); pass++) {
            Set<Integer> moved = new HashSet<>(follow);
            for (Move move : plan.moves()) {
                if (move.srcInv() != move.dstInv() && snapshot.invs().get(move.dstInv()).kind() != Kind.SINK) {
                    moved.add(move.key());
                }
            }
            if (moved.size() == follow.size()) {
                break;
            }
            follow = moved;
            plan = pass(base, layout, focus, follow);
        }
        return new Round(plan, escalated);
    }

    private static Plan pass(Work base, Layout layout, Focus focus, Set<Integer> follow) {
        Work w = new Work(base, focus);
        w.gatherKeys.addAll(follow);
        moves(w, layout);
        return new Plan(List.copyOf(w.moves), w.unplaced, w.claims.clone(), Set.copyOf(w.stuck));
    }

    private static boolean changed(SortSnapshot snapshot, int[] claims) {
        for (int i = 0; i < snapshot.invs().size(); i++) {
            SortSnapshot.Inv inv = snapshot.invs().get(i);
            if (!inv.isEmpty() && !inv.frozen() && claims[i] != inv.claim()) {
                return true;
            }
        }
        return false;
    }

    private static Plan planFollowing(SortSnapshot snapshot, SortMode mode, Focus focus, Settings settings) {
        Set<Integer> follow = new HashSet<>();
        Plan plan = plan(snapshot, mode, focus, settings, follow);
        // a focused plan follows the items it moves: the rest of each across the network comes together too
        for (int pass = 0; pass < FOLLOW_PASSES && !focus.all(); pass++) {
            Set<Integer> moved = new HashSet<>(follow);
            for (Move move : plan.moves()) {
                if (move.srcInv() != move.dstInv() && snapshot.invs().get(move.dstInv()).kind() != Kind.SINK) {
                    moved.add(move.key());
                }
            }
            if (moved.size() == follow.size()) {
                break;
            }
            follow = moved;
            plan = plan(snapshot, mode, focus, settings, follow);
        }
        return plan;
    }

    private static Plan plan(SortSnapshot snapshot, SortMode mode, Focus focus, Settings settings,
                             Set<Integer> follow) {
        Work work = new Work(snapshot, focus, settings);
        work.gatherKeys.addAll(follow);
        work.assignBulk();
        compact(work);
        return new Plan(List.copyOf(work.moves), work.unplaced, work.claims, Set.copyOf(work.stuck));
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Tidy

    /** A chest its family has more of than it needs: home to nothing, so what it holds goes home. */
    private static final int SOURCE = -1, EMPTY = 0, PINNED = 1, DOMINANT = 2, CRATE = 3, BULK = 4, SINK = 5,
            SURPLUS = 6;

    /** What tidy judged every inventory to be for: the same for every pass of one round, focused or not. */
    private static final class Layout {
        int n;
        int[] type, dominant;
        List<Set<Integer>> pinGroups;
        List<Integer> crates, equipmentCrates, order;
        /**
         * The stackable odds and ends that take the odds and ends' crates only after the chests of miscellaneous
         * items: those the crates have no room for once the unstackable ones are in (a crate counts items, not
         * stacks, so it holds far more of what doesn't stack than a chest does).
         */
        Set<Integer> crateLast = Set.of();
        Map<Integer, List<Integer>> exclusive, newHome;
        final Map<Integer, List<Integer>> pinnedByKey = new HashMap<>(), pinnedByGroup = new HashMap<>(),
                dominantByGroup = new HashMap<>();
        final Map<Integer, int[]> primary = new HashMap<>(), full = new HashMap<>();
        /** How many of each key's homes are its own family's, at the front; the rest are other families' room. */
        final Map<Integer, Integer> own = new HashMap<>();
        final Map<Integer, List<Integer>> nearby = new HashMap<>();
    }

    private static Layout layout(Work w) {
        int n = w.invs.size();
        Layout L = new Layout();
        L.n = n;
        w.group = w.grouping();
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
                    groups.add(w.groupOf(key));
                }
            }
            pinGroups.add(groups);
            type[i] = switch (inv.kind()) {
                case SOURCE -> SOURCE;
                case SINK -> SINK;
                case BULK, POOLED -> BULK;
                default -> !inv.pins().isEmpty() ? PINNED : inv.kind() == Kind.MISC ? CRATE : inv.isEmpty() ? EMPTY
                        : DOMINANT;
            };
            if (type[i] == DOMINANT) {
                dominant[i] = w.dominantGroup(i, groupSlots);
            }
        }
        // a family keeps only as many chests as it fills, those holding the most of it: the rest give it up, so a
        // family spread over two chests comes together in one
        int size = w.chestSize();
        Map<Integer, List<Integer>> byGroup = new HashMap<>();
        for (int i = 0; i < n; i++) {
            if (type[i] == DOMINANT) {
                byGroup.computeIfAbsent(dominant[i], g -> new ArrayList<>()).add(i);
            }
        }
        for (Map.Entry<Integer, List<Integer>> entry : byGroup.entrySet()) {
            int group = entry.getKey();
            List<Integer> chests = entry.getValue();
            // a group the crates hold keeps none: its chests give it up for the crates
            int keep = w.crated(group) ? 0 : Math.max(1, (groupSlots.getOrDefault(group, 0) + size - 1) / size);
            if (chests.size() <= keep) {
                continue;
            }
            Map<Integer, Integer> slotsHere = new HashMap<>();
            for (int i : chests) {
                slotsHere.put(i, w.groupSlotsIn(i, group));
            }
            // frozen chests keep theirs (nothing can leave them); then the chest it was for, then the most of it
            chests.sort(Comparator.<Integer>comparingInt(i -> w.invs.get(i).frozen() ? 0 : 1)
                    .thenComparingInt(i -> w.invs.get(i).claim() == group ? 0 : 1)
                    .thenComparingInt(i -> -slotsHere.get(i)).thenComparingInt(i -> i));
            for (int i : chests.subList(keep, chests.size())) {
                type[i] = SURPLUS;
                dominant[i] = -1;
            }
        }
        // each crate is the equipment's or the odds and ends'
        List<Integer> crates = new ArrayList<>(), equipmentCrates = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (type[i] == CRATE) {
                boolean equipment = w.equipmentCrates.contains(i);
                (equipment ? equipmentCrates : crates).add(i);
                dominant[i] = equipment ? w.equipmentNode() : SortSnapshot.ROOT;
            }
            w.claims[i] = dominant[i];
        }
        Map<Integer, List<Integer>> exclusive = w.exclusiveHomes();

        // families no chest is for, biggest first, each take as many empty chests as they fill
        Set<Integer> claimed = new HashSet<>();
        long crateSlots = 0;
        for (int i = 0; i < n; i++) {
            if (type[i] == PINNED) {
                claimed.addAll(pinGroups.get(i));
            } else if (type[i] == DOMINANT) {
                claimed.add(dominant[i]);
            }
        }
        long crateItems = 0;
        for (int i : crates) {
            SortSnapshot.Inv crate = w.invs.get(i);
            crateSlots += Math.min(crate.size(), crate.capacity() / 64L);
            crateItems += Math.min(crate.capacity(), crate.size() * 64L);
        }
        L.crateLast = crateLast(w, crates, crateItems);
        // the odds and ends' crates are for them, while they have room for them all
        if (crateSlots > 0 && crateSlots >= groupSlots.getOrDefault(SortSnapshot.ROOT, 0)) {
            claimed.add(SortSnapshot.ROOT);
        }
        // and the equipment's for the equipment groups they have room for
        for (int group : groupSlots.keySet()) {
            if (w.crated(group)) {
                claimed.add(group);
            }
        }
        List<Integer> unclaimed = new ArrayList<>();
        for (int group : groupSlots.keySet()) {
            if (!claimed.contains(group)) {
                unclaimed.add(group);
            }
        }
        unclaimed.sort(Comparator.<Integer>comparingInt(g -> -groupSlots.get(g)).thenComparingInt(g -> g));
        // families that have chests but fill more than those hold: room for the rest, before anything new is placed,
        // so a full chest's overflow lands beside it rather than in some other family's chest
        Map<Integer, Integer> overflow = new HashMap<>();
        for (int i = 0; i < n; i++) {
            if (type[i] == DOMINANT) {
                overflow.merge(dominant[i], -w.invs.get(i).size(), Integer::sum);
            } else if (type[i] == PINNED) {
                for (int g : pinGroups.get(i)) {
                    overflow.merge(g, -w.invs.get(i).size(), Integer::sum);
                }
            }
        }
        List<Integer> overflowing = new ArrayList<>();
        for (Map.Entry<Integer, Integer> entry : overflow.entrySet()) {
            int group = entry.getKey();
            int left = groupSlots.getOrDefault(group, 0) + entry.getValue();
            if (left > 0 && group != SortSnapshot.ROOT && !w.crated(group)) {
                entry.setValue(left);
                overflowing.add(group);
            }
        }
        overflowing.sort(Comparator.<Integer>comparingInt(g -> -overflow.get(g)).thenComparingInt(g -> g));
        // they take empty chests, or chests their families gave up: whichever holds the most of them already, then
        // the one nearest the family's own chests (or, for a family new to the storage, its closest relatives')
        List<Integer> free = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if ((type[i] == EMPTY || type[i] == SURPLUS) && !w.invs.get(i).frozen()) {
                free.add(i);
            }
        }
        Map<Integer, List<Integer>> newHome = new HashMap<>();
        List<Integer> wanting = new ArrayList<>(overflowing);
        wanting.addAll(unclaimed);
        for (int k = 0; k < wanting.size() && !free.isEmpty(); k++) {
            int group = wanting.get(k);
            boolean more = k < overflowing.size();
            int left = more ? overflow.get(group) : groupSlots.get(group);
            Map<Integer, Integer> slotsHere = new HashMap<>();
            for (int i : free) {
                slotsHere.put(i, w.groupSlotsIn(i, group));
            }
            List<Integer> anchors = anchors(w, group, type, dominant, pinGroups, newHome, more);
            Map<Integer, Long> distance = new HashMap<>();
            for (int i : free) {
                distance.put(i, w.distance(i, anchors));
            }
            free.sort(Comparator.<Integer>comparingInt(i -> -slotsHere.get(i))
                    .thenComparingLong(distance::get).thenComparingInt(i -> i));
            while (left > 0 && !free.isEmpty()) {
                int chest = free.remove(0);
                left -= w.invs.get(chest).size();
                if (type[chest] == SURPLUS) {
                    type[chest] = DOMINANT;
                    dominant[chest] = group;
                    w.claims[chest] = group;
                } else {
                    newHome.computeIfAbsent(group, g -> new ArrayList<>()).add(chest);
                }
            }
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
        for (int j = 0; j < n; j++) {
            if (type[j] == PINNED) {
                for (int key : w.invs.get(j).pins()) {
                    L.pinnedByKey.computeIfAbsent(key, k -> new ArrayList<>()).add(j);
                }
                for (int g : pinGroups.get(j)) {
                    L.pinnedByGroup.computeIfAbsent(g, k -> new ArrayList<>()).add(j);
                }
            } else if (type[j] == DOMINANT) {
                L.dominantByGroup.computeIfAbsent(dominant[j], k -> new ArrayList<>()).add(j);
            }
        }
        L.type = type;
        L.dominant = dominant;
        L.pinGroups = pinGroups;
        L.crates = crates;
        L.equipmentCrates = equipmentCrates;
        L.exclusive = exclusive;
        L.newHome = newHome;
        L.order = order;
        return L;
    }

    /**
     * The chests a chest for {@code group} is best placed beside: its own (pinned for it or kept for it) when
     * {@code own}, else those of its closest relatives that have any, chests just given out included.
     */
    private static List<Integer> anchors(Work w, int group, int[] type, int[] dominant, List<Set<Integer>> pinGroups,
                                         Map<Integer, List<Integer>> newHome, boolean own) {
        List<Integer> out = new ArrayList<>();
        if (w.positions == null) {
            return out;
        }
        List<Integer> families = own || group <= SortSnapshot.ROOT ? List.of(group) : w.nearestGroups(group);
        for (int g : families) {
            for (int i = 0; i < type.length; i++) {
                if (type[i] == DOMINANT && dominant[i] == g || type[i] == PINNED && pinGroups.get(i).contains(g)) {
                    out.add(i);
                }
            }
            out.addAll(newHome.getOrDefault(g, List.of()));
            if (!out.isEmpty()) {
                break;
            }
        }
        return out;
    }

    private static int indexOf(int[] list, int x) {
        for (int k = 0; k < list.length; k++) {
            if (list[k] == x) {
                return k;
            }
        }
        return -1;
    }

    /**
     * {@code key}'s homes, best first, with the strict chests that refuse it left out: the same for every slot of it
     * this round, so worked out once. Without {@code all}, an item too many for the odds and ends may be given only
     * the homes its own family has; the nearest families' are added only when asked for.
     */
    private static int[] homes(Work w, Layout L, int key, int group, boolean all) {
        int[] cached = (all ? L.full : L.primary).get(key);
        if (cached != null) {
            return cached;
        }
        List<Integer> homes = new ArrayList<>();
        boolean[] in = new boolean[L.n];
        for (int j : L.pinnedByKey.getOrDefault(key, List.of())) {
            homes.add(j);
            in[j] = true;
        }
        for (int j : w.bulkHomes.getOrDefault(key, List.of())) {
            if (!in[j]) {
                homes.add(j);
                in[j] = true;
            }
        }
        List<Integer> tier = new ArrayList<>();
        for (int j : L.pinnedByGroup.getOrDefault(group, List.of())) {
            if (!in[j]) {
                tier.add(j);
            }
        }
        addAll(homes, in, w.byHeld(tier, key));
        if (group == SortSnapshot.ROOT) {
            if (!stackableLast(w, L, key)) {
                addAll(homes, in, w.byHeld(L.crates, key));
            }
        } else if (w.crated(group)) {
            addAll(homes, in, w.byHeld(L.equipmentCrates, key));
        }
        addAll(homes, in, w.byHeld(L.dominantByGroup.getOrDefault(group, List.of()), key));
        // then the chests just given to its family: all it has if it had none, else room for what overflows
        for (int j : L.newHome.getOrDefault(group, List.of())) {
            if (!in[j]) {
                homes.add(j);
                in[j] = true;
            }
        }
        if (group == SortSnapshot.ROOT && stackableLast(w, L, key)) {
            addAll(homes, in, w.byHeld(L.crates, key));
        }
        boolean lazy = false;
        int own = homes.size();
        if (w.many.contains(key)) {
            // too many to be odds and ends: when its family has no room, the nearest family with room takes it instead
            if (!all && !homes.isEmpty()) {
                lazy = true;
            } else {
                for (int j : nearby(w, L, key, group)) {
                    if (!in[j]) {
                        homes.add(j);
                        in[j] = true;
                    }
                }
            }
        }
        if (!lazy && homes.isEmpty()) {
            // nowhere for its family: in with the miscellaneous items
            boolean last = stackableLast(w, L, key);
            if (!last) {
                addAll(homes, in, w.byHeld(L.crates, key));
            }
            addAll(homes, in, w.byHeld(L.dominantByGroup.getOrDefault(SortSnapshot.ROOT, List.of()), key));
            addAll(homes, in, L.newHome.getOrDefault(SortSnapshot.ROOT, List.of()));
            if (last) {
                addAll(homes, in, w.byHeld(L.crates, key));
            }
            own = homes.size();
        }
        // a strict chest never takes what isn't pinned to it, even as its family's dominant chest
        int kept = 0;
        for (int x = 0; x < own; x++) {
            kept += w.invs.get(homes.get(x)).refuses(key) ? 0 : 1;
        }
        L.own.put(key, kept);
        int[] out = homes.stream().filter(j -> !w.invs.get(j).refuses(key)).mapToInt(Integer::intValue).toArray();
        L.primary.put(key, out);
        if (!lazy) {
            L.full.put(key, out);
        }
        return out;
    }

    /** Whether {@code key} takes the odds and ends' crates after their chests (see {@link Layout#crateLast}). */
    private static boolean stackableLast(Work w, Layout L, int key) {
        return L.crateLast.contains(key);
    }

    /**
     * The stackable odds and ends left out of the odds and ends' {@code crates} (holding {@code room} items in all):
     * none while no odds and ends are unstackable or the crates have room for them all. Otherwise the unstackable
     * ones have the room first, then each stackable one that fits whole in what is left, those already in the
     * crates first.
     */
    private static Set<Integer> crateLast(Work w, List<Integer> crates, long room) {
        Map<Integer, Long> misc = w.miscTotals();
        long unstackable = 0, all = 0;
        for (Map.Entry<Integer, Long> entry : misc.entrySet()) {
            all += entry.getValue();
            if (w.keys.get(entry.getKey()).maxStack() <= 1) {
                unstackable += entry.getValue();
            }
        }
        if (crates.isEmpty() || unstackable == 0 || all <= room) {
            return Set.of();
        }
        Map<Integer, Integer> inCrates = new HashMap<>();
        for (int c : crates) {
            w.held.get(c).forEach((key, count) -> inCrates.merge(key, count, Integer::sum));
        }
        List<Integer> stackable = new ArrayList<>();
        for (int key : misc.keySet()) {
            if (w.keys.get(key).maxStack() > 1) {
                stackable.add(key);
            }
        }
        stackable.sort(Comparator.<Integer>comparingInt(k -> inCrates.containsKey(k) ? 0 : 1)
                .thenComparingInt(k -> k));
        long left = room - unstackable;
        Set<Integer> last = new HashSet<>();
        for (int key : stackable) {
            long count = misc.get(key);
            if (count <= left) {
                left -= count;
            } else {
                last.add(key);
            }
        }
        return last;
    }

    private static void addAll(List<Integer> homes, boolean[] in, List<Integer> add) {
        for (int j : add) {
            if (in[j]) {
                continue;
            }
            homes.add(j);
            in[j] = true;
        }
    }

    private static void moves(Work w, Layout L) {
        int n = w.invs.size();
        int[] type = L.type;
        for (int i : L.order) {
            SortSnapshot.Inv inv = w.invs.get(i);
            if (inv.frozen() || !w.movable(i)) {
                continue;
            }
            for (int slot = 0; slot < inv.size() && !w.full(); slot++) {
                int key = inv.key()[slot];
                if (key < 0 || !w.consider(i, key)) {
                    continue;
                }
                int group = w.groupOf(key);
                int[] homes;
                int at, own;
                List<Integer> shared = L.exclusive.get(key);
                if (shared != null) {
                    // pinned strictly to several chests: it stays in any of them (they are evened out below), and
                    // from anywhere else it goes to the one holding fewest
                    if (shared.contains(i)) {
                        continue;
                    }
                    homes = w.byFewest(shared, key).stream().filter(j -> !w.invs.get(j).refuses(key))
                            .mapToInt(Integer::intValue).toArray();
                    at = indexOf(homes, i);
                    own = homes.length;
                } else {
                    // the nearest families' homes are only needed when it isn't already in one of its own
                    homes = homes(w, L, key, group, false);
                    at = indexOf(homes, i);
                    if (at < 0) {
                        homes = homes(w, L, key, group, true);
                        at = indexOf(homes, i);
                    }
                    own = L.own.get(key);
                }
                if (at == 0) {
                    continue;
                }
                int[] better;
                int length = at > 0 ? at : homes.length;
                boolean evicting = inv.refuses(key);
                if (inv.source() || evicting) {
                    // anywhere else will do: the crates for it and misc chests, then unpinned chests, then ones
                    // pinned for something else (never a crate for something else)
                    boolean[] mark = new boolean[n];
                    int[] wider = new int[length + n + L.crates.size() + L.equipmentCrates.size()];
                    int size = 0;
                    for (int x = 0; x < length; x++) {
                        wider[size++] = homes[x];
                        mark[homes[x]] = true;
                    }
                    for (int c : w.crated(group) ? L.equipmentCrates : L.crates) {
                        if (c != i && !mark[c]) {
                            wider[size++] = c;
                            mark[c] = true;
                        }
                    }
                    for (int pass = 0; pass < 2; pass++) {
                        for (int j = 0; j < n; j++) {
                            if (j == i || type[j] == SOURCE || type[j] == SINK || type[j] == BULK || type[j] == CRATE
                                    || mark[j] || w.invs.get(j).refuses(key)
                                    || (pass == 0) == (type[j] == PINNED)) {
                                continue;
                            }
                            wider[size++] = j;
                            mark[j] = true;
                        }
                    }
                    better = wider;
                    length = size;
                } else {
                    better = homes;
                }
                for (int x = 0; x < length; x++) {
                    if (inv.key()[slot] < 0 || w.full()) {
                        break;
                    }
                    w.place(i, slot, better[x], Integer.MAX_VALUE, true, true);
                }
                // stuck: it belongs elsewhere and found no room there. An item already in one of its family's homes
                // (the second chest of a family that fills more than one, say) is at home, however full the better
                // ones are; one only lodging with another family is not.
                if (inv.key()[slot] >= 0 && (at < 0 || at >= own || evicting || inv.source())) {
                    w.stuck.add(i);
                }
                if (inv.source() && inv.key()[slot] >= 0) {
                    w.unplaced += inv.count()[slot];
                }
            }
        }

        w.feedSinks();
        w.balance(L.exclusive);
        // partial stacks of one item within a chest are merged; a plain chest does that itself (see orderWithin)
        for (int i = 0; i < n; i++) {
            if ((type[i] == PINNED || type[i] == DOMINANT || type[i] == CRATE || type[i] == EMPTY || type[i] == SURPLUS)
                    && !w.invs.get(i).plain() && w.touched(i)) {
                w.mergeWithin(i);
            }
        }
    }

    /**
     * The chests of the families other than {@code group}, nearest {@code key}'s own family first; within a family,
     * those pinned for it, then those it is the dominant family of, holding the most of {@code key} first, then the
     * chests just given to it (or the equipment's crates, for a group they hold).
     */
    private static List<Integer> nearby(Work w, Layout L, int key, int group) {
        List<Integer> cached = L.nearby.get(key);
        if (cached != null) {
            return cached;
        }
        List<Integer> homes = new ArrayList<>();
        for (int near : w.nearestGroups(w.keys.get(key).node())) {
            if (near == group) {
                continue;
            }
            homes.addAll(w.byHeld(L.pinnedByGroup.getOrDefault(near, List.of()), key));
            homes.addAll(w.byHeld(L.dominantByGroup.getOrDefault(near, List.of()), key));
            homes.addAll(L.newHome.getOrDefault(near, List.of()));
            if (w.crated(near)) {
                for (int c : w.byHeld(L.equipmentCrates, key)) {
                    if (!homes.contains(c)) {
                        homes.add(c);
                    }
                }
            }
        }
        L.nearby.put(key, homes);
        return homes;
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
            Kind kind = w.invs.get(i).kind();
            if (kind != Kind.SOURCE && kind != Kind.SINK) {
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

        // 2. pinned chests and drawers take their items
        for (int i : storage) {
            SortSnapshot.Inv inv = w.invs.get(i);
            if (inv.frozen() || !w.movable(i)) {
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
            if (inv.frozen() || !w.movable(i)) {
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
                if (inv.frozen() || shared.contains(i) || inv.kind() == Kind.POOLED) {
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
                    space = (int) Math.min(space, w.room(dst[0]));
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
            boolean chest = inv.kind() == Kind.STORAGE || inv.kind() == Kind.MISC;
            if (chest && inv.pins().isEmpty() && !inv.isEmpty() && !inv.frozen() && w.focus.has(i)) {
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

        w.feedSinks();
        w.balance(exclusive);
    }

    /**
     * Whether {@code plan} judges a chest to be for something other than its claim: the network's layout has shifted,
     * so a focused sort should become a whole one, or the rest of what that chest was (or is now) for stays put. An
     * empty chest is left out (it lost its claim to the moves that emptied it, which shifts nothing), and so is a
     * frozen one (it keeps its claim).
     */
    public static boolean layoutChanged(SortSnapshot snapshot, Plan plan) {
        for (int i = 0; i < snapshot.invs().size(); i++) {
            SortSnapshot.Inv inv = snapshot.invs().get(i);
            if (!inv.isEmpty() && !inv.frozen() && plan.claims()[i] != inv.claim()) {
                return true;
            }
        }
        return false;
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
        return new SortSnapshot.Inv(inv.kind(), inv.plain(), inv.limit(), key, count, inv.only(), inv.capacity(),
                inv.pins(), inv.strict(), inv.frozen(), inv.claim());
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
        final List<SortSnapshot.Node> nodes;
        List<SortSnapshot.Inv> invs;
        /** How many of each key each inventory held when planning began; what "holds the most" is judged on. */
        final List<Map<Integer, Integer>> held;
        /** The keys each slot held when planning began: a drawer's limit is already for the item it holds. */
        final List<int[]> heldKeys;
        final Focus focus;
        final Settings settings;
        /** Where each inventory is, or null if unknown. */
        @Nullable
        final List<SortSnapshot.Pos> positions;
        /** Keys, and families, pinned to the inventories being gathered for: those move from anywhere. */
        final Set<Integer> gatherKeys = new HashSet<>(), gatherNodes = new HashSet<>();
        final int[] claims;
        /** The drawers each key lives in. */
        Map<Integer, List<Integer>> bulkHomes = new HashMap<>();
        /** Tidy's groups, once worked out. */
        Grouping.Result group;
        /** The crates that are the equipment's, once worked out; the rest are the odds and ends'. */
        Set<Integer> equipmentCrates = Set.of();
        /** Keys with more than {@link #MISC_MAX_ITEMS} to store: never miscellaneous. */
        Set<Integer> many = new HashSet<>();
        /** Keys of {@link #many} whose family has no group, by the group nearest it they count towards instead. */
        Map<Integer, Integer> rehomed = new HashMap<>();
        /** The groups nearest each node, nearest first, once asked for. */
        Map<Integer, List<Integer>> nearest = new HashMap<>();
        List<Move> moves = new ArrayList<>();
        int unplaced;
        Set<Integer> stuck = new HashSet<>();
        /** Inventories items were moved into. */
        Set<Integer> received = new HashSet<>();
        /** Slots items were moved into, as {@code inv << 32 | slot}: what isn't there yet can't be taken on. */
        Set<Long> receivedSlots = new HashSet<>();

        Work(SortSnapshot snapshot, Focus focus, Settings settings) {
            keys = snapshot.keys();
            nodes = snapshot.nodes();
            invs = snapshot.copy().invs();
            positions = snapshot.positions();
            this.focus = focus;
            this.settings = settings;
            claims = new int[invs.size()];
            Arrays.fill(claims, -1);
            for (int g : focus.gather()) {
                SortSnapshot.Inv inv = invs.get(g);
                for (int pin : inv.pins()) {
                    gatherKeys.add(pin);
                    if (!inv.strict()) {
                        gatherNodes.add(keys.get(pin).node());
                    }
                }
                for (int only : inv.only()) {
                    if (inv.kind() == Kind.SINK && only >= 0) {
                        gatherKeys.add(only);
                    }
                }
            }
            held = new ArrayList<>(invs.size());
            heldKeys = new ArrayList<>(invs.size());
            for (SortSnapshot.Inv inv : invs) {
                Map<Integer, Integer> counts = new HashMap<>();
                for (int slot = 0; slot < inv.size(); slot++) {
                    if (inv.key()[slot] >= 0) {
                        counts.merge(inv.key()[slot], inv.count()[slot], Integer::sum);
                    }
                }
                held.add(counts);
                heldKeys.add(inv.key().clone());
            }
        }

        private Work(Work other) {
            keys = other.keys;
            nodes = other.nodes;
            invs = other.invs.stream().map(SortSnapshot.Inv::copy).toList();
            held = other.held;
            heldKeys = other.heldKeys;
            positions = other.positions;
            focus = other.focus;
            settings = other.settings;
            gatherKeys.addAll(other.gatherKeys);
            gatherNodes.addAll(other.gatherNodes);
            claims = other.claims;
            bulkHomes = other.bulkHomes;
            group = other.group;
            equipmentCrates = other.equipmentCrates;
            many = other.many;
            rehomed = other.rehomed;
            nearest = other.nearest;
            moves = new ArrayList<>(other.moves);
            unplaced = other.unplaced;
            stuck = new HashSet<>(other.stuck);
            received = new HashSet<>(other.received);
            receivedSlots = new HashSet<>(other.receivedSlots);
        }

        Work copy() {
            return new Work(this);
        }

        /** How far (squared) {@code inv} is from the nearest of {@code others}; 0 if positions are unknown. */
        long distance(int inv, List<Integer> others) {
            if (positions == null || others.isEmpty()) {
                return 0;
            }
            long best = Long.MAX_VALUE;
            for (int other : others) {
                best = Math.min(best, positions.get(inv).distSqr(positions.get(other)));
            }
            return best;
        }

        /** A fresh pass over {@code base}'s slots (drawers assigned, layout judged), with {@code focus}. */
        Work(Work base, Focus focus) {
            keys = base.keys;
            nodes = base.nodes;
            invs = base.invs.stream().map(SortSnapshot.Inv::copy).toList();
            held = base.held;
            heldKeys = base.heldKeys;
            positions = base.positions;
            this.focus = focus;
            settings = base.settings;
            gatherKeys.addAll(base.gatherKeys);
            gatherNodes.addAll(base.gatherNodes);
            claims = base.claims;
            bulkHomes = base.bulkHomes;
            group = base.group;
            equipmentCrates = base.equipmentCrates;
            many = base.many;
            rehomed = base.rehomed;
            nearest = base.nearest;
            depth = base.depth;
            groups = base.groups;
        }

        void adopt(Work other) {
            invs = other.invs;
            moves = other.moves;
            unplaced = other.unplaced;
            stuck = other.stuck;
            received = other.received;
            receivedSlots = other.receivedSlots;
        }

        /** Whether anything may be taken out of {@code inv}: never out of a sink or a compacting drawer. */
        boolean movable(int inv) {
            Kind kind = invs.get(inv).kind();
            return kind != Kind.SINK && kind != Kind.POOLED;
        }

        /** Whether the item in a slot of {@code inv} may move in this plan. */
        boolean consider(int inv, int key) {
            return movable(inv) && (focus.has(inv) || gathered(key));
        }

        /** Whether {@code key} is pinned to an inventory being gathered for. */
        boolean gathered(int key) {
            return gatherKeys.contains(key) || gatherNodes.contains(keys.get(key).node());
        }

        /** Whether {@code inv} is in the focus or had items moved into it, so is worth tidying within. */
        boolean touched(int inv) {
            return focus.has(inv) || received.contains(inv);
        }

        // -----------------------------------------------------------------------------------------------------------
        // Drawers

        /**
         * Each drawer slot is the home of what it holds, and each empty one is given the item filling the most stacks
         * outside drawers that has none (preferring the families the drawer already holds); what is left takes nothing.
         * Single drawers (1x1, the most room for one item) choose first, and take the items with the highest total count.
         */
        void assignBulk() {
            List<Integer> drawers = new ArrayList<>();
            for (int i = 0; i < invs.size(); i++) {
                SortSnapshot.Inv inv = invs.get(i);
                if (inv.kind() != Kind.BULK && inv.kind() != Kind.POOLED) {
                    continue;
                }
                for (int slot = 0; slot < inv.size(); slot++) {
                    int k = inv.key()[slot];
                    if (k >= 0) {
                        inv.only()[slot] = k;
                        List<Integer> homes = bulkHomes.computeIfAbsent(k, x -> new ArrayList<>());
                        if (!homes.contains(i)) {
                            homes.add(i);
                        }
                    }
                }
                if (inv.kind() == Kind.BULK && !inv.frozen()) {
                    drawers.add(i);
                }
            }
            boolean free = false;
            for (int i : drawers) {
                SortSnapshot.Inv inv = invs.get(i);
                for (int slot = 0; slot < inv.size(); slot++) {
                    free |= inv.key()[slot] < 0 && inv.only()[slot] == SortSnapshot.ANY;
                }
            }
            if (free) {
                Set<Integer> pinned = new HashSet<>();
                for (SortSnapshot.Inv inv : invs) {
                    pinned.addAll(inv.pins());
                }
                Map<Integer, Long> outside = new HashMap<>();
                for (SortSnapshot.Inv inv : invs) {
                    if (inv.kind() == Kind.BULK || inv.kind() == Kind.POOLED || inv.kind() == Kind.SINK) {
                        continue;
                    }
                    for (int slot = 0; slot < inv.size(); slot++) {
                        if (inv.key()[slot] >= 0) {
                            outside.merge(inv.key()[slot], (long) inv.count()[slot], Long::sum);
                        }
                    }
                }
                List<Integer> candidates = new ArrayList<>();
                Map<Integer, Integer> stacks = new HashMap<>();
                outside.forEach((k, count) -> {
                    int need = need(k, count);
                    if (need >= Math.max(1, settings.bulkMinStacks()) && !bulkHomes.containsKey(k) && !pinned.contains(k)) {
                        candidates.add(k);
                        stacks.put(k, need);
                    }
                });
                // single drawers first, each taking the item with the most in the network
                List<Integer> singles = new ArrayList<>();
                for (int i : drawers) {
                    if (single(invs.get(i))) {
                        singles.add(i);
                    }
                }
                List<Integer> byCount = new ArrayList<>(candidates);
                byCount.sort(Comparator.<Integer>comparingLong(k -> -outside.get(k)).thenComparingInt(k -> k));
                for (int i : singles) {
                    SortSnapshot.Inv inv = invs.get(i);
                    for (int slot = 0; slot < inv.size() && !byCount.isEmpty(); slot++) {
                        if (inv.key()[slot] < 0 && inv.only()[slot] == SortSnapshot.ANY) {
                            int pick = byCount.remove(0);
                            candidates.remove(Integer.valueOf(pick));
                            inv.only()[slot] = pick;
                            bulkHomes.computeIfAbsent(pick, x -> new ArrayList<>()).add(i);
                        }
                    }
                }
                drawers.removeAll(singles);
                candidates.sort(Comparator.<Integer>comparingInt(k -> -stacks.get(k)).thenComparingInt(k -> k));
                for (int i : drawers) {
                    SortSnapshot.Inv inv = invs.get(i);
                    Set<Integer> families = new HashSet<>();
                    for (int k : inv.key()) {
                        if (k >= 0) {
                            families.add(keys.get(k).node());
                        }
                    }
                    for (int slot = 0; slot < inv.size() && !candidates.isEmpty(); slot++) {
                        if (inv.key()[slot] >= 0 || inv.only()[slot] != SortSnapshot.ANY) {
                            continue;
                        }
                        int pick = candidates.get(0);
                        for (int k : candidates) {
                            if (families.contains(keys.get(k).node())) {
                                pick = k;
                                break;
                            }
                        }
                        candidates.remove(Integer.valueOf(pick));
                        inv.only()[slot] = pick;
                        families.add(keys.get(pick).node());
                        bulkHomes.computeIfAbsent(pick, x -> new ArrayList<>()).add(i);
                    }
                }
            }
            for (SortSnapshot.Inv inv : invs) {
                if (inv.kind() == Kind.BULK || inv.kind() == Kind.POOLED) {
                    for (int slot = 0; slot < inv.size(); slot++) {
                        if (inv.only()[slot] == SortSnapshot.ANY) {
                            inv.only()[slot] = SortSnapshot.NONE;
                        }
                    }
                }
            }
        }

        /** Whether {@code inv} is a single (1x1) drawer: one slot that takes items, void and locked slots aside. */
        static boolean single(SortSnapshot.Inv inv) {
            int usable = 0;
            for (int slot = 0; slot < inv.size(); slot++) {
                if (inv.key()[slot] >= 0 || inv.only()[slot] != SortSnapshot.NONE) {
                    usable++;
                }
            }
            return usable == 1;
        }

        /** How many more of {@code key} its drawers take. */
        long bulkRoom(int key) {
            long room = 0;
            for (int i : bulkHomes.getOrDefault(key, List.of())) {
                SortSnapshot.Inv inv = invs.get(i);
                if (inv.kind() != Kind.BULK || inv.frozen()) {
                    continue;
                }
                for (int slot = 0; slot < inv.size(); slot++) {
                    if (inv.key()[slot] == key || inv.key()[slot] < 0 && inv.only()[slot] == key) {
                        room += cap(i, slot, key) - inv.count()[slot];
                    }
                }
            }
            return room;
        }

        // -----------------------------------------------------------------------------------------------------------
        // Families

        /** Tidy's groups: which families get a chest, from the slots items fill outside their pinned homes and drawers. */
        Grouping.Result grouping() {
            Map<Integer, Long> totals = new HashMap<>();
            Set<Integer> pinned = new HashSet<>();
            Set<Integer> forced = new HashSet<>();
            Set<Integer> claimed = new HashSet<>();
            int chests = 0;
            List<Integer> crateInvs = new ArrayList<>();
            for (int i = 0; i < invs.size(); i++) {
                SortSnapshot.Inv inv = invs.get(i);
                if (inv.kind() != Kind.SINK && inv.kind() != Kind.POOLED) {
                    if (inv.kind() != Kind.SOURCE) {
                        pinned.addAll(inv.pins());
                    }
                    if (!inv.strict()) {
                        for (int pin : inv.pins()) {
                            forced.add(keys.get(pin).node());
                        }
                    }
                }
                if (inv.claim() >= 0) {
                    claimed.add(inv.claim());
                }
                if (inv.kind() == Kind.STORAGE && inv.pins().isEmpty()) {
                    chests++;
                } else if (inv.kind() == Kind.MISC && inv.pins().isEmpty()) {
                    crateInvs.add(i);
                }
                if (inv.kind() == Kind.BULK || inv.kind() == Kind.POOLED || inv.kind() == Kind.SINK) {
                    continue;
                }
                for (int slot = 0; slot < inv.size(); slot++) {
                    if (inv.key()[slot] >= 0) {
                        totals.merge(inv.key()[slot], (long) inv.count()[slot], Long::sum);
                    }
                }
            }
            // what is left to store outside pinned homes and drawers
            Map<Integer, Long> toStore = new HashMap<>();
            for (Map.Entry<Integer, Long> entry : totals.entrySet()) {
                int k = entry.getKey();
                if (pinned.contains(k)) {
                    continue;
                }
                long left = entry.getValue() - bulkRoom(k);
                if (left > 0) {
                    toStore.put(k, left);
                    if (left > MISC_MAX_ITEMS) {
                        many.add(k);
                    }
                }
            }
            int size = chestSize();
            int minFill = Math.max(SPARE_FILL, (int) Math.round(size * settings.minFill())) * Grouping.STACK;
            int[] slots = new int[nodes.size()], manySlots = new int[nodes.size()], fill = new int[nodes.size()];
            int[] items = new int[nodes.size()];
            toStore.forEach((k, left) -> {
                int node = keys.get(k).node();
                int need = need(k, left);
                slots[node] += need;
                items[node] = (int) Math.min(Integer.MAX_VALUE / 4, items[node] + left);
                if (many.contains(k)) {
                    manySlots[node] += need;
                }
                long stack = Math.max(1, keys.get(k).maxStack());
                fill[node] += (int) Math.min(Integer.MAX_VALUE / 4, (left * Grouping.STACK + stack - 1) / stack);
            });
            int equipment = equipmentNode();
            int[] crateSlots = new int[crateInvs.size()], was = new int[crateInvs.size()];
            long[] crateItems = new long[crateInvs.size()];
            for (int c = 0; c < crateInvs.size(); c++) {
                SortSnapshot.Inv crate = invs.get(crateInvs.get(c));
                crateSlots[c] = (int) Math.min(crate.size(), crate.capacity() / 64L);
                crateItems[c] = Math.min(crate.capacity(), crate.size() * 64L);
                was[c] = equipment >= 0 && crate.claim() == equipment ? Grouping.Crates.EQUIPMENT
                        : crate.claim() == SortSnapshot.ROOT ? Grouping.Crates.ODDS_AND_ENDS : 0;
            }
            Grouping.Result result = Grouping.compute(nodes, slots, manySlots, fill, items, crateable(), crateFirst(),
                    chests, size,
                    new Grouping.Crates(crateSlots, crateItems, was), minFill, SPARE_FILL * Grouping.STACK, forced,
                    claimed);
            equipmentCrates = new HashSet<>();
            for (int c = 0; c < crateInvs.size(); c++) {
                if (result.equipmentCrates()[c]) {
                    equipmentCrates.add(crateInvs.get(c));
                }
            }
            // too many of one item for the odds and ends, and no group for its family: it goes with the group nearest
            rehomed = new HashMap<>();
            for (int k : many) {
                int node = keys.get(k).node();
                if (result.group()[node] == SortSnapshot.ROOT && result.nearest()[node] > 0) {
                    rehomed.put(k, result.nearest()[node]);
                }
            }
            nearest = new HashMap<>();
            depth = null;
            groups = null;
            return result;
        }

        /** The {@link #CRATE_CATEGORY} node, or -1 if the tree has none. */
        int equipmentNode() {
            for (int i = 1; i < nodes.size(); i++) {
                if (nodes.get(i).parent() == SortSnapshot.ROOT && nodes.get(i).name().equals(CRATE_CATEGORY)) {
                    return i;
                }
            }
            return -1;
        }

        /** The nodes a crate may be for instead of the odds and ends: the {@link #CRATE_CATEGORY} and all under it. */
        boolean[] crateable() {
            boolean[] out = new boolean[nodes.size()];
            int equipment = equipmentNode();
            for (int i = 1; i < nodes.size(); i++) {
                out[i] = i == equipment || out[nodes.get(i).parent()];
            }
            return out;
        }

        /**
         * The {@link #CRATE_FIRST} group under the {@link #CRATE_CATEGORY} and all under it: its groups take the
         * crates' room before armour and the rest of the equipment.
         */
        boolean[] crateFirst() {
            boolean[] out = new boolean[nodes.size()];
            int equipment = equipmentNode();
            for (int i = 1; i < nodes.size(); i++) {
                SortSnapshot.Node node = nodes.get(i);
                out[i] = equipment >= 0 && node.parent() == equipment && node.name().equals(CRATE_FIRST)
                        || out[node.parent()];
            }
            return out;
        }

        /** Whether tidy keeps {@code group} in the crates rather than chests of its own. */
        boolean crated(int group) {
            return this.group != null && this.group.crated(group);
        }

        int groupOf(int key) {
            Integer moved = rehomed.get(key);
            return moved != null ? moved : group.of(keys.get(key));
        }

        /** The groups other than the root and {@code node}'s own, nearest to {@code node} in the family tree first. */
        List<Integer> nearestGroups(int node) {
            return nearest.computeIfAbsent(node, x -> nearestGroups(group, x));
        }

        /**
         * The groups of {@code result} other than the root and {@code node}'s own, closest relatives first (by how far
         * up the family tree from {@code node} they meet it), then the fewest slots filled, then the earlier node (as
         * {@link Grouping.Result#nearest} has it).
         */
        int[] depth;
        List<Integer> groups;

        private List<Integer> nearestGroups(Grouping.Result result, int node) {
            if (depth == null) {
                depth = new int[nodes.size()];
                for (int i = 1; i < nodes.size(); i++) {
                    depth[i] = depth[nodes.get(i).parent()] + 1;
                }
                Set<Integer> distinct = new java.util.LinkedHashSet<>();
                for (int g : result.group()) {
                    distinct.add(g);
                }
                groups = new ArrayList<>(distinct);
            }
            int own = result.group()[node];
            Map<Integer, Integer> distance = new HashMap<>();
            for (int g : groups) {
                if (g == SortSnapshot.ROOT || g == own || distance.containsKey(g)) {
                    continue;
                }
                // how far up from the node they meet: the closer the relative, the fewer
                int a = node, b = g, steps = 0;
                while (a != b) {
                    if (depth[a] >= depth[b]) {
                        a = nodes.get(a).parent();
                        steps++;
                    } else {
                        b = nodes.get(b).parent();
                    }
                }
                distance.put(g, steps);
            }
            List<Integer> groups = new ArrayList<>(distance.keySet());
            groups.sort(Comparator.<Integer>comparingInt(distance::get).thenComparingInt(g -> result.own()[g])
                    .thenComparingInt(g -> g));
            return groups;
        }

        /** The slots in an unpinned chest, on average (27 if there is none). */
        int chestSize() {
            int chests = 0;
            long slots = 0;
            for (SortSnapshot.Inv inv : invs) {
                if (inv.kind() == Kind.STORAGE && inv.pins().isEmpty()) {
                    chests++;
                    slots += inv.size();
                }
            }
            return chests > 0 ? (int) Math.max(1, Math.round((double) slots / chests)) : 27;
        }

        /** Each inventory's slots by group, merged, as planning began (the layout is judged before any move). */
        final Map<Integer, Map<Integer, Integer>> groupSlotsByInv = new HashMap<>();

        /** The slots {@code group}'s items fill in {@code inv}, merged. */
        int groupSlotsIn(int inv, int group) {
            return groupSlotsByInv.computeIfAbsent(inv, x -> {
                Map<Integer, Integer> out = new HashMap<>();
                held.get(x).forEach((key, count) -> out.merge(groupOf(key), need(key, count), Integer::sum));
                return out;
            }).getOrDefault(group, 0);
        }

        /** The slots each group fills across the network, merged; drawers, compacting drawers and sinks left out. */
        Map<Integer, Integer> groupSlots() {
            Map<Integer, Long> totals = new HashMap<>();
            for (int i = 0; i < invs.size(); i++) {
                Kind kind = invs.get(i).kind();
                if (kind != Kind.BULK && kind != Kind.POOLED && kind != Kind.SINK) {
                    held.get(i).forEach((key, count) -> totals.merge(key, (long) count, Long::sum));
                }
            }
            Map<Integer, Integer> slots = new HashMap<>();
            totals.forEach((key, count) -> slots.merge(groupOf(key), need(key, count), Integer::sum));
            return slots;
        }

        /** How many of each odd and end (an item of the root group, not pinned anywhere) storage holds in all. */
        Map<Integer, Long> miscTotals() {
            Set<Integer> pinned = new HashSet<>();
            for (SortSnapshot.Inv inv : invs) {
                if (inv.kind() != Kind.SOURCE && inv.kind() != Kind.SINK && inv.kind() != Kind.POOLED) {
                    pinned.addAll(inv.pins());
                }
            }
            Map<Integer, Long> totals = new HashMap<>();
            for (int i = 0; i < invs.size(); i++) {
                Kind kind = invs.get(i).kind();
                if (kind == Kind.BULK || kind == Kind.POOLED || kind == Kind.SINK) {
                    continue;
                }
                for (Map.Entry<Integer, Integer> entry : held.get(i).entrySet()) {
                    int key = entry.getKey();
                    if (!pinned.contains(key) && !bulkHomes.containsKey(key) && groupOf(key) == SortSnapshot.ROOT) {
                        totals.merge(key, (long) entry.getValue(), Long::sum);
                    }
                }
            }
            return totals;
        }

        /**
         * The group that would fill the most of {@code inv}'s slots once merged; ties go to the group with more slots
         * overall, then more items. Merged counts, so merging stacks never changes what a chest is for.
         */
        int dominantGroup(int inv, Map<Integer, Integer> groupSlots) {
            Map<Integer, Integer> slots = new HashMap<>();
            Map<Integer, Long> items = new HashMap<>();
            held.get(inv).forEach((key, count) -> {
                slots.merge(groupOf(key), need(key, count), Integer::sum);
                items.merge(groupOf(key), (long) count, Long::sum);
            });
            int best = -1;
            for (int g : slots.keySet()) {
                if (best < 0 || compareGroups(g, best, slots, items, groupSlots) < 0) {
                    best = g;
                }
            }
            // the group it was for keeps it while that group is still there and no other fills clearly more
            int claim = invs.get(inv).claim();
            if (claim >= 0 && claim != best && slots.containsKey(claim)
                    && slots.get(best) < slots.get(claim) + settings.tolerance()) {
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

        // -----------------------------------------------------------------------------------------------------------
        // Sinks

        /**
         * Tops up every sink with the items its slots name, up to the room each has: from sources first, then from
         * where the item isn't at home (holding fewest first), and from its homes only when nothing else has it.
         */
        void feedSinks() {
            for (int s = 0; s < invs.size() && !full(); s++) {
                SortSnapshot.Inv sink = invs.get(s);
                if (sink.kind() != Kind.SINK || sink.frozen()) {
                    continue;
                }
                for (int slot = 0; slot < sink.size() && !full(); slot++) {
                    int key = sink.only()[slot];
                    if (key < 0) {
                        continue;
                    }
                    List<Integer> givers = new ArrayList<>(), homes = new ArrayList<>();
                    for (int i = 0; i < invs.size(); i++) {
                        SortSnapshot.Inv inv = invs.get(i);
                        if (i == s || inv.frozen() || !consider(i, key) || amount(i, key) == 0) {
                            continue;
                        }
                        boolean home = inv.pins().contains(key) || bulkHomes.getOrDefault(key, List.of()).contains(i);
                        (home ? homes : givers).add(i);
                    }
                    givers.sort(Comparator.<Integer>comparingInt(i -> invs.get(i).source() ? 0 : 1)
                            .thenComparingLong(i -> amount(i, key)).thenComparingInt(i -> i));
                    givers.addAll(byFewest(homes, key));
                    for (int i : givers) {
                        SortSnapshot.Inv from = invs.get(i);
                        for (int f = from.size() - 1; f >= 0 && !full(); f--) {
                            int room = cap(s, slot, key) - sink.count()[slot];
                            if (room <= 0) {
                                break;
                            }
                            if (from.key()[f] != key || receivedSlots.contains(((long) i << 32) | f)) {
                                continue;
                            }
                            transfer(i, f, s, slot, Math.min(room, from.count()[f]));
                        }
                    }
                }
            }
        }

        // -----------------------------------------------------------------------------------------------------------

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
                if (inv.strict() && !inv.source() && inv.kind() != Kind.SINK) {
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

        /** The most of {@code key} a slot holds: a drawer's slot holds more of what stacks higher. */
        int cap(int inv, int slot, int key) {
            SortSnapshot.Inv in = invs.get(inv);
            int limit = in.limit()[slot];
            if (in.kind() == Kind.BULK || in.kind() == Kind.POOLED) {
                // an empty drawer slot's limit is for a 64-stack item; a filled one's is for what it holds
                if (heldKeys.get(inv)[slot] >= 0) {
                    return Math.max(1, limit);
                }
                return (int) Math.max(1, Math.min(Integer.MAX_VALUE, (long) limit * keys.get(key).maxStack() / 64));
            }
            return Math.max(1, Math.min(limit, keys.get(key).maxStack()));
        }

        /** How many more items {@code inv} takes in all (a crate's cap). */
        long room(int inv) {
            SortSnapshot.Inv in = invs.get(inv);
            return in.capacity() == Integer.MAX_VALUE ? Long.MAX_VALUE : Math.max(0, in.capacity() - in.total());
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
            return (int) Math.min(Integer.MAX_VALUE, (count + stack - 1) / stack);
        }

        /** {@code invs} ordered by how many of {@code key} each held, most first, then by index. */
        List<Integer> byHeld(List<Integer> list, int key) {
            List<Integer> sorted = new ArrayList<>(list);
            sorted.sort(Comparator.<Integer>comparingInt(i -> -held.get(i).getOrDefault(key, 0)).thenComparingInt(i -> i));
            return sorted;
        }

        /** Storage pinned for {@code key} and its drawers, then storage pinned (not strictly) for its family. */
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
            for (int i : bulkHomes.getOrDefault(key, List.of())) {
                if (!pinned.contains(i) && storage.contains(i)) {
                    pinned.add(i);
                }
            }
            int family = keys.get(key).node();
            for (int i : storage) {
                if (pinned.contains(i) || invs.get(i).strict()) {
                    continue;
                }
                for (int pin : invs.get(i).pins()) {
                    if (keys.get(pin).node() == family) {
                        pinned.add(i);
                        break;
                    }
                }
            }
            return pinned;
        }

        /**
         * Where {@code key} best goes in compact mode: chests pinned for it and its drawers, then chests by how many
         * they held of it. With {@code respectPins}, chests pinned for something else come last. Strict chests not
         * pinned for it, and drawers not its own, are left out.
         */
        List<Integer> preference(List<Integer> storage, int key, boolean respectPins) {
            List<Integer> pref = pinnedFor(storage, key);
            List<Integer> rest = new ArrayList<>();
            List<Integer> otherPinned = new ArrayList<>();
            for (int i : storage) {
                Kind kind = invs.get(i).kind();
                if (pref.contains(i) || invs.get(i).refuses(key) || kind == Kind.BULK || kind == Kind.POOLED) {
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
         * the same item if {@code partials}, then into empty slots that take it if {@code empties}. Returns how many
         * moved.
         */
        int place(int src, int srcSlot, int dst, int max, boolean partials, boolean empties) {
            SortSnapshot.Inv from = invs.get(src);
            SortSnapshot.Inv to = invs.get(dst);
            int key = from.key()[srcSlot];
            if (key < 0 || to.source() || to.kind() == Kind.SINK || from.frozen() || to.frozen() || !movable(src)) {
                return 0;
            }
            int left = (int) Math.min(Math.min(max, from.count()[srcSlot]), room(dst));
            int moved = 0;
            for (int pass = 0; pass < 2 && left > 0; pass++) {
                if (pass == 0 && !partials || pass == 1 && !empties) {
                    continue;
                }
                for (int slot = 0; slot < to.size() && left > 0 && !full(); slot++) {
                    if (dst == src && slot == srcSlot) {
                        continue;
                    }
                    boolean match = pass == 0 ? to.key()[slot] == key
                            : to.key()[slot] < 0 && (to.only()[slot] == SortSnapshot.ANY || to.only()[slot] == key);
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

        /** Moves {@code amount} from one slot to another, a stack at most per move (a drawer slot holds far more). */
        void transfer(int src, int srcSlot, int dst, int dstSlot, int amount) {
            SortSnapshot.Inv from = invs.get(src);
            SortSnapshot.Inv to = invs.get(dst);
            int key = from.key()[srcSlot];
            int stack = Math.max(1, keys.get(key).maxStack());
            for (int left = amount; left > 0; left -= stack) {
                moves.add(new Move(src, srcSlot, dst, dstSlot, key, Math.min(stack, left)));
            }
            if (dst != src) {
                received.add(dst);
            }
            receivedSlots.add(((long) dst << 32) | dstSlot);
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
