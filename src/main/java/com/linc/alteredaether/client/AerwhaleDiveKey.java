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
import com.linc.alteredaether.entity.AerwhaleDivePayload;
import com.linc.alteredaether.entity.TamedAerwhale;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * The key that dives a ridden aerwhale, space unless rebound. Holding it charges the dive as jump charges a horse's
 * leap, shown on the jump bar's place in the HUD; letting go sends the whale down its arc. Only the pilot dives.
 */
@EventBusSubscriber(modid = AlteredAether.MOD_ID, value = Dist.CLIENT)
public final class AerwhaleDiveKey {
    public static final KeyMapping KEY = new KeyMapping("key.altered_aether.aerwhale_dive",
            AerwhaleEjectKey.RIDING_AERWHALE, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_SPACE,
            AerwhaleEjectKey.CATEGORY);

    /** Ticks to a full charge, about as long as a horse's jump bar takes to fill. */
    private static final int CHARGE_TICKS = 10;
    private static final ResourceLocation BAR_BACKGROUND = ResourceLocation.withDefaultNamespace("hud/jump_bar_background");
    private static final ResourceLocation BAR_PROGRESS = ResourceLocation.withDefaultNamespace("hud/jump_bar_progress");

    private static int chargeTicks;

    private static float charge() {
        return Math.min(1, chargeTicks / (float) CHARGE_TICKS);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || !(minecraft.player.getVehicle() instanceof TamedAerwhale whale)
                || minecraft.screen != null || whale.getControllingPassenger() != minecraft.player) {
            // a screen opening lets go of every key; that isn't a dive
            chargeTicks = 0;
            return;
        }
        if (KEY.isDown()) {
            if (whale.canDive()) {
                chargeTicks = Math.min(chargeTicks + 1, CHARGE_TICKS);
            }
        } else if (chargeTicks > 0) {
            int power = Math.round(charge() * 100);
            chargeTicks = 0;
            if (whale.canDive()) {
                whale.startDive(power);
                PacketDistributor.sendToServer(new AerwhaleDivePayload(power));
            }
        }
    }

    /** The charge bar, drawn where a horse's jump bar shows. */
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

    private AerwhaleDiveKey() {
    }
}
