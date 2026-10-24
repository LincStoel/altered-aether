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
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.common.world.ModifiableBiomeInfo;

import java.util.HashSet;
import java.util.Set;

/**
 * Removes the natural spawns of every added mob whose {@code aether_exclusive} toggle is on from all biomes outside
 * the Aether. Runs in the REMOVE phase, so it sees spawns the owning mod added in ADD.
 */
public record AetherExclusiveModifier(HolderSet<Biome> aetherBiomes) implements BiomeModifier {

    public static final MapCodec<AetherExclusiveModifier> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Biome.LIST_CODEC.fieldOf("aether_biomes").forGetter(AetherExclusiveModifier::aetherBiomes)
    ).apply(i, AetherExclusiveModifier::new));

    @Override
    public void modify(Holder<Biome> biome, Phase phase, ModifiableBiomeInfo.BiomeInfo.Builder builder) {
        if (phase != Phase.REMOVE || aetherBiomes.contains(biome)) {
            return;
        }
        Set<EntityType<?>> exclusive = exclusiveTypes();
        if (exclusive.isEmpty()) {
            return;
        }
        var spawns = builder.getMobSpawnSettings();
        for (MobCategory category : MobCategory.values()) {
            spawns.getSpawner(category).removeIf(data -> exclusive.contains(data.type));
        }
        exclusive.forEach(spawns::removeSpawnCost);
    }

    private static Set<EntityType<?>> exclusiveTypes() {
        Set<EntityType<?>> types = new HashSet<>();
        for (AddedMob mob : AddedMob.ALL) {
            AACommonConfig.MobSettings settings = AACommonConfig.mob(mob.key());
            if (settings != null && settings.aetherExclusive().get()) {
                BuiltInRegistries.ENTITY_TYPE.getOptional(ResourceLocation.parse(mob.entityId())).ifPresent(types::add);
            }
        }
        return types;
    }

    @Override
    public MapCodec<? extends BiomeModifier> codec() {
        return AARegistries.AETHER_EXCLUSIVE.get();
    }
}
