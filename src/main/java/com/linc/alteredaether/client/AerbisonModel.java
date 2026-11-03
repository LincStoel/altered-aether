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
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * The aerbison, from the Blockbench model. Walking, galloping, sailing down, lying and rising are blended in code
 * from the entity's smoothed weights; the one-shots are keyframed in {@link AerbisonAnimations} and laid on top. The
 * body is one root part, "bison", standing on the ground, so whole-body poses (rearing, rolling, the gallop's pitch)
 * pivot about its feet. The hump is a part of its own, "mane", for the flare of the sail. Left is +x, as in the model.
 */
public class AerbisonModel extends HierarchicalModel<Aerbison> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(AlteredAether.id("aerbison"), "main");
    private static final float DEG = Mth.DEG_TO_RAD;

    private final ModelPart root;
    private final ModelPart bison, mane, neck, head;
    private final ModelPart legBackLeft, legBackRight, legFrontLeft, legFrontRight;

    public AerbisonModel(ModelPart root) {
        this.root = root;
        bison = root.getChild("bison");
        mane = bison.getChild("body").getChild("mane");
        neck = bison.getChild("neck");
        head = neck.getChild("head");
        legBackLeft = bison.getChild("leg_back_left");
        legBackRight = bison.getChild("leg_back_right");
        legFrontLeft = bison.getChild("leg_front_left");
        legFrontRight = bison.getChild("leg_front_right");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition bison = root.addOrReplaceChild("bison", CubeListBuilder.create(),
                PartPose.offset(0, 24, 6));
        PartDefinition body = bison.addOrReplaceChild("body", CubeListBuilder.create()
                .texOffs(0, 90).addBox(-8, 10, -6, 16, 15, 23),
                PartPose.offsetAndRotation(0, -14, -4, Mth.HALF_PI, 0, 0));
        PartDefinition mane = body.addOrReplaceChild("mane", CubeListBuilder.create()
                .texOffs(36, 39).addBox(-10, -9, -6, 20, 19, 26),
                PartPose.offset(0, 0, 0));
        PartDefinition neck = bison.addOrReplaceChild("neck", CubeListBuilder.create()
                .texOffs(72, 85).addBox(-5, -6, -15, 10, 10, 18),
                PartPose.offset(0, -20, -2));
        PartDefinition head = neck.addOrReplaceChild("head", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-8, -6, -14, 16, 20, 16)
                .texOffs(0, 0).addBox(-2, 12, -18, 4, 9, 4)
                .texOffs(5, 37).addBox(-8, 12, -14, 16, 7, 11),
                PartPose.offset(0, -3, -14));
        PartDefinition horns = head.addOrReplaceChild("horns", CubeListBuilder.create()
                .texOffs(66, 24).addBox(3, -10, -1, 2, 10, 4)
                .texOffs(94, 24).addBox(3, -6, 9, 2, 10, 4)
                .texOffs(109, 31).addBox(3, 1, 5, 2, 3, 4)
                .texOffs(109, 31).addBox(-15, 1, 5, 2, 3, 4)
                .texOffs(94, 24).addBox(-15, -6, 9, 2, 10, 4)
                .texOffs(66, 24).addBox(-15, -10, -1, 2, 10, 4),
                PartPose.offsetAndRotation(5, 1, -9, Mth.HALF_PI, 0, 0));
        PartDefinition horn_left_tip = horns.addOrReplaceChild("horn_left_tip", CubeListBuilder.create()
                .texOffs(80, 24).addBox(8, -37, -20, 2, 10, 4),
                PartPose.offsetAndRotation(-5, -26, 40, Mth.HALF_PI, 0, 0));
        PartDefinition horn_right_tip = horns.addOrReplaceChild("horn_right_tip", CubeListBuilder.create()
                .texOffs(80, 24).addBox(8, -37, -20, 2, 10, 4),
                PartPose.offsetAndRotation(-23, -26, 40, Mth.HALF_PI, 0, 0));
        PartDefinition leg_back_left = bison.addOrReplaceChild("leg_back_left", CubeListBuilder.create()
                .texOffs(100, 0).addBox(-3, -1, -4, 6, 10, 8),
                PartPose.offset(6, -9, 14));
        PartDefinition leg_back_right = bison.addOrReplaceChild("leg_back_right", CubeListBuilder.create()
                .texOffs(100, 0).addBox(-3, -1, -4, 6, 10, 8),
                PartPose.offset(-6, -9, 14));
        PartDefinition leg_front_left = bison.addOrReplaceChild("leg_front_left", CubeListBuilder.create()
                .texOffs(66, 0).addBox(-4, -1, -4, 8, 13, 8),
                PartPose.offset(7, -12, -9));
        PartDefinition leg_front_right = bison.addOrReplaceChild("leg_front_right", CubeListBuilder.create()
                .texOffs(66, 0).addBox(-4, -1, -4, 8, 13, 8),
                PartPose.offset(-7, -12, -9));
        return LayerDefinition.create(mesh, 128, 128);
    }

    @Override
    public ModelPart root() {
        return root;
    }

    private static float smooth(float t) {
        t = Mth.clamp(t, 0, 1);
        return t * t * (3 - 2 * t);
    }

    @Override
    public void setupAnim(Aerbison e, float swing, float amount, float age, float yaw, float pitch) {
        root().getAllParts().forEach(ModelPart::resetPose);
        mane.xScale = mane.yScale = mane.zScale = 1;
        neck.xScale = neck.yScale = neck.zScale = 1;
        head.xScale = head.yScale = head.zScale = 1;

        float partial = age - e.tickCount;
        float lying = Mth.lerp(partial, e.lieO, e.lie);
        float gallop = smooth(Mth.lerp(partial, e.gallopO, e.gallop));
        float charge = smooth(Mth.lerp(partial, e.chargeO, e.charge));
        float sail = smooth(Mth.lerp(partial, e.sailO, e.sail));
        float thud = Mth.lerp(partial, e.thudO, e.thud);

        // walking: the user's swing, diagonal pairs together, with the half-speed bob of the body and a sway of the head
        float walk = amount * (1 - gallop) * (1 - sail) * (1 - lying);
        float stride = swing * 0.6662F;
        float swingAngle = 45 * DEG * Mth.cos(stride) * walk;
        legBackLeft.xRot += swingAngle;
        legFrontRight.xRot += swingAngle;
        legBackRight.xRot -= swingAngle;
        legFrontLeft.xRot -= swingAngle;
        bison.y += 2 * Mth.cos(swing * 0.3331F) * walk;
        head.yRot += 2 * DEG * Mth.cos(stride) * walk;

        // galloping: a bound on its own phase, since a stampede sets the velocity itself
        if (gallop > 0) {
            float phase = age * 0.55F;
            legFrontLeft.xRot += 60 * DEG * Mth.sin(phase + 0.3F) * gallop;
            legFrontRight.xRot += 60 * DEG * Mth.sin(phase) * gallop;
            legBackLeft.xRot += 60 * DEG * Mth.sin(phase + 2.5F) * gallop;
            legBackRight.xRot += 60 * DEG * Mth.sin(phase + 2.2F) * gallop;
            bison.xRot += 5 * DEG * Mth.sin(phase + 1.1F) * gallop;
            bison.y -= 1.5F * Math.abs(Mth.sin(phase)) * gallop;
            neck.xRot += 4 * DEG * Mth.sin(phase + 0.6F) * gallop;
        }
        neck.xRot += 28 * DEG * charge;
        head.xRot += 14 * DEG * charge;

        // sailing: legs stretched out like a leaping deer, and the mane flared like a canopy
        if (sail > 0) {
            float paddle = 7 * DEG * Mth.sin(age * 0.15F);
            legFrontLeft.xRot += -40 * DEG * sail + paddle * sail;
            legFrontRight.xRot += -40 * DEG * sail - paddle * sail;
            legBackLeft.xRot += 40 * DEG * sail - paddle * sail;
            legBackRight.xRot += 40 * DEG * sail + paddle * sail;
            legFrontLeft.zRot -= 14 * DEG * sail;
            legBackLeft.zRot -= 14 * DEG * sail;
            legFrontRight.zRot += 14 * DEG * sail;
            legBackRight.zRot += 14 * DEG * sail;
            head.xRot -= 12 * DEG * sail;
            float ripple = 0.02F * Mth.sin(age * 0.9F);
            mane.xScale += 0.12F * sail + ripple;
            mane.zScale += 0.12F * sail + ripple;
            mane.yScale += 0.06F * sail;
        }

        // lying: the knees fold front first, then the hind legs; reversed, the hind end rises first
        if (lying > 0) {
            float front = smooth(lying / 0.6F);
            float back = smooth((lying - 0.4F) / 0.6F);
            legFrontLeft.xRot += 88 * DEG * front;
            legFrontRight.xRot += 88 * DEG * front;
            legBackLeft.xRot -= 88 * DEG * back;
            legBackRight.xRot -= 88 * DEG * back;
            bison.xRot += 14 * DEG * front * (1 - back);
            bison.y += 3 * front + 4 * back;
            neck.xRot += 8 * DEG * lying;
            float lungs = 0.03F * Mth.sin(age * 0.06F) * lying;
            mane.xScale += lungs;
            mane.zScale += lungs;
        }

        // landing thud
        if (thud > 0) {
            bison.y += 1.5F * thud;
            legFrontLeft.zRot -= 10 * DEG * thud;
            legBackLeft.zRot -= 10 * DEG * thud;
            legFrontRight.zRot += 10 * DEG * thud;
            legBackRight.zRot += 10 * DEG * thud;
        }

        // idle breathing
        float breath = Mth.sin(age * 0.08F);
        bison.y += 0.15F * breath;
        mane.xScale += 0.012F * breath;
        mane.zScale += 0.012F * breath;

        // head look, held back by anything that owns the head
        float look = 1 - 0.5F * lying;
        if (e.isStaggering()) {
            look = 0;
        } else if (e.grazeAnim.isStarted() || e.hornTossAnim.isStarted()) {
            look *= 0.15F;
        }
        look *= 1 - 0.7F * gallop;
        neck.yRot += Mth.clamp(yaw, -40, 40) * DEG * 0.35F * look;
        head.yRot += Mth.clamp(yaw, -40, 40) * DEG * 0.65F * look;
        head.xRot += pitch * DEG * 0.8F * look;

        if (e.isBaby()) {
            head.xScale = head.yScale = head.zScale = 1.35F;
            neck.xScale = neck.yScale = neck.zScale = 1.15F;
        }

        animate(e.grazeAnim, AerbisonAnimations.GRAZE, age);
        animate(e.headShakeAnim, AerbisonAnimations.HEAD_SHAKE, age);
        animate(e.windupAnim, AerbisonAnimations.WINDUP, age);
        animate(e.rouseAnim, AerbisonAnimations.ROUSE, age);
        animate(e.stompAnim, AerbisonAnimations.STOMP, age);
        animate(e.hornTossAnim, AerbisonAnimations.HORN_TOSS, age);
        animate(e.wallowAnim, AerbisonAnimations.WALLOW, age);
        animate(e.staggerAnim, AerbisonAnimations.STAGGER, age);
        animate(e.refuseAnim, AerbisonAnimations.REFUSE, age);
    }
}
