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
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.animation.KeyframeAnimations;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;
import org.joml.Vector3f;

/**
 * The gravitite golem, a placeholder until its Blockbench model comes: a thin holystone body, a big gravitite head,
 * spindly limbs. The parts are found by name, so a generated model (between the markers) only needs the same names:
 * golem (the root, on the ground), legs, leg_left, leg_right, body, head, arm_left, arm_right. The held animations
 * are blended by the golem's smoothed weights, each at its own pace, and the one-shots play over them.
 */
public class GravititeGolemModel extends HierarchicalModel<GravititeGolem> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(AlteredAether.id("gravitite_golem"), "main");
    private static final float DEG = Mth.DEG_TO_RAD;

    private final Vector3f cache = new Vector3f();
    private final ModelPart root;
    private final ModelPart head;

    public GravititeGolemModel(ModelPart root) {
        this.root = root;
        head = part("head");
    }

    private ModelPart part(String name) {
        return getAnyDescendantWithName(name).orElseThrow(() -> new IllegalStateException("gravitite golem model has no " + name));
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        // BEGIN GENERATED MODEL (tools/blockbench/bbmodel_to_java.py; edit the .bbmodel, not this)
        PartDefinition golem = root.addOrReplaceChild("golem", CubeListBuilder.create(), PartPose.offset(0, 24, 0));
        PartDefinition legs = golem.addOrReplaceChild("legs", CubeListBuilder.create(), PartPose.offset(0, -7, 0));
        legs.addOrReplaceChild("leg_left", CubeListBuilder.create().texOffs(0, 40)
                .addBox(-1, 0, -1, 2, 7, 2), PartPose.offset(1.5F, 0, 0));
        legs.addOrReplaceChild("leg_right", CubeListBuilder.create().texOffs(8, 40)
                .addBox(-1, 0, -1, 2, 7, 2), PartPose.offset(-1.5F, 0, 0));
        PartDefinition body = golem.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 28)
                .addBox(-2.5F, -7, -1.5F, 5, 7, 3), PartPose.offset(0, -7, 0));
        body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-4.5F, -9, -4.5F, 9, 9, 9), PartPose.offset(0, -7, 0));
        body.addOrReplaceChild("arm_left", CubeListBuilder.create().texOffs(16, 40)
                .addBox(-0.5F, -0.5F, -0.5F, 1, 7, 1), PartPose.offset(3, -6.5F, 0));
        body.addOrReplaceChild("arm_right", CubeListBuilder.create().texOffs(20, 40)
                .addBox(-0.5F, -0.5F, -0.5F, 1, 7, 1), PartPose.offset(-3, -6.5F, 0));
        // END GENERATED MODEL
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public ModelPart root() {
        return root;
    }

    private void play(AnimationDefinition animation, float seconds, float weight) {
        if (weight > 0.001F) {
            KeyframeAnimations.animate(this, animation, (long) (seconds * 1000), weight, cache);
        }
    }

    private static float smooth(float t) {
        t = Mth.clamp(t, 0, 1);
        return t * t * (3 - 2 * t);
    }

    @Override
    public void setupAnim(GravititeGolem golem, float swing, float amount, float age, float yaw, float pitch) {
        root().getAllParts().forEach(part -> {
            part.resetPose();
            part.xScale = part.yScale = part.zScale = 1;
        });
        float partial = age - golem.tickCount;
        float meditate = smooth(Mth.lerp(partial, golem.meditateO, golem.meditate));
        float concentrate = smooth(Mth.lerp(partial, golem.concentrateO, golem.concentrate));
        float walk = smooth(Mth.lerp(partial, golem.walkO, golem.walk));
        float levitate = smooth(Mth.lerp(partial, golem.levitateO, golem.levitate));
        float seconds = age / 20;

        play(GravititeGolemAnimations.MEDITATE, seconds, meditate);
        play(GravititeGolemAnimations.CONCENTRATE, seconds, concentrate);
        play(GravititeGolemAnimations.WALK, seconds, walk);
        play(GravititeGolemAnimations.LEVITATE, seconds, levitate);

        // looks about only while it's up and about
        float look = 1 - Math.min(1, meditate + concentrate);
        head.yRot += Mth.clamp(yaw, -40, 40) * DEG * look;
        head.xRot += pitch * DEG * 0.8F * look;

        animate(golem.sitDownAnim, GravititeGolemAnimations.SIT_DOWN, age);
        animate(golem.standUpAnim, GravititeGolemAnimations.STAND_UP, age);
        animate(golem.repelAnim, GravititeGolemAnimations.REPEL, age);
        animate(golem.shakeHeadAnim, GravititeGolemAnimations.SHAKE_HEAD, age);
        animate(golem.linkPulseAnim, GravititeGolemAnimations.LINK_PULSE, age);
    }
}
