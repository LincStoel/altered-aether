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

import net.minecraft.client.animation.AnimationChannel;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.animation.Keyframe;
import net.minecraft.client.animation.KeyframeAnimations;

/**
 * The aerbison's one-shot animations, as keyframes on the bones of {@link AerbisonModel}. Times are in seconds,
 * rotations in degrees, positions in pixels with y up (as Blockbench's animator writes them), so any of these can be
 * re-authored in Blockbench and swapped in so long as the bone names match. The server lands each effect on the tick
 * the matching keyframe does (see AerbisonTiming); lying down, rising and walking are code in the model.
 */
public final class AerbisonAnimations {
    private static final String BISON = "bison", NECK = "neck", HEAD = "head", MANE = "mane";
    private static final String FL = "leg_front_left", FR = "leg_front_right", BL = "leg_back_left", BR = "leg_back_right";

    /** Head down to the grass, three chews and up again. */
    public static final AnimationDefinition GRAZE = new B(2.4F)
            .rot(NECK, r(0, 0, 0, 0), r(0.5F, 48, 0, 0), r(1.9F, 48, 0, 0), r(2.4F, 0, 0, 0))
            .rot(HEAD, r(0, 0, 0, 0), r(0.5F, 18, 0, 0), r(0.8F, 10, 0, 0), r(1.0F, 20, 0, 0), r(1.2F, 10, 0, 0),
                    r(1.4F, 20, 0, 0), r(1.6F, 12, 0, 0), r(1.9F, 18, 0, 0), r(2.4F, 0, 0, 0))
            .rot(HEAD, r(0, 0, 0, 0), r(1.0F, 0, 0, 4), r(1.6F, 0, 0, -4), r(2.4F, 0, 0, 0))
            .rot(BISON, r(0, 0, 0, 0), r(0.5F, 3, 0, 0), r(1.9F, 3, 0, 0), r(2.4F, 0, 0, 0))
            .build();

    /** A fast shake of the head and mane, to clear the flies. */
    public static final AnimationDefinition HEAD_SHAKE = new B(1.0F)
            .rot(HEAD, rl(0, 0, 0, 0), rl(0.12F, 0, 0, 22), rl(0.26F, 0, 0, -22), rl(0.40F, 0, 0, 18), rl(0.54F, 0, 0, -13),
                    rl(0.68F, 0, 0, 7), rl(0.84F, 0, 0, -3), rl(1.0F, 0, 0, 0))
            .rot(NECK, rl(0, 0, 0, 0), rl(0.04F, 0, 0, 0), rl(0.16F, 0, 0, 11), rl(0.30F, 0, 0, -11), rl(0.44F, 0, 0, 9),
                    rl(0.58F, 0, 0, -6.5F), rl(0.72F, 0, 0, 3.5F), rl(0.88F, 0, 0, -1.5F), rl(1.0F, 0, 0, 0))
            .rot(MANE, rl(0, 0, 0, 0), rl(0.08F, 0, 0, 0), rl(0.2F, 0, 3, 0), rl(0.34F, 0, -3, 0), rl(0.48F, 0, 2.5F, 0),
                    rl(0.62F, 0, -2, 0), rl(0.76F, 0, 1, 0), rl(1.0F, 0, 0, 0))
            .build();

    /** Rears back and paws the ground: the leader's wind-up to a stampede. */
    public static final AnimationDefinition WINDUP = new B(1.3F)
            .rot(BISON, r(0, 0, 0, 0), r(0.25F, -7, 0, 0), r(1.05F, -7, 0, 0), r(1.3F, 0, 0, 0))
            .rot(FR, r(0, 0, 0, 0), r(0.15F, -38, 0, 0), r(0.3F, 26, 0, 0), r(0.45F, -38, 0, 0), r(0.6F, 26, 0, 0),
                    r(0.75F, -38, 0, 0), r(0.9F, 26, 0, 0), r(1.05F, 0, 0, 0))
            .pos(FR, p(0, 0, 0, 0), p(0.15F, 0, 1.5F, 0), p(0.3F, 0, 0, 0), p(0.45F, 0, 1.5F, 0), p(0.6F, 0, 0, 0),
                    p(0.75F, 0, 1.5F, 0), p(0.9F, 0, 0, 0))
            .rot(NECK, r(0, 0, 0, 0), r(0.3F, 26, 0, 0), r(1.05F, 26, 0, 0), r(1.3F, 30, 0, 0))
            .rot(HEAD, r(0, 0, 0, 0), r(0.3F, 10, 0, 0))
            .build();

