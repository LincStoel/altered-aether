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

import java.util.function.IntBinaryOperator;

/** The search behind {@link BiomeEdgeField}, kept free of Minecraft classes so it can be unit tested. */
public final class EdgeSearch {
    /**
     * The distance from (x, z) to the nearest point of another class, up to {@code reach}: rings of probes spaced a
     * quart apart, then a bisection along the bearing that first found the border. A sliver of another class narrower
     * than the gap between bearings can be missed, which only puts the border a little further off.
     */
    public static double distance(int x, int z, int reach, IntBinaryOperator classAt) {
        int own = classAt.applyAsInt(x, z);
        int rings = Math.max(1, (int) Math.ceil(reach / 4.0));
        int directions = reach <= 48 ? 16 : 24;
        double previous = 0;
        for (int ring = 1; ring <= rings; ring++) {
            double radius = reach * (double) ring / rings;
            for (int d = 0; d < directions; d++) {
                // Each ring's bearings sit half a step round from the last ring's.
                double angle = (d + (ring % 2) * 0.5) * (Math.PI * 2 / directions);
                double cos = Math.cos(angle), sin = Math.sin(angle);
                if (classAt.applyAsInt(x + (int) Math.round(cos * radius), z + (int) Math.round(sin * radius)) != own) {
                    double lo = previous, hi = radius;
                    for (int i = 0; i < 4; i++) {
                        double mid = (lo + hi) * 0.5;
                        if (classAt.applyAsInt(x + (int) Math.round(cos * mid), z + (int) Math.round(sin * mid)) == own) {
                            lo = mid;
                        } else {
                            hi = mid;
                        }
                    }
                    return (lo + hi) * 0.5;
                }
            }
            previous = radius;
        }
        return reach;
    }

    private EdgeSearch() {
    }
}
