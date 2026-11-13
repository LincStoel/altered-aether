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
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import org.joml.Vector3f;

import java.util.Set;

/**
 * The gravitite golem, generated from its Blockbench model (see {@link GravititeGolemAnimations} for the command).
 * Seated, concentrating and floating are held, blended by the golem's smoothed weights; while it stands up or sits
 * down, that transition alone poses it, since each carries the whole body between sitting and floating. The one-shots
 * play over either. The code finds only the head by name.
 */
public class GravititeGolemModel extends HierarchicalModel<GravititeGolem> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(AlteredAether.id("gravitite_golem"), "main");
    private static final float DEG = Mth.DEG_TO_RAD;
    /** Seconds the repel's end pose is held, then eased out over; with its length they make GolemTiming.REPEL_TICKS. */
    private static final float REPEL_HOLD = 0.25F;
    private static final float REPEL_EASE = 0.35F;

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
        // BEGIN GENERATED MODEL (tools/blockbench/bbmodel_to_java.py; edit the Blockbench model, not this)
        PartDefinition all = root.addOrReplaceChild("all", CubeListBuilder.create(),
                PartPose.offset(0, 11, -1));
        PartDefinition legs = all.addOrReplaceChild("legs", CubeListBuilder.create(),
                PartPose.offset(3.5F, 6, 2));
        legs.addOrReplaceChild("leg_left", CubeListBuilder.create()
                .texOffs(15, 53).addBox(-4, -1, -3, 4, 8, 6),
                PartPose.offset(-7, 0, 0));
        legs.addOrReplaceChild("leg_right", CubeListBuilder.create()
                .texOffs(93, 53).mirror().addBox(0, -1, -3, 4, 8, 6).mirror(false),
                PartPose.offset(0, 0, 0));
        PartDefinition arms = all.addOrReplaceChild("arms", CubeListBuilder.create(),
                PartPose.offset(0, -5, 1));
        arms.addOrReplaceChild("arm_left", CubeListBuilder.create()
                .texOffs(15, 26).addBox(-4, -2, -3, 4, 11, 6),
                PartPose.offset(-7, 0, 0));
        arms.addOrReplaceChild("arm_right", CubeListBuilder.create()
                .texOffs(93, 25).addBox(0, -2, -3, 4, 11, 6),
                PartPose.offset(7, 0, 0));
        PartDefinition head = all.addOrReplaceChild("head", CubeListBuilder.create()
                .texOffs(42, 3).addBox(-6, -9, -7, 12, 14, 10),
                PartPose.offset(0, -6, 1.5F));
        head.addOrReplaceChild("nose", CubeListBuilder.create()
                .texOffs(55, 29).addBox(-3, -1, -3, 6, 8, 3),
                PartPose.offset(0, -1, -7));
        head.addOrReplaceChild("gem-protrusion", CubeListBuilder.create()
                .texOffs(56, 12).addBox(-1, -2, -1.5F, 2, 4, 1, Set.of(Direction.NORTH))
                .texOffs(57, 12).addBox(-1, -2, -1.5F, 2, 4, 1, Set.of(Direction.WEST))
                .texOffs(53, 12).addBox(-1, -2, -1.5F, 2, 4, 1, Set.of(Direction.SOUTH))
                .texOffs(55, 12).addBox(-1, -2, -1.5F, 2, 4, 1, Set.of(Direction.EAST))
                .texOffs(56, 13).addBox(-1, -2, -1.5F, 2, 4, 1, Set.of(Direction.DOWN))
                .texOffs(54, 16).addBox(-1, -2, -1.5F, 2, 4, 1, Set.of(Direction.UP))
                .texOffs(55, 13).addBox(-2, -1, -1.5F, 4, 2, 1, Set.of(Direction.NORTH))
                .texOffs(56, 13).addBox(-2, -1, -1.5F, 4, 2, 1, Set.of(Direction.WEST))
                .texOffs(50, 13).addBox(-2, -1, -1.5F, 4, 2, 1, Set.of(Direction.SOUTH))
                .texOffs(54, 13).addBox(-2, -1, -1.5F, 4, 2, 1, Set.of(Direction.EAST))
                .texOffs(55, 14).addBox(-2, -1, -1.5F, 4, 2, 1, Set.of(Direction.DOWN))
                .texOffs(51, 15).addBox(-2, -1, -1.5F, 4, 2, 1, Set.of(Direction.UP)),
                PartPose.offset(0, -7, -6.5F));
        PartDefinition body = all.addOrReplaceChild("body", CubeListBuilder.create(),
                PartPose.offset(0, 1.75F, 4.5F));
        body.addOrReplaceChild("body_bottom", CubeListBuilder.create()
                .texOffs(42, 70).addBox(-5.5F, -2, -6, 11, 7, 11, new CubeDeformation(0.5F)),
                PartPose.offset(0, 2.25F, 0.5F));
        body.addOrReplaceChild("body_top", CubeListBuilder.create()
                .texOffs(37, 42).addBox(-7, -12, -7, 14, 12, 13),
                PartPose.offset(0, 2.25F, 0.5F));
        // END GENERATED MODEL
        return LayerDefinition.create(mesh, 128, 128);
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
        float look = 0;
        if (golem.standUpAnim.isStarted() || golem.sitDownAnim.isStarted()) {
            animate(golem.standUpAnim, GravititeGolemAnimations.STAND_UP, age);
            animate(golem.sitDownAnim, GravititeGolemAnimations.SIT_DOWN, age);
        } else {
            float partial = age - golem.tickCount;
            float seated = smooth(Mth.lerp(partial, golem.meditateO, golem.meditate));
            float concentrate = smooth(Mth.lerp(partial, golem.concentrateO, golem.concentrate));
            float floating = smooth(Mth.lerp(partial, golem.floatingO, golem.floating));
            play(GravititeGolemAnimations.SEATED, 0, seated);
            play(GravititeGolemAnimations.CONCENTRATING, age / 20, concentrate);
            play(GravititeGolemAnimations.FLOATING, (age - golem.floatSince) / 20, floating);
            // it looks about only while it's up and about
            look = floating;
        }
        head.yRot += Mth.clamp(yaw, -40, 40) * DEG * look;
        head.xRot += pitch * DEG * 0.8F * look;

        golem.repelAnim.updateTime(age, 1);
        golem.repelAnim.ifStarted(state -> {
            // it ends with the arms thrust up: hold them there a moment, then let them settle back
            float t = state.getAccumulatedTime() / 1000F;
            float out = (t - GravititeGolemAnimations.REPEL.lengthInSeconds() - REPEL_HOLD) / REPEL_EASE;
            play(GravititeGolemAnimations.REPEL, t, 1 - smooth(out));
        });
        animate(golem.shakeHeadAnim, GravititeGolemAnimations.SHAKE_HEAD, age);
        animate(golem.linkPulseAnim, GravititeGolemAnimations.LINK_PULSE, age);
    }
}
