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
package com.linc.alteredaether;

import com.linc.alteredaether.config.AAClientConfig;
import com.linc.alteredaether.config.AACommonConfig;
import com.linc.alteredaether.registry.AARegistries;
import com.linc.alteredaether.worldgen.region.AlteredRegion;
import com.linc.alteredaether.worldgen.surface.AASurfaceRules;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import org.slf4j.Logger;
import terrablender.api.Regions;

@Mod(AlteredAether.MOD_ID)
public final class AlteredAether {
    public static final String MOD_ID = "altered_aether";
    public static final Logger LOGGER = LogUtils.getLogger();

    public AlteredAether(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, AACommonConfig.SPEC);
        container.registerConfig(ModConfig.Type.CLIENT, AAClientConfig.SPEC);
        AARegistries.register(modBus);
        modBus.addListener(this::commonSetup);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            int weight = AACommonConfig.REGION_WEIGHT.get();
            if (weight > 0) {
                Regions.register(new AlteredRegion(weight));
            }
            AASurfaceRules.register();
        });
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
