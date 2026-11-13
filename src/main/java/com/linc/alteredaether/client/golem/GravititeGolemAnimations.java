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

import net.minecraft.client.animation.AnimationChannel;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.animation.Keyframe;
import net.minecraft.client.animation.KeyframeAnimations;

/**
 * The gravitite golem's animations. The generated block comes from the Blockbench export, regenerated with:
 * <pre>
 * python tools/blockbench/bbmodel_to_java.py E:/blockbench/gravitite_golem/gravitite_golem.geo.bbmodel
 *     --prefix animation.gravitite_golem.
 *     --model src/main/java/com/linc/alteredaether/client/golem/GravititeGolemModel.java
 *     --animations src/main/java/com/linc/alteredaether/client/golem/GravititeGolemAnimations.java
 * </pre>
 * SEATED, CONCENTRATING and FLOATING are held, blended by the golem's weights; SIT_DOWN and STAND_UP carry it whole
 * between seated and floating (their lengths are in entity/golem/GolemTiming). STANDING is Blockbench's rest pose,
 * unused. REPEL is Blockbench's too, and ends with the arms up: the model holds that a moment, then eases it out. The
 * one-shots after the generated block (shake_head, link_pulse) are still hand-written placeholders that play over
 * whatever is held; a Blockbench animation of the same name can replace each.
 */
public final class GravititeGolemAnimations {
    private static final AnimationChannel.Target ROT = AnimationChannel.Targets.ROTATION;
    private static final AnimationChannel.Target POS = AnimationChannel.Targets.POSITION;
    private static final AnimationChannel.Target SCALE = AnimationChannel.Targets.SCALE;

    // BEGIN GENERATED ANIMATIONS (tools/blockbench/bbmodel_to_java.py; edit the Blockbench model, not this)
    /** animation.gravitite_golem.concentrating, 0.2917 s, looping. */
    public static final AnimationDefinition CONCENTRATING = AnimationDefinition.Builder.withLength(0.2917F)
            .looping()
            .addAnimation("all", ch(POS, pl(0, 0, -4, 0)))
            .addAnimation("head", ch(ROT, rl(0, 15, 0, 0), rl(0.125F, 17.5F, 0, 0), rl(0.2917F, 15, 0, 0)))
            .addAnimation("nose", ch(ROT, rl(0, -14.9864F, 0.6469F, 0), rl(0.125F, -22.3774F, 1.936F, 0), rl(0.2083F, -12.4319F, 1.2915F, 0), rl(0.2917F, -14.9864F, 0.6469F, 0)))
            .addAnimation("nose", ch(POS, pl(0, 0, 0, 1)))
            .addAnimation("arms", ch(ROT, rl(0, -137.5F, 0, 0), rl(0.125F, -135, 0, 0), rl(0.2917F, -137.5F, 0, 0)))
            .addAnimation("arm_left", ch(ROT, rl(0, 0, 0, -7.5F)))
            .addAnimation("arm_right", ch(ROT, rl(0, 0, 0, 7.5F)))
            .addAnimation("legs", ch(ROT, rl(0, -90, 0, 0)))
            .addAnimation("legs", ch(POS, pl(0, 0, 0, -1)))
            .addAnimation("leg_left", ch(ROT, rl(0, 0, 0, -90)))
            .addAnimation("leg_left", ch(POS, pl(0, -3.5F, -2, 0)))
            .addAnimation("leg_right", ch(ROT, rl(0, 0, 0, 90)))
            .addAnimation("leg_right", ch(POS, pl(0, 3.5F, -2, 0)))
            .build();

