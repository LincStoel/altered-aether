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

import com.linc.alteredaether.compat.AddedMob;
import com.linc.alteredaether.config.AACommonConfig;
import com.linc.alteredaether.registry.AARegistries;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.biome.MobSpawnSettings.SpawnerData;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.common.world.ModifiableBiomeInfo;

import java.util.Optional;

/**
 * Adds one mob's spawn entry (and its spawn cost) to a set of biomes, scaled by that mob's entry in the common config.
 * The category defaults to the entity's own. The per-biome numbers live in data; the config can switch the mob off, scale its weight, override its group size
 * and retune its spawn cost without touching that data.
 */
public record ConfiguredSpawnsModifier(HolderSet<Biome> biomes, String mob, Optional<MobCategory> category, int weight,
                                       int minCount, int maxCount, boolean applyCost) implements BiomeModifier {

    public static final MapCodec<ConfiguredSpawnsModifier> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Biome.LIST_CODEC.fieldOf("biomes").forGetter(ConfiguredSpawnsModifier::biomes),
            Codec.STRING.fieldOf("mob").forGetter(ConfiguredSpawnsModifier::mob),
            MobCategory.CODEC.optionalFieldOf("category").forGetter(ConfiguredSpawnsModifier::category),
            Codec.INT.fieldOf("weight").forGetter(ConfiguredSpawnsModifier::weight),
            Codec.INT.fieldOf("min_count").forGetter(ConfiguredSpawnsModifier::minCount),
            Codec.INT.fieldOf("max_count").forGetter(ConfiguredSpawnsModifier::maxCount),
            Codec.BOOL.optionalFieldOf("apply_cost", true).forGetter(ConfiguredSpawnsModifier::applyCost)
    ).apply(i, ConfiguredSpawnsModifier::new));

    @Override
    public void modify(Holder<Biome> biome, Phase phase, ModifiableBiomeInfo.BiomeInfo.Builder builder) {
        if (phase != Phase.ADD || !biomes.contains(biome)) {
            return;
        }
        Optional<EntityType<?>> type = entityType();
        AACommonConfig.MobSettings settings = AACommonConfig.mob(mob);
        if (type.isEmpty() || settings == null || !settings.enabled().get()) {
            return;
        }

        int scaled = (int) Math.round(weight * settings.weightMultiplier().get());
        int min = settings.minGroup().get() >= 0 ? settings.minGroup().get() : minCount;
        int max = Math.max(min, settings.maxGroup().get() >= 0 ? settings.maxGroup().get() : maxCount);
        MobSpawnSettings.Builder spawns = builder.getMobSpawnSettings();
        if (scaled > 0) {
            spawns.addSpawn(category.orElse(type.get().getCategory()), new SpawnerData(type.get(), scaled, min, max));
        }
        double charge = settings.charge().get();
        if (applyCost && charge > 0) {
            spawns.addMobCharge(type.get(), charge, settings.energyBudget().get());
        }
    }

    private Optional<EntityType<?>> entityType() {
        return AddedMob.ALL.stream()
                .filter(m -> m.key().equals(mob))
                .findFirst()
                .flatMap(m -> BuiltInRegistries.ENTITY_TYPE.getOptional(ResourceLocation.parse(m.entityId())));
    }

    @Override
    public MapCodec<? extends BiomeModifier> codec() {
        return AARegistries.CONFIGURED_SPAWNS.get();
    }
}
