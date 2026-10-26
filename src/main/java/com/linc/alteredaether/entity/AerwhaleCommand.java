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

import com.linc.alteredaether.AlteredAether;

import java.util.Locale;

/** What a tamed aerwhale does while nobody rides it. Its owner cycles through these by sneaking and using it. */
public enum AerwhaleCommand {
    /** Circle slowly round where it was told to stay, coming back if pushed away. */
    STAY,
    /** Roam freely, as a wild aerwhale does. */
    WANDER,
    /** Keep near its owner as a wolf does, teleporting to catch up when left far behind. */
    FOLLOW;

    private static final AerwhaleCommand[] VALUES = values();

    public AerwhaleCommand next() {
        return VALUES[(ordinal() + 1) % VALUES.length];
    }

    public String translationKey() {
        return "message." + AlteredAether.MOD_ID + ".aerwhale." + name().toLowerCase(Locale.ROOT);
    }

    public static AerwhaleCommand byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : STAY;
    }

    public static AerwhaleCommand byName(String name) {
        for (AerwhaleCommand command : VALUES) {
            if (command.name().equalsIgnoreCase(name)) {
                return command;
            }
        }
        return STAY;
    }
}
