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
package com.linc.alteredaether.worldgen.feature;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DriftShapeTest {
    private static final double PEAK = 20;

    @Test
    void crestReachesThePeakAndTroughIsBare() {
        assertEquals(1, DriftShape.profile(DriftShape.CREST), 1e-9);
        assertEquals(0, DriftShape.profile(0.95), 1e-9);
        assertTrue(DriftShape.inside(DriftShape.CREST * DriftShape.SPACING, 1, PEAK));
        assertFalse(DriftShape.inside(0.9 * DriftShape.SPACING, 1, PEAK));
    }

    @Test
    void windwardIsGentlerThanLee() {
        double windward = DriftShape.CREST, lee = DriftShape.LEE;
        assertTrue(windward > 3 * lee, "the windward ramp should be several times the lee face's width");
    }

    @Test
    void lipOverhangsAHollow() {
        // Just past the crest on the lee side, near the top there is snow while lower down there is air: an overhang.
        double crestAt = DriftShape.CREST * DriftShape.SPACING;
        boolean overhang = false;
        for (double along = crestAt; along < crestAt + 12; along += 0.5) {
            boolean high = DriftShape.inside(along, PEAK * 0.8, PEAK);
            boolean low = DriftShape.inside(along, PEAK * 0.3, PEAK);
            overhang |= high && !low;
        }
        assertTrue(overhang, "the upper layers should lean out over the lee face");
    }

    @Test
    void belowGroundIsAlwaysInside() {
        assertTrue(DriftShape.inside(0.95 * DriftShape.SPACING, -1, PEAK));
    }
}
