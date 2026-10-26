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
package com.linc.alteredaether.menu;

import com.linc.alteredaether.entity.TamedAerwhale;
import com.linc.alteredaether.registry.AARegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

/**
 * A tamed aerwhale's inventory, laid out as a horse's: the saddle slot on the left, and with a chest the saddlebag's
 * five columns of three, like a mule's. Only the whale's owner gets these slots; anyone else riding it sees just their
 * own inventory.
 */
public final class AerwhaleInventoryMenu extends AbstractContainerMenu {
    public static final int COLUMNS = 5;
    private static final int WHALE_SLOTS = TamedAerwhale.INVENTORY_SIZE;

    @Nullable
    private final TamedAerwhale whale;
    private final Container container;
    private final boolean owner;

    public AerwhaleInventoryMenu(int id, Inventory playerInventory, @Nullable TamedAerwhale whale) {
        super(AARegistries.AERWHALE_MENU.get(), id);
        this.whale = whale;
        this.container = whale != null ? whale.inventory() : new SimpleContainer(WHALE_SLOTS);
        this.owner = whale != null && whale.isOwnedBy(playerInventory.player);
        container.startOpen(playerInventory.player);

        addSlot(new Slot(container, TamedAerwhale.SADDLE_SLOT, 8, 18) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return isActive() && TamedAerwhale.isSaddleItem(stack) && !hasItem();
            }

            @Override
            public boolean mayPickup(Player player) {
                return isActive();
            }

            @Override
            public boolean isActive() {
                return owner && whale.isSaddleable();
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < COLUMNS; column++) {
                addSlot(new Slot(container, 1 + row * COLUMNS + column, 80 + column * 18, 18 + row * 18) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        return isActive();
                    }

                    @Override
                    public boolean mayPickup(Player player) {
                        return isActive();
                    }

                    @Override
                    public boolean isActive() {
                        return owner && whale.hasChest();
                    }
                });
            }
        }
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(playerInventory, column + row * 9 + 9, 8 + column * 18, 84 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(playerInventory, column, 8 + column * 18, 142));
        }
    }

    /** The client's copy, opened for the whale whose id the server sent. */
    public static AerwhaleInventoryMenu fromNetwork(int id, Inventory playerInventory, RegistryFriendlyByteBuf buffer) {
        TamedAerwhale whale = playerInventory.player.level().getEntity(buffer.readVarInt()) instanceof TamedAerwhale found
                ? found : null;
        return new AerwhaleInventoryMenu(id, playerInventory, whale);
    }

    @Nullable
    public TamedAerwhale whale() {
        return whale;
    }

    /** Whether the viewer owns the whale, and so sees its saddle and saddlebag. */
    public boolean owner() {
        return owner;
    }

    @Override
    public boolean stillValid(Player player) {
        return whale != null && whale.isAlive() && !whale.isBaby() && whale.distanceTo(player) < 10
                && container.stillValid(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        int playerStart = WHALE_SLOTS;
        int hotbarStart = playerStart + 27;
        if (index < playerStart) {
            if (!moveItemStackTo(stack, playerStart, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (getSlot(TamedAerwhale.SADDLE_SLOT).mayPlace(stack)) {
            if (!moveItemStackTo(stack, 0, 1, false)) {
                return ItemStack.EMPTY;
            }
        } else if (owner && whale.hasChest() && moveItemStackTo(stack, 1, playerStart, false)) {
            // into the saddlebag
        } else if (index < hotbarStart) {
            if (!moveItemStackTo(stack, hotbarStart, slots.size(), false)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, playerStart, hotbarStart, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        container.stopOpen(player);
    }
}
