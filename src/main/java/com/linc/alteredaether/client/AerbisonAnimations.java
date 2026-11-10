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
 * The aerbison's animations, generated from the Blockbench project (E:/blockbench/aerbison/aerbison.geo.bbmodel) by
 * tools/blockbench/bbmodel_to_java.py. Edit them in Blockbench and re-run:
 * <pre>
 * python tools/blockbench/bbmodel_to_java.py E:/blockbench/aerbison/aerbison.geo.bbmodel --prefix animation.aerbison.
 *     --model src/main/java/com/linc/alteredaether/client/AerbisonModel.java
 *     --animations src/main/java/com/linc/alteredaether/client/AerbisonAnimations.java
 * </pre>
 * The server lands each effect on the tick its keyframe does, so a change of length or of the moment a hoof lands
 * wants the matching number in entity/aerbison/AerbisonTiming changed with it. How each one is played (walk,
 * gallop and idle by weight at their own pace, lay_down then laying_idle while lying, lay_down in reverse to get up) is in
 * {@link AerbisonModel}.
 */
public final class AerbisonAnimations {
    private static final AnimationChannel.Target ROT = AnimationChannel.Targets.ROTATION;
    private static final AnimationChannel.Target POS = AnimationChannel.Targets.POSITION;

    // BEGIN GENERATED ANIMATIONS (tools/blockbench/bbmodel_to_java.py; edit the .bbmodel, not this)
    /** animation.aerbison.walk, 3 s, looping. */
    public static final AnimationDefinition WALK = AnimationDefinition.Builder.withLength(3)
            .looping()
            .addAnimation("neck", ch(ROT, rl(0, 0, 2.5F, 0), rl(1.5F, 0, -2.5F, 0), rl(3, 0, 2.5F, 0)))
            .addAnimation("neck", ch(POS, pl(0, 0, 0, 0), pl(0.75F, 0, 0.5F, 0), pl(1.5F, 0, 0, 0), pl(2.25F, 0, 0.5F, 0), pl(3, 0, 0, 0)))
            .addAnimation("head", ch(ROT, rc(0, 0, 2.5F, 0), rc(1.5F, 0, -2.5F, 0), rc(3, 0, 2.5F, 0)))
            .addAnimation("leg_back_right", ch(ROT, rl(0, 0, 0, 0), rl(0.375F, 15, 0, 0), rl(1.125F, -15, 0, 0), rl(1.5F, 0, 0, 0)))
            .addAnimation("leg_back_right", ch(POS, pl(0, 0, 0, 2), pl(0.75F, 0, 1, 0), pl(1.5F, 0, 0, -2), pl(3, 0, 0, 2)))
            .addAnimation("leg_back_left", ch(ROT, rl(1.5F, 0, 0, 0), rl(1.875F, 15, 0, 0), rl(2.625F, -15, 0, 0), rl(3, 0, 0, 0)))
            .addAnimation("leg_back_left", ch(POS, pl(0, 0, 0, -2), pl(1.5F, 0, 0, 2), pl(3, 0, 0, -2)))
            .addAnimation("leg_front_right", ch(ROT, rl(1.5F, 0, 0, 0), rl(1.875F, 15, 0, 0), rl(2.625F, -15, 0, 0), rl(3, 0, 0, 0)))
            .addAnimation("leg_front_right", ch(POS, pl(0, 0, 0, -2), pl(1.5F, 0, 0, 2), pl(2.25F, 0, 2, 0), pl(3, 0, 0, -2)))
            .addAnimation("mane", ch(ROT, rl(0, 0, 0, -2.5F), rl(1.5F, 0, 0, 2.5F), rl(3, 0, 0, -2.5F)))
            .addAnimation("mane", ch(POS, pl(0, 0, 0, 0), pl(0.75F, 0, 0, 1), pl(1.5F, 0, 0, 0), pl(2.25F, 0, 0, 1), pl(3, 0, 0, 0)))
            .addAnimation("rear_body", ch(POS, pl(0, 0, 0, 0), pl(0.75F, 0, 0, 0.5F), pl(1.5F, 0, 0, 0), pl(2.25F, 0, 0, 0.5F), pl(3, 0, 0, 0)))
            .addAnimation("leg_front_left", ch(ROT, rl(0, 0, 0, 0), rl(0.375F, 15, 0, 0), rl(1.125F, -15, 0, 0), rl(1.5F, 0, 0, 0)))
            .addAnimation("leg_front_left", ch(POS, pl(0, 0, 0, 2), pl(0.75F, 0, 2, 0), pl(1.5F, 0, 0, -2), pl(3, 0, 0, 2)))
            .build();

