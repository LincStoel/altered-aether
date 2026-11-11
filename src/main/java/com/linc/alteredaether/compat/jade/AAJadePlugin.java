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

import com.linc.alteredaether.AlteredAether;
import com.linc.alteredaether.entity.golem.GolemMode;
import com.linc.alteredaether.entity.golem.GolemNetworks;
import com.linc.alteredaether.entity.golem.GolemSorter;
import com.linc.alteredaether.entity.golem.GolemStorage;
import com.linc.alteredaether.entity.golem.GravititeGolem;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
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

import java.util.List;

/**
 * Jade lines for gravitite golems: a golem's mode, links, network and what it is doing (naming what holds it up), and
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
            tag.putInt("golems", net.members().size());
            GolemSorter.Report report = GolemSorter.report(level, net);
            tag.putString("status", golem.isPaused() ? "paused" : !golem.isSeated() ? "moving"
                    : report.status().name().toLowerCase());
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
            if (tag.getInt("golems") > 1) {
                tooltip.add(line("network", tag.getInt("golems")));
            }
            String status = tag.getString("status");
            tooltip.add(switch (status) {
                case "sorting" -> line("sorting", tag.getInt("moves"));
                case "no_space" -> line("no_space", tag.getInt("unplaced"));
                case "sorted", "paused", "moving" -> line(status);
                default -> line("idle");
            });
            if (tag.getInt("inUse") > 0) {
                tooltip.add(line("in_use", tag.getInt("inUse")));
            }
            if (tag.getInt("away") > 0) {
                tooltip.add(line("out_of_reach", tag.getInt("away")));
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
            List<ItemStack> pins = GolemStorage.pins(level, pos);
            if (!pins.isEmpty()) {
                tag.putString("pin", pins.get(0).getHoverName().getString());
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
            if (tag.contains("pin")) {
                tooltip.add(line("pinned", tag.getString("pin")));
            }
        }

        @Override
        public ResourceLocation getUid() {
            return UID;
        }
    }
}
