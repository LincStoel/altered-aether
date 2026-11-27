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
package com.linc.alteredaether.essence;

import com.linc.alteredaether.AlteredAether;
import com.linc.alteredaether.config.AACommonConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.TooltipFlag;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * A forked skyroot Dowsing Rod: held, it dips toward the nearest essence source within {@code dowsing_range} blocks,
 * the more the straighter it is pointed at it, and twitches faster the nearer it is. The server keeps the source in
 * the {@code dowsing_target} component; the dip is the item model's {@code altered_aether:dip} ({@link #dip}).
 */
public class DowsingRodItem extends Item {
    private static final int LOOK_EVERY = 20;

    public DowsingRodItem(Properties properties) {
        super(properties);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (!(level instanceof ServerLevel server) || !(entity instanceof LivingEntity holder)
                || (level.getGameTime() + entity.getId()) % LOOK_EVERY != 0) {
            return;
        }
        if (holder.getMainHandItem() != stack && holder.getOffhandItem() != stack) {
            return;
        }
        BlockPos target = EssenceSites.nearest(server, entity.blockPosition(), AACommonConfig.DOWSING_RANGE.get())
                .orElse(null);
        if (!Objects.equals(target, stack.get(EssenceRegistries.DOWSING_TARGET.get()))) {
            if (target == null) {
                stack.remove(EssenceRegistries.DOWSING_TARGET.get());
            } else {
                stack.set(EssenceRegistries.DOWSING_TARGET.get(), target);
            }
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip." + AlteredAether.MOD_ID + ".dowsing_rod")
                .withStyle(ChatFormatting.GRAY));
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || !oldStack.is(newStack.getItem());
    }

    /**
     * How far the rod dips, 0 to 1, for {@code holder} at {@code time}: how straight it points at the source, pulled
     * down and up again faster the nearer the source is; 0 with no source.
     */
    public static float dip(ItemStack stack, @Nullable LivingEntity holder, long time) {
        BlockPos target = stack.get(EssenceRegistries.DOWSING_TARGET.get());
        if (target == null || holder == null) {
            return 0;
        }
        double dx = target.getX() + 0.5 - holder.getX(), dz = target.getZ() + 0.5 - holder.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);
        double toward = Math.toDegrees(Math.atan2(-dx, dz));
        double off = Math.abs(Mth.wrapDegrees(toward - holder.getYRot()));
        double aim = distance < 2 ? 1 : Mth.clamp(1 - off / 90.0, 0, 1);
        double near = Mth.clamp(1 - distance / AACommonConfig.DOWSING_RANGE.get(), 0, 1);
        double speed = 0.05 + near * 0.6;
        double twitch = 0.8 + 0.2 * Math.sin(time * speed);
        return (float) Mth.clamp(aim * aim * twitch, 0, 1);
    }
}
