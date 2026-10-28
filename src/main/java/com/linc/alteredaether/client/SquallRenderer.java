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

import com.aetherteam.aether.client.renderer.AetherModelLayers;
import com.aetherteam.aether.client.renderer.entity.ZephyrRenderer;
import com.aetherteam.aether.client.renderer.entity.layers.ZephyrTransparencyLayer;
import com.aetherteam.aether.client.renderer.entity.model.ZephyrModel;
import com.aetherteam.aether.entity.monster.Zephyr;
import com.linc.alteredaether.AlteredAether;
import com.linc.alteredaether.registry.AARegistries;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * The zephyr's renderer and model in the squall's storm-cloud textures, with its translucent outer layer swapped for
 * ours. There is no classic texture, so the squall keeps the current model when the Aether's legacy models are on.
 */
public class SquallRenderer extends ZephyrRenderer {
    private static final ResourceLocation TEXTURE = AlteredAether.id("textures/entity/mobs/squall/squall.png");
    private static final ResourceLocation LAYER_TEXTURE = AlteredAether.id("textures/entity/mobs/squall/squall_layer.png");

    public SquallRenderer(EntityRendererProvider.Context context) {
        super(context);
        layers.removeIf(layer -> layer instanceof ZephyrTransparencyLayer);
        addLayer(new TransparencyLayer(this, new ZephyrModel(context.bakeLayer(AetherModelLayers.ZEPHYR_TRANSPARENCY))));
    }

    @Override
    public EntityModel<Zephyr> getModel() {
        return getDefaultModel();
    }

    @Override
    public ResourceLocation getDefaultTexture() {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getOldTexture() {
        return TEXTURE;
    }

    /** ZephyrTransparencyLayer with our texture. */
    private static final class TransparencyLayer extends RenderLayer<Zephyr, EntityModel<Zephyr>> {
        private final ZephyrModel transparency;

        TransparencyLayer(RenderLayerParent<Zephyr, EntityModel<Zephyr>> parent, ZephyrModel transparency) {
            super(parent);
            this.transparency = transparency;
        }

        @Override
        public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, Zephyr squall,
                           float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw,
                           float headPitch) {
            if (squall.isInvisible()) {
                return;
            }
            getParentModel().copyPropertiesTo(transparency);
            transparency.prepareMobModel(squall, limbSwing, limbSwingAmount, partialTick);
            transparency.setupAnim(squall, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
            VertexConsumer consumer = buffer.getBuffer(RenderType.entityTranslucent(LAYER_TEXTURE));
            transparency.renderToBuffer(poseStack, consumer, packedLight, LivingEntityRenderer.getOverlayCoords(squall, 0.0F));
        }
    }

    @EventBusSubscriber(modid = AlteredAether.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Registration {
        @SubscribeEvent
        public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(AARegistries.SQUALL.get(), SquallRenderer::new);
        }

        private Registration() {
        }
    }
}
