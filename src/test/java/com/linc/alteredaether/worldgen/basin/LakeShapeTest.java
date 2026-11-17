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
package com.linc.alteredaether.worldgen.basin;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A big lake's outline: wet in the middle, dry by its radius, the same for the same seed. */
class LakeShapeTest {
    private static final int SEEDS = 200;

    @Test
    void theMiddleIsAlwaysWet() {
        for (long seed = 0; seed < SEEDS; seed++) {
            assertTrue(new LakeShape(seed, 110, 40).wet(0, 0), "seed " + seed);
        }
    }

    @Test
    void theWaterNeverReachesTheRadius() {
        for (long seed = 0; seed < SEEDS; seed++) {
            LakeShape shape = new LakeShape(seed, 110, 40);
            for (int i = 0; i < 64; i++) {
                double angle = i * Math.PI / 32;
                for (int r = 110; r <= 140; r += 5) {
                    assertFalse(shape.wet((int) Math.round(Math.cos(angle) * r), (int) Math.round(Math.sin(angle) * r)),
                            "seed " + seed + " at " + r);
                }
            }
        }
    }

    @Test
    void theSameSeedGivesTheSameLake() {
        LakeShape a = new LakeShape(42, 100, 40), b = new LakeShape(42, 100, 40);
        for (int dx = -100; dx <= 100; dx += 7) {
            for (int dz = -100; dz <= 100; dz += 7) {
                assertEquals(a.value(dx, dz), b.value(dx, dz));
            }
        }
    }

    @Test
    void beyondHasTheShoresSign() {
        LakeShape shape = new LakeShape(7, 100, 40);
        assertTrue(shape.beyond(0, 0) < 0);
        assertTrue(shape.beyond(130, 0) > 0);
    }

    @Test
    void theWaterIsNeitherAPuddleNorTheWholeDisc() {
        // Across many lakes, the water covers a fair share of the disc on average.
        double total = 0;
        for (long seed = 0; seed < SEEDS; seed++) {
            LakeShape shape = new LakeShape(seed, 100, 40);
            int wet = 0, all = 0;
            for (int dx = -100; dx <= 100; dx += 4) {
                for (int dz = -100; dz <= 100; dz += 4) {
                    if (dx * dx + dz * dz <= 100 * 100) {
                        all++;
                        wet += shape.wet(dx, dz) ? 1 : 0;
                    }
                }
            }
            total += (double) wet / all;
        }
        double share = total / SEEDS;
        assertTrue(share > 0.25 && share < 0.75, "average wet share " + share);
    }

    @Test
    void theBedShelvesFromTheShore() {
        LakeShape shape = new LakeShape(3, 100, 40);
        assertEquals(0, shape.depthShare(130, 0));
        assertTrue(shape.depthShare(0, 0) > 0.5);
    }
}
