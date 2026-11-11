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
 * The gravitite golem's animations. These are placeholders, written by hand, for a Blockbench model to replace: give
 * the Blockbench animations these names (animation.gravitite_golem.meditate and so on) and generate them over the
 * marked block with tools/blockbench/bbmodel_to_java.py, as the aerbison's are:
 * <pre>
 * python tools/blockbench/bbmodel_to_java.py gravitite_golem.bbmodel --prefix animation.gravitite_golem.
 *     --model src/main/java/com/linc/alteredaether/client/golem/GravititeGolemModel.java
 *     --animations src/main/java/com/linc/alteredaether/client/golem/GravititeGolemAnimations.java
 * </pre>
 * The held ones (meditate, concentrate, walk, levitate) loop and are blended by the golem's weights, so the seated
 * ones carry the seated pose; the rest play once (their lengths are in entity/golem/GolemTiming).
 */
public final class GravititeGolemAnimations {
    private static final AnimationChannel.Target ROT = AnimationChannel.Targets.ROTATION;
    private static final AnimationChannel.Target POS = AnimationChannel.Targets.POSITION;
    private static final AnimationChannel.Target SCALE = AnimationChannel.Targets.SCALE;

    // BEGIN GENERATED ANIMATIONS (tools/blockbench/bbmodel_to_java.py; edit the .bbmodel, not this)
    /** animation.gravitite_golem.meditate, 4 s, looping: sitting cross-legged, hands on knees, breathing slowly. */
    public static final AnimationDefinition MEDITATE = AnimationDefinition.Builder.withLength(4)
            .looping()
            .addAnimation("golem", ch(POS, pl(0, 0, -6, 0), pl(4, 0, -6, 0)))
            .addAnimation("leg_left", ch(ROT, rl(0, -90, 25, 0), rl(4, -90, 25, 0)))
            .addAnimation("leg_right", ch(ROT, rl(0, -90, -25, 0), rl(4, -90, -25, 0)))
            .addAnimation("arm_left", ch(ROT, rc(0, -35, 0, -12), rc(2, -38, 0, -12), rc(4, -35, 0, -12)))
            .addAnimation("arm_right", ch(ROT, rc(0, -35, 0, 12), rc(2, -38, 0, 12), rc(4, -35, 0, 12)))
            .addAnimation("body", ch(POS, pc(0, 0, 0, 0), pc(2, 0, 0.3F, 0), pc(4, 0, 0, 0)))
            .addAnimation("head", ch(ROT, rc(0, 4, 0, 0), rc(2, 1, 0, 0), rc(4, 4, 0, 0)))
            .build();

    /** animation.gravitite_golem.concentrate, 1 s, looping: seated, hands raised and trembling, head bowed. */
    public static final AnimationDefinition CONCENTRATE = AnimationDefinition.Builder.withLength(1)
            .looping()
            .addAnimation("golem", ch(POS, pl(0, 0, -6, 0), pl(1, 0, -6, 0)))
            .addAnimation("leg_left", ch(ROT, rl(0, -90, 25, 0), rl(1, -90, 25, 0)))
            .addAnimation("leg_right", ch(ROT, rl(0, -90, -25, 0), rl(1, -90, -25, 0)))
            .addAnimation("arm_left", ch(ROT, rl(0, -80, 0, -15), rl(0.25F, -82, 0, -17), rl(0.5F, -79, 0, -14),
                    rl(0.75F, -82, 0, -16), rl(1, -80, 0, -15)))
            .addAnimation("arm_right", ch(ROT, rl(0, -80, 0, 15), rl(0.25F, -79, 0, 14), rl(0.5F, -82, 0, 17),
                    rl(0.75F, -79, 0, 15), rl(1, -80, 0, 15)))
            .addAnimation("head", ch(ROT, rl(0, 12, 0, 0), rl(0.25F, 13, 1, 0), rl(0.5F, 12, 0, 0), rl(0.75F, 13, -1, 0),
                    rl(1, 12, 0, 0)))
            .build();

