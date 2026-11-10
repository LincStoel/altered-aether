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
 * The aerbison, from the Blockbench model (the parts are generated, see {@link AerbisonAnimations}). The keyframed
 * animations are blended by the entity's smoothed weights, each playing at its authored pace: walk while moving,
 * gallop while stampeding or moving faster than a stroll, idle while standing with nothing else to do, and the one-shots on top (lay_down and laying_idle are generated but not played
 * yet). The mane-sail, the landing thud, the charge and the head look are code.
 * The root, "bison", stands on the ground; the entity's left is +x, as in vanilla's quadrupeds.
 */
public class AerbisonModel extends HierarchicalModel<Aerbison> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(AlteredAether.id("aerbison"), "main");
    private static final float DEG = Mth.DEG_TO_RAD;

    private final Vector3f cache = new Vector3f();
    private final ModelPart root;
    private final ModelPart bison, mane, neck, head;
    private final ModelPart legBackLeft, legBackRight, legFrontLeft, legFrontRight;

    public AerbisonModel(ModelPart root) {
        this.root = root;
        bison = root.getChild("bison");
        mane = part("mane");
        neck = part("neck");
        head = part("head");
        legBackLeft = part("leg_back_left");
        legBackRight = part("leg_back_right");
        legFrontLeft = part("leg_front_left");
        legFrontRight = part("leg_front_right");
    }

    private ModelPart part(String name) {
        return getAnyDescendantWithName(name).orElseThrow(() -> new IllegalStateException("aerbison model has no " + name));
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        // BEGIN GENERATED MODEL (tools/blockbench/bbmodel_to_java.py; edit the .bbmodel, not this)
        PartDefinition bison = root.addOrReplaceChild("bison", CubeListBuilder.create(),
                PartPose.offset(0, 24, 6));
        PartDefinition bodyAndNeck = bison.addOrReplaceChild("body_and_neck", CubeListBuilder.create(),
                PartPose.offset(0, -14, -4));
        PartDefinition body = bodyAndNeck.addOrReplaceChild("body", CubeListBuilder.create(),
                PartPose.offsetAndRotation(0, 0, 0, 90 * DEG, 0, 0));
        body.addOrReplaceChild("rear_body", CubeListBuilder.create()
                .texOffs(90, 122).addBox(-10, -15.5F, -10.5F, 20, 24, 23),
                PartPose.offset(0, 17.5F, 5.5F));
        body.addOrReplaceChild("mane", CubeListBuilder.create()
                .texOffs(81, 61).addBox(-12, -9, -6, 24, 19, 28),
                PartPose.offset(0, 0, 0));
        PartDefinition neck = bodyAndNeck.addOrReplaceChild("neck", CubeListBuilder.create()
                .texOffs(2, 74).addBox(-5, -6, -21, 10, 10, 24),
                PartPose.offset(0, -6, 2));
        PartDefinition head = neck.addOrReplaceChild("head", CubeListBuilder.create()
                .texOffs(0, 1).addBox(-10, -8, -14, 20, 22, 16)
                .texOffs(5, 39).addBox(-10, 14, -14, 20, 5, 11),
                PartPose.offset(0, -6, -14));
        head.addOrReplaceChild("nose", CubeListBuilder.create()
                .texOffs(5, 58).addBox(-3, -1.5F, -2, 6, 9, 4)
                .texOffs(47, 61).addBox(-3, -1.5F, -1, 6, 5, 5),
                PartPose.offset(0, 12.5F, -16));
        PartDefinition horns = head.addOrReplaceChild("horns", CubeListBuilder.create(),
                PartPose.offsetAndRotation(-5, 1, -9, 90 * DEG, 0, 0));
        PartDefinition hornRight = horns.addOrReplaceChild("horn_right", CubeListBuilder.create()
                .texOffs(111, 23).addBox(-12, -26, -14, 4, 4, 4)
                .texOffs(110, 1).addBox(-12, -33, -10, 4, 11, 5)
                .texOffs(83, 22).addBox(-12, -38, -22, 4, 11, 6),
                PartPose.offset(3, 28, 20));
        hornRight.addOrReplaceChild("horn_right_r1", CubeListBuilder.create()
                .texOffs(84, 2).addBox(-12, -37, -20, 4, 11, 5),
                PartPose.offsetAndRotation(0, -53, 21, 90 * DEG, 0, 0));
        PartDefinition hornLeft = horns.addOrReplaceChild("horn_left", CubeListBuilder.create()
                .texOffs(111, 23).addBox(-12, -26, -14, 4, 4, 4)
                .texOffs(110, 1).addBox(-12, -33, -10, 4, 11, 5)
                .texOffs(83, 22).addBox(-12, -38, -22, 4, 11, 6),
                PartPose.offset(27, 28, 20));
        hornLeft.addOrReplaceChild("horn_left_r1", CubeListBuilder.create()
                .texOffs(84, 2).addBox(-12, -37, -20, 4, 11, 5),
                PartPose.offsetAndRotation(0, -53, 21, 90 * DEG, 0, 0));
        PartDefinition legBack = bison.addOrReplaceChild("leg_back", CubeListBuilder.create(),
                PartPose.offset(-6, -9, 14));
        legBack.addOrReplaceChild("leg_back_right", CubeListBuilder.create()
                .texOffs(42, 121).addBox(-3, -3, -3, 6, 12, 8),
                PartPose.offset(0, 0, 0));
        legBack.addOrReplaceChild("leg_back_left", CubeListBuilder.create()
                .texOffs(42, 121).addBox(-3, -3, -3, 6, 12, 8),
                PartPose.offset(12, 0, 0));
        PartDefinition frontLegs = bison.addOrReplaceChild("front_legs", CubeListBuilder.create(),
                PartPose.offset(-7, -12, -9));
        frontLegs.addOrReplaceChild("leg_front_right", CubeListBuilder.create()
                .texOffs(2, 120).addBox(-6, -1, -3, 8, 13, 8),
                PartPose.offset(0, 0, 0));
        frontLegs.addOrReplaceChild("leg_front_left", CubeListBuilder.create()
                .texOffs(2, 120).addBox(-2, -1, -3, 8, 13, 8),
                PartPose.offset(14, 0, 0));
        // END GENERATED MODEL
        return LayerDefinition.create(mesh, 192, 192);
    }

    @Override
    public ModelPart root() {
        return root;
    }

    private static float smooth(float t) {
        t = Mth.clamp(t, 0, 1);
        return t * t * (3 - 2 * t);
    }

    private void play(AnimationDefinition animation, float seconds, float weight) {
        if (weight > 0.001F) {
            KeyframeAnimations.animate(this, animation, (long) (seconds * 1000), weight, cache);
        }
    }

    @Override
    public void setupAnim(Aerbison e, float swing, float amount, float age, float yaw, float pitch) {
        root().getAllParts().forEach(part -> {
            part.resetPose();
            part.xScale = part.yScale = part.zScale = 1;
        });

        float partial = age - e.tickCount;
        float walking = smooth(Mth.lerp(partial, e.walkO, e.walk));
        float gallop = smooth(Mth.lerp(partial, e.gallopO, e.gallop));
        float charge = smooth(Mth.lerp(partial, e.chargeO, e.charge));
        float sail = smooth(Mth.lerp(partial, e.sailO, e.sail));
        float idle = smooth(Mth.lerp(partial, e.idleO, e.idle));
        float thud = Mth.lerp(partial, e.thudO, e.thud);
        float seconds = age / 20;

        // the walk plays at its authored pace and full stride whenever it's moving, as in Blockbench
        play(AerbisonAnimations.WALK, seconds, walking * (1 - gallop) * (1 - sail));
        play(AerbisonAnimations.GALLOP, seconds, gallop);
        play(AerbisonAnimations.IDLE, seconds, idle);

        // the head lowered to the horns when squaring up to something
        neck.xRot += 28 * DEG * charge * (1 - gallop);
        head.xRot += 14 * DEG * charge * (1 - gallop);

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
            // the mane is under the body, which is turned on its end: its x is the width, z the height
            float ripple = 0.02F * Mth.sin(age * 0.9F);
            mane.xScale += 0.12F * sail + ripple;
            mane.zScale += 0.12F * sail + ripple;
            mane.yScale += 0.06F * sail;
        }

        // landing thud
        if (thud > 0) {
            bison.y += 1.5F * thud;
            legFrontLeft.zRot -= 10 * DEG * thud;
            legBackLeft.zRot -= 10 * DEG * thud;
            legFrontRight.zRot += 10 * DEG * thud;
            legBackRight.zRot += 10 * DEG * thud;
        }

        // head look, held back by anything that owns the head
        float look = 1;
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
        animate(e.staggerAnim, AerbisonAnimations.STAGGER, age);
        animate(e.refuseAnim, AerbisonAnimations.REFUSE, age);
    }
}
