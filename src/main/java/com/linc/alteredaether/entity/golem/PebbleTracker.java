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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Keeps a player holding a golem's pebble told what that golem's network links and where its golems sit, so their
 * client can show the range and the links (see client/golem/PebbleHints) and the tooltip can say how many.
 */
@EventBusSubscriber(modid = AlteredAether.MOD_ID)
public final class PebbleTracker {
    /** Ticks between sends while nothing changes. */
    private static final int REFRESH = 100;

    private record Sent(UUID golem, int version, long tick) {
    }

    private static final Map<UUID, Sent> SENT = new HashMap<>();

    private PebbleTracker() {
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 10 != 0) {
            return;
        }
        PebbleLink link = held(player);
        if (link == null || link.dead() || !link.dimension().equals(player.level().dimension())) {
            return;
        }
        GolemNetworks.Net net = GolemNetworks.get(player.serverLevel()).netOf(link.golem());
        if (net == null) {
            return;
        }
        Sent sent = SENT.get(player.getUUID());
        long now = player.serverLevel().getGameTime();
        if (sent == null || !sent.golem.equals(link.golem()) || sent.version != net.version() || now - sent.tick >= REFRESH) {
            send(player, link.golem(), net);
        }
    }

    @Nullable
    private static PebbleLink held(ServerPlayer player) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (stack.is(AARegistries.STORAGE_PEBBLE.get())) {
                return stack.get(AARegistries.PEBBLE_LINK.get());
            }
        }
        return null;
    }

    /** Sends {@code golem}'s network to {@code player} now. */
    public static void sendNow(ServerPlayer player, UUID golem) {
        GolemNetworks.Net net = GolemNetworks.get(player.serverLevel()).netOf(golem);
        if (net != null) {
            send(player, golem, net);
        }
    }

    private static void send(ServerPlayer player, UUID golem, GolemNetworks.Net net) {
        ServerLevel level = player.serverLevel();
        List<BlockPos> homes = new ArrayList<>();
        for (GolemNetworks.Member member : net.members().values()) {
            homes.add(member.home());
        }
        GolemNetworks.Member member = net.members().get(golem);
        String name = member == null ? net.name() : member.name();
        int linked = GolemNetworks.inventories(net, pos -> level.isLoaded(pos) ? GolemStorage.inventoryKey(level, pos) : pos.asLong());
        PacketDistributor.sendToPlayer(player, new GolemPayloads.Links(golem, name, (byte) net.mode().ordinal(),
                AACommonConfig.GOLEM_RANGE.get(), linked, net.maxLinks(), new ArrayList<>(net.links()), homes));
        SENT.put(player.getUUID(), new Sent(golem, net.version(), level.getGameTime()));
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        SENT.remove(event.getEntity().getUUID());
    }
}
