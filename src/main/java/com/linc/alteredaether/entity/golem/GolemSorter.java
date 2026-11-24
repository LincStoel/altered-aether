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
import com.linc.alteredaether.compat.QuarkSorting;
import com.linc.alteredaether.config.AACommonConfig;
import com.linc.alteredaether.entity.golem.route.FlightPath;
import com.linc.alteredaether.entity.golem.route.Router;
import com.linc.alteredaether.entity.golem.sort.Move;
import com.linc.alteredaether.entity.golem.sort.Planner;
import com.linc.alteredaether.entity.golem.sort.SortSnapshot;
import com.linc.alteredaether.entity.golem.sort.SortTriggers;
import com.linc.alteredaether.registry.AARegistries;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Runs every golem network: notices when its storage changes, plans with {@link Planner}, and moves items one flight
 * at a time. A flight is only a picture: the item stays in its chest the whole way, and on landing it is taken out
 * and put in the target in the same tick, after checking both ends again. Anything the target won't take goes back.
 *
 * <p>A network sorts what changed, not everything (see {@link SortTriggers}): a chest a player put things in is sorted
 * a moment after, and the chests those things went to after that. A whole-network sort comes only from a mode switch,
 * a redstone pulse, a newly linked container or two networks joining. What it saw in each inventory is kept
 * ({@link InvCache}), so only what changed is read again.
 *
 * <p>An item leaves its chest through an open face and flies one axis at a time round whatever is in the way
 * ({@link Router}); a chest with no open face can't be used. The nearest golem that reaches a chest lifts from it.
 *
 * <p>All networks share a small time budget each tick for looking into storage and planning, and one route search;
 * flights in the air are always seen through. Nothing here loads a chunk.
 */
@EventBusSubscriber(modid = AlteredAether.MOD_ID)
public final class GolemSorter {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** Time all networks may spend looking and planning each tick. */
    private static final long BUDGET_NANOS = 500_000;
    /** Moves looked at past the first when the first can't start yet. */
    private static final int LOOKAHEAD = 8;
    /** Ticks between checks that both ends of a flight still work. */
    private static final int RECHECK = 5;
    /** Ticks over which a network's scans cover all its storage. */
    private static final int SCAN_SPAN = 40;
    /** Ticks a route is trusted before it is worked out again. */
    private static final int ROUTE_TTL = 100;
    /**
     * Cells flooded from storage to tell whether it is shut off from every golem; a pocket bigger than this is taken as
     * open to them.
     */
    private static final int CUT_OFF_CAP = 4096;
    /** Ticks between looks at whether storage found shut off has a way through again. */
    private static final int CUT_OFF_RECHECK = 40;
    /** How near a player closing storage must be to a linked inventory for it to be looked at at once. */
    private static final int CLOSE_RANGE = 12;

    private static final Map<ServerLevel, Map<UUID, Runtime>> RUNTIMES = new WeakHashMap<>();
    /** The tick each level last ran a route search in: one a tick, across all its networks. */
    private static final Map<ServerLevel, Long> SEARCHED = new WeakHashMap<>();
    private static int nextFlightId = 1;
    private static int rotation;

    /** What a network is doing, for Jade and for messages. */
    public enum Status { SORTED, SORTING, PAUSED, NO_SPACE, IDLE }

    /**
     * A status and the numbers that go with it; {@code waiting} is whether a sort is due or under way, and
     * {@code flightTargets} where the items in the air are going.
     */
    public record Report(Status status, int movesLeft, int unplaced, int outOfReach, int inUse, int blocked,
                         boolean waiting, List<BlockPos> flightTargets) {
    }

    private GolemSorter() {
    }

    private static Runtime runtime(ServerLevel level, UUID net) {
        return RUNTIMES.computeIfAbsent(level, l -> new HashMap<>()).computeIfAbsent(net, Runtime::new);
    }

    @Nullable
    private static Runtime existing(ServerLevel level, UUID net) {
        Map<UUID, Runtime> map = RUNTIMES.get(level);
        return map == null ? null : map.get(net);
    }

    private static int delay() {
        return AACommonConfig.GOLEM_SORT_DELAY.get();
    }

    /**
     * Has the network sort all its storage after the delay: for a mode switch, a redstone pulse, a newly linked
     * container, or networks joining. Several asked for within the delay make one sort.
     */
    public static void requestFullSort(ServerLevel level, GolemNetworks.Net net) {
        runtime(level, net.id).triggers.requestFullSort(level.getGameTime(), delay());
    }

    /**
     * Storage newly linked to {@code net}: what it holds now is taken as seen (so linking alone sorts nothing new on
     * its own account), room may have freed for stuck items, and the whole network is sorted after the delay.
     */
    public static void linked(ServerLevel level, GolemNetworks.Net net, Collection<BlockPos> positions) {
        Runtime runtime = runtime(level, net.id);
        for (BlockPos pos : positions) {
            if (level.isLoaded(pos)) {
                runtime.cache.read(level, pos, GolemStorage.inventoryKey(level, pos), GolemStorage.role(level, pos));
            }
        }
        long now = level.getGameTime();
        runtime.triggers.spaceFreed(now, delay());
        runtime.triggers.requestFullSort(now, delay());
        runtime.contentsChanged = true;
    }

    /** Storage unlinked from {@code net}: forgotten, and nothing is sorted on its account. */
    public static void unlinked(ServerLevel level, GolemNetworks.Net net, Collection<BlockPos> positions) {
        Runtime runtime = existing(level, net.id);
        if (runtime == null) {
            return;
        }
        for (BlockPos pos : positions) {
            long key = level.isLoaded(pos) ? GolemStorage.inventoryKey(level, pos) : pos.asLong();
            runtime.cache.forget(key);
            runtime.triggers.forget(key);
            runtime.claims.remove(key);
            runtime.cutOff.remove(key);
        }
        runtime.contentsChanged = true;
    }

    /**
     * Whether the linked storage at {@code pos} was found shut off from {@code net}'s golems: open only into a pocket
     * no golem can get into, so it is left alone like walled-in storage.
     */
    public static boolean cutOff(ServerLevel level, GolemNetworks.Net net, BlockPos pos) {
        Runtime runtime = existing(level, net.id);
        return runtime != null && !runtime.cutOff.isEmpty() && level.isLoaded(pos)
                && runtime.cutOff.containsKey(GolemStorage.inventoryKey(level, pos));
    }

    public static Report report(ServerLevel level, GolemNetworks.Net net) {
        Runtime runtime = existing(level, net.id);
        if (runtime == null) {
            return new Report(Status.IDLE, 0, 0, 0, 0, 0, false, List.of());
        }
        List<BlockPos> targets = new ArrayList<>();
        runtime.flights.forEach(flight -> targets.add(flight.dst));
        return new Report(runtime.status, runtime.queue.size() + runtime.flights.size(), runtime.unplaced,
                runtime.outOfReach, runtime.inUse, runtime.blocked,
                runtime.triggers.pending() || !runtime.queue.isEmpty() || !runtime.flights.isEmpty() || runtime.finishPending,
                targets);
    }

    /** Calls off every flight {@code golem} takes part in; the items stay where they are. */
    public static void cancelFlights(GravititeGolem golem) {
        if (!(golem.level() instanceof ServerLevel level)) {
            return;
        }
        GolemNetworks.Net net = GolemNetworks.get(level).netOf(golem.getUUID());
        Runtime runtime = net == null ? null : existing(level, net.id);
        if (runtime == null) {
            return;
        }
        for (Flight flight : new ArrayList<>(runtime.flights)) {
            if (flight.path.contains(golem.getUUID())) {
                runtime.cancel(level, flight);
            }
        }
    }

