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

/** Client-only presentation settings. */
public final class AAClientConfig {
    public static final ModConfigSpec SPEC;

    public static final BooleanValue STORM_FOG;
    public static final DoubleValue STORM_FOG_STRENGTH;
    public static final BooleanValue SKY_FLASHES;
    public static final BooleanValue LOCAL_RAIN;
    public static final DoubleValue PARTICLE_DENSITY;
    public static final BooleanValue FIREFLIES;
    public static final EnumValue<ShaderMode> SHADER_MODE;

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
        FIREFLIES = b.comment("Fireflies in the Skyroot Cathedral and Nurselog Hollow: a few in the shade by day, swarms",
                        "at night. Scaled by particle_density.")
                .define("fireflies", true);
        SHADER_MODE = b.comment("How atmospheric effects adapt to shaderpacks. AUTO detects Iris.")
                .defineEnum("shader_mode", ShaderMode.AUTO);
        b.pop();
        SPEC = b.build();
    }

    private AAClientConfig() {
    }
}
