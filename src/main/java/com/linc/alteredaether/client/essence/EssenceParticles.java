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
package com.linc.alteredaether.client.essence;

import com.linc.alteredaether.essence.EssenceMoteOptions;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * The light about an essence source, slow and soft where a squall's sparks are quick and sharp: notes that flit about
 * the orb and twinkle, and motes that drift out along a lazy curve to a capstone, a jar or the plinth and fade as they
 * arrive. Both are full bright.
 */
public final class EssenceParticles {
    private static final int FULL_BRIGHT = 0xF000F0;

    /** A note of light: wanders about the orb, twinkling, for three to five seconds. */
    public static final class Note extends TextureSheetParticle {
        private final SpriteSet sprites;
        private final float twinkle;

        Note(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, SpriteSet sprites) {
            super(level, x, y, z);
            this.sprites = sprites;
            this.xd = dx;
            this.yd = dy;
            this.zd = dz;
            this.lifetime = 60 + random.nextInt(41);
            this.quadSize = 0.045F + random.nextFloat() * 0.03F;
            this.gravity = 0;
            this.hasPhysics = false;
            this.friction = 0.92F;
            this.twinkle = random.nextFloat() * Mth.TWO_PI;
            setSprite(sprites.get(0, 3));
            alpha = 0;
        }

        @Override
        public void tick() {
            super.tick();
            if (removed) {
                return;
            }
            // flit: now and then a small nudge in a new direction, the friction easing it out again
            if (random.nextInt(8) == 0) {
                xd += (random.nextDouble() - 0.5) * 0.012;
                yd += (random.nextDouble() - 0.45) * 0.01;
                zd += (random.nextDouble() - 0.5) * 0.012;
            }
            float life = (float) age / lifetime;
            float fade = Math.min(1, Math.min(life * 5, (1 - life) * 4));
            float shine = 0.5F + 0.5F * Mth.sin(age * 0.35F + twinkle);
            alpha = fade * (0.45F + 0.55F * shine);
            setSprite(sprites.get(Mth.floor(shine * 3.99F), 3));
        }

        @Override
        public ParticleRenderType getRenderType() {
            return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
        }

        @Override
        protected int getLightColor(float partialTick) {
            return FULL_BRIGHT;
        }
    }

    /**
     * A mote: drifts from where it starts to its target over its life along a curve (a quadratic bezier) bowed out
     * sideways and a little up by up to the options' {@code curve}, easing in and out, and fades as it arrives.
     */
    public static final class Mote extends TextureSheetParticle {
        private final Vec3 from, bow, to;
        private final float wobble;

        Mote(ClientLevel level, double x, double y, double z, EssenceMoteOptions options, SpriteSet sprites) {
            super(level, x, y, z);
            this.from = new Vec3(x, y, z);
            this.to = options.target();
            Vec3 path = to.subtract(from);
            Vec3 side = path.cross(new Vec3(0, 1, 0));
            if (side.lengthSqr() < 1.0E-4) {
                side = new Vec3(1, 0, 0);
            }
            side = side.normalize().scale((random.nextDouble() * 2 - 1) * options.curve());
            this.bow = from.add(path.scale(0.5)).add(side).add(0, options.curve() * 0.5 * random.nextDouble(), 0);
            this.lifetime = Math.max(10, options.life());
            this.quadSize = 0.05F + random.nextFloat() * 0.025F;
            this.gravity = 0;
            this.hasPhysics = false;
            this.wobble = random.nextFloat() * Mth.TWO_PI;
            setSprite(sprites.get(random.nextInt(2), 1));
            alpha = 0;
        }

        @Override
        public void tick() {
            xo = x;
            yo = y;
            zo = z;
            if (age++ >= lifetime) {
                remove();
                return;
            }
            float life = (float) age / lifetime;
            // ease in and out, so it lingers leaving and arriving
            double t = life * life * (3 - 2 * life);
            double u = 1 - t;
            x = u * u * from.x + 2 * u * t * bow.x + t * t * to.x;
            y = u * u * from.y + 2 * u * t * bow.y + t * t * to.y;
            z = u * u * from.z + 2 * u * t * bow.z + t * t * to.z;
            // and drifts a touch about its path, as the notes flit
            x += Mth.sin(age * 0.21F + wobble) * 0.02;
            y += Mth.sin(age * 0.17F + wobble * 2) * 0.015;
            z += Mth.cos(age * 0.19F + wobble) * 0.02;
            alpha = Math.min(1, Math.min(life * 6, (1 - life) * 3)) * 0.9F;
            quadSize *= life > 0.8F ? 0.97F : 1;
        }

        @Override
        public ParticleRenderType getRenderType() {
            return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
        }

        @Override
        protected int getLightColor(float partialTick) {
            return FULL_BRIGHT;
        }
    }

    public record NoteProvider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                       double dx, double dy, double dz) {
            return new Note(level, x, y, z, dx, dy, dz, sprites);
        }
    }

    public record MoteProvider(SpriteSet sprites) implements ParticleProvider<EssenceMoteOptions> {
        @Override
        public Particle createParticle(EssenceMoteOptions options, ClientLevel level, double x, double y, double z,
                                       double dx, double dy, double dz) {
            return new Mote(level, x, y, z, options, sprites);
        }
    }

    private EssenceParticles() {
    }
}
