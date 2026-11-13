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
package com.linc.alteredaether.item;

import com.linc.alteredaether.AlteredAether;
import com.linc.alteredaether.compat.CreateCompat;
import com.linc.alteredaether.config.AACommonConfig;
import com.linc.alteredaether.entity.golem.GolemAccess;
import com.linc.alteredaether.entity.golem.GolemNetworks;
import com.linc.alteredaether.entity.golem.GolemPayloads;
import com.linc.alteredaether.entity.golem.GolemSorter;
import com.linc.alteredaether.entity.golem.GolemStorage;
import com.linc.alteredaether.entity.golem.GravititeGolem;
import com.linc.alteredaether.entity.golem.PebbleInfo;
import com.linc.alteredaether.entity.golem.PebbleTracker;
import com.linc.alteredaether.entity.golem.ThrownPebble;
import com.linc.alteredaether.registry.AARegistries;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A storage pebble: a chip of a golem's gravitite that answers to its network. Used on storage it links that storage
 * to the network (the chest doesn't open), or unlinks it; used crouching on the top of a block it sends its golem
 * there; used on one of the network's golems it links everything around it, or crouching unlinks it all (see
 * GolemInteractions). None of that uses it up. Used on nothing it is thrown, and shatters. It glows within reach of
 * its network, and goes dull for good once the network is gone.
 */
public class StoragePebbleItem extends Item {
    private static final String TIP = "item." + AlteredAether.MOD_ID + ".storage_pebble.";

    public StoragePebbleItem(Properties properties) {
        super(properties);
    }

    private static Component message(String key, Object... args) {
        return Component.translatable("message." + AlteredAether.MOD_ID + ".golem." + key, args);
    }

    /**
     * The network {@code link} answers to now: its origin golem's while that golem lives, else the network it last
     * belonged to (or the one that network joined); null once that is gone.
     */
    @Nullable
    public static GolemNetworks.Net network(ServerLevel level, PebbleLink link) {
        ServerLevel home = level.getServer().getLevel(link.dimension());
        if (home == null || link.dead()) {
            return null;
        }
        GolemNetworks networks = GolemNetworks.get(home);
        GolemNetworks.Net net = networks.netOf(link.origin());
        return net != null ? net : link.hasNetwork() ? networks.resolve(link.network()) : null;
    }

    /** Whether {@code link}'s pebble is active at {@code at}: its network lives here and one of its golems reaches. */
    public static boolean active(ServerLevel level, @Nullable PebbleLink link, BlockPos at) {
        if (link == null || link.dead() || !link.dimension().equals(level.dimension())) {
            return false;
        }
        GolemNetworks.Net net = network(level, link);
        return net != null && net.reaches(at);
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        PebbleLink link = stack.get(AARegistries.PEBBLE_LINK.get());
        Level level = context.getLevel();
        if (link == null) {
            return InteractionResult.PASS;
        }
        // a live pebble always takes the click, so storage never opens under it
        if (level.isClientSide || !(context.getPlayer() instanceof ServerPlayer player)
                || !(level instanceof ServerLevel server)) {
            return link.dead() ? InteractionResult.PASS : InteractionResult.SUCCESS;
        }
        BlockPos pos = context.getClickedPos();
        if (!server.mayInteract(player, pos)) {
            return InteractionResult.FAIL;
        }
        GolemNetworks.Net net = link.dead() ? null : network(server, link);
        if (net == null) {
            if (!link.dead()) {
                stack.set(AARegistries.PEBBLE_LINK.get(), link.asDead());
            }
            player.displayClientMessage(message("pebble_dead"), true);
            return InteractionResult.PASS;
        }
        if (!link.dimension().equals(server.dimension())) {
            player.displayClientMessage(message("cant_hear", net.name()), true);
            return InteractionResult.SUCCESS;
        }
        // crouching on the top of a block sends the golem there; on storage it links as usual
        if (player.isShiftKeyDown() && context.getClickedFace() == Direction.UP && GolemStorage.handler(server, pos) == null) {
            sendGolem(link, net, player, server, pos.above());
            return InteractionResult.SUCCESS;
        }
        toggleLink(link, net, player, server, pos);
        return InteractionResult.SUCCESS;
    }

