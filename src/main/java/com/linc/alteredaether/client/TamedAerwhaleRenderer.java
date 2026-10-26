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

import com.aetherteam.aether.entity.passive.Aerwhale;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

/** The animated aerwhale renderer, plus our saddle and saddlebags, and the calves' size. */
public final class TamedAerwhaleRenderer extends AnimatedAerwhaleRenderer {
    public TamedAerwhaleRenderer(EntityRendererProvider.Context context) {
        super(context);
        addLayer(new AerwhaleSaddleLayer(this, context.getModelSet()));
    }

    @Override
    protected void scale(Aerwhale whale, PoseStack poseStack, float partialTick) {
        if (whale.isBaby()) {
            poseStack.scale(0.5F, 0.5F, 0.5F);
        }
        super.scale(whale, poseStack, partialTick);
    }
}
