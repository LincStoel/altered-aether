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

import com.aetherteam.aether.block.AetherBlocks;
import com.linc.alteredaether.AlteredAether;
import com.linc.alteredaether.block.PhasedAercloudBlock;
import com.linc.alteredaether.compat.cloud.ShipRiders;
import com.linc.alteredaether.config.AACommonConfig;
import com.linc.alteredaether.registry.AARegistries;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * A scripted look at phased aerclouds in a running client, the parts a GameTest can't see: with the system property
 * {@code altered_aether.cloud_tour} set (the client run's {@code -PaaCloudTour=<label>}, which needs Sable), it
 * quick-plays the world "tour" and shoots {@code screenshots/clouds_<label>/NN_name.png}:
 *
 * <ul>
 *   <li>a gallery, with ticking frozen: every cloud in a row, its eight phased steps from the faintest, then the cloud
 *   itself, against a dark wall;</li>
 *   <li>a Sable deck driven sideways through a bank of cold aercloud with a slab of blue in it, the player standing on
 *   the open deck: from the deck inside the bank, then the mouth of the tunnel it left from a few blocks out, then the
 *   same view once the tunnel has firmed up.</li>
 * </ul>
 *
 * <p>The player jumps once inside the bank. What it measures it logs under "Cloud tour": how far the player strays from
 * where they stand on the deck while it is in the bank (a cloud shoving them shows up here), whether both sides count
 * them as riding, and whether they land back on the deck after the jump. Then it quits.
 */
public final class CloudTour {
    private static final String LABEL = System.getProperty("altered_aether.cloud_tour", "");
    private static final ResourceKey<Level> THE_AETHER =
            ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath("aether", "the_aether"));
    /** High above the islands, away from the other tours' stages. */
    private static final int STAGE_Y = 285, STAGE_OFFSET = 700;
    private static final String SHIP_NAME = "cloud_tour";

    /** Where the bank starts and ends along x, relative to the ride's origin; the deck starts just short of it. */
    private static final int BANK_START = 10, BANK_END = 22, BLUE_START = 17, BLUE_END = 18;
    /** The deck's centre, relative to the ride's origin, at which things happen. */
    private static final double INSIDE_AT = 13.5, JUMP_AT = 15.5, LOOK_BACK_AT = 28;
    private static final double SPEED = 4;

    /** The gallery's origin, on its floor, and the ride's. Set on the server at login. */
    private static volatile BlockPos gallery, ride;
    /** Set by the server for the client: the deck's position and the player's offset from it, and whether it counts
     * them as riding. */
    private static volatile Vec3 shipPos, serverOffset;
    private static volatile boolean serverRiding, driving;
    private static volatile double shipStartX = Double.NaN;

    private CloudTour() {
    }

    private static boolean on() {
        return !LABEL.isBlank();
    }

    private static void log(String message, Object... args) {
        AlteredAether.LOGGER.info("Cloud tour: " + message, args);
    }

    /** The deck's centre along x, relative to the ride's origin. */
    private static double deckX() {
        Vec3 pos = shipPos;
        return pos == null || Double.isNaN(shipStartX) ? Double.NaN : -2 + (pos.x - shipStartX);
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Server: the stages and the ship

    @EventBusSubscriber(modid = AlteredAether.MOD_ID)
    public static final class Server {
        @SubscribeEvent
        public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
            if (!on() || !(event.getEntity() instanceof ServerPlayer player)) {
                return;
            }
            if (!ModList.get().isLoaded("sable")) {
                AlteredAether.LOGGER.error("Cloud tour: needs Sable");
                return;
            }
            MinecraftServer server = player.getServer();
            ServerLevel aether = server.getLevel(THE_AETHER);
            if (aether == null) {
                AlteredAether.LOGGER.error("Cloud tour: no Aether");
                return;
            }
            WithSable.register();
            BlockPos spawn = aether.getSharedSpawnPos();
            BlockPos g = new BlockPos(spawn.getX(), STAGE_Y, spawn.getZ() + STAGE_OFFSET);
            BlockPos r = g.offset(60, 0, 0);
            aether.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
            aether.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
            aether.setDayTime(6000);
            player.getInventory().clearContent();
            player.setGameMode(GameType.CREATIVE);
            player.getAbilities().flying = true;
            player.onUpdateAbilities();
            player.teleportTo(aether, g.getX() + 8.5, g.getY() + 4, g.getZ() + 14.5, 180, 0);
            WithSable.removeShips(aether);
            clear(aether, g.offset(-4, -3, -4), g.offset(20, 14, 18));
            clear(aether, r.offset(-8, -4, -10), r.offset(36, 10, 10));
            server.tickRateManager().setFrozen(true);
            buildGallery(aether, g);
            buildBank(aether, r);
            gallery = g;
            ride = r;
            log("gallery at {}, ride at {}; fade {} s", g.toShortString(), r.toShortString(),
                    AACommonConfig.PHASED_AERCLOUD_SECONDS.get());
        }

        @SubscribeEvent
        public static void onServerTick(ServerTickEvent.Post event) {
            if (!on() || ride == null) {
                return;
            }
            ServerLevel aether = event.getServer().getLevel(THE_AETHER);
            ServerPlayer player = event.getServer().getPlayerList().getPlayers().isEmpty() ? null
                    : event.getServer().getPlayerList().getPlayers().get(0);
            Vec3 ship = aether == null ? null : WithSable.shipPos(aether);
            shipPos = ship;
            if (ship != null && player != null) {
                serverOffset = player.position().subtract(ship);
                serverRiding = ShipRiders.onShip(player);
            }
        }

        private Server() {
        }
    }

    private static void clear(ServerLevel level, BlockPos from, BlockPos to) {
        for (Entity entity : level.getEntitiesOfClass(Entity.class, AABB.encapsulatingFullBlocks(from, to),
                e -> !(e instanceof ServerPlayer))) {
            entity.discard();
        }
        for (BlockPos pos : BlockPos.betweenClosed(from, to)) {
            if (!level.getBlockState(pos).isAir()) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            }
        }
    }

    /** A row per cloud: its eight steps from the faintest along x, then the cloud itself, before a dark wall. */
    private static void buildGallery(ServerLevel level, BlockPos g) {
        int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
        for (int x = -2; x <= 18; x++) {
            for (int y = -1; y <= 12; y++) {
                level.setBlock(g.offset(x, y, -1), Blocks.BLACK_CONCRETE.defaultBlockState(), flags);
            }
            for (int z = -1; z <= 2; z++) {
                level.setBlock(g.offset(x, -1, z), Blocks.BLACK_CONCRETE.defaultBlockState(), flags);
            }
        }
        PhasedAercloudBlock phased = AARegistries.PHASED_AERCLOUD.get();
        PhasedAercloudBlock.Variant[] variants = PhasedAercloudBlock.Variant.values();
        for (int row = 0; row < variants.length; row++) {
            PhasedAercloudBlock.Variant variant = variants[row];
            int y = 10 - row * 2;
            for (int fade = 0; fade <= PhasedAercloudBlock.MAX_FADE; fade++) {
                level.setBlock(g.offset(fade * 2, y, 0), phased.defaultBlockState()
                        .setValue(PhasedAercloudBlock.VARIANT, variant).setValue(PhasedAercloudBlock.FADE, fade), flags);
            }
            if (variant.block() != null) {
                level.setBlock(g.offset(16, y, 0), variant.block().defaultBlockState(), flags);
            }
        }
    }

    /** A bank of cold aercloud with a slab of blue through its middle, and a deck of stone just short of it. */
    private static void buildBank(ServerLevel level, BlockPos r) {
        int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
        BlockState cold = AetherBlocks.COLD_AERCLOUD.get().defaultBlockState();
        BlockState blue = AetherBlocks.BLUE_AERCLOUD.get().defaultBlockState();
        for (int x = BANK_START; x <= BANK_END; x++) {
            for (int y = -2; y <= 6; y++) {
                for (int z = -6; z <= 6; z++) {
                    level.setBlock(r.offset(x, y, z), x >= BLUE_START && x <= BLUE_END ? blue : cold, flags);
                }
            }
        }
        for (int x = -4; x <= 0; x++) {
            for (int z = -2; z <= 2; z++) {
                level.setBlock(r.offset(x, 0, z), Blocks.STONE.defaultBlockState(), flags);
            }
        }
    }

    /** The ride starts: the deck is made a Sable body and the player set on it, not flying. */
    private static void startRide(ServerLevel level) {
        level.getServer().tickRateManager().setFrozen(false);
        BlockPos r = ride;
        if (!WithSable.assemble(level, r)) {
            log("could not make the deck a Sable body");
            return;
        }
        ServerPlayer player = level.getServer().getPlayerList().getPlayers().get(0);
        player.getAbilities().flying = false;
        player.onUpdateAbilities();
        player.teleportTo(level, r.getX() - 1.5, r.getY() + 1, r.getZ() + 0.5, -90, 15);
        log("deck made, player on it");
    }

    /** Everything that touches Sable, loaded only when it is installed. */
    private static final class WithSable {
        private static boolean registered;
        private static dev.ryanhcode.sable.sublevel.ServerSubLevel ship;

        static void register() {
            if (!registered) {
                registered = true;
                NeoForge.EVENT_BUS.addListener(WithSable::onPrePhysics);
            }
        }

        static void removeShips(ServerLevel level) {
            var container = dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level);
            if (container == null) {
                return;
            }
            for (var sub : new java.util.ArrayList<>(container.getAllSubLevels())) {
                if (SHIP_NAME.equals(sub.getName())) {
                    container.removeSubLevel(sub, dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason.REMOVED);
                }
            }
            ship = null;
        }

        static boolean assemble(ServerLevel level, BlockPos r) {
            java.util.List<BlockPos> blocks = new java.util.ArrayList<>();
            for (BlockPos pos : BlockPos.betweenClosed(r.offset(-4, 0, -2), r.offset(0, 0, 2))) {
                blocks.add(pos.immutable());
            }
            ship = dev.ryanhcode.sable.api.SubLevelAssemblyHelper.assembleBlocks(level, r.offset(-2, 0, 0), blocks,
                    new dev.ryanhcode.sable.companion.math.BoundingBox3i(r.offset(-5, -1, -3), r.offset(1, 1, 3)));
            if (ship == null) {
                return false;
            }
            ship.setName(SHIP_NAME);
            return true;
        }

        static Vec3 shipPos(ServerLevel level) {
            if (ship == null || ship.isRemoved()) {
                return null;
            }
            var at = ship.logicalPose().position();
            return new Vec3(at.x(), at.y(), at.z());
        }

        /** Holds the deck level and still, or drives it along x at {@link #SPEED} while the tour says so. */
        private static void onPrePhysics(dev.ryanhcode.sable.neoforge.event.ForgeSablePrePhysicsTickEvent event) {
            var sub = ship;
            if (sub == null || sub.isRemoved() || event.getPhysicsSystem().getLevel() != sub.getLevel()) {
                return;
            }
            var handle = event.getPhysicsSystem().getPhysicsHandle(sub);
            if (handle == null || !handle.isValid()) {
                return;
            }
            var velocity = handle.getLinearVelocity(new org.joml.Vector3d());
            var spin = handle.getAngularVelocity(new org.joml.Vector3d());
            double target = driving ? SPEED : 0;
            handle.addLinearAndAngularVelocity(new org.joml.Vector3d(target - velocity.x, -velocity.y, -velocity.z),
                    spin.negate());
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Client

    private enum Stage { WAIT, GALLERY, BOARD, RIDE, LOOK_BACK, FIRM, DONE }

    @EventBusSubscriber(modid = AlteredAether.MOD_ID, value = Dist.CLIENT)
    public static final class Client {
        private static Stage stage = Stage.WAIT;
        private static int waited, settled, shot, framesHere;
        private static Vec3 wanted;
        // the measurements
        private static Vec3 baseline;
        private static double strayY, strayXZ;
        private static int jumpTick = -1, ridingTicks, notRidingTicks, clientNotRiding, sampled;
        private static boolean landedAgain;
        private static double peakJump;

        @SubscribeEvent
        public static void onRender(RenderLevelStageEvent event) {
            Vec3 want = wanted;
            if (want != null && event.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL
                    && event.getCamera().getPosition().distanceToSqr(want) < 1.0E-4) {
                framesHere++;
            }
        }

        @SubscribeEvent
        public static void onTick(ClientTickEvent.Post event) {
            Minecraft mc = Minecraft.getInstance();
            BlockPos g = gallery;
            if (!on() || g == null || mc.player == null || mc.level == null || !mc.level.dimension().equals(THE_AETHER)) {
                return;
            }
            MinecraftServer server = mc.getSingleplayerServer();
            mc.options.hideGui = true;
            waited++;
            switch (stage) {
                case WAIT -> {
                    boolean ready = mc.levelRenderer.hasRenderedAllSections()
                            && mc.level.getBlockState(g.offset(0, -1, 0)).is(Blocks.BLACK_CONCRETE);
                    settled = ready ? settled + 1 : 0;
                    hold(mc, Vec3.atLowerCornerOf(g).add(8.5, 5.5, 14.5), Vec3.atLowerCornerOf(g).add(8.5, 5.5, 0.5));
                    if (settled >= 80) {
                        go(Stage.GALLERY);
                    }
                }
                case GALLERY -> {
                    hold(mc, Vec3.atLowerCornerOf(g).add(8.5, 5.5, 14.5), Vec3.atLowerCornerOf(g).add(8.5, 5.5, 0.5));
                    if (waited > 10 && framesHere >= 3) {
                        shoot(mc, "gallery");
                        server.execute(() -> startRide(server.getLevel(THE_AETHER)));
                        go(Stage.BOARD);
                    }
                }
                case BOARD -> {
                    wanted = null;
                    look(mc, -90, 10);
                    Vec3 offset = playerOffset(mc);
                    if (waited == 60 && offset != null) {
                        baseline = offset;
                        log("on the deck: offset {}, riding (client {}, server {})", fmt(offset), clientRiding(mc),
                                serverRiding);
                        driving = true;
                        shipStartX = shipPos.x;
                        go(Stage.RIDE);
                    } else if (waited > 400) {
                        log("never got onto the deck");
                        finish(mc, server);
                    }
                }
                case RIDE -> {
                    look(mc, -90, 10);
                    measure(mc);
                    double deck = deckX();
                    if (shot == 1 && deck >= INSIDE_AT) {
                        BlockPos eye = BlockPos.containing(mc.player.getEyePosition());
                        log("inside the bank: eye in {}, sky light {}, block light {}",
                                mc.level.getBlockState(eye), mc.level.getBrightness(LightLayer.SKY, eye),
                                mc.level.getBrightness(LightLayer.BLOCK, eye));
                        shoot(mc, "inside_the_bank");
                    }
                    if (jumpTick < 0 && deck >= JUMP_AT && mc.player.onGround()) {
                        mc.player.jumpFromGround();
                        jumpTick = waited;
                        log("jumped at deck x {}", String.format("%.1f", deck));
                    }
                    if (deck >= LOOK_BACK_AT) {
                        report(mc);
                        driving = false;
                        server.execute(() -> {
                            ServerPlayer player = server.getPlayerList().getPlayers().get(0);
                            player.getAbilities().flying = true;
                            player.onUpdateAbilities();
                        });
                        mc.player.getAbilities().flying = true;
                        go(Stage.LOOK_BACK);
                    } else if (waited > 600) {
                        log("the deck never got through the bank (at x {})", String.format("%.1f", deck));
                        finish(mc, server);
                    }
                }
                case LOOK_BACK -> {
                    // the tunnel's mouth in the bank's near face, from a few blocks out
                    BlockPos r = ride;
                    hold(mc, Vec3.atLowerCornerOf(r).add(3.5, 2.6, 0.5), Vec3.atLowerCornerOf(r).add(16.5, 1, 0.5));
                    if (shot == 2 && waited > 10 && framesHere >= 3) {
                        shoot(mc, "tunnel_entrance");
                    }
                    int firm = (int) (AACommonConfig.PHASED_AERCLOUD_SECONDS.get() * 20) + 60;
                    if (waited >= firm && framesHere >= 3) {
                        shoot(mc, "tunnel_firm");
                        finish(mc, server);
                    }
                }
                default -> {
                }
            }
        }

        private static void go(Stage next) {
            stage = next;
            waited = 0;
            log("stage {}", next);
        }

        /** Stands the camera at {@code cam} looking at {@code at}, flying, and counts frames drawn from there. */
        private static void hold(Minecraft mc, Vec3 cam, Vec3 at) {
            double dx = at.x - cam.x, dy = at.y - cam.y, dz = at.z - cam.z;
            float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90;
            float pitch = (float) -(Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * Mth.RAD_TO_DEG);
            mc.player.moveTo(cam.x, cam.y - mc.player.getEyeHeight(), cam.z, yaw, pitch);
            mc.player.setYHeadRot(yaw);
            mc.player.setDeltaMovement(Vec3.ZERO);
            if (wanted == null || wanted.distanceToSqr(cam) > 1.0E-6) {
                wanted = cam;
                framesHere = 0;
            }
        }

        /** Turns the player's view without moving them. */
        private static void look(Minecraft mc, float yaw, float pitch) {
            mc.player.setYRot(yaw);
            mc.player.setYHeadRot(yaw);
            mc.player.setXRot(pitch);
        }

        private static boolean clientRiding(Minecraft mc) {
            return ShipRiders.onShip(mc.player);
        }

        /** The client player's offset from the deck's position as the server last saw it. */
        private static Vec3 playerOffset(Minecraft mc) {
            Vec3 ship = shipPos;
            return ship == null ? null : mc.player.position().subtract(ship);
        }

        private static void measure(Minecraft mc) {
            Vec3 offset = playerOffset(mc);
            double deck = deckX();
            if (offset == null || baseline == null || Double.isNaN(deck)) {
                return;
            }
            boolean inBank = deck + 2.5 >= BANK_START && deck - 2.5 <= BANK_END;
            boolean jumping = jumpTick >= 0 && waited - jumpTick < 40;
            if (jumping) {
                peakJump = Math.max(peakJump, offset.y - baseline.y);
            } else if (jumpTick >= 0 && !landedAgain && Math.abs(offset.y - baseline.y) < 0.15) {
                landedAgain = true;
            }
            if (inBank) {
                sampled++;
                if (!jumping) {
                    strayY = Math.max(strayY, Math.abs(offset.y - baseline.y));
                }
                strayXZ = Math.max(strayXZ, Math.hypot(offset.x - baseline.x, offset.z - baseline.z));
                if (serverRiding) {
                    ridingTicks++;
                } else {
                    notRidingTicks++;
                }
                if (!clientRiding(mc)) {
                    clientNotRiding++;
                }
            }
            if (waited % 10 == 0) {
                log("deck x {}, player offset {} (server {}), riding client {} server {}, on ground {}",
                        String.format("%.1f", deck), fmt(offset.subtract(baseline)),
                        serverOffset == null ? "?" : fmt(serverOffset.subtract(baseline)), clientRiding(mc),
                        serverRiding, mc.player.onGround());
            }
        }

        private static void report(Minecraft mc) {
            Vec3 offset = playerOffset(mc);
            log("summary: {} ticks in the bank; strayed up to {} vertically (not counting the jump) and {} across the deck;"
                            + " jump peaked {} above the deck and {}; server counted them riding {} ticks, not {}; client not"
                            + " riding {} ticks; at the end the player is {} from where they started on the deck",
                    sampled, String.format("%.2f", strayY), String.format("%.2f", strayXZ), String.format("%.2f", peakJump),
                    landedAgain ? "landed back on the deck" : "never landed back on the deck", ridingTicks,
                    notRidingTicks, clientNotRiding, offset == null || baseline == null ? "?" : fmt(offset.subtract(baseline)));
        }

        private static void shoot(Minecraft mc, String name) {
            String dir = "clouds_" + LABEL;
            new java.io.File(mc.gameDirectory, "screenshots/" + dir).mkdirs();
            String file = dir + "/" + String.format("%02d_%s.png", shot++, name);
            log("shooting {}", name);
            Screenshot.grab(mc.gameDirectory, file, mc.getMainRenderTarget(),
                    message -> AlteredAether.LOGGER.info("Cloud tour: {}", message.getString()));
        }

        private static void finish(Minecraft mc, MinecraftServer server) {
            stage = Stage.DONE;
            driving = false;
            if (server != null) {
                server.execute(() -> {
                    ServerLevel aether = server.getLevel(THE_AETHER);
                    if (aether != null) {
                        WithSable.removeShips(aether);
                    }
                });
            }
            log("done");
            gallery = null;
            ride = null;
            mc.stop();
        }

        private static String fmt(Vec3 v) {
            return String.format("(%.2f, %.2f, %.2f)", v.x, v.y, v.z);
        }

        private Client() {
        }
    }
}
