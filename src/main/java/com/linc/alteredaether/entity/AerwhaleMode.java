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

import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.toml.TomlParser;
import com.linc.alteredaether.AlteredAether;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.fml.loading.LoadingModList;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Whose aerwhales are in play: ours ({@link TamedAerwhale}) or, when the {@code use_deep_aether_aerwhales} option is on
 * and Deep Aether is loaded, Deep Aether's. Deep Aether's aerwhales come from mixins, which are chosen before NeoForge
 * loads any config, so the option is read here straight from the file at startup and fixed for the session; the
 * config screen's value only takes effect after a restart.
 *
 * <p>No Minecraft classes here, nor our classes that use them ({@link AlteredAether#MOD_ID} is a constant):
 * {@code AAMixinPlugin} reads this while mixins are still being set up.
 */
public final class AerwhaleMode {
    public static final String CONFIG_KEY = "use_deep_aether_aerwhales";
    public static final String DEEP_AETHER = "deep_aether";

    private static final boolean DEEP_AETHER_LOADED = LoadingModList.get().getModFileById(DEEP_AETHER) != null;
    private static final boolean DEEP_AETHER_WHALES = DEEP_AETHER_LOADED && readOption();

    /** Deep Aether's aerwhales are in play: ours are switched off. */
    public static boolean deepAether() {
        return DEEP_AETHER_WHALES;
    }

    /** Deep Aether is loaded but its aerwhale mixins are cancelled in favour of ours. */
    public static boolean deepAetherCancelled() {
        return DEEP_AETHER_LOADED && !DEEP_AETHER_WHALES;
    }

    private static boolean readOption() {
        Path file = FMLPaths.CONFIGDIR.get().resolve(AlteredAether.MOD_ID + "-common.toml");
        if (!Files.isRegularFile(file)) {
            return false;
        }
        try (Reader reader = Files.newBufferedReader(file)) {
            Config config = new TomlParser().parse(reader);
            return config.get("aerwhales." + CONFIG_KEY) instanceof Boolean value && value;
        } catch (Exception e) {
            LoggerFactory.getLogger(AerwhaleMode.class)
                    .warn("Couldn't read {} from {}; using our own aerwhales", CONFIG_KEY, file, e);
            return false;
        }
    }

    private AerwhaleMode() {
    }
}
