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

import com.linc.alteredaether.AlteredAether;
import com.linc.alteredaether.config.AAClientConfig;
import com.linc.alteredaether.storm.StormPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

import java.lang.reflect.Method;

/**
 * How a Tempest storm looks and sounds from inside it, eased in and out as the player walks into and out of the
 * storm's reach. Built in two layers so it holds up under shaderpacks:
 * <ol>
 *   <li>Everything a shaderpack respects: distant lightning, drawn by spawning bolts that exist only on this client
 *   (each brings vanilla's sky flash and thunder, and Iris hands it to the shaderpack as a real bolt); the client's
 *   rain and thunder levels, so rain falls round the player and a shaderpack's wetness and darkening follow the storm;
 *   and crackling sparks on the high ground nearby.</li>
 *   <li>Fog drawn closer and darkened toward storm grey. Shaderpacks draw their own fog, so this only shows without
 *   one; with one active it is skipped, and the first layer is turned up a little to make up for it.</li>
 * </ol>
 * The intensity comes from the server (see StormPayload); everything here is presentation only.
 */
@EventBusSubscriber(modid = AlteredAether.MOD_ID, value = Dist.CLIENT)
public final class StormAtmosphere {
    private static final ResourceLocation THE_AETHER = ResourceLocation.fromNamespaceAndPath("aether", "the_aether");
    /** Storm grey, for the fog. */
    private static final float FOG_R = 0.30f, FOG_G = 0.32f, FOG_B = 0.38f;

    /** The storm as this client shows it, eased toward what the server last said. */
    private static float storm;
    /** The rain level the server set, and the level this class last wrote, to tell the two apart. */
    private static float serverRain, serverThunder, writtenRain = -1, writtenThunder = -1;
    private static int nextBoltId = -1_000_000;

