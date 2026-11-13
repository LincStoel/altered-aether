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

import com.linc.alteredaether.entity.golem.route.FlightPath;
import com.linc.alteredaether.entity.golem.route.Router;
import com.linc.alteredaether.entity.golem.route.Router.Cell;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An item's route: straight through open air, setting off along the longest axis, round a wall one axis at a time,
 * none when walled in, never doubling back; and its flight lands where the route ends, sooner for shorter routes.
 */
class RouterTest {
    private static final Router.Bounds BOUNDS = new Router.Bounds(-20, -5, -20, 20, 10, 20);

    private static Router.CellGrid open(Set<Cell> walls) {
        return (x, y, z) -> !walls.contains(new Cell(x, y, z));
    }

    /** Every leg runs along one axis. */
    private static void assertAxisAligned(List<Cell> corners) {
        for (int i = 1; i < corners.size(); i++) {
            Cell a = corners.get(i - 1), b = corners.get(i);
            int axes = (a.x() != b.x() ? 1 : 0) + (a.y() != b.y() ? 1 : 0) + (a.z() != b.z() ? 1 : 0);
            assertTrue(axes <= 1, "a leg isn't along one axis: " + a + " to " + b);
        }
    }

    private static List<Cell> cells(List<Cell> corners) {
        List<Cell> cells = new ArrayList<>();
        cells.add(corners.get(0));
        for (int i = 1; i < corners.size(); i++) {
            Cell a = corners.get(i - 1), b = corners.get(i);
            int dx = Integer.signum(b.x() - a.x()), dy = Integer.signum(b.y() - a.y()), dz = Integer.signum(b.z() - a.z());
            Cell at = a;
            while (!at.equals(b)) {
                at = new Cell(at.x() + dx, at.y() + dy, at.z() + dz);
                cells.add(at);
            }
        }
        return cells;
    }

    private static int axis(Cell a, Cell b) {
        return a.x() != b.x() ? 0 : a.y() != b.y() ? 1 : 2;
    }

    @Test
    void openAirIsAStraightRouteAlongTheLongestAxisFirst() {
        List<Cell> route = Router.route(open(Set.of()), List.of(new Cell(0, 1, 0)), List.of(new Cell(9, 2, 3)), BOUNDS);
        assertNotNull(route);
        assertAxisAligned(route);
        assertTrue(route.size() <= 4, "more than three legs: " + route);
        assertEquals(new Cell(9, 1, 0), route.get(1), "didn't set off along x, the longest: " + route);
    }

    @Test
    void aWallIsGoneRound() {
        Set<Cell> walls = new HashSet<>();
        for (int y = -5; y <= 3; y++) {
            for (int z = -20; z <= 20; z++) {
                walls.add(new Cell(4, y, z));
            }
        }
        List<Cell> route = Router.route(open(walls), List.of(new Cell(0, 0, 0)), List.of(new Cell(8, 0, 0)), BOUNDS);
        assertNotNull(route, "no way over the wall");
        assertAxisAligned(route);
        for (Cell cell : cells(route)) {
            assertTrue(!walls.contains(cell), "went through the wall at " + cell);
        }
        assertEquals(new Cell(8, 0, 0), route.get(route.size() - 1));
    }

    @Test
    void aMazeIsSearchedAndNeverDoublesBack() {
        Set<Cell> walls = new HashSet<>();
        // a wall with one gap, beside the straight line, so no three-leg route gets through
        for (int y = -5; y <= 10; y++) {
            for (int z = -20; z <= 20; z++) {
                if (!(y == 0 && z == 6)) {
                    walls.add(new Cell(4, y, z));
                }
            }
        }
        List<Cell> route = Router.route(open(walls), List.of(new Cell(0, 0, -2)), List.of(new Cell(8, 0, -3)), BOUNDS);
        assertNotNull(route, "no route through the gap");
        assertAxisAligned(route);
        List<Cell> cells = cells(route);
        assertTrue(cells.contains(new Cell(4, 0, 6)), "didn't go through the gap");
        for (int i = 2; i < route.size(); i++) {
            Cell a = route.get(i - 2), b = route.get(i - 1), c = route.get(i);
            assertTrue(axis(a, b) != axis(b, c), "doubled back along an axis at " + b + ": " + route);
        }
    }

    @Test
    void aSealedGoalHasNoRoute() {
        Set<Cell> walls = new HashSet<>();
        Cell goal = new Cell(6, 0, 0);
        for (int[] d : new int[][]{{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}}) {
            walls.add(new Cell(goal.x() + d[0], goal.y() + d[1], goal.z() + d[2]));
        }
        assertNull(Router.route(open(walls), List.of(new Cell(0, 0, 0)), List.of(goal), BOUNDS));
    }

    @Test
    void aFlightLandsWhereItsRouteEndsAndLongerTakesLonger() {
        List<double[]> shortRoute = List.of(new double[]{0.5, 1, 0.5}, new double[]{0.5, 1, 3.5});
        List<double[]> longRoute = List.of(new double[]{0.5, 1, 0.5}, new double[]{0.5, 1, 9.5}, new double[]{8.5, 1, 9.5},
                new double[]{8.5, 3, 9.5});
        double[][] table = FlightPath.table(longRoute);
        double[] end = table[table.length - 1];
        assertEquals(8.5, end[0], 1.0E-9);
        assertEquals(3, end[1], 1.0E-9);
        assertEquals(9.5, end[2], 1.0E-9);
        assertTrue(FlightPath.ticks(longRoute, 1) > FlightPath.ticks(shortRoute, 1), "the long flight isn't longer");
        assertEquals(30, FlightPath.ticks(shortRoute, 30), "the least flight time wasn't kept");
        // it never jumps: no tick moves it further than its top speed
        for (int i = 1; i < table.length; i++) {
            double dx = table[i][0] - table[i - 1][0], dy = table[i][1] - table[i - 1][1], dz = table[i][2] - table[i - 1][2];
            assertTrue(Math.sqrt(dx * dx + dy * dy + dz * dz) <= 0.6, "jumped at tick " + i);
        }
        double[] stretched = FlightPath.at(table, 1000, 1000);
        assertEquals(8.5, stretched[0], 1.0E-9);
    }
}
