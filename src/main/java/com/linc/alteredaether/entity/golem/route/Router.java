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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Finds the way an item flies between two storage blocks: out of an open face, one axis at a time round whatever is
 * in the way, in through an open face. Pure: the world is a {@link CellGrid} of open and closed cells.
 *
 * <p>Like the Aether's Slider, a route sets off along the axis with the most distance to cover, and like a shulker
 * bullet it never doubles straight back along the axis it just came. Routes with few bends are preferred. The usual
 * case, a clear path of at most three legs, is found by {@link #straight} without searching; {@link #search} (A*) is
 * the fallback, with a node budget.
 */
public final class Router {
    /** Cells A* may look at before it gives up. */
    public static final int NODE_BUDGET = 6000;
    /** What a bend costs, in cells: enough that a route bends only to save a real detour. */
    public static final int TURN_COST = 2;

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

        Cell with(int a, int value) {
            return a == 0 ? new Cell(value, y, z) : a == 1 ? new Cell(x, value, z) : new Cell(x, y, value);
        }

        public int manhattan(Cell o) {
            return Math.abs(x - o.x) + Math.abs(y - o.y) + Math.abs(z - o.z);
        }
    }

    /** The box a route must stay in, inclusive. */
    public record Bounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        boolean contains(int x, int y, int z) {
            return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
        }
    }

    private Router() {
    }

    /** The route from any of {@code starts} to any of {@code goals}: straight if one is clear, else searched for. */
    @Nullable
    public static List<Cell> route(CellGrid grid, List<Cell> starts, List<Cell> goals, Bounds bounds) {
        List<Cell> straight = straight(grid, starts, goals, bounds);
        return straight != null ? straight : search(grid, starts, goals, bounds);
    }

    /**
     * The best route of at most three legs, each along one axis, or null if none is clear. Of those as short and as
     * straight as each other, the one setting off along the longest axis wins.
     *
     * @return the route's corners: its start cell, each cell it turns at, and its goal cell
     */
    @Nullable
    public static List<Cell> straight(CellGrid grid, List<Cell> starts, List<Cell> goals, Bounds bounds) {
        List<Cell[]> pairs = new ArrayList<>();
        for (Cell s : starts) {
            for (Cell g : goals) {
                pairs.add(new Cell[]{s, g});
            }
        }
        pairs.sort(Comparator.comparingInt(p -> p[0].manhattan(p[1])));
        List<Cell> best = null;
        int bestCost = Integer.MAX_VALUE;
        for (Cell[] pair : pairs) {
            Cell s = pair[0], g = pair[1];
            int length = s.manhattan(g);
            if (length >= bestCost) {
                break;
            }
            for (int[] order : preferred(s, g)) {
                List<Cell> corners = legs(grid, s, g, order, bounds);
                if (corners == null) {
                    continue;
                }
                int cost = length + TURN_COST * Math.max(0, corners.size() - 2);
                if (cost < bestCost) {
                    bestCost = cost;
                    best = corners;
                }
            }
        }
        return best;
    }

    /** The axis orders to try from {@code s} to {@code g}, longest distance first. */
    private static List<int[]> preferred(Cell s, Cell g) {
        List<int[]> orders = new ArrayList<>(List.of(ORDERS));
        orders.sort(Comparator.<int[]>comparingInt(o -> -Math.abs(g.axis(o[0]) - s.axis(o[0])))
                .thenComparingInt(o -> -Math.abs(g.axis(o[1]) - s.axis(o[1]))));
        return orders;
    }

    /** The corners of the route from {@code s} to {@code g} taking the axes in {@code order}, or null if blocked. */
    @Nullable
    private static List<Cell> legs(CellGrid grid, Cell s, Cell g, int[] order, Bounds bounds) {
        if (!open(grid, bounds, s.x, s.y, s.z)) {
            return null;
        }
        List<Cell> corners = new ArrayList<>();
        corners.add(s);
        Cell at = s;
        for (int axis : order) {
            int to = g.axis(axis);
            int from = at.axis(axis);
            if (to == from) {
                continue;
            }
            int step = to > from ? 1 : -1;
            for (int v = from + step; ; v += step) {
                Cell next = at.with(axis, v);
                if (!open(grid, bounds, next.x, next.y, next.z)) {
                    return null;
                }
                if (v == to) {
                    break;
                }
            }
            at = at.with(axis, to);
            corners.add(at);
        }
        if (corners.size() == 1) {
            corners.add(g);
        }
        return corners;
    }

    private static boolean open(CellGrid grid, Bounds bounds, int x, int y, int z) {
        return bounds.contains(x, y, z) && grid.isOpen(x, y, z);
    }

    private record Node(Cell cell, int dir, int g, int f, int tie, Node parent) {
    }

    /**
     * A* over open cells with six neighbours, a bend penalty and no doubling back, within {@link #NODE_BUDGET} cells;
     * null if no route was found. Returns the route's corners as {@link #straight} does.
     */
    @Nullable
    public static List<Cell> search(CellGrid grid, List<Cell> starts, List<Cell> goals, Bounds bounds) {
        Set<Cell> goalSet = new HashSet<>(goals);
        PriorityQueue<Node> open = new PriorityQueue<>(Comparator.comparingInt(Node::f)
                .thenComparingInt(n -> -n.g).thenComparingInt(Node::tie));
        Map<Long, Integer> best = new HashMap<>();
        for (Cell s : starts) {
            if (open(grid, bounds, s.x, s.y, s.z)) {
                open.add(new Node(s, -1, 0, h(s, goals), 0, null));
            }
        }
        int expanded = 0;
        while (!open.isEmpty() && expanded < NODE_BUDGET) {
            Node node = open.poll();
            if (goalSet.contains(node.cell)) {
                return corners(node);
            }
            long key = key(node.cell, node.dir, bounds);
            Integer seen = best.get(key);
            if (seen != null && seen <= node.g) {
                continue;
            }
            best.put(key, node.g);
            expanded++;
            int dominant = dominantAxis(node.cell, goals);
            for (int d = 0; d < 6; d++) {
                if (node.dir >= 0 && d == (node.dir ^ 1)) {
                    // never straight back the way it came
                    continue;
                }
                Cell next = new Cell(node.cell.x + DIRS[d][0], node.cell.y + DIRS[d][1], node.cell.z + DIRS[d][2]);
                if (!open(grid, bounds, next.x, next.y, next.z)) {
                    continue;
                }
                int g = node.g + 1 + (node.dir >= 0 && node.dir != d ? TURN_COST : 0);
                Integer known = best.get(key(next, d, bounds));
                if (known != null && known <= g) {
                    continue;
                }
                open.add(new Node(next, d, g, g + h(next, goals), d / 2 == dominant ? 0 : 1, node));
            }
        }
        return null;
    }

    private static int h(Cell cell, List<Cell> goals) {
        int best = Integer.MAX_VALUE;
        for (Cell goal : goals) {
            best = Math.min(best, cell.manhattan(goal));
        }
        return best;
    }

    /** The axis with the most distance left to the nearest goal. */
    private static int dominantAxis(Cell cell, List<Cell> goals) {
        Cell goal = goals.get(0);
        for (Cell g : goals) {
            if (cell.manhattan(g) < cell.manhattan(goal)) {
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

    private static long key(Cell cell, int dir, Bounds b) {
        long sx = b.maxX - b.minX + 1, sy = b.maxY - b.minY + 1;
        long index = (cell.x - b.minX) + sx * ((cell.y - b.minY) + sy * (long) (cell.z - b.minZ));
        return index * 7 + (dir + 1);
    }

    private static List<Cell> corners(Node end) {
        List<Node> path = new ArrayList<>();
        for (Node n = end; n != null; n = n.parent) {
            path.add(0, n);
        }
        List<Cell> corners = new ArrayList<>();
        corners.add(path.get(0).cell);
        for (int i = 1; i < path.size() - 1; i++) {
            if (path.get(i).dir != path.get(i + 1).dir) {
                corners.add(path.get(i).cell);
            }
        }
        if (path.size() > 1) {
            corners.add(path.get(path.size() - 1).cell);
        } else {
            corners.add(path.get(0).cell);
        }
        return corners;
    }
}
