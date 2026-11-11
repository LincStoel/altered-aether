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
package com.linc.alteredaether.client.golem;

import com.linc.alteredaether.AlteredAether;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.RenderStateShard;
import org.lwjgl.opengl.GL11;

/**
 * A render state that turns the depth test off, for glows meant to be seen through walls. Vanilla's own "always"
 * depth state only leaves the test alone, and late in the frame (after particles) it is still on, so walls would hide
 * what's drawn. Goes in a render type's layering slot, beside its depth test state. (As in Spectral Enchantments.)
 */
final class ThroughWalls {
    static final RenderStateShard.LayeringStateShard SHARD = new RenderStateShard.LayeringStateShard(
            AlteredAether.MOD_ID + "_through_walls", ThroughWalls::depthOff, ThroughWalls::depthBack);

    /** Whether the depth test was on before, so it's put back as it was. Render thread. */
    private static boolean depthWasOn;

    private ThroughWalls() {
    }

    private static void depthOff() {
        depthWasOn = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        RenderSystem.disableDepthTest();
    }

    private static void depthBack() {
        if (depthWasOn) {
            RenderSystem.enableDepthTest();
        }
    }
}
