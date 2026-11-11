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
package com.linc.alteredaether.item;

import com.linc.alteredaether.entity.AerwhaleWhistle;
import com.linc.alteredaether.entity.TamedAerwhale;
import com.linc.alteredaether.registry.AARegistries;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Blown like a goat horn, it sounds an aerwhale's call and, in the Aether, brings the whale it's linked to round to
 * its owner (see {@link AerwhaleWhistle}). It's linked by using it on a tamed aerwhale you own.
 */
public class WhaleWhistleItem extends Item {
    /** The colour of a linked whale's name in the tooltip. */
    private static final int NAME_COLOUR = 0xE072D7;
    /** The colour of "an aerwhale" in the tooltip of a whistle linked to an unnamed whale. */
    private static final int UNNAMED_COLOUR = 0x417E8E;
    /** How long the whistle is held to the lips, in ticks: as long as the call it sounds, about five seconds. */
    private static final int BLOW_TICKS = 105;
    /** How often a whistle in an inventory checks its whale's name, in ticks. */
    private static final int NAME_CHECK = 20;

    public WhaleWhistleItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        player.startUsingItem(hand);
        if (player instanceof ServerPlayer server) {
            level.playSound(null, player, AARegistries.WHALE_WHISTLE_SOUND.get(), SoundSource.PLAYERS, 2.0F, 1.0F);
            level.gameEvent(GameEvent.INSTRUMENT_PLAY, player.position(), GameEvent.Context.of(player));
            player.awardStat(Stats.ITEM_USED.get(this));
            WhistleLink link = stack.get(AARegistries.WHISTLE_LINK.get());
            int cooldown = AerwhaleWhistle.blow(server, link);
            player.getCooldowns().addCooldown(this, cooldown);
        }
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.TOOT_HORN;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return BLOW_TICKS;
    }

    /** Links {@code stack} to {@code whale}, which {@code player} owns. */
    public static void link(ItemStack stack, TamedAerwhale whale, Player player) {
        stack.set(AARegistries.WHISTLE_LINK.get(), new WhistleLink(whale.getUUID(), Optional.ofNullable(whale.getCustomName())));
        player.displayClientMessage(Component.translatable("message.altered_aether.whale_whistle.linked",
                whale.getDisplayName()), true);
        whale.call(0.5F, 1.3F);
    }

    /** Keeps the tooltip's name up to date while the whale is about, should it be renamed. */
    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity holder, int slot, boolean selected) {
        if (!(level instanceof ServerLevel server) || server.getGameTime() % NAME_CHECK != 0) {
            return;
        }
        WhistleLink link = stack.get(AARegistries.WHISTLE_LINK.get());
        if (link != null && server.getEntity(link.whale()) instanceof TamedAerwhale whale
                && !Objects.equals(link.name().orElse(null), whale.getCustomName())) {
            stack.set(AARegistries.WHISTLE_LINK.get(), new WhistleLink(link.whale(), Optional.ofNullable(whale.getCustomName())));
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        WhistleLink link = stack.get(AARegistries.WHISTLE_LINK.get());
        if (link == null) {
            tooltip.add(Component.translatable("item.altered_aether.whale_whistle.unlinked").withStyle(ChatFormatting.GRAY));
            return;
        }
        Component whale = link.name()
                .map(name -> (Component) name.copy().withColor(NAME_COLOUR))
                .orElseGet(() -> Component.translatable("item.altered_aether.whale_whistle.an_aerwhale").withColor(UNNAMED_COLOUR));
        tooltip.add(Component.translatable("item.altered_aether.whale_whistle.summons", whale).withStyle(ChatFormatting.GRAY));
    }
}
