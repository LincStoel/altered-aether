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
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Which families of the tree tidy mode keeps a chest for: as fine-grained as the storage has chests for. Pure.
 *
 * <p>Every item belongs to the root to begin with. The heaviest family is split off into a group of its own while the
 * groups still fit the chests there are, each needing as many chests as its items fill in slots; then its own
 * sub-families get the same chance, and so on down. A family holding less than {@code minFill} stacks' worth of items
 * never gets its own group: it stays with its parent, and a group left with less than that once its sub-families are
 * split off folds back into its parent. What ends up with the root is the miscellaneous items. Then, if chests are left
 * over, the same again with {@code spareFill}, so spare chests take small families rather than stand empty.
 *
 * <p>A family a chest was already kept for needs only half as much to stay a group, so one item more or less doesn't
 * reshuffle the storage. A pinned family is always a group, kept in its pinned chest, needing no chest of its own.
 *
 * <p>Some items are too many to be miscellaneous ({@code many}). A family holding one is given a chest that would
 * otherwise stand empty, however little it fills. If it gets none, those items go with the group nearest their family
 * in the tree instead ({@link Result#nearest}), and so don't keep the odds and ends a chest they won't fill.
 *
 * <p>Each crate is for one thing: the equipment ({@code crateable}) or the odds and ends, never both. The equipment's
 * groups take crates first (those kept for them before, then those for nothing yet, then the odds and ends') until the crates have room for them all,
 * judged by items, as a crate counts them; they then fill those crates biggest first, and a group that doesn't fit
 * needs a chest after all. The crates left over are the odds and ends'. A group in the crates needs no chest
 * ({@link Result#crated}).
 */
public final class Grouping {
    /** Fill is counted in hundredths of a stack. */
    public static final int STACK = 100;

    /**
     * @param group   the group each node's items belong to (a node index; the root for the miscellaneous items)
     * @param own     the slots each group's items fill (0 for a node that isn't a group)
     * @param nearest for each node, the group nearest it in the tree other than the root (the closest relative, then
     *                the group filling fewest slots, then the earliest node), or -1 if there is none; what its items
     *                too many to be miscellaneous go with when its group is the root
     * @param crated  the groups kept in the crates rather than chests of their own
     * @param equipmentCrates which crates (in the order given) are for the equipment; the rest are the odds and ends'
     */
    public record Result(int[] group, int[] own, int[] nearest, boolean[] crated, boolean[] equipmentCrates) {
        public int of(SortSnapshot.Key key) {
            return group[key.node()];
        }

        public boolean crated(int group) {
            return group > 0 && group < crated.length && crated[group];
        }
    }

    /**
     * The crates, in a fixed order.
     *
     * @param slots how many full stacks each holds (what the odds and ends are judged by)
     * @param items how many items each holds (what the equipment is judged by)
     * @param was   what each was for last time: {@link #EQUIPMENT}, {@link #ODDS_AND_ENDS} or 0 for neither
     */
    public record Crates(int[] slots, long[] items, int[] was) {
        public static final int EQUIPMENT = 1, ODDS_AND_ENDS = 2;
    }

    private Grouping() {
    }

    /**
     * @param nodes     the family tree, root first, parents before children
     * @param slots     the slots the items directly in each node fill, merged
     * @param many      the part of {@code slots} filled by items too many to be miscellaneous
     * @param fill      how many stacks' worth (in hundredths, {@link #STACK}) the items directly in each node are
     * @param items     how many items are directly in each node
     * @param crateable the nodes whose groups crates take as well as the odds and ends
     * @param chests    how many chests there are to keep groups in
     * @param chestSize the slots in a chest
     * @param crates    the crates, for the equipment or the odds and ends besides chests
     * @param minFill   the fewest stacks' worth (in hundredths) a family holds to be worth a group
     * @param spareFill the fewest it holds to be worth a chest that would otherwise stay empty
     * @param forced    pinned families: always groups, needing no chest
     * @param claimed   families a chest was kept for last time
     */
    public static Result compute(List<SortSnapshot.Node> nodes, int[] slots, int[] many, int[] fill, int[] items,
                                 boolean[] crateable, int chests, int chestSize, Crates crates, int minFill,
                                 int spareFill, Set<Integer> forced, Set<Integer> claimed) {
        int n = nodes.size();
        int[] weight = fill.clone();
        boolean anyMany = false;
        for (int m : many) {
            anyMany |= m > 0;
        }
        int[] depth = new int[n];
        List<List<Integer>> children = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            children.add(new ArrayList<>());
        }
        for (int i = 1; i < n; i++) {
            int parent = nodes.get(i).parent();
            depth[i] = depth[parent] + 1;
            children.get(parent).add(i);
        }
        for (int i = n - 1; i > 0; i--) {
            weight[nodes.get(i).parent()] += weight[i];
        }
        boolean[] in = new boolean[n];
        in[SortSnapshot.ROOT] = true;
        for (int f : forced) {
            if (f > 0 && f < n) {
                in[f] = true;
            }
        }
        Context ctx = new Context(nodes, slots, many, fill, items, crateable, depth, chestSize, crates, minFill, forced,
                claimed);
        split(ctx, in, weight, children, chests);
        // chests still to spare: rather than leave them empty, the biggest groups left split further, down to
        // families of a couple of stacks (lone one-offs still share the odds and ends). Here, and only here, what is
        // too many to be miscellaneous counts with the group nearest it, so a chest the odds and ends were kept
        // that they won't fill is spare too; counting it so from the start would let the heaviest families split
        // finer before lighter ones were kept apart at all.
        if (spareFill < ctx.minFill || anyMany) {
            int before = ctx.minFill;
            ctx.minFill = Math.max(1, Math.min(spareFill, ctx.minFill));
            ctx.spare = true;
            if (ctx.need(in) <= chests) {
                split(ctx, in, weight, children, chests);
            } else {
                ctx.minFill = before;
                ctx.spare = false;
            }
        }
        // a chest still spare goes to a family holding something too many to be odds and ends, however little it
        // fills, rather than those items going in with another family; the biggest first. Judged as if they stayed
        // with the odds and ends, so only a chest that would otherwise stand empty is taken.
        if (anyMany) {
            boolean spare = ctx.spare;
            ctx.spare = false;
            int[] now = ctx.assign(in, new int[n], new int[n]);
            List<Integer> candidates = new ArrayList<>();
            for (int i = 1; i < n; i++) {
                if (many[i] > 0 && now[i] == SortSnapshot.ROOT) {
                    candidates.add(i);
                }
            }
            candidates.sort((a, b) -> fill[a] != fill[b] ? Integer.compare(fill[b], fill[a]) : Integer.compare(a, b));
            for (int c : candidates) {
                boolean was = in[c];
                in[c] = true;
                ctx.kept.add(c);
                if (ctx.need(in) > chests) {
                    in[c] = was;
                    ctx.kept.remove(c);
                }
            }
            ctx.spare = spare;
        }
        int[] own = new int[n];
        int[] ownItems = new int[n];
        int[] group = ctx.assign(in, own, new int[n], ownItems);
        Context.CrateUse use = ctx.crates(own, ownItems);
        return new Result(group, own, ctx.nearest(group, own), use.crated(), use.equipment());
    }

    /** Splits off the heaviest families first while the groups fit {@code chests}, each holding the context's fill. */
    private static void split(Context ctx, boolean[] in, int[] weight, List<List<Integer>> children, int chests) {
        // heaviest first, then the earlier node
        PriorityQueue<Integer> queue = new PriorityQueue<>((a, b) -> weight[a] != weight[b]
                ? Integer.compare(weight[b], weight[a]) : Integer.compare(a, b));
        queue.addAll(children.get(SortSnapshot.ROOT));
        while (!queue.isEmpty()) {
            int c = queue.poll();
            if (ctx.forced.contains(c)) {
                // pinned: its items live in its pinned chest, so it isn't split further
                continue;
            }
            if (!in[c] && weight[c] >= ctx.threshold(c)) {
                in[c] = true;
                if (ctx.need(in) > chests) {
                    in[c] = false;
                }
            }
            if (weight[c] >= ctx.threshold(c) / 2 && weight[c] > 0) {
                // a sub-family may fit where its parent didn't, by the slots that rounding up saves
                queue.addAll(children.get(c));
            }
        }
    }

    private static final class Context {
        final List<SortSnapshot.Node> nodes;
        final int[] slots, many, fill, items, depth;
        final boolean[] crateable;
        final int chestSize;
        final Crates crates;
        int minFill;
        /** Splitting for spare chests: what is too many to be miscellaneous counts with the group nearest it. */
        boolean spare;
        /** Families given a spare chest for what they hold too many of to be miscellaneous: kept whatever they fill. */
        final Set<Integer> kept = new java.util.HashSet<>();
        final Set<Integer> forced, claimed;
        final Integer[] deepestFirst;

        Context(List<SortSnapshot.Node> nodes, int[] slots, int[] many, int[] fill, int[] items, boolean[] crateable,
                int[] depth, int chestSize, Crates crates, int minFill, Set<Integer> forced, Set<Integer> claimed) {
            this.nodes = nodes;
            this.slots = slots;
            this.many = many;
            this.fill = fill;
            this.items = items;
            this.crateable = crateable;
            this.depth = depth;
            this.chestSize = Math.max(1, chestSize);
            this.crates = crates;
            this.minFill = Math.max(1, minFill);
            this.forced = forced;
            this.claimed = claimed;
            deepestFirst = new Integer[nodes.size()];
            for (int i = 0; i < deepestFirst.length; i++) {
                deepestFirst[i] = i;
            }
            Arrays.sort(deepestFirst, (a, b) -> depth[a] != depth[b] ? Integer.compare(depth[b], depth[a])
                    : Integer.compare(b, a));
        }

        int threshold(int node) {
            return claimed.contains(node) ? Math.max(1, minFill / 2) : minFill;
        }

        /** The chests the groups {@code in} need. */
        int need(boolean[] in) {
            int[] own = new int[nodes.size()];
            int[] ownItems = new int[nodes.size()];
            int[] group = assign(in, own, new int[nodes.size()], ownItems);
            // what is too many to be miscellaneous goes with the group nearest it instead
            int[] nearest = null;
            for (int i = 0; i < own.length && spare; i++) {
                if (group[i] == SortSnapshot.ROOT && many[i] > 0) {
                    if (nearest == null) {
                        nearest = nearest(group, own.clone());
                    }
                    if (nearest[i] > 0) {
                        own[SortSnapshot.ROOT] -= many[i];
                        own[nearest[i]] += many[i];
                    }
                }
            }
            CrateUse use = crates(own, ownItems);
            int need = 0;
            for (int g = 1; g < own.length; g++) {
                if (own[g] > 0 && !forced.contains(g) && !use.crated()[g]) {
                    need += (own[g] + chestSize - 1) / chestSize;
                }
            }
            int misc = Math.max(0, own[SortSnapshot.ROOT] - use.miscSlots());
            return need + (misc + chestSize - 1) / chestSize;
        }

        /**
         * @param crated    the groups in the crates
         * @param equipment which crates are the equipment's
         * @param miscSlots the slots of the crates left to the odds and ends
         */
        record CrateUse(boolean[] crated, boolean[] equipment, int miscSlots) {
        }

        /**
         * Which crates are the equipment's (until they have room for all its groups), the groups they hold (biggest
         * first, each that still fits), and the slots of the crates left to the odds and ends. None are the
         * equipment's if none of its groups fits them.
         */
        CrateUse crates(int[] own, int[] ownItems) {
            int n = own.length;
            int count = crates.slots().length;
            boolean[] crated = new boolean[n];
            boolean[] equipment = new boolean[count];
            List<Integer> candidates = new ArrayList<>();
            long wanted = 0;
            for (int g = 1; g < n; g++) {
                if (own[g] > 0 && crateable[g] && !forced.contains(g)) {
                    candidates.add(g);
                    wanted += ownItems[g];
                }
            }
            if (!candidates.isEmpty() && count > 0) {
                // the equipment's own crates first, then crates for nothing yet, the odds and ends' last
                List<Integer> order = new ArrayList<>();
                for (int was : new int[]{Crates.EQUIPMENT, 0, Crates.ODDS_AND_ENDS}) {
                    for (int c = 0; c < count; c++) {
                        if (crates.was()[c] == was) {
                            order.add(c);
                        }
                    }
                }
                long room = 0;
                for (int c : order) {
                    if (room >= wanted) {
                        break;
                    }
                    equipment[c] = true;
                    room += crates.items()[c];
                }
                candidates.sort((a, b) -> ownItems[a] != ownItems[b] ? Integer.compare(ownItems[b], ownItems[a])
                        : Integer.compare(a, b));
                boolean any = false;
                for (int g : candidates) {
                    if (ownItems[g] <= room) {
                        crated[g] = true;
                        room -= ownItems[g];
                        any = true;
                    }
                }
                if (!any) {
                    // nothing of the equipment fits them: the crates are the odds and ends' after all
                    Arrays.fill(equipment, false);
                }
            }
            int miscSlots = 0;
            for (int c = 0; c < count; c++) {
                if (!equipment[c]) {
                    miscSlots += crates.slots()[c];
                }
            }
            return new CrateUse(crated, equipment, miscSlots);
        }

        int[] assign(boolean[] in, int[] own, int[] ownFill) {
            return assign(in, own, ownFill, new int[nodes.size()]);
        }

        /**
         * Each node's group, with {@code own} filled with each group's slots, {@code ownFill} its fill and
         * {@code ownItems} its items: a group left holding too little folds into the group above it.
         */
        int[] assign(boolean[] in, int[] own, int[] ownFill, int[] ownItems) {
            int n = nodes.size();
            int[] group = new int[n];
            for (int i = 0; i < n; i++) {
                group[i] = in[i] ? i : group[nodes.get(i).parent() < 0 ? 0 : nodes.get(i).parent()];
                own[group[i]] += slots[i];
                ownFill[group[i]] += fill[i];
                ownItems[group[i]] += items[i];
            }
            int[] folded = new int[n];
            Arrays.fill(folded, -1);
            for (int g : deepestFirst) {
                if (g == SortSnapshot.ROOT || !in[g] || forced.contains(g) || kept.contains(g)
                        || ownFill[g] >= threshold(g)) {
                    continue;
                }
                int up = group[nodes.get(g).parent()];
                own[up] += own[g];
                ownFill[up] += ownFill[g];
                ownItems[up] += ownItems[g];
                own[g] = 0;
                ownFill[g] = 0;
                ownItems[g] = 0;
                folded[g] = up;
            }
            for (int i = 0; i < n; i++) {
                int g = group[i];
                while (folded[g] >= 0) {
                    g = folded[g];
                }
                group[i] = g;
            }
            return group;
        }

        /**
         * For each node, the group of {@code group} nearest it other than the root: its closest relative, the one under
         * its nearest ancestor (or itself) that has any group under it; among those, the group filling fewest slots
         * ({@code own}), so a big family isn't the one mixed, then the earliest node; -1 if there is none. Two passes
         * over the tree, up then down.
         */
        int[] nearest(int[] group, int[] own) {
            int n = nodes.size();
            List<Integer> groups = new ArrayList<>();
            boolean[] seen = new boolean[n];
            for (int g : group) {
                if (g != SortSnapshot.ROOT && !seen[g]) {
                    seen[g] = true;
                    groups.add(g);
                }
            }
            groups.sort((a, b) -> own[a] != own[b] ? Integer.compare(own[a], own[b]) : Integer.compare(a, b));
            // the best rank within each node's subtree, up from the leaves (children come after their parents)
            int none = Integer.MAX_VALUE;
            int[] best = new int[n];
            Arrays.fill(best, none);
            for (int rank = 0; rank < groups.size(); rank++) {
                best[groups.get(rank)] = rank;
            }
            for (int i = n - 1; i > 0; i--) {
                int parent = nodes.get(i).parent();
                best[parent] = Math.min(best[parent], best[i]);
            }
            // then down: a node with no group under it takes its parent's
            for (int i = 1; i < n; i++) {
                if (best[i] == none) {
                    best[i] = best[nodes.get(i).parent()];
                }
            }
            int[] out = new int[n];
            for (int i = 0; i < n; i++) {
                out[i] = best[i] == none ? -1 : groups.get(best[i]);
            }
            return out;
        }
    }
}
