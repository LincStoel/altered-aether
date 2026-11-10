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
package com.linc.alteredaether.debug;

import com.linc.alteredaether.AlteredAether;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;

/** Dev runs only: hands JEI's runtime to {@link JeiShot}, for the client run's {@code -PaaJeiShot}. */
@JeiPlugin
public final class DevJeiPlugin implements IModPlugin {
    @Override
    public ResourceLocation getPluginUid() {
        return AlteredAether.id("jei_dev");
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime runtime) {
        JeiShot.start(runtime);
    }
}
