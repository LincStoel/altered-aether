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
package com.linc.alteredaether.worldgen.grove;

import net.minecraft.util.RandomSource;

/** Small pieces of giants, drawn on their own for tests: nothing here reads the terrain. */
public final class GroveSamples {
    /** A lone crown of the given size spreading from a point, in skyroot. */
    public static Giant crown(RandomSource random, int x, int y, int z, double size) {
        Sketch sketch = new Sketch(null, random, 1, Integer.MAX_VALUE, x, z, y);
        sketch.crown(x, y, z, size, 1.2, 0, 0);
        return sketch.done(GiantType.LINK, GrovePalette.SKYROOT, random.nextLong());
    }

    private GroveSamples() {
    }
}
