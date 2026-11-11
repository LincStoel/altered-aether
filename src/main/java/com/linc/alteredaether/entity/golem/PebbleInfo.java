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

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * What a client last heard about the golem networks whose pebbles its player held (see PebbleTracker), for the
 * pebble's tooltip and the hints shown while holding it. Empty on a server.
 */
public final class PebbleInfo {
    private static final Map<UUID, Info> INFO = new ConcurrentHashMap<>();
    /** Where the client's player is; set by the client, so this class needs nothing client-only. */
    public static volatile Supplier<Vec3> viewer = () -> null;

    public record Info(UUID golem, String name, GolemMode mode, int range, int linked, int maxLinks,
                       List<BlockPos> links, List<BlockPos> homes) {
        public Component modeTitle() {
            return mode.title();
        }

        /** Blocks from the client's player to the nearest golem of the network, or -1 if unknown. */
        public double distance() {
            Vec3 at = viewer.get();
            if (at == null || homes.isEmpty()) {
                return -1;
            }
            double best = Double.MAX_VALUE;
            for (BlockPos home : homes) {
                best = Math.min(best, Math.sqrt(home.getBottomCenter().distanceToSqr(at)));
            }
            return best;
        }
    }

    private PebbleInfo() {
    }

    public static void put(GolemPayloads.Links links) {
        INFO.put(links.golem(), new Info(links.golem(), links.name(), GolemMode.byId(links.mode()), links.range(),
                links.linked(), links.maxLinks(), List.copyOf(links.links()), List.copyOf(links.homes())));
    }

    @Nullable
    public static Info get(UUID golem) {
        return INFO.get(golem);
    }

    public static void clear() {
        INFO.clear();
    }
}
