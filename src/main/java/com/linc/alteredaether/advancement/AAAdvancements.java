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
package com.linc.alteredaether.advancement;

import com.aetherteam.aether.item.AetherItems;
import com.linc.alteredaether.AlteredAether;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Advancements awarded in code, each through a {@code minecraft:impossible} criterion (see AAAdvancementProvider).
 */
public final class AAAdvancements {
    public static final ResourceLocation FAVORITE_SNACK = AlteredAether.id("favorite_snack");
    public static final ResourceLocation AIRBORNE_CARGO = AlteredAether.id("airborne_cargo");
    public static final ResourceLocation LIVING_LIGHTNING_ROD = AlteredAether.id("living_lightning_rod");

    /** Grants {@code criterion} of {@code id} to a server player; does nothing on the client. */
    public static void award(Player player, ResourceLocation id, String criterion) {
        if (player instanceof ServerPlayer serverPlayer) {
            AdvancementHolder advancement = serverPlayer.server.getAdvancements().get(id);
            if (advancement != null) {
                serverPlayer.getAdvancements().award(advancement, criterion);
            }
        }
    }

    /**
     * Favorite Snack, for an aerwhale (wild, a calf or one's own) eating a blue gummy swet. Call with the stack before
     * it's consumed.
     */
    public static void fedAerwhale(Player player, ItemStack food) {
        if (food.is(AetherItems.BLUE_GUMMY_SWET.get())) {
            award(player, FAVORITE_SNACK, "fed");
        }
    }

    private AAAdvancements() {
    }
}