    /** animation.aerbison.head_shake, 1 s. */
    public static final AnimationDefinition HEAD_SHAKE = AnimationDefinition.Builder.withLength(1)
            .addAnimation("head", ch(ROT, rl(0, 0, 0, 0), rl(0.12F, 0, 0, -22), rl(0.26F, 0, 0, 22), rl(0.4F, 0, 0, -18), rl(0.54F, 0, 0, 13), rl(0.68F, 0, 0, -7), rl(0.84F, 0, 0, 3), rl(1, 0, 0, 0)))
            .addAnimation("neck", ch(ROT, rl(0, 0, 0, 0), rl(0.04F, 0, 0, 0), rl(0.16F, 0, 0, -11), rl(0.3F, 0, 0, 11), rl(0.44F, 0, 0, -9), rl(0.58F, 0, 0, 6.5F), rl(0.72F, 0, 0, -3.5F), rl(0.88F, 0, 0, 1.5F), rl(1, 0, 0, 0)))
            .addAnimation("mane", ch(ROT, rl(0, 0, 0, 0), rl(0.08F, 0, 0, 0), rl(0.2F, 0, -3, 0), rl(0.34F, 0, 3, 0), rl(0.48F, 0, -2.5F, 0), rl(0.62F, 0, 2, 0), rl(0.76F, 0, -1, 0), rl(1, 0, 0, 0)))
            .addAnimation("nose", ch(ROT, rl(0, 0, 0, 0), rl(0.1F, 0, 0, 5), rl(0.15F, 0, 0, -8.33F), rl(0.25F, 0, 0, -5), rl(0.3F, 0, 0, 8.33F), rl(0.4F, 0, 0, 5), rl(0.45F, 0, 0, -8.33F), rl(0.55F, 0, 0, -5), rl(0.6F, 0, 0, 8.33F), rl(0.7F, 0, 0, 3), rl(0.75F, 0, 0, -6.5F), rl(0.85F, 0, 0, -3), rl(0.9F, 0, 0, 3), rl(1, 0, 0, 0)))
            .build();

    /** animation.aerbison.rouse, 0.5 s. */
    public static final AnimationDefinition ROUSE = AnimationDefinition.Builder.withLength(0.5F)
            .addAnimation("head", ch(ROT, rc(0, 0, 0, 0), rc(0.15F, -22, 0, 0), rc(0.5F, 0, 0, 0)))
            .addAnimation("neck", ch(POS, pc(0, 0, 0, 0), pc(0.1F, 0, 1, 0), pc(0.3F, 0, 0, 0), pc(0.5F, 0, 0, 0)))
            .addAnimation("mane", ch(POS, pc(0, 0, 0, 0), pc(0.1F, 0, 0, 1), pc(0.3F, 0, 0, 0), pc(0.5F, 0, 0, 0)))
            .addAnimation("rear_body", ch(ROT, rl(0, 0, 0, 0), rl(0.1F, -2.5F, 0, 0), rl(0.3F, 0, 0, 0), rl(0.5F, 0, 0, 0)))
            .addAnimation("front_legs", ch(ROT, rl(0, 0, 0, 0), rl(0.1F, 5, 0, 0), rl(0.3F, 0, 0, 0), rl(0.5F, 0, 0, 0)))
            .addAnimation("front_legs", ch(POS, pc(0, 0, 0, 0), pc(0.1F, 0, 1, 0), pc(0.3F, 0, 0, 0), pc(0.5F, 0, 0, 0)))
            .build();

    /** animation.aerbison.refuse, 0.7 s. */
    public static final AnimationDefinition REFUSE = AnimationDefinition.Builder.withLength(0.7F)
            .addAnimation("head", ch(ROT, rc(0, 0, 0, 0), rc(0.3F, 6, 0, 0), rc(0.45F, -24, 0, 0), rc(0.7F, 0, 0, 0)))
            .addAnimation("neck", ch(ROT, rc(0, 0, 0, 0), rc(0.15F, -5, 0, 0), rc(0.3F, 0, 0, 0)))
            .addAnimation("neck", ch(POS, pc(0, 0, 0, 0), pc(0.45F, 0, 0, 1), pc(0.7F, 0, 0, 0)))
            .addAnimation("mane", ch(ROT, rc(0, 0, 0, 0), rc(0.15F, -5, 0, 0), rc(0.3F, 0, 0, 0)))
            .addAnimation("mane", ch(POS, pc(0, 0, 0, 0), pc(0.45F, 0, 0, 1), pc(0.7F, 0, 0, 0)))
            .addAnimation("rear_body", ch(ROT, rl(0, 0, 0, 0), rl(0.45F, -5, 0, 0), rl(0.7F, 0, 0, 0)))
            .addAnimation("front_legs", ch(ROT, rl(0, 0, 0, 0), rl(0.2F, 5, 0, 0), rl(0.45F, -5, 0, 0), rl(0.7F, 0, 0, 0)))
            .addAnimation("front_legs", ch(POS, pc(0, 0, 0, 0), pc(0.45F, 0, 1, 0), pc(0.7F, 0, 0, 0)))
            .build();

