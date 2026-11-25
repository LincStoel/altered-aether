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
import com.linc.alteredaether.electric.AetherJarBlock;
import com.linc.alteredaether.electric.AetherJarBlockEntity;
import com.linc.alteredaether.electric.AetherJarItem;
import com.linc.alteredaether.electric.SquallGeneratorBlock;
import com.linc.alteredaether.electric.SquallGeneratorCore;
import com.linc.alteredaether.electric.SquallGeneratorHost;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.config.IPluginConfig;

/**
 * Jade lines for the Tempest Peaks electricity: a jar's charge and the copper it is electrifying, a Squall
 * Generator's ambrosium, superheat and FE. Their state lives on the server, so it is sent across.
 */
final class ElectricJade {
    private static final String KEY = "jade." + AlteredAether.MOD_ID + ".";

    static void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(Jar.INSTANCE, AetherJarBlockEntity.class);
        registration.registerBlockDataProvider(Generator.INSTANCE, BlockEntity.class);
    }

    static void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(Jar.INSTANCE, AetherJarBlock.class);
        registration.registerBlockComponent(Generator.INSTANCE, SquallGeneratorBlock.class);
    }

    private static Component line(String key, Object... args) {
        return Component.translatable(KEY + key, args);
    }

    enum Jar implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
        INSTANCE;

        private static final ResourceLocation UID = AlteredAether.id("aether_jar");

        @Override
        public void appendServerData(CompoundTag data, BlockAccessor accessor) {
            if (accessor.getBlockEntity() instanceof AetherJarBlockEntity jar) {
                data.putInt("aa_energy", jar.energy());
                data.putBoolean("aa_creative", jar.creative());
                data.putInt("aa_copper", jar.liveCopper());
            }
        }

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            CompoundTag data = accessor.getServerData();
            if (!data.contains("aa_energy")) {
                return;
            }
            if (data.getBoolean("aa_creative")) {
                tooltip.add(line("creative_energy"));
                return;
            }
            tooltip.add(line("energy", AetherJarItem.fe(data.getInt("aa_energy")),
                    AetherJarItem.fe(AetherJarBlockEntity.capacity())));
            if (data.getInt("aa_copper") > 0) {
                tooltip.add(line("live_copper", data.getInt("aa_copper")));
            }
        }

        @Override
        public ResourceLocation getUid() {
            return UID;
        }
    }

    enum Generator implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
        INSTANCE;

        private static final ResourceLocation UID = AlteredAether.id("squall_generator");

        @Override
        public boolean shouldRequestData(BlockAccessor accessor) {
            return accessor.getBlockEntity() instanceof SquallGeneratorHost;
        }

        @Override
        public void appendServerData(CompoundTag data, BlockAccessor accessor) {
            if (accessor.getBlockEntity() instanceof SquallGeneratorHost host) {
                SquallGeneratorCore core = host.core();
                int queued = core.items().getStackInSlot(SquallGeneratorCore.FUEL_SLOT).getCount();
                data.putInt("aa_burn", core.burnTime());
                data.putInt("aa_queued", queued);
                data.putInt("aa_superheat", core.superheat());
                data.putInt("aa_fe", core.storedEnergy());
            }
        }

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            CompoundTag data = accessor.getServerData();
            if (!data.contains("aa_burn")) {
                return;
            }
            if (data.getInt("aa_burn") > 0) {
                tooltip.add(line("generator.fuel", data.getInt("aa_burn") / 20));
            } else if (data.getInt("aa_fe") <= 0 && data.getInt("aa_queued") <= 0) {
                tooltip.add(line("generator.idle"));
            }
            if (data.getInt("aa_superheat") > 0) {
                tooltip.add(line("generator.superheated", data.getInt("aa_superheat") / 20));
            }
        }

        @Override
        public ResourceLocation getUid() {
            return UID;
        }
    }

    private ElectricJade() {
    }
}
