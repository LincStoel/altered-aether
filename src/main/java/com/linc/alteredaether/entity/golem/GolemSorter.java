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
import com.linc.alteredaether.entity.golem.sort.Move;
import com.linc.alteredaether.entity.golem.sort.Planner;
import com.linc.alteredaether.entity.golem.sort.SortSnapshot;
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
 * <p>All networks share a small time budget each tick for looking into storage and planning; flights in the air are
 * always seen through. Nothing here loads a chunk.
 */
@EventBusSubscriber(modid = AlteredAether.MOD_ID)
public final class GolemSorter {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** Time all networks may spend looking and planning each tick. */
    private static final long BUDGET_NANOS = 500_000;
    /** Plans in a row, with nothing changed from outside, before a network decides it is going round in circles. */
    private static final int MAX_ROUNDS = 12;
    /** Moves looked at past the first when the first can't start yet. */
    private static final int LOOKAHEAD = 8;
    /** Ticks between checks that both ends of a flight still work. */
    private static final int RECHECK = 5;

    private static final Map<ServerLevel, Map<UUID, Runtime>> RUNTIMES = new WeakHashMap<>();
    private static int nextFlightId = 1;
    private static int rotation;

    /** What a network is doing, for Jade and for messages. */
    public enum Status { SORTED, SORTING, PAUSED, NO_SPACE, IDLE }

    /** A status and the numbers that go with it. */
    public record Report(Status status, int movesLeft, int unplaced, int outOfReach, int inUse) {
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

    /** Has the network look at everything again soon: something was linked, unlinked, opened or switched. */
    public static void markDirty(ServerLevel level, GolemNetworks.Net net) {
        Runtime runtime = runtime(level, net.id);
        runtime.dirty = true;
        runtime.rounds = 0;
    }

