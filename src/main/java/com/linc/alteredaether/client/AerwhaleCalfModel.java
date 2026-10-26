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
import com.linc.alteredaether.AlteredAether;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * A calf on the Aether's aerwhale model: the same boxes and texture, but with a big head on a short body, the way baby
 * animals are drawn. The Aether's model is one part with everything hung from it, so the calf's layout splits it at the
 * back of the head, where the body joins: a "front" part with the head and flippers, and a "trunk" with the middle,
 * dorsal fin, back and flukes. Both pivot on that join, so the head grows forward and up from it and the body shrinks
 * back toward it.
 */
public final class AerwhaleCalfModel extends AnimatedAerwhaleModel {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(AlteredAether.id("aerwhale"), "calf");
    private static final float HEAD_SCALE = 1.3F;
    private static final float BODY_SCALE = 0.75F;

    private final ModelPart front;
    private final ModelPart trunk;

    public AerwhaleCalfModel(ModelPart root) {
        this(root, root.getChild("head"));
    }

    private AerwhaleCalfModel(ModelPart root, ModelPart body) {
        super(root, body.getChild("front"), body.getChild("trunk"));
        this.front = body.getChild("front");
        this.trunk = body.getChild("trunk");
    }

    /**
     * The Aether's {@code AerwhaleModel.createBodyLayer}, box for box and with the same texture offsets, regrouped
     * about the join at z -3 of its body part; every position below is the Aether's moved by +3 in z.
     */
    public static LayerDefinition createCalfLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition body = mesh.getRoot().addOrReplaceChild("head", CubeListBuilder.create(),
                PartPose.offsetAndRotation(0, 24, 0, -0.0436F, 0, 0));
        PartDefinition front = body.addOrReplaceChild("front", CubeListBuilder.create()
                        .texOffs(104, 0).addBox(-13, -6, -29, 26, 6, 30)
                        .texOffs(0, 0).addBox(-12, -19, -28, 24, 18, 28),
                PartPose.offset(0, 0, -3));
        front.addOrReplaceChild("flipper_right", CubeListBuilder.create()
                        .texOffs(104, 94).mirror().addBox(-19, 2, 0, 19, 3, 14).mirror(false),
                PartPose.offsetAndRotation(-12, -11, -9, -0.1309F, 0.2618F, -0.0873F));
        front.addOrReplaceChild("flipper_left", CubeListBuilder.create()
                        .texOffs(104, 94).addBox(0, 2, 0, 19, 3, 14),
                PartPose.offsetAndRotation(12, -11, -9, -0.1309F, -0.2618F, 0.0873F));
        PartDefinition trunk = body.addOrReplaceChild("trunk", CubeListBuilder.create()
                        .texOffs(104, 36).addBox(-12, -6, -1, 24, 6, 26),
                PartPose.offset(0, 0, -3));
        trunk.addOrReplaceChild("fin", CubeListBuilder.create()
                        .texOffs(0, 46).addBox(-2, -5, 0, 2, 7, 8),
                PartPose.offsetAndRotation(1, -16.5F, 7, -0.1309F, 0, 0));
        trunk.addOrReplaceChild("middle_top", CubeListBuilder.create()
                        .texOffs(0, 46).addBox(1, 2, 0.5F, 22, 14, 25),
                PartPose.offsetAndRotation(-12, -19, -1, -0.0873F, 0, 0));
        trunk.addOrReplaceChild("tail_right", CubeListBuilder.create()
                        .texOffs(170, 84).mirror().addBox(0, 2, 0, 15, 3, 24).mirror(false),
                PartPose.offsetAndRotation(-8.5F, -7, 33, -0.1309F, -0.7854F, -0.0436F));
        trunk.addOrReplaceChild("tail_left", CubeListBuilder.create()
                        .texOffs(170, 84).addBox(-15, 2, 0, 15, 3, 24),
                PartPose.offsetAndRotation(8.5F, -7, 33, -0.1309F, 0.7854F, 0.0436F));
        trunk.addOrReplaceChild("back", CubeListBuilder.create()
                        .texOffs(104, 68).addBox(2, 11, 2.5F, 19, 5, 21)
                        .texOffs(0, 85).addBox(3, 2, 1, 17, 9, 22),
                PartPose.offsetAndRotation(-11.5F, -17, 23, -0.2182F, 0, 0));
        return LayerDefinition.create(mesh, 256, 128);
    }

    @Override
    public void setupAnim(Aerwhale whale, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw,
                          float headPitch) {
        super.setupAnim(whale, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        front.xScale = front.yScale = front.zScale = HEAD_SCALE;
        trunk.xScale = trunk.yScale = trunk.zScale = BODY_SCALE;
    }
}
