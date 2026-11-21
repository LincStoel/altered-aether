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
package com.linc.alteredaether.worldgen.skirt;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The cloud skirts' grid arithmetic: which islands are skirted, and the collar's shape. */
class SkirtGridTest {
    /** A hand-drawn terrain: sky wherever nothing is drawn. */
    private static class Drawn implements SkirtGrid.Columns {
        final Map<Long, Integer> tops = new HashMap<>();
        final Set<Long> rift = new HashSet<>();

        Drawn disc(int cx, int cz, int radius, int top) {
            for (int x = cx - radius; x <= cx + radius; x++) {
                for (int z = cz - radius; z <= cz + radius; z++) {
                    if ((x - cx) * (x - cx) + (z - cz) * (z - cz) <= radius * radius) {
                        tops.put(SkirtGrid.key(x, z), top);
                    }
                }
            }
            return this;
        }

        @Override
        public int top(int gx, int gz) {
            return tops.getOrDefault(SkirtGrid.key(gx, gz), SkirtGrid.SKY);
        }

        @Override
        public boolean rift(int gx, int gz) {
            return rift.contains(SkirtGrid.key(gx, gz));
        }
    }

    @Test
    void aboutOneIslandInFiveIsSkirted() {
        // Islands big enough for a collar, at different places, drawn for with one seed.
        int skirted = 0, n = 2000;
        for (int i = 0; i < n; i++) {
            Drawn drawn = new Drawn().disc(i * 40, i * -17, 6, 200);
            if (SkirtGrid.flood(drawn, 42, i * 40, i * -17).skirted()) {
                skirted++;
            }
        }
        double share = skirted / (double) n;
        assertTrue(Math.abs(share - SkirtGrid.CHANCE) < 0.04, "share " + share);
    }

    @Test
    void anIslandsDrawIsTheSameFromWhereverItIsFound() {
        for (int i = 0; i < 200; i++) {
            Drawn drawn = new Drawn().disc(i * 40, 0, 6, 190);
            boolean first = SkirtGrid.flood(drawn, 7, i * 40, 0).skirted();
            assertEquals(first, SkirtGrid.flood(drawn, 7, i * 40 + 6, 0).skirted());
            assertEquals(first, SkirtGrid.flood(drawn, 7, i * 40 - 3, 4).skirted());
        }
    }

    @Test
    void aScrapOfRockGetsNoCollar() {
        Drawn scrap = new Drawn().disc(0, 0, 1, 200);
        assertTrue(scrap.tops.size() < SkirtGrid.MIN_ISLAND);
        for (long seed = 0; seed < 50; seed++) {
            assertFalse(SkirtGrid.flood(scrap, seed, 0, 0).skirted());
        }
    }

    @Test
    void anIslandIsTheSameFromWhereverItIsFound() {
        Drawn drawn = new Drawn().disc(0, 0, 10, -8).disc(14, 0, 6, -2);
        long[] a = SkirtGrid.flood(drawn, 1, 0, 0).points(), b = SkirtGrid.flood(drawn, 1, 17, 1).points();
        Arrays.sort(a);
        Arrays.sort(b);
        assertArrayEquals(a, b);
    }

    @Test
    void aDropSeparatesIslands() {
        // An islet right beside one far higher: two islands.
        Drawn drawn = new Drawn().disc(0, 0, 6, 180).disc(12, 0, 6, 220);
        assertEquals(SkirtGrid.flood(drawn, 1, 0, 0).points().length + SkirtGrid.flood(drawn, 1, 12, 0).points().length,
                drawn.tops.size());
    }

    @Test
    void aHugeLandmassIsOverTheCapAndBare() {
        Drawn drawn = new Drawn();
        for (int x = 0; x < 100; x++) {
            for (int z = 0; z < 100; z++) {
                drawn.tops.put(SkirtGrid.key(x, z), 200);
            }
        }
        SkirtGrid.Island island = SkirtGrid.flood(drawn, 1, 50, 50);
        assertTrue(island.overCap());
        assertFalse(island.skirted());
    }

    @Test
    void riftColumnsAreNeverRim() {
        Drawn drawn = new Drawn().disc(0, 0, 6, 200);
        drawn.tops.keySet().forEach(drawn.rift::add);
        assertFalse(SkirtGrid.rim(drawn, 6, 0));
        // No rim, so no collar, whatever the draw.
        for (long seed = 0; seed < 50; seed++) {
            assertFalse(SkirtGrid.flood(drawn, seed, 0, 0).skirted());
        }
        assertEquals(0, SkirtGrid.riftFade(0));
        assertEquals(1, SkirtGrid.riftFade(SkirtGrid.RIFT_FADE));
    }

    @Test
    void theCollarIsALensUnderTheRim() {
        double[] at = new double[2];
        assertTrue(SkirtGrid.span(0, -10, 1, 24, 4, 0, 0, at));
        assertEquals(-10 - SkirtGrid.GAP, at[1], 1e-9);
        assertEquals(-10 - SkirtGrid.GAP - 4, at[0], 1e-9);
        double last = at[1] - at[0];
        for (double d = 2; d < 24; d += 2) {
            assertTrue(SkirtGrid.span(d, -10, 1, 24, 4, 0, 0, at));
            double thick = at[1] - at[0];
            assertTrue(thick < last, "thinner at " + d);
            last = thick;
        }
        assertFalse(SkirtGrid.span(24, -10, 1, 24, 4, 0, 0, at));
        assertFalse(SkirtGrid.span(4, -10, 0, 24, 4, 0, 0, at));
    }