    /** animation.gravitite_golem.seated, 0 s. */
    public static final AnimationDefinition SEATED = AnimationDefinition.Builder.withLength(0)
            .addAnimation("all", ch(POS, pl(0, 0, -4, 0)))
            .addAnimation("head", ch(ROT, rl(0, 0, 0, 0)))
            .addAnimation("arms", ch(ROT, rl(0, -25, 0, 0)))
            .addAnimation("arm_left", ch(ROT, rl(0, 0, 0, 0)))
            .addAnimation("arm_right", ch(ROT, rl(0, 0, 0, 0)))
            .addAnimation("legs", ch(ROT, rl(0, -90, 0, 0)))
            .addAnimation("legs", ch(POS, pl(0, 0, 0, -1)))
            .addAnimation("leg_left", ch(ROT, rl(0, 0, 0, -90)))
            .addAnimation("leg_left", ch(POS, pl(0, -3.5F, -2, 0)))
            .addAnimation("leg_right", ch(ROT, rl(0, 0, 0, 90)))
            .addAnimation("leg_right", ch(POS, pl(0, 3.5F, -2, 0)))
            .build();

    /** animation.gravitite_golem.floating, 4 s, looping. */
    public static final AnimationDefinition FLOATING = AnimationDefinition.Builder.withLength(4)
            .looping()
            .addAnimation("head", ch(ROT, rl(0, -10, 0, 0)))
            .addAnimation("arms", ch(ROT, rc(0, -2.5F, 0, 0), rc(2, -12.5F, 0, 0), rc(4, -2.5F, 0, 0)))
            .addAnimation("leg_left", ch(ROT, rc(0, 3, 0, 0), rc(1, -3, 0, 0), rc(2, 3, 0, 0), rc(3, -3, 0, 0), rc(4, 3, 0, 0)))
            .addAnimation("leg_right", ch(ROT, rc(0, -3, 0, 0), rc(1, 3, 0, 0), rc(2, -3, 0, 0), rc(3, 3, 0, 0), rc(4, -3, 0, 0)))
            .addAnimation("all", ch(ROT, rl(0, 10, -0.0001F, 0.0001F)))
            .addAnimation("all", ch(POS, pc(0, 0, 5, 0), pc(2, 0, 5.93F, 0), pc(4, 0, 5, 0)))
            .build();

    /** animation.gravitite_golem.sit_down, 4 s. */
    public static final AnimationDefinition SIT_DOWN = AnimationDefinition.Builder.withLength(4)
            .addAnimation("head", ch(ROT, rl(0, -10, 0, 0), rl(2, 0, 0, 0), rl(4, 0, 0, 0)))
            .addAnimation("head", ch(POS, pl(0, 0, 0, 0)))
            .addAnimation("arms", ch(ROT, rl(0, -2.5F, 0, 0), rl(2, 0, 0, 0), rl(4, -25, 0, 0)))
            .addAnimation("arms", ch(POS, pl(0, 0, 0, 0)))
            .addAnimation("arm_left", ch(ROT, rl(0, 0, 0, 0), rl(2, 0, 0, 0), rl(4, 0, 0, 0)))
            .addAnimation("arm_left", ch(POS, pl(0, 0, 0, 0), pl(2, 0, 0, 0)))
            .addAnimation("arm_right", ch(ROT, rl(2, 0, 0, 0), rl(4, 0, 0, 0)))
            .addAnimation("arm_right", ch(POS, pl(0, 0, 0, 0)))
            .addAnimation("legs", ch(ROT, rl(0, 0, 0, 0), rl(2, 0, 0, 0), rl(4, -90, 0, 0)))
            .addAnimation("legs", ch(POS, pl(0, 0, 0, 0), pl(2, 0, 0, 0), pl(4, 0, 0, -1)))
            .addAnimation("leg_left", ch(ROT, rl(0, 3, 0, 0), rl(2, 0, 0, 0), rl(4, 0, 0, -90)))
            .addAnimation("leg_left", ch(POS, pl(2, 0, 0, 0), pl(4, -3.5F, -2, 0)))
            .addAnimation("leg_right", ch(ROT, rl(0, -3, 0, 0), rl(2, 0, 0, 0), rl(4, 0, 0, 90)))
            .addAnimation("leg_right", ch(POS, pl(2, 0, 0, 0), pl(4, 3.5F, -2, 0)))
            .addAnimation("all", ch(ROT, rl(0, 10, -0.0001F, 0.0001F), rl(2, 0, 0, 0)))
            .addAnimation("all", ch(POS, pl(0, 0, 5, 0), pl(2, 0, 0, 0), pl(4, 0, -4, 0)))
            .build();

