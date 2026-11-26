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

import com.linc.alteredaether.AlteredAether;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

/** An Aether Jar in hand: it keeps its charge, shown on a bar and in the tooltip. */
public class AetherJarItem extends BlockItem {
    private static final int BAR_COLOUR = 0x8FD3FF;

    public AetherJarItem(Block block, Properties properties) {
        super(block, properties);
    }

    public static int energy(ItemStack stack) {
        if (creative(stack)) {
            return AetherJarBlockEntity.capacity();
        }
        return stack.getOrDefault(ElectricRegistries.STORED_ENERGY.get(), 0);
    }

    public static boolean creative(ItemStack stack) {
        return stack.getItem() instanceof BlockItem item && item.getBlock() instanceof AetherJarBlock jar && jar.creative();
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return !creative(stack) && energy(stack) > 0;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Mth.clamp(Math.round(13.0F * energy(stack) / AetherJarBlockEntity.capacity()), 0, 13);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return BAR_COLOUR;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        if (creative(stack)) {
            tooltip.add(Component.translatable("tooltip." + AlteredAether.MOD_ID + ".creative_aether_jar")
                    .withStyle(ChatFormatting.GRAY));
            return;
        }
        tooltip.add(Component.translatable("tooltip." + AlteredAether.MOD_ID + ".energy", fe(energy(stack)),
                fe(AetherJarBlockEntity.capacity())).withStyle(ChatFormatting.GRAY));
    }

    /** {@code amount} with thousands separators. */
    public static String fe(int amount) {
        return NumberFormat.getIntegerInstance(Locale.ROOT).format(amount);
    }
}