    public static Report report(ServerLevel level, GolemNetworks.Net net) {
        Runtime runtime = existing(level, net.id);
        if (runtime == null) {
            return new Report(Status.IDLE, 0, 0, 0, 0);
        }
        return new Report(runtime.status, runtime.queue.size() + runtime.flights.size(), runtime.unplaced,
                runtime.outOfReach, runtime.inUse);
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
            if (runtimes != null) {
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

    /** A player closing a linked chest is the quickest sign something changed. */
    @SubscribeEvent
    public static void onContainerClose(PlayerContainerEvent.Close event) {
        if (event.getEntity() instanceof ServerPlayer player && player.level() instanceof ServerLevel level) {
            for (GolemNetworks.Net net : GolemNetworks.get(level).near(player.blockPosition(),
                    AACommonConfig.GOLEM_RANGE.get() * 2 + 8)) {
                markDirty(level, net);
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

    /** The items a snapshot names, by key, and where each inventory in it is. */
    private record Snap(SortSnapshot snapshot, List<ItemStack> items, List<BlockPos> positions, List<Long> keys) {
    }

    /** One network's sorting, kept while the server runs. */
    private static final class Runtime {
        final UUID netId;
        final Map<Long, Integer> hashes = new HashMap<>();
        final Deque<Planned> queue = new ArrayDeque<>();
        final List<Flight> flights = new ArrayList<>();
        final Map<Long, Integer> lifted = new HashMap<>();
        boolean dirty = true, verify, working;
        int rounds, cursor, version = -1;
        Status status = Status.IDLE;
        int unplaced, outOfReach, inUse;
        long lastNoSpace = Long.MIN_VALUE;

        Runtime(UUID netId) {
            this.netId = netId;
        }

        void tick(ServerLevel level, GolemNetworks networks, GolemNetworks.Net net, long deadline) {
            if (net.version() != version) {
                version = net.version();
                dirty = true;
                rounds = 0;
            }
            long now = level.getGameTime();
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
                status = loaded.values().stream().anyMatch(GravititeGolem::isPaused) ? Status.PAUSED : Status.IDLE;
                return;
            }

            int interval = AACommonConfig.GOLEM_SCAN_INTERVAL.get();
            if (now % interval == 0 && System.nanoTime() < deadline) {
                scanNext(level, networks, net);
            }
            if (queue.isEmpty() && flights.isEmpty() && (dirty || verify) && System.nanoTime() < deadline) {
                plan(level, networks, net, team, now);
            }
            if (!queue.isEmpty()) {
                startFlights(level, team, now);
            }
            if (!queue.isEmpty() || !flights.isEmpty()) {
                status = Status.SORTING;
            }
        }

        /** The working golems that can reach each other, starting from the first: the rest wait their turn. */
        private static List<GravititeGolem> team(List<GravititeGolem> active) {
            if (active.isEmpty()) {
                return active;
            }
            int range = AACommonConfig.GOLEM_RANGE.get();
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
                        if (neighbours(golem, member, range)) {
                            team.add(golem);
                            grew = true;
                            break;
                        }
                    }
                }
            }
            return team;
        }

        private static boolean neighbours(GravititeGolem a, GravititeGolem b, int range) {
            BlockPos ha = Objects.requireNonNull(a.home()), hb = Objects.requireNonNull(b.home());
            double dx = ha.getX() - hb.getX(), dz = ha.getZ() - hb.getZ();
            return dx * dx + dz * dz <= 4.0 * range * range && Math.abs(ha.getY() - hb.getY()) <= range;
        }

        // -----------------------------------------------------------------------------------------------------------
        // Noticing change

        private void scanNext(ServerLevel level, GolemNetworks networks, GolemNetworks.Net net) {
            List<BlockPos> links = new ArrayList<>(net.links());
            if (links.isEmpty()) {
                return;
            }
            cursor = Math.floorMod(cursor, links.size());
            BlockPos pos = links.get(cursor++);
            if (!level.isLoaded(pos)) {
                return;
            }
            if (GolemStorage.role(level, pos) == GolemStorage.Role.NONE) {
                networks.prune(net, pos);
                return;
            }
            IItemHandler handler = GolemStorage.handler(level, pos);
            if (handler == null) {
                return;
            }
            long key = GolemStorage.inventoryKey(level, pos);
            int hash = GolemStorage.contentHash(handler);
            Integer old = hashes.put(key, hash);
            if (old == null || old != hash) {
                dirty = true;
                rounds = 0;
            }
        }

        private void rehash(ServerLevel level, BlockPos pos) {
            IItemHandler handler = GolemStorage.handler(level, pos);
            if (handler != null) {
                hashes.put(GolemStorage.inventoryKey(level, pos), GolemStorage.contentHash(handler));
            }
        }

        // -----------------------------------------------------------------------------------------------------------
        // Planning

        private void plan(ServerLevel level, GolemNetworks networks, GolemNetworks.Net net, List<GravititeGolem> team,
                          long now) {
            Snap snap = snapshot(level, networks, net, team);
            dirty = false;
            Planner.Plan plan = Planner.plan(snap.snapshot, net.mode().sort());
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
                verify = false;
                if (reorder(level, snap)) {
                    verify = true;
                    working = true;
                    return;
                }
                status = unplaced > 0 ? Status.NO_SPACE : Status.SORTED;
                if (working) {
                    working = false;
                    for (GravititeGolem golem : team) {
                        level.playSound(null, golem.blockPosition(), AARegistries.GOLEM_CHIME.get(), SoundSource.NEUTRAL,
                                0.8F, 1.0F);
                    }
                    if (snap.positions.size() >= 16) {
                        for (ServerPlayer player : level.players()) {
                            if (team.stream().anyMatch(golem -> golem.distanceToSqr(player) < 32 * 32)) {
                                AAAdvancements.award(player, AAAdvancements.PERFECTLY_ORGANIZED, "sorted");
                            }
                        }
                    }
                }
                rounds = 0;
                return;
            }
            if (++rounds > MAX_ROUNDS) {
                // going round in circles: rest until something changes from outside
                LOGGER.debug("Golem network {} stopped after {} plans without settling", netId, MAX_ROUNDS);
                verify = false;
                status = Status.SORTED;
                return;
            }
            for (Move move : plan.moves()) {
                queue.add(new Planned(snap.positions.get(move.srcInv()), move.srcSlot(), snap.positions.get(move.dstInv()),
                        move.dstSlot(), snap.items.get(move.key()), move.count(), snap.keys.get(move.srcInv()),
                        snap.keys.get(move.dstInv())));
            }
            verify = true;
            working = true;
            status = Status.SORTING;
        }

        private Snap snapshot(ServerLevel level, GolemNetworks networks, GolemNetworks.Net net, List<GravititeGolem> team) {
            int range = AACommonConfig.GOLEM_RANGE.get();
            int maxSlots = AACommonConfig.GOLEM_MAX_SLOTS.get();
            List<SortSnapshot.Key> keys = new ArrayList<>();
            List<ItemStack> items = new ArrayList<>();
            Map<ItemKey, Integer> index = new HashMap<>();
            List<SortSnapshot.Inv> invs = new ArrayList<>();
            List<BlockPos> positions = new ArrayList<>();
            List<Long> invKeys = new ArrayList<>();
            Set<Long> seen = new HashSet<>();
            List<BlockPos> gone = new ArrayList<>();
            int slots = 0;
            outOfReach = 0;
            inUse = 0;
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
                if (team.stream().noneMatch(golem -> GolemStorage.inReach(Objects.requireNonNull(golem.home()), pos, range))) {
                    outOfReach++;
                    continue;
                }
                if (GolemStorage.inUse(level, pos)) {
                    inUse++;
                    continue;
                }
                IItemHandler handler = GolemStorage.handler(level, pos);
                if (handler == null) {
                    continue;
                }
                int n = handler.getSlots();
                if (slots + n > maxSlots) {
                    break;
                }
                slots += n;
                int[] limit = new int[n], slotKey = new int[n], count = new int[n];
                for (int slot = 0; slot < n; slot++) {
                    limit[slot] = handler.getSlotLimit(slot);
                    ItemStack stack = handler.getStackInSlot(slot);
                    if (stack.isEmpty()) {
                        slotKey[slot] = -1;
                    } else {
                        slotKey[slot] = keyOf(stack, keys, items, index);
                        count[slot] = stack.getCount();
                    }
                }
                Set<Integer> pins = new HashSet<>();
                for (ItemStack pin : GolemStorage.pins(level, pos)) {
                    pins.add(keyOf(pin, keys, items, index));
                }
                boolean plain = GolemStorage.plainContainer(level, pos) != null;
                invs.add(new SortSnapshot.Inv(role == GolemStorage.Role.SOURCE, plain, limit, slotKey, count, pins));
                positions.add(pos);
                invKeys.add(key);
                hashes.put(key, GolemStorage.contentHash(handler));
            }
            for (BlockPos pos : gone) {
                networks.prune(net, pos);
            }
            return new Snap(new SortSnapshot(keys, invs), items, positions, invKeys);
        }

