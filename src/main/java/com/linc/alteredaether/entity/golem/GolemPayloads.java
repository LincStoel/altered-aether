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
package com.linc.alteredaether.entity.golem;

import com.linc.alteredaether.AlteredAether;
import com.linc.alteredaether.client.golem.GolemClientHooks;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.List;
import java.util.UUID;

/** What the server tells clients about gravitite golems: glows, link lists for pebble holders, and flying items. */
public final class GolemPayloads {
    private static final StreamCodec<ByteBuf, List<BlockPos>> POSITIONS = BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list());
    private static final StreamCodec<ByteBuf, List<Integer>> INTS = ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list());
    private static final StreamCodec<ByteBuf, Vec3> VEC3 = StreamCodec.composite(
            ByteBufCodecs.DOUBLE, Vec3::x, ByteBufCodecs.DOUBLE, Vec3::y, ByteBufCodecs.DOUBLE, Vec3::z, Vec3::new);

    private GolemPayloads() {
    }

    /** Kinds of glow. */
    public static final byte LINK = 0, UNLINK = 1, SHOW = 2, SEARCH = 3, MARK = 4;

    /**
     * Makes blocks and golems glow for the player it is sent to: {@code bright} blocks fully, {@code dim} ones
     * faintly (links out of reach), the {@code golems} by entity id, and a thread of particles between each pair in
     * {@code threads}.
     */
    public record Glow(byte kind, int ticks, List<BlockPos> bright, List<BlockPos> dim, List<Integer> golems,
                       List<Integer> threads) implements CustomPacketPayload {
        public static final Type<Glow> TYPE = new Type<>(AlteredAether.id("golem_glow"));
        public static final StreamCodec<ByteBuf, Glow> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.BYTE, Glow::kind, ByteBufCodecs.VAR_INT, Glow::ticks, POSITIONS, Glow::bright,
                POSITIONS, Glow::dim, INTS, Glow::golems, INTS, Glow::threads, Glow::new);

        public static Glow blocks(byte kind, int ticks, List<BlockPos> bright) {
            return new Glow(kind, ticks, bright, List.of(), List.of(), List.of());
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * A golem network as its pebble's holder sees it: the golem's name, mode, range, links and where each golem
     * sits. Sent while the player holds that golem's pebble.
     */
    public record Links(UUID golem, String name, byte mode, int range, int linked, int maxLinks, List<BlockPos> links,
                        List<BlockPos> homes) implements CustomPacketPayload {
        public static final Type<Links> TYPE = new Type<>(AlteredAether.id("golem_links"));
        public static final StreamCodec<ByteBuf, Links> STREAM_CODEC = new StreamCodec<>() {
            @Override
            public Links decode(ByteBuf buf) {
                return new Links(UUIDUtil.STREAM_CODEC.decode(buf), ByteBufCodecs.STRING_UTF8.decode(buf), buf.readByte(),
                        ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
                        ByteBufCodecs.VAR_INT.decode(buf), POSITIONS.decode(buf), POSITIONS.decode(buf));
            }

            @Override
            public void encode(ByteBuf buf, Links links) {
                UUIDUtil.STREAM_CODEC.encode(buf, links.golem());
                ByteBufCodecs.STRING_UTF8.encode(buf, links.name());
                buf.writeByte(links.mode());
                ByteBufCodecs.VAR_INT.encode(buf, links.range());
                ByteBufCodecs.VAR_INT.encode(buf, links.linked());
                ByteBufCodecs.VAR_INT.encode(buf, links.maxLinks());
                POSITIONS.encode(buf, links.links());
                POSITIONS.encode(buf, links.homes());
            }
        };

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * An item starting to fly for golem {@code golem} from {@code from} to {@code to}, over each point in {@code via}
     * (the heads of the golems that relay it), taking {@code ticks}. Only a picture: the item stays in its chest until
     * the server moves it on landing.
     */
    public record Flight(int id, int golem, ItemStack item, Vec3 from, Vec3 to, List<Vec3> via, int ticks)
            implements CustomPacketPayload {
        public static final Type<Flight> TYPE = new Type<>(AlteredAether.id("golem_flight"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Flight> STREAM_CODEC = new StreamCodec<>() {
            @Override
            public Flight decode(RegistryFriendlyByteBuf buf) {
                int id = buf.readVarInt();
                int golem = buf.readVarInt();
                ItemStack item = ItemStack.STREAM_CODEC.decode(buf);
                Vec3 from = VEC3.decode(buf);
                Vec3 to = VEC3.decode(buf);
                List<Vec3> via = VEC3.apply(ByteBufCodecs.list()).decode(buf);
                return new Flight(id, golem, item, from, to, via, buf.readVarInt());
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buf, Flight flight) {
                buf.writeVarInt(flight.id());
                buf.writeVarInt(flight.golem());
                ItemStack.STREAM_CODEC.encode(buf, flight.item());
                VEC3.encode(buf, flight.from());
                VEC3.encode(buf, flight.to());
                VEC3.apply(ByteBufCodecs.list()).encode(buf, flight.via());
                buf.writeVarInt(flight.ticks());
            }
        };

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** A flight ended: it landed, or it was called off and the item fades where it is. */
    public record FlightEnd(int id, boolean landed) implements CustomPacketPayload {
        public static final Type<FlightEnd> TYPE = new Type<>(AlteredAether.id("golem_flight_end"));
        public static final StreamCodec<ByteBuf, FlightEnd> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, FlightEnd::id, ByteBufCodecs.BOOL, FlightEnd::landed, FlightEnd::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    @EventBusSubscriber(modid = AlteredAether.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        @SubscribeEvent
        public static void register(RegisterPayloadHandlersEvent event) {
            PayloadRegistrar registrar = event.registrar("1");
            // the handlers run only on clients; the client classes load only when one does
            registrar.playToClient(Glow.TYPE, Glow.STREAM_CODEC, (payload, context) -> GolemClientHooks.glow(payload));
            registrar.playToClient(Links.TYPE, Links.STREAM_CODEC, (payload, context) -> GolemClientHooks.links(payload));
            registrar.playToClient(Flight.TYPE, Flight.STREAM_CODEC, (payload, context) -> GolemClientHooks.flight(payload));
            registrar.playToClient(FlightEnd.TYPE, FlightEnd.STREAM_CODEC,
                    (payload, context) -> GolemClientHooks.flightEnd(payload));
        }

        private Registration() {
        }
    }
}
