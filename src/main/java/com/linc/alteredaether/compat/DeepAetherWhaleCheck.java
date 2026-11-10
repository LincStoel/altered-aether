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
package com.linc.alteredaether.compat;

import com.linc.alteredaether.AlteredAether;
import com.linc.alteredaether.entity.AerwhaleMode;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;

import java.util.List;

/**
 * At startup, with Deep Aether installed and our aerwhales in use, checks that its aerwhale patches were all cancelled
 * as {@link DeepAetherWhalePatches} expects, and logs a warning for each that wasn't, so a Deep Aether update that moves
 * them shows up in the log rather than as odd whales.
 */
@EventBusSubscriber(modid = AlteredAether.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class DeepAetherWhaleCheck {
    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        List<String> problems = check();
        if (problems.isEmpty()) {
            return;
        }
        String version = ModList.get().getModContainerById(AerwhaleMode.DEEP_AETHER)
                .map(container -> container.getModInfo().getVersion().toString()).orElse("?");
        AlteredAether.LOGGER.warn("Deep Aether {} has changed its aerwhale patches since Altered Aether was made for it. "
                + "Its whales may not be fully replaced by ours; set aerwhales.{} = true in "
                + "config/altered_aether-common.toml to use Deep Aether's whales instead.", version,
                AerwhaleMode.CONFIG_KEY);
        problems.forEach(problem -> AlteredAether.LOGGER.warn("  {}", problem));
    }

    /**
     * What doesn't match, or nothing if all is as expected or there is nothing to check. Loads the patched classes of
     * this side first: MixinSquared only asks about a mixin when its target loads.
     */
    public static List<String> check() {
        if (!AerwhaleMode.deepAetherCancelled()) {
            return List.of();
        }
        boolean client = FMLEnvironment.dist.isClient();
        List<DeepAetherWhalePatches.Patch> expected = DeepAetherWhalePatches.KNOWN.stream()
                .filter(patch -> client || !patch.client())
                .toList();
        for (DeepAetherWhalePatches.Patch patch : expected) {
            try {
                Class.forName(patch.target(), false, DeepAetherWhaleCheck.class.getClassLoader());
            } catch (ClassNotFoundException e) {
                AlteredAether.LOGGER.warn("The Aether has no {}", patch.target());
            }
        }
        return DeepAetherWhalePatches.problems(expected, DeepAetherWhalePatches.cancelled(),
                DeepAetherWhalePatches.spared());
    }

    private DeepAetherWhaleCheck() {
    }
}
