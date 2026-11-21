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
package com.linc.alteredaether.compat.cloud;

import dev.ryanhcode.sable.Sable;
import net.minecraft.world.entity.Entity;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Who is riding a Sable body: standing on it, or in a seat on it. Aerclouds don't collide with them, as they don't with
 * the body itself, so a cloud a ship sails through doesn't shove its crew off the deck (see AercloudDragMixin and
 * PhasedAercloudBlock). Asked on both sides: the client works out its own player's collisions too.
 *
 * <p>Sable stops counting someone as on a body the moment they leave its deck, as in a jump, so anyone seen on one in
 * the last {@link #GRACE} ticks still counts: otherwise a jump inside a cloud would meet the cloud, and a blue one
 * throws them high above the ship.
 *
 * <p>Safe to load without Sable; only the inner class touches it.
 */
public final class ShipRiders {
    /** Ticks someone still counts as riding after Sable last had them on a body; longer than a jump. */
    public static final int GRACE = 20;

    /** Each entity's own tick count when it was last on a body; one map serves both sides, as each has its own entities. */
    private static final Map<Entity, Integer> LAST_ON = Collections.synchronizedMap(new WeakHashMap<>());

    private ShipRiders() {
    }

    public static boolean onShip(Entity entity) {
        if (entity == null || !AercloudPhasing.enabled()) {
            return false;
        }
        Integer last = LAST_ON.get(entity);
        if (WithSable.onShip(entity)) {
            if (last == null || last != entity.tickCount) {
                LAST_ON.put(entity, entity.tickCount);
            }
            return true;
        }
        return last != null && entity.tickCount - last <= GRACE;
    }

    private static final class WithSable {
        static boolean onShip(Entity entity) {
            return Sable.HELPER.getTrackingOrVehicleSubLevel(entity) != null;
        }
    }
}
