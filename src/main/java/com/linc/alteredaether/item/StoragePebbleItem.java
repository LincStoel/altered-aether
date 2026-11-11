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

import com.linc.alteredaether.AlteredAether;
import com.linc.alteredaether.config.AACommonConfig;
import com.linc.alteredaether.entity.golem.GolemNetworks;
import com.linc.alteredaether.entity.golem.GolemPayloads;
import com.linc.alteredaether.entity.golem.GolemSorter;
import com.linc.alteredaether.entity.golem.GolemStorage;
import com.linc.alteredaether.entity.golem.GravititeGolem;
import com.linc.alteredaether.entity.golem.PebbleInfo;
import com.linc.alteredaether.entity.golem.PebbleTracker;
import com.linc.alteredaether.registry.AARegistries;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * A storage pebble: a chip of a golem's gravitite that answers to it. Used on storage it links that storage to the
 * golem's network (the chest doesn't open), or unlinks it; used crouching on the top of a block it sends the golem
 * there; used on golems it shows links and links golems (see GolemInteractions). It is never used up.
 */
public class StoragePebbleItem extends Item {
    public StoragePebbleItem(Properties properties) {
        super(properties);
    }

    private static Component message(String key, Object... args) {
        return Component.translatable("message." + AlteredAether.MOD_ID + ".golem." + key, args);
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        PebbleLink link = stack.get(AARegistries.PEBBLE_LINK.get());
        Level level = context.getLevel();
        if (link == null) {
            return InteractionResult.PASS;
        }
        // a live pebble always takes the click, so storage never opens under it
        if (level.isClientSide || !(context.getPlayer() instanceof ServerPlayer player)
                || !(level instanceof ServerLevel server)) {
            return link.dead() ? InteractionResult.PASS : InteractionResult.SUCCESS;
        }
        BlockPos pos = context.getClickedPos();
        if (!server.mayInteract(player, pos)) {
            return InteractionResult.FAIL;
        }
        if (link.dead()) {
            player.displayClientMessage(message("pebble_dead"), true);
            return InteractionResult.PASS;
        }
        if (!link.dimension().equals(server.dimension())) {
            player.displayClientMessage(message("cant_hear", link.name()), true);
            return InteractionResult.SUCCESS;
        }
        if (player.isShiftKeyDown() && context.getClickedFace() == Direction.UP) {
            sendGolem(link, player, server, pos.above());
            return InteractionResult.SUCCESS;
        }
        toggleLink(link, player, server, pos);
        return InteractionResult.SUCCESS;
    }

    private static void sendGolem(PebbleLink link, ServerPlayer player, ServerLevel level, BlockPos target) {
        if (!(level.getEntity(link.golem()) instanceof GravititeGolem golem)) {
            player.displayClientMessage(message("cant_hear", link.name()), true);
            return;
        }
        int max = AACommonConfig.GOLEM_MAX_MOVE.get();
        if (golem.blockPosition().distSqr(target) > (double) max * max) {
            player.displayClientMessage(message("too_far_to_send", golem.getName(), max), true);
            return;
        }
        if (!golem.canStand(target)) {
            player.displayClientMessage(message("cant_sit", golem.getName()), true);
            return;
        }
        golem.sendTo(target, player);
        level.playSound(null, target, AARegistries.GOLEM_LINK.get(), SoundSource.NEUTRAL, 0.6F, 0.8F);
        PacketDistributor.sendToPlayer(player, GolemPayloads.Glow.blocks(GolemPayloads.MARK, 60, List.of(target.below())));
        player.displayClientMessage(message("on_its_way", golem.getName()), true);
    }

