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

import com.aetherteam.aether.entity.passive.Aerwhale;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;
import java.util.EnumSet;

/**
 * A wild aerwhale drawn to a player holding its taming food, the way animals follow wheat. The Aether's steering always
 * flies at full speed and can't hover, so once close the whale circles the player lazily overhead rather than stopping.
 * That steering also turns very slowly, half a degree a tick, so the whale would take most of a minute to come round;
 * a tempted whale turns faster, toward the food. After the player puts the food away it pays no attention to food for a
 * few seconds, as vanilla's tempted animals do.
 */
public final class AerwhaleTemptGoal extends Goal {
    private static final double RANGE = 16;
    private static final double CIRCLE_RADIUS = 6;
    private static final double CIRCLE_HEIGHT = 3;
    private static final int CALM_DOWN = 100;
    /** Degrees a tick a tempted whale turns toward the food. */
    private static final float TURN = 2.5F;
    private static final float MAX_PITCH = 40;

    private final Aerwhale whale;
    @Nullable
    private Player player;
    private int calmDown;

    public AerwhaleTemptGoal(Aerwhale whale) {
        this.whale = whale;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (calmDown > 0) {
            calmDown--;
            return false;
        }
        player = whale.level().getNearestPlayer(whale.getX(), whale.getY(), whale.getZ(), RANGE,
                entity -> entity instanceof Player candidate && tempts(candidate));
        return player != null && !whale.isVehicle();
    }

    @Override
    public boolean canContinueToUse() {
        return player != null && player.isAlive() && tempts(player) && !whale.isVehicle()
                && whale.distanceToSqr(player) < RANGE * RANGE * 2;
    }

    private static boolean tempts(Player player) {
        return !player.isSpectator() && (TamedAerwhale.isTamingFood(player.getMainHandItem())
                || TamedAerwhale.isTamingFood(player.getOffhandItem()));
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void stop() {
        player = null;
        calmDown = CALM_DOWN;
    }

    @Override
    public void tick() {
        if (TamedAerwhale.overhead(whale, player)) {
            // right above it: turning toward them would only spin it round under their feet
            return;
        }
        double dx = whale.getX() - player.getX();
        double dz = whale.getZ() - player.getZ();
        double x = player.getX();
        double z = player.getZ();
        if (dx * dx + dz * dz < (CIRCLE_RADIUS + 3) * (CIRCLE_RADIUS + 3)) {
            // aim a little ahead round the circle, so it keeps coming round
            double angle = Math.atan2(dz, dx) + 0.6;
            x += Math.cos(angle) * CIRCLE_RADIUS;
            z += Math.sin(angle) * CIRCLE_RADIUS;
        }
        double y = player.getY() + CIRCLE_HEIGHT;
        whale.getMoveControl().setWantedPosition(x, y, z, 1.0);

        // the Aether's steering then turns on from wherever this leaves it
        double tx = x - whale.getX();
        double ty = y - whale.getY();
        double tz = z - whale.getZ();
        // no tighter than a tamed whale's circle, so it comes round in a sweep rather than on the spot
        float turn = Math.min(TURN, TamedAerwhale.maxTurn(whale.getDeltaMovement().length()));
        float yaw = Mth.approachDegrees(whale.getYRot(), (float) (Mth.atan2(tz, tx) * Mth.RAD_TO_DEG) - 90.0F, turn);
        float pitch = Mth.approachDegrees(whale.getXRot(), Mth.clamp(
                (float) (Mth.atan2(ty, Math.sqrt(tx * tx + tz * tz)) * Mth.RAD_TO_DEG), -MAX_PITCH, MAX_PITCH), TURN / 2);
        whale.setYRot(yaw);
        whale.setYRotData(yaw);
        whale.setXRot(pitch);
        whale.setXRotData(pitch);
    }
}
