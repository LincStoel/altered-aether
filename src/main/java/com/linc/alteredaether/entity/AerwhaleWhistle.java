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
import com.linc.alteredaether.item.WhistleLink;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * What a whale whistle does when blown in the Aether: finds the whale it's linked to and brings it round to its owner,
 * to come in level and broadside across their front, its back at their feet, for them to jump aboard. A whale within
 * simulation distance flies there by itself (see {@link TamedAerwhale#startApproach}). One further off is loaded from
 * wherever it was left if need be and fetched: it appears out of sight, behind and above them, then swings round beside
 * them and comes in. If no such flight has room, it doesn't come.
 */
@EventBusSubscriber(modid = AlteredAether.MOD_ID)
public final class AerwhaleWhistle {
    public static final ResourceKey<Level> THE_AETHER =
            ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath("aether", "the_aether"));
    /** Cooldown after a call the whale answers, in ticks: about as long as its flight in. */
    public static final int ANSWERED_COOLDOWN = 100;
    /** Cooldown after a call that came to nothing, in ticks. */
    public static final int UNANSWERED_COOLDOWN = 20;
    /** How long a whale left far away has to load before the call goes unanswered, in ticks. */
    private static final int LOAD_TICKS = 100;
    private static final TicketType<ChunkPos> TICKET =
            TicketType.create(AlteredAether.MOD_ID + ":whale_whistle", Comparator.comparingLong(ChunkPos::toLong), LOAD_TICKS);

    /** Turns away from straight ahead it may arrive at, in degrees, best first. */
    private static final double[] ARRIVAL_TURNS = {0, 25, -25, 50, -50};
    /** How far it may arrive above where its back is level with its owner's feet, in blocks, tried half a block apart. */
    private static final double MAX_ARRIVAL_RISE = 3.5;
    /** Gaps it may arrive at between its flank and its owner, in blocks, best first. */
    private static final double[] ARRIVAL_GAPS = {1.25, 2.25};
    /** How far short of straight in front it arrives, in blocks, so it glides on across their front. */
    private static final double ARRIVAL_LEAD = 2;
    /** How far it glides on once it arrives, which must be open too. */
    private static final double GLIDE = 6;
    /**
     * Where it may appear, best first, as {blocks behind, blocks to the side, blocks above the feet}: far enough behind
     * to be out of sight, wide enough to pass beside rather than over them.
     */
    private static final double[][] STARTS = {{20, 8, 10}, {16, 7, 8}, {12, 6, 6}};
    /** Between the samples checked for room along the flight, in blocks. */
    private static final double SAMPLE = 0.75;

    private static final ResourceLocation TO_ME = AlteredAether.id("to_me");

    /** Calls waiting on a whale to load, by the player who blew the whistle. */
    private static final Map<UUID, Pending> PENDING = new HashMap<>();

    private record Pending(UUID whale, long deadline) {
    }

    private AerwhaleWhistle() {
    }

    /**
     * {@code player} blew a whistle carrying {@code link}: calls the whale if it can, or says why not. Returns the
     * cooldown to put on the whistle.
     */
    public static int blow(ServerPlayer player, @Nullable WhistleLink link) {
        if (link == null) {
            tell(player, "unlinked");
            return UNANSWERED_COOLDOWN;
        }
        if (!player.level().dimension().equals(THE_AETHER)) {
            tell(player, "cant_hear");
            return UNANSWERED_COOLDOWN;
        }
        MinecraftServer server = player.server;
        TamedAerwhale whale = find(server, link.whale());
        if (whale != null) {
            return summon(player, whale) ? ANSWERED_COOLDOWN : UNANSWERED_COOLDOWN;
        }
        // left somewhere nobody is: load it there, and call it once it's in
        AerwhaleLocations.Location location = AerwhaleLocations.get(server).find(link.whale());
        ServerLevel level = location == null ? null : server.getLevel(location.dimension());
        if (level == null) {
            tell(player, "no_answer");
            return UNANSWERED_COOLDOWN;
        }
        level.getChunkSource().addRegionTicket(TICKET, location.chunk(), 1, location.chunk());
        PENDING.put(player.getUUID(), new Pending(link.whale(), server.getTickCount() + LOAD_TICKS));
        return ANSWERED_COOLDOWN;
    }

    @Nullable
    private static TamedAerwhale find(MinecraftServer server, UUID id) {
        for (ServerLevel level : server.getAllLevels()) {
            if (level.getEntity(id) instanceof TamedAerwhale whale && whale.isAlive()) {
                return whale;
            }
        }
        return null;
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (PENDING.isEmpty()) {
            return;
        }
        MinecraftServer server = event.getServer();
        for (Iterator<Map.Entry<UUID, Pending>> it = PENDING.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Pending> entry = it.next();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null || !player.isAlive() || !player.level().dimension().equals(THE_AETHER)) {
                it.remove();
                continue;
            }
            TamedAerwhale whale = find(server, entry.getValue().whale());
            if (whale != null) {
                it.remove();
                summon(player, whale);
            } else if (server.getTickCount() >= entry.getValue().deadline()) {
                it.remove();
                tell(player, "no_answer");
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        PENDING.clear();
    }

    /**
     * Brings {@code whale} round to {@code player}, if it's theirs to call: flying in if it's within simulation distance
     * of them, otherwise fetched if there's room. Returns whether it answered.
     */
    public static boolean summon(ServerPlayer player, TamedAerwhale whale) {
        if (!whale.isOwnedBy(player)) {
            tell(player, "no_answer");
            return false;
        }
        if (whale.hasPassenger(player)) {
            return false;
        }
        if (whale.isVehicle()) {
            player.displayClientMessage(Component.translatable("message.altered_aether.whale_whistle.busy",
                    whale.getDisplayName()), true);
            return false;
        }
        ServerLevel level = player.serverLevel();
        if (whale.level() == level && level.isPositionEntityTicking(whale.blockPosition())) {
            whale.startApproach(player);
            award(player);
            return true;
        }
        return fetch(player, whale);
    }

    /**
     * Fetches {@code whale} to {@code player} from wherever it is: it appears behind and above them and flies round to
     * their front, if there's room for that. Returns whether it came.
     */
    public static boolean fetch(ServerPlayer player, TamedAerwhale whale) {
        ServerLevel level = player.serverLevel();
        WhistleFlight flight = plan(level, player, whale);
        if (flight == null) {
            tell(player, "no_room");
            return false;
        }
        Vec3 start = flight.start();
        Vec3 heading = flight.heading(0);
        float yaw = (float) (Mth.atan2(heading.z, heading.x) * Mth.RAD_TO_DEG) - 90.0F;
        if (whale.level() != level) {
            Entity moved = whale.changeDimension(new DimensionTransition(level, start, Vec3.ZERO, yaw, 0,
                    DimensionTransition.DO_NOTHING));
            if (!(moved instanceof TamedAerwhale arrived)) {
                tell(player, "no_answer");
                return false;
            }
            whale = arrived;
        } else {
            whale.moveTo(start.x, start.y, start.z, yaw, 0);
            // Clients ease a teleported entity over three ticks, which would streak a whale already in sight across
            // the sky; tracked afresh, it simply appears where it now is.
            ServerChunkCache chunks = level.getChunkSource();
            chunks.removeEntity(whale);
            chunks.addEntity(whale);
        }
        whale.startSummon(flight);
        award(player);
        return true;
    }

    private static void award(ServerPlayer player) {
        AdvancementHolder toMe = player.server.getAdvancements().get(TO_ME);
        if (toMe != null) {
            player.getAdvancements().award(toMe, "summoned");
        }
    }

    /**
     * The best flight round to {@code player} that {@code whale} has room for, appearing behind them, or null. It
     * arrives as low as it can, as straight ahead and as close as it can, and comes from whichever side has room.
     */
    @Nullable
    static WhistleFlight plan(Level level, Player player, TamedAerwhale whale) {
        return plan(level, player, whale, false);
    }

    /**
     * The best flight round to {@code player} that {@code whale} has room for from where it is now, setting off the way
     * it's going, or null. It comes in from its own side of them.
     */
    @Nullable
    public static WhistleFlight planFromHere(Level level, Player player, TamedAerwhale whale) {
        return plan(level, player, whale, true);
    }

    @Nullable
    private static WhistleFlight plan(Level level, Player player, TamedAerwhale whale, boolean fromHere) {
        double width = whale.getBbWidth();
        double height = whale.getBbHeight();
        Vec3 feet = player.position();
        Vec3 forward = Vec3.directionFromRotation(0, player.getYRot());
        Vec3 right = new Vec3(-forward.z, 0, forward.x);
        AABB body = player.getBoundingBox().inflate(0.5);
        double first = player.getRandom().nextBoolean() ? 1 : -1;
        for (double rise = -whale.deckHeight(); rise <= MAX_ARRIVAL_RISE; rise += 0.5) {
            for (double turn : ARRIVAL_TURNS) {
                Vec3 toward = forward.yRot((float) Math.toRadians(turn));
                for (double gap : ARRIVAL_GAPS) {
                    double reach = width / 2 + gap;
                    for (double side : new double[]{first, -first}) {
                        // it comes in from this side, crossing their front toward the other
                        Vec3 across = new Vec3(toward.z, 0, -toward.x).scale(side);
                        Vec3 end = feet.add(toward.scale(reach)).add(across.scale(-ARRIVAL_LEAD)).add(0, rise, 0);
                        if (fromHere) {
                            // it can only come in from the side it's on
                            Vec3 from = whale.position();
                            if (across.dot(from.subtract(end)) > 0
                                    || !arrivalClear(level, whale, end, across, width, height, body)) {
                                continue;
                            }
                            double run = from.distanceTo(end);
                            WhistleFlight flight = new WhistleFlight(from, whale.travelHeading().scale(run * 0.35), end,
                                    across.scale(Math.max(4, run * 0.3)));
                            if (flightClear(level, whale, flight, width, height, body)) {
                                return flight;
                            }
                            continue;
                        }
                        if (!arrivalClear(level, whale, end, across, width, height, body)) {
                            continue;
                        }
                        for (double[] start : STARTS) {
                            Vec3 from = feet.add(forward.scale(-start[0])).add(right.scale(side * start[1]))
                                    .add(0, start[2], 0);
                            // set on the far side of the arrival from the way it comes in
                            if (across.dot(from.subtract(end)) > 0) {
                                continue;
                            }
                            WhistleFlight flight = new WhistleFlight(from, forward.scale(start[0] * 0.8), end,
                                    across.scale(Math.max(start[1], 4)));
                            if (flightClear(level, whale, flight, width, height, body)) {
                                return flight;
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    /** Whether it has room to arrive at {@code end}, out of any water, and to glide on across from there. */
    private static boolean arrivalClear(Level level, TamedAerwhale whale, Vec3 end, Vec3 across, double width,
                                        double height, AABB player) {
        AABB box = box(end, width, height).move(0, 0.01, 0);
        if (box.intersects(player) || !loaded(level, box) || !level.noCollision(whale, box) || level.containsAnyLiquid(box)) {
            return false;
        }
        for (double s = SAMPLE; s <= GLIDE; s += SAMPLE) {
            AABB glide = box.move(across.scale(s)).deflate(0.25);
            if (glide.intersects(player) || !loaded(level, glide) || !level.noCollision(whale, glide)) {
                return false;
            }
        }
        return true;
    }

    /** Whether all of {@code flight} is open, and it starts somewhere roomy and dry. */
    private static boolean flightClear(Level level, TamedAerwhale whale, WhistleFlight flight, double width,
                                       double height, AABB player) {
        AABB start = box(flight.start(), width, height).inflate(1);
        if (!loaded(level, start) || !level.noCollision(whale, start) || level.containsAnyLiquid(start)) {
            return false;
        }
        double length = flight.length();
        for (double s = SAMPLE; ; s = Math.min(s + SAMPLE, length)) {
            AABB box = box(flight.at(s), width, height).deflate(0.25);
            if (box.intersects(player) || !loaded(level, box) || !level.noCollision(whale, box)) {
                return false;
            }
            if (s >= length) {
                return true;
            }
        }
    }

    private static AABB box(Vec3 bottom, double width, double height) {
        return new AABB(bottom.x - width / 2, bottom.y, bottom.z - width / 2,
                bottom.x + width / 2, bottom.y + height, bottom.z + width / 2);
    }

    private static boolean loaded(Level level, AABB box) {
        return level.hasChunksAt(BlockPos.containing(box.minX, box.minY, box.minZ),
                BlockPos.containing(box.maxX, box.maxY, box.maxZ));
    }

    private static void tell(ServerPlayer player, String message) {
        player.displayClientMessage(Component.translatable("message.altered_aether.whale_whistle." + message), true);
    }
}
