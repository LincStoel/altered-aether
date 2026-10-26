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
import com.linc.alteredaether.entity.TamedAerwhale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

import javax.annotation.Nullable;

/**
 * Turns the view of a player standing on an aerwhale's back along with the whale, frame by frame. A player's camera
 * shows their yaw exactly as it stands rather than easing it between ticks, since the mouse moves it every frame, so
 * turning it once a tick, as the deck carries everything else, makes the view judder through every turn.
 */
@EventBusSubscriber(modid = AlteredAether.MOD_ID, value = Dist.CLIENT)
public final class AerwhaleDeckCamera {
    /** The whale the view last turned with, and its yaw as last drawn. */
    @Nullable
    private static TamedAerwhale whale;
    private static float lastYaw;

    @SubscribeEvent
    public static void onFrame(RenderFrameEvent.Pre event) {
        LocalPlayer player = Minecraft.getInstance().player;
        TamedAerwhale under = TamedAerwhale.deckUnderLocalPlayer();
        if (player == null || under == null || under.level() != player.level() || player.isPassenger()) {
            whale = null;
            return;
        }
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(true);
        float yaw = Mth.rotLerp(partialTick, under.yBodyRotO, under.yBodyRot);
        if (under == whale) {
            float turn = Mth.wrapDegrees(yaw - lastYaw);
            if (turn != 0) {
                player.setYRot(player.getYRot() + turn);
                player.yRotO += turn;
                player.setYHeadRot(player.getYHeadRot() + turn);
                player.yHeadRotO += turn;
                player.setYBodyRot(player.yBodyRot + turn);
                player.yBodyRotO += turn;
            }
        }
        whale = under;
        lastYaw = yaw;
    }

    private AerwhaleDeckCamera() {
    }
}