    /** A follower roused by the stampede: a toss of the head and a hop. */
    public static final AnimationDefinition ROUSE = new B(0.5F)
            .rot(HEAD, r(0, 0, 0, 0), r(0.15F, -22, 0, 0), r(0.5F, 0, 0, 0))
            .pos(BISON, p(0, 0, 0, 0), p(0.12F, 0, 1, 0), p(0.3F, 0, 0, 0), p(0.5F, 0, 0, 0))
            .build();

    /** Rears and stamps; the gust fires at 0.45 s. */
    public static final AnimationDefinition STOMP = new B(0.9F)
            .rot(BISON, r(0, 0, 0, 0), r(0.35F, -28, 0, 0), rl(0.45F, 4, 0, 0), r(0.9F, 0, 0, 0))
            .pos(BISON, p(0, 0, 0, 0), p(0.35F, 0, 5, 0), pl(0.45F, 0, 0, 0))
            .rot(FL, r(0, 0, 0, 0), r(0.35F, -65, 0, 0), rl(0.45F, 12, 0, 0), r(0.6F, 30, 0, 0), r(0.9F, 0, 0, 0))
            .rot(FR, r(0, 0, 0, 0), r(0.35F, -65, 0, 0), rl(0.45F, 12, 0, 0), r(0.6F, 30, 0, 0), r(0.9F, 0, 0, 0))
            .rot(BL, r(0, 0, 0, 0), r(0.35F, 8, 0, 0), r(0.45F, 0, 0, 0), r(0.6F, -30, 0, 0), r(0.9F, 0, 0, 0))
            .rot(BR, r(0, 0, 0, 0), r(0.35F, 8, 0, 0), r(0.45F, 0, 0, 0), r(0.6F, -30, 0, 0), r(0.9F, 0, 0, 0))
            .rot(NECK, r(0, 0, 0, 0), r(0.35F, -6, 0, 0), rl(0.45F, 20, 0, 0), r(0.9F, 0, 0, 0))
            .build();

    /** Dips the head and hooks up with the horns; the hit lands at 0.35 s. */
    public static final AnimationDefinition HORN_TOSS = new B(0.6F)
            .rot(NECK, r(0, 0, 0, 0), r(0.2F, 34, 0, 0), rl(0.35F, -22, 0, 0), r(0.6F, 0, 0, 0))
            .rot(HEAD, r(0, 0, 0, 0), r(0.2F, 12, 0, 0), rl(0.35F, -18, 0, 0), r(0.6F, 0, 0, 0))
            .rot(HEAD, r(0, 0, 0, 0), r(0.35F, 0, 0, 16), r(0.6F, 0, 0, 0))
            .pos(BISON, p(0, 0, 0, 0), p(0.2F, 0, 0, 1), p(0.35F, 0, 0, -2), p(0.6F, 0, 0, 0))
            .build();

    /** Rolls onto its side and rocks, legs kicking. Played while lying. */
    public static final AnimationDefinition WALLOW = new B(2.6F)
            .rot(BISON, r(0, 0, 0, 0), r(0.6F, 0, 0, 72), r(0.9F, 0, 0, 60), r(1.2F, 0, 0, 78), r(1.5F, 0, 0, 58),
                    r(1.8F, 0, 0, 76), r(2.6F, 0, 0, 0))
            .pos(BISON, p(0, 0, 0, 0), p(0.6F, 0, 2, 0), p(1.8F, 0, 2, 0), p(2.6F, 0, 0, 0))
            .rot(FL, r(0.6F, 0, 0, 0), r(0.9F, 25, 0, 0), r(1.2F, -25, 0, 0), r(1.5F, 25, 0, 0), r(1.8F, 0, 0, 0))
            .rot(BL, r(0.6F, 0, 0, 0), r(0.9F, -25, 0, 0), r(1.2F, 25, 0, 0), r(1.5F, -25, 0, 0), r(1.8F, 0, 0, 0))
            .rot(NECK, r(0, 0, 0, 0), r(0.6F, 0, 0, -20), r(1.8F, 0, 0, -20), r(2.6F, 0, 0, 0))
            .build();

