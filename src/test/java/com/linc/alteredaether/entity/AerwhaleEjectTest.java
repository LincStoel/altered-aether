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

/** A leap throws the rider as high as it's charged, well within the time the whale coasts beneath them. */
class AerwhaleEjectTest {
    private static final double GRAVITY = 0.08;

    @Test
    void chargeSetsTheHeight() {
        assertEquals(AerwhaleEject.MIN_HEIGHT, AerwhaleEject.heightFor(0), 1.0E-9);
        assertEquals(AerwhaleEject.MAX_HEIGHT, AerwhaleEject.heightFor(1), 1.0E-9);
        assertEquals(AerwhaleEject.MAX_HEIGHT, AerwhaleEject.heightFor(2), 1.0E-9);
    }

    @Test
    void launchReachesItsHeight() {
        for (double height : new double[]{AerwhaleEject.MIN_HEIGHT, 5, AerwhaleEject.MAX_HEIGHT}) {
            double speed = AerwhaleEject.launchSpeed(height, GRAVITY);
            assertEquals(height, AerwhaleEject.apex(speed, GRAVITY), 0.1, "apex for " + height);
        }
    }

    @Test
    void whaleCoastsUntilTheRiderIsDown() {
        double speed = AerwhaleEject.launchSpeed(AerwhaleEject.MAX_HEIGHT, GRAVITY);
        int airtime = AerwhaleEject.airtime(speed, GRAVITY);
        assertTrue(airtime < AerwhaleEject.COAST_TICKS, "in the air " + airtime + " ticks");
    }
}
