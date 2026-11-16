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
package com.linc.alteredaether.loot;

import com.aetherteam.aether.block.AetherBlocks;
import com.linc.alteredaether.registry.AARegistries;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;

/**
 * Gravitite ore drops raw gravitite, as iron ore drops raw iron: one, multiplied by Fortune the way vanilla's ores are.
 * The Aether's own loot table is left alone; this swaps what it rolls. Its JSON conditions limit it to that table and
 * leave silk touch out, so silk touch still gives the ore. Drills and lasers that break the ore get raw gravitite too.
 */
public final class GravititeOreDrops extends LootModifier {
    public static final MapCodec<GravititeOreDrops> CODEC = RecordCodecBuilder.mapCodec(i -> codecStart(i)
            .apply(i, GravititeOreDrops::new));

    public GravititeOreDrops(LootItemCondition[] conditions) {
        super(conditions);
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> loot, LootContext context) {
        ItemStack tool = context.getParamOrNull(LootContextParams.TOOL);
        int fortune = tool == null ? 0 : EnchantmentHelper.getItemEnchantmentLevel(context.getLevel().registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.FORTUNE), tool);
        for (int i = 0; i < loot.size(); i++) {
            ItemStack stack = loot.get(i);
            if (stack.is(AetherBlocks.GRAVITITE_ORE.get().asItem())) {
                int count = stack.getCount();
                if (fortune > 0) {
                    // vanilla's ore bonus (ApplyBonusCount.OreDrops)
                    count *= Math.max(0, context.getRandom().nextInt(fortune + 2) - 1) + 1;
                }
                loot.set(i, new ItemStack(AARegistries.RAW_GRAVITITE.get(), count));
            }
        }
        return loot;
    }

    @Override
    public MapCodec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }
}
