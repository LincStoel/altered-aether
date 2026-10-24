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
package com.linc.alteredaether.worldgen.surface;

import com.google.gson.JsonParser;
import com.linc.alteredaether.AlteredAether;
import com.mojang.serialization.JsonOps;
import io.github.razordevs.aeroblender.aether.AetherRuleCategory;
import net.minecraft.world.level.levelgen.SurfaceRules;
import terrablender.api.SurfaceRuleManager;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * Registers our biomes' surface rules with TerraBlender. Aeroblender dispatches the Aether's surface rules by biome
 * namespace, so these apply to {@code altered_aether:} biomes only and leave every other mod's biomes alone.
 *
 * <p>The rules themselves are data: datagen writes them to {@link #PATH}, and they are decoded here with vanilla's
 * own surface rule codec.
 */
public final class AASurfaceRules {
    public static final String PATH = "/data/" + AlteredAether.MOD_ID + "/surface_rules/aether.json";

    public static void register() {
        SurfaceRules.RuleSource rules = load();
        SurfaceRuleManager.addSurfaceRules(AetherRuleCategory.THE_AETHER, AlteredAether.MOD_ID, rules);
    }

    private static SurfaceRules.RuleSource load() {
        try (InputStream in = AASurfaceRules.class.getResourceAsStream(PATH)) {
            if (in == null) {
                throw new IllegalStateException("Missing " + PATH + "; run datagen");
            }
            var json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            return SurfaceRules.RuleSource.CODEC.parse(JsonOps.INSTANCE, json)
                    .getOrThrow(error -> new IllegalStateException("Bad surface rules in " + PATH + ": " + error));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private AASurfaceRules() {
    }
}
