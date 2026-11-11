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
package com.linc.alteredaether.entity.golem;

/**
 * The gravitite golem's one-shot animations: the entity event that starts each, and how many ticks it runs. A new
 * Blockbench animation of a different length wants its number changed here too (see client/golem/
 * GravititeGolemAnimations).
 */
public final class GolemTiming {
    public static final byte EVENT_SIT_DOWN = 70;
    public static final byte EVENT_STAND_UP = 71;
    public static final byte EVENT_REPEL = 72;
    public static final byte EVENT_SHAKE_HEAD = 73;
    public static final byte EVENT_LINK_PULSE = 74;

    public static final int SIT_DOWN_TICKS = 15;
    public static final int STAND_UP_TICKS = 15;
    public static final int REPEL_TICKS = 10;
    public static final int SHAKE_HEAD_TICKS = 16;
    public static final int LINK_PULSE_TICKS = 10;

    private GolemTiming() {
    }
}
