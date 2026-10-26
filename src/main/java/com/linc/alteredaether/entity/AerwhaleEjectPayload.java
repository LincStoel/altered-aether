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
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * A rider letting go of a charged leap off their aerwhale, with how far it charged (0 to 1) and how fast the whale was
 * going as their client saw it. Only the pilot's client knows the whale's true speed; the server just hears where it
 * ends up each tick.
 */
public record AerwhaleEjectPayload(float charge, float vx, float vy, float vz) implements CustomPacketPayload {
    public static final Type<AerwhaleEjectPayload> TYPE = new Type<>(AlteredAether.id("aerwhale_eject"));
    public static final StreamCodec<ByteBuf, AerwhaleEjectPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, AerwhaleEjectPayload::charge,
            ByteBufCodecs.FLOAT, AerwhaleEjectPayload::vx,
            ByteBufCodecs.FLOAT, AerwhaleEjectPayload::vy,
            ByteBufCodecs.FLOAT, AerwhaleEjectPayload::vz,
            AerwhaleEjectPayload::new);

    public AerwhaleEjectPayload(float charge, Vec3 velocity) {
        this(charge, (float) velocity.x, (float) velocity.y, (float) velocity.z);
    }

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
                    whale.eject(context.player(), payload.charge(), new Vec3(payload.vx(), payload.vy(), payload.vz()));
                }
            });
        }

        private Registration() {
        }
    }
}