    /**
     * What a comparator reads from {@code net}: how full its storage is, all of it together, as a comparator reads a
     * chest. Worked out at most once a tick, from what the network last saw.
     */
    public static int signal(ServerLevel level, GolemNetworks.Net net) {
        Runtime runtime = runtime(level, net.id);
        long now = level.getGameTime();
        if (runtime.signalTick != now) {
            runtime.signalTick = now;
            runtime.signal = runtime.computeSignal(level, net);
        }
        return runtime.signal;
    }

    /** {@code net}'s storage as one read-only inventory: every loaded linked inventory an item can get at. */
    public static IItemHandler networkView(ServerLevel level, GolemNetworks.Net net) {
        List<IItemHandler> handlers = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        Runtime runtime = existing(level, net.id);
        for (BlockPos pos : net.links()) {
            if (!level.isLoaded(pos)) {
                continue;
            }
            long key = GolemStorage.inventoryKey(level, pos);
            if (!seen.add(key) || runtime != null && runtime.cutOff.containsKey(key) || GolemAccess.encased(level, pos)) {
                continue;
            }
            IItemHandler handler = GolemStorage.handler(level, pos);
            if (handler != null) {
                handlers.add(handler);
            }
        }
        return new NetworkInventory(handlers);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        long deadline = System.nanoTime() + BUDGET_NANOS;
        rotation++;
        for (ServerLevel level : event.getServer().getAllLevels()) {
            GolemNetworks networks = GolemNetworks.get(level);
            Map<UUID, Runtime> runtimes = RUNTIMES.get(level);
            if (networks.all().isEmpty()) {
                if (runtimes != null) {
                    runtimes.clear();
                }
                continue;
            }
            List<GolemNetworks.Net> nets = new ArrayList<>(networks.all());
            int start = Math.floorMod(rotation, nets.size());
            for (int i = 0; i < nets.size(); i++) {
                GolemNetworks.Net net = nets.get((start + i) % nets.size());
                runtime(level, net.id).tick(level, networks, net, deadline);
            }
            runtimes = RUNTIMES.get(level);
            if (runtimes != null && runtimes.size() > nets.size()) {
                Set<UUID> live = new HashSet<>();
                for (GolemNetworks.Net net : nets) {
                    live.add(net.id);
                }
                runtimes.entrySet().removeIf(entry -> {
                    if (!live.contains(entry.getKey())) {
                        entry.getValue().cancelAll(level);
                        return true;
                    }
                    return false;
                });
            }
        }
    }

    /**
     * A player closing storage is the quickest sign something changed: linked storage near them that changed, or
     * changed while they had it open, is sorted after the delay.
     */
    @SubscribeEvent
    public static void onContainerClose(PlayerContainerEvent.Close event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        long now = level.getGameTime();
        BlockPos at = player.blockPosition();
        GolemNetworks networks = GolemNetworks.get(level);
        for (GolemNetworks.Net net : networks.near(at, AACommonConfig.GOLEM_RANGE.get() + CLOSE_RANGE)) {
            Runtime runtime = runtime(level, net.id);
            Set<Long> seen = new HashSet<>();
            for (BlockPos pos : net.links()) {
                if (pos.distSqr(at) > CLOSE_RANGE * CLOSE_RANGE || !level.isLoaded(pos)) {
                    continue;
                }
                long key = GolemStorage.inventoryKey(level, pos);
                if (!seen.add(key)) {
                    continue;
                }
                if (runtime.triggers.waitingForClose(key)) {
                    runtime.triggers.closed(key, now, delay());
                    runtime.cache.read(level, pos, key, GolemStorage.role(level, pos));
                    continue;
                }
                runtime.noticed(level, pos, key, runtime.cache.read(level, pos, key, GolemStorage.role(level, pos)), false, now);
            }
        }
    }

    /**
     * A player opening a chest a flight is coming to or leaving lands that flight at once, so what they see in the
     * chest matches what they just watched fly. Without this the flight is called off at its next check and the item
     * never arrives, though it was seen to leave. The menu is open by now, so the chest counts as in use.
     */
    @SubscribeEvent
    public static void onContainerOpen(PlayerContainerEvent.Open event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        Map<UUID, Runtime> runtimes = RUNTIMES.get(level);
        if (runtimes == null || runtimes.isEmpty()) {
            return;
        }
        GolemNetworks networks = GolemNetworks.get(level);
        for (GolemNetworks.Net net : networks.near(player.blockPosition(), AACommonConfig.GOLEM_RANGE.get() + CLOSE_RANGE)) {
            Runtime runtime = runtimes.get(net.id);
            if (runtime != null) {
                runtime.landOpened(level, net);
            }
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            Map<UUID, Runtime> runtimes = RUNTIMES.remove(level);
            if (runtimes != null) {
                runtimes.values().forEach(runtime -> runtime.cancelAll(level));
            }
            SEARCHED.remove(level);
        }
    }

    // ---------------------------------------------------------------------------------------------------------------

    /** A move waiting to fly. */
    private record Planned(BlockPos src, int srcSlot, BlockPos dst, int dstSlot, ItemStack item, int count,
                           long srcKey, long dstKey) {
    }

    /** A move in the air. {@code dstSlot} is -1 when it goes wherever the target takes it. */
    private static final class Flight {
        final int id;
        final BlockPos src, dst;
        final int srcSlot;
        int dstSlot;
        final ItemStack item;
        final int count;
        final long srcKey, dstKey;
        final long start, end;
        /** The golems it passes: the one that lifts it first, the one that sets it down last. */
        final List<UUID> path;

        Flight(int id, Planned move, int dstSlot, int count, long start, long end, List<UUID> path) {
            this.id = id;
            this.src = move.src;
            this.dst = move.dst;
            this.srcSlot = move.srcSlot;
            this.dstSlot = dstSlot;
            this.item = move.item;
            this.count = count;
            this.srcKey = move.srcKey;
            this.dstKey = move.dstKey;
            this.start = start;
            this.end = end;
            this.path = path;
        }
    }

    /** A snapshot and, for each inventory in it, where it is and its key. */
    private record Snap(SortSnapshot snapshot, List<ItemStack> items, List<BlockPos> positions, List<Long> keys) {
        int indexOf(long key) {
            return keys.indexOf(key);
        }
    }

    /** Which route: from one inventory to another, past the heads of these golems (by entity id). */
    private record RouteKey(long src, long dst, List<Integer> golems) {
    }

    /** A route worked out for a pair of chests and golems: its points, or null if there is none, and its cells. */
    private record CachedRoute(@Nullable List<Vec3> points, List<BlockPos> cells, long expiry) {
    }

    /** What looking for a route came to: points to fly along, none at all, or a wait for next tick's search. */
    private record Routed(@Nullable List<Vec3> points, boolean waiting) {
        static final Routed NONE = new Routed(null, false);
        static final Routed WAIT = new Routed(null, true);
    }

    /** One network's sorting, kept while the server runs. */
    private static final class Runtime {
        final UUID netId;
        final InvCache cache = new InvCache();
        final SortTriggers triggers = new SortTriggers();
        final Deque<Planned> queue = new ArrayDeque<>();
        final List<Flight> flights = new ArrayList<>();
        final Map<Long, Integer> lifted = new HashMap<>();
        final Map<RouteKey, CachedRoute> routes = new HashMap<>();
        /** Routes that need a search, by the tick they last waited for one: they don't look again until it's free. */
        final Map<RouteKey, Long> waitingSearch = new HashMap<>();
        /**
         * Linked storage shut off from every golem (its open faces lead only into a closed pocket), by key: frozen and
         * counted blocked, like encased storage, until a way opens.
         */
        final Map<Long, BlockPos> cutOff = new HashMap<>();
        long cutOffChecked;
        /** Whether a flight started since the last round was planned; if none did, the next round would plan the same. */
        boolean launched = true;
        /** The family each inventory was judged to be for, by key; saved with the network. */
        final Map<Long, String> claims = new HashMap<>();
        /** Every inventory this sort moved items into (every one, for a sort asked for), to tidy within once done. */
        final Set<Long> touched = new HashSet<>();
        boolean working, claimsLoaded, finishPending, contentsChanged;
        /** A snapshot ranked shared pins while a filter was still being counted: sort again once it is. */
        boolean awaitingBreadth;
        int cursor;
        Status status = Status.IDLE;
        int unplaced, outOfReach, inUse, blocked;
        long lastNoSpace = Long.MIN_VALUE / 2;
        long signalTick = -1;
        int signal;