    /** animation.aerbison.graze, 2.4 s. */
    public static final AnimationDefinition GRAZE = AnimationDefinition.Builder.withLength(2.4F)
            .addAnimation("neck", ch(ROT, rc(0, 0, 0, 0), rc(0.5F, 10.5F, 0, 0), rc(1.9F, 5.5F, 0, 0), rc(2.4F, 0, 0, 0)))
            .addAnimation("head", ch(ROT, rc(0, 0, 0, 0), rc(0.5F, 4, 0, -2), rc(0.8F, -2.5F, 0, -3.2F), rc(1, -2.5F, 0, -4), rc(1.2F, 5, 0, -1.3333F), rc(1.4F, 12.5F, 0, 1.3333F), rc(1.6F, 12, 0, 4), rc(1.9F, 18, 0, 2.5F), rc(2.4F, 0, 0, 0)))
            .addAnimation("body", ch(ROT, rc(0, 0, 0, 0), rc(0.5F, 3, 0, 0), rc(1.9F, 3, 0, 0), rc(2.4F, 0, 0, 0)))
            .addAnimation("body", ch(POS, pl(0, 0, 0, 0), pl(0.5F, 0, -1, 0), pl(1.9F, 0, -1, 0), pl(2.4F, 0, 0, 0)))
            .build();

    /** animation.aerbison.windup, 1.2 s. */
    public static final AnimationDefinition WINDUP = AnimationDefinition.Builder.withLength(1.2F)
            .addAnimation("neck", ch(ROT, rc(0, 0, 0, 0), rc(0.1F, -18.22F, 0, 0), rc(0.65F, 11, 0, 0), rc(1.2F, 10, 0, 0)))
            .addAnimation("neck", ch(POS, pl(0, 0, 0, 0), pl(0.3F, 0, 1, 0), pl(1.2F, 0, 0, 0)))
            .addAnimation("head", ch(ROT, rc(0, 0, 0, 0), rc(0.4F, 21, 0, 0), rc(0.65F, 21.45F, 0, 0), rc(1.2F, 12.5F, 0, 0)))
            .addAnimation("body", ch(ROT, rc(0, 0, 0, 0), rc(0.2F, -7, 0, 0), rc(0.95F, -7, 0, 0), rc(1.2F, 0, 0, 0)))
            .addAnimation("body", ch(POS, pl(0, 0, 0, 0), pl(0.3F, 0, 2, 0), pl(0.65F, 0, 0, 0), pl(1.2F, 0, 0, 0)))
            .addAnimation("mane", ch(ROT, rl(0, 0, 0, 0), rl(0.1F, 0, 0, 0), rl(0.35F, 5.83F, 0, 0), rl(0.65F, 10, 0, 0), rl(1.2F, 0, 0, 0)))
            .addAnimation("body_and_neck", ch(POS, pl(0, 0, 0, 0), pl(0.25F, 0, 1, 0), pl(1.2F, 0, 0, 0)))
            .addAnimation("rear_body", ch(ROT, rl(0, 0, 0, 0), rl(1.2F, 0, 0, 0)))
            .addAnimation("front_legs", ch(ROT, rl(0, 0, 0, 0), rl(0.15F, 7.5F, 0, 0), rl(0.45F, -5, 0, 0), rl(0.65F, 0, 0, 0), rl(1.2F, 0, 0, 0)))
            .addAnimation("front_legs", ch(POS, pl(0, 0, 0, 0), pl(0.3F, 0, 3, 0), pl(0.65F, 0, 0, 0), pl(1.2F, 0, 0, 0)))
            .build();