    /** Sends the pebble's golem (or, if it is elsewhere, the network's golem nearest the spot) to {@code target}. */
    private static void sendGolem(PebbleLink link, GolemNetworks.Net net, ServerPlayer player, ServerLevel level, BlockPos target) {
        GravititeGolem golem = null;
        if (net.members().containsKey(link.origin()) && level.getEntity(link.origin()) instanceof GravititeGolem origin) {
            golem = origin;
        } else {
            double best = Double.MAX_VALUE;
            for (UUID id : net.members().keySet()) {
                if (level.getEntity(id) instanceof GravititeGolem member && member.blockPosition().distSqr(target) < best) {
                    best = member.blockPosition().distSqr(target);
                    golem = member;
                }
            }
        }
        if (golem == null) {
            player.displayClientMessage(message("cant_hear", net.name()), true);
            return;
        }
        int max = AACommonConfig.GOLEM_MAX_MOVE.get();
        if (golem.blockPosition().distSqr(target) > (double) max * max) {
            player.displayClientMessage(message("too_far_to_send", golem.getName(), max), true);
            return;
        }
        if (!golem.canStand(target)) {
            player.displayClientMessage(message("cant_sit", golem.getName()), true);
            return;
        }
        golem.sendTo(target, player);
        level.playSound(null, target, AARegistries.GOLEM_LINK.get(), SoundSource.NEUTRAL, 0.6F, 0.8F);
        PacketDistributor.sendToPlayer(player, GolemPayloads.Glow.blocks(GolemPayloads.MARK, 60, List.of(target.below())));
        player.displayClientMessage(message("on_its_way", golem.getName()), true);
    }

    private static void toggleLink(PebbleLink link, GolemNetworks.Net net, ServerPlayer player, ServerLevel level, BlockPos pos) {
        GolemNetworks networks = GolemNetworks.get(level);
        if (GolemStorage.handler(level, pos) == null) {
            return;
        }
        GolemStorage.Role role = GolemStorage.role(level, pos);
        BlockPos partner = GolemStorage.partner(level, pos);
        if (role == GolemStorage.Role.NONE && networks.netAt(pos, partner) != net) {
            player.displayClientMessage(message("not_storage", level.getBlockState(pos).getBlock().getName()), true);
            return;
        }
        List<BlockPos> both = new ArrayList<>(List.of(pos));
        if (partner != null) {
            both.add(partner);
        }
        GolemNetworks.Net owner = networks.netAt(pos, partner);
        switch (networks.toggleLink(net, pos, partner, p -> GolemStorage.inventoryKey(level, p))) {
            case LINKED -> {
                level.playSound(null, pos, AARegistries.GOLEM_LINK.get(), SoundSource.BLOCKS, 0.8F, 1.0F);
                boolean blocked = GolemAccess.encased(level, pos);
                PacketDistributor.sendToPlayer(player, blocked
                        ? new GolemPayloads.Glow(GolemPayloads.LINK, -1, List.of(), both, List.of(), List.of(), List.of())
                        : GolemPayloads.Glow.blocks(GolemPayloads.LINK, -1, both, GolemStorage.strict(level, both)));
                int linked = GolemNetworks.inventories(net, p -> GolemStorage.inventoryKey(level, p));
                String key = blocked ? "linked_blocked" : role == GolemStorage.Role.SOURCE ? "linked_source" : "linked";
                player.displayClientMessage(message(key, net.name(), linked, net.maxLinks()), true);
                GolemSorter.linked(level, net, both);
            }
            case UNLINKED -> {
                level.playSound(null, pos, AARegistries.GOLEM_UNLINK.get(), SoundSource.BLOCKS, 0.8F, 1.0F);
                PacketDistributor.sendToPlayer(player, GolemPayloads.Glow.blocks(GolemPayloads.UNLINK, -1, both));
                player.displayClientMessage(message("unlinked", net.name()), true);
                GolemSorter.unlinked(level, net, both);
            }
            case OUT_OF_REACH -> player.displayClientMessage(message("out_of_reach", net.name()), true);
            case FULL -> player.displayClientMessage(message("full", net.name(), net.maxLinks()), true);
            case TAKEN -> player.displayClientMessage(message("taken", owner == null ? "" : owner.name()), true);
        }
        PebbleTracker.send(player, net);
    }

