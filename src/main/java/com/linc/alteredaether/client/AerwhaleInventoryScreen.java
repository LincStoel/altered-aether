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

import com.linc.alteredaether.entity.TamedAerwhale;
import com.linc.alteredaether.menu.AerwhaleInventoryMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/**
 * A tamed aerwhale's inventory screen, drawn with the horse screen's own background and slot sprites so it sits with
 * vanilla's: the whale in the window on the left, its saddle slot beside it, and with a chest the saddlebag's slots.
 */
public final class AerwhaleInventoryScreen extends AbstractContainerScreen<AerwhaleInventoryMenu> {
    private static final ResourceLocation BACKGROUND = ResourceLocation.withDefaultNamespace("textures/gui/container/horse.png");
    private static final ResourceLocation CHEST_SLOTS = ResourceLocation.withDefaultNamespace("container/horse/chest_slots");
    private static final ResourceLocation SADDLE_SLOT = ResourceLocation.withDefaultNamespace("container/horse/saddle_slot");
    /** The whale is a good deal bigger than a horse; this fits it in the same window. */
    private static final int WHALE_SCALE = 6;

    public AerwhaleInventoryScreen(AerwhaleInventoryMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = (width - imageWidth) / 2;
        int y = (height - imageHeight) / 2;
        graphics.blit(BACKGROUND, x, y, 0, 0, imageWidth, imageHeight);
        TamedAerwhale whale = menu.whale();
        if (whale == null) {
            return;
        }
        if (menu.owner()) {
            if (whale.hasChest()) {
                graphics.blitSprite(CHEST_SLOTS, 90, 54, 0, 0, x + 79, y + 17, AerwhaleInventoryMenu.COLUMNS * 18, 54);
            }
            graphics.blitSprite(SADDLE_SLOT, x + 7, y + 17, 18, 18);
        }
        InventoryScreen.renderEntityInInventoryFollowsMouse(graphics, x + 26, y + 18, x + 78, y + 70, WHALE_SCALE, 0.25F,
                mouseX, mouseY, whale);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
}
