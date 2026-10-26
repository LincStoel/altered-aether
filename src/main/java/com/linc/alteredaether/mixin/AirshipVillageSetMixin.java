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

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Integrated Villages' airship village belongs to the Aether here (see AACompatProvider), so its own structure is kept
 * out of every dimension's structure state: any set that places it is treated as having no biomes, and never placed.
 * Our set places our copy of the structure, which this leaves alone. Without the mod, nothing matches.
 */
@Mixin(ChunkGeneratorStructureState.class)
public abstract class AirshipVillageSetMixin {
    private static final ResourceLocation AIRSHIP_VILLAGE =
            ResourceLocation.fromNamespaceAndPath("integrated_villages", "airship_village");

    @Inject(method = "hasBiomesForStructureSet", at = @At("HEAD"), cancellable = true)
    private static void altered_aether$dropAirshipVillage(StructureSet set, BiomeSource biomes,
                                                          CallbackInfoReturnable<Boolean> cir) {
        if (set.structures().stream().anyMatch(entry -> entry.structure().is(AIRSHIP_VILLAGE))) {
            cir.setReturnValue(false);
        }
    }
}
