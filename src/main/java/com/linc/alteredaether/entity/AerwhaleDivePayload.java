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
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * A pilot letting go of a charged dive, with its power from 0 to 100 as on a horse's jump bar. The pilot's client has
 * already started the dive, since it flies the whale; the server only hears of it, for the call and the cooldown.
 */
public record AerwhaleDivePayload(int power) implements CustomPacketPayload {
    public static final Type<AerwhaleDivePayload> TYPE = new Type<>(AlteredAether.id("aerwhale_dive"));
    public static final StreamCodec<ByteBuf, AerwhaleDivePayload> STREAM_CODEC =
            ByteBufCodecs.VAR_INT.map(AerwhaleDivePayload::new, AerwhaleDivePayload::power);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @EventBusSubscriber(modid = AlteredAether.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        @SubscribeEvent
        public static void register(RegisterPayloadHandlersEvent event) {
            event.registrar("1").playToServer(TYPE, STREAM_CODEC, (payload, context) -> {
                if (context.player().getVehicle() instanceof TamedAerwhale whale) {
                    whale.diveCalled(context.player(), payload.power());
                }
            });
        }

        private Registration() {
        }
    }
}
