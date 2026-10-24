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

import com.aetherteam.aether.world.structure.LargeAercloudStructure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * The large aercloud's bounding box starts at {@code Math.max(y - 16, 0)}, where 0 is the Aether's old minimum build
 * height. In the tall Aether (minimum -64) a low cloud's box comes out inverted (floor 0, top below 0), which throws
 * on a worldgen worker and brings the server down. The floor becomes the cloud's real base instead; a box reaching
 * below the world is harmless.
 *
 * <p>No data or event can reach this: the clamp is a literal inside the structure's piece generator.
 */
@Mixin(LargeAercloudStructure.class)
public abstract class LargeAercloudStructureMixin {
    @ModifyArg(method = "lambda$generatePieces$7", at = @At(value = "INVOKE", target = "Ljava/lang/Math;max(II)I"),
            index = 1)
    private static int altered_aether$unclampedFloor(int floor) {
        return Integer.MIN_VALUE;
    }
}
