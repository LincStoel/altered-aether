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

import com.linc.alteredaether.worldgen.tall.TallRemap;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Tall-Aether Y remap, part 4: Deep Aether's surface structures (altar camps, campfires, combiner corridors) are
 * discarded when the ground lies outside a height range written as plain numbers for the stock Aether. In the tall
 * Aether that range is stretched like every other height, or nearly every camp would be thrown away. Only applies
 * when Deep Aether is loaded; {@code require = 0} so a Deep Aether update can't stop the game from starting.
 */
@Pseudo
@Mixin(targets = "io.github.razordevs.deep_aether.world.structure.HeightSpawningChecks")
public abstract class DeepAetherHeightChecksMixin {
    @ModifyVariable(method = "checkHeight", at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0)
    private int altered_aether$remapBelow(int below, @Local(argsOnly = true) Structure.GenerationContext context) {
        return TallRemap.active(context) ? TallRemap.remap(below) : below;
    }

    @ModifyVariable(method = "checkHeight", at = @At("HEAD"), argsOnly = true, ordinal = 1, require = 0)
    private int altered_aether$remapAbove(int above, @Local(argsOnly = true) Structure.GenerationContext context) {
        return TallRemap.active(context) ? TallRemap.remap(above) : above;
    }
}
