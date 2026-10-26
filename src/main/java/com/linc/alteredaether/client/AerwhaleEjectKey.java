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
package com.linc.alteredaether.client;

import com.linc.alteredaether.AlteredAether;
import com.linc.alteredaether.entity.AerwhaleEject;
import com.linc.alteredaether.entity.AerwhaleEjectPayload;
import com.linc.alteredaether.entity.TamedAerwhale;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.settings.IKeyConflictContext;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * The key that leaps off a ridden aerwhale, J unless rebound. Holding it charges a leap, shown on the jump
 * bar's place in the HUD; letting go springs the rider up off the whale's back.
 */
@EventBusSubscriber(modid = AlteredAether.MOD_ID, value = Dist.CLIENT)
public final class AerwhaleEjectKey {
    /** Our own section of the controls screen. */
    public static final String CATEGORY = "key.categories.altered_aether";

    /**
     * Only in play while riding an aerwhale, so it can share a key with something used on foot without showing as a
     * clash in the controls screen.
     */
    private static final IKeyConflictContext RIDING_AERWHALE = new IKeyConflictContext() {
        @Override
        public boolean isActive() {
            Minecraft minecraft = Minecraft.getInstance();
            return minecraft.screen == null && minecraft.player != null
                    && minecraft.player.getVehicle() instanceof TamedAerwhale;
        }

        @Override
        public boolean conflicts(IKeyConflictContext other) {
            return this == other;
        }
    };

    public static final KeyMapping KEY = new KeyMapping("key.altered_aether.aerwhale_eject", RIDING_AERWHALE,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J, CATEGORY);

    private static final ResourceLocation BAR_BACKGROUND = ResourceLocation.withDefaultNamespace("hud/jump_bar_background");
    private static final ResourceLocation BAR_PROGRESS = ResourceLocation.withDefaultNamespace("hud/jump_bar_progress");

    private static int chargeTicks;

    /** How far the leap has charged, from 0 to 1. */
    public static float charge() {
        return Math.min(1, chargeTicks / (float) AerwhaleEject.CHARGE_TICKS);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || !(minecraft.player.getVehicle() instanceof TamedAerwhale whale)
                || minecraft.screen != null) {
            // a screen opening lets go of every key; that isn't a leap
            chargeTicks = 0;
            return;
        }
        if (KEY.isDown()) {
            chargeTicks = Math.min(chargeTicks + 1, AerwhaleEject.CHARGE_TICKS);
        } else if (chargeTicks > 0) {
            PacketDistributor.sendToServer(new AerwhaleEjectPayload(charge(), velocity(whale)));
            chargeTicks = 0;
        }
    }

    /** How far the whale moved this tick: what the pilot flew it, or what this client eased it along. */
    private static Vec3 velocity(Entity whale) {
        return whale.position().subtract(whale.xo, whale.yo, whale.zo);
    }

    /** The charge bar, drawn over the jump bar's place, where a horse's or camel's charge shows. */
    static void renderBar(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        if (chargeTicks <= 0 || minecraft.options.hideGui || minecraft.player == null) {
            return;
        }
        int x = graphics.guiWidth() / 2 - 91;
        int y = graphics.guiHeight() - 29;
        RenderSystem.enableBlend();
        graphics.blitSprite(BAR_BACKGROUND, x, y, 182, 5);
        int filled = (int) (charge() * 183.0F);
        if (filled > 0) {
            graphics.blitSprite(BAR_PROGRESS, 182, 5, 0, 0, x, y, filled, 5);
        }
        RenderSystem.disableBlend();
    }

    private AerwhaleEjectKey() {
    }
}