    /** animation.aerbison.stomp, 1 s. */
    public static final AnimationDefinition STOMP = AnimationDefinition.Builder.withLength(1)
            .addAnimation("neck", ch(ROT, rl(0.1F, 0, 0, 0), rl(0.3F, -12.5F, 0, 0), rl(0.8F, 12.5F, 0, 0), rl(1, 0, 0, 0)))
            .addAnimation("neck", ch(POS, pl(0.1F, 0, 0, 0), pl(0.4F, 0, 6, 0), pl(0.8F, 0, 0, 0), pl(0.9F, 0, -2, 0), pl(1, 0, 0, 0)))
            .addAnimation("body", ch(ROT, rl(0, 0, 0, 0), rl(0.9F, 0, 0, 0)))
            .addAnimation("head", ch(ROT, rl(0, 0, 0, 0), rl(0.05F, 2.5F, 0, 0), rl(0.15F, -5, 0, 0), rl(0.3F, 0, 0, 0), rl(0.45F, 6.67F, 0, 0), rl(0.65F, 12.5F, 0, 0), rl(1, 0, 0, 0)))
            .addAnimation("mane", ch(ROT, rl(0.1F, 0, 0, 0), rl(0.25F, -3.75F, 0, 0), rl(0.4F, -2.5F, 0, 0), rl(0.8F, 5, 0, 0), rl(1, 0, 0, 0)))
            .addAnimation("mane", ch(POS, pl(0, 0, 0, 0), pl(0.1F, 0, 0, -1), pl(0.15F, 0, 0, 0), pl(0.4F, 0, 0, 6), pl(0.9F, 0, 0, -1), pl(1, 0, 0, 0)))
            .addAnimation("leg_back", ch(ROT, rl(0.1F, 0, 0, 0), rl(0.25F, 5, 0, 0), rl(0.4F, -13.44F, 0, 0), rl(0.65F, -12.5F, 0, 0), rl(0.8F, 40, 0, 0), rl(0.95F, 0, 0, 0)))
            .addAnimation("leg_back", ch(POS, pl(0.1F, 0, 0, 0), pl(0.25F, 0, 0, -1), pl(0.4F, 0, 1.7F, -1.14F), pl(0.65F, 0, 4.97F, 1), pl(0.8F, 0, 6, 0), pl(0.85F, 0, 4, 0), pl(0.95F, 0, 0, 0)))
            .addAnimation("body_and_neck", ch(ROT, rl(0, 0, 0, 0), rl(0.9F, 0, 0, 0)))
            .addAnimation("rear_body", ch(ROT, rl(0.1F, 0, 0, 0), rl(0.2F, -11.67F, 0, 0), rl(0.4F, -20, 0, 0), rl(0.65F, 4.23F, 0, 0), rl(0.8F, 15.42F, 0, 0), rl(0.9F, 7.67F, 0, 0), rl(1, 0, 0, 0)))
            .addAnimation("rear_body", ch(POS, pl(0.1F, 0, 0, 0), pl(0.2F, 0, 0, -1.5F), pl(0.65F, 0, 0, 4), pl(0.8F, 0, 0, 5), pl(0.9F, 0, 0, 2), pl(1, 0, 0, 0)))
            .addAnimation("front_legs", ch(ROT, rl(0.15F, 0, 0, 0), rl(0.35F, 17.5F, 0, 0), rl(0.6F, -10, 0, 0), rl(0.75F, 0, 0, 0), rl(1, 0, 0, 0)))
            .addAnimation("front_legs", ch(POS, pl(0.1F, 0, 0, 0), pl(0.4F, 0, 6, 0), pl(0.75F, 0, 0.5F, 0), pl(1, 0, 0, 0)))
            .build();

    /** animation.aerbison.horn_toss, 0.6 s. */
    public static final AnimationDefinition HORN_TOSS = AnimationDefinition.Builder.withLength(0.6F)
            .addAnimation("neck", ch(ROT, rc(0, 0, 0, 0), rc(0.2F, 14, 0, 0), rc(0.35F, -22, 0, 0), rc(0.6F, 0, 0, 0)))
            .addAnimation("head", ch(ROT, rc(0, 0, 0, 0), rc(0.2F, 12, 0, -9.1429F), rc(0.35F, -18, 0, -16), rc(0.6F, 0, 0, 0)))
            .addAnimation("mane", ch(ROT, rl(0, 0, 0, 0), rl(0.2F, 5, 0, 0), rl(0.4F, -18.5F, 0, 0), rl(0.6F, 0, 0, 0)))
            .addAnimation("mane", ch(POS, pl(0, 0, 0, 0), pl(0.2F, 0, 0, 0), pl(0.4F, 0, 0, 3.5F), pl(0.6F, 0, 0, 0)))
            .addAnimation("leg_back", ch(ROT, rl(0, 0, 0, 0), rl(0.2F, 5, 0, 0), rl(0.5F, -6.25F, 0, 0), rl(0.6F, 0, 0, 0)))
            .addAnimation("body_and_neck", ch(POS, pl(0, 0, 0, 0), pl(0.2F, 0, 0, -1), pl(0.5F, 0, 0, 1.75F), pl(0.6F, 0, 0, 0)))
            .addAnimation("front_legs", ch(ROT, rl(0, 0, 0, 0), rl(0.3F, 5, 0, 0), rl(0.4F, -20, 0, 0), rl(0.6F, 0, 0, 0)))
            .addAnimation("front_legs", ch(POS, pl(0, 0, 0, 0), pl(0.2F, 0, 0, 0), pl(0.4F, 0, 1, 0), pl(0.5F, 0, 0, 1), pl(0.6F, 0, 0, 0)))
            .addAnimation("nose", ch(ROT, rl(0, 0, 0, 0), rl(0.1F, -24.36F, 0.64F, 7.39F), rl(0.2F, -14.9465F, -1.8303F, 5.631F), rl(0.3F, 5.4634F, 2.3604F, -7.4822F), rl(0.4F, -18.8704F, -2.2646F, 0.355F), rl(0.5F, -9.3736F, -1.5537F, 2.468F), rl(0.6F, 0, 0, 0)))
            .build();

