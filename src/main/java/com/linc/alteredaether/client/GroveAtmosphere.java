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
package com.linc.alteredaether.client;

import com.linc.alteredaether.AATags;
import com.linc.alteredaether.AlteredAether;
import com.linc.alteredaether.config.AAClientConfig;
import com.linc.alteredaether.registry.AARegistries;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Fireflies in the giant-tree groves. A few drift in the shade by day, under the canopy, in the hollows and over water;
 * at night they come out everywhere in loose swarms. They are particles spawned round the player on this client only,
 * so they cost the server nothing.
 */
@EventBusSubscriber(modid = AlteredAether.MOD_ID, value = Dist.CLIENT)
public final class GroveAtmosphere {
    private static final ResourceLocation THE_AETHER = ResourceLocation.fromNamespaceAndPath("aether", "the_aether");
    /** How far round the player fireflies appear. */
    private static final int SPREAD = 20, RISE = 8;
    /** Spawns per tick at full night and in full daylight, before the density setting. */
    private static final double NIGHT_RATE = 0.6, DAY_RATE = 0.25;

    private static boolean inGrove;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        Player player = minecraft.player;
        if (level == null || player == null || minecraft.isPaused() || !AAClientConfig.FIREFLIES.get()
                || !level.dimension().location().equals(THE_AETHER)) {
            return;
        }
        // The biome is checked once a second; fireflies near a grove's edge follow the player out a little way.
        if (level.getGameTime() % 20 == 0) {
            inGrove = level.getBiome(player.blockPosition()).is(AATags.HAS_FIREFLIES);
        }
        if (!inGrove) {
            return;
        }
        RandomSource random = level.random;
        double night = level.getSkyDarken() / 11.0;
        double rate = (DAY_RATE + (NIGHT_RATE - DAY_RATE) * night) * AAClientConfig.PARTICLE_DENSITY.get();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (double budget = rate; budget > 0; budget--) {
            if (budget < 1 && random.nextDouble() >= budget) {
                break;
            }
            pos.set(player.getBlockX() + random.nextInt(2 * SPREAD + 1) - SPREAD,
                    player.getBlockY() + random.nextInt(2 * RISE + 1) - RISE / 2,
                    player.getBlockZ() + random.nextInt(2 * SPREAD + 1) - SPREAD);
            if (!level.isLoaded(pos) || !level.getBlockState(pos).isAir()) {
                continue;
            }
            // By day only in the shade, where little sky reaches: under the canopy, in hollows, low over water.
            boolean shade = level.getBrightness(LightLayer.SKY, pos) < 11
                    || level.getFluidState(pos.below()).is(Fluids.WATER);
            if (night < 0.5 && !shade) {
                continue;
            }
            int swarm = 1 + random.nextInt(night > 0.5 ? 4 : 2);
            for (int i = 0; i < swarm; i++) {
                level.addParticle(AARegistries.FIREFLY.get(), pos.getX() + random.nextDouble() * 3 - 1,
                        pos.getY() + random.nextDouble() * 2, pos.getZ() + random.nextDouble() * 3 - 1, 0, 0, 0);
            }
        }
    }

    private GroveAtmosphere() {
    }
}