        Runtime(UUID netId) {
            this.netId = netId;
        }

        void tick(ServerLevel level, GolemNetworks networks, GolemNetworks.Net net, long deadline) {
            long now = level.getGameTime();
            if (!claimsLoaded) {
                claimsLoaded = true;
                // claims saved before families were a tree name a flat family ("tag:...", "kind:..."): dropped
                net.claims().forEach((key, name) -> {
                    if (!name.contains(":")) {
                        claims.put(key, name);
                    }
                });
            }
            if (awaitingBreadth && FilterBreadth.idle()) {
                // the filters that share an item have been counted: the narrowest of them now win it
                awaitingBreadth = false;
                triggers.requestFullSort(now, delay());
            }
            boolean idle = flights.isEmpty() && queue.isEmpty() && !finishPending && !triggers.pending();
            int interval = AACommonConfig.GOLEM_SCAN_INTERVAL.get();
            boolean scanTick = now % interval == 0;
            if (idle && !scanTick && !contentsChanged) {
                // nothing to do this tick: no flights, nothing waiting, no scan due
                return;
            }
            Map<UUID, GravititeGolem> loaded = new LinkedHashMap<>();
            for (UUID id : net.members().keySet()) {
                if (level.getEntity(id) instanceof GravititeGolem golem && golem.isAlive()) {
                    loaded.put(id, golem);
                }
            }
            List<GravititeGolem> active = new ArrayList<>();
            for (GravititeGolem golem : loaded.values()) {
                if (golem.isWorking()) {
                    active.add(golem);
                }
            }
            List<GravititeGolem> team = team(active);
            Set<UUID> teamIds = new HashSet<>();
            team.forEach(golem -> teamIds.add(golem.getUUID()));

            for (Flight flight : new ArrayList<>(flights)) {
                if (!teamIds.containsAll(flight.path)) {
                    cancel(level, flight);
                } else if (now >= flight.end) {
                    land(level, net, flight);
                } else if ((now - flight.start) % RECHECK == 0 && !stillGood(level, flight)) {
                    cancel(level, flight);
                }
            }

            for (GravititeGolem golem : loaded.values()) {
                if (golem.isSeated()) {
                    boolean busy = false;
                    for (Flight flight : flights) {
                        if (flight.path.contains(golem.getUUID())) {
                            busy = true;
                            break;
                        }
                    }
                    golem.setState(busy ? GravititeGolem.CONCENTRATING : GravititeGolem.SEATED);
                }
            }

            if (team.isEmpty()) {
                queue.clear();
                triggers.stopCascade();
                finishPending = false;
                status = loaded.values().stream().anyMatch(GravititeGolem::isPaused) ? Status.PAUSED : Status.IDLE;
                flushSignal(level, net);
                return;
            }

            if (scanTick && !cutOff.isEmpty() && now - cutOffChecked >= CUT_OFF_RECHECK && System.nanoTime() < deadline) {
                recheckCutOff(level, net, team, now);
            }
            if (scanTick && System.nanoTime() < deadline) {
                scan(level, networks, net, team, interval, now);
            }
            if (queue.isEmpty() && flights.isEmpty() && System.nanoTime() < deadline) {
                if (finishPending || !launched && triggers.cascading()) {
                    // when no move of the last round could fly, another round would only plan the same ones again
                    triggers.stopCascade();
                    finishPending = false;
                    finish(level, net, team);
                } else {
                    round(level, networks, net, team, now);
                }
            }
            if (!queue.isEmpty()) {
                startFlights(level, net, team, now);
            }
            if (!queue.isEmpty() || !flights.isEmpty()) {
                status = Status.SORTING;
            }
            flushSignal(level, net);
        }

        /** The working golems that reach each other, starting from the first: the rest wait their turn. */
        private static List<GravititeGolem> team(List<GravititeGolem> active) {
            if (active.isEmpty()) {
                return active;
            }
            List<GravititeGolem> team = new ArrayList<>();
            team.add(active.get(0));
            boolean grew = true;
            while (grew) {
                grew = false;
                for (GravititeGolem golem : active) {
                    if (team.contains(golem)) {
                        continue;
                    }
                    for (GravititeGolem member : team) {
                        if (neighbours(golem, member)) {
                            team.add(golem);
                            grew = true;
                            break;
                        }
                    }
                }
            }
            return team;
        }

        private static boolean neighbours(GravititeGolem a, GravititeGolem b) {
            return GolemNetworks.withinLinkDistance(Objects.requireNonNull(a.home()), Objects.requireNonNull(b.home()));
        }

        // -----------------------------------------------------------------------------------------------------------
        // Noticing change

        /** Looks into enough linked storage that the whole network is covered every {@link #SCAN_SPAN} ticks. */
        private void scan(ServerLevel level, GolemNetworks networks, GolemNetworks.Net net, List<GravititeGolem> team,
                          int interval, long now) {
            List<BlockPos> links = new ArrayList<>(net.links());
            if (links.isEmpty()) {
                return;
            }
            int count = Math.max(1, (int) Math.ceil(links.size() * (double) interval / SCAN_SPAN));
            for (int i = 0; i < Math.min(count, links.size()); i++) {
                cursor = Math.floorMod(cursor, links.size());
                BlockPos pos = links.get(cursor++);
                if (!level.isLoaded(pos)) {
                    continue;
                }
                GolemStorage.Role role = GolemStorage.role(level, pos);
                if (role == GolemStorage.Role.NONE) {
                    networks.prune(net, pos);
                    cache.forget(pos.asLong());
                    triggers.forget(pos.asLong());
                    continue;
                }
                long key = GolemStorage.inventoryKey(level, pos);
                InvCache.Seen seen = cache.read(level, pos, key, role);
                boolean open = GolemStorage.inUse(level, pos);
                if (!open && triggers.waitingForClose(key)) {
                    // closed without word of it (a player left, say): sorted as if they had closed it
                    triggers.closed(key, now, delay());
                }
                noticed(level, pos, key, seen, open, now);
            }
        }

        /** Acts on what reading an inventory found: a change is sorted after the delay, and freed room retried. */
        void noticed(ServerLevel level, BlockPos pos, long key, InvCache.Seen seen, boolean inUse, long now) {
            if (!seen.changed()) {
                return;
            }
            contentsChanged = true;
            // a sink that changed (its machine used something up) wants its items pulled in from anywhere
            InvCache.Entry entry = cache.get(key);
            boolean sink = entry != null && entry.inv.kind() == SortSnapshot.Kind.SINK;
            if (triggers.changed(key, now, delay(), inUse, seen.pins() || sink) && !inUse) {
                acknowledge(level, pos);
            }
            if (seen.fewer()) {
                triggers.spaceFreed(now, delay());
            }
        }

        /** The nearest working golem turns to the storage that changed and pulses: it has noticed. */
        private void acknowledge(ServerLevel level, BlockPos pos) {
            GolemNetworks.Net net = GolemNetworks.get(level).net(netId);
            if (net == null) {
                return;
            }
            GravititeGolem nearest = null;
            double best = Double.MAX_VALUE;
            for (UUID id : net.members().keySet()) {
                if (level.getEntity(id) instanceof GravititeGolem golem && golem.isWorking() && golem.home() != null
                        && GolemStorage.inReach(golem.home(), pos, AACommonConfig.GOLEM_RANGE.get())) {
                    double d = golem.home().distSqr(pos);
                    if (d < best) {
                        best = d;
                        nearest = golem;
                    }
                }
            }
            if (nearest != null) {
                nearest.face(pos);
                nearest.event(GolemTiming.EVENT_LINK_PULSE);
            }
        }

