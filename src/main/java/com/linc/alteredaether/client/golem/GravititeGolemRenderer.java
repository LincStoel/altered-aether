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
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * Draws the gravitite golem, its glowing gravitite (every purple pixel fullbright, the nose brighter still, all dark
 * while it rests), and the items it has in the air, which ride along with the golem so shaderpacks treat them as part
 * of it. A dyed golem's gravitite is drawn again over the purple from grey masks tinted its colour. The glow textures
 * and masks come from tools/textures/gravitite_golem_glow.py.
 */
public class GravititeGolemRenderer extends MobRenderer<GravititeGolem, GravititeGolemModel> {
    private static final String DIR = "textures/entity/mobs/gravitite_golem/";
    private static final ResourceLocation TEXTURE = AlteredAether.id(DIR + "gravitite_golem.png");
    /** The purple pixels drawn unlit over the body, in their own colours, and as a grey mask to tint. */
    private static final RenderType GLOW = RenderType.entityTranslucentEmissive(AlteredAether.id(DIR + "gravitite_golem_glow.png"));
    private static final RenderType GLOW_TINT = RenderType.entityTranslucentEmissive(AlteredAether.id(DIR + "gravitite_golem_glow_tint.png"));
    /** The nose's purple added on top again, so it outshines the rest. */
    private static final RenderType NOSE_GLOW = RenderType.eyes(AlteredAether.id(DIR + "gravitite_golem_nose_glow.png"));
    private static final RenderType NOSE_GLOW_TINT = RenderType.eyes(AlteredAether.id(DIR + "gravitite_golem_nose_glow_tint.png"));
    /** The purple pixels as a grey mask, lit like the body: a dyed golem's gravitite while it rests and its glow is out. */
    private static final RenderType BODY_TINT = RenderType.entityCutoutNoCull(AlteredAether.id(DIR + "gravitite_golem_tint.png"));
    /** Gravitite's own purple, what an undyed (magenta) golem and its pebble show. */
    private static final int GRAVITITE_RGB = 0xE072D7;

    public GravititeGolemRenderer(EntityRendererProvider.Context context) {
        super(context, new GravititeGolemModel(context.bakeLayer(GravititeGolemModel.LAYER)), 0.5F);
        addLayer(new Tint(this));
        addLayer(new Glow(this, GLOW, GLOW_TINT));
        addLayer(new Glow(this, NOSE_GLOW, NOSE_GLOW_TINT));
    }

    /** The colour a dye tints gravitite: magenta is gravitite's own purple. */
    public static int tint(int dyeRgb) {
        return dyeRgb == GravititeGolem.DEFAULT_COLOR.getTextureDiffuseColor() ? GRAVITITE_RGB : dyeRgb & 0xFFFFFF;
    }

    /** The colour {@code golem}'s gravitite is tinted, or -1 for none (it is gravitite's own colour). */
    private static int tintOf(GravititeGolem golem) {
        return golem.color() == GravititeGolem.DEFAULT_COLOR ? -1 : 0xFF000000 | tint(golem.color().getTextureDiffuseColor());
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

    /** A dyed golem's gravitite, lit as the body is, under its glow. */
    private static final class Tint extends RenderLayer<GravititeGolem, GravititeGolemModel> {
        Tint(RenderLayerParent<GravititeGolem, GravititeGolemModel> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack poseStack, MultiBufferSource buffers, int light, GravititeGolem golem, float swing,
                           float amount, float partial, float age, float yaw, float pitch) {
            int color = tintOf(golem);
            if (color != -1 && !golem.isInvisible()) {
                getParentModel().renderToBuffer(poseStack, buffers.getBuffer(BODY_TINT), light,
                        LivingEntityRenderer.getOverlayCoords(golem, 0), color);
            }
        }
    }

    /** A glow drawn fullbright over the body, in gravitite's colour or the golem's dye; dark while it rests. */
    private static final class Glow extends RenderLayer<GravititeGolem, GravititeGolemModel> {
        private final RenderType own, tinted;

        Glow(RenderLayerParent<GravititeGolem, GravititeGolemModel> parent, RenderType own, RenderType tinted) {
            super(parent);
            this.own = own;
            this.tinted = tinted;
        }

        @Override
        public void render(PoseStack poseStack, MultiBufferSource buffers, int light, GravititeGolem golem, float swing,
                           float amount, float partial, float age, float yaw, float pitch) {
            if (golem.isPaused() || golem.isRefused() || golem.isInvisible()) {
                return;
            }
            int color = tintOf(golem);
            getParentModel().renderToBuffer(poseStack, buffers.getBuffer(color == -1 ? own : tinted), 0xF000F0,
                    OverlayTexture.NO_OVERLAY, color);
        }
    }
}
