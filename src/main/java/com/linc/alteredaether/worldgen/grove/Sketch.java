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
package com.linc.alteredaether.worldgen.grove;

import com.linc.alteredaether.worldgen.TerrainProbe;
import com.linc.alteredaether.worldgen.grove.shape.Bounds;
import com.linc.alteredaether.worldgen.grove.shape.Carve;
import com.linc.alteredaether.worldgen.grove.shape.LeafCluster;
import com.linc.alteredaether.worldgen.grove.shape.Part;
import com.linc.alteredaether.worldgen.grove.shape.Trunk;
import com.linc.alteredaether.worldgen.grove.shape.Tube;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A giant being drawn: collects its trunks, limbs, roots, hollows and foliage, with the shared vocabulary every kind
 * of giant is drawn in (crowns, buttress roots, reading the ground), then seals them into a {@link Giant}.
 */
final class Sketch {
    final RandomSource random;
    final TerrainProbe probe;
    /** Vertical scale for the height mode: 1 in the tall Aether. */
    final double v;
    final int x, z, ground, buildTop;

    final List<Part> wood = new ArrayList<>();
    final List<Carve> carves = new ArrayList<>();
    final List<LeafCluster> leaves = new ArrayList<>();
    private final Map<String, List<BlockPos>> points = new HashMap<>();
    double footRadius;
    int crown = Integer.MIN_VALUE;

    Sketch(TerrainProbe probe, RandomSource random, double vertical, int buildTop, int x, int z, int ground) {
        this.probe = probe;
        this.random = random;
        this.v = vertical;
        this.buildTop = buildTop;
        this.x = x;
        this.z = z;
        this.ground = ground;
    }

    double range(double min, double max) {
        return Mth.lerp(random.nextDouble(), min, max);
    }

    int range(int min, int max) {
        return min + random.nextInt(max - min + 1);
    }

    /** A height above the ground, scaled for the height mode and kept clear of the build limit. */
    int rise(int from, double blocks) {
        return Math.min(from + (int) Math.round(blocks * v), buildTop - 8);
    }

    /** The ground near a column, searched from this giant's own ground; MIN_VALUE over a void. */
    int groundAt(double px, double pz) {
        return probe.surfaceNear(Mth.floor(px), Mth.floor(pz), ground, ground - 48, ground + 48);
    }

    Trunk trunk(Trunk trunk) {
        wood.add(trunk);
        return trunk;
    }

    Tube tube(Tube tube) {
        wood.add(tube);
        return tube;
    }

    void part(Part part) {
        wood.add(part);
    }

    void carve(Carve carve) {
        carves.add(carve);
    }

    void point(String kind, double px, double py, double pz) {
        points.computeIfAbsent(kind, k -> new ArrayList<>()).add(BlockPos.containing(px, py, pz));
    }

    /**
     * A domed crown of limbs spreading from a point: each limb sweeps out and up and forks, its branches sprout twigs,
     * and every twig and branch end carries a clump of leaves, so the clumps overlap into one canopy. {@code leanX}
     * and {@code leanZ} push the whole crown outward, so trees round a clearing hold their canopies away from it.
     */
    void crown(double ax, double ay, double az, double size, double limbRadius, double leanX, double leanZ) {
        int limbs = range(4, 7);
        double phase = random.nextDouble() * Mth.TWO_PI;
        for (int i = 0; i < limbs; i++) {
            double angle = phase + i * Mth.TWO_PI / limbs + range(-0.35, 0.35);
            double length = size * range(0.7, 1.0);
            double[] d = direction(angle, range(0.3, 0.8), leanX, leanZ);
            Tube limb = branch(ax, ay, az, d, length, limbRadius);
            int forks = range(1, 2);
            for (int f = 0; f < forks; f++) {
                double[] p = limb.at(range(0.35, 0.7));
                double[] fd = direction(angle + (random.nextBoolean() ? 1 : -1) * range(0.5, 0.9), range(0.3, 0.7),
                        leanX, leanZ);
                branch(p[0], p[1], p[2], fd, length * range(0.4, 0.6), Math.max(0.85, p[3] * 0.6));
            }
        }
    }

    /** A unit vector at a compass angle and an elevation, pushed by a lean. */
    private static double[] direction(double angle, double elevation, double leanX, double leanZ) {
        double dx = Math.cos(angle) * Math.cos(elevation) + leanX, dy = Math.sin(elevation),
                dz = Math.sin(angle) * Math.cos(elevation) + leanZ;
        double norm = Math.sqrt(dx * dx + dy * dy + dz * dz);
        return new double[]{dx / norm, dy / norm, dz / norm};
    }

