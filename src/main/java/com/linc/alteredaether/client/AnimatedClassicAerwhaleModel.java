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

import com.aetherteam.aether.client.renderer.entity.model.ClassicAerwhaleModel;
import com.aetherteam.aether.entity.passive.Aerwhale;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * The Aether's b1.7.3 aerwhale model, animated like {@link AnimatedAerwhaleModel}. Its parts are separate already, so a
 * calf just scales them: a bigger head, and a smaller body drawn up close behind it.
 */
public final class AnimatedClassicAerwhaleModel extends ClassicAerwhaleModel {
    private static final float HEAD_SCALE = 1.3F;
    private static final float BODY_SCALE = 0.8F;

    public AnimatedClassicAerwhaleModel(ModelPart root) {
        super(root);
    }

    @Override
    public void setupAnim(Aerwhale whale, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw,
                          float headPitch) {
        for (ModelPart part : new ModelPart[]{head, middleBody, backBody, leftFin, rightFin, backFinLeft, backFinRight}) {
            part.resetPose();
        }
        float effort = 1 + Math.min(limbSwingAmount, 1);
        float paddle = Mth.sin(ageInTicks * 0.09F) * 0.1F * effort;
        float stroke = Mth.sin(ageInTicks * 0.07F + 1) * 0.08F * effort;
        leftFin.zRot += paddle;
        rightFin.zRot -= paddle;
        // the flukes lie along x, turned out to either side, so they flap about z like the fins
        backFinLeft.zRot += stroke;
        backFinRight.zRot -= stroke;
        if (whale.isBaby()) {
            scale(head, HEAD_SCALE);
            leftFin.x *= HEAD_SCALE;
            rightFin.x *= HEAD_SCALE;
            // the middle hangs from just behind the head; the back, and the flukes with it, from the middle's end
            scale(middleBody, BODY_SCALE);
            middleBody.z = 16 * HEAD_SCALE - 2;
            float backZ = middleBody.z + (1 + 15) * BODY_SCALE + 8.5F * BODY_SCALE;
            scale(backBody, BODY_SCALE);
            backBody.z = backZ;
            scale(backFinLeft, BODY_SCALE);
            scale(backFinRight, BODY_SCALE);
            backFinLeft.z = backZ;
            backFinRight.z = backZ;
        }
    }

    private static void scale(ModelPart part, float scale) {
        part.xScale = scale;
        part.yScale = scale;
        part.zScale = scale;
    }
}
