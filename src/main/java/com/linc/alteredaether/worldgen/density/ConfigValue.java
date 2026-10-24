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
package com.linc.alteredaether.worldgen.density;

import com.linc.alteredaether.config.AACommonConfig;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.function.Supplier;

/**
 * A constant taken from the common config, so terrain data can scale a landform by a config value (multiply a peak
 * height by {@code crag_strength}, say) without any of it being hardcoded. The value is read once, when a world's
 * terrain is wired, so changing the config affects worlds loaded afterwards.
 */
public record ConfigValue(Key key, double value) implements DensityFunction.SimpleFunction {
    public enum Key implements StringRepresentable {
        CRAG_STRENGTH("crag_strength", AACommonConfig.CRAG_STRENGTH),
        TEMPEST_STRENGTH("tempest_strength", AACommonConfig.TEMPEST_STRENGTH),
        DUNE_HEIGHT("dune_height", AACommonConfig.DUNE_HEIGHT),
        ATOLL_SIZE("atoll_size", AACommonConfig.ATOLL_SIZE);

        public static final Codec<Key> CODEC = StringRepresentable.fromEnum(Key::values);

        private final String name;
        private final Supplier<Double> value;

        Key(String name, ModConfigSpec.DoubleValue value) {
            this.name = name;
            this.value = value;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    public static final MapCodec<ConfigValue> MAP_CODEC =
            Key.CODEC.fieldOf("key").xmap(key -> new ConfigValue(key, Double.NaN), ConfigValue::key);
    public static final KeyDispatchDataCodec<ConfigValue> CODEC = KeyDispatchDataCodec.of(MAP_CODEC);

    @Override
    public double compute(FunctionContext context) {
        return Double.isNaN(value) ? key.value.get() : value;
    }

    /** Wiring a world's terrain reads the config once and fixes the value for that world. */
    @Override
    public DensityFunction mapAll(Visitor visitor) {
        return visitor.apply(Double.isNaN(value) ? new ConfigValue(key, key.value.get()) : this);
    }

    @Override
    public double minValue() {
        return Double.isNaN(value) ? 0 : value;
    }

    @Override
    public double maxValue() {
        return Double.isNaN(value) ? 10 : value;
    }

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return CODEC;
    }
}
