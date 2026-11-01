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
        return sketch.done(GiantType.LINK, random.nextLong());
    }

    /** A spring stump standing on level ground at a height, with no mire round it, in skyroot. */
    public static Giant springStump(RandomSource random, int x, int ground, int z) {
        Sketch sketch = new Sketch(null, random, 1, Integer.MAX_VALUE, x, z, ground);
        ColossalStump.sketch(sketch, 0, true);
        ColossalStump.runoff(sketch, null);
        return sketch.done(GiantType.SPRING_STUMP, random.nextLong());
    }

    /**
     * A giant of a type on level ground, as the grid draws it but with no lake and no roots under the island, and
     * with a burrow cut into it if it will take one (null when the type has none to draw, or it will not).
     */
    public static Giant burrowed(GiantType type, RandomSource random, int x, int ground, int z) {
        Sketch sketch = new Sketch(null, random, 1, Integer.MAX_VALUE, x, z, ground);
        sketch.arching = type.crowned();
        switch (type) {
            case HOLLOW_ELDER -> HollowElder.sketch(sketch);
            case TUNNEL_GIANT -> TunnelGiant.sketch(sketch);
            case TULE_MASS -> TuleMass.sketch(sketch);
            case BOW_COLONY -> BowColony.sketch(sketch);
            case CATHEDRAL_RING -> CathedralRing.sketch(sketch);
            case FALLEN_COLOSSUS -> FallenColossus.sketch(sketch, 0);
            case SNAG -> Snag.sketch(sketch);
            case COLOSSAL_STUMP -> ColossalStump.sketch(sketch, 0, false);
            case LATTICE_SKELETON -> LatticeSkeleton.sketch(sketch);
            case REGROWTH_RING -> RegrowthRing.sketch(sketch);
            default -> {
                return null;
            }
        }
        Burrow.sketch(sketch, random);
        Giant giant = sketch.done(type, random.nextLong());
        return giant.points("burrow").isEmpty() ? null : giant;
    }

    private GroveSamples() {
    }
}
