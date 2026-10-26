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
package com.linc.alteredaether.entity;

import com.linc.alteredaether.AlteredAether;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * Keeps a client out of a server whose aerwhales differ from its own. Deep Aether's patch adds synced data to every
 * aerwhale, so a client and server that disagree on {@link AerwhaleMode} would misread each other's whales. This
 * payload is never sent; its channel version, the mode, makes NeoForge refuse the connection when the two differ.
 */
public record AerwhaleModeCheck() implements CustomPacketPayload {
    public static final Type<AerwhaleModeCheck> TYPE = new Type<>(AlteredAether.id("aerwhale_mode"));
    public static final StreamCodec<ByteBuf, AerwhaleModeCheck> STREAM_CODEC = StreamCodec.unit(new AerwhaleModeCheck());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @EventBusSubscriber(modid = AlteredAether.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        @SubscribeEvent
        public static void register(RegisterPayloadHandlersEvent event) {
            String version = AerwhaleMode.deepAether() ? "deep_aether" : "altered_aether";
            event.registrar(version).playToClient(TYPE, STREAM_CODEC, (payload, context) -> {
            });
        }

        private Registration() {
        }
    }
}