        /** Takes the golem's own changes as seen, so its work never sets off another sort. */
        private void rehash(ServerLevel level, BlockPos pos) {
            if (level.isLoaded(pos)) {
                cache.read(level, pos, GolemStorage.inventoryKey(level, pos), GolemStorage.role(level, pos));
                contentsChanged = true;
            }
        }

        // -----------------------------------------------------------------------------------------------------------
        // Planning

        /** Plans the next round of a sort, if one is due. */
        private void round(ServerLevel level, GolemNetworks networks, GolemNetworks.Net net, List<GravititeGolem> team,
                           long now) {
            SortTriggers.Round round = triggers.next(now);
            if (round == null) {
                if (status == Status.SORTING) {
                    status = unplaced > 0 ? Status.NO_SPACE : Status.SORTED;
                }
                return;
            }
            cache.trim();
            Snap snap = snapshot(level, networks, net, team);
            Set<Integer> sources = round.full() ? null : new HashSet<>();
            Set<Integer> gather = new HashSet<>();
            for (int i = 0; i < snap.keys.size(); i++) {
                long key = snap.keys.get(i);
                if (sources != null && round.focus().contains(key)) {
                    sources.add(i);
                }
                if (round.gather().contains(key)) {
                    gather.add(i);
                }
            }
            if (sources != null && sources.isEmpty() && gather.isEmpty()) {
                triggers.planned(round, Set.of(), false);
                return;
            }
            Planner.Settings settings = new Planner.Settings(AACommonConfig.GOLEM_MAJORITY_TOLERANCE.get(),
                    AACommonConfig.GOLEM_MIN_GROUP_FILL.get(), AACommonConfig.GOLEM_BULK_MIN_STACKS.get());
            boolean wholeAsked = sources == null;
            // a chest now for something else means the layout has shifted: the whole network is sorted to it
            Planner.Round planned = Planner.planRound(snap.snapshot, net.mode().sort(),
                    new Planner.Focus(sources, gather), settings, true);
            Planner.Plan plan = planned.plan();
            if (sources != null && planned.escalated()) {
                sources = null;
                round = new SortTriggers.Round(true, Set.of(), round.gather());
            }
            saveClaims(networks, net, snap, plan);
            Set<Long> stuck = new HashSet<>();
            for (int i : plan.stuck()) {
                stuck.add(snap.keys.get(i));
            }
            triggers.stuck(stuck);
            if (wholeAsked) {
                // a sort asked for (mode switch, redstone, a new link): every chest is tidied within. Otherwise only
                // those the golems put something into are, so a chest a player only took from keeps its gaps and
                // every stack stays where they last saw it
                for (long key : snap.keys) {
                    touched.add(key);
                }
            }
            unplaced = plan.unplaced();
            if (unplaced > 0 && now - lastNoSpace > 1200) {
                lastNoSpace = now;
                for (GravititeGolem golem : team) {
                    golem.shakeHead();
                }
                tellNearby(level, team, Component.translatable("message." + AlteredAether.MOD_ID + ".golem.no_space",
                        team.get(0).getName(), unplaced));
            }
            if (plan.isEmpty()) {
                triggers.planned(round, Set.of(), false);
                finish(level, net, team);
                return;
            }
            Set<Long> destinations = new HashSet<>();
            for (Move move : plan.moves()) {
                queue.add(new Planned(snap.positions.get(move.srcInv()), move.srcSlot(), snap.positions.get(move.dstInv()),
                        move.dstSlot(), snap.items.get(move.key()), move.count(), snap.keys.get(move.srcInv()),
                        snap.keys.get(move.dstInv())));
                destinations.add(snap.keys.get(move.dstInv()));
                if (QuarkSorting.available()) {
                    // with Quark, the chest items were taken out of is tidied too
                    touched.add(snap.keys.get(move.srcInv()));
                }
            }
            touched.addAll(destinations);
            launched = false;
            finishPending = !triggers.planned(round, destinations, true);
            working = true;
            status = Status.SORTING;
        }

        /** A sort is done: storage tidied within, and a chime if the golems had work to do. */
        private void finish(ServerLevel level, GolemNetworks.Net net, List<GravititeGolem> team) {
            int reordered = reorder(level);
            touched.clear();
            status = unplaced > 0 ? Status.NO_SPACE : Status.SORTED;
            if (working || reordered > 0) {
                working = false;
                for (GravititeGolem golem : team) {
                    level.playSound(null, golem.blockPosition(), AARegistries.GOLEM_CHIME.get(), SoundSource.NEUTRAL,
                            0.8F, 1.0F);
                }
                if (GolemNetworks.inventories(net, p -> level.isLoaded(p) ? GolemStorage.inventoryKey(level, p) : p.asLong()) >= 16) {
                    for (ServerPlayer player : level.players()) {
                        if (team.stream().anyMatch(golem -> golem.distanceToSqr(player) < 32 * 32)) {
                            AAAdvancements.award(player, AAAdvancements.PERFECTLY_ORGANIZED, "sorted");
                        }
                    }
                }
            }
        }

        /** Keeps the family each inventory was judged to be for, for next time and across restarts. */
        private void saveClaims(GolemNetworks networks, GolemNetworks.Net net, Snap snap, Planner.Plan plan) {
            for (int i = 0; i < snap.keys.size(); i++) {
                int claim = plan.claims()[i];
                if (claim >= 0) {
                    claims.put(snap.keys.get(i), snap.snapshot.path(claim));
                } else {
                    claims.remove(snap.keys.get(i));
                }
            }
            networks.setClaims(net, new HashMap<>(claims));
        }

        private Snap snapshot(ServerLevel level, GolemNetworks networks, GolemNetworks.Net net, List<GravititeGolem> team) {
            int range = AACommonConfig.GOLEM_RANGE.get();
            int maxSlots = AACommonConfig.GOLEM_MAX_SLOTS.get();
            List<SortSnapshot.Inv> invs = new ArrayList<>();
            List<BlockPos> positions = new ArrayList<>();
            List<Long> invKeys = new ArrayList<>();
            Set<Long> seen = new HashSet<>();
            List<BlockPos> gone = new ArrayList<>();
            int slots = 0;
            outOfReach = 0;
            inUse = 0;
            blocked = 0;
            long now = level.getGameTime();
            for (BlockPos pos : net.links()) {
                if (!level.isLoaded(pos) || !level.shouldTickBlocksAt(pos)) {
                    continue;
                }
                GolemStorage.Role role = GolemStorage.role(level, pos);
                if (role == GolemStorage.Role.NONE) {
                    gone.add(pos);
                    continue;
                }
                long key = GolemStorage.inventoryKey(level, pos);
                if (!seen.add(key)) {
                    continue;
                }
                boolean open = GolemStorage.inUse(level, pos);
                // read it now if it changed since the last scan; a change is also sorted in its own turn
                noticed(level, pos, key, cache.read(level, pos, key, role), open, now);
                InvCache.Entry entry = cache.get(key);
                if (entry == null) {
                    continue;
                }
                if (slots + entry.inv.size() > maxSlots) {
                    break;
                }
                slots += entry.inv.size();
                boolean frozen = false;
                if (team.stream().noneMatch(golem -> GolemStorage.inReach(Objects.requireNonNull(golem.home()), pos, range))) {
                    outOfReach++;
                    frozen = true;
                } else if (open) {
                    inUse++;
                    frozen = true;
                } else if (cutOff.containsKey(key) || GolemAccess.encased(level, pos)) {
                    // walled in, or open only into a pocket no golem can get into
                    blocked++;
                    frozen = true;
                } else if (role == GolemStorage.Role.SINK && GolemStorage.sinkShut(level, pos)) {
                    // a brass funnel held shut by redstone takes nothing
                    frozen = true;
                }
                String claim = claims.get(key);
                invs.add(cache.view(level, pos, entry).with(frozen, claim == null ? -1 : cache.node(claim)));
                positions.add(pos);
                invKeys.add(key);
            }
            for (BlockPos pos : gone) {
                networks.prune(net, pos);
            }
            if (cache.rankShared(invs, invKeys)) {
                awaitingBreadth = true;
            }
            List<SortSnapshot.Pos> where = new ArrayList<>(positions.size());
            for (BlockPos pos : positions) {
                where.add(new SortSnapshot.Pos(pos.getX(), pos.getY(), pos.getZ()));
            }
            return new Snap(new SortSnapshot(List.copyOf(cache.keys), invs, List.copyOf(cache.nodes), where),
                    cache.items, positions, invKeys);
        }

