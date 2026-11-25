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
package com.linc.alteredaether.electric;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Flash blasting: a Squall Generator's spark does at once what a blast furnace takes time over. Its own recipes
 * ({@link FlashBlastingRecipe}) come first, then vanilla blasting's, and a whole stack goes at once, as with Create's
 * fans, so the Aether has a way to smelt ore without fire.
 */
public final class FlashBlasting {
    /** What {@code stack} turns into, whole, split into full stacks; null if nothing flash blasts it. */
    @Nullable
    public static List<ItemStack> process(Level level, ItemStack stack) {
        ItemStack one = result(level, stack);
        if (one == null) {
            return null;
        }
        List<ItemStack> out = new ArrayList<>();
        int total = one.getCount() * stack.getCount();
        while (total > 0) {
            int count = Math.min(total, one.getMaxStackSize());
            out.add(one.copyWithCount(count));
            total -= count;
        }
        return out;
    }

    public static boolean canProcess(Level level, ItemStack stack) {
        return !stack.isEmpty() && result(level, stack) != null;
    }

    /** What one of {@code stack}'s items flash blasts into. */
    @Nullable
    private static ItemStack result(Level level, ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        SingleRecipeInput input = new SingleRecipeInput(stack.copyWithCount(1));
        Optional<ItemStack> own = level.getRecipeManager().getRecipeFor(ElectricRegistries.FLASH_BLASTING.get(), input, level)
                .map(holder -> holder.value().assemble(input, level.registryAccess()));
        if (own.isPresent() && !own.get().isEmpty()) {
            return own.get();
        }
        return level.getRecipeManager().getRecipeFor(RecipeType.BLASTING, input, level)
                .map(holder -> holder.value().assemble(input, level.registryAccess()))
                .filter(result -> !result.isEmpty())
                .orElse(null);
    }

    private FlashBlasting() {
    }
}
