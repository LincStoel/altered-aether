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
package com.linc.alteredaether.compat.jade;

import com.aetherteam.aether.block.AetherBlocks;
import com.aetherteam.aether.block.miscellaneous.FloatingBlock;
import com.linc.alteredaether.AlteredAether;
import com.linc.alteredaether.compat.GravititeRework;
import com.linc.alteredaether.entity.golem.GolemMode;
import net.neoforged.fml.ModList;
import com.linc.alteredaether.entity.golem.GolemNetworks;
import com.linc.alteredaether.entity.golem.GolemSorter;
import com.linc.alteredaether.entity.golem.GolemStorage;
import com.linc.alteredaether.entity.golem.GravititeGolem;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IEntityComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.config.IPluginConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * Jade lines for gravitite golems: a golem's mode, links, the golems linked to it and what it is doing (naming what holds it up), and
 * on linked storage which golem it belongs to and what is pinned to it. Loaded only by Jade.
 */
@WailaPlugin(AlteredAether.MOD_ID)
public final class AAJadePlugin implements IWailaPlugin {
    private static final String KEY = "jade." + AlteredAether.MOD_ID + ".golem.";

    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerEntityDataProvider(Golem.INSTANCE, GravititeGolem.class);
        registration.registerBlockDataProvider(Storage.INSTANCE, BlockEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerEntityComponent(Golem.INSTANCE, GravititeGolem.class);
        registration.registerBlockComponent(Storage.INSTANCE, Block.class);
        registration.registerBlockComponent(GravititeLift.INSTANCE, FloatingBlock.class);
    }

    /**
     * With Sable, whether an enchanted gravitite block lifts: it does only while it has a redstone signal (see
     * compat/SableLift). Signals are block states, so the client can tell on its own.
     */
    enum GravititeLift implements IBlockComponentProvider {
        INSTANCE;

        private static final ResourceLocation UID = AlteredAether.id("gravitite_lift");

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            if (!accessor.getBlockState().is(AetherBlocks.ENCHANTED_GRAVITITE.get()) || !ModList.get().isLoaded("sable")
                    || !GravititeRework.active()) {
                return;
            }
            boolean powered = accessor.getLevel().hasNeighborSignal(accessor.getPosition());
            tooltip.add(Component.translatable("jade." + AlteredAether.MOD_ID + ".gravitite." + (powered ? "lifting" : "inert")));
        }

        @Override
        public ResourceLocation getUid() {
            return UID;
        }
    }

    private static Component line(String key, Object... args) {
        return Component.translatable(KEY + key, args);
    }

    enum Golem implements IEntityComponentProvider, IServerDataProvider<EntityAccessor> {
        INSTANCE;

        private static final ResourceLocation UID = AlteredAether.id("gravitite_golem");

        @Override
        public void appendServerData(CompoundTag data, EntityAccessor accessor) {
            if (!(accessor.getEntity() instanceof GravititeGolem golem) || !(golem.level() instanceof ServerLevel level)) {
                return;
            }
            GolemNetworks.Net net = GolemNetworks.get(level).netOf(golem.getUUID());
            if (net == null) {
                return;
            }
            CompoundTag tag = new CompoundTag();
            tag.putInt("mode", net.mode().ordinal());
            tag.putInt("linked", GolemNetworks.inventories(net, pos -> GolemStorage.inventoryKey(level, pos)));
            tag.putInt("max", net.maxLinks());
            List<String> others = new ArrayList<>();
            net.members().forEach((id, member) -> {
                if (!id.equals(golem.getUUID())) {
                    others.add(member.name());
                }
            });
            tag.putString("others", String.join(", ", others));
            GolemSorter.Report report = GolemSorter.report(level, net);
            tag.putString("status", golem.isRefused() ? "refused" : golem.isPaused() ? "paused" : !golem.isSeated() ? "moving"
                    : report.status().name().toLowerCase());
            tag.putInt("cap", com.linc.alteredaether.config.AACommonConfig.GOLEM_MAX_NETWORK.get());
            tag.putInt("blocked", report.blocked());
            tag.putInt("moves", report.movesLeft());
            tag.putInt("unplaced", report.unplaced());
            tag.putInt("away", report.outOfReach());
            tag.putInt("inUse", report.inUse());
            data.put("golem", tag);
        }

        @Override
        public void appendTooltip(ITooltip tooltip, EntityAccessor accessor, IPluginConfig config) {
            CompoundTag tag = accessor.getServerData().getCompound("golem");
            if (tag.isEmpty()) {
                return;
            }
            tooltip.add(line("mode", GolemMode.byId(tag.getInt("mode")).title()));
            tooltip.add(line("links", tag.getInt("linked"), tag.getInt("max")));
            if (!tag.getString("others").isEmpty()) {
                tooltip.add(line("linked_golems", tag.getString("others")));
            }
            String status = tag.getString("status");
            tooltip.add(switch (status) {
                case "sorting" -> line("sorting", tag.getInt("moves"));
                case "no_space" -> line("no_space", tag.getInt("unplaced"));
                case "refused" -> line("refused", tag.getInt("cap"));
                case "sorted", "paused", "moving" -> line(status);
                default -> line("idle");
            });
            if (tag.getInt("inUse") > 0) {
                tooltip.add(line("in_use", tag.getInt("inUse")));
            }
            if (tag.getInt("away") > 0) {
                tooltip.add(line("out_of_reach", tag.getInt("away")));
            }
            if (tag.getInt("blocked") > 0) {
                tooltip.add(line("blocked", tag.getInt("blocked")));
            }
        }

        @Override
        public ResourceLocation getUid() {
            return UID;
        }
    }

    enum Storage implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
        INSTANCE;

        private static final ResourceLocation UID = AlteredAether.id("golem_storage");

        @Override
        public boolean shouldRequestData(BlockAccessor accessor) {
            return accessor.getBlockEntity() != null;
        }

        @Override
        public void appendServerData(CompoundTag data, BlockAccessor accessor) {
            if (!(accessor.getLevel() instanceof ServerLevel level)) {
                return;
            }
            BlockPos pos = accessor.getPosition();
            GolemNetworks.Net net = GolemNetworks.get(level).netAt(pos, GolemStorage.partner(level, pos));
            if (net == null) {
                return;
            }
            CompoundTag tag = new CompoundTag();
            tag.putString("name", net.name());
            tag.putBoolean("source", GolemStorage.role(level, pos) == GolemStorage.Role.SOURCE);
            tag.putBoolean("encased", com.linc.alteredaether.entity.golem.GolemAccess.encased(level, pos));
            GolemStorage.Pins pins = GolemStorage.pins(level, pos);
            if (!pins.isEmpty()) {
                tag.putString("pin", pins.items().get(0).getHoverName().getString());
                tag.putBoolean("strict", pins.strict());
            }
            data.put("golem_link", tag);
        }

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            CompoundTag tag = accessor.getServerData().getCompound("golem_link");
            if (tag.isEmpty()) {
                return;
            }
            tooltip.add(line("linked_to", tag.getString("name")));
            if (tag.getBoolean("source")) {
                tooltip.add(line("source"));
            }
            if (tag.getBoolean("encased")) {
                tooltip.add(line("encased"));
            }
            if (tag.contains("pin")) {
                tooltip.add(line(tag.getBoolean("strict") ? "pinned_only" : "pinned", tag.getString("pin")));
            }
        }

        @Override
        public ResourceLocation getUid() {
            return UID;
        }
    }
}
