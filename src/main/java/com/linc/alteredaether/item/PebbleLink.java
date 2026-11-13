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

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.UUID;

/**
 * The gravitite golem a storage pebble belongs to, the dimension it sits in, its name when the pebble was handed out,
 * and whether the golem is gone. Kept to what never changes while the golem lives, so a pebble in hand doesn't bob
 * each time its golem's links or mode change: the tooltip reads those from what the client is sent (see
 * GolemLinksPayload). The one thing here that does change is {@code far}: whether whoever carries the pebble is out of
 * reach of every golem of its network, which is when it goes dark.
 */
public record PebbleLink(UUID golem, ResourceKey<Level> dimension, String name, boolean dead, boolean far) {
    public static final Codec<PebbleLink> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.fieldOf("golem").forGetter(PebbleLink::golem),
            ResourceKey.codec(Registries.DIMENSION).fieldOf("dimension").forGetter(PebbleLink::dimension),
            Codec.STRING.optionalFieldOf("name", "").forGetter(PebbleLink::name),
            Codec.BOOL.optionalFieldOf("dead", false).forGetter(PebbleLink::dead),
            Codec.BOOL.optionalFieldOf("far", false).forGetter(PebbleLink::far)
    ).apply(instance, PebbleLink::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, PebbleLink> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, PebbleLink::golem,
            ResourceKey.streamCodec(Registries.DIMENSION), PebbleLink::dimension,
            ByteBufCodecs.STRING_UTF8, PebbleLink::name,
            ByteBufCodecs.BOOL, PebbleLink::dead,
            ByteBufCodecs.BOOL, PebbleLink::far,
            PebbleLink::new);

    /** A pebble fresh from its golem, so within its reach. */
    public PebbleLink(UUID golem, ResourceKey<Level> dimension, String name, boolean dead) {
        this(golem, dimension, name, dead, false);
    }

    public PebbleLink asDead() {
        return new PebbleLink(golem, dimension, name, true, far);
    }

    public PebbleLink withFar(boolean far) {
        return new PebbleLink(golem, dimension, name, dead, far);
    }

    /** Whether the pebble glows: its golem lives and is within reach. */
    public boolean glows() {
        return !dead && !far;
    }
}
