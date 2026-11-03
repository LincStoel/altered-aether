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

import com.linc.alteredaether.AlteredAether;
import com.linc.alteredaether.entity.Aerbison;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

/** The aerbison's renderer: its model and texture, and a calf drawn at half size. */
public class AerbisonRenderer extends MobRenderer<Aerbison, AerbisonModel> {
    private static final ResourceLocation TEXTURE = AlteredAether.id("textures/entity/mobs/aerbison/aerbison.png");

    public AerbisonRenderer(EntityRendererProvider.Context context) {
        super(context, new AerbisonModel(context.bakeLayer(AerbisonModel.LAYER)), 1.0F);
    }

    @Override
    public ResourceLocation getTextureLocation(Aerbison bison) {
        return TEXTURE;
    }

    @Override
    protected void scale(Aerbison bison, PoseStack poseStack, float partialTick) {
        if (bison.isBaby()) {
            poseStack.scale(0.55F, 0.55F, 0.55F);
            shadowRadius = 0.55F;
        } else {
            shadowRadius = 1.0F;
        }
    }
}