    /** animation.aerbison.lay_down, 3 s. */
    public static final AnimationDefinition LAY_DOWN = AnimationDefinition.Builder.withLength(3)
            .addAnimation("bison", ch(ROT, rl(0, 0, 0, 0), rl(0.9F, 0, 0, 0), rl(2.1F, 0, 0, 0)))
            .addAnimation("bison", ch(POS, pl(0, 0, 0, 0), pl(0.9F, 0, 0, 0), pl(2.1F, 0, 0, 3), pl(3, 0, 0, 1)))
            .addAnimation("leg_front_right", ch(ROT, rl(0, 0, 0, 0), rl(0.85F, 0, 0, 0), rl(1.35F, -29.56F, -2.98F, 18.41F), rl(1.6F, -14.3347F, -4.4636F, 27.6119F), rl(2.1F, 0, 0, 67.5F), rl(3, 0, 0, 80)))
            .addAnimation("leg_front_right", ch(POS, pl(0, 0, 0, 0), pl(0.85F, 0, 0, 0), pl(1.6F, -1.09F, 0, 0), pl(2.1F, -3, 0, 0)))
            .addAnimation("leg_back_right", ch(ROT, rl(0, 0, 0, 0), rl(0.85F, 0, 0, 0), rl(1.05F, 0, 0, 40), rl(1.25F, 0, 0, 23.64F), rl(1.75F, 0, 0, 57.5F), rl(3, 0, 0, 82.5F)))
            .addAnimation("leg_back_right", ch(POS, pl(0, 0, 0, 0), pl(0.85F, 0, 0, 0), pl(1.05F, -1.67F, 0, 0), pl(1.75F, -3, 0, 0)))
            .addAnimation("neck", ch(ROT, rl(0, 0, 0, 0), rl(0.85F, 0, 0, 0), rl(1.6F, 0, 0, 0), rl(2.1F, 0, 0, 0)))
            .addAnimation("neck", ch(POS, pl(0, 0, 0, 0), pl(0.85F, 0, 0, 0), pl(1.6F, 0, -0.53F, 0), pl(1.9F, 0, -1, 0)))
            .addAnimation("head", ch(ROT, rl(0, 0, 0, 0), rl(0.35F, 7.5F, 0, 0), rl(0.65F, -7, 0, 0), rl(1.6F, 10, 0, 0), rl(1.9F, 0, 0, 0), rl(2.1F, -10, 0, 0), rl(2.5F, -15, 0, 0), rl(3, -12.5F, 0, 0)))
            .addAnimation("head", ch(POS, pl(0, 0, 0, 0), pl(0.65F, 0, 0, 0), pl(0.85F, 0, 0, 0), pl(1.6F, 0, 0, 0), pl(1.9F, 0, 0, 0), pl(2.1F, 0, 1, 0)))
            .addAnimation("leg_back_left", ch(ROT, rl(0, 0, 0, 0), rl(0.85F, 0, 0, 0), rl(1.05F, 0, 0, -40), rl(1.25F, 0, 0, -23.64F), rl(1.75F, 0, 0, -70), rl(3, 0, 0, -85)))
            .addAnimation("leg_back_left", ch(POS, pl(0, 0, 0, 0), pl(0.85F, 0, 0, 0), pl(1.05F, 1.67F, 0, 0), pl(1.75F, 3, 0, 0)))
            .addAnimation("mane", ch(POS, pl(0, 0, 0, 0), pl(0.3F, 0, 0, -1), pl(1.6F, 0, 0, 0)))
            .addAnimation("leg_back", ch(ROT, rl(0, 0, 0, 0), rl(0.35F, 10, 0, 0), rl(0.85F, 0, 0, 0), rl(1.55F, -90, 0, 0)))
            .addAnimation("leg_back", ch(POS, pl(0, 0, 0, 0), pl(0.35F, 0, 0, -1), pl(0.85F, 0, 0, 0), pl(1.05F, 0, 1, 1.33F), pl(1.2F, 0, -0.5F, 1.53F), pl(1.55F, 0, -4, 3), pl(2.1F, 0, -4, 11)))
            .addAnimation("body_and_neck", ch(ROT, rl(0, 0, 0, 0), rl(0.85F, 0, 0, 0), rl(1.6F, 0, 0, 0)))
            .addAnimation("body_and_neck", ch(POS, pl(0, 0, 0, 0), pl(0.35F, 0, 2, -3), pl(0.85F, 0, 0, 0), pl(1.6F, 0, -3, 3), pl(2.1F, 0, -8, 8), pl(2.5F, 0, -9, 8)))
            .addAnimation("rear_body", ch(ROT, rl(0, 0, 0, 0), rl(0.85F, 0, 0, 0), rl(1.55F, -17.5F, 0, 0), rl(2.1F, 0, 0, 0)))
            .addAnimation("rear_body", ch(POS, pl(0, 0, 0, 0), pl(0.35F, 0, 0, -1.91F), pl(0.85F, 0, 0, 0), pl(1.55F, 0, 0, -4.18F), pl(2.1F, 0, 0, -1)))
            .addAnimation("front_legs", ch(ROT, rl(0, 0, 0, 0), rl(0.3F, 7.5F, 0, 0), rl(0.85F, 0, 0, 0), rl(1.6F, -32.73F, 0, 0), rl(2.1F, -90, 0, 0)))
            .addAnimation("front_legs", ch(POS, pl(0, 0, 0, 0), pl(0.3F, 0, 0, -2), pl(0.85F, 0, 0, 0), pl(1.6F, 0, -1.33F, 3.33F), pl(2.1F, 0, -7, 10)))
            .addAnimation("leg_front_left", ch(ROT, rl(0, 0, 0, 0), rl(0.85F, 0, 0, 0), rl(1.35F, -29.56F, 2.98F, -18.41F), rl(1.6F, -14.3347F, 4.4636F, -27.6119F), rl(2.1F, 0, 0, -62.5F), rl(3, 0, 0, -85)))
            .addAnimation("leg_front_left", ch(POS, pl(0, 0, 0, 0), pl(0.85F, 0, 0, 0), pl(1.6F, 1.09F, 0, 0), pl(2.1F, 3, 0, 0)))
            .addAnimation("nose", ch(ROT, rl(0, 0, 0, 0), rl(0.85F, 0, 0, 0), rl(1.6F, -10, 0, 0), rl(2.1F, 5, 0, 0)))
            .addAnimation("nose", ch(POS, pl(0, 0, 0, 0), pl(0.85F, 0, 0, 0), pl(1.6F, 0, 0, 0), pl(2.1F, 0, 0, 0)))
            .build();

