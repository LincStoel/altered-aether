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
package com.linc.alteredaether.item;

import com.linc.alteredaether.entity.golem.GravititeGolem;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;

import java.util.UUID;

/**
 * The golem network a storage pebble belongs to, the golem it came from ({@code origin}), the dimension they sit in,
 * the network's name and colour when last seen, and whether the network is gone. The pebble follows its origin golem
 * from network to network while that golem lives, and the network after. Whether it glows (its carrier within reach of
 * the network) is worked out where it is drawn, from what the client is told (see PebbleTracker), so a pebble in a
 * chest or on the ground never shows a stale glow and a pebble in hand doesn't bob when it changes.
 *
 * <p>Pebbles from before networks had ids of their own store only their golem ({@code golem}, read as the origin) and
 * pick their network up from it.
 */
public record PebbleLink(UUID origin, UUID network, ResourceKey<Level> dimension, String name, DyeColor color, boolean dead) {
    /** The network of a pebble not yet told its own: one from before pebbles belonged to networks. */
    public static final UUID NO_NETWORK = new UUID(0, 0);

    private static final StreamCodec<ByteBuf, DyeColor> COLOR = ByteBufCodecs.VAR_INT.map(DyeColor::byId, DyeColor::getId);

    public static final Codec<PebbleLink> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.fieldOf("golem").forGetter(PebbleLink::origin),
            UUIDUtil.CODEC.optionalFieldOf("network", NO_NETWORK).forGetter(PebbleLink::network),
            ResourceKey.codec(Registries.DIMENSION).fieldOf("dimension").forGetter(PebbleLink::dimension),
            Codec.STRING.optionalFieldOf("name", "").forGetter(PebbleLink::name),
            DyeColor.CODEC.optionalFieldOf("color", GravititeGolem.DEFAULT_COLOR).forGetter(PebbleLink::color),
            Codec.BOOL.optionalFieldOf("dead", false).forGetter(PebbleLink::dead)
    ).apply(instance, PebbleLink::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, PebbleLink> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, PebbleLink::origin,
            UUIDUtil.STREAM_CODEC, PebbleLink::network,
            ResourceKey.streamCodec(Registries.DIMENSION), PebbleLink::dimension,
            ByteBufCodecs.STRING_UTF8, PebbleLink::name,
            COLOR.cast(), PebbleLink::color,
            ByteBufCodecs.BOOL, PebbleLink::dead,
            PebbleLink::new);

    public PebbleLink asDead() {
        return new PebbleLink(origin, network, dimension, name, color, true);
    }

    /** The pebble now belonging to {@code network}, named {@code name} and dyed {@code color}. */
    public PebbleLink in(UUID network, String name, DyeColor color) {
        return new PebbleLink(origin, network, dimension, name, color, dead);
    }

    public boolean hasNetwork() {
        return !NO_NETWORK.equals(network);
    }
}
