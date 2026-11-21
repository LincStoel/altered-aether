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
import com.linc.alteredaether.entity.golem.route.FlightPath;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Items flying between chests for golems that are sorting: out of a face of one chest, one axis at a time, into a face
 * of the next, speeding up along each leg and easing round each corner. Only a picture of what the server will do when
 * the item lands; each flight ends on its own timer even if word of its landing never comes. Drawn by the golem's
 * renderer, so shaderpacks treat it as part of the entity.
 */
public final class GolemFlights {
    /** Ticks past its end a flight is kept for its landing word, before it is dropped regardless. */
    private static final int GRACE = 10;

    static final class View {
        final int id, golem;
        final ItemStack item;
        final List<Vec3> points;
        final long start;
        final int ticks;
        /** Where the item is each tick, as the server timed it (see route/FlightPath). */
        final double[][] table;

        View(GolemPayloads.Flight flight, long start) {
            id = flight.id();
            golem = flight.golem();
            item = flight.item();
            this.start = start;
            ticks = Math.max(1, flight.ticks());
            points = flight.points().isEmpty() ? List.of(Vec3.ZERO) : List.copyOf(flight.points());
            List<double[]> raw = new ArrayList<>();
            for (Vec3 point : points) {
                raw.add(new double[]{point.x, point.y, point.z});
            }
            table = FlightPath.table(raw);
        }

        /** Where the item is {@code time} ticks after setting off, between the table's ticks. */
        Vec3 at(double time) {
            double[] at = FlightPath.at(table, ticks, time);
            return new Vec3(at[0], at[1], at[2]);
        }

        double elapsed(float now) {
            return Mth.clamp(now - start, 0, ticks);
        }

        AABB bounds() {
            AABB box = new AABB(points.get(0), points.get(0));
            for (Vec3 point : points) {
                box = box.minmax(new AABB(point, point));
            }
            return box.inflate(1.0);
        }
    }

    private static final Map<Integer, View> FLIGHTS = new HashMap<>();
    private static long ticks;
    @Nullable
    private static ClientLevel level;

    private GolemFlights() {
    }

    static void add(GolemPayloads.Flight flight) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        syncLevel(mc.level);
        FLIGHTS.put(flight.id(), new View(flight, ticks));
        rebound(mc.level, flight.golem());
    }

    static void end(GolemPayloads.FlightEnd end) {
        Minecraft mc = Minecraft.getInstance();
        View view = FLIGHTS.remove(end.id());
        if (view == null || mc.level == null) {
            return;
        }
        Vec3 at = view.at(view.elapsed(ticks));
        int count = AAClientConfig.GOLEM_REDUCED_EFFECTS.get() ? 3 : 10;
        for (int i = 0; i < count; i++) {
            mc.level.addParticle(new DustParticleOptions(mote(mc.level, view.golem, !end.landed()), 0.8F),
                    at.x + (mc.level.random.nextDouble() - 0.5) * 0.4, at.y + (mc.level.random.nextDouble() - 0.5) * 0.4,
                    at.z + (mc.level.random.nextDouble() - 0.5) * 0.4, 0, end.landed() ? -0.05 : 0.02, 0);
        }
        rebound(mc.level, view.golem);
    }

    /** The colour of the motes on a flight of the golem {@code golemId}: its network's. */
    private static org.joml.Vector3f mote(ClientLevel level, int golemId, boolean dark) {
        return GravititeGolem.mote(level.getEntity(golemId) instanceof GravititeGolem golem ? golem.color()
                : GravititeGolem.DEFAULT_COLOR, dark);
    }

    /** Widens a golem's culling box to its flights, so they're drawn while on screen. */
    private static void rebound(ClientLevel level, int golemId) {
        if (!(level.getEntity(golemId) instanceof GravititeGolem golem)) {
            return;
        }
        AABB box = null;
        for (View view : FLIGHTS.values()) {
            if (view.golem == golemId) {
                box = box == null ? view.bounds() : box.minmax(view.bounds());
            }
        }
        golem.flightBounds = box;
    }

    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            clear();
            return;
        }
        syncLevel(mc.level);
        ticks++;
        boolean show = AAClientConfig.GOLEM_SHOW_FLIGHTS.get();
        boolean reduced = AAClientConfig.GOLEM_REDUCED_EFFECTS.get();
        for (Iterator<View> it = FLIGHTS.values().iterator(); it.hasNext(); ) {
            View view = it.next();
            if (ticks > view.start + view.ticks + GRACE) {
                it.remove();
                rebound(mc.level, view.golem);
                continue;
            }
            if (show && ticks % (reduced ? 6 : 2) == 0) {
                Vec3 at = view.at(view.elapsed(ticks));
                mc.level.addParticle(new DustParticleOptions(mote(mc.level, view.golem, false), 0.5F), at.x, at.y, at.z, 0, 0, 0);
            }
        }
    }

    /** Draws {@code golem}'s flying items; the pose is at the golem's (interpolated) feet. */
    static void render(GravititeGolem golem, float partial, PoseStack poseStack, MultiBufferSource buffers) {
        if (FLIGHTS.isEmpty() || !AAClientConfig.GOLEM_SHOW_FLIGHTS.get()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Vec3 origin = golem.getPosition(partial);
        float now = ticks + partial;
        for (View view : FLIGHTS.values()) {
            if (view.golem != golem.getId()) {
                continue;
            }
            Vec3 at = view.at(view.elapsed(now)).subtract(origin);
            poseStack.pushPose();
            poseStack.translate(at.x, at.y - 0.15, at.z);
            poseStack.mulPose(Axis.YP.rotationDegrees((now * 6 + view.id * 47) % 360));
            // the ground transform draws an item at a quarter block; this makes it about half a block, easy to follow
            poseStack.scale(1.1F, 1.1F, 1.1F);
            mc.getItemRenderer().renderStatic(view.item, ItemDisplayContext.GROUND, LightTexture.FULL_BRIGHT,
                    OverlayTexture.NO_OVERLAY, poseStack, buffers, golem.level(), view.id);
            poseStack.popPose();
        }
    }

    static void clear() {
        FLIGHTS.clear();
        level = null;
    }

    private static void syncLevel(ClientLevel current) {
        if (level != current) {
            FLIGHTS.clear();
            level = current;
        }
    }
}
