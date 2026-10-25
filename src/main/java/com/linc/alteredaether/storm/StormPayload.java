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
package com.linc.alteredaether.storm;

import com.linc.alteredaether.AlteredAether;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * The storm's intensity where a player stands, sent to them about once a second so their client can darken the sky,
 * bring the rain and flash the clouds. The client has no world seed, so it can't work the storm field out itself.
 */
public record StormPayload(float intensity) implements CustomPacketPayload {
    public static final Type<StormPayload> TYPE = new Type<>(AlteredAether.id("storm"));
    public static final StreamCodec<ByteBuf, StormPayload> STREAM_CODEC =
            ByteBufCodecs.FLOAT.map(StormPayload::new, StormPayload::intensity);

    /** The latest intensity received, read by the client's atmosphere. */
    private static volatile float received;

    public static float received() {
        return received;
    }

    public static void clear() {
        received = 0;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @EventBusSubscriber(modid = AlteredAether.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        @SubscribeEvent
        public static void register(RegisterPayloadHandlersEvent event) {
            event.registrar("1").playToClient(TYPE, STREAM_CODEC, (payload, context) -> received = payload.intensity());
        }

        private Registration() {
        }
    }
}
