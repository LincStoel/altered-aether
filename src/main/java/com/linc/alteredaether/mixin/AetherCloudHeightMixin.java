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

import com.aetherteam.aether.client.renderer.level.AetherSkyRenderEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import org.spongepowered.asm.mixin.Mixin;

/**
 * In the tall Aether the cloud layer sits at height -28 instead of 9.5, down near the bottom of the extended world. The
 * stock Aether keeps its own. Only the Aether's effects change; the Overworld's clouds keep theirs.
 *
 * <p>The height is a constructor literal handed to {@link DimensionSpecialEffects}, so the override replaces the
 * getter the Aether's cloud renderer reads.
 */
@Mixin(AetherSkyRenderEffects.class)
public abstract class AetherCloudHeightMixin extends DimensionSpecialEffects {
    private static final float TALL_CLOUD_HEIGHT = -28.0F;

    private AetherCloudHeightMixin() {
        super(0.0F, false, SkyType.NONE, false, false);
    }

    @Override
    public float getCloudHeight() {
        ClientLevel level = Minecraft.getInstance().level;
        return level != null && level.getMinBuildHeight() < 0 ? TALL_CLOUD_HEIGHT : super.getCloudHeight();
    }
}
