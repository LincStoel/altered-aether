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
import com.linc.alteredaether.registry.AARegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Linking, or unlinking, everything around one golem at once: its pebble used on it, standing or crouching. Only plain
 * storage (chests, barrels, shulker boxes) and blocks tagged {@code golem_linkable} are swept up, never anything a
 * player couldn't open themselves; storage linked to another network is left alone. Linking asks for one sort of the
 * whole network; unlinking sorts nothing.
 */
public final class GolemLinkAll {
    private GolemLinkAll() {
    }

    /** What a sweep did: how many inventories it linked or unlinked, and how many it couldn't link. */
    public record Result(int changed, int skipped) {
    }

    /** Links the unlinked storage within {@code golem}'s reach, nearest first, as far as the network's cap allows. */
    public static Result linkAll(GravititeGolem golem, ServerPlayer player) {
        ServerLevel level = (ServerLevel) golem.level();
        GolemNetworks networks = GolemNetworks.get(level);
        GolemNetworks.Net net = networks.netOf(golem.getUUID());
        BlockPos home = golem.home();
        if (net == null || home == null) {
            return new Result(0, 0);
        }
        List<BlockPos> candidates = candidates(level, home);
        candidates.sort(Comparator.comparingDouble(pos -> pos.distSqr(home)));
        List<BlockPos> linked = new ArrayList<>(), glow = new ArrayList<>();
        int skipped = 0;
        for (BlockPos pos : candidates) {
            BlockPos partner = GolemStorage.partner(level, pos);
            GolemNetworks.Net owner = networks.netAt(pos, partner);
            if (owner == net) {
                continue;
            }
            if (owner != null || !level.mayInteract(player, pos)) {
                skipped++;
                continue;
            }
            GolemNetworks.LinkResult result = networks.link(net, pos, partner, p -> GolemStorage.inventoryKey(level, p));
            if (result == GolemNetworks.LinkResult.LINKED) {
                linked.add(pos);
                glow.add(pos);
                if (partner != null) {
                    linked.add(partner);
                    glow.add(partner);
                }
            } else {
                skipped++;
            }
        }
        if (!linked.isEmpty()) {
            GolemSorter.linked(level, net, linked);
        }
        int count = (int) linked.stream().map(pos -> GolemStorage.inventoryKey(level, pos)).distinct().count();
        feedback(golem, level, GolemPayloads.Glow.blocks(GolemPayloads.LINK, -1, glow, GolemStorage.strict(level, glow)));
        player.displayClientMessage(Component.translatable("message." + AlteredAether.MOD_ID + ".golem.linked_all",
                golem.getName(), count, GolemNetworks.inventories(net, p -> GolemStorage.inventoryKey(level, p)),
                net.maxLinks()), true);
        if (count > 0) {
            level.playSound(null, golem.blockPosition(), AARegistries.GOLEM_LINK.get(), SoundSource.NEUTRAL, 0.9F, 1.1F);
        }
        PebbleTracker.send(player, net);
        return new Result(count, skipped);
    }

    /** Unlinks everything the network has linked within {@code golem}'s reach. */
    public static Result unlinkAll(GravititeGolem golem, ServerPlayer player) {
        ServerLevel level = (ServerLevel) golem.level();
        GolemNetworks networks = GolemNetworks.get(level);
        GolemNetworks.Net net = networks.netOf(golem.getUUID());
        BlockPos home = golem.home();
        if (net == null || home == null) {
            return new Result(0, 0);
        }
        int range = AACommonConfig.GOLEM_RANGE.get();
        List<BlockPos> unlinked = new ArrayList<>();
        Set<Long> inventories = new HashSet<>();
        for (BlockPos pos : new ArrayList<>(net.links())) {
            if (!GolemStorage.inReach(home, pos, range) || !net.links().contains(pos)) {
                continue;
            }
            BlockPos partner = level.isLoaded(pos) ? GolemStorage.partner(level, pos) : null;
            if (networks.unlink(net, pos, partner)) {
                unlinked.add(pos);
                inventories.add(Math.min(pos.asLong(), partner == null ? Long.MAX_VALUE : partner.asLong()));
                if (partner != null) {
                    unlinked.add(partner);
                }
            }
        }
        if (!unlinked.isEmpty()) {
            GolemSorter.unlinked(level, net, unlinked);
            level.playSound(null, golem.blockPosition(), AARegistries.GOLEM_UNLINK.get(), SoundSource.NEUTRAL, 0.9F, 0.9F);
        }
        feedback(golem, level, GolemPayloads.Glow.blocks(GolemPayloads.UNLINK, -1, unlinked));
        player.displayClientMessage(Component.translatable("message." + AlteredAether.MOD_ID + ".golem.unlinked_all",
                golem.getName(), inventories.size()), true);
        PebbleTracker.send(player, net);
        return new Result(inventories.size(), 0);
    }

    /** The glow, sent to everyone near, the golem's gesture, and a wave out to the edge of its reach. */
    private static void feedback(GravititeGolem golem, ServerLevel level, GolemPayloads.Glow glow) {
        if (!glow.bright().isEmpty() || !glow.blocked().isEmpty()) {
            PacketDistributor.sendToPlayersNear(level, null, golem.getX(), golem.getY(), golem.getZ(), 32, glow);
        }
        golem.repelVisual(level);
        golem.startWave();
    }

    /**
     * Storage within reach of a golem sitting at {@code home} that link-all may take: plain containers and blocks
     * tagged linkable, that an item can get at, in loaded chunks. A double chest is one (its first half).
     */
    private static List<BlockPos> candidates(ServerLevel level, BlockPos home) {
        int range = AACommonConfig.GOLEM_RANGE.get();
        List<BlockPos> found = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        ChunkPos min = new ChunkPos(home.offset(-range, 0, -range)), max = new ChunkPos(home.offset(range, 0, range));
        for (int cx = min.x; cx <= max.x; cx++) {
            for (int cz = min.z; cz <= max.z; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    BlockPos pos = be.getBlockPos();
                    if (!GolemStorage.inReach(home, pos, range) || !linkable(level, pos)) {
                        continue;
                    }
                    if (seen.add(GolemStorage.inventoryKey(level, pos)) && !GolemAccess.encased(level, pos)) {
                        found.add(pos.immutable());
                    }
                }
            }
        }
        return found;
    }

    private static boolean linkable(ServerLevel level, BlockPos pos) {
        GolemStorage.Role role = GolemStorage.role(level, pos);
        if (role == GolemStorage.Role.NONE) {
            return false;
        }
        return level.getBlockState(pos).is(GolemStorage.LINKABLE) || GolemStorage.plainContainer(level, pos) != null;
    }
}
