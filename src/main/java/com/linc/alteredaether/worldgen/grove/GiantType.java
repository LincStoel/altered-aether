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

import java.util.Locale;

/**
 * The kinds of giant a grove cell can hold, each with the biome it grows in and how often it is chosen there.
 * {@link #LINK} is not a tree but the roots and canopy bridges joining two neighbours.
 */
public enum GiantType {
    HOLLOW_ELDER(GroveField.CATHEDRAL, 3),
    TUNNEL_GIANT(GroveField.CATHEDRAL, 2),
    TULE_MASS(GroveField.CATHEDRAL, 2),
    BOW_COLONY(GroveField.CATHEDRAL, 3),
    CATHEDRAL_RING(GroveField.CATHEDRAL, 2),
    FALLEN_COLOSSUS(GroveField.NURSELOG, 4),
    SNAG(GroveField.NURSELOG, 2),
    COLOSSAL_STUMP(GroveField.NURSELOG, 2),
    LATTICE_SKELETON(GroveField.NURSELOG, 1),
    REGROWTH_RING(GroveField.NURSELOG, 2),
    /** A colossal stump with a spring in its hollow heart, chosen in place of a plain one rather than by weight. */
    SPRING_STUMP("", 0),
    /** The Cathedral's landmark, placed by the landmark field rather than the grove grid. */
    GRANDFATHER("", 0),
    /** The deserted village tree, a Cathedral landmark like the Grandfather. */
    VILLAGE_TREE("", 0),
    LINK("", 0),
    /** The fallen giant and roots across a break in a Nurselog Hollow island, placed by the break field. */
    BREAK_BRIDGE("", 0),
    /** A young skyroot of the midstory between the giants, placed by {@link Understory} rather than the grid. */
    YOUNG("", 0);

    final String biome;
    final int weight;

    GiantType(String biome, int weight) {
        this.biome = biome;
        this.weight = weight;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** True for a living tree with a high crown, which can carry a canopy bridge. */
    boolean crowned() {
        return biome.equals(GroveField.CATHEDRAL);
    }
}
