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
import com.linc.alteredaether.electric.AetherJarBlockEntity;
import com.linc.alteredaether.electric.AetherJarItem;
import com.linc.alteredaether.electric.ElectricRegistries;
import com.linc.alteredaether.essence.EssenceOrbBlock;
import com.linc.alteredaether.essence.EssenceOrbBlockEntity;
import com.linc.alteredaether.essence.EssencePlinthBlock;
import com.linc.alteredaether.essence.EssencePlinthBlockEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.ui.BoxStyle;
import snownee.jade.api.ui.IElementHelper;
import snownee.jade.api.ui.ProgressStyle;

/**
 * Jade lines for the essence sources: how much the circle draws now, how many capstones it has gemmed and how many
 * jars it is charging; and on the plinth, a bar for how far an infusion has come or how full the jar being charged is.
 */
final class EssenceJade {
    private static final String KEY = "jade." + AlteredAether.MOD_ID + ".essence.";

    static void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(Orb.INSTANCE, EssenceOrbBlockEntity.class);
        registration.registerBlockDataProvider(Plinth.INSTANCE, EssencePlinthBlockEntity.class);
    }

    static void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(Orb.INSTANCE, EssenceOrbBlock.class);
        registration.registerBlockComponent(Plinth.INSTANCE, EssencePlinthBlock.class);
    }

    enum Orb implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
        INSTANCE;

        private static final ResourceLocation UID = AlteredAether.id("essence_orb");

        @Override
        public void appendServerData(CompoundTag data, BlockAccessor accessor) {
            if (accessor.getBlockEntity() instanceof EssenceOrbBlockEntity orb && orb.getLevel() != null) {
                data.putDouble("aa_flow", orb.flow(orb.getLevel().getGameTime()) * orb.capture());
                data.putInt("aa_gems", Integer.bitCount(orb.pairedGems()));
                data.putInt("aa_charging", orb.charging().size() + (orb.plinthActive() ? 1 : 0));
            }
        }

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            CompoundTag data = accessor.getServerData();
            if (!data.contains("aa_flow")) {
                return;
            }
            tooltip.add(Component.translatable(KEY + "flow", String.format(java.util.Locale.ROOT, "%.1f",
                    data.getDouble("aa_flow"))));
            tooltip.add(Component.translatable(KEY + "capture", data.getInt("aa_gems")));
            if (data.getInt("aa_charging") > 0) {
                tooltip.add(Component.translatable(KEY + "charging", data.getInt("aa_charging")));
            }
        }

        @Override
        public ResourceLocation getUid() {
            return UID;
        }
    }

    enum Plinth implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
        INSTANCE;

        private static final ResourceLocation UID = AlteredAether.id("essence_plinth");

        /** Icy blue for a jar's charge, like the electricity; essence gold for an infusion. */
        private static final int JAR_COLOR = 0xFF6FC3F0;
        private static final int JAR_COLOR_DARK = 0xFF2F7FB8;
        private static final int INFUSE_COLOR = 0xFFF0D070;
        private static final int INFUSE_COLOR_DARK = 0xFFB08A28;

        @Override
        public void appendServerData(CompoundTag data, BlockAccessor accessor) {
            if (accessor.getBlockEntity() instanceof EssencePlinthBlockEntity plinth) {
                ItemStack work = plinth.work();
                if (work.getItem() instanceof AetherJarItem
                        && !work.is(ElectricRegistries.CREATIVE_AETHER_JAR_ITEM.get())) {
                    data.putInt("aa_jar", AetherJarItem.energy(work));
                } else {
                    data.putInt("aa_progress", plinth.progress());
                    data.putInt("aa_cost", plinth.cost());
                }
            }
        }

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            CompoundTag data = accessor.getServerData();
            if (data.contains("aa_jar")) {
                int energy = data.getInt("aa_jar");
                int capacity = AetherJarBlockEntity.capacity();
                bar(tooltip, (float) energy / capacity, Component.translatable(KEY + "plinth_jar",
                        AetherJarItem.fe(energy), AetherJarItem.fe(capacity)), JAR_COLOR, JAR_COLOR_DARK);
            } else if (data.getInt("aa_cost") > 0) {
                float ratio = Math.min(1F, (float) data.getInt("aa_progress") / data.getInt("aa_cost"));
                bar(tooltip, ratio, Component.translatable(KEY + "plinth", (int) (ratio * 100)),
                        INFUSE_COLOR, INFUSE_COLOR_DARK);
            }
        }

        private static void bar(ITooltip tooltip, float ratio, Component text, int color, int dark) {
            IElementHelper helper = IElementHelper.get();
            ProgressStyle style = helper.progressStyle().color(color, dark).textColor(0xFFFFFFFF);
            tooltip.add(helper.progress(Math.clamp(ratio, 0F, 1F), text, style, BoxStyle.getNestedBox(), true));
        }

        @Override
        public ResourceLocation getUid() {
            return UID;
        }
    }

    private EssenceJade() {
    }
}
