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
package com.linc.alteredaether.mixin;

import com.linc.alteredaether.compat.boulder.FallingBoulders;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Create Simulated keeps Sable bodies out of the void with its "end sea": below a set height, every body in the
 * dimension is slowed and pushed back up. Its loop skips a body whose centre of mass isn't known, so a falling boulder
 * (see compat/boulder) is reported as having none, and falls through while every other body is treated as before. A
 * boulder that does stall in the end sea is removed by the boulder manager once it has lived too long.
 *
 * <p>Applies only with Simulated and Sable loaded (see AAMixinPlugin); {@code require = 0} so a Simulated update can't
 * stop the game from starting.
 */
@Pseudo
@Mixin(targets = "dev.simulated_team.simulated.content.end_sea.EndSeaPhysics")
public abstract class SimulatedEndSeaMixin {
    @ModifyExpressionValue(method = "physicsTick",
            at = @At(value = "INVOKE", target = "Ldev/ryanhcode/sable/api/physics/mass/MassData;getCenterOfMass()Lorg/joml/Vector3dc;"),
            require = 0)
    private Vector3dc altered_aether$boulderIgnoresEndSea(Vector3dc centre, @Local ServerSubLevel subLevel) {
        return FallingBoulders.isBoulder(subLevel) ? null : centre;
    }
}