        /**
         * Tidies within each plain container this sort touched, as one read and write: with Quark's own sort when
         * Quark is there, else into full stacks grouped by family. Returns how many it changed.
         */
        private int reorder(ServerLevel level) {
            GolemNetworks.Net net = GolemNetworks.get(level).net(netId);
            if (net == null || touched.isEmpty()) {
                return 0;
            }
            int changed = 0;
            Set<Long> done = new HashSet<>();
            for (BlockPos pos : net.links()) {
                if (!level.isLoaded(pos)) {
                    continue;
                }
                long key = GolemStorage.inventoryKey(level, pos);
                if (!touched.contains(key) || !done.add(key) || GolemStorage.inUse(level, pos)) {
                    continue;
                }
                Container container = GolemStorage.plainContainer(level, pos);
                InvCache.Entry entry = cache.get(key);
                if (container == null || entry == null || container.getContainerSize() != entry.inv.size()
                        || GolemStorage.role(level, pos) != GolemStorage.Role.STORAGE) {
                    continue;
                }
                // it must still be as last read
                cache.read(level, pos, key, GolemStorage.Role.STORAGE);
                entry = cache.get(key);
                if (entry == null || !sameAs(container, entry.inv, cache.items)) {
                    continue;
                }
                int before = GolemStorage.contentHash(Objects.requireNonNull(GolemStorage.handler(level, pos)));
                if (!QuarkSorting.sort(container)) {
                    SortSnapshot.Inv ordered = Planner.orderWithin(entry.inv, cache.keys);
                    if (ordered == null) {
                        continue;
                    }
                    for (int slot = 0; slot < ordered.size(); slot++) {
                        int k = ordered.key()[slot];
                        container.setItem(slot, k < 0 ? ItemStack.EMPTY : cache.items.get(k).copyWithCount(ordered.count()[slot]));
                    }
                    container.setChanged();
                }
                IItemHandler after = GolemStorage.handler(level, pos);
                if (after == null || GolemStorage.contentHash(after) == before) {
                    continue;
                }
                level.sendParticles(new DustParticleOptions(GravititeGolem.mote(net.color(), false), 0.8F), pos.getX() + 0.5,
                        pos.getY() + 1.0, pos.getZ() + 0.5, 6, 0.3, 0.1, 0.3, 0);
                rehash(level, pos);
                changed++;
            }
            return changed;
        }

        private static boolean sameAs(Container container, SortSnapshot.Inv inv, List<ItemStack> items) {
            for (int slot = 0; slot < inv.size(); slot++) {
                ItemStack stack = container.getItem(slot);
                int key = inv.key()[slot];
                if (key < 0 ? !stack.isEmpty()
                        : stack.getCount() != inv.count()[slot] || !ItemStack.isSameItemSameComponents(stack, items.get(key))) {
                    return false;
                }
            }
            return true;
        }

        // -----------------------------------------------------------------------------------------------------------
        // Flights

        private void startFlights(ServerLevel level, GolemNetworks.Net net, List<GravititeGolem> team, long now) {
            int range = AACommonConfig.GOLEM_RANGE.get();
            int perGolem = AACommonConfig.GOLEM_MAX_IN_FLIGHT.get();
            // nothing here changes a block, so what each cell holds is looked at once for all of this tick's routes
            Router.CellGrid grid = GolemAccess.grid(level);
            Iterator<Planned> it = queue.iterator();
            int looked = 0;
            while (it.hasNext() && looked++ <= LOOKAHEAD) {
                Planned move = it.next();
                if (busy(move)) {
                    continue;
                }
                // the nearest golems that reach each end lift and set down
                GravititeGolem lifter = nearest(team, move.src, range, perGolem, true);
                GravititeGolem setter = nearest(team, move.dst, range, perGolem, false);
                if (setter == null || nearest(team, move.src, range, perGolem, false) == null) {
                    it.remove();
                    continue;
                }
                // but a golem that reaches both ends does the whole move: an item is handed on only when none does
                if (reachesBoth(team, move, range)) {
                    lifter = nearestToBoth(team, move, range, perGolem);
                    setter = lifter;
                }
                if (lifter == null) {
                    continue;
                }
                IItemHandler src = GolemStorage.handler(level, move.src);
                IItemHandler dst = GolemStorage.handler(level, move.dst);
                if (src == null || dst == null || move.srcSlot >= src.getSlots()
                        || GolemStorage.inUse(level, move.src) || GolemStorage.inUse(level, move.dst)) {
                    it.remove();
                    continue;
                }
                ItemStack there = src.getStackInSlot(move.srcSlot);
                ItemStack lifted = there.isEmpty() || !ItemStack.isSameItemSameComponents(there, move.item)
                        ? ItemStack.EMPTY : src.extractItem(move.srcSlot, move.count, true);
                if (lifted.isEmpty() || !ItemStack.isSameItemSameComponents(lifted, move.item)) {
                    it.remove();
                    continue;
                }
                int dstSlot = move.dstSlot;
                int accepted = dstSlot < dst.getSlots() ? lifted.getCount() - dst.insertItem(dstSlot, lifted, true).getCount() : 0;
                if (accepted <= 0) {
                    if (!flights.isEmpty()) {
                        // it may wait on a flight still in the air to make room
                        continue;
                    }
                    dstSlot = -1;
                    accepted = lifted.getCount() - ItemHandlerHelper.insertItemStacked(dst, lifted, true).getCount();
                    if (accepted <= 0) {
                        it.remove();
                        continue;
                    }
                }
                List<GravititeGolem> path = path(team, lifter, setter);
                Routed routed = route(level, grid, move, path, now);
                if (routed.waiting()) {
                    continue;
                }
                it.remove();
                if (routed.points() == null) {
                    // no way through: storage shut off from the golems is frozen until a way opens
                    noRoute(level, net, grid, move, team, now);
                    continue;
                }
                List<double[]> points = new ArrayList<>();
                for (Vec3 point : routed.points()) {
                    points.add(new double[]{point.x, point.y, point.z});
                }
                int ticks = FlightPath.ticks(points, AACommonConfig.GOLEM_MOVE_TICKS.get());
                List<UUID> ids = new ArrayList<>();
                path.forEach(golem -> ids.add(golem.getUUID()));
                Flight flight = new Flight(nextFlightId++, move, dstSlot, accepted, now, now + ticks, ids);
                flights.add(flight);
                launched = true;
                lifter.face(move.src);
                // it concentrates from the moment it looks at the chest, not a tick later
                for (GravititeGolem golem : path) {
                    if (golem.isSeated()) {
                        golem.setState(GravititeGolem.CONCENTRATING);
                    }
                }
                lift(level, move.src);
                lift(level, move.dst);
                PacketDistributor.sendToPlayersTrackingEntity(lifter, new GolemPayloads.Flight(flight.id, lifter.getId(),
                        move.item.copyWithCount(accepted), routed.points(), ticks));
                if (flights.size() >= perGolem * team.size()) {
                    break;
                }
            }
        }