    private static boolean shaderCheckFailed;
    private static Method irisInstance, irisInUse;
    private static boolean shaderActive;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        Player player = minecraft.player;
        if (level == null || player == null || minecraft.isPaused()) {
            return;
        }
        if (!level.dimension().location().equals(THE_AETHER)) {
            storm = 0;
            writtenRain = writtenThunder = -1;
            return;
        }
        if (level.getGameTime() % 40 == 0) {
            shaderActive = shaderPackInUse();
        }
        // About three seconds to build or fade, so walking out of a storm doesn't switch it off.
        storm += (StormPayload.received() - storm) * 0.015f;
        if (storm < 0.002f) {
            storm = 0;
        }
        weather(level);
        if (storm <= 0) {
            return;
        }
        RandomSource random = level.getRandom();
        float boost = shaderActive ? 1.4f : 1f;
        if (AAClientConfig.SKY_FLASHES.get() && storm > 0.15f && random.nextFloat() < storm * 0.012f * boost) {
            distantBolt(level, player, random);
        }
        sparks(level, player, random, boost);
    }

    /**
     * The rain and thunder levels: the server's, or the storm's where that is heavier. The server only writes these
     * when its own weather changes, so whatever differs from what was last written here came from it.
     */
    private static void weather(ClientLevel level) {
        float rain = level.getRainLevel(1), thunder = level.getThunderLevel(1);
        if (Math.abs(rain - writtenRain) > 1e-4f) {
            serverRain = rain;
        }
        if (Math.abs(thunder - writtenThunder) > 1e-4f) {
            serverThunder = thunder;
        }
        boolean local = AAClientConfig.LOCAL_RAIN.get();
        writtenRain = Math.max(serverRain, local ? storm * 0.9f : 0);
        writtenThunder = Math.max(serverThunder, local ? storm * 0.6f : 0);
        level.setRainLevel(writtenRain);
        level.setThunderLevel(writtenThunder);
    }

    /** A bolt somewhere off in the storm, for this client's eyes only: the flash, the thunder, nothing struck. */
    private static void distantBolt(ClientLevel level, Player player, RandomSource random) {
        double angle = random.nextDouble() * Mth.TWO_PI, distance = 48 + random.nextDouble() * 112;
        int x = Mth.floor(player.getX() + Math.cos(angle) * distance), z = Mth.floor(player.getZ() + Math.sin(angle) * distance);
        int y = level.hasChunk(x >> 4, z >> 4) ? level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z)
                : Mth.floor(player.getY()) + 30;
        if (y <= level.getMinBuildHeight() + 1) {
            y = Mth.floor(player.getY()) - 20 - random.nextInt(40);
        }
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt == null) {
            return;
        }
        bolt.setVisualOnly(true);
        bolt.moveTo(x + 0.5, y, z + 0.5);
        bolt.setId(nextBoltId--);
        level.addEntity(bolt);
    }

    /** Sparks crackling over the high ground round the player, more of them the fiercer the storm. */
    private static void sparks(ClientLevel level, Player player, RandomSource random, float boost) {
        float count = storm * 1.6f * boost * AAClientConfig.PARTICLE_DENSITY.get().floatValue();
        for (int i = 0; i < Mth.floor(count) + (random.nextFloat() < count % 1 ? 1 : 0); i++) {
            int x = Mth.floor(player.getX()) + random.nextInt(33) - 16, z = Mth.floor(player.getZ()) + random.nextInt(33) - 16;
            if (!level.hasChunk(x >> 4, z >> 4)) {
                continue;
            }
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
            if (y <= level.getMinBuildHeight() + 1 || Math.abs(y - player.getY()) > 24) {
                continue;
            }
            level.addParticle(ParticleTypes.ELECTRIC_SPARK, x + random.nextDouble(), y + random.nextDouble() * 0.6,
                    z + random.nextDouble(), (random.nextDouble() - 0.5) * 0.2, random.nextDouble() * 0.15,
                    (random.nextDouble() - 0.5) * 0.2);
        }
    }

    @SubscribeEvent
    public static void onFogColour(ViewportEvent.ComputeFogColor event) {
        float amount = fogAmount();
        if (amount <= 0) {
            return;
        }
        event.setRed(Mth.lerp(amount * 0.6f, event.getRed(), FOG_R));
        event.setGreen(Mth.lerp(amount * 0.6f, event.getGreen(), FOG_G));
        event.setBlue(Mth.lerp(amount * 0.6f, event.getBlue(), FOG_B));
    }

    @SubscribeEvent
    public static void onFog(ViewportEvent.RenderFog event) {
        float amount = fogAmount();
        if (amount <= 0 || event.getMode() != FogRenderer.FogMode.FOG_TERRAIN) {
            return;
        }
        float scale = 1 - 0.45f * amount;
        event.setNearPlaneDistance(event.getNearPlaneDistance() * scale);
        event.setFarPlaneDistance(event.getFarPlaneDistance() * scale);
        event.setCanceled(true);
    }

    /** How strongly to draw the storm into the fog: nothing with a shaderpack, which draws its own. */
    private static float fogAmount() {
        if (!AAClientConfig.STORM_FOG.get() || storm <= 0 || shaderActive) {
            return 0;
        }
        return Mth.clamp(storm * AAClientConfig.STORM_FOG_STRENGTH.get().floatValue(), 0, 1);
    }

    /** Whether a shaderpack is drawing the world, by the config or, on auto, by asking Iris if it is present. */
    private static boolean shaderPackInUse() {
        switch (AAClientConfig.SHADER_MODE.get()) {
            case FORCE_ON -> {
                return true;
            }
            case FORCE_OFF -> {
                return false;
            }
            default -> {
            }
        }
        if (shaderCheckFailed) {
            return false;
        }
        try {
            if (irisInstance == null) {
                Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                irisInstance = api.getMethod("getInstance");
                irisInUse = api.getMethod("isShaderPackInUse");
            }
            return (boolean) irisInUse.invoke(irisInstance.invoke(null));
        } catch (ReflectiveOperationException | LinkageError e) {
            // No Iris, or not the API we know: treat it as no shaderpack and stop asking.
            shaderCheckFailed = true;
            return false;
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            storm = 0;
            serverRain = serverThunder = 0;
            writtenRain = writtenThunder = -1;
            StormPayload.clear();
        }
    }

    private StormAtmosphere() {
    }
}
