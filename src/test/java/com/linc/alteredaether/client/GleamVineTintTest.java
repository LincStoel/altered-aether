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
package com.linc.alteredaether.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GleamVineTintTest {
    private static double saturation(int rgb) {
        return GleamVineTint.toHsv((rgb >> 16 & 0xFF) / 255.0, (rgb >> 8 & 0xFF) / 255.0, (rgb & 0xFF) / 255.0)[1];
    }

    private static double hue(int rgb) {
        return GleamVineTint.toHsv((rgb >> 16 & 0xFF) / 255.0, (rgb >> 8 & 0xFF) / 255.0, (rgb & 0xFF) / 255.0)[0];
    }

    /** What tools/textures/gleam_vines.py's from_leaf gives for the same leaf averages (to rounding). */
    @Test
    void matchesThePythonPreview() {
        assertRgb(0xFCCDDF, GleamVineTint.fromLeaf(0xE8C8D4));
        assertRgb(0x739C3F, GleamVineTint.fromLeaf(0x6A8F3A));
        assertRgb(0xF4BF23, GleamVineTint.fromLeaf(0xE0B020));
    }

    @Test
    void paleLeavesGainSaturationButSaturatedOnesDoNot() {
        int pale = 0xE8C8D4;
        int boosted = GleamVineTint.fromLeaf(pale);
        // the plain tint of the same leaf, without the boost, is the colour scaled by 1 / mean grey
        assertTrue(saturation(boosted) > saturation(pale) * 1.1, "pale leaves should be more saturated");

        int golden = 0xE0B020;
        assertEquals(saturation(golden), saturation(GleamVineTint.fromLeaf(golden)), 0.02);
    }

    @Test
    void leafTintsRunDarkerThanThePlainOne() {
        int leaf = 0x6A8F3A;
        int plain = GleamVineTint.multiply(0xFFFFFF, GleamVineTint.fromLeaf(leaf));
        // a vine's mean grey (0.809) times the tint lands near 0.88 of the leaf's brightness
        double expected = 0.88 * Math.max(leaf >> 16 & 0xFF, Math.max(leaf >> 8 & 0xFF, leaf & 0xFF));
        double actual = 0.809 * Math.max(plain >> 16 & 0xFF, Math.max(plain >> 8 & 0xFF, plain & 0xFF));
        assertEquals(expected, actual, 4);
    }

    @Test
    void brightLeavesKeepTheirHueWhenTheTintIsClamped() {
        int leaf = 0xF0FFF0;
        int tint = GleamVineTint.fromLeaf(leaf);
        assertTrue((tint >> 16 & 0xFF) <= 255 && (tint >> 8 & 0xFF) <= 255 && (tint & 0xFF) <= 255);
        assertEquals(hue(leaf), hue(tint), 0.02);
    }

    @Test
    void averagesTheOpaquePixelsOfAnAbgrImage() {
        int red = 0xFF0000FF, blue = 0xFFFF0000, clear = 0x00123456;
        assertEquals(0x7F007F,GleamVineTint.average(new int[]{red, blue, clear}));
        assertEquals(GleamVineTint.DEFAULT, GleamVineTint.average(new int[]{clear}));
    }

    private static void assertRgb(int expected, int actual) {
        for (int shift = 0; shift <= 16; shift += 8) {
            assertEquals(expected >> shift & 0xFF, actual >> shift & 0xFF, 2, "channel at shift " + shift);
        }
    }
}
