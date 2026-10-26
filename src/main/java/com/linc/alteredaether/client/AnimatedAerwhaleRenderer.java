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
import com.aetherteam.aether.client.renderer.entity.AerwhaleRenderer;
import com.aetherteam.aether.client.renderer.entity.model.AerwhaleModel;
import com.aetherteam.aether.client.renderer.entity.model.ClassicAerwhaleModel;
import com.aetherteam.aether.entity.passive.Aerwhale;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

/**
 * The Aether's aerwhale renderer, legacy switch and all, drawing our animated models instead of its still ones, and a
 * calf's model for a calf. Used for wild aerwhales too, unless Deep Aether's aerwhales are in play.
 */
public class AnimatedAerwhaleRenderer extends AerwhaleRenderer {
    private final AnimatedAerwhaleModel model;
    private final AerwhaleCalfModel calfModel;
    private final AnimatedClassicAerwhaleModel classicModel;
    /** Whether the whale being drawn is a calf, for the model getters, which aren't told which whale it is. */
    private boolean calf;

    public AnimatedAerwhaleRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = new AnimatedAerwhaleModel(context.bakeLayer(AetherModelLayers.AERWHALE));
        this.calfModel = new AerwhaleCalfModel(context.bakeLayer(AerwhaleCalfModel.LAYER));
        this.classicModel = new AnimatedClassicAerwhaleModel(context.bakeLayer(AetherModelLayers.AERWHALE_CLASSIC));
    }

    @Override
    public void render(Aerwhale whale, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffer,
                       int packedLight) {
        calf = whale.isBaby();
        super.render(whale, yaw, partialTick, poseStack, buffer, packedLight);
    }

    @Override
    public AerwhaleModel getDefaultModel() {
        return calf ? calfModel : model;
    }

    @Override
    public ClassicAerwhaleModel getOldModel() {
        return classicModel;
    }
}