    private static void toggleLink(PebbleLink link, ServerPlayer player, ServerLevel level, BlockPos pos) {
        GolemNetworks networks = GolemNetworks.get(level);
        GolemNetworks.Net net = networks.netOf(link.golem());
        if (net == null) {
            player.displayClientMessage(message("pebble_dead"), true);
            return;
        }
        if (GolemStorage.handler(level, pos) == null) {
            return;
        }
        GolemStorage.Role role = GolemStorage.role(level, pos);
        BlockPos partner = GolemStorage.partner(level, pos);
        if (role == GolemStorage.Role.NONE && networks.netAt(pos, partner) != net) {
            player.displayClientMessage(message("not_storage", level.getBlockState(pos).getBlock().getName()), true);
            return;
        }
        List<BlockPos> both = new ArrayList<>(List.of(pos));
        if (partner != null) {
            both.add(partner);
        }
        GolemNetworks.Net owner = networks.netAt(pos, partner);
        switch (networks.toggleLink(net, pos, partner, p -> GolemStorage.inventoryKey(level, p))) {
            case LINKED -> {
                level.playSound(null, pos, AARegistries.GOLEM_LINK.get(), SoundSource.BLOCKS, 0.8F, 1.0F);
                PacketDistributor.sendToPlayer(player, GolemPayloads.Glow.blocks(GolemPayloads.LINK, -1, both));
                int linked = GolemNetworks.inventories(net, p -> GolemStorage.inventoryKey(level, p));
                player.displayClientMessage(message(role == GolemStorage.Role.SOURCE ? "linked_source" : "linked",
                        link.name(), linked, net.maxLinks()), true);
                GolemSorter.markDirty(level, net);
            }
            case UNLINKED -> {
                level.playSound(null, pos, AARegistries.GOLEM_UNLINK.get(), SoundSource.BLOCKS, 0.8F, 1.0F);
                PacketDistributor.sendToPlayer(player, GolemPayloads.Glow.blocks(GolemPayloads.UNLINK, -1, both));
                player.displayClientMessage(message("unlinked", link.name()), true);
                GolemSorter.markDirty(level, net);
            }
            case OUT_OF_REACH -> player.displayClientMessage(message("out_of_reach", link.name()), true);
            case FULL -> player.displayClientMessage(message("full", link.name(), net.maxLinks()), true);
            case TAKEN -> player.displayClientMessage(message("taken", owner == null ? "" : owner.name()), true);
        }
        PebbleTracker.sendNow(player, link.golem());
    }

    /** A pebble whose golem is gone turns dull. */
    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (level.isClientSide || entity.tickCount % 40 != 0 || !(level instanceof ServerLevel server)) {
            return;
        }
        PebbleLink link = stack.get(AARegistries.PEBBLE_LINK.get());
        if (link == null || link.dead()) {
            return;
        }
        ServerLevel home = server.getServer().getLevel(link.dimension());
        if (home == null || GolemNetworks.get(home).netOf(link.golem()) == null) {
            stack.set(AARegistries.PEBBLE_LINK.get(), link.asDead());
        }
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        PebbleLink link = stack.get(AARegistries.PEBBLE_LINK.get());
        String prefix = "item." + AlteredAether.MOD_ID + ".storage_pebble.";
        if (link == null) {
            tooltip.add(Component.translatable(prefix + "unlinked").withStyle(ChatFormatting.GRAY));
            return;
        }
        if (link.dead()) {
            tooltip.add(Component.translatable(prefix + "dead", link.name()).withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        tooltip.add(Component.translatable(prefix + "belongs", link.name()).withStyle(ChatFormatting.LIGHT_PURPLE));
        PebbleInfo.Info info = PebbleInfo.get(link.golem());
        if (info != null) {
            tooltip.add(Component.translatable(prefix + "mode", info.modeTitle()).withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable(prefix + "links", info.linked(), info.maxLinks()).withStyle(ChatFormatting.GRAY));
            double distance = info.distance();
            if (distance >= 0) {
                tooltip.add(Component.translatable(prefix + "distance", (int) Math.round(distance)).withStyle(ChatFormatting.GRAY));
            }
        }
        tooltip.add(Component.translatable(prefix + "use").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable(prefix + "send").withStyle(ChatFormatting.DARK_GRAY));
    }

    /** Whether {@code player} holds a pebble for {@code golem}. */
    public static boolean holdsFor(Player player, GravititeGolem golem) {
        for (ItemStack stack : List.of(player.getMainHandItem(), player.getOffhandItem())) {
            PebbleLink link = stack.get(AARegistries.PEBBLE_LINK.get());
            if (link != null && link.golem().equals(golem.getUUID())) {
                return true;
            }
        }
        return false;
    }
}
