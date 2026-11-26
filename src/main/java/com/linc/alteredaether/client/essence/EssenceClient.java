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
package com.linc.alteredaether.client.essence;

import com.linc.alteredaether.AlteredAether;
import com.linc.alteredaether.essence.DowsingRodItem;
import com.linc.alteredaether.essence.EssenceRegistries;
import net.minecraft.client.renderer.item.ItemProperties;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

/** The essence sources' renderers, their motes of light, and the Dowsing Rod's dip. */
@EventBusSubscriber(modid = AlteredAether.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class EssenceClient {
    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(EssenceRegistries.ORB_BLOCK_ENTITY.get(), EssenceOrbRenderer::new);
        event.registerBlockEntityRenderer(EssenceRegistries.PLINTH_BLOCK_ENTITY.get(), EssencePlinthRenderer::new);
    }

    @SubscribeEvent
    public static void registerParticles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(EssenceRegistries.ESSENCE_NOTE.get(), EssenceParticles.NoteProvider::new);
        event.registerSpriteSet(EssenceRegistries.ESSENCE_MOTE.get(), EssenceParticles.MoteProvider::new);
    }

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> ItemProperties.register(EssenceRegistries.DOWSING_ROD.get(), AlteredAether.id("dip"),
                (stack, level, entity, seed) -> DowsingRodItem.dip(stack, entity,
                        level != null ? level.getGameTime() : entity != null ? entity.level().getGameTime() : 0)));
    }

    private EssenceClient() {
    }
}
