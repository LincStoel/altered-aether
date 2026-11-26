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
package com.linc.alteredaether.compat.create;

import com.linc.alteredaether.compat.CreateElectric;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.createmod.ponder.foundation.PonderIndex;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** The Aerogel Wheel's renderer and Ponder scene, on the client, with Create. */
public final class CreateElectricClient {
    public static void register(IEventBus modBus) {
        modBus.addListener(CreateElectricClient::renderers);
        modBus.addListener(CreateElectricClient::setup);
    }

    private static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(CreateElectric.AEROGEL_WHEEL_BLOCK_ENTITY.get(), WheelRenderer::new);
    }

    private static void setup(FMLClientSetupEvent event) {
        PonderIndex.addPlugin(new WheelPonder());
    }

    /**
     * Spins the wheel's model about its axle. Drawn here rather than by a Flywheel visual: there are few wheels, and
     * this way it shows whichever renderer is in use.
     */
    static final class WheelRenderer implements BlockEntityRenderer<AerogelWheelBlockEntity> {
        WheelRenderer(BlockEntityRendererProvider.Context context) {
        }

        @Override
        public void render(AerogelWheelBlockEntity wheel, float partialTick, PoseStack pose, MultiBufferSource buffers,
                           int light, int overlay) {
            SuperByteBuffer model = CachedBuffers.block(KineticBlockEntityRenderer.KINETIC_BLOCK, wheel.getBlockState());
            KineticBlockEntityRenderer.renderRotatingBuffer(wheel, model, pose, buffers.getBuffer(RenderType.translucent()),
                    light);
        }

        @Override
        public boolean shouldRenderOffScreen(AerogelWheelBlockEntity wheel) {
            return true;
        }
    }

    private CreateElectricClient() {
    }
}