    /** animation.aerbison.laying_idle, 6 s, looping. */
    public static final AnimationDefinition LAYING_IDLE = AnimationDefinition.Builder.withLength(6)
            .looping()
            .addAnimation("bison", ch(ROT, rl(0, 0, 0, 0)))
            .addAnimation("bison", ch(POS, pl(0, 0, 0, 1)))
            .addAnimation("leg_front_right", ch(ROT, rl(0, 0, 0, 80)))
            .addAnimation("leg_front_right", ch(POS, pl(0, -3, 0, 0)))
            .addAnimation("leg_back_right", ch(ROT, rl(0, 0, 0, 82.5F)))
            .addAnimation("leg_back_right", ch(POS, pl(0, -3, 0, 0)))
            .addAnimation("neck", ch(ROT, rl(0, 0, 0, 0)))
            .addAnimation("neck", ch(POS, pl(0, 0, -1, 0)))
            .addAnimation("head", ch(ROT, rl(0, -12.5F, 0, 0), rl(3, -10, 0, 0), rl(6, -12.5F, 0, 0)))
            .addAnimation("head", ch(POS, pl(0, 0, 1, 0)))
            .addAnimation("leg_back_left", ch(ROT, rl(0, 0, 0, -85)))
            .addAnimation("leg_back_left", ch(POS, pl(0, 3, 0, 0)))
            .addAnimation("mane", ch(POS, pl(0, 0, 0, 0)))
            .addAnimation("leg_back", ch(ROT, rl(0, -90, 0, 0)))
            .addAnimation("leg_back", ch(POS, pl(0, 0, -4, 11)))
            .addAnimation("body_and_neck", ch(ROT, rl(0, 0, 0, 0)))
            .addAnimation("body_and_neck", ch(POS, pl(0, 0, -9, 8), pl(3, 0, -8, 8), pl(6, 0, -9, 8)))
            .addAnimation("rear_body", ch(ROT, rl(0, 0, 0, 0)))
            .addAnimation("rear_body", ch(POS, pl(0, 0, 0, -1)))
            .addAnimation("front_legs", ch(ROT, rl(0, -90, 0, 0)))
            .addAnimation("front_legs", ch(POS, pl(0, 0, -7, 10)))
            .addAnimation("leg_front_left", ch(ROT, rl(0, 0, 0, -85)))
            .addAnimation("leg_front_left", ch(POS, pl(0, 3, 0, 0)))
            .addAnimation("nose", ch(ROT, rl(0, 5, 0, 0)))
            .addAnimation("nose", ch(POS, pl(0, 0, 0, 0)))
            .build();