    /** animation.gravitite_golem.walk, 1 s, looping. */
    public static final AnimationDefinition WALK = AnimationDefinition.Builder.withLength(1)
            .looping()
            .addAnimation("leg_left", ch(ROT, rc(0, 25, 0, 0), rc(0.5F, -25, 0, 0), rc(1, 25, 0, 0)))
            .addAnimation("leg_right", ch(ROT, rc(0, -25, 0, 0), rc(0.5F, 25, 0, 0), rc(1, -25, 0, 0)))
            .addAnimation("arm_left", ch(ROT, rc(0, -20, 0, -5), rc(0.5F, 20, 0, -5), rc(1, -20, 0, -5)))
            .addAnimation("arm_right", ch(ROT, rc(0, 20, 0, 5), rc(0.5F, -20, 0, 5), rc(1, 20, 0, 5)))
            .addAnimation("body", ch(POS, pc(0, 0, 0, 0), pc(0.25F, 0, 0.5F, 0), pc(0.5F, 0, 0, 0), pc(0.75F, 0, 0.5F, 0),
                    pc(1, 0, 0, 0)))
            .build();

    /** animation.gravitite_golem.levitate, 2 s, looping: legs hanging, arms out, bobbing. */
    public static final AnimationDefinition LEVITATE = AnimationDefinition.Builder.withLength(2)
            .looping()
            .addAnimation("golem", ch(POS, pc(0, 0, 0, 0), pc(1, 0, 0.6F, 0), pc(2, 0, 0, 0)))
            .addAnimation("leg_left", ch(ROT, rc(0, 10, 0, -4), rc(1, 14, 0, -6), rc(2, 10, 0, -4)))
            .addAnimation("leg_right", ch(ROT, rc(0, 10, 0, 4), rc(1, 14, 0, 6), rc(2, 10, 0, 4)))
            .addAnimation("arm_left", ch(ROT, rc(0, 0, 0, -45), rc(1, 0, 0, -50), rc(2, 0, 0, -45)))
            .addAnimation("arm_right", ch(ROT, rc(0, 0, 0, 45), rc(1, 0, 0, 50), rc(2, 0, 0, 45)))
            .build();

    /** animation.gravitite_golem.sit_down, 0.75 s: settling into place. */
    public static final AnimationDefinition SIT_DOWN = AnimationDefinition.Builder.withLength(0.75F)
            .addAnimation("head", ch(ROT, rc(0, 0, 0, 0), rc(0.4F, 15, 0, 0), rc(0.75F, 0, 0, 0)))
            .build();

    /** animation.gravitite_golem.stand_up, 0.75 s: looking up as it rises. */
    public static final AnimationDefinition STAND_UP = AnimationDefinition.Builder.withLength(0.75F)
            .addAnimation("head", ch(ROT, rc(0, 0, 0, 0), rc(0.4F, -12, 0, 0), rc(0.75F, 0, 0, 0)))
            .build();

    /** animation.gravitite_golem.repel, 0.5 s: arms flung out. */
    public static final AnimationDefinition REPEL = AnimationDefinition.Builder.withLength(0.5F)
            .addAnimation("arm_left", ch(ROT, rl(0, 0, 0, 0), rl(0.1F, -20, 0, -95), rc(0.5F, 0, 0, 0)))
            .addAnimation("arm_right", ch(ROT, rl(0, 0, 0, 0), rl(0.1F, -20, 0, 95), rc(0.5F, 0, 0, 0)))
            .addAnimation("head", ch(ROT, rl(0, 0, 0, 0), rl(0.1F, -15, 0, 0), rc(0.5F, 0, 0, 0)))
            .build();

    /** animation.gravitite_golem.shake_head, 0.8 s. */
    public static final AnimationDefinition SHAKE_HEAD = AnimationDefinition.Builder.withLength(0.8F)
            .addAnimation("head", ch(ROT, rc(0, 0, 0, 0), rc(0.2F, 0, 25, 0), rc(0.4F, 0, -25, 0), rc(0.6F, 0, 20, 0),
                    rc(0.8F, 0, 0, 0)))
            .build();

    /** animation.gravitite_golem.link_pulse, 0.5 s: the head swelling for a moment. */
    public static final AnimationDefinition LINK_PULSE = AnimationDefinition.Builder.withLength(0.5F)
            .addAnimation("head", ch(SCALE, sc(0, 1, 1, 1), sc(0.15F, 1.12F, 1.12F, 1.12F), sc(0.5F, 1, 1, 1)))
            .build();
    // END GENERATED ANIMATIONS

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
