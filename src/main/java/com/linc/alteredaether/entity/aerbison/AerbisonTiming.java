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
package com.linc.alteredaether.entity.aerbison;

/**
 * Entity event ids and tick timings the server and client agree on. The server lands each effect on the tick its
 * animation's keyframe does (see client/AerbisonAnimations), so a hit, a launch or a bite is seen as it happens. The
 * ids avoid every one vanilla's living entities handle (up to 63).
 */
public final class AerbisonTiming {
    public static final byte EVENT_GRAZE = 80;
    public static final byte EVENT_HEAD_SHAKE = 81;
    public static final byte EVENT_WINDUP = 82;
    public static final byte EVENT_ROUSE = 83;
    public static final byte EVENT_STOMP = 84;
    public static final byte EVENT_HORN_TOSS = 85;
    public static final byte EVENT_WALLOW = 86;
    public static final byte EVENT_REFUSE = 87;
    public static final byte EVENT_LAND = 88;

    public static final int GRAZE_TICKS = 48;
    /** The bite: when a grazing calf's growth is sped up. */
    public static final int GRAZE_BITE_TICK = 24;
    public static final int HEAD_SHAKE_TICKS = 20;
    public static final int WINDUP_TICKS = 26;
    public static final int ROUSE_TICKS = 10;
    public static final int STOMP_TICKS = 18;
    /** The tick the stamp lands and the gust fires. */
    public static final int STOMP_LAUNCH_TICK = 9;
    public static final int HORN_TOSS_TICKS = 12;
    public static final int HORN_TOSS_HIT_TICK = 7;
    public static final int WALLOW_TICKS = 52;
    public static final int REFUSE_TICKS = 14;
    public static final int STAGGER_TICKS = 40;

    private AerbisonTiming() {
    }
}
