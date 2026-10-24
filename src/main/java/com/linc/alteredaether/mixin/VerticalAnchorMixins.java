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
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Tall-Aether Y remap, part 2: absolute and above-bottom anchors, which every height provider and height-range
 * placement resolves through, are stretched onto the tall Aether (see TallRemap). Below-top anchors need no change.
 */
public final class VerticalAnchorMixins {
    @Mixin(VerticalAnchor.Absolute.class)
    public abstract static class Absolute {
        @Shadow
        @Final
        private int y;

        @Inject(method = "resolveY", at = @At("HEAD"), cancellable = true)
        private void altered_aether$remap(WorldGenerationContext context, CallbackInfoReturnable<Integer> cir) {
            if (TallRemap.active(context)) {
                cir.setReturnValue(TallRemap.remap(y));
            }
        }
    }

    @Mixin(VerticalAnchor.AboveBottom.class)
    public abstract static class AboveBottom {
        @Shadow
        @Final
        private int offset;

        @Inject(method = "resolveY", at = @At("HEAD"), cancellable = true)
        private void altered_aether$remap(WorldGenerationContext context, CallbackInfoReturnable<Integer> cir) {
            if (TallRemap.active(context)) {
                cir.setReturnValue(context.getMinGenY() + TallRemap.remapOffset(offset));
            }
        }
    }

    private VerticalAnchorMixins() {
    }
}