        /** The key for {@code stack}, numbering it if it is new. */
        private static int keyOf(ItemStack stack, List<SortSnapshot.Key> keys, List<ItemStack> items, Map<ItemKey, Integer> index) {
            ItemKey key = new ItemKey(stack);
            Integer known = index.get(key);
            if (known != null) {
                return known;
            }
            int id = keys.size();
            keys.add(new SortSnapshot.Key(stack.getMaxStackSize(), GolemGroups.group(stack), GolemGroups.order(stack)));
            items.add(stack.copyWithCount(1));
            index.put(new ItemKey(items.get(id)), id);
            return id;
        }

        /** Reorders each plain container in place, as one read and write; true if any changed. */
        private boolean reorder(ServerLevel level, Snap snap) {
            boolean any = false;
            for (int i = 0; i < snap.positions.size(); i++) {
                SortSnapshot.Inv inv = snap.snapshot.invs().get(i);
                if (!inv.plain()) {
                    continue;
                }
                BlockPos pos = snap.positions.get(i);
                if (GolemStorage.inUse(level, pos)) {
                    continue;
                }
                Container container = GolemStorage.plainContainer(level, pos);
                if (container == null || container.getContainerSize() != inv.size()) {
                    continue;
                }
                // read it again: it must still be as the snapshot saw it
                if (!sameAs(container, inv, snap.items)) {
                    dirty = true;
                    continue;
                }
                SortSnapshot.Inv ordered = Planner.orderWithin(inv, snap.snapshot.keys());
                if (ordered == null) {
                    continue;
                }
                for (int slot = 0; slot < ordered.size(); slot++) {
                    int key = ordered.key()[slot];
                    container.setItem(slot, key < 0 ? ItemStack.EMPTY : snap.items.get(key).copyWithCount(ordered.count()[slot]));
                }
                container.setChanged();
                level.sendParticles(new DustParticleOptions(GravititeGolem.GRAVITITE, 0.8F), pos.getX() + 0.5,
                        pos.getY() + 1.0, pos.getZ() + 0.5, 6, 0.3, 0.1, 0.3, 0);
                rehash(level, pos);
                any = true;
            }
            return any;
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

        private void startFlights(ServerLevel level, List<GravititeGolem> team, long now) {
            int range = AACommonConfig.GOLEM_RANGE.get();
            int perGolem = AACommonConfig.GOLEM_MAX_IN_FLIGHT.get();
            Iterator<Planned> it = queue.iterator();
            int looked = 0;
            while (it.hasNext() && looked++ <= LOOKAHEAD) {
                Planned move = it.next();
                if (busy(move)) {
                    continue;
                }
                GravititeGolem lifter = null, setter = null;
                for (GravititeGolem golem : team) {
                    BlockPos home = Objects.requireNonNull(golem.home());
                    if (lifter == null && GolemStorage.inReach(home, move.src, range) && inFlight(golem) < perGolem) {
                        lifter = golem;
                    }
                    if (setter == null && GolemStorage.inReach(home, move.dst, range)) {
                        setter = golem;
                    }
                }
                if (lifter == null || setter == null) {
                    if (team.stream().noneMatch(g -> GolemStorage.inReach(Objects.requireNonNull(g.home()), move.src, range))
                            || setter == null) {
                        it.remove();
                    }
                    continue;
                }
                IItemHandler src = GolemStorage.handler(level, move.src);
                IItemHandler dst = GolemStorage.handler(level, move.dst);
                if (src == null || dst == null || move.srcSlot >= src.getSlots()
                        || GolemStorage.inUse(level, move.src) || GolemStorage.inUse(level, move.dst)) {
                    it.remove();
                    dirty = true;
                    continue;
                }
                ItemStack there = src.getStackInSlot(move.srcSlot);
                ItemStack lifted = there.isEmpty() || !ItemStack.isSameItemSameComponents(there, move.item)
                        ? ItemStack.EMPTY : src.extractItem(move.srcSlot, move.count, true);
                if (lifted.isEmpty() || !ItemStack.isSameItemSameComponents(lifted, move.item)) {
                    it.remove();
                    dirty = true;
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
                        dirty = true;
                        continue;
                    }
                }
                it.remove();
                List<UUID> path = path(team, lifter, setter, range);
                int ticks = AACommonConfig.GOLEM_MOVE_TICKS.get();
                Flight flight = new Flight(nextFlightId++, move, dstSlot, accepted, now, now + ticks, path);
                flights.add(flight);
                lift(level, move.src);
                lift(level, move.dst);
                List<Vec3> via = new ArrayList<>();
                if (path.size() > 1) {
                    for (UUID id : path) {
                        Entity golem = level.getEntity(id);
                        if (golem != null) {
                            via.add(golem.position().add(0, golem.getBbHeight() + 0.6, 0));
                        }
                    }
                }
                PacketDistributor.sendToPlayersTrackingEntity(lifter, new GolemPayloads.Flight(flight.id, lifter.getId(),
                        move.item.copyWithCount(accepted), top(move.src), top(move.dst), via, ticks));
                if (flights.size() >= perGolem * team.size()) {
                    break;
                }
            }
        }

