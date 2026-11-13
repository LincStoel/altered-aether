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

import com.linc.alteredaether.config.AACommonConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.ToLongFunction;

/**
 * Every gravitite golem network in a dimension: which golems belong to it, where each sits, and the storage linked to
 * it. The one place links live, so a golem's links outlast it being unloaded, and a chest belongs to one network only.
 *
 * <p>Networks follow from where golems sit and what colour they are dyed: golems of one colour that sit within reach
 * of each other, directly or through others, are one network, of at most {@code max_network_golems}. A golem whose
 * arrival (built, dyed or sent there) would take a network past that is <b>refused</b>: it stays in a network of its
 * own and rests until there is room. Networks are worked out again only when a golem is built, removed, dyed or seated
 * somewhere new ({@link #join}, {@link #leave}).
 *
 * <p>A network's id outlives changes where it can: when networks join, the biggest keeps its id (and its mode), and
 * the others' ids become aliases of it, so pebbles of any of them still work ({@link #resolve}); when one splits, the
 * biggest part keeps it.
 */
public final class GolemNetworks extends SavedData {
    private static final String ID = "altered_aether_golem_networks";

    private final Map<UUID, Net> nets = new LinkedHashMap<>();
    private final Map<UUID, UUID> golemNet = new HashMap<>();
    private final Map<Long, UUID> linkNet = new HashMap<>();
    /** Ids of networks that joined others, to the id of the one they joined. */
    private final Map<UUID, UUID> aliases = new HashMap<>();
    /** The block under each golem's home, to its network: what a comparator behind it reads. */
    private final Map<Long, UUID> floorIndex = new HashMap<>();
    /** Networks the last {@link #regroup} joined together, for a whole-network sort. */
    private final Set<UUID> merged = new HashSet<>();

    /**
     * One golem in a network: where it sits, what it is called (for messages when it isn't loaded), its colour, and
     * whether it was refused by a full network.
     */
    public record Member(BlockPos home, String name, DyeColor color, boolean refused) {
        Member with(boolean refused) {
            return new Member(home, name, color, refused);
        }
    }

    /** A network: its golems, the mode they sort in, its linked storage in the order it was linked, and its claims. */
    public static final class Net {
        public final UUID id;
        private GolemMode mode = GolemMode.TIDY;
        private final Map<UUID, Member> members = new LinkedHashMap<>();
        private final Set<BlockPos> links = new LinkedHashSet<>();
        /** The family each linked inventory was last judged to be for, by inventory key and family name. */
        private final Map<Long, String> claims = new HashMap<>();
        /** Bumped on every change, so clients are sent fresh link lists. */
        private int version;

        Net(UUID id) {
            this.id = id;
        }

        public GolemMode mode() {
            return mode;
        }

        public Map<UUID, Member> members() {
            return Collections.unmodifiableMap(members);
        }

        public Set<BlockPos> links() {
            return Collections.unmodifiableSet(links);
        }

        public Map<Long, String> claims() {
            return Collections.unmodifiableMap(claims);
        }

        public int version() {
            return version;
        }

        /** The colour its golems are dyed. */
        public DyeColor color() {
            return members.isEmpty() ? GravititeGolem.DEFAULT_COLOR : members.values().iterator().next().color();
        }

        /** The most storage blocks this network may link. */
        public int maxLinks() {
            return AACommonConfig.GOLEM_MAX_LINKS.get() * Math.max(1, members.size());
        }

        /** Whether some golem of this network reaches {@code pos} from where it sits. */
        public boolean reaches(BlockPos pos) {
            int range = AACommonConfig.GOLEM_RANGE.get();
            for (Member member : members.values()) {
                if (GolemStorage.inReach(member.home(), pos, range)) {
                    return true;
                }
            }
            return false;
        }

        /** The network's name in messages: its first golem's. */
        public String name() {
            return members.isEmpty() ? "" : members.values().iterator().next().name();
        }
    }

    /**
     * What joining did to a golem: its network, whether networks were joined together on its account (which wants a
     * whole-network sort), and whether it was refused by a full network.
     */
    public record Joined(Net net, boolean merged, boolean refused) {
    }

    public enum LinkResult { LINKED, UNLINKED, OUT_OF_REACH, FULL, TAKEN }

