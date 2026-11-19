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

import com.linc.alteredaether.registry.AARegistries;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.common.world.ModifiableBiomeInfo;

/**
 * Recolours the water of every biome in {@code biomes} still using {@code from} (vanilla's default blue). Biomes
 * that chose their own water colour, such as the swampy giant-tree biomes or the archwood groves, are left alone.
 */
public record WaterColorModifier(HolderSet<Biome> biomes, int from, int waterColor, int waterFogColor)
        implements BiomeModifier {

    public static final MapCodec<WaterColorModifier> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Biome.LIST_CODEC.fieldOf("biomes").forGetter(WaterColorModifier::biomes),
            Codec.INT.fieldOf("from").forGetter(WaterColorModifier::from),
            Codec.INT.fieldOf("water_color").forGetter(WaterColorModifier::waterColor),
            Codec.INT.fieldOf("water_fog_color").forGetter(WaterColorModifier::waterFogColor)
    ).apply(i, WaterColorModifier::new));

    @Override
    public void modify(Holder<Biome> biome, Phase phase, ModifiableBiomeInfo.BiomeInfo.Builder builder) {
        if (phase != Phase.MODIFY || !biomes.contains(biome)) {
            return;
        }
        var effects = builder.getSpecialEffects();
        if (effects.waterColor() == from) {
            effects.waterColor(waterColor).waterFogColor(waterFogColor);
        }
    }

    @Override
    public MapCodec<? extends BiomeModifier> codec() {
        return AARegistries.WATER_COLOR.get();
    }
}