        private boolean busy(Planned move) {
            for (Flight flight : flights) {
                if (flight.src.equals(move.src) && flight.srcSlot == move.srcSlot || flight.dstKey == move.dstKey
                        || flight.srcKey == move.dstKey && flight.srcSlot == move.dstSlot) {
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
        private static List<UUID> path(List<GravititeGolem> team, GravititeGolem from, GravititeGolem to, int range) {
            if (from == to) {
                return List.of(from.getUUID());
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
                    if (!previous.containsKey(next) && neighbours(at, next, range)) {
                        previous.put(next, at);
                        open.add(next);
                    }
                }
            }
            List<UUID> path = new ArrayList<>();
            for (GravititeGolem at = to; ; at = previous.get(at)) {
                path.add(0, at.getUUID());
                if (at == from || previous.get(at) == null) {
                    break;
                }
            }
            return path;
        }

        private static Vec3 top(BlockPos pos) {
            return new Vec3(pos.getX() + 0.5, pos.getY() + 0.9, pos.getZ() + 0.5);
        }

        /** Whether both ends of a flight still work: the item is still there, the target still has room. */
        private boolean stillGood(ServerLevel level, Flight flight) {
            IItemHandler src = GolemStorage.handler(level, flight.src);
            IItemHandler dst = GolemStorage.handler(level, flight.dst);
            if (src == null || dst == null || flight.srcSlot >= src.getSlots()
                    || GolemStorage.inUse(level, flight.src) || GolemStorage.inUse(level, flight.dst)) {
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
            if (src == null || dst == null || flight.srcSlot >= src.getSlots()) {
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
                if (level.isLoaded(pos) && GolemStorage.role(level, pos) == GolemStorage.Role.STORAGE) {
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
                dirty = true;
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
        // Lids: a chest's lid lifts while the golem works on it. Only chests: the event isn't saved and fires nothing.

        private void lift(ServerLevel level, BlockPos pos) {
            long key = pos.asLong();
            if (lifted.merge(key, 1, Integer::sum) == 1) {
                BlockState state = level.getBlockState(pos);
                if (level.getBlockEntity(pos) instanceof ChestBlockEntity) {
                    level.blockEvent(pos, state.getBlock(), 1, ChestBlockEntity.getOpenCount(level, pos) + 1);
                } else {
                    level.sendParticles(new DustParticleOptions(GravititeGolem.GRAVITITE_DARK, 0.7F), pos.getX() + 0.5,
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

    /** An item without its count, compared and hashed as stacks are merged. */
    private static final class ItemKey {
        final ItemStack stack;
        final int hash;

        ItemKey(ItemStack stack) {
            this.stack = stack;
            this.hash = ItemStack.hashItemAndComponents(stack);
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof ItemKey other && ItemStack.isSameItemSameComponents(stack, other.stack);
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }
}