    /** Reeling after a wall: recoil, then a slow dazed wobble. */
    public static final AnimationDefinition STAGGER = new B(2.0F)
            .rot(BISON, r(0, 0, 0, 0), r(0.15F, -10, 0, 0), r(0.4F, 0, 0, 0))
            .rot(BISON, r(0, 0, 0, 0), r(0.25F, 0, 0, 7), r(0.6F, 0, 0, -7), r(0.95F, 0, 0, 5), r(1.3F, 0, 0, -5),
                    r(1.65F, 0, 0, 2), r(2.0F, 0, 0, 0))
            .pos(BISON, p(0, 0, 0, 0), p(0.15F, 0, 0, 3), p(0.4F, 0, 0, 0))
            .rot(HEAD, r(0, 0, 0, 0), r(0.5F, 0, 0, 15), r(1.0F, 0, 0, -15), r(1.5F, 0, 0, 10), r(2.0F, 0, 0, 0))
            .rot(NECK, r(0, 0, 0, 0), r(0.3F, 12, 0, 0), r(1.7F, 12, 0, 0), r(2.0F, 0, 0, 0))
            .build();

    /** A stampede with no room: one paw, then an indignant toss of the head. */
    public static final AnimationDefinition REFUSE = new B(0.7F)
            .rot(BISON, r(0, 0, 0, 0), r(0.15F, -5, 0, 0), r(0.3F, 0, 0, 0))
            .rot(FR, r(0, 0, 0, 0), r(0.15F, -38, 0, 0), r(0.3F, 20, 0, 0), r(0.45F, 0, 0, 0))
            .rot(HEAD, r(0, 0, 0, 0), r(0.3F, 6, 0, 0), r(0.45F, -24, 0, 0), r(0.7F, 0, 0, 0))
            .pos(BISON, p(0, 0, 0, 0), p(0.45F, 0, 0, 1), p(0.7F, 0, 0, 0))
            .build();

    private static Keyframe r(float t, float x, float y, float z) {
        return new Keyframe(t, KeyframeAnimations.degreeVec(x, y, z), AnimationChannel.Interpolations.CATMULLROM);
    }

    private static Keyframe rl(float t, float x, float y, float z) {
        return new Keyframe(t, KeyframeAnimations.degreeVec(x, y, z), AnimationChannel.Interpolations.LINEAR);
    }

    private static Keyframe p(float t, float x, float y, float z) {
        return new Keyframe(t, KeyframeAnimations.posVec(x, y, z), AnimationChannel.Interpolations.CATMULLROM);
    }

    private static Keyframe pl(float t, float x, float y, float z) {
        return new Keyframe(t, KeyframeAnimations.posVec(x, y, z), AnimationChannel.Interpolations.LINEAR);
    }

    private static final class B {
        private final AnimationDefinition.Builder builder;

        B(float length) {
            builder = AnimationDefinition.Builder.withLength(length);
        }

        B rot(String bone, Keyframe... frames) {
            builder.addAnimation(bone, new AnimationChannel(AnimationChannel.Targets.ROTATION, frames));
            return this;
        }

        B pos(String bone, Keyframe... frames) {
            builder.addAnimation(bone, new AnimationChannel(AnimationChannel.Targets.POSITION, frames));
            return this;
        }

        AnimationDefinition build() {
            return builder.build();
        }
    }

    private AerbisonAnimations() {
    }
}
