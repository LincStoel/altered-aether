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
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Tall-Aether Y remap, part 1: marks the generation contexts that belong to the tall Aether, so the anchor mixins
 * know when to remap.
 */
@Mixin(WorldGenerationContext.class)
public abstract class WorldGenerationContextMixin implements TallRemap.Context {
    @Shadow
    @Final
    private int minY;

    @Unique
    private boolean altered_aether$tall;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void altered_aether$detectTallAether(ChunkGenerator generator, LevelHeightAccessor level, CallbackInfo ci) {
        altered_aether$tall = TallRemap.isTallAether(generator, minY);
    }

    @Override
    public boolean altered_aether$isTallAether() {
        return altered_aether$tall;
    }
}
