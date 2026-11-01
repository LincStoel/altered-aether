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
package com.linc.alteredaether.worldgen.modifier;

import com.linc.alteredaether.config.AACommonConfig;
import com.linc.alteredaether.registry.AARegistries;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings.MobSpawnCost;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.common.world.ModifiableBiomeInfo;

import java.util.Optional;

/**
 * Multiplies the spawn-cost energy budget of a mob that biomes already carry, which is what sets how many of it fit in
 * an area: a multiplier under one thins it out, over one packs it in. Biomes with no cost entry for the mob are left alone.
 * Only used for the cockatrice rebalance, so the cockatrice config switch turns it off.
 */
public record ScaleSpawnCostModifier(HolderSet<Biome> biomes, ResourceLocation mob, double budgetMultiplier)
        implements BiomeModifier {

    public static final MapCodec<ScaleSpawnCostModifier> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Biome.LIST_CODEC.fieldOf("biomes").forGetter(ScaleSpawnCostModifier::biomes),
            ResourceLocation.CODEC.fieldOf("mob").forGetter(ScaleSpawnCostModifier::mob),
            com.mojang.serialization.Codec.DOUBLE.fieldOf("budget_multiplier")
                    .forGetter(ScaleSpawnCostModifier::budgetMultiplier)
    ).apply(i, ScaleSpawnCostModifier::new));

    @Override
    public void modify(Holder<Biome> biome, Phase phase, ModifiableBiomeInfo.BiomeInfo.Builder builder) {
        if (phase != Phase.MODIFY || AACommonConfig.DISABLE_COCKATRICE_REBALANCE.get() || !biomes.contains(biome)) {
            return;
        }
        Optional<EntityType<?>> type = BuiltInRegistries.ENTITY_TYPE.getOptional(mob);
        if (type.isEmpty()) {
            return;
        }
        MobSpawnCost cost = builder.getMobSpawnSettings().getCost(type.get());
        if (cost != null) {
            builder.getMobSpawnSettings().addMobCharge(type.get(), cost.charge(), cost.energyBudget() * budgetMultiplier);
        }
    }

    @Override
    public MapCodec<? extends BiomeModifier> codec() {
        return AARegistries.SCALE_SPAWN_COST.get();
    }
}
