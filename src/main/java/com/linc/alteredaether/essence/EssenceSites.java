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
import com.linc.alteredaether.worldgen.essence.EssenceField;
import com.linc.alteredaether.worldgen.landmark.Landmark;
import com.linc.alteredaether.worldgen.landmark.LandmarkField;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Where the essence sources are: the Channels on Highlands islets ({@link EssenceField}) and the Wellsprings over the
 * landmarks ({@link LandmarkField}), as world generation lays them out. {@link #nearest} looks only at what has already
 * been worked out, so it never stalls the server; to have it worked out ahead, the channel cells round each player
 * holding a Dowsing Rod are searched on a background thread when they cross into a new cell (the landmarks' cells are
 * warmed by the rumour maps' own look-ahead, loot/RumourWarmup).
 */
@EventBusSubscriber(modid = AlteredAether.MOD_ID)
public final class EssenceSites {
    private static final int INTERVAL = 20;
    /** Background searches at once at most; they share the pool chunks generate on. */
    private static final int MAX_RUNNING = 1;
    private static final AtomicInteger RUNNING = new AtomicInteger();
    /** Per player, the channel cell last warmed round, packed; touched only on the server thread. */
    private static final Map<UUID, Long> WARMED = new HashMap<>();

    /** The nearest source to {@code from} within {@code range} blocks, if any is known. */
    public static Optional<BlockPos> nearest(ServerLevel level, BlockPos from, int range) {
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        int x = from.getX(), z = from.getZ();
        EssenceField channels = EssenceField.get(level.dimension());
        if (channels != null) {
            int cell = EssenceField.CELL;
            for (int cx = Math.floorDiv(x - range, cell); cx <= Math.floorDiv(x + range, cell); cx++) {
                for (int cz = Math.floorDiv(z - range, cell); cz <= Math.floorDiv(z + range, cell); cz++) {
                    Optional<EssenceField.Site> site = channels.siteIfKnown(cx, cz);
                    if (site != null && site.isPresent()) {
                        double d = Math.hypot(site.get().x() - x, site.get().z() - z);
                        if (d <= range && d < bestDistance) {
                            bestDistance = d;
                            best = new BlockPos(site.get().x(), site.get().top(), site.get().z());
                        }
                    }
                }
            }
        }
        LandmarkField landmarks = LandmarkField.get(level.dimension());
        if (landmarks != null) {
            for (Landmark landmark : landmarks.knownNear(x, z, range)) {
                for (BlockPos point : landmark.points(Landmark.ORB)) {
                    double d = Math.hypot(point.getX() - x, point.getZ() - z);
                    if (d <= range && d < bestDistance) {
                        bestDistance = d;
                        best = point.getY() == Integer.MIN_VALUE ? point.atY(from.getY()) : point;
                    }
                }
            }
        }
        return Optional.ofNullable(best);
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        EssenceField field = EssenceField.get(level.dimension());
        if (field == null) {
            return;
        }
        long time = level.getGameTime();
        for (ServerPlayer player : level.players()) {
            if ((time + player.getId()) % INTERVAL != 0 || !holdsRod(player)) {
                continue;
            }
            int range = AACommonConfig.DOWSING_RANGE.get();
            int x = player.getBlockX(), z = player.getBlockZ();
            long here = ((long) Math.floorDiv(x, EssenceField.CELL) << 32)
                    ^ (Math.floorDiv(z, EssenceField.CELL) & 0xFFFFFFFFL);
            Long warmed = WARMED.get(player.getUUID());
            if (warmed != null && warmed == here || RUNNING.get() >= MAX_RUNNING) {
                continue;
            }
            WARMED.put(player.getUUID(), here);
            RUNNING.incrementAndGet();
            CompletableFuture.runAsync(() -> warm(field, x, z, range), Util.backgroundExecutor())
                    .whenComplete((done, error) -> {
                        RUNNING.decrementAndGet();
                        if (error != null) {
                            AlteredAether.LOGGER.warn("Couldn't look ahead for essence channels near {}, {}", x, z,
                                    error);
                        }
                    });
        }
    }

    private static boolean holdsRod(ServerPlayer player) {
        return player.getMainHandItem().is(EssenceRegistries.DOWSING_ROD.get())
                || player.getOffhandItem().is(EssenceRegistries.DOWSING_ROD.get());
    }

    /** Works out every channel cell within {@code range} of (x, z) not yet known. */
    private static void warm(EssenceField field, int x, int z, int range) {
        int cell = EssenceField.CELL;
        for (int cx = Math.floorDiv(x - range, cell); cx <= Math.floorDiv(x + range, cell); cx++) {
            for (int cz = Math.floorDiv(z - range, cell); cz <= Math.floorDiv(z + range, cell); cz++) {
                field.site(cx, cz);
            }
        }
    }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        WARMED.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        WARMED.clear();
    }

    private EssenceSites() {
    }
}
