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
package com.linc.alteredaether.client.golem;

import com.linc.alteredaether.AlteredAether;
import com.linc.alteredaether.entity.golem.GravititeGolem;
import com.linc.alteredaether.entity.golem.PebbleInfo;
import com.linc.alteredaether.item.PebbleLink;
import com.linc.alteredaether.registry.AARegistries;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.HashMap;
import java.util.Map;

/** The gravitite golem on the client: its model and renderer, the pebble's look, glows, flights, hints and hum. */
public final class GolemClient {
    private GolemClient() {
    }

    @EventBusSubscriber(modid = AlteredAether.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Registration {
        @SubscribeEvent
        public static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
            event.registerLayerDefinition(GravititeGolemModel.LAYER, GravititeGolemModel::createBodyLayer);
        }

        @SubscribeEvent
        public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(AARegistries.GRAVITITE_GOLEM.get(), GravititeGolemRenderer::new);
            event.registerEntityRenderer(AARegistries.THROWN_PEBBLE.get(), ThrownItemRenderer::new);
        }

        @SubscribeEvent
        public static void setup(FMLClientSetupEvent event) {
            event.enqueueWork(() -> ItemProperties.register(AARegistries.STORAGE_PEBBLE.get(), AlteredAether.id("dull"),
                    (stack, level, entity, seed) -> {
                        PebbleLink link = stack.get(AARegistries.PEBBLE_LINK.get());
                        return link == null || !link.glows() ? 1 : 0;
                    }));
            PebbleInfo.viewer = () -> {
                Entity player = Minecraft.getInstance().player;
                return player == null ? null : player.position();
            };
        }

        private Registration() {
        }
    }

    @EventBusSubscriber(modid = AlteredAether.MOD_ID, value = Dist.CLIENT)
    public static final class Events {
        private static final Map<Integer, GolemHumSound> HUMS = new HashMap<>();

        @SubscribeEvent
        public static void onTick(ClientTickEvent.Post event) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || mc.isPaused()) {
                return;
            }
            GolemGlow.tick();
            GolemFlights.tick();
            PebbleHints.tick();
            hums(mc);
        }

        /** A hum for each golem concentrating near the player. */
        private static void hums(Minecraft mc) {
            if (mc.player == null || mc.player.tickCount % 10 != 0) {
                return;
            }
            HUMS.values().removeIf(sound -> !mc.getSoundManager().isActive(sound) && sound.isStopped());
            for (Entity entity : mc.level.entitiesForRendering()) {
                if (entity instanceof GravititeGolem golem && golem.state() == GravititeGolem.CONCENTRATING
                        && golem.distanceToSqr(mc.player) < 24 * 24 && !HUMS.containsKey(golem.getId())) {
                    GolemHumSound sound = new GolemHumSound(golem);
                    HUMS.put(golem.getId(), sound);
                    mc.getSoundManager().play(sound);
                }
            }
        }

        /** How many golem hums are playing. */
        public static int hums() {
            Minecraft mc = Minecraft.getInstance();
            return (int) HUMS.values().stream().filter(sound -> mc.getSoundManager().isActive(sound)).count();
        }

        @SubscribeEvent
        public static void onRender(RenderLevelStageEvent event) {
            GolemGlowRenderer.render(event);
        }

        @SubscribeEvent
        public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
            GolemGlow.clear();
            GolemFlights.clear();
            PebbleInfo.clear();
            HUMS.clear();
        }

        private Events() {
        }
    }
}
