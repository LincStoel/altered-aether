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
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.resources.ResourceLocation;

/**
 * Draws the gravitite golem, its glowing eyes (dark while redstone holds it still), and the items it has in the air,
 * which ride along with the golem so shaderpacks treat them as part of it.
 */
public class GravititeGolemRenderer extends MobRenderer<GravititeGolem, GravititeGolemModel> {
    private static final ResourceLocation TEXTURE = AlteredAether.id("textures/entity/mobs/gravitite_golem/gravitite_golem.png");
    private static final RenderType EYES = RenderType.eyes(
            AlteredAether.id("textures/entity/mobs/gravitite_golem/gravitite_golem_eyes.png"));

    public GravititeGolemRenderer(EntityRendererProvider.Context context) {
        super(context, new GravititeGolemModel(context.bakeLayer(GravititeGolemModel.LAYER)), 0.35F);
        addLayer(new Eyes(this));
    }

    @Override
    public ResourceLocation getTextureLocation(GravititeGolem golem) {
        return TEXTURE;
    }

    @Override
    public void render(GravititeGolem golem, float yaw, float partial, PoseStack poseStack, MultiBufferSource buffers, int light) {
        super.render(golem, yaw, partial, poseStack, buffers, light);
        GolemFlights.render(golem, partial, poseStack, buffers);
    }

    private static final class Eyes extends EyesLayer<GravititeGolem, GravititeGolemModel> {
        Eyes(RenderLayerParent<GravititeGolem, GravititeGolemModel> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack poseStack, MultiBufferSource buffers, int light, GravititeGolem golem, float swing,
                           float amount, float partial, float age, float yaw, float pitch) {
            if (!golem.isPaused()) {
                super.render(poseStack, buffers, light, golem, swing, amount, partial, age, yaw, pitch);
            }
        }

        @Override
        public RenderType renderType() {
            return EYES;
        }
    }
}
