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

import com.linc.alteredaether.AlteredAether;
import com.linc.alteredaether.item.PebbleLink;
import com.linc.alteredaether.item.StoragePebbleItem;
import com.linc.alteredaether.registry.AARegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.LeadItem;
import net.minecraft.world.item.NameTagItem;
import net.minecraft.world.item.SpawnEggItem;
import net.neoforged.neoforge.items.ItemHandlerHelper;

import java.util.UUID;

/**
 * Using a gravitite golem. The network's pebble links everything within its reach (crouching, unlinks it all); a dye
 * of another colour recolours it; an enchanted gravitite ingot or nugget mends it; crouching with an empty hand
 * switches its network's mode; an Aether stone or glowstone dust buys a pebble; anything else, an empty hand most of
 * all, shows what the network holds.
 */
public final class GolemInteractions {
    /** What a golem takes for a storage pebble: an Aether stone, or glowstone dust. */
    public static final TagKey<Item> PEBBLE_PRICE = TagKey.create(Registries.ITEM, AlteredAether.id("golem_pebble_price"));

    private GolemInteractions() {
    }

    static InteractionResult interact(GravititeGolem golem, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (stack.getItem() instanceof NameTagItem || stack.getItem() instanceof SpawnEggItem
                || stack.getItem() instanceof LeadItem) {
            return InteractionResult.PASS;
        }
        if (stack.isEmpty() && hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        if (!(player instanceof ServerPlayer serverPlayer) || !(golem.level() instanceof ServerLevel level)) {
            return InteractionResult.SUCCESS;
        }
        if (stack.is(AARegistries.STORAGE_PEBBLE.get())) {
            usePebble(golem, serverPlayer, level, stack);
        } else if (stack.getItem() instanceof DyeItem dye && dye.getDyeColor() != golem.color()) {
            dye(golem, serverPlayer, level, stack, dye);
        } else if (heals(stack) > 0 && golem.getHealth() < golem.getMaxHealth()) {
            heal(golem, serverPlayer, level, stack);
        } else if (stack.isEmpty() && player.isShiftKeyDown()) {
            switchMode(golem, serverPlayer, level);
        } else if (stack.is(PEBBLE_PRICE) && buyPebble(golem, serverPlayer, level, stack)) {
            return InteractionResult.CONSUME;
        } else {
            GolemNetworkMenu.open(golem, serverPlayer, level);
        }
        return InteractionResult.CONSUME;
    }

    /**
     * Trades one of {@code stack} for a pebble, unless the player already carries one for this golem's network (they
     * see the network instead). Returns whether a pebble was given.
     */
    private static boolean buyPebble(GravititeGolem golem, ServerPlayer player, ServerLevel level, ItemStack stack) {
        GolemNetworks.Net net = GolemNetworks.get(level).netOf(golem.getUUID());
        if (net == null || carriesPebble(player, net)) {
            return false;
        }
        stack.consume(1, player);
        return givePebble(golem, player, level, true);
    }

    /** Whether {@code player} carries a live pebble for {@code net}. */
    static boolean carriesPebble(Player player, GolemNetworks.Net net) {
        for (ItemStack carried : player.getInventory().items) {
            PebbleLink link = carried.get(AARegistries.PEBBLE_LINK.get());
            if (link != null && !link.dead() && net.id.equals(link.network())) {
                return true;
            }
        }
        return false;
    }

    /** How much an enchanted gravitite ingot or nugget mends a golem, as iron mends an iron golem: half its health, or a ninth of that. */
    private static float heals(ItemStack stack) {
        if (stack.is(AARegistries.ENCHANTED_GRAVITITE_INGOT.get())) {
            return 6.0F;
        }
        return stack.is(AARegistries.ENCHANTED_GRAVITITE_NUGGET.get()) ? 6.0F / 9 : 0;
    }

    private static void heal(GravititeGolem golem, ServerPlayer player, ServerLevel level, ItemStack stack) {
        golem.heal(heals(stack));
        stack.consume(1, player);
        level.playSound(null, golem.blockPosition(), AARegistries.GOLEM_CHIME.get(), SoundSource.NEUTRAL, 0.8F,
                0.8F + golem.getRandom().nextFloat() * 0.4F);
    }

    private static Component message(String key, Object... args) {
        return Component.translatable("message." + AlteredAether.MOD_ID + ".golem." + key, args);
    }

    /**
     * Hands {@code player} a pebble for {@code golem}'s network, unless they already carry one; {@code tell} says so
     * either way. Returns whether one was given.
     */
    static boolean givePebble(GravititeGolem golem, ServerPlayer player, ServerLevel level, boolean tell) {
        GolemNetworks.Net net = GolemNetworks.get(level).netOf(golem.getUUID());
        if (net == null) {
            return false;
        }
        if (carriesPebble(player, net)) {
            if (tell) {
                player.displayClientMessage(message("already_carry", net.name()), true);
            }
            return false;
        }
        ItemStack pebble = new ItemStack(AARegistries.STORAGE_PEBBLE.get());
        pebble.set(AARegistries.PEBBLE_LINK.get(), new PebbleLink(golem.getUUID(), net.id, level.dimension(), net.name(),
                net.color(), false));
        ItemHandlerHelper.giveItemToPlayer(player, pebble);
        golem.event(GolemTiming.EVENT_LINK_PULSE);
        level.playSound(null, golem.blockPosition(), AARegistries.GOLEM_LINK.get(), SoundSource.NEUTRAL, 0.8F, 1.3F);
        if (tell) {
            player.displayClientMessage(message("pebble", golem.getName()), true);
        }
        PebbleTracker.send(player, net);
        return true;
    }

    private static void switchMode(GravititeGolem golem, ServerPlayer player, ServerLevel level) {
        GolemNetworks networks = GolemNetworks.get(level);
        GolemNetworks.Net net = networks.netOf(golem.getUUID());
        if (net == null) {
            return;
        }
        GolemMode mode = net.mode().next();
        networks.setMode(net, mode);
        for (UUID id : net.members().keySet()) {
            if (level.getEntity(id) instanceof GravititeGolem member) {
                member.setModeShown(mode);
                member.event(GolemTiming.EVENT_LINK_PULSE);
            }
        }
        GolemSorter.requestFullSort(level, net);
        level.playSound(null, golem.blockPosition(), AARegistries.GOLEM_CHIME.get(), SoundSource.NEUTRAL, 0.7F,
                mode == GolemMode.TIDY ? 1.2F : 0.9F);
        player.displayClientMessage(message("mode", mode.title(),
                Component.translatable("message." + AlteredAether.MOD_ID + ".golem.mode." + mode.name().toLowerCase() + ".desc")),
                true);
    }

    /**
     * The network's own pebble links everything within this golem's reach, or crouching unlinks it all; another
     * network's pebble does nothing here.
     */
    private static void usePebble(GravititeGolem golem, ServerPlayer player, ServerLevel level, ItemStack stack) {
        PebbleLink link = stack.get(AARegistries.PEBBLE_LINK.get());
        if (link == null) {
            player.displayClientMessage(message("pebble_unlinked"), true);
            return;
        }
        GolemNetworks.Net net = StoragePebbleItem.network(level, link);
        if (net == null) {
            stack.set(AARegistries.PEBBLE_LINK.get(), link.asDead());
            player.displayClientMessage(message("pebble_dead"), true);
            return;
        }
        if (!net.members().containsKey(golem.getUUID())) {
            player.displayClientMessage(message("other_network", golem.getName(), net.name()), true);
            return;
        }
        if (!StoragePebbleItem.active(level, link, player.blockPosition())) {
            player.displayClientMessage(message("cant_hear", net.name()), true);
            return;
        }
        if (golem.home() == null || !golem.isSeated()) {
            player.displayClientMessage(message("golem_moving", golem.getName()), true);
            return;
        }
        if (player.isShiftKeyDown()) {
            GolemLinkAll.unlinkAll(golem, player);
        } else {
            GolemLinkAll.linkAll(golem, player);
        }
    }

    /** Recolours the golem: it leaves its network for one of the new colour, if there's room. */
    private static void dye(GravititeGolem golem, ServerPlayer player, ServerLevel level, ItemStack stack, DyeItem dye) {
        golem.setColor(dye.getDyeColor(), player);
        stack.consume(1, player);
        level.playSound(null, golem.blockPosition(), SoundEvents.DYE_USE, SoundSource.PLAYERS, 1.0F, 1.0F);
        golem.event(GolemTiming.EVENT_LINK_PULSE);
        GolemNetworks.Net net = GolemNetworks.get(level).netOf(golem.getUUID());
        if (net != null && !golem.isRefused()) {
            player.displayClientMessage(message(net.members().size() > 1 ? "dyed_joined" : "dyed",
                    golem.getName(), Component.translatable("color.minecraft." + dye.getDyeColor().getSerializedName()),
                    net.members().size()), true);
        }
    }
}
