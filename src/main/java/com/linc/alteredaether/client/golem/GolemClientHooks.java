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

import com.linc.alteredaether.entity.golem.GolemPayloads;
import com.linc.alteredaether.entity.golem.PebbleInfo;

/** Where the golem payloads land on a client (see GolemPayloads.Registration). */
public final class GolemClientHooks {
    private GolemClientHooks() {
    }

    public static void glow(GolemPayloads.Glow payload) {
        GolemGlow.add(payload);
    }

    public static void links(GolemPayloads.Links payload) {
        PebbleInfo.put(payload);
    }

    public static void flight(GolemPayloads.Flight payload) {
        GolemFlights.add(payload);
    }

    public static void flightEnd(GolemPayloads.FlightEnd payload) {
        GolemFlights.end(payload);
    }
}