    /** animation.aerbison.stagger, 2 s. */
    public static final AnimationDefinition STAGGER = AnimationDefinition.Builder.withLength(2)
            .addAnimation("head", ch(ROT, rc(0, 0, 0, 0), rc(0.5F, 0, 0, -15), rc(1, 0, 0, 15), rc(1.5F, 0, 0, -10), rc(2, 0, 0, 0)))
            .addAnimation("neck", ch(ROT, rc(0, 0, 0, 0), rc(0.3F, 12, 0, 0), rc(1.7F, 12, 0, 0), rc(2, 0, 0, 0)))
            .addAnimation("leg_front_right", ch(ROT, rl(0, 0, 0, 0), rl(0.15F, -12.5F, 0, 0), rl(0.3F, 1.0704F, 0.135F, 4.9784F), rl(0.35F, 5, 0, 0), rl(0.45F, 0, 0, 0)))
            .addAnimation("leg_front_right", ch(POS, pl(0, 0, 0, 0), pl(0.15F, 0, 0, 2), pl(0.3F, 0, 0, 1), pl(0.45F, 0, 0, 0)))
            .addAnimation("leg_back", ch(ROT, rl(0, 0, 0, 0), rl(0.2F, -10, 0, 0), rl(0.35F, 5, 0, 0), rl(0.45F, 0, 0, 0)))
            .addAnimation("leg_back", ch(POS, pl(0, 0, 0, 0), pl(0.2F, 0, 0, 1), pl(0.35F, 0, 0, 0), pl(0.45F, 0, 0, 0)))
            .addAnimation("body_and_neck", ch(ROT, rc(0, 0, 0, 0), rc(0.15F, -10, 0, -4.2F), rc(0.25F, -6, 0, -7), rc(0.4F, 0, 0, -1), rc(0.6F, 0, 0, 7), rc(0.95F, 0, 0, -5), rc(1.3F, 0, 0, 5), rc(1.65F, 0, 0, -2), rc(2, 0, 0, 0)))
            .addAnimation("body_and_neck", ch(POS, pc(0, 0, 0, 0), pc(0.15F, 0, 0, 3), pc(0.4F, 0, 0, 0)))
            .addAnimation("leg_front_left", ch(ROT, rl(0, 0, 0, 0), rl(0.2F, -12.5F, 0, 0), rl(0.25F, -8.0995F, -0.7062F, -4.95F), rl(0.4F, 5, 0, 0), rl(0.55F, 0, 0, 0)))
            .addAnimation("leg_front_left", ch(POS, pl(0, 0, 0, 0), pl(0.15F, 0, 0, 2), pl(0.35F, 0, 0, 1), pl(0.55F, 0, 0, 0)))
            .build();

    /** animation.aerbison.idle, 10 s, looping. */
    public static final AnimationDefinition IDLE = AnimationDefinition.Builder.withLength(10)
            .looping()
            .addAnimation("body", ch(POS, pl(0, 0, 0, 0), pl(5, 0, 1, 0), pl(10, 0, 0, 0)))
            .addAnimation("neck", ch(POS, pl(0, 0, 0, 0), pl(5, 0, 0.5F, 0), pl(10, 0, 0, 0)))
            .build();

