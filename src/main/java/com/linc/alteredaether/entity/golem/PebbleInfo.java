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

import com.linc.alteredaether.item.PebbleLink;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * What a client last heard about the golem networks whose pebbles its player carries (see PebbleTracker), for the
 * pebble's look and tooltip and the hints shown while holding one. Empty on a server.
 */
public final class PebbleInfo {
    private static final Map<UUID, Info> INFO = new ConcurrentHashMap<>();
    /** Where the client's player is; set by the client, so this class needs nothing client-only. */
    public static volatile Supplier<Vec3> viewer = () -> null;

    public record Info(UUID network, String name, GolemMode mode, int color, int range, int linked, int maxLinks,
                       List<BlockPos> links, List<BlockPos> homes, List<Integer> members, Set<BlockPos> blocked,
                       Set<BlockPos> strict) {
        public Component modeTitle() {
            return mode.title();
        }

        /** Whether a pebble at {@code at} is within reach of one of the network's golems. */
        public boolean reaches(Vec3 at) {
            BlockPos pos = BlockPos.containing(at);
            for (BlockPos home : homes) {
                if (GolemStorage.inReach(home, pos, range)) {
                    return true;
                }
            }
            return false;
        }
    }

    private PebbleInfo() {
    }

    public static void put(GolemPayloads.Links links) {
        INFO.put(links.network(), new Info(links.network(), links.name(), GolemMode.byId(links.mode()), links.color(),
                links.range(), links.linked(), links.maxLinks(), List.copyOf(links.links()), List.copyOf(links.homes()),
                List.copyOf(links.members()), Set.copyOf(links.blocked()), Set.copyOf(links.strict())));
    }

    @Nullable
    public static Info get(UUID network) {
        return INFO.get(network);
    }

    /**
     * Whether a pebble glows: its network lives and the one carrying it ({@code at}, else the client's player) is
     * within reach of one of its golems. A pebble of a network the client hasn't heard about glows unless dead.
     */
    public static boolean bright(@Nullable PebbleLink link, @Nullable Vec3 at) {
        if (link == null || link.dead()) {
            return false;
        }
        Info info = INFO.get(link.network());
        Vec3 where = at != null ? at : viewer.get();
        return info == null || where == null || info.reaches(where);
    }

    public static void clear() {
        INFO.clear();
    }
}
