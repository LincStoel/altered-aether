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
import com.linc.alteredaether.registry.AARegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The Golem Chest Network screen's menu: no slots at all, since nothing is put in or taken out through it. On the
 * server it follows what the golem's network holds and sends it to the player whenever it changes (see
 * {@link GolemNetworkView}); it closes when the player walks away from the golem, or the golem goes.
 */
public final class GolemNetworkMenu extends AbstractContainerMenu {
    public static final Component TITLE = Component.translatable("container." + AlteredAether.MOD_ID + ".golem_network");
    /** How far from the golem the screen stays open, as a chest's does. */
    private static final double REACH = 8.0;
    /** Ticks between sends while the storage keeps changing. */
    private static final int SEND_INTERVAL = 10;
    /** Ticks between sends when the network isn't running, so changes can't be told. */
    private static final int SEND_UNTRACKED = 40;

    private final int golemId;
    private DyeColor color;
    // server
    @Nullable
    private final GravititeGolem golem;
    @Nullable
    private final ServerPlayer player;
    @Nullable
    private UUID sentNet;
    private int sentVersion;
    private long sentTick;
    // client
    private GolemNetworkView.Contents contents = GolemNetworkView.Contents.EMPTY;
    private int received;
    private List<GolemSelections.Pick> picks = List.of();

    private GolemNetworkMenu(int id, int golemId, DyeColor color, @Nullable GravititeGolem golem,
                             @Nullable ServerPlayer player) {
        super(AARegistries.GOLEM_NETWORK_MENU.get(), id);
        this.golemId = golemId;
        this.color = color;
        this.golem = golem;
        this.player = player;
    }

    /** The client's menu, from what {@link #open} wrote. */
    public static GolemNetworkMenu fromNetwork(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        return new GolemNetworkMenu(id, buf.readVarInt(), DyeColor.byId(buf.readByte()), null, null);
    }

    /** Opens the screen on {@code golem}'s network for {@code player}; a golem with nothing linked shakes its head. */
    public static void open(GravititeGolem golem, ServerPlayer player, ServerLevel level) {
        GolemNetworks.Net net = GolemNetworks.get(level).netOf(golem.getUUID());
        if (net == null || net.links().isEmpty()) {
            golem.shakeHead();
            return;
        }
        DyeColor color = net.color();
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new GolemNetworkMenu(id, golem.getId(), color,
                golem, player), TITLE), buf -> {
            buf.writeVarInt(golem.getId());
            buf.writeByte(color.getId());
        });
        if (player.containerMenu instanceof GolemNetworkMenu menu && menu.golem == golem) {
            GolemSelections.opened(player, net, level);
            menu.sendPicks(net);
        }
        golem.event(GolemTiming.EVENT_LINK_PULSE);
    }

    /** The golem's entity id. */
    public int golemId() {
        return golemId;
    }

    /** The network's colour. */
    public DyeColor color() {
        return color;
    }

    @Nullable
    GravititeGolem golem() {
        return golem;
    }

    /** Client: what the network holds, as last sent. */
    public GolemNetworkView.Contents contents() {
        return contents;
    }

    /** Client: how many times contents have arrived, so the screen knows to list them again. */
    public int received() {
        return received;
    }

    /** Client: the contents sent for this menu. */
    public void receive(GolemPayloads.NetworkContents payload) {
        contents = payload.contents();
        received++;
    }

    /** Client: the items picked out, in order, with their colours. */
    public List<GolemSelections.Pick> picks() {
        return picks;
    }

    /** Client: the picks as the server has them. */
    public void receive(GolemPayloads.Picks payload) {
        List<GolemSelections.Pick> list = new ArrayList<>();
        for (int i = 0; i < payload.items().size() && i < payload.colors().size(); i++) {
            list.add(new GolemSelections.Pick(payload.items().get(i), payload.colors().get(i), 0));
        }
        picks = List.copyOf(list);
    }

    /**
     * Client: picks {@code item}, or drops it if picked, before the server answers (which it does with the picks as
     * they are). Returns false if every colour is taken.
     */
    public boolean togglePick(ItemStack item) {
        List<GolemSelections.Pick> list = new ArrayList<>(picks);
        if (list.removeIf(pick -> ItemStack.isSameItemSameComponents(pick.item(), item))) {
            picks = List.copyOf(list);
        } else if (list.size() >= GolemSelections.MAX) {
            return false;
        } else {
            Set<DyeColor> taken = EnumSet.noneOf(DyeColor.class);
            list.forEach(pick -> taken.add(pick.color()));
            list.add(new GolemSelections.Pick(item.copyWithCount(1), GolemSelections.firstFree(color, taken), 0));
            picks = List.copyOf(list);
        }
        PacketDistributor.sendToServer(new GolemPayloads.Pick(containerId, false, List.of(item.copyWithCount(1))));
        return true;
    }

    /** Client: makes {@code items} the picks (none: clears them). */
    public void replacePicks(List<ItemStack> items) {
        List<ItemStack> sent = new ArrayList<>();
        for (ItemStack item : items) {
            if (sent.size() < GolemSelections.MAX) {
                sent.add(item.copyWithCount(1));
            }
        }
        PacketDistributor.sendToServer(new GolemPayloads.Pick(containerId, true, sent));
    }

    /** Server: what the screen asked to pick. */
    void pick(boolean replace, List<ItemStack> items) {
        if (player == null || golem == null || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        GolemNetworks.Net net = GolemNetworks.get(level).netOf(golem.getUUID());
        if (net == null) {
            return;
        }
        if (replace) {
            GolemSelections.replace(player, net, level, items);
        } else if (items.size() == 1) {
            GolemSelections.toggle(player, net, level, items.getFirst());
        }
        sendPicks(net);
    }

    private void sendPicks(GolemNetworks.Net net) {
        if (player == null) {
            return;
        }
        List<ItemStack> items = new ArrayList<>();
        List<DyeColor> colors = new ArrayList<>();
        for (GolemSelections.Pick pick : GolemSelections.picks(player, net.id)) {
            items.add(pick.item());
            colors.add(pick.color());
        }
        PacketDistributor.sendToPlayer(player, new GolemPayloads.Picks(containerId, items, colors));
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (player != null && golem != null && player.level() instanceof ServerLevel level) {
            send(level, false);
        }
    }

    /** Sends what the network holds if it changed since the last send (or {@code force}), at most every few ticks. */
    void send(ServerLevel level, boolean force) {
        if (player == null || golem == null) {
            return;
        }
        GolemNetworks.Net net = GolemNetworks.get(level).netOf(golem.getUUID());
        if (net == null) {
            return;
        }
        long now = level.getGameTime();
        int version = GolemSorter.contentsVersion(level, net.id);
        boolean first = sentNet == null;
        boolean changed = !net.id.equals(sentNet) || version != sentVersion || version < 0;
        int interval = version < 0 ? SEND_UNTRACKED : SEND_INTERVAL;
        if (!force && !first && (!changed || now - sentTick < interval)) {
            return;
        }
        sentNet = net.id;
        sentVersion = version;
        sentTick = now;
        color = net.color();
        BlockPos from = golem.home() != null ? golem.home() : golem.blockPosition();
        PacketDistributor.sendToPlayer(player, new GolemPayloads.NetworkContents(containerId,
                GolemNetworkView.build(level, net, from)));
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        if (golem == null) {
            return true;
        }
        return golem.isAlive() && !golem.isRemoved() && player.distanceToSqr(golem) <= REACH * REACH;
    }
}
