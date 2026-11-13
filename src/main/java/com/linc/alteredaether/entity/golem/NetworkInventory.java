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
package com.linc.alteredaether.entity.golem;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.List;

/**
 * A golem network's storage seen as one inventory, read-only: for Create's threshold switch, which reads the network
 * when it faces the block a golem sits on. Nothing can be put in or taken out through it.
 */
public final class NetworkInventory implements IItemHandler {
    private final List<IItemHandler> handlers;
    private final int[] starts;
    private final int slots;

    NetworkInventory(List<IItemHandler> handlers) {
        this.handlers = handlers;
        starts = new int[handlers.size()];
        int total = 0;
        for (int i = 0; i < handlers.size(); i++) {
            starts[i] = total;
            total += handlers.get(i).getSlots();
        }
        slots = total;
    }

    private int handler(int slot) {
        int lo = 0, hi = starts.length - 1;
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (starts[mid] <= slot) {
                lo = mid;
            } else {
                hi = mid - 1;
            }
        }
        return lo;
    }

    @Override
    public int getSlots() {
        return slots;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        if (slot < 0 || slot >= slots) {
            return ItemStack.EMPTY;
        }
        int h = handler(slot);
        return handlers.get(h).getStackInSlot(slot - starts[h]);
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        return stack;
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        return ItemStack.EMPTY;
    }

    @Override
    public int getSlotLimit(int slot) {
        if (slot < 0 || slot >= slots) {
            return 0;
        }
        int h = handler(slot);
        return handlers.get(h).getSlotLimit(slot - starts[h]);
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return false;
    }
}
