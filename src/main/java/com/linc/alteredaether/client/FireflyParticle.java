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
import com.linc.alteredaether.registry.AARegistries;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

/**
 * A firefly: a mote that wanders slowly through the air, glowing on and off. It is drawn at full brightness, so it
 * glows in the dark of a canopy or a night whatever the light around it, and shaderpacks, which keep particles, show
 * it too. Mostly ambrosium gold; now and then pale blue or green.
 */
public final class FireflyParticle extends TextureSheetParticle {
    private static final int FULL_BRIGHT = 0xF000F0;
    private final float phase, speed, peak;

    private FireflyParticle(ClientLevel level, double x, double y, double z, SpriteSet sprites) {
        super(level, x, y, z);
        pickSprite(sprites);
        lifetime = 100 + random.nextInt(140);
        quadSize = 0.05f + random.nextFloat() * 0.04f;
        gravity = 0;
        hasPhysics = true;
        friction = 0.96f;
        xd = (random.nextDouble() - 0.5) * 0.02;
        yd = (random.nextDouble() - 0.5) * 0.01;
        zd = (random.nextDouble() - 0.5) * 0.02;
        float roll = random.nextFloat();
        if (roll < 0.88f) {
            setColor(1.0f, 0.82f + random.nextFloat() * 0.08f, 0.35f);
        } else if (roll < 0.94f) {
            setColor(0.62f, 0.86f, 1.0f);
        } else {
            setColor(0.66f, 1.0f, 0.56f);
        }
        phase = random.nextFloat() * Mth.TWO_PI;
        speed = 0.08f + random.nextFloat() * 0.12f;
        peak = 0.75f + random.nextFloat() * 0.25f;
        alpha = 0;
    }

    @Override
    public void tick() {
        super.tick();
        // A slow, aimless drift.
        xd += (random.nextDouble() - 0.5) * 0.004;
        yd += (random.nextDouble() - 0.5) * 0.002;
        zd += (random.nextDouble() - 0.5) * 0.004;
        // Glowing on and off, faded in at birth and out at the end.
        float glow = 0.5f + 0.5f * Mth.sin(age * speed + phase);
        float fade = Math.min(1, Math.min(age / 20f, (lifetime - age) / 20f));
        alpha = peak * fade * (0.15f + 0.85f * glow * glow);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    protected int getLightColor(float partialTick) {
        return FULL_BRIGHT;
    }

    public record Provider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                       double xd, double yd, double zd) {
            return new FireflyParticle(level, x, y, z, sprites);
        }
    }

    @EventBusSubscriber(modid = AlteredAether.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        @SubscribeEvent
        public static void onRegisterProviders(RegisterParticleProvidersEvent event) {
            event.registerSpriteSet(AARegistries.FIREFLY.get(), Provider::new);
        }

        private Registration() {
        }
    }
}
