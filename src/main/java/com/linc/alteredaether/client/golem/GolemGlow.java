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
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * What glows for the gravitite golem's player: linked storage when it is linked, storage holding a searched item, a
 * spot a golem was sent to, a golem a full network turned away (red), and what the player looks at while holding a
 * pebble. Each glow fades in, holds, and fades out, with gravitite motes drifting off it so it still reads under a
 * shaderpack. Blocks get a pane; a golem gets no box, only motes streaming off its body. All of it is the colour its
 * network is dyed; storage no golem can get at glows red, pinned storage yellow.
 *
 * <p>Apart from those, {@link #HELD} is the faint outline of a held pebble's linked storage, drawn while it is held.
 */
public final class GolemGlow {
    static final int FADE_IN_TICKS = 4;
    static final int FADE_OUT_TICKS = 20;
    /** How long a look glow lingers, fading, once the look moves off. */
    static final int LOOK_LINGER = 10;
    /** A kind of glow of the client's own: what a pebble's holder is looking at. */
    static final byte LOOK = 100;
    /** The edge colour of pinned storage (by an item frame, item shelf or placard): glow-ink yellow. */
    static final int STRICT_COLOR = 0xFFE14A;
    /** The edge colour of storage no golem can get at, and of a golem a full network turned away. */
    static final int BLOCKED_COLOR = 0xFF4A4A;

    /**
     * A block ({@code pos}) or golem ({@code entity}, else -1) glowing {@code color} at {@code strength} (1 bright,
     * less for faint), from {@code startTick} to {@code endTick}, fading out over its last {@code fadeOut} ticks.
     */
    record Glow(byte kind, @Nullable BlockPos pos, int entity, int color, float strength, long startTick, long endTick,
                int fadeOut) {
    }

    /** Two golems joined by a thread of motes of {@code color} until {@code endTick}. */
    record Thread(int a, int b, int color, long endTick) {
    }

    /** A faint outline on a held pebble's linked storage: never through walls, fading with distance. */
    record Held(BlockPos pos, int color, float alpha) {
    }

    static final List<Glow> GLOWS = new ArrayList<>();
    static final List<Thread> THREADS = new ArrayList<>();
    static final List<Held> HELD = new ArrayList<>();
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
            case GolemPayloads.SEARCH -> AAClientConfig.GOLEM_SEARCH_SECONDS.get();
            case GolemPayloads.UNLINK -> 1;
            default -> AAClientConfig.GOLEM_LINK_GLOW_SECONDS.get();
        };
        long length = payload.ticks() > 0 ? payload.ticks() : seconds * 20L;
        long end = ticks + Math.max(length, FADE_IN_TICKS + FADE_OUT_TICKS);
        if (kind == GolemPayloads.SEARCH) {
            // a fresh search replaces the last one
            GLOWS.removeIf(glow -> glow.kind() == GolemPayloads.SEARCH);
            THREADS.clear();
        }
        int color = color(payload.dye().getTextureDiffuseColor());
        Set<BlockPos> strict = new HashSet<>(payload.strict());
        float strength = kind == GolemPayloads.UNLINK ? 0.6F : 1.0F;
        for (BlockPos pos : payload.bright()) {
            GLOWS.add(new Glow(kind, pos, -1, strict.contains(pos) ? STRICT_COLOR : color, strength, ticks, end, FADE_OUT_TICKS));
        }
        for (BlockPos pos : payload.blocked()) {
            GLOWS.add(new Glow(kind, pos, -1, BLOCKED_COLOR, strength, ticks, end, FADE_OUT_TICKS));
        }
        int golemColor = kind == GolemPayloads.REFUSE ? BLOCKED_COLOR : color;
        for (int id : payload.golems()) {
            GLOWS.add(new Glow(kind, null, id, golemColor, strength, ticks, end, FADE_OUT_TICKS));
        }
        for (int i = 0; i + 1 < payload.threads().size(); i += 2) {
            THREADS.add(new Thread(payload.threads().get(i), payload.threads().get(i + 1), deep(color), end));
        }
    }

    /**
     * Keeps the look glow on {@code blocks} (or the golem {@code entity}, if not -1) for one more tick: a look glow
     * already there is extended without fading in again, and one the look has left fades out.
     */
    static void look(Collection<BlockPos> blocks, int entity, int color) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        syncLevel(mc.level);
        // a tick over the linger, so a look still on holds at full strength between ticks
        long end = ticks + LOOK_LINGER + 1;
        if (entity >= 0) {
            extend(null, entity, color, end);
        }
        for (BlockPos pos : blocks) {
            extend(pos, -1, color, end);
        }
    }

    private static void extend(@Nullable BlockPos pos, int entity, int color, long end) {
        for (int i = 0; i < GLOWS.size(); i++) {
            Glow glow = GLOWS.get(i);
            if (glow.kind() == LOOK && glow.entity() == entity && (pos == null ? glow.pos() == null : pos.equals(glow.pos()))) {
                GLOWS.set(i, new Glow(LOOK, pos, entity, color, 1.0F, glow.startTick(), end, LOOK_LINGER));
                return;
            }
        }
        GLOWS.add(new Glow(LOOK, pos, entity, color, 1.0F, ticks, end, LOOK_LINGER));
    }

    /**
     * The colour a network dyed {@code dyeRgb} (its dye's texture colour) glows: the dye's, or for an undyed
     * (magenta) one the client's configured gravitite glow.
     */
    static int color(int dyeRgb) {
        return dyeRgb == GravititeGolem.DEFAULT_COLOR.getTextureDiffuseColor() ? AAClientConfig.golemGlowColor()
                : dyeRgb & 0xFFFFFF;
    }

    /** A glow colour's deeper tone, for fills and threads. */
    static int deep(int rgb) {
        return (int) ((rgb >> 16 & 0xFF) * 0.75F) << 16 | (int) ((rgb >> 8 & 0xFF) * 0.75F) << 8 | (int) ((rgb & 0xFF) * 0.75F);
    }

    /** A glow colour as dust particles take it. */
    static Vector3f mote(int rgb) {
        return Vec3.fromRGB24(rgb & 0xFFFFFF).toVector3f();
    }

    static float fade(Glow glow, float now) {
        float in = Math.clamp((now - glow.startTick()) / FADE_IN_TICKS, 0F, 1F);
        float out = Math.clamp((glow.endTick() - now) / glow.fadeOut(), 0F, 1F);
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
        boolean blocks = ticks % (reduced ? 16 : 6) == 0, golems = ticks % (reduced ? 6 : 2) == 0;
        for (Glow glow : GLOWS) {
            boolean golem = glow.entity() >= 0;
            if (!(golem ? golems : blocks) || !golem && (glow.strength() < 0.5F || glow.kind() == LOOK)) {
                continue;
            }
            AABB box = box(mc.level, glow, 1);
            if (box == null) {
                continue;
            }
            if (golem) {
                // in place of a box: motes all over its body, drifting up, thinning as the glow fades
                if (random.nextFloat() > fade(glow, ticks) + 0.2F) {
                    continue;
                }
                for (int i = reduced ? 1 : 2; i > 0; i--) {
                    mc.level.addParticle(new DustParticleOptions(mote(glow.color()), 0.9F),
                            box.minX + random.nextDouble() * box.getXsize(), box.minY + random.nextDouble() * box.getYsize(),
                            box.minZ + random.nextDouble() * box.getZsize(), 0, 0.04, 0);
                }
                continue;
            }
            Vec3 at = new Vec3(box.minX + random.nextDouble() * box.getXsize(), box.maxY,
                    box.minZ + random.nextDouble() * box.getZsize());
            mc.level.addParticle(new DustParticleOptions(mote(glow.color()), 0.7F), at.x, at.y, at.z, 0, 0.02, 0);
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
                    mc.level.addParticle(new DustParticleOptions(mote(thread.color()), 0.6F), at.x, at.y, at.z, 0, 0, 0);
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
        return blockBox(level, glow.pos());
    }

    @Nullable
    static AABB blockBox(ClientLevel level, @Nullable BlockPos pos) {
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
        HELD.clear();
        level = null;
    }

    private static void syncLevel(ClientLevel current) {
        if (level != current) {
            GLOWS.clear();
            THREADS.clear();
            HELD.clear();
            level = current;
        }
    }
}
