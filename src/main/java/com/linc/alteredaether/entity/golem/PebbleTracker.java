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
import com.linc.alteredaether.config.AACommonConfig;
import com.linc.alteredaether.item.PebbleLink;
import com.linc.alteredaether.registry.AARegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Keeps a player carrying pebbles told about each of their networks: what it links (and which of that no golem can get
 * at, or is pinned strictly), where its golems sit, its colour. Their client uses it to light the pebbles up near the
 * network, to outline its storage while one is held (see client/golem/PebbleHints), and for the tooltip.
 */
@EventBusSubscriber(modid = AlteredAether.MOD_ID)
public final class PebbleTracker {
    /** Ticks between sends while nothing changes. */
    private static final int REFRESH = 100;

    private record Sent(int version, long tick) {
    }

    private static final Map<UUID, Map<UUID, Sent>> SENT = new HashMap<>();

    private PebbleTracker() {
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 10 != 0) {
            return;
        }
        Set<UUID> carried = new LinkedHashSet<>();
        for (ItemStack stack : player.getInventory().items) {
            add(player, stack, carried);
        }
        for (ItemStack stack : player.getInventory().offhand) {
            add(player, stack, carried);
        }
        if (carried.isEmpty()) {
            return;
        }
        GolemNetworks networks = GolemNetworks.get(player.serverLevel());
        Map<UUID, Sent> sent = SENT.computeIfAbsent(player.getUUID(), k -> new HashMap<>());
        long now = player.serverLevel().getGameTime();
        for (UUID id : carried) {
            GolemNetworks.Net net = networks.net(id);
            if (net == null) {
                continue;
            }
            Sent last = sent.get(id);
            if (last == null || last.version != net.version() || now - last.tick >= REFRESH) {
                send(player, net);
            }
        }
    }

    private static void add(ServerPlayer player, ItemStack stack, Set<UUID> carried) {
        if (!stack.is(AARegistries.STORAGE_PEBBLE.get())) {
            return;
        }
        PebbleLink link = stack.get(AARegistries.PEBBLE_LINK.get());
        if (link != null && !link.dead() && link.hasNetwork() && link.dimension().equals(player.level().dimension())) {
            carried.add(link.network());
        }
    }

    /** Sends {@code net} to {@code player} now. */
    public static void send(ServerPlayer player, GolemNetworks.Net net) {
        ServerLevel level = player.serverLevel();
        List<BlockPos> homes = new ArrayList<>();
        List<Integer> members = new ArrayList<>();
        for (Map.Entry<UUID, GolemNetworks.Member> member : net.members().entrySet()) {
            homes.add(member.getValue().home());
            if (level.getEntity(member.getKey()) instanceof GravititeGolem golem) {
                members.add(golem.getId());
            }
        }
        List<BlockPos> blocked = new ArrayList<>(), strict = new ArrayList<>();
        for (BlockPos link : net.links()) {
            if (!net.reaches(link) || level.isLoaded(link) && GolemAccess.encased(level, link)) {
                blocked.add(link);
            }
            if (level.isLoaded(link) && GolemStorage.pins(level, link).strict()) {
                strict.add(link);
            }
        }
        int linked = GolemNetworks.inventories(net, pos -> level.isLoaded(pos) ? GolemStorage.inventoryKey(level, pos) : pos.asLong());
        PacketDistributor.sendToPlayer(player, new GolemPayloads.Links(net.id, net.name(), (byte) net.mode().ordinal(),
                net.color().getTextureDiffuseColor(), AACommonConfig.GOLEM_RANGE.get(), linked, net.maxLinks(),
                AACommonConfig.GOLEM_MAX_NETWORK.get(), new ArrayList<>(net.links()), homes, members, blocked, strict));
        SENT.computeIfAbsent(player.getUUID(), k -> new HashMap<>()).put(net.id, new Sent(net.version(), level.getGameTime()));
    }

    /** Sends the network the pebble {@code link} belongs to, if it still lives. */
    public static void sendNow(ServerPlayer player, PebbleLink link) {
        GolemNetworks.Net net = GolemNetworks.get(player.serverLevel()).resolve(link.network());
        if (net != null) {
            send(player, net);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        SENT.remove(event.getEntity().getUUID());
    }
}
