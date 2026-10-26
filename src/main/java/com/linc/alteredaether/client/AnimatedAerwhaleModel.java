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

import com.aetherteam.aether.client.renderer.entity.model.AerwhaleModel;
import com.aetherteam.aether.entity.passive.Aerwhale;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * The Aether's aerwhale model, which stands still, brought to life: its flippers paddle slowly, its flukes rise and
 * fall, and its dorsal fin sways, all a little harder as it picks up speed.
 */
public class AnimatedAerwhaleModel extends AerwhaleModel {
    private final ModelPart flipperLeft;
    private final ModelPart flipperRight;
    private final ModelPart tailLeft;
    private final ModelPart tailRight;
    private final ModelPart fin;

    /** The Aether's own layout, where every part hangs off the body, which it calls the head. */
    public AnimatedAerwhaleModel(ModelPart root) {
        this(root, null, null);
    }

    /** For a layout that hangs the flippers and the tail from parts of their own, as the calf's does. */
    protected AnimatedAerwhaleModel(ModelPart root, ModelPart flippers, ModelPart tail) {
        super(root);
        ModelPart front = flippers != null ? flippers : head;
        ModelPart back = tail != null ? tail : head;
        this.flipperLeft = front.getChild("flipper_left");
        this.flipperRight = front.getChild("flipper_right");
        this.tailLeft = back.getChild("tail_left");
        this.tailRight = back.getChild("tail_right");
        this.fin = back.getChild("fin");
    }

    @Override
    public void setupAnim(Aerwhale whale, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw,
                          float headPitch) {
        float effort = 1 + Math.min(limbSwingAmount, 1);
        float paddle = Mth.sin(ageInTicks * 0.09F) * 0.1F * effort;
        float stroke = Mth.sin(ageInTicks * 0.07F + 1) * 0.08F * effort;
        flipperLeft.resetPose();
        flipperRight.resetPose();
        tailLeft.resetPose();
        tailRight.resetPose();
        fin.resetPose();
        flipperLeft.zRot += paddle;
        flipperRight.zRot -= paddle;
        tailLeft.xRot += stroke;
        tailRight.xRot += stroke;
        fin.zRot += Mth.sin(ageInTicks * 0.05F) * 0.04F;
    }
}
