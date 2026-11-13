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
package com.linc.alteredaether.client.golem;

import com.linc.alteredaether.config.AAClientConfig;
import com.linc.alteredaether.entity.golem.GolemPayloads;
import com.linc.alteredaether.entity.golem.GravititeGolem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * What glows for the gravitite golem's player: linked storage when it is linked, everything linked when its pebble is
 * used on its golem, storage holding a searched item, a spot a golem was sent to. Each glow fades in, holds, and
 * fades out over its last second, with gravitite motes drifting off it so it still reads under a shaderpack.
 */
public final class GolemGlow {
    static final int FADE_IN_TICKS = 4;
    static final int FADE_OUT_TICKS = 20;
    /** The edge colour of storage pinned strictly (by a glow item frame or placard): glow-ink yellow. */
    static final int STRICT_COLOR = 0xFFE14A;
    private static final Vector3f STRICT_MOTE = Vec3.fromRGB24(STRICT_COLOR).toVector3f();

    /**
     * A block ({@code pos}) or golem ({@code entity}, else -1) glowing {@code color} at {@code strength} (1 bright,
     * less for faint), from {@code startTick} to {@code endTick}.
     */
    record Glow(byte kind, @Nullable BlockPos pos, int entity, int color, float strength, long startTick, long endTick) {
    }

    /** Two golems joined by a thread of motes until {@code endTick}. */
    record Thread(int a, int b, long endTick) {
    }

    static final List<Glow> GLOWS = new ArrayList<>();
    static final List<Thread> THREADS = new ArrayList<>();
    static long ticks;
    @Nullable
    private static ClientLevel level;

    private GolemGlow() {
    }

    static void add(GolemPayloads.Glow payload) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        syncLevel(mc.level);
        byte kind = payload.kind();
        int seconds = switch (kind) {
            case GolemPayloads.SHOW -> AAClientConfig.GOLEM_SEARCH_SECONDS.get();
            case GolemPayloads.SEARCH -> AAClientConfig.GOLEM_SEARCH_SECONDS.get();
            case GolemPayloads.UNLINK -> 1;
            default -> AAClientConfig.GOLEM_LINK_GLOW_SECONDS.get();
        };
        long length = payload.ticks() > 0 ? payload.ticks() : seconds * 20L;
        long end = ticks + Math.max(length, FADE_IN_TICKS + FADE_OUT_TICKS);
        if (kind == GolemPayloads.SHOW || kind == GolemPayloads.SEARCH) {
            // a fresh look replaces the last one
            GLOWS.removeIf(glow -> glow.kind() == GolemPayloads.SHOW || glow.kind() == GolemPayloads.SEARCH);
            THREADS.clear();
        }
        int color = AAClientConfig.golemGlowColor();
        Set<BlockPos> strict = new HashSet<>(payload.strict());
        float strength = kind == GolemPayloads.UNLINK ? 0.6F : 1.0F;
        for (BlockPos pos : payload.bright()) {
            GLOWS.add(new Glow(kind, pos, -1, strict.contains(pos) ? STRICT_COLOR : color, strength, ticks, end));
        }
        for (BlockPos pos : payload.dim()) {
            GLOWS.add(new Glow(kind, pos, -1, strict.contains(pos) ? STRICT_COLOR : color, 0.3F, ticks, end));
        }
        for (int id : payload.golems()) {
            GLOWS.add(new Glow(kind, null, id, color, strength, ticks, end));
        }
        for (int i = 0; i + 1 < payload.threads().size(); i += 2) {
            THREADS.add(new Thread(payload.threads().get(i), payload.threads().get(i + 1), end));
        }
    }

    static float fade(Glow glow, float now) {
        float in = Math.clamp((now - glow.startTick()) / FADE_IN_TICKS, 0F, 1F);
        float out = Math.clamp((glow.endTick() - now) / FADE_OUT_TICKS, 0F, 1F);
        return in * out * glow.strength();
    }

    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            clear();
            return;
        }
        syncLevel(mc.level);
        ticks++;
        GLOWS.removeIf(glow -> ticks >= glow.endTick() || glow.entity() >= 0 && mc.level.getEntity(glow.entity()) == null);
        THREADS.removeIf(thread -> ticks >= thread.endTick());
        boolean reduced = AAClientConfig.GOLEM_REDUCED_EFFECTS.get();
        RandomSource random = mc.level.random;
        int every = reduced ? 16 : 6;
        if (ticks % every == 0) {
            for (Glow glow : GLOWS) {
                AABB box = box(mc.level, glow, 1);
                if (box == null || glow.strength() < 0.5F) {
                    continue;
                }
                Vec3 at = new Vec3(box.minX + random.nextDouble() * box.getXsize(), box.maxY,
                        box.minZ + random.nextDouble() * box.getZsize());
                mc.level.addParticle(new DustParticleOptions(glow.color() == STRICT_COLOR ? STRICT_MOTE : GravititeGolem.GRAVITITE,
                        0.7F), at.x, at.y, at.z, 0, 0.02, 0);
            }
        }
        if (ticks % (reduced ? 8 : 3) == 0) {
            for (Thread thread : THREADS) {
                Entity a = mc.level.getEntity(thread.a()), b = mc.level.getEntity(thread.b());
                if (a == null || b == null) {
                    continue;
                }
                Vec3 from = a.position().add(0, a.getBbHeight() * 0.8, 0), to = b.position().add(0, b.getBbHeight() * 0.8, 0);
                double length = from.distanceTo(to);
                int points = Math.max(2, (int) (length / (reduced ? 1.5 : 0.6)));
                for (int i = 0; i <= points; i++) {
                    double t = (i + random.nextDouble() * 0.5) / points;
                    Vec3 at = from.lerp(to, Math.min(1, t)).add(0, Math.sin(Math.min(1, t) * Math.PI) * length * 0.08, 0);
                    mc.level.addParticle(new DustParticleOptions(GravititeGolem.GRAVITITE_DARK, 0.6F), at.x, at.y, at.z, 0, 0, 0);
                }
            }
        }
    }

    /** The box a glow covers: the block's own shape (both halves of a double chest), or the golem's. */
    @Nullable
    static AABB box(ClientLevel level, Glow glow, float partial) {
        if (glow.entity() >= 0) {
            Entity entity = level.getEntity(glow.entity());
            if (entity == null) {
                return null;
            }
            Vec3 lerp = entity.getPosition(partial).subtract(entity.position());
            return entity.getBoundingBox().move(lerp).inflate(0.05);
        }
        BlockPos pos = glow.pos();
        if (pos == null || !level.isLoaded(pos)) {
            return null;
        }
        var shape = level.getBlockState(pos).getShape(level, pos);
        AABB box = shape.isEmpty() ? new AABB(pos) : shape.bounds().move(pos);
        return box.inflate(0.01);
    }

    static void clear() {
        GLOWS.clear();
        THREADS.clear();
        level = null;
    }

    private static void syncLevel(ClientLevel current) {
        if (level != current) {
            GLOWS.clear();
            THREADS.clear();
            level = current;
        }
    }
}
