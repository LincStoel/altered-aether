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

import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps the Aether's airship village at least {@link #MIN_BLOCKS} blocks from 0,0: a placement with the village's salt
 * is never a structure chunk inside that circle. Vanilla placements have no origin exclusion of their own, and this is
 * also what /locate and explorer maps ask, so they skip the excluded cells too.
 */
@Mixin(StructurePlacement.class)
public abstract class AirshipVillageSpawnExclusionMixin {
    private static final int AIRSHIP_SALT = 1982700830;
    private static final long MIN_BLOCKS = 1500;

    @Shadow
    protected abstract int salt();

    @Inject(method = "isStructureChunk", at = @At("HEAD"), cancellable = true)
    private void altered_aether$keepAirshipVillageAwayFromSpawn(ChunkGeneratorStructureState state, int chunkX,
                                                                int chunkZ, CallbackInfoReturnable<Boolean> cir) {
        if (this.salt() != AIRSHIP_SALT) {
            return;
        }
        long x = chunkX * 16L + 8;
        long z = chunkZ * 16L + 8;
        if (x * x + z * z < MIN_BLOCKS * MIN_BLOCKS) {
            cir.setReturnValue(false);
        }
    }
}
