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
import com.linc.alteredaether.advancement.AAAdvancements;
import com.linc.alteredaether.config.AACommonConfig;
import com.linc.alteredaether.item.PebbleLink;
import com.linc.alteredaether.registry.AARegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.LeadItem;
import net.minecraft.world.item.NameTagItem;
import net.minecraft.world.item.SpawnEggItem;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.network.PacketDistributor;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Using a gravitite golem. An empty hand takes its pebble (crouching switches its mode); its own pebble shows what it
 * has linked; another golem's pebble links the two golems, or unlinks them; an ambrosium shard heals it; anything
 * else asks where that item is stored.
 */
public final class GolemInteractions {
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
        } else if (stack.isEmpty()) {
            if (player.isShiftKeyDown()) {
                switchMode(golem, serverPlayer, level);
            } else {
                givePebble(golem, serverPlayer, level);
            }
        } else {
            search(golem, serverPlayer, level, stack);
        }
        return InteractionResult.CONSUME;
    }

    private static Component message(String key, Object... args) {
        return Component.translatable("message." + AlteredAether.MOD_ID + ".golem." + key, args);
    }

    private static void givePebble(GravititeGolem golem, ServerPlayer player, ServerLevel level) {
        for (ItemStack carried : player.getInventory().items) {
            PebbleLink link = carried.get(AARegistries.PEBBLE_LINK.get());
            if (link != null && !link.dead() && link.golem().equals(golem.getUUID())) {
                player.displayClientMessage(message("already_carry", golem.getName()), true);
                return;
            }
        }
        ItemStack pebble = new ItemStack(AARegistries.STORAGE_PEBBLE.get());
        pebble.set(AARegistries.PEBBLE_LINK.get(),
                new PebbleLink(golem.getUUID(), level.dimension(), golem.getName().getString(), false));
        ItemHandlerHelper.giveItemToPlayer(player, pebble);
        golem.event(GolemTiming.EVENT_LINK_PULSE);
        level.playSound(null, golem.blockPosition(), AARegistries.GOLEM_LINK.get(), SoundSource.NEUTRAL, 0.8F, 1.3F);
        player.displayClientMessage(message("pebble", golem.getName()), true);
        PebbleTracker.sendNow(player, golem.getUUID());
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
        GolemSorter.markDirty(level, net);
        level.playSound(null, golem.blockPosition(), AARegistries.GOLEM_CHIME.get(), SoundSource.NEUTRAL, 0.7F,
                mode == GolemMode.TIDY ? 1.2F : 0.9F);
        player.displayClientMessage(message("mode", mode.title(),
                Component.translatable("message." + AlteredAether.MOD_ID + ".golem.mode." + mode.name().toLowerCase() + ".desc")),
                true);
    }

    private static void usePebble(GravititeGolem golem, ServerPlayer player, ServerLevel level, ItemStack stack) {
        PebbleLink link = stack.get(AARegistries.PEBBLE_LINK.get());
        if (link == null) {
            player.displayClientMessage(message("pebble_unlinked"), true);
            return;
        }
        if (link.dead()) {
            player.displayClientMessage(message("pebble_dead"), true);
            return;
        }
        if (link.golem().equals(golem.getUUID())) {
            showLinks(golem, player, level);
            return;
        }
        if (!link.dimension().equals(level.dimension()) || !(level.getEntity(link.golem()) instanceof GravititeGolem other)) {
            player.displayClientMessage(message("cant_hear", link.name()), true);
            return;
        }
        GolemNetworks networks = GolemNetworks.get(level);
        GolemNetworks.Net mine = networks.netOf(other.getUUID());
        GolemNetworks.Net theirs = networks.netOf(golem.getUUID());
        if (mine == null || theirs == null) {
            return;
        }
        if (mine == theirs) {
            networks.split(golem.getUUID());
            level.playSound(null, golem.blockPosition(), AARegistries.GOLEM_UNLINK.get(), SoundSource.NEUTRAL, 0.8F, 1.0F);
            player.displayClientMessage(message("golems_unlinked", other.getName(), golem.getName()), true);
            glowGolems(player, List.of(other, golem), GolemPayloads.UNLINK);
            GolemSorter.markDirty(level, mine);
            return;
        }
        if (!golem.isSeated() || !other.isSeated() || golem.home() == null || other.home() == null) {
            player.displayClientMessage(message("golems_not_seated"), true);
            return;
        }
        switch (networks.canMerge(mine, theirs, other.home(), golem.home())) {
            case TOO_FAR -> player.displayClientMessage(message("golems_too_far", other.getName(), golem.getName(),
                    AACommonConfig.GOLEM_RANGE.get() * 2), true);
            case TOO_MANY -> player.displayClientMessage(message("golems_too_many",
                    AACommonConfig.GOLEM_MAX_NETWORK.get()), true);
            default -> {
                networks.merge(mine, theirs);
                for (UUID id : mine.members().keySet()) {
                    if (level.getEntity(id) instanceof GravititeGolem member) {
                        member.setModeShown(mine.mode());
                        member.event(GolemTiming.EVENT_LINK_PULSE);
                    }
                }
                level.playSound(null, golem.blockPosition(), AARegistries.GOLEM_LINK.get(), SoundSource.NEUTRAL, 0.8F, 1.0F);
                player.displayClientMessage(message("golems_linked", other.getName(), golem.getName()), true);
                glowGolems(player, List.of(other, golem), GolemPayloads.LINK);
                AAAdvancements.award(player, AAAdvancements.HIVE_MIND, "linked");
                GolemSorter.markDirty(level, mine);
            }
        }
    }

    private static void glowGolems(ServerPlayer player, List<GravititeGolem> golems, byte kind) {
        List<Integer> ids = golems.stream().map(GravititeGolem::getId).toList();
        PacketDistributor.sendToPlayer(player, new GolemPayloads.Glow(kind, 40, List.of(), List.of(), ids,
                ids.size() == 2 ? ids : List.of(), List.of()));
    }

    /** Everything the golem's network has linked glows for a while: what it reaches brightly, the rest faintly. */
    static void showLinks(GravititeGolem golem, ServerPlayer player, ServerLevel level) {
        GolemNetworks.Net net = GolemNetworks.get(level).netOf(golem.getUUID());
        if (net == null) {
            return;
        }
        List<BlockPos> bright = new ArrayList<>(), dim = new ArrayList<>();
        for (BlockPos link : net.links()) {
            (net.reaches(link) ? bright : dim).add(link);
        }
        List<Integer> golems = new ArrayList<>();
        for (UUID id : net.members().keySet()) {
            if (level.getEntity(id) instanceof GravititeGolem member) {
                golems.add(member.getId());
            }
        }
        List<Integer> threads = new ArrayList<>();
        for (int i = 0; i + 1 < golems.size(); i++) {
            threads.add(golems.get(i));
            threads.add(golems.get(i + 1));
        }
        golem.event(GolemTiming.EVENT_LINK_PULSE);
        List<BlockPos> strict = GolemStorage.strict(level, net.links());
        PacketDistributor.sendToPlayer(player, new GolemPayloads.Glow(GolemPayloads.SHOW, -1, bright, dim, golems, threads,
                strict));
        int inventories = GolemNetworks.inventories(net, pos -> GolemStorage.inventoryKey(level, pos));
        player.displayClientMessage(message("links", golem.getName(), inventories, net.maxLinks(), net.mode().title()), true);
    }

    /** Glows every linked inventory holding {@code wanted}, and says how many there are in all. */
    private static void search(GravititeGolem golem, ServerPlayer player, ServerLevel level, ItemStack wanted) {
        GolemNetworks.Net net = GolemNetworks.get(level).netOf(golem.getUUID());
        if (net == null) {
            return;
        }
        List<BlockPos> found = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        long total = 0;
        for (BlockPos pos : net.links()) {
            if (!level.isLoaded(pos) || !seen.add(GolemStorage.inventoryKey(level, pos))) {
                continue;
            }
            IItemHandler handler = GolemStorage.handler(level, pos);
            if (handler == null) {
                continue;
            }
            long here = 0;
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                ItemStack stack = handler.getStackInSlot(slot);
                if (stack.is(wanted.getItem())) {
                    here += stack.getCount();
                }
            }
            if (here > 0) {
                found.add(pos);
                BlockPos other = GolemStorage.partner(level, pos);
                if (other != null) {
                    found.add(other);
                }
                total += here;
            }
        }
        golem.event(GolemTiming.EVENT_LINK_PULSE);
        if (found.isEmpty()) {
            golem.shakeHead();
            player.displayClientMessage(message("none_stored", wanted.getHoverName()), true);
            return;
        }
        PacketDistributor.sendToPlayer(player, GolemPayloads.Glow.blocks(GolemPayloads.SEARCH, -1, found,
                GolemStorage.strict(level, found)));
        int chests = (int) found.stream().map(pos -> GolemStorage.inventoryKey(level, pos)).distinct().count();
        player.displayClientMessage(message(chests == 1 ? "found_one" : "found", wanted.getHoverName(),
                NumberFormat.getIntegerInstance().format(total), chests), true);
    }
}
