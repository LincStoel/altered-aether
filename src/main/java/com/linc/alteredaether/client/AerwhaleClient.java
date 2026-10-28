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
package com.linc.alteredaether.client;

import com.aetherteam.aether.AetherConfig;
import com.aetherteam.aether.entity.AetherEntityTypes;
import com.aetherteam.aether.entity.passive.Aerwhale;
import com.linc.alteredaether.AlteredAether;
import com.linc.alteredaether.entity.AerwhaleMode;
import com.linc.alteredaether.entity.TamedAerwhale;
import com.linc.alteredaether.registry.AARegistries;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * Client wiring for aerwhales: the animated renderers, the saddle's and calf's geometry, the inventory screen, and the
 * key and charge bar for leaping off.
 */
@EventBusSubscriber(modid = AlteredAether.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class AerwhaleClient {
    @SubscribeEvent
    public static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(AerwhaleSaddleLayer.SADDLE, AerwhaleSaddleLayer::createSaddle);
        event.registerLayerDefinition(AerwhaleSaddleLayer.CLASSIC_SADDLE, AerwhaleSaddleLayer::createClassicSaddle);
        event.registerLayerDefinition(AerwhaleCalfModel.LAYER, AerwhaleCalfModel::createCalfLayer);
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.<Aerwhale>registerEntityRenderer(AARegistries.TAMED_AERWHALE.get(), TamedAerwhaleRenderer::new);
        // wild ones animate too; Deep Aether's aerwhales have its own renderer patches, which this would sidestep
        if (!AerwhaleMode.deepAether()) {
            event.registerEntityRenderer(AetherEntityTypes.AERWHALE.get(), AnimatedAerwhaleRenderer::new);
        }
    }

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(AARegistries.AERWHALE_MENU.get(), AerwhaleInventoryScreen::new);
    }

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(AerwhaleDiveKey.KEY);
        event.register(AerwhaleEjectKey.KEY);
    }

    @SubscribeEvent
    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.JUMP_METER, AlteredAether.id("aerwhale_dive_bar"), AerwhaleDiveKey::renderBar);
        event.registerAbove(VanillaGuiLayers.JUMP_METER, AlteredAether.id("aerwhale_eject_bar"), AerwhaleEjectKey::renderBar);
    }

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        TamedAerwhale.legacyModel = AetherConfig.CLIENT.legacy_models::get;
    }

    private AerwhaleClient() {
    }
}
