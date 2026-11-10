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
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;

import java.util.Optional;
import java.util.UUID;

/**
 * The aerwhale a whale whistle calls, and its name when the whistle last heard it, for the tooltip: the client can't
 * look the whale up itself.
 */
public record WhistleLink(UUID whale, Optional<Component> name) {
    public static final Codec<WhistleLink> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.fieldOf("whale").forGetter(WhistleLink::whale),
            ComponentSerialization.CODEC.optionalFieldOf("name").forGetter(WhistleLink::name)
    ).apply(instance, WhistleLink::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, WhistleLink> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, WhistleLink::whale,
            ComponentSerialization.OPTIONAL_STREAM_CODEC, WhistleLink::name,
            WhistleLink::new);
}