    public static GolemNetworks get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(GolemNetworks::new, GolemNetworks::load), ID);
    }

    public Collection<Net> all() {
        return Collections.unmodifiableCollection(nets.values());
    }

    @Nullable
    public Net net(UUID id) {
        return nets.get(id);
    }

    /** The network {@code id} is now, following it through the networks it joined; null if it is gone. */
    @Nullable
    public Net resolve(UUID id) {
        for (int i = 0; i < 64 && id != null; i++) {
            Net net = nets.get(id);
            if (net != null) {
                return net;
            }
            id = aliases.get(id);
        }
        return null;
    }

    @Nullable
    public Net netOf(UUID golem) {
        UUID id = golemNet.get(golem);
        return id == null ? null : nets.get(id);
    }

    @Nullable
    public Member member(UUID golem) {
        Net net = netOf(golem);
        return net == null ? null : net.members.get(golem);
    }

    /** Whether {@code golem} was refused by a full network and rests until there is room. */
    public boolean refused(UUID golem) {
        Member member = member(golem);
        return member != null && member.refused();
    }

    /** The network whose golem sits on the block at {@code floor}, or null. */
    @Nullable
    public Net netAtFloor(BlockPos floor) {
        UUID id = floorIndex.get(floor.asLong());
        return id == null ? null : nets.get(id);
    }

    /** The network linked to the storage at {@code pos}, checking the other half of a double chest as well. */
    @Nullable
    public Net netAt(BlockPos pos, @Nullable BlockPos partner) {
        UUID id = linkNet.get(pos.asLong());
        if (id == null && partner != null) {
            id = linkNet.get(partner.asLong());
        }
        return id == null ? null : nets.get(id);
    }

    /**
     * Brings {@code golem}'s place in the networks up to date: where it sits, its name and its colour. A golem that is
     * new, or has moved or been dyed, has the networks of its colour worked out again.
     */
    public Joined join(UUID golem, BlockPos home, String name, DyeColor color) {
        Net net = netOf(golem);
        Member old = net == null ? null : net.members.get(golem);
        merged.clear();
        if (old == null || !old.home().equals(home) || old.color() != color) {
            Member member = new Member(home.immutable(), name, color, false);
            if (net != null && old.color() != color && net.members.size() > 1) {
                // a network is all one colour: a golem dyed another leaves it, and its links stay
                net.members.remove(golem);
                changed(net);
                net = null;
            }
            if (net == null) {
                net = new Net(UUID.randomUUID());
                nets.put(net.id, net);
                golemNet.put(golem, net.id);
            }
            net.members.put(golem, member);
            regroup(color, golem);
            if (old != null && old.color() != color) {
                regroup(old.color(), null);
            }
            indexFloors();
            setDirty();
        } else if (!old.name().equals(name)) {
            net.members.put(golem, new Member(old.home(), name, old.color(), old.refused()));
            changed(net);
        }
        Net now = netOf(golem);
        return new Joined(now, now != null && merged.contains(now.id), refused(golem));
    }

    /**
     * Takes a golem that died or was removed out of its network. A network left without golems is gone, links and
     * all; the rest of its colour are worked out again, which may split it or let a refused golem in.
     */
    public void leave(UUID golem) {
        UUID id = golemNet.remove(golem);
        Net net = id == null ? null : nets.get(id);
        if (net == null) {
            return;
        }
        Member member = net.members.remove(golem);
        if (net.members.isEmpty()) {
            for (BlockPos link : net.links) {
                linkNet.remove(link.asLong());
            }
            nets.remove(net.id);
            aliases.values().removeIf(target -> target.equals(net.id));
        }
        changed(net);
        if (member != null) {
            merged.clear();
            regroup(member.color(), null);
        }
        indexFloors();
        setDirty();
    }

    /** The networks joined together by the last change, for a whole-network sort each. */
    public Set<UUID> lastMerged() {
        return Set.copyOf(merged);
    }

    public void setMode(Net net, GolemMode mode) {
        net.mode = mode;
        changed(net);
    }

    /** Remembers which family each inventory (by key) was judged to be for, by family name. */
    public void setClaims(Net net, Map<Long, String> claims) {
        if (!net.claims.equals(claims)) {
            net.claims.clear();
            net.claims.putAll(claims);
            setDirty();
        }
    }

    /**
     * Links the storage at {@code pos} (both halves of a double chest) to {@code net}, or unlinks it if it was already.
     * {@code inventoryKey} tells which links are one inventory, for the link cap.
     */
    public LinkResult toggleLink(Net net, BlockPos pos, @Nullable BlockPos partner, ToLongFunction<BlockPos> inventoryKey) {
        Net owner = netAt(pos, partner);
        if (owner == net) {
            unlink(net, pos);
            if (partner != null) {
                unlink(net, partner);
            }
            changed(net);
            return LinkResult.UNLINKED;
        }
        return link(net, pos, partner, inventoryKey);
    }

    /** Links the storage at {@code pos} (and its other half) to {@code net} if it isn't linked to any network. */
    public LinkResult link(Net net, BlockPos pos, @Nullable BlockPos partner, ToLongFunction<BlockPos> inventoryKey) {
        Net owner = netAt(pos, partner);
        if (owner == net) {
            return LinkResult.LINKED;
        }
        if (owner != null) {
            return LinkResult.TAKEN;
        }
        if (!net.reaches(pos)) {
            return LinkResult.OUT_OF_REACH;
        }
        if (inventories(net, inventoryKey) >= net.maxLinks()) {
            return LinkResult.FULL;
        }
        link(net, pos);
        if (partner != null) {
            link(net, partner);
        }
        changed(net);
        return LinkResult.LINKED;
    }

    /** Unlinks the storage at {@code pos} (and its other half) from {@code net}; false if it wasn't linked to it. */
    public boolean unlink(Net net, BlockPos pos, @Nullable BlockPos partner) {
        if (netAt(pos, partner) != net) {
            return false;
        }
        unlink(net, pos);
        if (partner != null) {
            unlink(net, partner);
        }
        changed(net);
        return true;
    }

    /** Drops a link to storage that is gone (both halves, if it was a double chest and the other half is gone too). */
    public void prune(Net net, BlockPos pos) {
        if (net.links.contains(pos)) {
            unlink(net, pos);
            changed(net);
        }
    }

    private void link(Net net, BlockPos pos) {
        net.links.add(pos.immutable());
        linkNet.put(pos.asLong(), net.id);
    }

    private void unlink(Net net, BlockPos pos) {
        net.links.remove(pos);
        linkNet.remove(pos.asLong(), net.id);
    }

    /** How many inventories a network has linked: a double chest's two halves count once. */
    public static int inventories(Net net, ToLongFunction<BlockPos> inventoryKey) {
        Set<Long> seen = new HashSet<>();
        for (BlockPos link : net.links) {
            seen.add(inventoryKey.applyAsLong(link));
        }
        return seen.size();
    }

    /** Whether golems sitting at {@code a} and {@code b} reach each other, and so can be one network. */
    public static boolean withinLinkDistance(BlockPos a, BlockPos b) {
        return GolemStorage.inReach(a, b, AACommonConfig.GOLEM_RANGE.get());
    }

    /** Networks with a golem sitting within {@code distance} of {@code pos}. */
    public List<Net> near(BlockPos pos, double distance) {
        List<Net> near = new ArrayList<>();
        for (Net net : nets.values()) {
            for (Member member : net.members.values()) {
                if (member.home().distSqr(pos) <= distance * distance) {
                    near.add(net);
                    break;
                }
            }
        }
        return near;
    }

    private void changed(Net net) {
        net.version++;
        setDirty();
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Working out the networks of one colour

    /**
     * Works out again the networks of golems dyed {@code color}, after {@code cause} (if any) was built, moved or
     * dyed. Golems already working together stay together where they still reach each other; {@code cause} joins
     * every network it reaches if they fit in one together, else it is refused; then refused golems are let in where
     * there is now room. Links go with the network that reaches them, preferring the one they were linked to.
     */
    private void regroup(DyeColor color, @Nullable UUID cause) {
        int max = AACommonConfig.GOLEM_MAX_NETWORK.get();
        // every golem of this colour, in a stable order: network by network, then as they joined
        Map<UUID, Member> all = new LinkedHashMap<>();
        Map<UUID, UUID> oldNet = new HashMap<>();
        Set<Net> affected = new LinkedHashSet<>();
        for (Net net : nets.values()) {
            for (Map.Entry<UUID, Member> entry : net.members.entrySet()) {
                if (entry.getValue().color() == color) {
                    all.put(entry.getKey(), entry.getValue());
                    oldNet.put(entry.getKey(), net.id);
                    affected.add(net);
                }
            }
        }
        if (all.isEmpty()) {
            return;
        }
        List<UUID> settled = new ArrayList<>(), refused = new ArrayList<>();
        for (Map.Entry<UUID, Member> entry : all.entrySet()) {
            if (entry.getKey().equals(cause)) {
                continue;
            }
            (entry.getValue().refused() ? refused : settled).add(entry.getKey());
        }
        List<List<UUID>> groups = components(settled, all);
        // a network over the cap (the cap was lowered) keeps its first golems; the rest are refused
        for (List<UUID> group : groups) {
            while (group.size() > max) {
                refused.add(0, group.remove(group.size() - 1));
            }
        }
        Set<UUID> refusedNow = new HashSet<>();
        if (cause != null && all.containsKey(cause)) {
            if (!admit(cause, groups, all, max)) {
                refusedNow.add(cause);
            }
        }
        for (UUID golem : refused) {
            if (!admit(golem, groups, all, max)) {
                refusedNow.add(golem);
            }
        }
        for (UUID golem : refusedNow) {
            List<UUID> alone = new ArrayList<>();
            alone.add(golem);
            groups.add(alone);
        }
        apply(groups, all, oldNet, affected, refusedNow);
    }

    /** Lets {@code golem} into the groups it reaches, joining them, if they fit together; false if they don't. */
    private static boolean admit(UUID golem, List<List<UUID>> groups, Map<UUID, Member> all, int max) {
        BlockPos home = all.get(golem).home();
        List<List<UUID>> touching = new ArrayList<>();
        int size = 1;
        for (List<UUID> group : groups) {
            for (UUID other : group) {
                if (withinLinkDistance(home, all.get(other).home())) {
                    touching.add(group);
                    size += group.size();
                    break;
                }
            }
        }
        if (size > max) {
            return false;
        }
        List<UUID> joined = new ArrayList<>();
        for (List<UUID> group : touching) {
            joined.addAll(group);
            groups.remove(group);
        }
        joined.add(golem);
        groups.add(joined);
        return true;
    }

    /** The golems in {@code golems} that reach each other, directly or through others, as groups. */
    private static List<List<UUID>> components(List<UUID> golems, Map<UUID, Member> all) {
        List<List<UUID>> groups = new ArrayList<>();
        Set<UUID> placed = new HashSet<>();
        for (UUID start : golems) {
            if (!placed.add(start)) {
                continue;
            }
            List<UUID> group = new ArrayList<>();
            group.add(start);
            for (int i = 0; i < group.size(); i++) {
                BlockPos at = all.get(group.get(i)).home();
                for (UUID other : golems) {
                    if (!placed.contains(other) && withinLinkDistance(at, all.get(other).home())) {
                        placed.add(other);
                        group.add(other);
                    }
                }
            }
            // keep the order golems joined in, so the first golem (the network's name) stays first
            group.sort(Comparator.comparingInt(golems::indexOf));
            groups.add(group);
        }
        return groups;
    }

    /** Makes {@code groups} the networks of their colour, in place of {@code affected}, carrying links and claims over. */
    private void apply(List<List<UUID>> groups, Map<UUID, Member> all, Map<UUID, UUID> oldNet, Set<Net> affected,
                       Set<UUID> refusedNow) {
        Map<UUID, Net> old = new LinkedHashMap<>();
        for (Net net : affected) {
            old.put(net.id, net);
        }
        // the biggest groups pick ids first: each takes the old id most of its golems had, if no bigger group took it
        List<List<UUID>> order = new ArrayList<>(groups);
        order.sort(Comparator.<List<UUID>>comparingInt(g -> refusedNow.contains(g.get(0)) && g.size() == 1 ? 1 : 0)
                .thenComparingInt(g -> -g.size()));
        Set<UUID> taken = new HashSet<>();
        Map<List<UUID>, UUID> ids = new LinkedHashMap<>();
        Map<List<UUID>, Net> from = new HashMap<>();
        for (List<UUID> group : order) {
            Map<UUID, Integer> votes = new LinkedHashMap<>();
            for (UUID golem : group) {
                UUID was = oldNet.get(golem);
                if (was != null) {
                    votes.merge(was, 1, Integer::sum);
                }
            }
            UUID id = null;
            int best = 0;
            for (Map.Entry<UUID, Integer> vote : votes.entrySet()) {
                Net was = old.get(vote.getKey());
                if (!taken.contains(vote.getKey()) && (vote.getValue() > best || vote.getValue() == best && id != null
                        && was != null && old.get(id) != null && was.members.size() > old.get(id).members.size())) {
                    id = vote.getKey();
                    best = vote.getValue();
                }
            }
            UUID source = votes.isEmpty() ? null : votes.entrySet().stream()
                    .max(Comparator.comparingInt(Map.Entry::getValue)).get().getKey();
            if (id == null) {
                id = UUID.randomUUID();
            }
            taken.add(id);
            ids.put(group, id);
            Net mode = old.get(id) != null ? old.get(id) : source == null ? null : old.get(source);
            from.put(group, mode);
            // networks joining that both bring storage want a whole-network sort; a golem arriving alone doesn't
            int withLinks = 0;
            for (UUID was : votes.keySet()) {
                if (old.get(was) != null && !old.get(was).links.isEmpty()) {
                    withLinks++;
                }
            }
            if (withLinks > 1) {
                merged.add(id);
            }
        }

        // the new networks
        Map<UUID, Net> fresh = new LinkedHashMap<>();
        for (List<UUID> group : groups) {
            UUID id = ids.get(group);
            Net net = new Net(id);
            Net was = from.get(group);
            if (was != null) {
                net.mode = was.mode;
                net.version = was.version;
            }
            for (UUID golem : group) {
                net.members.put(golem, all.get(golem).with(refusedNow.contains(golem)));
            }
            fresh.put(id, net);
        }
        // where each old network went: its own id, else the network most of its golems went to
        Map<UUID, UUID> successor = new HashMap<>();
        for (Net net : old.values()) {
            if (fresh.containsKey(net.id)) {
                successor.put(net.id, net.id);
                continue;
            }
            Map<UUID, Integer> where = new HashMap<>();
            for (UUID golem : net.members.keySet()) {
                for (List<UUID> group : groups) {
                    if (group.contains(golem)) {
                        where.merge(ids.get(group), 1, Integer::sum);
                    }
                }
            }
            where.entrySet().stream().max(Map.Entry.comparingByValue()).ifPresent(e -> successor.put(net.id, e.getKey()));
        }
        // links, with their claims: to the network that reaches them, the one they were linked to first
        for (Net net : old.values()) {
            UUID next = successor.get(net.id);
            for (BlockPos link : net.links) {
                Net to = next == null ? null : fresh.get(next);
                if (to == null || !to.reaches(link)) {
                    for (Net candidate : fresh.values()) {
                        if (candidate.reaches(link) && !candidate.members.values().iterator().next().refused()) {
                            to = candidate;
                            break;
                        }
                    }
                }
                if (to == null) {
                    to = next == null ? null : fresh.get(next);
                }
                if (to == null) {
                    linkNet.remove(link.asLong(), net.id);
                    continue;
                }
                to.links.add(link);
                linkNet.put(link.asLong(), to.id);
            }
            for (Map.Entry<Long, String> claim : net.claims.entrySet()) {
                Net to = next == null ? null : fresh.get(next);
                if (to != null) {
                    to.claims.putIfAbsent(claim.getKey(), claim.getValue());
                }
            }
        }
        // swap them in, keeping the others' order
        Map<UUID, Net> rebuilt = new LinkedHashMap<>();
        for (Net net : nets.values()) {
            if (!old.containsKey(net.id)) {
                rebuilt.put(net.id, net);
            } else if (fresh.containsKey(net.id)) {
                rebuilt.put(net.id, fresh.get(net.id));
            }
        }
        for (Net net : fresh.values()) {
            rebuilt.putIfAbsent(net.id, net);
        }
        nets.clear();
        nets.putAll(rebuilt);
        for (Net net : fresh.values()) {
            for (UUID golem : net.members.keySet()) {
                golemNet.put(golem, net.id);
            }
            changed(net);
        }
        for (Net net : old.values()) {
            UUID next = successor.get(net.id);
            if (next != null && !next.equals(net.id)) {
                aliases.put(net.id, next);
            }
        }
        aliases.keySet().removeAll(fresh.keySet());
    }

    private void indexFloors() {
        floorIndex.clear();
        for (Net net : nets.values()) {
            for (Member member : net.members.values()) {
                floorIndex.put(member.home().below().asLong(), net.id);
            }
        }
    }

    // ---------------------------------------------------------------------------------------------------------------

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Net net : nets.values()) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("id", net.id);
            entry.putString("mode", net.mode.name());
            ListTag members = new ListTag();
            for (Map.Entry<UUID, Member> member : net.members.entrySet()) {
                CompoundTag m = new CompoundTag();
                m.putUUID("golem", member.getKey());
                m.put("home", NbtUtils.writeBlockPos(member.getValue().home()));
                m.putString("name", member.getValue().name());
                m.putString("color", member.getValue().color().getSerializedName());
                m.putBoolean("refused", member.getValue().refused());
                members.add(m);
            }
            entry.put("members", members);
            entry.put("links", new LongArrayTag(net.links.stream().mapToLong(BlockPos::asLong).toArray()));
            CompoundTag claims = new CompoundTag();
            net.claims.forEach((key, group) -> claims.putString(Long.toString(key), group));
            entry.put("claims", claims);
            list.add(entry);
        }
        tag.put("networks", list);
        ListTag aliasList = new ListTag();
        aliases.forEach((from, to) -> {
            CompoundTag alias = new CompoundTag();
            alias.putUUID("from", from);
            alias.putUUID("to", to);
            aliasList.add(alias);
        });
        tag.put("aliases", aliasList);
        return tag;
    }

    private static GolemNetworks load(CompoundTag tag, HolderLookup.Provider registries) {
        GolemNetworks data = new GolemNetworks();
        for (Tag t : tag.getList("networks", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) t;
            Net net = new Net(entry.getUUID("id"));
            try {
                net.mode = GolemMode.valueOf(entry.getString("mode"));
            } catch (IllegalArgumentException e) {
                net.mode = GolemMode.TIDY;
            }
            for (Tag mt : entry.getList("members", Tag.TAG_COMPOUND)) {
                CompoundTag m = (CompoundTag) mt;
                UUID golem = m.getUUID("golem");
                BlockPos home = NbtUtils.readBlockPos(m, "home").orElse(BlockPos.ZERO);
                DyeColor color = DyeColor.byName(m.getString("color"), GravititeGolem.DEFAULT_COLOR);
                net.members.put(golem, new Member(home, m.getString("name"), color, m.getBoolean("refused")));
                data.golemNet.put(golem, net.id);
            }
            for (long link : entry.getLongArray("links")) {
                BlockPos pos = BlockPos.of(link);
                net.links.add(pos);
                data.linkNet.put(link, net.id);
            }
            CompoundTag claims = entry.getCompound("claims");
            for (String key : claims.getAllKeys()) {
                try {
                    net.claims.put(Long.parseLong(key), claims.getString(key));
                } catch (NumberFormatException ignored) {
                    // not one of ours
                }
            }
            if (!net.members.isEmpty()) {
                data.nets.put(net.id, net);
            }
        }
        for (Tag t : tag.getList("aliases", Tag.TAG_COMPOUND)) {
            CompoundTag alias = (CompoundTag) t;
            data.aliases.put(alias.getUUID("from"), alias.getUUID("to"));
        }
        // networks follow from where golems sit and their colours: work them out again, in case the rules changed
        // (networks saved before colours, or a new reach or cap)
        Set<DyeColor> colors = new LinkedHashSet<>();
        for (Net net : data.nets.values()) {
            for (Member member : net.members.values()) {
                colors.add(member.color());
            }
        }
        for (DyeColor color : colors) {
            data.regroup(color, null);
        }
        data.merged.clear();
        data.indexFloors();
        return data;
    }
}
