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
package com.linc.alteredaether.worldgen.density;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EdgeSearchTest {
    @Test
    void straightBorderIsFoundToAboutABlock() {
        // Class 1 east of x = 100.
        for (int x = 60; x < 100; x += 7) {
            double d = EdgeSearch.distance(x, 0, 48, (px, pz) -> px >= 100 ? 1 : 0);
            assertEquals(100 - x, d, 2.5, "x=" + x);
        }
    }

    @Test
    void noBorderInReachGivesTheReach() {
        assertEquals(40, EdgeSearch.distance(0, 0, 40, (px, pz) -> 0));
    }

    @Test
    void circularBorderIsFoundFromInside() {
        // A disc of class 1, radius 50, round the origin; points inside measure out to its rim.
        for (int r = 10; r < 50; r += 10) {
            int at = r;
            double d = EdgeSearch.distance(at, 0, 64, (px, pz) -> px * px + pz * pz <= 2500 ? 1 : 0);
            assertEquals(50 - r, d, 3.0, "r=" + r);
        }
    }
}
