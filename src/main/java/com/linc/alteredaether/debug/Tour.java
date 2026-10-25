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
package com.linc.alteredaether.debug;

import com.linc.alteredaether.AlteredAether;
import com.linc.alteredaether.registry.AARegistries;
import com.linc.alteredaether.storm.TempestStorms;
import com.linc.alteredaether.worldgen.landmark.Landmark;
import com.linc.alteredaether.worldgen.landmark.LandmarkField;
import com.linc.alteredaether.worldgen.landmark.LandmarkType;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * A guided camera tour for checking how things look, without anyone at the keyboard. With the system property
 * {@code altered_aether.tour} set to a comma-separated list of stops (landmark types, {@code storm} for the Tempest
 * crown under a forced storm, and {@code campfire} for an ambrosium campfire at night), a dev client that quick-plays
 * into a world flies to each stop, frames it from a few sides, saves a screenshot of each view to
 * {@code screenshots/tour/}, and quits.
 */
public final class Tour {
    private static final String SPEC = System.getProperty("altered_aether.tour", "");
    private static final ResourceKey<Level> THE_AETHER =
            ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath("aether", "the_aether"));

    /**
     * One screenshot: where the camera stands, the point it looks at, the time of day, and what to set up first. A
     * campfire shot's camera is placed once the campfire has found its ground.
     */
    record Shot(String name, double x, double y, double z, double fx, double fy, double fz, long time, boolean storm,
                boolean campfire) {
        float yaw() {
            return (float) (Mth.atan2(fz - z, fx - x) * Mth.RAD_TO_DEG) - 90;
        }

        float pitch() {
            double dx = fx - x, dz = fz - z;
            return (float) -(Mth.atan2(fy - y, Math.sqrt(dx * dx + dz * dz)) * Mth.RAD_TO_DEG);
        }

        Shot from(double cx, double cy, double cz, double tx, double ty, double tz) {
            return new Shot(name, cx, cy, cz, tx, ty, tz, time, storm, campfire);
        }
    }

    /** The planned shots, published by the server side once the stops are found. */
    private static volatile List<Shot> plan;
    /** Where the last campfire was put, once the server has found ground for it. */
    private static volatile BlockPos campfireAt;

    @EventBusSubscriber(modid = AlteredAether.MOD_ID)
    public static final class Server {
        @SubscribeEvent
        public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
            if (SPEC.isBlank() || !(event.getEntity() instanceof ServerPlayer player)) {
                return;
            }
            MinecraftServer server = player.getServer();
            ServerLevel aether = server.getLevel(THE_AETHER);
            LandmarkField field = LandmarkField.get(THE_AETHER);
            if (aether == null || field == null) {
                AlteredAether.LOGGER.error("Tour: the Aether or its landmarks aren't loaded");
                return;
            }
            CompletableFuture.supplyAsync(() -> shots(aether, field), Util.backgroundExecutor())
                    .thenAcceptAsync(shots -> {
                        aether.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
                        aether.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
                        player.setGameMode(GameType.CREATIVE);
                        player.getAbilities().flying = true;
                        player.onUpdateAbilities();
                        Shot first = shots.getFirst();
                        player.teleportTo(aether, first.x(), first.y(), first.z(), first.yaw(), first.pitch());
                        AlteredAether.LOGGER.info("Tour: {} shots planned", shots.size());
                        plan = shots;
                    }, server);
        }

        private static List<Shot> shots(ServerLevel aether, LandmarkField field) {
            List<Shot> shots = new ArrayList<>();
            BlockPos spawn = aether.getSharedSpawnPos();
            Landmark last = null;
            for (String stop : SPEC.split(",")) {
                stop = stop.trim().toLowerCase(Locale.ROOT);
                if (stop.equals("campfire")) {
                    // Near the last landmark; the server finds open, level ground for it when the shot comes up.
                    int x = last == null ? spawn.getX() : last.x(), z = last == null ? spawn.getZ() : last.z();
                    shots.add(new Shot("campfire", x, 100, z, x, 100, z, 14000, false, true));
                    continue;
                }
                boolean storm = stop.equals("storm");
                LandmarkType type = storm ? LandmarkType.TEMPEST_SPIRES : LandmarkType.valueOf(stop.toUpperCase(Locale.ROOT));
                Landmark site = field.nearest(type, spawn.getX(), spawn.getZ(), 60000).orElse(null);
                if (site == null) {
                    AlteredAether.LOGGER.error("Tour: no {} found", type.id());
                    continue;
                }
                last = site;
                AlteredAether.LOGGER.info("Tour: {} at {} {}", type.id(), site.x(), site.z());
                boolean tall = type == LandmarkType.TEMPEST_SPIRES || type == LandmarkType.FROZEN_SPIRE;
                int top = site.visitY() == Integer.MIN_VALUE ? 70 : site.visitY();
                double focus = tall ? top - 70 : top - 6;
                double distance = tall ? Math.max(90, site.extent() * 2.2) : Mth.clamp(site.extent() * 0.9, 48, 110);
                int views = storm ? 1 : 3;
                for (int i = 0; i < views; i++) {
                    double angle = Math.PI * 0.25 + i * Mth.TWO_PI / views;
                    double ex = site.x() + Math.cos(angle) * distance, ez = site.z() + Math.sin(angle) * distance;
                    double ey = focus + (tall ? 15 : distance * 0.55);
                    shots.add(new Shot(stop + "_" + i, ex, ey, ez, site.x(), focus, site.z(), storm ? 13000 : 6000,
                            storm, false));
                }
            }
            return shots;
        }

        private Server() {
        }
    }

    /** Flies the camera through the plan, a shot at a time, then quits the game. */
    @EventBusSubscriber(modid = AlteredAether.MOD_ID, value = Dist.CLIENT)
    public static final class Client {
        /** Ticks to wait at a new place at least, and at most before shooting regardless. */
        private static final int SETTLE = 60, GIVE_UP = 1600;
        private static int index = -1, waited;
        private static Shot current;
        /** How far the camera has been raised to see over whatever stood in its way. */
        private static double lift;

        @SubscribeEvent
        public static void onTick(ClientTickEvent.Post event) {
            List<Shot> shots = plan;
            Minecraft minecraft = Minecraft.getInstance();
            if (shots == null || minecraft.player == null || minecraft.level == null
                    || !minecraft.level.dimension().equals(THE_AETHER)) {
                return;
            }
            MinecraftServer server = minecraft.getSingleplayerServer();
            if (index < 0) {
                next(minecraft, server, shots);
                return;
            }
            Shot shot = current;
            if (shot.campfire()) {
                BlockPos fire = campfireAt;
                if (fire == null) {
                    return;
                }
                shot = shot.from(fire.getX() + 1.5, fire.getY() + 0.4, fire.getZ() - 2.2, fire.getX() + 1.5,
                        fire.getY() + 0.3, fire.getZ() + 0.5);
            }
            shot = shot.from(shot.x(), shot.y() + lift, shot.z(), shot.fx(), shot.fy(), shot.fz());
            minecraft.player.moveTo(shot.x(), shot.y(), shot.z(), shot.yaw(), shot.pitch());
            minecraft.player.setDeltaMovement(0, 0, 0);
            waited++;
            // Climb until nothing stands between the camera and most of the way to what it frames.
            if (!shot.campfire() && lift < 120 && minecraft.level.hasChunk(Mth.floor(shot.x()) >> 4, Mth.floor(shot.z()) >> 4)) {
                net.minecraft.world.phys.Vec3 eye = minecraft.player.getEyePosition();
                net.minecraft.world.phys.Vec3 target = eye.lerp(new net.minecraft.world.phys.Vec3(shot.fx(), shot.fy(), shot.fz()), 0.7);
                var hit = minecraft.level.clip(new net.minecraft.world.level.ClipContext(eye, target,
                        net.minecraft.world.level.ClipContext.Block.VISUAL, net.minecraft.world.level.ClipContext.Fluid.NONE,
                        minecraft.player));
                if (hit.getType() != net.minecraft.world.phys.HitResult.Type.MISS) {
                    lift += 3;
                    readyTicks = 0;
                    return;
                }
            }
            BlockPos focus = BlockPos.containing(shot.fx(), shot.fy(), shot.fz());
            boolean ready = minecraft.level.hasChunk(focus.getX() >> 4, focus.getZ() >> 4)
                    && minecraft.levelRenderer.isSectionCompiled(focus)
                    && minecraft.levelRenderer.hasRenderedAllSections();
            if (waited >= GIVE_UP || waited >= SETTLE && ready && settledFor(ready) >= 40) {
                AlteredAether.LOGGER.info("Tour: shooting {} from {} at time {}, {} ticks; client height {}..{}", shot.name(),
                        minecraft.player.position(), minecraft.level.getDayTime(), waited,
                        minecraft.level.getMinBuildHeight(), minecraft.level.getMaxBuildHeight());
                if (campfireAt != null) {
                    AlteredAether.LOGGER.info("Tour: client sees {} with {}", minecraft.level.getBlockState(campfireAt),
                            minecraft.level.getBlockEntity(campfireAt));
                }
                // Vanilla makes the screenshots folder but not one inside it.
                new java.io.File(minecraft.gameDirectory, "screenshots/tour").mkdirs();
                Screenshot.grab(minecraft.gameDirectory, "tour/" + shot.name() + ".png", minecraft.getMainRenderTarget(),
                        message -> AlteredAether.LOGGER.info("Tour: {} after {} ticks", message.getString(), waited));
                next(minecraft, server, shots);
            }
        }

        private static int readyTicks;

        /** How many ticks in a row everything has been drawn: the last chunks can still be arriving. */
        private static int settledFor(boolean ready) {
            readyTicks = ready ? readyTicks + 1 : 0;
            return readyTicks;
        }

        private static void next(Minecraft minecraft, MinecraftServer server, List<Shot> shots) {
            index++;
            waited = 0;
            readyTicks = 0;
            lift = 0;
            if (index >= shots.size()) {
                plan = null;
                TempestStorms.force(-1);
                minecraft.stop();
                return;
            }
            current = shots.get(index);
            campfireAt = null;
            minecraft.options.hideGui = true;
            if (server != null) {
                Shot shot = current;
                server.execute(() -> setUp(server, shot));
            }
        }

        /** The time of day, the storm, and the campfire, set up on the integrated server for a shot. */
        private static void setUp(MinecraftServer server, Shot shot) {
            ServerLevel aether = server.getLevel(THE_AETHER);
            if (aether == null) {
                return;
            }
            aether.setDayTime(shot.time());
            TempestStorms.force(shot.storm() ? 1 : -1);
            if (shot.campfire()) {
                // A little stage of holystone in open sky, so nothing can stand in the camera's way.
                BlockPos ground = new BlockPos((int) shot.x(), Integer.getInteger("altered_aether.tour.stage", 200), (int) shot.z());
                net.minecraft.world.level.block.state.BlockState floor = net.minecraft.core.registries.BuiltInRegistries.BLOCK
                        .get(ResourceLocation.parse("aether:holystone")).defaultBlockState();
                for (int dx = -4; dx <= 6; dx++) {
                    for (int dz = -6; dz <= 4; dz++) {
                        aether.setBlockAndUpdate(ground.offset(dx, -1, dz), floor);
                    }
                }
                aether.setBlockAndUpdate(ground, AARegistries.AMBROSIUM_CAMPFIRE.get().defaultBlockState());
                aether.setBlockAndUpdate(ground.east(2), net.minecraft.world.level.block.Blocks.CAMPFIRE
                        .defaultBlockState());
                campfireAt = ground;
                AlteredAether.LOGGER.info("Tour: campfire at {}: {}", ground.toShortString(), aether.getBlockState(ground));
            }
        }

        private Client() {
        }
    }

    private Tour() {
    }
}
