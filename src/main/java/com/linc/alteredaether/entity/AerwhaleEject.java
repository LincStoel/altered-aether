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
package com.linc.alteredaether.entity;

/**
 * The numbers behind leaping off a ridden aerwhale: the rider charges a leap, then springs straight up off its back,
 * keeping the whale's speed, while the whale coasts on long enough for them to come down on it again.
 */
public final class AerwhaleEject {
    /** How high the shortest charge throws the rider, in blocks: enough to clear the seat. */
    public static final double MIN_HEIGHT = 2;
    /** How high a full charge throws the rider, in blocks. */
    public static final double MAX_HEIGHT = 10;
    /** Ticks of holding the key to charge fully. */
    public static final int CHARGE_TICKS = 20;
    /** Ticks a whale left without a pilot coasts on before it turns to follow its owner. */
    public static final int COAST_TICKS = 50;
    /** The most speed, in blocks a tick, a rider's client may claim the whale had. */
    public static final double MAX_INHERITED_SPEED = 1.5;
    /** A player's vertical drag in the air, per tick. */
    private static final double DRAG = 0.98;
    /**
     * The least gravity the throw is worked out for. The gravity attribute can be set to 0 or below, under which a
     * thrown player never comes down and the loops below would never end.
     */
    private static final double MIN_GRAVITY = 0.01;

    /** How high a charge of {@code charge} (0 to 1) throws the rider. */
    public static double heightFor(float charge) {
        return MIN_HEIGHT + (MAX_HEIGHT - MIN_HEIGHT) * (Float.isNaN(charge) ? 0 : Math.clamp(charge, 0, 1));
    }

    /** The upward speed that carries a player {@code height} blocks up under {@code gravity}, found by bisection. */
    public static double launchSpeed(double height, double gravity) {
        gravity = Math.max(gravity, MIN_GRAVITY);
        double low = 0;
        double high = 5;
        for (int i = 0; i < 40; i++) {
            double mid = (low + high) / 2;
            if (apex(mid, gravity) < height) {
                low = mid;
            } else {
                high = mid;
            }
        }
        return high;
    }

    /** How high a player thrown up at {@code speed} rises, moving then slowing each tick as a player does. */
    public static double apex(double speed, double gravity) {
        gravity = Math.max(gravity, MIN_GRAVITY);
        double y = 0;
        double vy = speed;
        while (vy > 0) {
            y += vy;
            vy = (vy - gravity) * DRAG;
        }
        return y;
    }

    /** Ticks a player thrown up at {@code speed} takes to come back down to where they started. */
    public static int airtime(double speed, double gravity) {
        gravity = Math.max(gravity, MIN_GRAVITY);
        double y = 0;
        double vy = speed;
        int ticks = 0;
        do {
            y += vy;
            vy = (vy - gravity) * DRAG;
            ticks++;
        } while (y > 0 && ticks < 1000);
        return ticks;
    }

    private AerwhaleEject() {
    }
}
