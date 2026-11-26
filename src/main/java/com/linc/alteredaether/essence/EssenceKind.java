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
package com.linc.alteredaether.essence;

import com.linc.alteredaether.config.AACommonConfig;
import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

/**
 * The two kinds of essence source: a Channel, found on Highlands islets, and a Wellspring, over a landmark, which flows
 * stronger and blesses twice as fast. Each source rolls a grade in [0, 1) and its flow is {@link #flow}.
 */
public enum EssenceKind implements StringRepresentable {
    CHANNEL("channel", 1.8),
    WELLSPRING("wellspring", 1.1);

    public static final Codec<EssenceKind> CODEC = StringRepresentable.fromEnum(EssenceKind::values);
    public static final StreamCodec<ByteBuf, EssenceKind> STREAM_CODEC =
            ByteBufCodecs.idMapper(i -> values()[i], EssenceKind::ordinal);

    private final String name;
    /** Skews the grade toward the low end: most channels are near their least, few near their most. */
    private final double skew;

    EssenceKind(String name, double skew) {
        this.name = name;
        this.skew = skew;
    }

    /** FE per tick a full circle draws from a source of this kind and {@code grade}, before breathing. */
    public double flow(double grade) {
        int min = this == CHANNEL ? AACommonConfig.CHANNEL_MIN_FLOW.get() : AACommonConfig.WELLSPRING_MIN_FLOW.get();
        int max = this == CHANNEL ? AACommonConfig.CHANNEL_MAX_FLOW.get() : AACommonConfig.WELLSPRING_MAX_FLOW.get();
        return min + Math.max(0, max - min) * Math.pow(Math.clamp(grade, 0.0, 1.0), skew);
    }

    public int regenTicks() {
        return this == CHANNEL ? AACommonConfig.CHANNEL_REGEN_TICKS.get() : AACommonConfig.WELLSPRING_REGEN_TICKS.get();
    }

    public int growthTicks() {
        return this == CHANNEL ? AACommonConfig.CHANNEL_GROWTH_TICKS.get() : AACommonConfig.WELLSPRING_GROWTH_TICKS.get();
    }

    /** How strong a Quintessence Phial brewed here is: a Wellspring's is twice a Channel's. */
    public int amplifier() {
        return this == CHANNEL ? 0 : 1;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
