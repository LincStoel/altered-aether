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

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.phys.Vec3;

/**
 * A mote of essence drifting from an orb to {@code (x, y, z)} over {@code life} ticks, along a curve that bows out by
 * up to {@code curve} blocks (a stream to the plinth bows little, a mote to a capstone more).
 */
public record EssenceMoteOptions(double x, double y, double z, int life, float curve) implements ParticleOptions {
    public static final MapCodec<EssenceMoteOptions> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.DOUBLE.fieldOf("x").forGetter(EssenceMoteOptions::x),
            Codec.DOUBLE.fieldOf("y").forGetter(EssenceMoteOptions::y),
            Codec.DOUBLE.fieldOf("z").forGetter(EssenceMoteOptions::z),
            Codec.INT.fieldOf("life").forGetter(EssenceMoteOptions::life),
            Codec.FLOAT.fieldOf("curve").forGetter(EssenceMoteOptions::curve)
    ).apply(i, EssenceMoteOptions::new));
    public static final StreamCodec<ByteBuf, EssenceMoteOptions> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.DOUBLE, EssenceMoteOptions::x,
            ByteBufCodecs.DOUBLE, EssenceMoteOptions::y,
            ByteBufCodecs.DOUBLE, EssenceMoteOptions::z,
            ByteBufCodecs.VAR_INT, EssenceMoteOptions::life,
            ByteBufCodecs.FLOAT, EssenceMoteOptions::curve,
            EssenceMoteOptions::new);

    public EssenceMoteOptions(Vec3 target, int life, float curve) {
        this(target.x, target.y, target.z, life, curve);
    }

    public Vec3 target() {
        return new Vec3(x, y, z);
    }

    @Override
    public ParticleType<?> getType() {
        return EssenceRegistries.ESSENCE_MOTE.get();
    }

    /** The registered type, with the codecs above. */
    public static final class Type extends ParticleType<EssenceMoteOptions> {
        public Type() {
            super(false);
        }

        @Override
        public MapCodec<EssenceMoteOptions> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<? super RegistryFriendlyByteBuf, EssenceMoteOptions> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
