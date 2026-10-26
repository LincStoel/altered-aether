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
package com.linc.alteredaether.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A dive leaves at the whale's own angle and bends smoothly down to exactly its depth, ending nose down. */
class AerwhaleDiveTest {
    @Test
    void chargeSetsTheDepth() {
        assertEquals(3, AerwhaleDive.depthFor(1), 0.2);
        assertEquals(3, AerwhaleDive.depthFor(0), 1.0E-9);
        assertEquals(15, AerwhaleDive.depthFor(90), 1.0E-9);
        assertEquals(15, AerwhaleDive.depthFor(100), 1.0E-9);
    }

    @Test
    void leavesAtItsAngleAndBendsDownToItsDepth() {
        for (double depth : new double[]{3, 9, 15}) {
            for (float pitch : new float[]{-60, -45, -20, 0, 20, 45, 60}) {
                AerwhaleDive dive = AerwhaleDive.of(depth, pitch);
                String which = depth + " deep from " + pitch;
                assertEquals(0, dive.height(0), 1.0E-9, "starts where the whale is: " + which);
                assertEquals(pitch, dive.pitch(0), 1.0E-3, "starts at the whale's own angle: " + which);
                assertEquals(-depth, dive.height(dive.length()), 1.0E-9, "ends depth below: " + which);
                assertTrue(dive.k() >= 0, "only ever bends downward: " + which);
                assertTrue(dive.pitch(dive.length()) < 0 && dive.pitch(dive.length()) >= -66,
                        "ends nose down, not much past a ridden whale's pitch: " + dive.pitch(dive.length()) + ", " + which);
                assertTrue(dive.length() > 0 && dive.length() <= 4 * depth + 1.0E-9, "a sensible length: " + which);
                assertTrue(!dive.done(dive.length() - 0.01) && dive.done(dive.length()));
            }
        }
    }

    @Test
    void levelDivesNeverRise() {
        AerwhaleDive dive = AerwhaleDive.of(15, 0);
        for (double s = 0.5; s <= dive.length(); s += 0.5) {
            assertTrue(dive.height(s) < 0, "should only go down, at " + s);
        }
    }
}
