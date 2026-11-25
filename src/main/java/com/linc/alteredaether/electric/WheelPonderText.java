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
package com.linc.alteredaether.electric;

import com.linc.alteredaether.AlteredAether;

import java.util.List;
import java.util.Map;

/**
 * The words of the Aerogel Wheel's Ponder scene (compat/create/WheelPonder), kept apart from it so the language file
 * can be generated without Create: Ponder looks each line up by its number in the scene.
 */
public final class WheelPonderText {
    public static final String SCENE = "aerogel_wheel";
    public static final String HEADER = "Spinning the Aerogel Wheel";
    public static final List<String> LINES = List.of(
            "The Aerogel Wheel is turned by the charge of two Aether Jars",
            "Set a lightning rod level with it two blocks out on each side, each on top of a jar",
            "A jar may sit on any side of its rod, turned any way, so long as its top touches the rod; leave the blocks between the rods and the wheel empty",
            "Lightning striking the rods charges the jars, as do Squall Generators sparking at them and cables into the jars' bottoms",
            "Set its speed on the panel at its side, up to 256 RPM either way",
            "The faster it turns, the more stress it gives, and the faster it drains the jars",
            "When either jar runs dry, or it has a redstone signal, it stops");

    /** The scene's lines in the language file, as Ponder names them. */
    public static void language(Map<String, String> lang) {
        String prefix = AlteredAether.MOD_ID + ".ponder." + SCENE + ".";
        lang.put(prefix + "header", HEADER);
        for (int i = 0; i < LINES.size(); i++) {
            lang.put(prefix + "text_" + (i + 1), LINES.get(i));
        }
    }

    private WheelPonderText() {
    }
}
