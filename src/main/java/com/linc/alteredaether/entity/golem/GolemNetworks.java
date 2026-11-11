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
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
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
 * Every golem is in a network, alone if it is linked to no other.
 */
public final class GolemNetworks extends SavedData {
    private static final String ID = "altered_aether_golem_networks";

    private final Map<UUID, Net> nets = new LinkedHashMap<>();
    private final Map<UUID, UUID> golemNet = new HashMap<>();
    private final Map<Long, UUID> linkNet = new HashMap<>();

    /** One golem in a network: where it sits and what it is called, for messages when it isn't loaded. */
    public record Member(BlockPos home, String name) {
    }

    /** A network: its golems, the mode they sort in, and its linked storage, in the order it was linked. */
    public static final class Net {
        public final UUID id;
        private GolemMode mode = GolemMode.TIDY;
        private final Map<UUID, Member> members = new LinkedHashMap<>();
        private final Set<BlockPos> links = new LinkedHashSet<>();
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

        public int version() {
            return version;
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

    public enum LinkResult { LINKED, UNLINKED, OUT_OF_REACH, FULL, TAKEN }

    public enum GolemLinkResult { LINKED, UNLINKED, TOO_FAR, TOO_MANY }

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

    @Nullable
    public Net netOf(UUID golem) {
        UUID id = golemNet.get(golem);
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

    /** {@code golem}'s network, made for it alone if it has none; its home and name are brought up to date. */
    public Net join(UUID golem, BlockPos home, String name) {
        Net net = netOf(golem);
        if (net == null) {
            net = new Net(UUID.randomUUID());
            nets.put(net.id, net);
            golemNet.put(golem, net.id);
        }
        Member old = net.members.get(golem);
        if (old == null || !old.home().equals(home) || !old.name().equals(name)) {
            net.members.put(golem, new Member(home.immutable(), name));
            changed(net);
        }
        return net;
    }

    /**
     * Takes a golem that died or was removed out of its network. A network left without golems loses its links; one
     * with golems left keeps them (out of reach ones go dormant).
     */
    public void leave(UUID golem) {
        UUID id = golemNet.remove(golem);
        Net net = id == null ? null : nets.get(id);
        if (net == null) {
            return;
        }
        net.members.remove(golem);
        if (net.members.isEmpty()) {
            for (BlockPos link : net.links) {
                linkNet.remove(link.asLong());
            }
            nets.remove(net.id);
        }
        changed(net);
    }

    public void setMode(Net net, GolemMode mode) {
        net.mode = mode;
        changed(net);
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
        Set<Long> seen = new java.util.HashSet<>();
        for (BlockPos link : net.links) {
            seen.add(inventoryKey.applyAsLong(link));
        }
        return seen.size();
    }

    /** Whether {@code net} can be linked with {@code other}, whose golem sits at {@code otherHome}. */
    public GolemLinkResult canMerge(Net net, Net other, BlockPos home, BlockPos otherHome) {
        int range = AACommonConfig.GOLEM_RANGE.get();
        double dx = home.getX() - otherHome.getX(), dz = home.getZ() - otherHome.getZ();
        if (dx * dx + dz * dz > 4.0 * range * range || Math.abs(home.getY() - otherHome.getY()) > range) {
            return GolemLinkResult.TOO_FAR;
        }
        if (net.members.size() + other.members.size() > AACommonConfig.GOLEM_MAX_NETWORK.get()) {
            return GolemLinkResult.TOO_MANY;
        }
        return GolemLinkResult.LINKED;
    }

    /** Folds {@code other} into {@code net}: its golems and its links. {@code net}'s mode wins. */
    public void merge(Net net, Net other) {
        if (net == other) {
            return;
        }
        for (Map.Entry<UUID, Member> member : other.members.entrySet()) {
            net.members.put(member.getKey(), member.getValue());
            golemNet.put(member.getKey(), net.id);
        }
        for (BlockPos link : other.links) {
            link(net, link);
        }
        nets.remove(other.id);
        changed(net);
    }

    /**
     * Takes {@code golem} out of its network into one of its own, with the links only it reaches. The network it
     * leaves keeps the rest.
     */
    public void split(UUID golem) {
        Net net = netOf(golem);
        if (net == null || net.members.size() < 2) {
            return;
        }
        Member member = net.members.remove(golem);
        Net alone = new Net(UUID.randomUUID());
        alone.mode = net.mode;
        alone.members.put(golem, member);
        nets.put(alone.id, alone);
        golemNet.put(golem, alone.id);
        int range = AACommonConfig.GOLEM_RANGE.get();
        for (BlockPos link : new ArrayList<>(net.links)) {
            if (GolemStorage.inReach(member.home(), link, range) && !net.reaches(link)) {
                unlink(net, link);
                link(alone, link);
            }
        }
        changed(net);
        changed(alone);
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
                members.add(m);
            }
            entry.put("members", members);
            entry.put("links", new LongArrayTag(net.links.stream().mapToLong(BlockPos::asLong).toArray()));
            list.add(entry);
        }
        tag.put("networks", list);
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
                net.members.put(golem, new Member(home, m.getString("name")));
                data.golemNet.put(golem, net.id);
            }
            for (long link : entry.getLongArray("links")) {
                BlockPos pos = BlockPos.of(link);
                net.links.add(pos);
                data.linkNet.put(link, net.id);
            }
            if (!net.members.isEmpty()) {
                data.nets.put(net.id, net);
            }
        }
        return data;
    }
}
