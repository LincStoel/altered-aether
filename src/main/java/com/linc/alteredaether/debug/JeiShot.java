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
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.List;
import java.util.Optional;

/**
 * A screenshot of a JEI page, without anyone at the keyboard: with the system property
 * {@code altered_aether.jei_shot} set to a recipe category's id (the client run's {@code -PaaJeiShot}), once in a
 * world it opens that page, saves {@code screenshots/tour/jei_<path>.png} and quits. Loaded only with JEI.
 */
public final class JeiShot {
    public static final String PROPERTY = "altered_aether.jei_shot";
    /** Ticks in the world before the page opens, for the world to settle; then before the shot, for it to draw. */
    private static final int ENTER = 100, OPEN = 40;

    private final IJeiRuntime runtime;
    private final ResourceLocation category;
    private int ticks;

    private JeiShot(IJeiRuntime runtime, ResourceLocation category) {
        this.runtime = runtime;
        this.category = category;
    }

    /** Starts the shot if the property asks for one. */
    public static void start(IJeiRuntime runtime) {
        String spec = System.getProperty(PROPERTY, "");
        if (spec.isEmpty()) {
            return;
        }
        ResourceLocation id = spec.contains(":") ? ResourceLocation.parse(spec) : AlteredAether.id(spec);
        JeiShot shot = new JeiShot(runtime, id);
        NeoForge.EVENT_BUS.addListener(shot::onTick);
    }

    private void onTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }
        ticks++;
        if (ticks == ENTER) {
            Optional<RecipeType<?>> type = runtime.getJeiHelpers().getRecipeType(category);
            if (type.isEmpty()) {
                AlteredAether.LOGGER.error("JEI shot: no recipe category {}", category);
                minecraft.stop();
                return;
            }
            runtime.getRecipesGui().showTypes(List.of(type.get()));
        } else if (ticks == ENTER + OPEN) {
            new java.io.File(minecraft.gameDirectory, "screenshots/tour").mkdirs();
            Screenshot.grab(minecraft.gameDirectory, "tour/jei_" + category.getPath() + ".png",
                    minecraft.getMainRenderTarget(), message -> AlteredAether.LOGGER.info("JEI shot: {}", message.getString()));
        } else if (ticks == ENTER + OPEN + 10) {
            minecraft.stop();
        }
    }
}
