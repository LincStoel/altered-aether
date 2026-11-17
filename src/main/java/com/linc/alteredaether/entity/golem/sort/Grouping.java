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
 * <p>Every item belongs to the root to begin with. The heaviest family (by the slots its items fill once merged) is
 * split off into a group of its own while the groups still fit the chests there are, each needing as many chests as its
 * items fill; then its own sub-families get the same chance, and so on down. A family filling less than
 * {@code minFill} slots never gets its own group: it stays with its parent, and a group left with less than that once
 * its sub-families are split off folds back into its parent. What ends up with the root is the miscellaneous items.
 *
 * <p>A family a chest was already kept for needs only half as much to stay a group, so one item more or less doesn't
 * reshuffle the storage. A pinned family is always a group, kept in its pinned chest, needing no chest of its own.
 */
public final class Grouping {
    /**
     * @param group the group each node's items belong to (a node index; the root for the miscellaneous items)
     * @param own   the slots each group's items fill (0 for a node that isn't a group)
     */
    public record Result(int[] group, int[] own) {
        public int of(SortSnapshot.Key key) {
            return group[key.node()];
        }
    }

    private Grouping() {
    }

    /**
     * @param nodes      the family tree, root first, parents before children
     * @param direct     the slots the items directly in each node fill
     * @param chests     how many chests there are to keep groups in
     * @param chestSize  the slots in a chest
     * @param miscSlots  the slots kept for the miscellaneous items besides chests (crates)
     * @param minFill    the fewest slots a family fills to be worth a group
     * @param forced     pinned families: always groups, needing no chest
     * @param claimed    families a chest was kept for last time
     */
    public static Result compute(List<SortSnapshot.Node> nodes, int[] direct, int chests, int chestSize, int miscSlots,
                                 int minFill, Set<Integer> forced, Set<Integer> claimed) {
        int n = nodes.size();
        int[] weight = direct.clone();
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
        Context ctx = new Context(nodes, direct, depth, chestSize, miscSlots, minFill, forced, claimed);

        // heaviest first, then the earlier node
        PriorityQueue<Integer> queue = new PriorityQueue<>((a, b) -> weight[a] != weight[b]
                ? Integer.compare(weight[b], weight[a]) : Integer.compare(a, b));
        queue.addAll(children.get(SortSnapshot.ROOT));
        int size = Math.max(1, chestSize);
        while (!queue.isEmpty()) {
            int c = queue.poll();
            if (in[c]) {
                // pinned: its items live in its pinned chest, so it isn't split further
                continue;
            }
            if (weight[c] >= ctx.threshold(c)) {
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
        int[] own = new int[n];
        int[] group = ctx.assign(in, own);
        return new Result(group, own);
    }

    private static final class Context {
        final List<SortSnapshot.Node> nodes;
        final int[] direct, depth;
        final int chestSize, miscSlots, minFill;
        final Set<Integer> forced, claimed;
        final Integer[] deepestFirst;

        Context(List<SortSnapshot.Node> nodes, int[] direct, int[] depth, int chestSize, int miscSlots, int minFill,
                Set<Integer> forced, Set<Integer> claimed) {
            this.nodes = nodes;
            this.direct = direct;
            this.depth = depth;
            this.chestSize = Math.max(1, chestSize);
            this.miscSlots = miscSlots;
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
            assign(in, own);
            int need = 0;
            for (int g = 1; g < own.length; g++) {
                if (own[g] > 0 && !forced.contains(g)) {
                    need += (own[g] + chestSize - 1) / chestSize;
                }
            }
            int misc = Math.max(0, own[SortSnapshot.ROOT] - miscSlots);
            return need + (misc + chestSize - 1) / chestSize;
        }

        /**
         * Each node's group, with {@code own} filled with each group's slots: a group left too small folds into the
         * group above it.
         */
        int[] assign(boolean[] in, int[] own) {
            int n = nodes.size();
            int[] group = new int[n];
            for (int i = 0; i < n; i++) {
                group[i] = in[i] ? i : group[nodes.get(i).parent() < 0 ? 0 : nodes.get(i).parent()];
                own[group[i]] += direct[i];
            }
            int[] folded = new int[n];
            Arrays.fill(folded, -1);
            for (int g : deepestFirst) {
                if (g == SortSnapshot.ROOT || !in[g] || forced.contains(g) || own[g] >= threshold(g)) {
                    continue;
                }
                int up = group[nodes.get(g).parent()];
                own[up] += own[g];
                own[g] = 0;
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
    }
}