    @Test
    void neighbouringCollarsJoin() {
        // Two rims 40 blocks apart at different heights: every point between them is in a collar, at a height
        // between theirs that changes smoothly.
        SkirtGrid.Sources sources = new SkirtGrid.Sources();
        sources.add(0, 0, -10);
        sources.add(40, 0, -20);
        double[] sample = new double[2], at = new double[2];
        double lastHeight = Double.NaN;
        for (int x = 0; x <= 40; x++) {
            SkirtGrid.sample(sources, x, 0, sample);
            assertTrue(SkirtGrid.span(sample[0], sample[1], 1, SkirtGrid.MAX_WIDTH, 4, 0, 0, at), "gap at " + x);
            assertTrue(sample[1] <= -10 + 1e-9 && sample[1] >= -20 - 1e-9);
            if (!Double.isNaN(lastHeight)) {
                assertTrue(sample[1] <= lastHeight + 1e-9);
                assertTrue(lastHeight - sample[1] < 2, "step at " + x);
            }
            lastHeight = sample[1];
        }
    }

    @Test
    void rimsOutOfReachChangeNothing() {
        // A tile sees more rims than its neighbour; a point both share must come out the same in each.
        SkirtGrid.Sources near = new SkirtGrid.Sources(), more = new SkirtGrid.Sources();
        near.add(0, 0, -8);
        near.add(12, 4, -14);
        more.add(-200, 0, 5);
        more.add(0, 0, -8);
        more.add(12, 4, -14);
        more.add(300, 40, -40);
        double[] a = new double[2], b = new double[2];
        SkirtGrid.sample(near, 6, 10, a);
        SkirtGrid.sample(more, 6, 10, b);
        assertArrayEquals(a, b);
        SkirtGrid.sample(near, 500, 500, a);
        assertEquals(SkirtGrid.FAR, a[0]);
    }

    @Test
    void aSlopedRimGetsNoCollar() {
        assertEquals(1, SkirtGrid.levelness(0));
        assertEquals(1, SkirtGrid.levelness(SkirtGrid.LEVEL));
        assertEquals(0, SkirtGrid.levelness(SkirtGrid.SLOPED));
        // A rim dropping a block in every eight is a slope.
        assertEquals(0, SkirtGrid.levelness(1 / 8.0));
        double between = SkirtGrid.levelness((SkirtGrid.LEVEL + SkirtGrid.SLOPED) / 2);
        assertTrue(between > 0 && between < 1);
    }

    @Test
    void theCollarNeedsOpenAirUnderIt() {
        assertEquals(0, SkirtGrid.clearance(0));
        assertEquals(0, SkirtGrid.clearance(SkirtGrid.CLEAR_MIN));
        assertEquals(1, SkirtGrid.clearance(SkirtGrid.CLEAR));
        double last = 0;
        for (int air = SkirtGrid.CLEAR_MIN; air <= SkirtGrid.CLEAR; air++) {
            double now = SkirtGrid.clearance(air);
            assertTrue(now >= last);
            last = now;
        }
    }

    @Test
    void aThinCollarIsStillOneUnbrokenLayer() {
        // Spans under a block thick but more than half, wherever they sit, fill exactly one block, and spans of one
        // thickness at heights a little apart fill blocks that touch: no holes where the span misses a block's centre.
        for (double middle = -12; middle <= -10; middle += 0.05) {
            assertEquals(1, SkirtGrid.layers(middle - 0.3, middle + 0.3), "at " + middle);
            int lowest = SkirtGrid.lowest(middle - 0.3, middle + 0.3);
            assertTrue(Math.abs(lowest + 0.5 - middle) <= 0.5 + 1e-9);
        }
        assertEquals(0, SkirtGrid.layers(-10.2, -9.8));
        assertEquals(4, SkirtGrid.layers(-14, -10));
        assertEquals(-14, SkirtGrid.lowest(-14, -10));
        // The lens tapers to an edge one layer thick all the way round, never thicker on the outside.
        double[] at = new double[2];
        int last = Integer.MAX_VALUE;
        for (double d = 0; d < 40; d += 0.5) {
            int count = SkirtGrid.span(d, -10, 1, 40, 5, 0, 0, at) ? SkirtGrid.layers(at[0], at[1]) : 0;
            assertTrue(count <= last, "thicker at " + d);
            last = count;
        }
    }

    @Test
    void widthAndThicknessStayInTheirRanges() {
        for (int i = 0; i < 2000; i++) {
            double x = i * 7.3 - 3000, z = i * -11.1 + 900;
            double w = SkirtGrid.width(99, x, z), t = SkirtGrid.thickness(99, x, z);
            assertTrue(w >= SkirtGrid.MIN_WIDTH - 1e-9 && w <= SkirtGrid.MAX_WIDTH + 1e-9, "width " + w);
            assertTrue(t >= SkirtGrid.MIN_THICK - 1e-9 && t <= SkirtGrid.MAX_THICK + 1e-9, "thickness " + t);
        }
    }
}