    /** animation.gravitite_golem.standing, 1 s, looping. */
    public static final AnimationDefinition STANDING = AnimationDefinition.Builder.withLength(1)
            .looping()
            .addAnimation("body", ch(ROT, rl(0, 0, 0, 0)))
            .addAnimation("body", ch(POS, pl(0, 0, 0, 0)))
            .addAnimation("body_bottom", ch(ROT, rl(0, 0, 0, 0)))
            .addAnimation("body_bottom", ch(POS, pl(0, 0, 0, 0)))
            .addAnimation("body_top", ch(ROT, rl(0, 0, 0, 0)))
            .addAnimation("body_top", ch(POS, pl(0, 0, 0, 0)))
            .addAnimation("head", ch(ROT, rl(0, 0, 0, 0)))
            .addAnimation("head", ch(POS, pl(0, 0, 0, 0)))
            .addAnimation("nose", ch(ROT, rl(0, 0, 0, 0)))
            .addAnimation("nose", ch(POS, pl(0, 0, 0, 0)))
            .addAnimation("arms", ch(ROT, rl(0, 0, 0, 0)))
            .addAnimation("arms", ch(POS, pl(0, 0, 0, 0)))
            .addAnimation("arm_left", ch(ROT, rl(0, 0, 0, 0)))
            .addAnimation("arm_left", ch(POS, pl(0, 0, 0, 0)))
            .addAnimation("arm_right", ch(ROT, rl(0, 0, 0, 0)))
            .addAnimation("arm_right", ch(POS, pl(0, 0, 0, 0)))
            .addAnimation("legs", ch(ROT, rl(0, 0, 0, 0)))
            .addAnimation("legs", ch(POS, pl(0, 0, 0, 0)))
            .addAnimation("leg_left", ch(ROT, rl(0, 0, 0, 0)))
            .addAnimation("leg_left", ch(POS, pl(0, 0, 0, 0)))
            .addAnimation("leg_right", ch(ROT, rl(0, 0, 0, 0)))
            .addAnimation("leg_right", ch(POS, pl(0, 0, 0, 0)))
            .addAnimation("all", ch(ROT, rl(0, 0, 0, 0)))
            .addAnimation("all", ch(POS, pl(0, 0, 0, 0)))
            .build();

    /** animation.gravitite_golem.repel, 0.4167 s. */
    public static final AnimationDefinition REPEL = AnimationDefinition.Builder.withLength(0.4167F)
            .addAnimation("head", ch(ROT, rl(0, 0, 0, 0), rl(0.1667F, 0, 0, 0), rl(0.2917F, -25, 0, 0), rl(0.4167F, 0, 0, 0)))
            .addAnimation("nose", ch(ROT, rc(0, 0, 0, 0), rc(0.1667F, 0, 0, 0), rc(0.25F, 2.5F, 0, 0), rc(0.3333F, -15, 0, 0), rc(0.4167F, 0, 0, 0)))
            .addAnimation("arms", ch(ROT, rl(0, 0, 0, 0), rl(0.1667F, -135, 0, 0), rl(0.4167F, -135, 0, 0)))
            .addAnimation("arm_left", ch(ROT, rl(0, 0, 0, 0), rl(0.1667F, 0, 0, -6), rl(0.2917F, 0, 0, 21.5F), rl(0.4167F, 0, 0, -6)))
            .addAnimation("arm_right", ch(ROT, rl(0, 0, 0, 0), rl(0.1667F, 0, 0, 6), rl(0.2917F, 0, 0, -21.5F), rl(0.4167F, 0, 0, 6)))
            .addAnimation("gem-protrusion", ch(POS, pl(0, 0, 0, 0), pl(0.25F, 0, 0, 0.5F), pl(0.2917F, 0, 0, 0)))
            .build();

