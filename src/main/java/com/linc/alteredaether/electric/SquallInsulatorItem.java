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
import com.linc.alteredaether.entity.Squall;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

import java.util.List;

/**
 * A Squall Insulator in hand. Used on a spent squall (see {@link Squall#isSpent}), it catches it, as an empty blaze
 * burner catches a blaze, and becomes a Squall Generator.
 */
public class SquallInsulatorItem extends BlockItem {
    public SquallInsulatorItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target,
                                                  InteractionHand hand) {
        if (!(target instanceof Squall squall) || !squall.isSpent() || !squall.isAlive()) {
            return InteractionResult.PASS;
        }
        if (!player.level().isClientSide) {
            SquallCapture.caught(squall, player);
            ItemStack generator = new ItemStack(ElectricRegistries.SQUALL_GENERATOR_ITEM.get());
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            if (stack.isEmpty()) {
                player.setItemInHand(hand, generator);
            } else if (!player.getInventory().add(generator)) {
                player.drop(generator, false);
            }
        }
        return InteractionResult.sidedSuccess(player.level().isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip." + AlteredAether.MOD_ID + ".squall_insulator")
                .withStyle(ChatFormatting.GRAY));
    }
}