        /** Whether one team golem reaches both ends of {@code move}. */
        private static boolean reachesBoth(List<GravititeGolem> team, Planned move, int range) {
            for (GravititeGolem golem : team) {
                BlockPos home = Objects.requireNonNull(golem.home());
                if (GolemStorage.inReach(home, move.src, range) && GolemStorage.inReach(home, move.dst, range)) {
                    return true;
                }
            }
            return false;
        }

        /** The golem sitting nearest the source of {@code move} that reaches both its ends and has room for a lift. */
        @Nullable
        private GravititeGolem nearestToBoth(List<GravititeGolem> team, Planned move, int range, int perGolem) {
            GravititeGolem best = null;
            double bestDist = Double.MAX_VALUE;
            for (GravititeGolem golem : team) {
                BlockPos home = Objects.requireNonNull(golem.home());
                if (!GolemStorage.inReach(home, move.src, range) || !GolemStorage.inReach(home, move.dst, range)
                        || inFlight(golem) >= perGolem) {
                    continue;
                }
                double d = home.distSqr(move.src);
                if (d < bestDist) {
                    bestDist = d;
                    best = golem;
                }
            }
            return best;
        }

        /** The team golem sitting nearest {@code pos} that reaches it (with room for another lift, if lifting). */
        @Nullable
        private GravititeGolem nearest(List<GravititeGolem> team, BlockPos pos, int range, int perGolem, boolean lifting) {
            GravititeGolem best = null;
            double bestDist = Double.MAX_VALUE;
            for (GravititeGolem golem : team) {
                BlockPos home = Objects.requireNonNull(golem.home());
                if (!GolemStorage.inReach(home, pos, range) || lifting && inFlight(golem) >= perGolem) {
                    continue;
                }
                double d = home.distSqr(pos);
                if (d < bestDist) {
                    bestDist = d;
                    best = golem;
                }
            }
            return best;
        }

        /**
         * Whether {@code move} must wait for a flight in the air: one lifting from the same slot, one filling the slot
         * it would fill or take from, or one going wherever its target takes it, which needs the target to itself.
         */
        private boolean busy(Planned move) {
            for (Flight flight : flights) {
                if (flight.srcKey == move.srcKey && flight.srcSlot == move.srcSlot
                        || flight.dstKey == move.dstKey && (flight.dstSlot == move.dstSlot || flight.dstSlot < 0)
                        || flight.srcKey == move.dstKey && flight.srcSlot == move.dstSlot
                        || flight.dstKey == move.srcKey && flight.dstSlot == move.srcSlot) {
                    return true;
                }
            }
            return false;
        }

        private int inFlight(GravititeGolem golem) {
            int n = 0;
            for (Flight flight : flights) {
                if (flight.path.get(0).equals(golem.getUUID())) {
                    n++;
                }
            }
            return n;
        }

        /** The golems an item passes from {@code from} to {@code to}, each within reach of the next. */
        private static List<GravititeGolem> path(List<GravititeGolem> team, GravititeGolem from, GravititeGolem to) {
            if (from == to) {
                return List.of(from);
            }
            Map<GravititeGolem, GravititeGolem> previous = new HashMap<>();
            Deque<GravititeGolem> open = new ArrayDeque<>();
            open.add(from);
            previous.put(from, from);
            while (!open.isEmpty()) {
                GravititeGolem at = open.poll();
                if (at == to) {
                    break;
                }
                for (GravititeGolem next : team) {
                    if (!previous.containsKey(next) && neighbours(at, next)) {
                        previous.put(next, at);
                        open.add(next);
                    }
                }
            }
            List<GravititeGolem> path = new ArrayList<>();
            for (GravititeGolem at = to; ; at = previous.get(at)) {
                path.add(0, at);
                if (at == from || previous.get(at) == null) {
                    break;
                }
            }
            return path;
        }

        /**
         * The way an item flies for {@code move}: out of an open face of its chest, past the head of each golem that
         * relays it (when more than one does), in through an open face of the target. Kept a while, and checked
         * still clear before it is used again. A route needing a search waits for the tick's search to be free, and
         * then does all the searching it needs at once.
         */
        private Routed route(ServerLevel level, Router.CellGrid grid, Planned move, List<GravititeGolem> path, long now) {
            List<Integer> ids = new ArrayList<>(path.size());
            path.forEach(golem -> ids.add(golem.getId()));
            RouteKey key = new RouteKey(move.srcKey, move.dstKey, List.copyOf(ids));
            CachedRoute cached = routes.get(key);
            if (cached != null && now < cached.expiry()) {
                if (cached.points() == null) {
                    return Routed.NONE;
                }
                if (cached.cells().stream().allMatch(cell -> GolemAccess.open(grid, cell))) {
                    return new Routed(cached.points(), false);
                }
            }
            if (waitingSearch.containsKey(key) && searchTaken(level, now)) {
                // it needs a search, and this tick's is spoken for
                return Routed.WAIT;
            }
            if (routes.size() > 512) {
                routes.values().removeIf(r -> now >= r.expiry());
            }
            if (waitingSearch.size() > 64) {
                waitingSearch.values().removeIf(tick -> now - tick > ROUTE_TTL);
            }
            List<GolemAccess.Face> from = GolemAccess.openFaces(level, move.src), to = GolemAccess.openFaces(level, move.dst);
            if (from.isEmpty() || to.isEmpty()) {
                return none(key, now);
            }
            List<BlockPos> homes = new ArrayList<>();
            path.forEach(golem -> homes.add(Objects.requireNonNull(golem.home())));
            Router.Bounds bounds = GolemAccess.bounds(homes);
            List<List<Router.Cell>> stops = new ArrayList<>();
            stops.add(from.stream().map(face -> GolemAccess.cell(face.cell())).toList());
            if (path.size() > 1) {
                for (GravititeGolem golem : path) {
                    // past its head, where it really sits; a head with a block on it is gone round instead
                    BlockPos head = golem.blockPosition().above();
                    if (GolemAccess.open(grid, head)) {
                        stops.add(List.of(GolemAccess.cell(head)));
                    }
                }
            }
            stops.add(to.stream().map(face -> GolemAccess.cell(face.cell())).toList());
            List<Vec3> points = new ArrayList<>();
            List<BlockPos> cells = new ArrayList<>();
            Router.Cell at = null;
            boolean searching = false;
            for (int i = 0; i + 1 < stops.size(); i++) {
                List<Router.Cell> starts = at == null ? stops.get(i) : List.of(at);
                List<Router.Cell> corners = Router.straight(grid, starts, stops.get(i + 1), bounds);
                if (corners == null) {
                    if (!searching) {
                        // one route a tick may search, across all networks in the level: this one, if it's free
                        if (searchTaken(level, now)) {
                            waitingSearch.put(key, now);
                            return Routed.WAIT;
                        }
                        SEARCHED.put(level, now);
                        searching = true;
                    }
                    Router.Search search = Router.search(grid, starts, stops.get(i + 1), bounds);
                    if (search.exhausted()) {
                        LOGGER.debug("A gravitite golem gave up looking for a way from {} to {}", move.src, move.dst);
                    }
                    corners = search.corners();
                }
                if (corners == null) {
                    return none(key, now);
                }
                if (at == null) {
                    BlockPos first = GolemAccess.pos(corners.get(0));
                    from.stream().filter(face -> face.cell().equals(first)).findFirst()
                            .ifPresent(face -> points.add(face.centre()));
                }
                for (int c = 0; c < corners.size(); c++) {
                    BlockPos cell = GolemAccess.pos(corners.get(c));
                    points.add(Vec3.atCenterOf(cell));
                    if (c > 0) {
                        BlockPos prev = GolemAccess.pos(corners.get(c - 1));
                        for (BlockPos p : BlockPos.betweenClosed(prev, cell)) {
                            cells.add(p.immutable());
                        }
                    } else {
                        cells.add(cell);
                    }
                }
                at = corners.get(corners.size() - 1);
            }
            waitingSearch.remove(key);
            BlockPos last = GolemAccess.pos(at);
            to.stream().filter(face -> face.cell().equals(last)).findFirst().ifPresent(face -> points.add(face.centre()));
            List<Vec3> simple = simplify(points);
            routes.put(key, new CachedRoute(simple, cells, now + ROUTE_TTL));
            return new Routed(simple, false);
        }

