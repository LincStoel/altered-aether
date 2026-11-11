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
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
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
 * Items gliding between chests for golems that are sorting. Only a picture of what the server will do when the item
 * lands; each flight ends on its own timer even if word of its landing never comes. Drawn by the golem's renderer, so
 * shaderpacks treat it as part of the entity.
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
        final double[] cumulative;

        View(GolemPayloads.Flight flight, long start) {
            id = flight.id();
            golem = flight.golem();
            item = flight.item();
            this.start = start;
            ticks = Math.max(1, flight.ticks());
            points = new ArrayList<>();
            points.add(flight.from());
            if (flight.via().isEmpty()) {
                // a single arc, peaking above the higher end
                Vec3 mid = flight.from().lerp(flight.to(), 0.5);
                double rise = Math.max(flight.from().y, flight.to().y) - mid.y + 0.8 + flight.from().distanceTo(flight.to()) * 0.15;
                points.add(mid.add(0, rise, 0));
            } else {
                points.addAll(flight.via());
            }
            points.add(flight.to());
            cumulative = new double[points.size()];
            for (int i = 1; i < points.size(); i++) {
                cumulative[i] = cumulative[i - 1] + points.get(i - 1).distanceTo(points.get(i));
            }
        }

        /** Where the item is, {@code t} of the way along (0-1): a smooth curve through the points. */
        Vec3 at(double t) {
            double eased = t * t * (3 - 2 * t);
            double length = cumulative[cumulative.length - 1];
            if (length <= 1.0E-6) {
                return points.get(0);
            }
            double d = eased * length;
            int seg = 0;
            while (seg < points.size() - 2 && cumulative[seg + 1] < d) {
                seg++;
            }
            double segLength = cumulative[seg + 1] - cumulative[seg];
            double u = segLength <= 1.0E-6 ? 0 : (d - cumulative[seg]) / segLength;
            Vec3 p0 = points.get(Math.max(0, seg - 1)), p1 = points.get(seg), p2 = points.get(seg + 1);
            Vec3 p3 = points.get(Math.min(points.size() - 1, seg + 2));
            return catmullRom(p0, p1, p2, p3, u);
        }

        double progress(float now) {
            return Mth.clamp((now - start) / ticks, 0, 1);
        }

        AABB bounds() {
            AABB box = new AABB(points.get(0), points.get(0));
            for (Vec3 point : points) {
                box = box.minmax(new AABB(point, point));
            }
            return box.inflate(1.0);
        }
    }

    private static Vec3 catmullRom(Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, double t) {
        double t2 = t * t, t3 = t2 * t;
        return new Vec3(
                0.5 * (2 * p1.x + (-p0.x + p2.x) * t + (2 * p0.x - 5 * p1.x + 4 * p2.x - p3.x) * t2 + (-p0.x + 3 * p1.x - 3 * p2.x + p3.x) * t3),
                0.5 * (2 * p1.y + (-p0.y + p2.y) * t + (2 * p0.y - 5 * p1.y + 4 * p2.y - p3.y) * t2 + (-p0.y + 3 * p1.y - 3 * p2.y + p3.y) * t3),
                0.5 * (2 * p1.z + (-p0.z + p2.z) * t + (2 * p0.z - 5 * p1.z + 4 * p2.z - p3.z) * t2 + (-p0.z + 3 * p1.z - 3 * p2.z + p3.z) * t3));
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
        Vec3 at = view.at(view.progress(ticks));
        int count = AAClientConfig.GOLEM_REDUCED_EFFECTS.get() ? 3 : 10;
        for (int i = 0; i < count; i++) {
            mc.level.addParticle(new DustParticleOptions(end.landed() ? GravititeGolem.GRAVITITE : GravititeGolem.GRAVITITE_DARK, 0.8F),
                    at.x + (mc.level.random.nextDouble() - 0.5) * 0.4, at.y + (mc.level.random.nextDouble() - 0.5) * 0.4,
                    at.z + (mc.level.random.nextDouble() - 0.5) * 0.4, 0, end.landed() ? -0.05 : 0.02, 0);
        }
        rebound(mc.level, view.golem);
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
                Vec3 at = view.at(view.progress(ticks));
                mc.level.addParticle(new DustParticleOptions(GravititeGolem.GRAVITITE, 0.5F), at.x, at.y, at.z, 0, 0, 0);
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
            Vec3 at = view.at(view.progress(now)).subtract(origin);
            poseStack.pushPose();
            poseStack.translate(at.x, at.y - 0.15, at.z);
            poseStack.mulPose(Axis.YP.rotationDegrees((now * 6 + view.id * 47) % 360));
            poseStack.scale(0.6F, 0.6F, 0.6F);
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

    /** Whether {@code entity} has an item in the air. */
    static boolean busy(Entity entity) {
        for (View view : FLIGHTS.values()) {
            if (view.golem == entity.getId()) {
                return true;
            }
        }
        return false;
    }
}