    /** animation.gravitite_golem.stand_up, 4 s. */
    public static final AnimationDefinition STAND_UP = AnimationDefinition.Builder.withLength(4)
            .addAnimation("head", ch(ROT, rl(0, 0, 0, 0), rl(2, 0, 0, 0), rl(4, -10, 0, 0)))
            .addAnimation("head", ch(POS, pl(0, 0, 0, 0), pl(2, 0, 0, 0)))
            .addAnimation("arms", ch(ROT, rl(0, -25, 0, 0), rl(2, 0, 0, 0), rl(4, -2.5F, 0, 0)))
            .addAnimation("arms", ch(POS, pl(0, 0, 0, 0), pl(2, 0, 0, 0)))
            .addAnimation("arm_left", ch(ROT, rl(0, 0, 0, 0), rl(2, 0, 0, 0)))
            .addAnimation("arm_left", ch(POS, pl(0, 0, 0, 0), pl(2, 0, 0, 0)))
            .addAnimation("arm_right", ch(ROT, rl(0, 0, 0, 0), rl(2, 0, 0, 0)))
            .addAnimation("arm_right", ch(POS, pl(0, 0, 0, 0), pl(2, 0, 0, 0)))
            .addAnimation("legs", ch(ROT, rl(0, -90, 0, 0), rl(2, 0, 0, 0)))
            .addAnimation("legs", ch(POS, pl(0, 0, 0, -1), pl(2, 0, 0, 0)))
            .addAnimation("leg_left", ch(ROT, rl(0, 0, 0, -90), rl(2, 0, 0, 0), rl(4, 3, 0, 0)))
            .addAnimation("leg_left", ch(POS, pl(0, -3.5F, -2, 0), pl(2, 0, 0, 0)))
            .addAnimation("leg_right", ch(ROT, rl(0, 0, 0, 90), rl(2, 0, 0, 0), rl(4, -3, 0, 0)))
            .addAnimation("leg_right", ch(POS, pl(0, 3.5F, -2, 0), pl(2, 0, 0, 0)))
            .addAnimation("all", ch(ROT, rl(0, 0, 0, 0), rl(2, 0, 0, 0), rl(4, 10, -0.0001F, 0.0001F)))
            .addAnimation("all", ch(POS, pl(0, 0, -4, 0), pl(2, 0, 0, 0), pl(4, 0, 5, 0)))
            .build();
    // END GENERATED ANIMATIONS

    /** shake_head, 0.8 s. */
    public static final AnimationDefinition SHAKE_HEAD = AnimationDefinition.Builder.withLength(0.8F)
            .addAnimation("head", ch(ROT, rc(0, 0, 0, 0), rc(0.2F, 0, 25, 0), rc(0.4F, 0, -25, 0), rc(0.6F, 0, 20, 0),
                    rc(0.8F, 0, 0, 0)))
            .build();

    /** link_pulse, 0.5 s: the head swelling for a moment. */
    public static final AnimationDefinition LINK_PULSE = AnimationDefinition.Builder.withLength(0.5F)
            .addAnimation("head", ch(SCALE, sc(0, 1, 1, 1), sc(0.15F, 1.08F, 1.08F, 1.08F), sc(0.5F, 1, 1, 1)))
            .build();

    private static AnimationChannel ch(AnimationChannel.Target target, Keyframe... frames) {
        return new AnimationChannel(target, frames);
    }

    private static Keyframe rc(float t, float x, float y, float z) {
        return new Keyframe(t, KeyframeAnimations.degreeVec(x, y, z), AnimationChannel.Interpolations.CATMULLROM);
    }

    private static Keyframe rl(float t, float x, float y, float z) {
        return new Keyframe(t, KeyframeAnimations.degreeVec(x, y, z), AnimationChannel.Interpolations.LINEAR);
    }

    private static Keyframe pc(float t, float x, float y, float z) {
        return new Keyframe(t, KeyframeAnimations.posVec(x, y, z), AnimationChannel.Interpolations.CATMULLROM);
    }

    private static Keyframe pl(float t, float x, float y, float z) {
        return new Keyframe(t, KeyframeAnimations.posVec(x, y, z), AnimationChannel.Interpolations.LINEAR);
    }

    private static Keyframe sc(float t, float x, float y, float z) {
        return new Keyframe(t, KeyframeAnimations.scaleVec(x, y, z), AnimationChannel.Interpolations.CATMULLROM);
    }

    private GravititeGolemAnimations() {
    }
}