    /** One limb or branch of a crown, bowing gently upward, with twigs along its outer half and a clump at its end. */
    private Tube branch(double ax, double ay, double az, double[] d, double length, double radius) {
        double ex = ax + d[0] * length, ey = ay + d[1] * length * v, ez = az + d[2] * length;
        Tube limb = tube(Tube.bezier(new double[]{ax, ay, az},
                new double[]{ax + d[0] * length * 0.2, ay + length * 0.3 * v, az + d[2] * length * 0.2},
                new double[]{ex - d[0] * length * 0.3, ey + length * 0.1 * v, ez - d[2] * length * 0.3},
                new double[]{ex, ey, ez}, radius, Math.max(0.8, radius * 0.35), 6, Part.Role.LOG));
        int twigs = Math.max(2, (int) Math.round(length / 3.5));
        for (int j = 0; j < twigs; j++) {
            double[] p = limb.at(range(0.35, 1.0));
            twig(p[0], p[1], p[2], d[0] + range(-0.9, 0.9), range(-0.1, 0.7), d[2] + range(-0.9, 0.9),
                    range(2.5, 4.5));
        }
        twig(ex, ey, ez, d[0] * 0.4, 1, d[2] * 0.4, range(1.5, 3.0));
        crown = Math.max(crown, (int) Math.ceil(ey + LeafCluster.MAX_RY));
        return limb;
    }

    /** A thin log from a point along a direction, wrapped in a clump of leaves. */
    void twig(double px, double py, double pz, double dx, double dy, double dz, double length) {
        double norm = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double qx = px + dx / norm * length, qy = py + dy / norm * length, qz = pz + dz / norm * length;
        wood.add(Tube.line(px, py, pz, qx, qy, qz, 0.85, 0.7, Part.Role.LOG));
        leaves.add(new LeafCluster(px, py, pz, qx, qy, qz, range(2.6, LeafCluster.MAX_RX),
                range(1.8, LeafCluster.MAX_RY)));
    }

    /**
     * Buttress roots round a trunk foot: thick where they leave the trunk, tapering as they snake out over the ground
     * and dive into it. Over a cliff edge they hang down instead.
     */
    void buttresses(double cx, double cz, double radius, int count, double length) {
        buttresses(cx, cz, radius, count, length, 0, 0);
    }

    /** Buttress roots that keep clear of a line through the trunk at {@code avoid} radians, to either side. */
    void buttresses(double cx, double cz, double radius, int count, double length, double avoid, double clearance) {
        double phase = random.nextDouble() * Mth.TWO_PI;
        for (int i = 0; i < count; i++) {
            double angle = phase + i * Mth.TWO_PI / count + range(-0.35, 0.35);
            double off = Math.abs(Mth.wrapDegrees((angle - avoid) * Mth.RAD_TO_DEG)) * Mth.DEG_TO_RAD;
            if (Math.min(off, Math.PI - off) < clearance) {
                continue;
            }
            double reach = length * range(0.7, 1.2);
            int n = 5;
            double[] xs = new double[n], ys = new double[n], zs = new double[n], rs = new double[n];
            double last = ground;
            double wander = range(-0.25, 0.25);
            for (int k = 0; k < n; k++) {
                double t = k / (double) (n - 1);
                double d = radius * 0.6 + (radius * 0.4 + reach) * t;
                double a = angle + wander * t;
                xs[k] = cx + Math.cos(a) * d;
                zs[k] = cz + Math.sin(a) * d;
                int g = groundAt(xs[k], zs[k]);
                double surface = g == Integer.MIN_VALUE ? last - 4 : g;
                last = surface;
                ys[k] = k == 0 ? ground + radius * 0.9 : surface + 0.4 - t * 1.2;
                rs[k] = Mth.lerp(t, radius * 0.5, 0.9);
            }
            wood.add(new Tube(xs, ys, zs, rs, Part.Role.LOG, Integer.MIN_VALUE));
        }
    }

    Giant done(GiantType type, GrovePalette palette, long salt) {
        Bounds bounds = Bounds.EMPTY;
        for (Part part : wood) {
            bounds = bounds.union(part.bounds());
        }
        for (Carve carve : carves) {
            bounds = bounds.union(carve.bounds());
        }
        for (LeafCluster cluster : leaves) {
            bounds = bounds.union(cluster.bounds());
        }
        Map<String, List<BlockPos>> sealed = new HashMap<>();
        points.forEach((kind, list) -> sealed.put(kind, List.copyOf(list)));
        return new Giant(type, x, z, ground, footRadius, crown, List.copyOf(wood), List.copyOf(carves),
                List.copyOf(leaves), Map.copyOf(sealed), palette, bounds, salt);
    }
}
