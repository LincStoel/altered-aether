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
import com.linc.alteredaether.essence.EssenceOrbBlock;
import com.linc.alteredaether.worldgen.essence.EssenceField;
import com.linc.alteredaether.worldgen.landmark.Landmark;
import com.linc.alteredaether.worldgen.landmark.LandmarkField;
import com.linc.alteredaether.worldgen.landmark.LandmarkType;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Checks headlessly that essence sources generate: with the system property {@code altered_aether.essence} set to a
 * radius (gradle {@code -PaaEssence=4096}), on startup it lists the Essence Channel sites in that radius of the origin
 * and the Wellspring point of the nearest landmark of each kind, generates the chunks of the first few, logs where
 * each orb landed against the ground under it, and stops the server. Lines start {@code ESSENCE}.
 */
@EventBusSubscriber(modid = AlteredAether.MOD_ID)
public final class EssenceProbe {
    private static final ResourceKey<Level> THE_AETHER =
            ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath("aether", "the_aether"));
    private static final int CHECK_CHANNELS = 8;

    private record Target(String what, int x, int z, int expected) {
    }

    @SubscribeEvent
    public static void onStarted(ServerStartedEvent event) {
        String spec = System.getProperty("altered_aether.essence", "");
        if (spec.isBlank()) {
            return;
        }
        MinecraftServer server = event.getServer();
        ServerLevel aether = server.getLevel(THE_AETHER);
        if (aether == null) {
            AlteredAether.LOGGER.error("ESSENCE the Aether isn't loaded");
            server.halt(false);
            return;
        }
        int radius = Integer.parseInt(spec.trim());
        CompletableFuture.supplyAsync(() -> targets(aether, radius), Util.backgroundExecutor())
                .whenCompleteAsync((targets, error) -> {
                    if (error != null) {
                        AlteredAether.LOGGER.error("ESSENCE search failed", error);
                    } else {
                        for (Target target : targets) {
                            check(aether, target);
                        }
                    }
                    server.halt(false);
                }, server);
    }

    private static List<Target> targets(ServerLevel aether, int radius) {
        List<Target> targets = new ArrayList<>();
        EssenceField field = EssenceField.get(THE_AETHER);
        if (field == null) {
            AlteredAether.LOGGER.info("ESSENCE no channel field (not the tall Aether?)");
        } else {
            int cells = 0, sites = 0;
            long topSum = 0;
            int cell = EssenceField.CELL;
            for (int cx = Math.floorDiv(-radius, cell); cx <= Math.floorDiv(radius, cell); cx++) {
                for (int cz = Math.floorDiv(-radius, cell); cz <= Math.floorDiv(radius, cell); cz++) {
                    cells++;
                    Optional<EssenceField.Site> site = field.site(cx, cz);
                    if (site.isPresent()) {
                        sites++;
                        topSum += site.get().top();
                        if (targets.size() < CHECK_CHANNELS) {
                            targets.add(new Target("channel", site.get().x(), site.get().z(), site.get().top()));
                        }
                    }
                }
            }
            AlteredAether.LOGGER.info("ESSENCE channels: {} of {} cells within {} hold one, average islet top y {}",
                    sites, cells, radius, sites == 0 ? 0 : topSum / sites);
        }
        LandmarkField landmarks = LandmarkField.get(THE_AETHER);
        if (landmarks != null) {
            for (LandmarkType type : LandmarkType.values()) {
                Optional<Landmark> site = landmarks.nearest(type, 0, 0, radius * 2);
                if (site.isEmpty()) {
                    AlteredAether.LOGGER.info("ESSENCE no {} within {}", type.id(), radius * 2);
                    continue;
                }
                List<BlockPos> points = site.get().points(Landmark.ORB);
                if (points.isEmpty()) {
                    AlteredAether.LOGGER.info("ESSENCE {} at {}, {} has no wellspring", type.id(), site.get().x(),
                            site.get().z());
                }
                for (BlockPos point : points) {
                    targets.add(new Target("wellspring/" + type.id(), point.getX(), point.getZ(), point.getY()));
                }
            }
        }
        return targets;
    }

    /** Generates the target's chunk and looks for its orb in and round its column. */
    private static void check(ServerLevel aether, Target target) {
        aether.getChunk(target.x() >> 4, target.z() >> 4);
        BlockPos found = null;
        for (int dx = -1; dx <= 1 && found == null; dx++) {
            for (int dz = -1; dz <= 1 && found == null; dz++) {
                for (int y = aether.getMaxBuildHeight() - 1; y >= aether.getMinBuildHeight(); y--) {
                    BlockPos at = new BlockPos(target.x() + dx, y, target.z() + dz);
                    BlockState state = aether.getBlockState(at);
                    if (state.getBlock() instanceof EssenceOrbBlock) {
                        found = at;
                        break;
                    }
                }
            }
        }
        int ground = aether.getHeight(Heightmap.Types.WORLD_SURFACE, target.x(), target.z());
        String expected = target.expected() == Integer.MIN_VALUE ? "from the ground" : "y " + target.expected();
        if (found == null) {
            AlteredAether.LOGGER.info("ESSENCE {} at {}, {} ({}): NO ORB; surface y {}", target.what(), target.x(),
                    target.z(), expected, ground);
            LandmarkField landmarks = LandmarkField.get(THE_AETHER);
            if (landmarks != null) {
                int bx = target.x() & ~15, bz = target.z() & ~15;
                for (Landmark site : landmarks.touching(bx, bz, bx + 15, bz + 15)) {
                    AlteredAether.LOGGER.info("ESSENCE   touching {} at {}, {}: orb points {}", site.type().id(),
                            site.x(), site.z(), site.points(Landmark.ORB));
                }
            }
            StringBuilder column = new StringBuilder();
            for (int y = ground + 12; y >= ground - 4; y--) {
                column.append(y).append('=').append(aether.getBlockState(new BlockPos(target.x(), y, target.z()))
                        .getBlock().getDescriptionId().replaceAll(".*[.]", "")).append(' ');
            }
            AlteredAether.LOGGER.info("ESSENCE   column {}", column);
        } else {
            BlockPos below = found.below();
            int gap = 0;
            while (gap < 64 && aether.getBlockState(below).isAir()) {
                below = below.below();
                gap++;
            }
            BlockPos solid = found.below();
            while (solid.getY() > aether.getMinBuildHeight() && soft(aether.getBlockState(solid))) {
                solid = solid.below();
            }
            AlteredAether.LOGGER.info("ESSENCE {} at {}, {} ({}): orb {} at y {}, {} open blocks over {}, {} over solid {}",
                    target.what(), target.x(), target.z(), expected,
                    aether.getBlockState(found).getBlock().getDescriptionId(), found.getY(), gap,
                    aether.getBlockState(below).getBlock().getDescriptionId(), found.getY() - solid.getY(),
                    aether.getBlockState(solid).getBlock().getDescriptionId());
        }
    }

    /** What a channel sees through on its way down to the ground: air, trees and soft plants. */
    private static boolean soft(BlockState state) {
        return state.getFluidState().isEmpty() && (!state.isSolid() || state.is(BlockTags.LOGS)
                || state.is(BlockTags.LEAVES));
    }

    private EssenceProbe() {
    }
}
