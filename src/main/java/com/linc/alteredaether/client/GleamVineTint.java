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

/**
 * The colour maths for leaf-tinted gleam vines, with no game classes so it can be tested alone. The vines' leaves are a
 * greyscale texture the game multiplies by a tint; this works out the tint from the colour of the leaves overhead.
 * Mirrors {@code from_leaf} in tools/textures/gleam_vines.py, whose preview sheet is where these constants are tuned.
 */
public final class GleamVineTint {
    /** The tint of a vine that hangs from anything but leaves: greyscale times this is the recolored cave vine. */
    public static final int DEFAULT = 0x4F906C;

    /** The mean grey of the leaves layer, so a tint of (leaf average / this) puts the vine's average on the leaf's. */
    private static final double MEAN_GREY = 0.809;
    /** Leaf-derived tints stay a little below the leaf's own brightness: the vines hang in the canopy's shade. */
    private static final double DARKEN = 0.88;
    /** The most saturation added to pale leaves (bright, not very saturated), or the vines wash out. */
    private static final double BOOST = 0.5;
    private static final double PALE_VALUE_FROM = 0.70, PALE_VALUE_SPAN = 0.30;
    private static final double PALE_SATURATION_FROM = 0.45, PALE_SATURATION_SPAN = 0.25;

    /** The mean colour of the opaque pixels (alpha above 127) of a native image's ABGR pixels, as 0xRRGGBB. */
    public static int average(int[] abgr) {
        long r = 0, g = 0, b = 0, count = 0;
        for (int pixel : abgr) {
            if ((pixel >>> 24) > 127) {
                r += pixel & 0xFF;
                g += (pixel >> 8) & 0xFF;
                b += (pixel >> 16) & 0xFF;
                count++;
            }
        }
        if (count == 0) {
            return DEFAULT;
        }
        return (int) (r / count) << 16 | (int) (g / count) << 8 | (int) (b / count);
    }

    /** The tint for vines hanging from leaves of average colour {@code leaf} (0xRRGGBB). */
    public static int fromLeaf(int leaf) {
        double[] hsv = toHsv((leaf >> 16 & 0xFF) / 255.0, (leaf >> 8 & 0xFF) / 255.0, (leaf & 0xFF) / 255.0);
        double paleness = clamp01((hsv[2] - PALE_VALUE_FROM) / PALE_VALUE_SPAN)
                * clamp01((PALE_SATURATION_FROM - hsv[1]) / PALE_SATURATION_SPAN);
        double saturation = Math.min(1.0, hsv[1] * (1 + BOOST * paleness));
        double[] rgb = toRgb(hsv[0], saturation, hsv[2]);
        double scale = DARKEN / MEAN_GREY * 255;
        double max = Math.max(rgb[0], Math.max(rgb[1], rgb[2])) * scale / 255;
        // a channel past 255 would change the hue; scale all three down together instead
        double fit = max > 1 ? 1 / max : 1;
        return channel(rgb[0] * scale * fit) << 16 | channel(rgb[1] * scale * fit) << 8 | channel(rgb[2] * scale * fit);
    }

    /** {@code leaf} (0xRRGGBB) multiplied channel by channel by {@code tint}, as the game's colour handlers do. */
    public static int multiply(int leaf, int tint) {
        int r = (leaf >> 16 & 0xFF) * (tint >> 16 & 0xFF) / 255;
        int g = (leaf >> 8 & 0xFF) * (tint >> 8 & 0xFF) / 255;
        int b = (leaf & 0xFF) * (tint & 0xFF) / 255;
        return r << 16 | g << 8 | b;
    }

    private static int channel(double value) {
        return (int) Math.max(0, Math.min(255, Math.round(value)));
    }

    private static double clamp01(double x) {
        return Math.max(0, Math.min(1, x));
    }

    /** Hue (0-1), saturation, value. */
    static double[] toHsv(double r, double g, double b) {
        double max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b)), delta = max - min;
        double hue;
        if (delta == 0) {
            hue = 0;
        } else if (max == r) {
            hue = ((g - b) / delta) % 6;
        } else if (max == g) {
            hue = (b - r) / delta + 2;
        } else {
            hue = (r - g) / delta + 4;
        }
        hue = (hue / 6 + 1) % 1;
        return new double[]{hue, max == 0 ? 0 : delta / max, max};
    }

    static double[] toRgb(double h, double s, double v) {
        double c = v * s;
        double x = c * (1 - Math.abs((h * 6) % 2 - 1));
        double m = v - c;
        double[] rgb = switch ((int) (h * 6) % 6) {
            case 0 -> new double[]{c, x, 0};
            case 1 -> new double[]{x, c, 0};
            case 2 -> new double[]{0, c, x};
            case 3 -> new double[]{0, x, c};
            case 4 -> new double[]{x, 0, c};
            default -> new double[]{c, 0, x};
        };
        return new double[]{rgb[0] + m, rgb[1] + m, rgb[2] + m};
    }

    private GleamVineTint() {
    }
}