        private Routed none(RouteKey key, long now) {
            waitingSearch.remove(key);
            routes.put(key, new CachedRoute(null, List.of(), now + ROUTE_TTL));
            return Routed.NONE;
        }

        private static boolean searchTaken(ServerLevel level, long now) {
            Long last = SEARCHED.get(level);
            return last != null && last == now;
        }

        // -----------------------------------------------------------------------------------------------------------
        // Storage shut off from the golems

        /**
         * {@code move} found no way through. Each end whose open faces lead only into a pocket that holds no golem is
         * shut off, as is all other linked storage opening only into that pocket: it is frozen and shown blocked
         * until a way opens. If the target was, the items the move was for are planned again, for somewhere else.
         */
        private void noRoute(ServerLevel level, GolemNetworks.Net net, Router.CellGrid grid, Planned move,
                             List<GravititeGolem> team, long now) {
            List<Router.Cell> heads = heads(team, grid);
            if (heads.isEmpty()) {
                return;
            }
            Router.Bounds bounds = GolemAccess.bounds(homes(team));
            boolean targetShut = cutOff.containsKey(move.dstKey);
            for (int end = 0; end < 2; end++) {
                BlockPos pos = end == 0 ? move.src : move.dst;
                if (cutOff.containsKey(end == 0 ? move.srcKey : move.dstKey)) {
                    continue;
                }
                Router.Pocket pocket = Router.flood(grid, GolemAccess.faceCells(level, pos), heads, bounds, CUT_OFF_CAP);
                if (pocket.reach() == Router.Reach.SEALED) {
                    shutOff(level, net, pocket);
                    targetShut |= end == 1;
                }
            }
            if (targetShut && !cutOff.containsKey(move.srcKey)) {
                triggers.changed(move.srcKey, now, delay(), false, false);
            }
        }

        /** Marks every linked inventory whose open faces all lie in {@code pocket} as shut off. */
        private void shutOff(ServerLevel level, GolemNetworks.Net net, Router.Pocket pocket) {
            for (BlockPos link : net.links()) {
                if (!level.isLoaded(link)) {
                    continue;
                }
                long key = GolemStorage.inventoryKey(level, link);
                if (cutOff.containsKey(key)) {
                    continue;
                }
                List<Router.Cell> faces = GolemAccess.faceCells(level, link);
                if (!faces.isEmpty() && faces.stream().allMatch(pocket::contains)) {
                    cutOff.put(key, link.immutable());
                    // shown at once; the next look over the network counts it among the blocked again
                    blocked++;
                }
            }
        }

        /**
         * Looks again at storage found shut off: what is gone, unlinked or walled in is dropped (walled-in storage is
         * counted as encased instead), and if any has a way to a golem again the whole network is sorted.
         */
        private void recheckCutOff(ServerLevel level, GolemNetworks.Net net, List<GravititeGolem> team, long now) {
            cutOffChecked = now;
            Router.CellGrid grid = GolemAccess.grid(level);
            List<Router.Cell> heads = heads(team, grid);
            Router.Bounds bounds = GolemAccess.bounds(homes(team));
            List<Router.Pocket> sealed = new ArrayList<>();
            boolean opened = false;
            for (Iterator<Map.Entry<Long, BlockPos>> it = cutOff.entrySet().iterator(); it.hasNext(); ) {
                BlockPos pos = it.next().getValue();
                if (!level.isLoaded(pos) || !net.links().contains(pos)) {
                    it.remove();
                    continue;
                }
                List<Router.Cell> faces = GolemAccess.faceCells(level, pos);
                if (faces.isEmpty()) {
                    it.remove();
                    continue;
                }
                // storage sharing a pocket found sealed a moment ago is shut off too
                if (sealed.stream().anyMatch(pocket -> faces.stream().allMatch(pocket::contains))) {
                    continue;
                }
                Router.Pocket pocket = heads.isEmpty() ? null : Router.flood(grid, faces, heads, bounds, CUT_OFF_CAP);
                if (pocket != null && pocket.reach() == Router.Reach.SEALED) {
                    sealed.add(pocket);
                } else {
                    it.remove();
                    opened = true;
                }
            }
            if (opened) {
                // a route found missing before the way opened would turn the sort's moves away again
                routes.values().removeIf(route -> route.points() == null);
                triggers.requestFullSort(now, delay());
            }
        }

        /** The open cells the team golems take up: storage an item can fly from to one of them is not shut off. */
        private static List<Router.Cell> heads(List<GravititeGolem> team, Router.CellGrid grid) {
            List<Router.Cell> cells = new ArrayList<>();
            for (GravititeGolem golem : team) {
                BlockPos feet = golem.blockPosition();
                for (BlockPos pos : List.of(feet, feet.above())) {
                    if (GolemAccess.open(grid, pos)) {
                        cells.add(GolemAccess.cell(pos));
                    }
                }
            }
            return cells;
        }

        private static List<BlockPos> homes(List<GravititeGolem> team) {
            List<BlockPos> homes = new ArrayList<>();
            team.forEach(golem -> homes.add(Objects.requireNonNull(golem.home())));
            return homes;
        }

        /** The points without repeats or points in the middle of a straight run. */
        private static List<Vec3> simplify(List<Vec3> points) {
            List<Vec3> out = new ArrayList<>();
            for (Vec3 point : points) {
                if (!out.isEmpty() && out.get(out.size() - 1).distanceToSqr(point) < 1.0E-6) {
                    continue;
                }
                if (out.size() >= 2) {
                    Vec3 a = out.get(out.size() - 2), b = out.get(out.size() - 1);
                    Vec3 ab = b.subtract(a).normalize(), bc = point.subtract(b).normalize();
                    if (ab.dot(bc) > 0.9999) {
                        out.set(out.size() - 1, point);
                        continue;
                    }
                }
                out.add(point);
            }
            return out;
        }

        /** Whether both ends of a flight still work: the item is still there, the target still has room. */
        private boolean stillGood(ServerLevel level, Flight flight) {
            IItemHandler src = GolemStorage.handler(level, flight.src);
            IItemHandler dst = GolemStorage.handler(level, flight.dst);
            if (src == null || dst == null || flight.srcSlot >= src.getSlots()
                    || GolemStorage.inUse(level, flight.src) || GolemStorage.inUse(level, flight.dst)
                    || GolemAccess.encased(level, flight.src) || GolemAccess.encased(level, flight.dst)) {
                return false;
            }
            ItemStack there = src.getStackInSlot(flight.srcSlot);
            if (there.isEmpty() || !ItemStack.isSameItemSameComponents(there, flight.item)) {
                return false;
            }
            ItemStack one = flight.item.copyWithCount(1);
            return (flight.dstSlot >= 0 && flight.dstSlot < dst.getSlots() && dst.insertItem(flight.dstSlot, one, true).isEmpty())
                    || ItemHandlerHelper.insertItemStacked(dst, one, true).isEmpty();
        }

