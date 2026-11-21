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
import com.linc.alteredaether.entity.TamedAerwhale;
import com.linc.alteredaether.registry.AARegistries;
import com.linc.alteredaether.storm.TempestStorms;
import com.linc.alteredaether.worldgen.grove.Giant;
import com.linc.alteredaether.worldgen.grove.GiantType;
import com.linc.alteredaether.worldgen.grove.GroveField;
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
 * crown under a forced storm, {@code campfire} for an ambrosium campfire at night, {@code whale} for a saddled
 * tamed aerwhale and its calf, then the player riding it, and {@code leap} for the rider leaping off it at full charge,
 * logging their height each tick rather than taking a screenshot), a dev client that quick-plays
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
                boolean campfire, boolean ground) {
        Shot(String name, double x, double y, double z, double fx, double fy, double fz, long time, boolean storm,
             boolean campfire) {
            this(name, x, y, z, fx, fy, fz, time, storm, campfire, false);
        }

        float yaw() {
            return (float) (Mth.atan2(fz - z, fx - x) * Mth.RAD_TO_DEG) - 90;
        }

        float pitch() {
            double dx = fx - x, dz = fz - z;
            return (float) -(Mth.atan2(fy - y, Math.sqrt(dx * dx + dz * dz)) * Mth.RAD_TO_DEG);
        }

        Shot from(double cx, double cy, double cz, double tx, double ty, double tz) {
            return new Shot(name, cx, cy, cz, tx, ty, tz, time, storm, campfire, ground);
        }
    }

    /** The planned shots, published by the server side once the stops are found. */
    private static volatile List<Shot> plan;
    /** Where the last campfire was put, once the server has found ground for it. */
    private static volatile BlockPos campfireAt;
    /** The tamed aerwhale posed for the whale shots, whose names all start with "whale". */
    private static TamedAerwhale whale;

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
                if (stop.startsWith("biome:")) {
                    biomeShots(aether, stop.substring(6), spawn, shots);
                    continue;
                }
                if (stop.startsWith("giant:")) {
                    giantShots(stop.substring(6), spawn, shots);
                    continue;
                }
                if (stop.equals("whale")) {
                    whaleShots(spawn, shots);
                    continue;
                }
                if (stop.startsWith("sea")) {
                    seaShots(stop, spawn, shots);
                    continue;
                }
                if (stop.startsWith("skirt")) {
                    skirtShots(stop, spawn, shots);
                    continue;
                }
                if (stop.equals("tarn")) {
                    tarnShots(spawn, shots);
                    continue;
                }
                if (stop.equals("highlands")) {
                    highlandsShots(spawn, shots);
                    continue;
                }
                if (stop.equals("stack")) {
                    stackShots(aether, spawn, shots);
                    continue;
                }
                if (stop.equals("leap")) {
                    double x = spawn.getX() + 0.5, y = Integer.getInteger("altered_aether.tour.stage", 200) + 2, z = spawn.getZ() + 0.5;
                    shots.add(new Shot("whale_leap", x, y + 8, z, x, y, z + 20, 6000, false, false));
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
                boolean tall = type == LandmarkType.TEMPEST_SPIRES || type == LandmarkType.FROZEN_SPIRE
                        || type == LandmarkType.GRANDFATHER;
                int top = site.visitY() == Integer.MIN_VALUE ? 70 : site.visitY();
                // A spire's visit height is its tip; the Grandfather's is its foot, so look at its middle.
                double focus = type == LandmarkType.GRANDFATHER ? top + 60 : tall ? top - 70 : top - 6;
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

        /**
         * A grove giant from outside, and from inside its hollow or tunnel when it has one, looking out the way in.
         */
        private static void giantShots(String name, BlockPos spawn, List<Shot> shots) {
            GroveField field = GroveField.get(THE_AETHER);
            if (name.equals("mire")) {
                // A swamp lake from low over the water, looking across it at its giant, and from above.
                var mire = field == null ? null : field.nearestMire(spawn.getX(), spawn.getZ(), 30000).orElse(null);
                if (mire == null) {
                    AlteredAether.LOGGER.error("Tour: no mire found");
                    return;
                }
                AlteredAether.LOGGER.info("Tour: mire at {} {}", mire.x(), mire.z());
                double out = mire.radius() * 1.05;
                shots.add(new Shot("mire", mire.x() + out * 0.7, mire.water() + 3, mire.z() + out * 0.7, mire.x(),
                        mire.water() + 8, mire.z(), 6000, false, false));
                shots.add(new Shot("mire_above", mire.x() + out, mire.water() + out, mire.z() - out, mire.x(),
                        mire.water(), mire.z(), 6000, false, false));
                return;
            }
            GiantType type = GiantType.valueOf(name.toUpperCase(Locale.ROOT));
            Giant site = field == null ? null : field.nearest(type, spawn.getX(), spawn.getZ(), 30000).orElse(null);
            if (site == null) {
                AlteredAether.LOGGER.error("Tour: no {} found", type.id());
                return;
            }
            AlteredAether.LOGGER.info("Tour: {} at {} {}", type.id(), site.x(), site.z());
            double distance = Math.max(36, site.extent() * 1.1);
            double ex = site.x() + distance * 0.7, ez = site.z() + distance * 0.7;
            shots.add(new Shot("giant_" + type.id(), ex, site.base() + distance * 0.5, ez, site.x(), site.base() + 12,
                    site.z(), 6000, false, false));
            List<BlockPos> inside = site.points("hollow_floor");
            List<BlockPos> ways = site.points("entrance");
            if (inside.isEmpty() && site.points("tunnel").size() == 2) {
                inside = List.of(site.points("tunnel").get(0));
                ways = List.of(site.points("tunnel").get(1));
            }
            if (!inside.isEmpty()) {
                BlockPos in = inside.getFirst();
                BlockPos out = ways.isEmpty() ? in.offset(8, 0, 0) : ways.getFirst();
                shots.add(new Shot("giant_" + type.id() + "_inside", in.getX() + 0.5, in.getY() + 1.6, in.getZ() + 0.5,
                        out.getX() + 0.5, in.getY() + 1.6, out.getZ() + 0.5, 6000, false, false));
            }
        }

        /**
         * Two views of a biome from near its middle: from high above, looking down across it, and from the ground,
         * looking out over it.
         */
        private static void biomeShots(ServerLevel aether, String name, BlockPos spawn, List<Shot> shots) {
            ResourceLocation id = name.contains(":") ? ResourceLocation.parse(name) : AlteredAether.id(name);
            var found = aether.findClosestBiome3d(b -> b.is(id), spawn, 24000, 32, 64);
            if (found == null) {
                AlteredAether.LOGGER.error("Tour: no {} found", id);
                return;
            }
            BlockPos middle = middle(aether, id, found.getFirst());
            AlteredAether.LOGGER.info("Tour: {} middle at {} {}", id, middle.getX(), middle.getZ());
            String label = "biome_" + id.getPath();
            int x = middle.getX(), z = middle.getZ();
            // Both shots stand on the ground found near the middle; the aerial one rises above and behind it.
            shots.add(new Shot(label + "_aerial", x, 100, z, x, 90, z, 6000, false, false, true));
            // The ground shot finds its own footing once the chunks are there, and looks out its most open side.
            shots.add(new Shot(label + "_ground", x, 100, z, x, 90, z - 120, 6000, false, false, true));
        }

        /** The nearest frozen tarn: low across the ice, and from above. */
        private static void tarnShots(BlockPos spawn, List<Shot> shots) {
            var field = com.linc.alteredaether.worldgen.basin.BasinField.get(THE_AETHER);
            com.linc.alteredaether.worldgen.basin.BasinSite found = null;
            int cell = field == null ? 1 : field.lakeCell();
            int ox = Math.floorDiv(spawn.getX(), cell), oz = Math.floorDiv(spawn.getZ(), cell);
            for (int r = 0; r < 60 && field != null && found == null; r++) {
                for (int cx = ox - r; cx <= ox + r && found == null; cx++) {
                    for (int cz = oz - r; cz <= oz + r && found == null; cz++) {
                        if (Math.max(Math.abs(cx - ox), Math.abs(cz - oz)) == r) {
                            found = field.lakeInCell(cx, cz).filter(site ->
                                    site.type() == com.linc.alteredaether.worldgen.basin.BasinType.FROZEN_LAKE)
                                    .orElse(null);
                        }
                    }
                }
            }
            if (found == null) {
                AlteredAether.LOGGER.error("Tour: no frozen tarn found");
                return;
            }
            AlteredAether.LOGGER.info("Tour: frozen tarn at {} {}", found.x(), found.z());
            double x = found.x() + 0.5, z = found.z() + 0.5, w = found.waterLevel(), out = found.radius() * 0.9;
            shots.add(new Shot("tarn", x + out, w + 6, z + out * 0.4, x, w, z, 6000, false, false));
            shots.add(new Shot("tarn_above", x - out, w + out, z - out, x, w, z, 6000, false, false));
        }

        /** The Highlands, from below among the main islands looking up at them, among them, and from above. */
        private static void highlandsShots(BlockPos spawn, List<Shot> shots) {
            var field = com.linc.alteredaether.worldgen.density.BiomeEdgeField.get(THE_AETHER);
            if (field == null) {
                AlteredAether.LOGGER.error("Tour: no biome edge field");
                return;
            }
            var allowed = field.solver(com.linc.alteredaether.worldgen.region.BiomeGroups.HIGHLANDS, 32);
            int x = spawn.getX(), z = spawn.getZ();
            outer:
            for (int r = 0; r < 100; r++) {
                for (int i = -r; i <= r; i++) {
                    for (int[] p : new int[][]{{i, -r}, {i, r}, {-r, i}, {r, i}}) {
                        int px = spawn.getX() + p[0] * 64, pz = spawn.getZ() + p[1] * 64;
                        if (allowed.inside(px, pz, 1) >= 31) {
                            x = px;
                            z = pz;
                            break outer;
                        }
                    }
                }
            }
            AlteredAether.LOGGER.info("Tour: Highlands at {} {}", x, z);
            // level with the islets, looking along them; then from just under them, looking out and up
            shots.add(new Shot("highlands_among", x, 205, z, x + 100, 198, z + 40, 6000, false, false));
            shots.add(new Shot("highlands_below", x, 150, z, x + 100, 190, z - 30, 6000, false, false));
            shots.add(new Shot("highlands_far", x - 60, 240, z - 60, x + 120, 190, z + 120, 6000, false, false));
        }

        /** One of the plateau's sea stacks near the nearest plateau (one with a pool if there is one), and above. */
        private static void stackShots(ServerLevel aether, BlockPos spawn, List<Shot> shots) {
            var field = com.linc.alteredaether.worldgen.landmark.StackField.get(THE_AETHER);
            var plateau = aether.findClosestBiome3d(b -> b.is(com.linc.alteredaether.worldgen.AABiomes.AETHER_PLATEAU),
                    spawn, 24000, 32, 64);
            BlockPos from = plateau == null ? spawn : plateau.getFirst();
            com.linc.alteredaether.worldgen.landmark.StackField.Stack found = null;
            for (int r = 128; r <= 1024 && field != null && found == null; r *= 2) {
                var stacks = field.touching(from.getX() - r, from.getZ() - r, from.getX() + r, from.getZ() + r);
                found = stacks.stream().filter(st -> st.pond() > 0).findFirst()
                        .orElse(stacks.isEmpty() ? null : stacks.getFirst());
            }
            if (found == null) {
                AlteredAether.LOGGER.error("Tour: no stack found");
                return;
            }
            AlteredAether.LOGGER.info("Tour: stack at {} {} {}", found.x(), found.top(), found.z());
            double x = found.x() + 0.5, z = found.z() + 0.5, top = found.top();
            shots.add(new Shot("stack", x + 34, top + 6, z + 20, x, top - 12, z, 6000, false, false));
            shots.add(new Shot("stack_above", x - 18, top + 30, z - 18, x, top, z, 6000, false, false));
        }

        /**
         * A saddled aerwhale held still in open sky over spawn, facing south, from each side and from above to see the
         * saddle sit on its back; then the player riding it, from behind in third person.
         */
        private static void whaleShots(BlockPos spawn, List<Shot> shots) {
            double x = spawn.getX() + 0.5, y = Integer.getInteger("altered_aether.tour.stage", 200), z = spawn.getZ() + 0.5;
            double fy = y + 2;
            shots.add(new Shot("whale_side", x + 12, fy, z + 0.01, x, fy, z, 6000, false, false));
            shots.add(new Shot("whale_front", x - 7, y + 6, z + 12, x, fy, z, 6000, false, false));
            shots.add(new Shot("whale_top", x + 0.01, y + 16, z + 0.5, x, fy, z + 0.5, 6000, false, false));
            shots.add(new Shot("whale_rear", x + 5, y + 5, z - 14, x, fy, z, 6000, false, false));
            shots.add(new Shot("whale_ride", x, y + 8, z, x, fy, z + 20, 6000, false, false));
        }

        /**
         * The cloud skirt round a Highlands islet: the nearest skirted rim to a point well away from spawn ({@code skirt} or
         * {@code skirt:dx:dz} for the offset), so its chunks are new and wear the skirt, framed from four sides
         * just above the rim and once from high up.
         */
        private static void skirtShots(String stop, BlockPos spawn, List<Shot> shots) {
            String[] parts = stop.split(":");
            int dx = parts.length == 3 ? Integer.parseInt(parts[1]) : 6000;
            int dz = parts.length == 3 ? Integer.parseInt(parts[2]) : 6000;
            com.linc.alteredaether.worldgen.skirt.SkirtField field =
                    com.linc.alteredaether.worldgen.skirt.SkirtField.get(THE_AETHER);
            int[] rim = field == null ? null
                    : field.nearestRim(spawn.getX() + dx, spawn.getZ() + dz, 4000).orElse(null);
            if (rim == null) {
                AlteredAether.LOGGER.error("Tour: no cloud skirt found{}", field == null ? " (no skirt field)" : "");
                return;
            }
            AlteredAether.LOGGER.info("Tour: low rim at {} {} {}", rim[0], rim[1], rim[2]);
            double x = rim[0] + 0.5, y = rim[1], z = rim[2] + 0.5;
            for (int i = 0; i < 4; i++) {
                double angle = Math.PI * 0.25 + i * Math.PI / 2;
                shots.add(new Shot("skirt_" + i, x + Math.cos(angle) * 44, y + 8, z + Math.sin(angle) * 44, x,
                        y - 5, z, 6000, false, false));
            }
            shots.add(new Shot("skirt_above", x + 70, y + 55, z + 70, x, y - 4, z, 6000, false, false));
        }

        /**
         * A cloud sea: {@code sea} or {@code sea:dx:dz} searches outward from spawn plus that offset (default 6000,6000,
         * so its chunks are new) for open sea, then looks across it from four sides at the height of its islands' ground,
         * and down on it from high above.
         */
        private static void seaShots(String stop, BlockPos spawn, List<Shot> shots) {
            String[] parts = stop.split(":");
            int dx = parts.length == 3 ? Integer.parseInt(parts[1]) : 6000;
            int dz = parts.length == 3 ? Integer.parseInt(parts[2]) : 6000;
            com.linc.alteredaether.worldgen.sea.CloudSea sea = com.linc.alteredaether.worldgen.sea.CloudSea.terrain(THE_AETHER);
            int[] found = null;
            for (int r = 0; r < 100 && sea != null && found == null; r++) {
                for (int i = -r; i <= r && found == null; i++) {
                    for (int[] p : new int[][]{{i, -r}, {i, r}, {-r, i}, {r, i}}) {
                        int x = spawn.getX() + dx + p[0] * 128, z = spawn.getZ() + dz + p[1] * 128;
                        if (sea.weight(x, z) > 0.98 && sea.weight(x + 120, z + 120) > 0.98) {
                            found = new int[]{x, z};
                            break;
                        }
                    }
                }
            }
            if (found == null) {
                AlteredAether.LOGGER.error("Tour: no cloud sea found{}", sea == null ? " (no cloud sea)" : "");
                return;
            }
            AlteredAether.LOGGER.info("Tour: cloud sea at {} {}", found[0], found[1]);
            double x = found[0] + 0.5, z = found[1] + 0.5;
            for (int i = 0; i < 4; i++) {
                double angle = Math.PI * 0.25 + i * Math.PI / 2;
                shots.add(new Shot("sea_" + i, x + Math.cos(angle) * 60, 20, z + Math.sin(angle) * 60,
                        x - Math.cos(angle) * 60, -14, z - Math.sin(angle) * 60, 6000, false, false));
            }
            shots.add(new Shot("sea_above", x + 90, 110, z + 90, x, -20, z, 6000, false, false));
        }

        /** Walks from a point toward where the biome is widest, so the shots sit well inside it. */
        private static BlockPos middle(ServerLevel aether, ResourceLocation id, BlockPos start) {
            BlockPos best = start;
            int bestDepth = depth(aether, id, start);
            for (int round = 0; round < 16; round++) {
                BlockPos next = best;
                for (int d = 0; d < 8; d++) {
                    double angle = d * Math.PI / 4;
                    BlockPos candidate = best.offset((int) (Math.cos(angle) * 24), 0, (int) (Math.sin(angle) * 24));
                    int depth = depth(aether, id, candidate);
                    if (depth > bestDepth) {
                        bestDepth = depth;
                        next = candidate;
                    }
                }
                if (next == best) {
                    break;
                }
                best = next;
            }
            return best;
        }

        /** How far the biome reaches from a point in its narrowest direction, up to 192 blocks; 0 outside it. */
        private static int depth(ServerLevel aether, ResourceLocation id, BlockPos pos) {
            var source = aether.getChunkSource().getGenerator().getBiomeSource();
            var sampler = aether.getChunkSource().randomState().sampler();
            int depth = Integer.MAX_VALUE;
            for (int d = 0; d < 8; d++) {
                double angle = d * Math.PI / 4;
                int reach = 0;
                for (int r = 0; r <= 192; r += 8) {
                    int x = pos.getX() + (int) (Math.cos(angle) * r), z = pos.getZ() + (int) (Math.sin(angle) * r);
                    if (!source.getNoiseBiome(net.minecraft.core.QuartPos.fromBlock(x), 16,
                            net.minecraft.core.QuartPos.fromBlock(z), sampler).is(id)) {
                        break;
                    }
                    reach = r;
                }
                depth = Math.min(depth, reach);
            }
            return depth;
        }

        private Server() {
        }
    }

    /** Flies the camera through the plan, a shot at a time, then quits the game. */
    @EventBusSubscriber(modid = AlteredAether.MOD_ID, value = Dist.CLIENT)
    public static final class Client {
        /** Ticks to wait at a new place at least, and at most before shooting regardless. */
        private static final int SETTLE = 60, GIVE_UP = 4000;
        private static int index = -1, waited;
        private static Shot current;
        /** How far the camera has been raised to see over whatever stood in its way. */
        private static double lift;
        /** Where a ground shot found its footing. */
        private static BlockPos footing;

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
            if (shot.ground()) {
                if (footing == null) {
                    footing = footing(minecraft, shot);
                    if (footing == null) {
                        return;
                    }
                }
                double fx = footing.getX() + 0.5, fy = footing.getY(), fz = footing.getZ() + 0.5;
                if (shot.name().endsWith("_aerial")) {
                    shot = shot.from(fx, fy + 55, fz + 95, fx, fy, fz);
                } else {
                    double angle = openest(minecraft, fx, fy + 1.62, fz);
                    shot = shot.from(fx, fy, fz, fx + Math.cos(angle) * 120, fy - 4, fz + Math.sin(angle) * 120);
                }
            }
            shot = shot.from(shot.x(), shot.y() + lift, shot.z(), shot.fx(), shot.fy(), shot.fz());
            if (shot.name().equals("whale_leap") && (leapTick >= 0 || minecraft.player.isPassenger() && ++waited > SETTLE)) {
                if (leapTick < 0) {
                    net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                            new com.linc.alteredaether.entity.AerwhaleEjectPayload(1, net.minecraft.world.phys.Vec3.ZERO));
                }
                AlteredAether.LOGGER.info("Tour leap {}: y {} motion {} riding {} onGround {}", leapTick,
                        String.format("%.3f", minecraft.player.getY()), minecraft.player.getDeltaMovement(),
                        minecraft.player.isPassenger(), minecraft.player.onGround());
                if (++leapTick > 40) {
                    leapTick = -1;
                    next(minecraft, server, shots);
                }
                return;
            }
            if (minecraft.player.isPassenger()) {
                // riding: the whale carries the player, and the camera looks from behind them
                minecraft.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
                minecraft.player.setYRot(shot.yaw() + 25);
                minecraft.player.setXRot(10);
            } else {
                minecraft.player.moveTo(shot.x(), shot.y(), shot.z(), shot.yaw(), shot.pitch());
                minecraft.player.setDeltaMovement(0, 0, 0);
            }
            waited++;
            // Climb until nothing stands between the camera and most of the way to what it frames.
            if (!shot.campfire() && !shot.name().startsWith("whale") && (!shot.ground() || shot.name().endsWith("_aerial")) && lift < 120 && minecraft.level.hasChunk(Mth.floor(shot.x()) >> 4, Mth.floor(shot.z()) >> 4)) {
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
                if (minecraft.player.getVehicle() instanceof TamedAerwhale ridden) {
                    net.minecraft.world.phys.Vec3 offset = minecraft.player.position().subtract(ridden.position());
                    // the rider's feet sit one vehicle attachment below the seat
                    net.minecraft.world.phys.Vec3 seat = ridden.getPassengerRidingPosition(minecraft.player)
                            .subtract(ridden.position()).subtract(minecraft.player.getVehicleAttachmentPoint(ridden));
                    AlteredAether.LOGGER.info("Tour: rider offset {} from the whale, seat at {}", offset, seat);
                }
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
        private static int leapTick = -1;

        /** The bearing, of eight, along which the view from an eye runs furthest before meeting anything. */
        private static double openest(Minecraft minecraft, double x, double y, double z) {
            double best = 0, bestReach = -1;
            for (int d = 0; d < 8; d++) {
                double angle = d * Math.PI / 4;
                var eye = new net.minecraft.world.phys.Vec3(x, y, z);
                var far = eye.add(Math.cos(angle) * 160, -6, Math.sin(angle) * 160);
                var hit = minecraft.level.clip(new net.minecraft.world.level.ClipContext(eye, far,
                        net.minecraft.world.level.ClipContext.Block.VISUAL, net.minecraft.world.level.ClipContext.Fluid.NONE,
                        minecraft.player));
                double reach = hit.getLocation().distanceTo(eye);
                if (reach > bestReach) {
                    bestReach = reach;
                    best = angle;
                }
            }
            return best;
        }

        /**
         * Open ground near the shot's spot, searching outward once the chunks there have arrived: a column with
         * ground and open sky over it, so the camera stands on the island rather than over a gap.
         */
        private static BlockPos footing(Minecraft minecraft, Shot shot) {
            var level = minecraft.level;
            int x = Mth.floor(shot.x()), z = Mth.floor(shot.z());
            for (int r = 0; r <= 48; r += 4) {
                for (int i = 0; i < Math.max(1, r * 2); i++) {
                    double angle = i * Mth.TWO_PI / Math.max(1, r * 2);
                    int px = x + (int) (Math.cos(angle) * r), pz = z + (int) (Math.sin(angle) * r);
                    if (!level.hasChunk(px >> 4, pz >> 4)) {
                        return null;
                    }
                    int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, px, pz);
                    if (y > level.getMinBuildHeight() + 2 && level.getBlockState(new BlockPos(px, y - 1, pz)).isSolid()) {
                        return new BlockPos(px, y, pz);
                    }
                }
            }
            return new BlockPos(x, Mth.floor(shot.y()), z);
        }

        /** How many ticks in a row everything has been drawn: the last chunks can still be arriving. */
        private static int settledFor(boolean ready) {
            readyTicks = ready ? readyTicks + 1 : 0;
            return readyTicks;
        }

        private static void next(Minecraft minecraft, MinecraftServer server, List<Shot> shots) {
            index++;
            waited = 0;
            footing = null;
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
            if (shot.name().startsWith("whale")) {
                ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
                if (whale == null) {
                    // clear out whales an earlier tour left in the world
                    aether.getEntitiesOfClass(TamedAerwhale.class, new net.minecraft.world.phys.AABB(BlockPos.containing(
                            shot.fx(), shot.fy(), shot.fz())).inflate(64)).forEach(net.minecraft.world.entity.Entity::discard);
                    whale = poseWhale(aether, player, shot.fx(), shot.fy() - 2, shot.fz(), false);
                    poseWhale(aether, player, shot.fx() - 12, shot.fy() - 1, shot.fz() - 2, true);
                }
                if (shot.name().equals("whale_ride") || shot.name().equals("whale_leap")) {
                    player.getAbilities().flying = false;
                    player.onUpdateAbilities();
                    player.startRiding(whale, true);
                }
            }
        }

        private static TamedAerwhale poseWhale(ServerLevel aether, ServerPlayer owner, double x, double y, double z,
                                               boolean calf) {
            TamedAerwhale tamed = AARegistries.TAMED_AERWHALE.get().create(aether);
            tamed.moveTo(x, y, z, 0, 0);
            tamed.setYRotData(0);
            tamed.setNoAi(true);
            tamed.setOwnerUUID(owner.getUUID());
            if (calf) {
                tamed.setBaby(true);
            } else {
                tamed.equipSaddle(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.SADDLE), null);
            }
            aether.addFreshEntity(tamed);
            return tamed;
        }

        private Client() {
        }
    }

    private Tour() {
    }
}
