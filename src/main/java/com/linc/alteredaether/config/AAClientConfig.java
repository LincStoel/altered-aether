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
package com.linc.alteredaether.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.ModConfigSpec.BooleanValue;
import net.neoforged.neoforge.common.ModConfigSpec.DoubleValue;
import net.neoforged.neoforge.common.ModConfigSpec.EnumValue;
import net.neoforged.neoforge.common.ModConfigSpec.IntValue;

/** Client-only presentation settings. */
public final class AAClientConfig {
    public static final ModConfigSpec SPEC;

    public static final BooleanValue STORM_FOG;
    public static final DoubleValue STORM_FOG_STRENGTH;
    public static final BooleanValue SKY_FLASHES;
    public static final BooleanValue LOCAL_RAIN;
    public static final DoubleValue PARTICLE_DENSITY;
    public static final EnumValue<ShaderMode> SHADER_MODE;
    public static final BooleanValue GLEAM_VINE_LEAF_TINT;
    public static final ModConfigSpec.ConfigValue<String> GOLEM_GLOW_COLOR;
    public static final BooleanValue GOLEM_GLOW_THROUGH_WALLS;
    public static final BooleanValue GOLEM_SHOW_FLIGHTS;
    public static final BooleanValue GOLEM_REDUCED_EFFECTS;
    public static final IntValue GOLEM_LINK_GLOW_SECONDS;
    public static final BooleanValue GOLEM_HELD_LINKS;
    public static final IntValue GOLEM_SEARCH_SECONDS;
    public static final IntValue GOLEM_NETWORK_ROWS;
    public static final EnumValue<NetworkSort> GOLEM_NETWORK_SORT;
    public static final BooleanValue GOLEM_NETWORK_JEI_SYNC;

    /** How the Golem Chest Network screen orders what it lists. */
    public enum NetworkSort {
        COUNT_DOWN, COUNT_UP, NAME, NAME_REVERSED, CHEST;

        public NetworkSort next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    public enum ShaderMode {
        /** Detect an active Iris shaderpack. */
        AUTO,
        /** Always use the shaderpack-friendly effects. */
        FORCE_ON,
        /** Always use the vanilla-path effects. */
        FORCE_OFF
    }

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("atmosphere");
        STORM_FOG = b.comment("Darken and thicken fog inside storm biomes. Shaderpacks draw their own fog, so this has",
                        "little effect with one active.")
                .define("storm_fog", true);
        STORM_FOG_STRENGTH = b.defineInRange("storm_fog_strength", 1.0, 0.0, 2.0);
        SKY_FLASHES = b.comment("Distant lightning flashes over storm biomes.").define("sky_flashes", true);
        LOCAL_RAIN = b.comment("Show rain inside storm biomes.").define("local_rain", true);
        PARTICLE_DENSITY = b.comment("Multiplier for ambient biome particles added by this mod.")
                .defineInRange("particle_density", 1.0, 0.0, 4.0);
        SHADER_MODE = b.comment("How atmospheric effects adapt to shaderpacks. AUTO detects Iris.")
                .defineEnum("shader_mode", ShaderMode.AUTO);
        b.pop();
        b.push("blocks");
        GLEAM_VINE_LEAF_TINT = b.comment("Tint gleam vines to match the leaves they hang from. Off, they are always the",
                        "Aether's blue-green.")
                .define("gleam_vine_leaf_tint", true);
        b.pop();
        b.push("gravitite_golem");
        GOLEM_GLOW_COLOR = b.comment("Colour of the glow on linked storage and golems, as #RRGGBB.")
                .define("glow_color", "#E072D7", o -> o instanceof String s && parseColor(s) >= 0);
        GOLEM_GLOW_THROUGH_WALLS = b.comment("Show the glow through walls.").define("glow_through_walls", false);
        GOLEM_SHOW_FLIGHTS = b.comment("Draw items flying between chests.").define("show_flights", true);
        GOLEM_REDUCED_EFFECTS = b.comment("Fewer particles around golems, their range and their flights.")
                .define("reduced_effects", false);
        GOLEM_LINK_GLOW_SECONDS = b.comment("Seconds a block glows after it is linked.")
                .defineInRange("link_glow_seconds", 2, 1, 30);
        GOLEM_HELD_LINKS = b.comment("While a storage pebble is held, faintly outline the storage its network has linked.")
                .define("held_pebble_links", true);
        GOLEM_SEARCH_SECONDS = b.comment("Seconds storage holding a searched item glows.")
                .defineInRange("search_seconds", 15, 1, 120);
        GOLEM_NETWORK_ROWS = b.comment("Rows of items the Golem Chest Network screen shows at once (fewer if the window",
                        "is too short).")
                .defineInRange("network_rows", 6, 4, 12);
        GOLEM_NETWORK_SORT = b.comment("How the Golem Chest Network screen orders items. Changes when you press its sort",
                        "button.")
                .defineEnum("network_sort", NetworkSort.COUNT_DOWN);
        GOLEM_NETWORK_JEI_SYNC = b.comment("Whether the Golem Chest Network screen's search box follows JEI's, and JEI's",
                        "follows it. Changes when you press its JEI button.")
                .define("network_jei_sync", false);
        b.pop();
        SPEC = b.build();
    }

    /** {@code #RRGGBB} (or {@code RRGGBB}) as an RGB int, or -1 if it isn't one. */
    public static int parseColor(String text) {
        String hex = text.startsWith("#") ? text.substring(1) : text;
        if (hex.length() != 6) {
            return -1;
        }
        try {
            return Integer.parseInt(hex, 16);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** The golem glow colour, falling back to the default if the setting is unreadable. */
    public static int golemGlowColor() {
        int color = parseColor(GOLEM_GLOW_COLOR.get());
        return color < 0 ? 0xE072D7 : color;
    }

    private AAClientConfig() {
    }
}
