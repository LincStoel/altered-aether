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
package com.linc.alteredaether.entity.golem.route;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Finds the way an item flies between two storage blocks: out of an open face, one axis at a time round whatever is
 * in the way, in through an open face. Pure: the world is a {@link CellGrid} of open and closed cells.
 *
 * <p>Like the Aether's Slider, a route sets off along the axis with the most distance to cover, and like a shulker
 * bullet it never doubles straight back along the axis it just came. Routes with few bends are preferred. The usual
 * case, a clear path of at most three legs, is found by {@link #straight} without searching; {@link #search} (A*) is
 * the fallback, with a node budget. {@link #flood} tells whether cells are shut off from others.
 */
public final class Router {
    /** Cells A* may look at before it gives up, counting a cell once for each axis it is reached along. */
    public static final int NODE_BUDGET = 12000;
    /** What a bend costs, in cells: enough that a route bends only to save a real detour. */
    public static final int TURN_COST = 2;
    /**
     * Cells flooded round the goals before A* sets off: goals shut in a pocket smaller than this, with no start in it,
     * have no way in, which the flood finds far sooner than a search that spends its whole budget outside.
     */
    public static final int SEAL_CHECK = 512;

    private static final int[][] DIRS = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
    private static final int[][] ORDERS = {{0, 1, 2}, {0, 2, 1}, {1, 0, 2}, {1, 2, 0}, {2, 0, 1}, {2, 1, 0}};

    /** Which cells an item can pass through. */
    @FunctionalInterface
    public interface CellGrid {
        boolean isOpen(int x, int y, int z);
    }

    public record Cell(int x, int y, int z) {
        int axis(int a) {
            return a == 0 ? x : a == 1 ? y : z;
        }

        public int manhattan(Cell o) {
            return Math.abs(x - o.x) + Math.abs(y - o.y) + Math.abs(z - o.z);
        }
    }

    /** The box a route must stay in, inclusive. */
    public record Bounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        public boolean contains(int x, int y, int z) {
            return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
        }

        /** A cell's place in the box, for marking cells off; the cell must be in the box. */
        long index(int x, int y, int z) {
            return (x - minX) + (maxX - minX + 1L) * ((y - minY) + (maxY - minY + 1L) * (z - minZ));
        }
    }

    /**
     * What a search came to: the route's corners, or null with {@code exhausted} telling whether it gave up at its
     * budget (there may be a way it didn't find) or ran out of cells (there is none).
     */
    public record Search(@Nullable List<Cell> corners, boolean exhausted) {
        static final Search NONE = new Search(null, false);
    }

    /** What a flood came to: it reached a target, it closed off without reaching one, or it grew past its cap. */
    public enum Reach { REACHES, SEALED, OPEN_ENDED }

    /** The cells a flood got to, and what it came to. */
    public static final class Pocket {
        private final Reach reach;
        private final Bounds bounds;
        private final LongSet cells;

        Pocket(Reach reach, Bounds bounds, LongSet cells) {
            this.reach = reach;
            this.bounds = bounds;
            this.cells = cells;
        }

        public Reach reach() {
            return reach;
        }

        public boolean contains(Cell cell) {
            return bounds.contains(cell.x, cell.y, cell.z) && cells.contains(bounds.index(cell.x, cell.y, cell.z));
        }
    }

    private Router() {
    }

    /** The route from any of {@code starts} to any of {@code goals}: straight if one is clear, else searched for. */
    @Nullable
    public static List<Cell> route(CellGrid grid, List<Cell> starts, List<Cell> goals, Bounds bounds) {
        List<Cell> straight = straight(grid, starts, goals, bounds);
        return straight != null ? straight : search(grid, starts, goals, bounds).corners();
    }

    /**
     * The best route of at most three legs, each along one axis, or null if none is clear. Of those as short and as
     * straight as each other, the one setting off along the longest axis wins.
     *
     * @return the route's corners: its start cell, each cell it turns at, and its goal cell
     */
    @Nullable
    public static List<Cell> straight(CellGrid grid, List<Cell> starts, List<Cell> goals, Bounds bounds) {
        List<Cell[]> pairs = new ArrayList<>(starts.size() * goals.size());
        for (Cell s : starts) {
            for (Cell g : goals) {
                pairs.add(new Cell[]{s, g});
            }
        }
        // every clear route of a pair costs the same, its length and a bend per axis after the first, so the first pair
        // with a clear route, cheapest first, has the best one
        pairs.sort(Comparator.<Cell[]>comparingInt(p -> legCost(p[0], p[1])).thenComparingInt(p -> p[0].manhattan(p[1])));
        int[] tried = new int[ORDERS.length];
        for (Cell[] pair : pairs) {
            Cell s = pair[0], g = pair[1];
            if (!open(grid, bounds, s.x, s.y, s.z)) {
                continue;
            }
            int triedCount = 0;
            orders:
            for (int[] order : preferred(s, g)) {
                // orders that differ only in axes with no distance to cover walk the same cells
                int sequence = sequence(s, g, order);
                for (int i = 0; i < triedCount; i++) {
                    if (tried[i] == sequence) {
                        continue orders;
                    }
                }
                tried[triedCount++] = sequence;
                List<Cell> corners = legs(grid, s, g, order, bounds);
                if (corners != null) {
                    return corners;
                }
            }
        }
        return null;
    }

    /** What a clear route of at most three legs from {@code s} to {@code g} costs. */
    private static int legCost(Cell s, Cell g) {
        int axes = (s.x != g.x ? 1 : 0) + (s.y != g.y ? 1 : 0) + (s.z != g.z ? 1 : 0);
        return s.manhattan(g) + TURN_COST * Math.max(0, axes - 1);
    }

    /** The axes {@code order} moves along from {@code s} to {@code g}, skipping those already level, as a number. */
    private static int sequence(Cell s, Cell g, int[] order) {
        int sequence = 0;
        for (int axis : order) {
            if (s.axis(axis) != g.axis(axis)) {
                sequence = sequence * 4 + axis + 1;
            }
        }
        return sequence;
    }

    /** The axis orders to try from {@code s} to {@code g}, longest distance first. */
    private static List<int[]> preferred(Cell s, Cell g) {
        List<int[]> orders = new ArrayList<>(List.of(ORDERS));
        orders.sort(Comparator.<int[]>comparingInt(o -> -Math.abs(g.axis(o[0]) - s.axis(o[0])))
                .thenComparingInt(o -> -Math.abs(g.axis(o[1]) - s.axis(o[1]))));
        return orders;
    }

    /**
     * The corners of the route from {@code s} (already known open) to {@code g} taking the axes in {@code order}, or
     * null if blocked.
     */
    @Nullable
    private static List<Cell> legs(CellGrid grid, Cell s, Cell g, int[] order, Bounds bounds) {
        List<Cell> corners = new ArrayList<>(4);
        corners.add(s);
        int x = s.x, y = s.y, z = s.z;
        for (int axis : order) {
            int to = g.axis(axis);
            int from = axis == 0 ? x : axis == 1 ? y : z;
            if (to == from) {
                continue;
            }
            int step = to > from ? 1 : -1;
            for (int v = from + step; ; v += step) {
                if (!open(grid, bounds, axis == 0 ? v : x, axis == 1 ? v : y, axis == 2 ? v : z)) {
                    return null;
                }
                if (v == to) {
                    break;
                }
            }
            if (axis == 0) {
                x = to;
            } else if (axis == 1) {
                y = to;
            } else {
                z = to;
            }
            corners.add(new Cell(x, y, z));
        }
        if (corners.size() == 1) {
            corners.add(g);
        }
        return corners;
    }

    private static boolean open(CellGrid grid, Bounds bounds, int x, int y, int z) {
        return bounds.contains(x, y, z) && grid.isOpen(x, y, z);
    }

    private static List<Cell> openOnly(CellGrid grid, Bounds bounds, List<Cell> cells) {
        List<Cell> open = new ArrayList<>(cells.size());
        for (Cell cell : cells) {
            if (open(grid, bounds, cell.x, cell.y, cell.z)) {
                open.add(cell);
            }
        }
        return open;
    }

    private record Node(Cell cell, int dir, int g, int f, int tie, @Nullable Node parent) {
    }

    /**
     * A* over open cells with six neighbours, a bend penalty and no doubling back, within {@link #NODE_BUDGET} cells.
     * A cell is looked at once for each axis it is reached along, not each direction (reaching it again from the other
     * end of an axis seldom leads anywhere better, and this way the budget goes three times as far), and the estimate
     * counts the bends the route still has to make, so the search keeps close to the way it will go. Goals shut in a small pocket that no start shares are found at
     * once by a flood ({@link #SEAL_CHECK}). Returns the route's corners as {@link #straight} does.
     */
    public static Search search(CellGrid grid, List<Cell> starts, List<Cell> goals, Bounds bounds) {
        List<Cell> openGoals = openOnly(grid, bounds, goals);
        List<Cell> openStarts = openOnly(grid, bounds, starts);
        if (openGoals.isEmpty() || openStarts.isEmpty()) {
            return Search.NONE;
        }
        if (flood(grid, openGoals, openStarts, bounds, SEAL_CHECK).reach() == Reach.SEALED) {
            return Search.NONE;
        }
        Set<Cell> goalSet = new HashSet<>(openGoals);
        PriorityQueue<Node> queue = new PriorityQueue<>(Comparator.comparingInt(Node::f)
                .thenComparingInt(n -> -n.g).thenComparingInt(Node::tie));
        LongSet closed = new LongSet();
        for (Cell s : openStarts) {
            queue.add(new Node(s, -1, 0, h(s.x, s.y, s.z, -1, openGoals), 0, null));
        }
        int expanded = 0;
        while (!queue.isEmpty()) {
            Node node = queue.poll();
            Cell cell = node.cell;
            long state = state(bounds, cell.x, cell.y, cell.z, node.dir);
            if (closed.contains(state)) {
                continue;
            }
            if (goalSet.contains(cell)) {
                return new Search(corners(node), false);
            }
            if (expanded++ >= NODE_BUDGET) {
                return new Search(null, true);
            }
            closed.add(state);
            int dominant = dominantAxis(cell, openGoals);
            for (int d = 0; d < 6; d++) {
                if (node.dir >= 0 && d == (node.dir ^ 1)) {
                    // never straight back the way it came
                    continue;
                }
                int x = cell.x + DIRS[d][0], y = cell.y + DIRS[d][1], z = cell.z + DIRS[d][2];
                if (!bounds.contains(x, y, z) || closed.contains(state(bounds, x, y, z, d)) || !grid.isOpen(x, y, z)) {
                    continue;
                }
                int g = node.g + 1 + (node.dir >= 0 && node.dir != d ? TURN_COST : 0);
                queue.add(new Node(new Cell(x, y, z), d, g, g + h(x, y, z, d, openGoals), d / 2 == dominant ? 0 : 1, node));
            }
        }
        return Search.NONE;
    }

    /** A cell reached heading {@code dir} (-1 for a start), as the search marks it looked at: by cell and axis. */
    private static long state(Bounds bounds, int x, int y, int z, int dir) {
        return bounds.index(x, y, z) * 4 + (dir < 0 ? 3 : dir / 2);
    }

    /**
     * The least a route from the cell at x, y, z heading {@code dir} (-1 for none yet) can cost to reach the nearest
     * goal: its distance, and a bend for each axis it still has to cover after the first, plus one more if it can't
     * carry straight on to cover the first.
     */
    private static int h(int x, int y, int z, int dir, List<Cell> goals) {
        int best = Integer.MAX_VALUE;
        for (Cell goal : goals) {
            int dx = goal.x - x, dy = goal.y - y, dz = goal.z - z;
            int axes = (dx != 0 ? 1 : 0) + (dy != 0 ? 1 : 0) + (dz != 0 ? 1 : 0);
            int bends = 0;
            if (axes > 0) {
                bends = axes - 1;
                if (dir >= 0) {
                    int along = dir / 2 == 0 ? dx : dir / 2 == 1 ? dy : dz;
                    boolean onward = (dir & 1) == 0 ? along > 0 : along < 0;
                    if (!onward) {
                        bends++;
                    }
                }
            }
            best = Math.min(best, Math.abs(dx) + Math.abs(dy) + Math.abs(dz) + TURN_COST * bends);
        }
        return best;
    }

    /** The axis with the most distance left to the nearest goal. */
    private static int dominantAxis(Cell cell, List<Cell> goals) {
        Cell goal = goals.get(0);
        int nearest = cell.manhattan(goal);
        for (Cell g : goals) {
            int d = cell.manhattan(g);
            if (d < nearest) {
                nearest = d;
                goal = g;
            }
        }
        int best = 0;
        for (int a = 1; a < 3; a++) {
            if (Math.abs(goal.axis(a) - cell.axis(a)) > Math.abs(goal.axis(best) - cell.axis(best))) {
                best = a;
            }
        }
        return best;
    }

    private static List<Cell> corners(Node end) {
        List<Node> path = new ArrayList<>();
        for (Node n = end; n != null; n = n.parent) {
            path.add(n);
        }
        Collections.reverse(path);
        List<Cell> corners = new ArrayList<>();
        corners.add(path.get(0).cell);
        for (int i = 1; i < path.size() - 1; i++) {
            if (path.get(i).dir != path.get(i + 1).dir) {
                corners.add(path.get(i).cell);
            }
        }
        corners.add(path.get(path.size() - 1).cell);
        return corners;
    }

    /**
     * Floods open cells from {@code from} within {@code bounds}, until it reaches any of {@code to}, runs out of cells
     * ({@link Reach#SEALED}: {@code from} is shut off from {@code to}), or has taken in {@code cap} cells.
     */
    public static Pocket flood(CellGrid grid, Collection<Cell> from, Collection<Cell> to, Bounds bounds, int cap) {
        LongSet seen = new LongSet();
        LongSet targets = new LongSet();
        for (Cell cell : to) {
            if (bounds.contains(cell.x, cell.y, cell.z)) {
                targets.add(bounds.index(cell.x, cell.y, cell.z));
            }
        }
        ArrayDeque<Cell> queue = new ArrayDeque<>();
        for (Cell cell : from) {
            if (!open(grid, bounds, cell.x, cell.y, cell.z)) {
                continue;
            }
            long index = bounds.index(cell.x, cell.y, cell.z);
            if (!seen.add(index)) {
                continue;
            }
            if (targets.contains(index)) {
                return new Pocket(Reach.REACHES, bounds, seen);
            }
            queue.add(cell);
        }
        int taken = queue.size();
        while (!queue.isEmpty()) {
            if (taken >= cap) {
                return new Pocket(Reach.OPEN_ENDED, bounds, seen);
            }
            Cell cell = queue.poll();
            for (int[] d : DIRS) {
                int x = cell.x + d[0], y = cell.y + d[1], z = cell.z + d[2];
                if (!bounds.contains(x, y, z)) {
                    continue;
                }
                long index = bounds.index(x, y, z);
                if (seen.contains(index) || !grid.isOpen(x, y, z)) {
                    continue;
                }
                seen.add(index);
                if (targets.contains(index)) {
                    return new Pocket(Reach.REACHES, bounds, seen);
                }
                queue.add(new Cell(x, y, z));
                taken++;
            }
        }
        return new Pocket(Reach.SEALED, bounds, seen);
    }

    /** A set of non-negative longs, open addressed: cell indices, without boxing them. */
    static final class LongSet {
        private long[] table = new long[1024];
        private int size;

        /** Adds {@code key}; false if it was there already. */
        boolean add(long key) {
            if (size * 2 >= table.length) {
                long[] old = table;
                table = new long[old.length * 2];
                for (long stored : old) {
                    if (stored != 0) {
                        insert(table, stored);
                    }
                }
            }
            if (!insert(table, key + 1)) {
                return false;
            }
            size++;
            return true;
        }

        boolean contains(long key) {
            long stored = key + 1;
            int mask = table.length - 1;
            for (int i = slot(stored, mask); table[i] != 0; i = (i + 1) & mask) {
                if (table[i] == stored) {
                    return true;
                }
            }
            return false;
        }

        /** Puts {@code stored} (a key plus one, so 0 marks an empty slot) into {@code into}; false if it was there. */
        private static boolean insert(long[] into, long stored) {
            int mask = into.length - 1;
            int i = slot(stored, mask);
            while (into[i] != 0) {
                if (into[i] == stored) {
                    return false;
                }
                i = (i + 1) & mask;
            }
            into[i] = stored;
            return true;
        }

        private static int slot(long stored, int mask) {
            long h = stored * 0x9E3779B97F4A7C15L;
            return (int) (h ^ (h >>> 32)) & mask;
        }
    }
}
