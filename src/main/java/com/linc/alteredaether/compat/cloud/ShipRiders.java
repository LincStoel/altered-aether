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

/**
 * Who is riding a Sable body: standing on it, or in a seat on it. Aerclouds don't collide with them, as they don't with
 * the body itself, so a cloud a ship sails through doesn't shove its crew off the deck (see AercloudDragMixin and
 * PhasedAercloudBlock). Asked on both sides: the client works out its own player's collisions too.
 *
 * <p>Safe to load without Sable; only the inner class touches it.
 */
public final class ShipRiders {
    private ShipRiders() {
    }

    public static boolean onShip(Entity entity) {
        return entity != null && AercloudPhasing.enabled() && WithSable.onShip(entity);
    }

    private static final class WithSable {
        static boolean onShip(Entity entity) {
            return Sable.HELPER.getTrackingOrVehicleSubLevel(entity) != null;
        }
    }
}
