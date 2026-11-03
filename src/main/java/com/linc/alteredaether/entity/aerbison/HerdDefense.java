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

import com.linc.alteredaether.entity.Aerbison;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.List;

/**
 * How a herd answers an attack on one of its own. From six or more blocks off, the adults stampede through the
 * attacker; from closer, or with no room to run, the bison and the adults near it go for the attacker with their
 * horns. A lone bison always fights. Calves just run.
 */
public final class HerdDefense {
    private static final int LOCKOUT_TICKS = 200;
    private static final double MIN_STAMPEDE_RANGE = 6;
    private static final double MELEE_RADIUS = 12;

    public static void onHurt(Aerbison bison, LivingEntity attacker) {
        if (!(bison.level() instanceof ServerLevel level) || bison.stampede != null || bison.isBaby()) {
            return;
        }
        if (attacker instanceof Player player && (player.isCreative() || player.isSpectator())) {
            return;
        }
        List<Aerbison> herd = StampedeGoal.nearbyAdults(level, bison);
        if (herd.size() >= 2 && bison.defendLockout <= 0 && bison.distanceTo(attacker) >= MIN_STAMPEDE_RANGE) {
            Aerbison leader = herd.stream().min((a, b) -> Float.compare(a.distanceTo(attacker), b.distanceTo(attacker)))
                    .orElse(bison);
            StampedePlanner.Plan plan = StampedePlanner.plan(level, leader, herd.size(), attacker);
            if (plan != null) {
                for (Aerbison member : herd) {
                    member.defendLockout = LOCKOUT_TICKS;
                    member.setTarget(null);
                }
                Stampede.start(level, leader, herd.stream().filter(b -> b != leader).toList(), plan);
                return;
            }
        }
        for (Aerbison member : herd) {
            if (member.distanceTo(attacker) <= MELEE_RADIUS && member.getTarget() == null) {
                member.setTarget(attacker);
            }
        }
        if (bison.getTarget() == null) {
            bison.setTarget(attacker);
        }
    }

    private HerdDefense() {
    }
}
