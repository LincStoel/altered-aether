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
package com.linc.alteredaether.registry;

import com.linc.alteredaether.AlteredAether;
import com.linc.alteredaether.worldgen.density.RegionMask;
import com.linc.alteredaether.worldgen.modifier.AetherExclusiveModifier;
import com.linc.alteredaether.worldgen.modifier.ClimateModifier;
import com.linc.alteredaether.worldgen.modifier.ConfiguredSpawnsModifier;
import com.linc.alteredaether.worldgen.provider.SubstituteStateProvider;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProviderType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Every deferred register the mod owns. */
public final class AARegistries {
    public static final DeferredRegister<MapCodec<? extends BiomeModifier>> BIOME_MODIFIER_SERIALIZERS =
            DeferredRegister.create(NeoForgeRegistries.Keys.BIOME_MODIFIER_SERIALIZERS, AlteredAether.MOD_ID);

    public static final DeferredHolder<MapCodec<? extends BiomeModifier>, MapCodec<ConfiguredSpawnsModifier>> CONFIGURED_SPAWNS =
            BIOME_MODIFIER_SERIALIZERS.register("configured_spawns", () -> ConfiguredSpawnsModifier.CODEC);
    public static final DeferredHolder<MapCodec<? extends BiomeModifier>, MapCodec<AetherExclusiveModifier>> AETHER_EXCLUSIVE =
            BIOME_MODIFIER_SERIALIZERS.register("aether_exclusive", () -> AetherExclusiveModifier.CODEC);
    public static final DeferredHolder<MapCodec<? extends BiomeModifier>, MapCodec<ClimateModifier>> CLIMATE =
            BIOME_MODIFIER_SERIALIZERS.register("climate", () -> ClimateModifier.CODEC);

    public static final DeferredRegister<BlockStateProviderType<?>> BLOCK_STATE_PROVIDERS =
            DeferredRegister.create(Registries.BLOCK_STATE_PROVIDER_TYPE, AlteredAether.MOD_ID);

    public static final DeferredHolder<BlockStateProviderType<?>, BlockStateProviderType<SubstituteStateProvider>> SUBSTITUTE_PROVIDER =
            BLOCK_STATE_PROVIDERS.register("substitute", () -> new BlockStateProviderType<>(SubstituteStateProvider.CODEC));

    public static final DeferredRegister<MapCodec<? extends DensityFunction>> DENSITY_FUNCTIONS =
            DeferredRegister.create(Registries.DENSITY_FUNCTION_TYPE, AlteredAether.MOD_ID);

    public static final DeferredHolder<MapCodec<? extends DensityFunction>, MapCodec<RegionMask>> REGION_MASK =
            DENSITY_FUNCTIONS.register("region_mask", () -> RegionMask.MAP_CODEC);

    public static void register(IEventBus modBus) {
        DENSITY_FUNCTIONS.register(modBus);
        BIOME_MODIFIER_SERIALIZERS.register(modBus);
        BLOCK_STATE_PROVIDERS.register(modBus);
    }

    private AARegistries() {
    }
}