    /** Used on nothing, the pebble is thrown like a snowball (see ThrownPebble), and that is the end of it. */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SNOWBALL_THROW, SoundSource.NEUTRAL,
                0.5F, 0.4F / (level.getRandom().nextFloat() * 0.4F + 0.8F));
        if (level instanceof ServerLevel server) {
            ThrownPebble pebble = new ThrownPebble(level, player);
            pebble.setItem(stack);
            pebble.setInert(!active(server, stack.get(AARegistries.PEBBLE_LINK.get()), player.blockPosition()));
            pebble.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, 1.5F, 1.0F);
            level.addFreshEntity(pebble);
        }
        player.awardStat(Stats.ITEM_USED.get(this));
        stack.consume(1, player);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    /**
     * Keeps the pebble with its network: it follows its origin golem from network to network while that golem lives,
     * and turns dull for good once its network is gone.
     */
    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (level.isClientSide || entity.tickCount % 10 != 0 || !(level instanceof ServerLevel server)) {
            return;
        }
        PebbleLink link = stack.get(AARegistries.PEBBLE_LINK.get());
        if (link == null || link.dead()) {
            return;
        }
        GolemNetworks.Net net = network(server, link);
        if (net == null) {
            stack.set(AARegistries.PEBBLE_LINK.get(), link.asDead());
            return;
        }
        if (!net.id.equals(link.network()) || !net.name().equals(link.name()) || net.color() != link.color()) {
            stack.set(AARegistries.PEBBLE_LINK.get(), link.in(net.id, net.name(), net.color()));
        }
    }

    /** Changing network or going dull doesn't make the pebble bob in the hand. */
    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        if (slotChanged || !newStack.is(oldStack.getItem())) {
            return true;
        }
        PebbleLink before = oldStack.get(AARegistries.PEBBLE_LINK.get()), after = newStack.get(AARegistries.PEBBLE_LINK.get());
        return before == null || after == null || !before.origin().equals(after.origin());
    }

    /** A pebble within reach of its network shimmers; a dull, dark or unlinked one doesn't. */
    @Override
    public boolean isFoil(ItemStack stack) {
        return PebbleInfo.bright(stack.get(AARegistries.PEBBLE_LINK.get()), null);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        PebbleLink link = stack.get(AARegistries.PEBBLE_LINK.get());
        if (link == null) {
            tooltip.add(Component.translatable(TIP + "unlinked").withStyle(ChatFormatting.GRAY));
        } else if (link.dead()) {
            tooltip.add(Component.translatable(TIP + "dead", link.name()).withStyle(ChatFormatting.DARK_GRAY));
        } else {
            PebbleInfo.Info info = PebbleInfo.get(link.network());
            tooltip.add(Component.translatable(TIP + "belongs", info != null ? info.name() : link.name())
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
            if (info != null) {
                tooltip.add(Component.translatable(TIP + "mode", info.modeTitle()).withStyle(ChatFormatting.GRAY));
                tooltip.add(Component.translatable(TIP + "links", info.linked(), info.maxLinks()).withStyle(ChatFormatting.GRAY));
            }
        }
        if (!PebbleInfo.altDown.getAsBoolean()) {
            tooltip.add(Component.translatable(TIP + "guide.hint").withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        guide(tooltip);
    }

    /**
     * Everything a gravitite golem answers to, for the pebble's tooltip while Alt is held: one line each, a heading
     * then what it does, so it fits on screen at large GUI scales.
     */
    private static void guide(List<Component> tooltip) {
        int cap = PebbleInfo.maxGolems > 0 ? PebbleInfo.maxGolems : AACommonConfig.GOLEM_MAX_NETWORK.get();
        entry(tooltip, "build");
        entry(tooltip, "hand");
        entry(tooltip, "pebble.storage");
        entry(tooltip, "pebble.golem");
        entry(tooltip, "pebble.send");
        entry(tooltip, "search");
        entry(tooltip, "dye", cap);
        entry(tooltip, "frames");
        entry(tooltip, ModList.get().isLoaded(CreateCompat.MOD) ? "frames.strict_create" : "frames.strict");
        entry(tooltip, "redstone");
        entry(tooltip, "comparator");
        entry(tooltip, "source");
    }

    private static void entry(List<Component> tooltip, String key, Object... args) {
        tooltip.add(Component.translatable(TIP + "guide." + key + ".title").withStyle(ChatFormatting.LIGHT_PURPLE)
                .append(Component.translatable(TIP + "guide." + key, args).withStyle(ChatFormatting.GRAY)));
    }
}
