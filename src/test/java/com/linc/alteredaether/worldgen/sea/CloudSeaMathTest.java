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
package com.linc.alteredaether.worldgen.sea;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The cloud sea's sheet: its lens, holes, drift and how it breaks up at the sea's edges. */
class CloudSeaMathTest {
    @Test
    void aRimIsOneBlockAndAFullLensIsTwenty() {
        int[] out = new int[2];
        // n = 0.1 + 1.5p is 0 exactly at the rim.
        assertTrue(CloudSeaMath.span(-0.1 / 1.5, 0, 1, out));
        assertEquals(out[0], out[1]);
        assertTrue(CloudSeaMath.span(1, 0, 1, out));
        assertEquals(20, out[1] - out[0] + 1);
    }

    @Test
    void holesAreHoles() {
        assertFalse(CloudSeaMath.span(-0.5, 0, 1, new int[2]));
    }

    @Test
    void theSheetStaysInItsBand() {
        int[] out = new int[2];
        for (double q = -3; q <= 3; q += 0.01) {
            for (double p = -1; p <= 2; p += 0.05) {
                if (CloudSeaMath.span(p, q, 1, out)) {
                    assertTrue(out[0] >= CloudSeaMath.BOTTOM && out[1] <= CloudSeaMath.TOP, q + " " + p);
                }
            }
            double lift = CloudSeaMath.lift(q);
            assertTrue(lift >= 0 && lift <= CloudSeaMath.DRIFT);
        }
    }

    @Test
    void theSeasEdgeBreaksItUp() {
        int[] out = new int[2];
        assertFalse(CloudSeaMath.span(2, 0, 0, out));
        assertFalse(CloudSeaMath.span(2, 0, CloudSeaMath.MIN_WEIGHT / 2, out));
        // Thinner, then gone, as the weight falls under the same noise.
        assertTrue(CloudSeaMath.thickness(0.5, 1) > CloudSeaMath.thickness(0.5, 0.7));
        assertTrue(CloudSeaMath.thickness(0.5, 0.2) < 0);
    }

    @Test
    void cosineEaseEnds() {
        assertEquals(0, CloudSeaMath.cosineEase(0), 1e-12);
        assertEquals(1, CloudSeaMath.cosineEase(1), 1e-12);
        assertEquals(0.5, CloudSeaMath.cosineEase(0.5), 1e-12);
        assertEquals(0, CloudSeaMath.cosineEase(2), 1e-12);
    }
}