        /** The move itself: checked once more, then taken and put in one tick. */
        private void land(ServerLevel level, GolemNetworks.Net net, Flight flight) {
            IItemHandler src = GolemStorage.handler(level, flight.src);
            IItemHandler dst = GolemStorage.handler(level, flight.dst);
            if (src == null || dst == null || flight.srcSlot >= src.getSlots()
                    || GolemAccess.encased(level, flight.src) || GolemAccess.encased(level, flight.dst)) {
                cancel(level, flight);
                return;
            }
            ItemStack taken = src.extractItem(flight.srcSlot, flight.count, true);
            if (taken.isEmpty() || !ItemStack.isSameItemSameComponents(taken, flight.item)) {
                cancel(level, flight);
                return;
            }
            boolean bySlot = flight.dstSlot >= 0 && flight.dstSlot < dst.getSlots();
            int accepted = bySlot ? taken.getCount() - dst.insertItem(flight.dstSlot, taken, true).getCount() : 0;
            if (accepted <= 0) {
                bySlot = false;
                accepted = taken.getCount() - ItemHandlerHelper.insertItemStacked(dst, taken, true).getCount();
            }
            if (accepted <= 0) {
                cancel(level, flight);
                return;
            }
            ItemStack real = src.extractItem(flight.srcSlot, accepted, false);
            if (real.isEmpty()) {
                cancel(level, flight);
                return;
            }
            if (!ItemStack.isSameItemSameComponents(real, flight.item)) {
                putBack(level, net, flight, src, real);
                cancel(level, flight);
                return;
            }
            ItemStack left = bySlot ? dst.insertItem(flight.dstSlot, real, false) : ItemHandlerHelper.insertItemStacked(dst, real, false);
            if (!left.isEmpty()) {
                left = ItemHandlerHelper.insertItemStacked(dst, left, false);
            }
            if (!left.isEmpty()) {
                putBack(level, net, flight, src, left);
            }
            flights.remove(flight);
            drop(level, flight.src);
            drop(level, flight.dst);
            rehash(level, flight.src);
            rehash(level, flight.dst);
            sendEnd(level, flight, true);
        }

        /** Lands every flight whose source or target a player has just opened. */
        void landOpened(ServerLevel level, GolemNetworks.Net net) {
            for (Flight flight : new ArrayList<>(flights)) {
                if (level.isLoaded(flight.src) && level.isLoaded(flight.dst)
                        && (GolemStorage.inUse(level, flight.src) || GolemStorage.inUse(level, flight.dst))) {
                    land(level, net, flight);
                }
            }
        }

        /**
         * What the target wouldn't take after all goes back where it came from, else into any linked storage, and
         * only if nothing takes it is it dropped at the chest.
         */
        private void putBack(ServerLevel level, GolemNetworks.Net net, Flight flight, IItemHandler src, ItemStack stack) {
            stack = src.insertItem(flight.srcSlot, stack, false);
            if (!stack.isEmpty()) {
                stack = ItemHandlerHelper.insertItemStacked(src, stack, false);
            }
            for (BlockPos pos : net.links()) {
                if (stack.isEmpty()) {
                    return;
                }
                GolemStorage.Role role = level.isLoaded(pos) ? GolemStorage.role(level, pos) : GolemStorage.Role.NONE;
                if (role == GolemStorage.Role.STORAGE || role == GolemStorage.Role.MISC) {
                    IItemHandler handler = GolemStorage.handler(level, pos);
                    if (handler != null) {
                        stack = ItemHandlerHelper.insertItemStacked(handler, stack, false);
                    }
                }
            }
            if (!stack.isEmpty()) {
                LOGGER.warn("A gravitite golem couldn't put {} back anywhere; dropped it at {}", stack, flight.src);
                Block.popResource(level, flight.src.above(), stack);
            }
        }

        void cancel(ServerLevel level, Flight flight) {
            if (flights.remove(flight)) {
                drop(level, flight.src);
                drop(level, flight.dst);
                sendEnd(level, flight, false);
                // what it was moving is looked at again soon
                triggers.changed(flight.srcKey, level.getGameTime(), delay(), false, false);
            }
        }

        void cancelAll(ServerLevel level) {
            for (Flight flight : new ArrayList<>(flights)) {
                cancel(level, flight);
            }
            queue.clear();
        }

        private void sendEnd(ServerLevel level, Flight flight, boolean landed) {
            Entity lifter = level.getEntity(flight.path.get(0));
            GolemPayloads.FlightEnd end = new GolemPayloads.FlightEnd(flight.id, landed);
            if (lifter != null) {
                PacketDistributor.sendToPlayersTrackingEntity(lifter, end);
            } else {
                PacketDistributor.sendToPlayersNear(level, null, flight.src.getX(), flight.src.getY(), flight.src.getZ(), 64, end);
            }
        }

        // -----------------------------------------------------------------------------------------------------------
        // Comparators

        int computeSignal(ServerLevel level, GolemNetworks.Net net) {
            double fill = 0;
            int slots = 0;
            boolean any = false;
            Set<Long> seen = new HashSet<>();
            for (BlockPos pos : net.links()) {
                if (!level.isLoaded(pos)) {
                    continue;
                }
                long key = GolemStorage.inventoryKey(level, pos);
                if (!seen.add(key)) {
                    continue;
                }
                InvCache.Entry entry = cache.get(key);
                if (entry == null) {
                    cache.read(level, pos, key, GolemStorage.role(level, pos));
                    entry = cache.get(key);
                }
                if (entry == null) {
                    continue;
                }
                fill += entry.fill;
                slots += entry.slots;
                any |= entry.total > 0;
            }
            return slots == 0 ? 0 : (int) Math.floor(fill / slots * 14.0) + (any ? 1 : 0);
        }

        /** Tells comparators behind the golems' seats that the network's contents changed, at most once a tick. */
        private void flushSignal(ServerLevel level, GolemNetworks.Net net) {
            if (!contentsChanged) {
                return;
            }
            contentsChanged = false;
            signalTick = -1;
            for (GolemNetworks.Member member : net.members().values()) {
                BlockPos floor = member.home().below();
                if (level.isLoaded(floor)) {
                    level.updateNeighbourForOutputSignal(floor, level.getBlockState(floor).getBlock());
                }
            }
        }

        // -----------------------------------------------------------------------------------------------------------
        // Lids: a chest's lid lifts while the golem works on it. Only chests: the event isn't saved and fires nothing.

        private void lift(ServerLevel level, BlockPos pos) {
            long key = pos.asLong();
            if (lifted.merge(key, 1, Integer::sum) == 1) {
                BlockState state = level.getBlockState(pos);
                if (level.getBlockEntity(pos) instanceof ChestBlockEntity) {
                    level.blockEvent(pos, state.getBlock(), 1, ChestBlockEntity.getOpenCount(level, pos) + 1);
                } else {
                    GolemNetworks.Net net = GolemNetworks.get(level).net(netId);
                    level.sendParticles(new DustParticleOptions(GravititeGolem.mote(
                                    net == null ? GravititeGolem.DEFAULT_COLOR : net.color(), true), 0.7F), pos.getX() + 0.5,
                            pos.getY() + 1.05, pos.getZ() + 0.5, 4, 0.25, 0.05, 0.25, 0);
                }
            }
        }

        private void drop(ServerLevel level, BlockPos pos) {
            long key = pos.asLong();
            Integer count = lifted.get(key);
            if (count == null) {
                return;
            }
            if (count > 1) {
                lifted.put(key, count - 1);
                return;
            }
            lifted.remove(key);
            if (level.isLoaded(pos) && level.getBlockEntity(pos) instanceof ChestBlockEntity) {
                level.blockEvent(pos, level.getBlockState(pos).getBlock(), 1, ChestBlockEntity.getOpenCount(level, pos));
            }
        }

        private static void tellNearby(ServerLevel level, List<GravititeGolem> team, Component message) {
            for (ServerPlayer player : level.players()) {
                if (team.stream().anyMatch(golem -> golem.distanceToSqr(player) < 16 * 16)) {
                    player.displayClientMessage(message, true);
                }
            }
        }
    }
}
