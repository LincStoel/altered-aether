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
import com.linc.alteredaether.config.AACommonConfig;
import io.wispforest.accessories.api.Accessory;
import io.wispforest.accessories.api.attributes.AccessoryAttributeBuilder;
import io.wispforest.accessories.api.slot.SlotReference;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * Static Boots, worn in the Accessories mod's shoes slot (by the {@code accessories:shoes} tag). The static they hold
 * sticks the wearer to the ground: knockback resistance here, and the rest (sinking faster, jumping no higher than a
 * plain jump, no bounce off a blue aercloud) in {@link StaticBoots}.
 */
public class StaticBootsItem extends Item implements Accessory {
    private static final ResourceLocation KNOCKBACK = AlteredAether.id("static_boots_knockback");

    public StaticBootsItem(Properties properties) {
        super(properties);
    }

    @Override
    public void getModifiers(ItemStack stack, SlotReference reference, AccessoryAttributeBuilder builder) {
        double resistance = AACommonConfig.STATIC_BOOTS_KNOCKBACK.get();
        if (resistance > 0) {
            builder.addExclusive(Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK, resistance,
                    AttributeModifier.Operation.ADD_VALUE);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip." + AlteredAether.MOD_ID + ".static_boots")
                .withStyle(ChatFormatting.GRAY));
    }
}
