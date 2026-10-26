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

import com.linc.alteredaether.config.AACommonConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * With {@code compat.disable_overworld_archwood} on, Ars Nouveau's and Ars Elemental's overworld TerraBlender regions
 * are never registered, so their archwood forests don't generate. Both regions hold nothing but archwood biomes, and
 * Ars Elemental's surface rules only touch its own. They register in common setup, after the common config has
 * loaded. {@code require = 0} so an Ars update can't stop the game from starting; the trees they scatter outside the
 * forests are removed by OverworldArchwoodModifier.
 */
public final class ArsArchwoodRegionMixins {
    @Pseudo
    @Mixin(targets = "com.hollingsworth.arsnouveau.common.world.Terrablender")
    public abstract static class ArsNouveau {
        @Inject(method = "registerBiomes", at = @At("HEAD"), cancellable = true, require = 0)
        private static void altered_aether$skipArchwoodForest(CallbackInfo ci) {
            if (AACommonConfig.DISABLE_OVERWORLD_ARCHWOOD.get()) {
                ci.cancel();
            }
        }
    }

    @Pseudo
    @Mixin(targets = "alexthw.ars_elemental.world.TerrablenderAE")
    public abstract static class ArsElemental {
        @Inject(method = "registerBiomes", at = @At("HEAD"), cancellable = true, require = 0)
        private static void altered_aether$skipElementalForests(CallbackInfo ci) {
            if (AACommonConfig.DISABLE_OVERWORLD_ARCHWOOD.get()) {
                ci.cancel();
            }
        }
    }
}