    /** animation.aerbison.gallop, 1.6667 s, looping. */
    public static final AnimationDefinition GALLOP = AnimationDefinition.Builder.withLength(1.6667F)
            .looping()
            .addAnimation("body", ch(ROT, rc(0, 0, 0, 0), rc(0.4167F, 0, 0, 0), rc(0.8333F, 0, 0, 0), rc(1.25F, 0, 0, 0), rc(1.6667F, 0, 0, 0)))
            .addAnimation("body", ch(POS, pc(0, 0, 0, 0), pc(0.4167F, 0, 0, 0), pc(0.8333F, 0, 0, 0), pc(1.25F, 0, 0, 0), pc(1.6667F, 0, 0, 0)))
            .addAnimation("neck", ch(ROT, rc(0, -7.5F, 0, 0), rc(0.4167F, 0, 0, 0), rc(0.625F, 7.5F, 0, 0), rc(0.8333F, -7.5F, 0, 0), rc(1.25F, 0, 0, 0), rc(1.4583F, 7.5F, 0, 0), rc(1.6667F, -7.5F, 0, 0)))
            .addAnimation("neck", ch(POS, pc(0, 0, 4, 0), pc(0.4167F, 0, -1, 0), pc(0.8333F, 0, 4, 0), pc(1.25F, 0, 0, 0), pc(1.6667F, 0, 4, 0)))
            .addAnimation("head", ch(ROT, rc(0, 5, 0, 0), rc(0.4167F, 2.5F, 0, 0), rc(0.625F, -10, 0, 0), rc(0.8333F, 5, 0, 0), rc(1.25F, 5, 0, 0), rc(1.4583F, -10, 0, 0), rc(1.6667F, 5, 0, 0)))
            .addAnimation("head", ch(POS, pc(0.4167F, 0, 0, 0), pc(1.25F, 0, 0, 0)))
            .addAnimation("leg_back_right", ch(ROT, rc(0, -16.08F, 0, 0), rc(0.0833F, 0, 0, 0), rc(0.2917F, 28.03F, 0, 0), rc(0.375F, 28.03F, 0, 0), rc(0.7083F, -30, 0, 0), rc(0.875F, 0, 0, 0), rc(1.125F, 28.03F, 0, 0), rc(1.2083F, 28.03F, 0, 0), rc(1.5833F, -30, 0, 0), rc(1.6667F, -16.08F, 0, 0)))
            .addAnimation("leg_back_left", ch(ROT, rc(0, 0, 0, 0), rc(0.2083F, 28.03F, 0, 0), rc(0.3333F, 28.03F, 0, 0), rc(0.6667F, -30, 0, 0), rc(0.8333F, 0, 0, 0), rc(1.0417F, 28.03F, 0, 0), rc(1.125F, 28.03F, 0, 0), rc(1.5F, -30, 0, 0), rc(1.6667F, 0, 0, 0)))
            .addAnimation("leg_front_right", ch(ROT, rc(0, -6.25F, 0, 0), rc(0.125F, -45, 0, 0), rc(0.2083F, -50, 0, 0), rc(0.625F, 37.5F, 0, 0), rc(0.6667F, 37.5F, 0, 0), rc(1, -45, 0, 0), rc(1.0833F, -50, 0, 0), rc(1.4583F, 37.5F, 0, 0), rc(1.5417F, 37.5F, 0, 0), rc(1.6667F, -6.25F, 0, 0)))
            .addAnimation("mane", ch(ROT, rc(0, -10, 0, 0), rc(0.4167F, 0, 0, 0), rc(0.625F, 10, 0, 0), rc(0.8333F, -10, 0, 0), rc(1.25F, 0, 0, 0), rc(1.4583F, 10, 0, 0), rc(1.6667F, -10, 0, 0)))
            .addAnimation("mane", ch(POS, pc(0, 0, 0, 4), pc(0.4167F, 0, 0, -1), pc(0.8333F, 0, 0, 4), pc(1.25F, 0, 0, 0), pc(1.6667F, 0, 0, 4)))
            .addAnimation("leg_back", ch(POS, pc(0, 0, 0, -3), pc(0.4167F, 0, 1, 1), pc(0.6667F, 0, 3, 0), pc(0.8333F, 0, 0, -3), pc(1.25F, 0, 1, 1), pc(1.5F, 0, 3, 0), pc(1.6667F, 0, 0, -3)))
            .addAnimation("rear_body", ch(ROT, rc(0, -15, 0, 0), rc(0.4167F, 7.5F, 0, 0), rc(0.625F, 0, 0, 0), rc(0.8333F, -15, 0, 0), rc(1.25F, 7.5F, 0, 0), rc(1.4583F, 0, 0, 0), rc(1.6667F, -15, 0, 0)))
            .addAnimation("rear_body", ch(POS, pc(0, 0, 0, 1), pc(0.4167F, 0, 0, 1), pc(0.625F, 0, 0, 3), pc(0.8333F, 0, 0, 1), pc(1.25F, 0, 0, 1), pc(1.4583F, 0, 0, 3), pc(1.6667F, 0, 0, 1)))
            .addAnimation("front_legs", ch(POS, pc(0, 0, 4, 0), pc(0.2083F, 0, 2, 0), pc(0.4167F, 0, -1, 0), pc(0.8333F, 0, 4, 0), pc(1.0417F, 0, 2, 0), pc(1.25F, 0, 0, 0), pc(1.6667F, 0, 4, 0)))
            .addAnimation("leg_front_left", ch(ROT, rc(0, -15, 0, 0), rc(0.125F, -50, 0, 0), rc(0.5833F, 37.5F, 0, 0), rc(0.625F, 37.5F, 0, 0), rc(0.9167F, -45, 0, 0), rc(1, -50, 0, 0), rc(1.375F, 37.5F, 0, 0), rc(1.4583F, 37.5F, 0, 0), rc(1.6667F, -15, 0, 0)))
            .addAnimation("nose", ch(POS, pc(0, 0, 0, 0), pc(0.1667F, 0, 0.5F, 0), pc(0.4167F, 0, 0, 0), pc(0.5833F, 0, -0.5F, 0), pc(0.8333F, 0, 0, 0), pc(1, 0, 0.5F, 0), pc(1.25F, 0, 0, 0), pc(1.4167F, 0, -0.5F, 0), pc(1.6667F, 0, 0, 0)))
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

    private AerbisonAnimations() {
    }
}
