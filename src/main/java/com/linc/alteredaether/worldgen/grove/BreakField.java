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

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.linc.alteredaether.config.AACommonConfig;
import com.linc.alteredaether.worldgen.TerrainProbe;
import com.linc.alteredaether.worldgen.basin.BasinField;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

/**
 * Where the Nurselog Hollow's islands have broken apart, with no living roots left to hold them: as a pure function of
 * the seed, like the other fields. Mostly a bite of an island's edge has cracked away and drifted out over the void,
 * sunk and tipped a little; much more rarely a crack runs right across an island and the far side has drifted off.
 * <p>
 * The piece that moves is whatever of the island lies within a circle centred out over the void, so the crack is the
 * part of the circle that crosses land. A site is kept only where, by the unbroken terrain, the circle crosses land
 * nowhere else, so the piece moves whole. The terrain function ({@code grove_break}) does the moving; the grove field
 * lays a fallen giant across the gap, the tree whose fall began it.
 */
public final class BreakField {
    public enum Kind {
        EDGE, CHASM;

        public String id() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /**
     * One break.
     *
     * @param x      the point of the crack nearest the island's heart, where it crosses land
     * @param nx     the unit direction from there out across the piece that moved
     * @param radius the circle the piece lies within, centred {@code radius} out from (x, z) along the normal
     * @param gap    how far the piece has drifted out along the normal
     * @param slide  how far it has slid sideways
     * @param sink   how far it has sunk, at the crack
     * @param tilt   how much further it sinks per block out from the crack
     * @param ground the ground at (x, z)
     */
    public record Site(Kind kind, double x, double z, double nx, double nz, double radius, double gap, double slide,
                       double sink, double tilt, double phase, int ground, long salt) {
        public double centreX() {
            return x + nx * radius;
        }

        public double centreZ() {
            return z + nz * radius;
        }

        /**
         * How far inside a chasm's fissure a point lies: a band {@code gap} wide running out from (x, z) along the
         * normal's side, {@code radius} each way along the crack, wandering and ragged, pinching shut at its ends.
         */
        public double fissure(double px, double py, double pz) {
            double dx = px - x, dz = pz - z;
            double t = -dx * nz + dz * nx;
            double end = t / radius;
            if (end <= -1 || end >= 1) {
                return -Double.MAX_VALUE;
            }
            double a = dx * nx + dz * nz - gap / 2 + 7 * Math.sin(t * 0.018 + phase)
                    + 1.4 * Math.sin(t * 0.21 + phase * 2.3) + 0.8 * Math.sin(py * 0.45 + t * 0.29 + phase);
            double half = gap / 2 * (1 - end * end * end * end);
            return half - Math.abs(a);
        }

        /** How far inside the moved piece's circle a point lies, its edge ragged as a break is. */
        public double inside(double px, double py, double pz) {
            double dx = px - centreX(), dz = pz - centreZ();
            double t = Math.atan2(dz, dx) * radius;
            return radius - Math.sqrt(dx * dx + dz * dz) + 2.5 * Math.sin(t * 0.11 + phase)
                    + 1.2 * Math.sin(t * 0.37 + phase * 2.3) + 0.8 * Math.sin(py * 0.45 + t * 0.29 + phase);
        }

        /** Where a point of the moved piece came from: x, y, z. */
        public double[] source(double px, double py, double pz) {
            double out = Math.max(0, (px - x) * nx + (pz - z) * nz - gap);
            return new double[]{px - nx * gap + nz * slide, py + sink + tilt * out, pz - nz * gap - nx * slide};
        }

        public boolean touches(int minX, int minZ, int maxX, int maxZ) {
            if (kind == Kind.CHASM) {
                double r = radius + gap + 8;
                double dx = Math.max(0, Math.max(minX - x, x - maxX)), dz = Math.max(0, Math.max(minZ - z, z - maxZ));
                return dx * dx + dz * dz <= r * r;
            }
            double cx = centreX(), cz = centreZ(), r = radius + gap + Math.abs(slide) + 4;
            double dx = Math.max(0, Math.max(minX - cx, cx - maxX)), dz = Math.max(0, Math.max(minZ - cz, cz - maxZ));
            return dx * dx + dz * dz <= r * r;
        }
    }

    private static final int CELL = 320;
    private static final int ATTEMPTS = 6;

    private final long seed;
    private final TerrainProbe probe;
    private final double vertical;
    private final Cache<Long, Optional<Site>> sites = CacheBuilder.newBuilder().maximumSize(1 << 12).build();

    /** @param probe the grove islands as they are before anything breaks */
    public BreakField(long seed, TerrainProbe probe, double vertical) {
        this.seed = seed;
        this.probe = probe;
        this.vertical = vertical;
    }

    /** Every break whose moving piece reaches into a block rectangle. */
    public List<Site> touching(int minX, int minZ, int maxX, int maxZ) {
        List<Site> found = new ArrayList<>(1);
        int reach = 560;
        for (int cx = Math.floorDiv(minX - reach, CELL); cx <= Math.floorDiv(maxX + reach, CELL); cx++) {
            for (int cz = Math.floorDiv(minZ - reach, CELL); cz <= Math.floorDiv(maxZ + reach, CELL); cz++) {
                site(cx, cz).filter(s -> s.touches(minX, minZ, maxX, maxZ)).ifPresent(found::add);
            }
        }
        return found;
    }

    /** The nearest break of a kind to a point, searching out ring by ring of cells. */
    public Optional<Site> nearest(Kind kind, int x, int z, int maxDistance) {
        int ox = Math.floorDiv(x, CELL), oz = Math.floorDiv(z, CELL);
        for (int ring = 0; ring <= maxDistance / CELL + 1; ring++) {
            Site best = null;
            double distance = Double.MAX_VALUE;
            for (int cx = ox - ring; cx <= ox + ring; cx++) {
                for (int cz = oz - ring; cz <= oz + ring; cz++) {
                    if (Math.max(Math.abs(cx - ox), Math.abs(cz - oz)) != ring) {
                        continue;
                    }
                    Optional<Site> site = site(cx, cz).filter(s -> s.kind() == kind);
                    if (site.isPresent() && Math.hypot(site.get().x() - x, site.get().z() - z) < distance) {
                        distance = Math.hypot(site.get().x() - x, site.get().z() - z);
                        best = site.get();
                    }
                }
            }
            if (best != null) {
                return Optional.of(best);
            }
        }
        return Optional.empty();
    }

    static long key(Site site) {
        return Math.floorDiv((long) site.x(), CELL) << 32 ^ (Math.floorDiv((long) site.z(), CELL) & 0xFFFFFFFFL);
    }

    Optional<Site> site(int cx, int cz) {
        long key = ((long) cx << 32) ^ (cz & 0xFFFFFFFFL);
        try {
            return sites.get(key, () -> Optional.ofNullable(find(cx, cz)));
        } catch (ExecutionException e) {
            throw new IllegalStateException(e.getCause());
        }
    }

    @Nullable
    private Site find(int cx, int cz) {
        RandomSource random = new XoroshiroRandomSource(BasinField.mix(seed ^ 0x3C6EF372FE94F82BL)
                ^ BasinField.mix(cx * 0x9E3779B97F4A7C15L + cz));
        if (random.nextDouble() >= AACommonConfig.GROVE_BREAK_CHANCE.get()) {
            return null;
        }
        Kind kind = random.nextDouble() < AACommonConfig.GROVE_CHASM_SHARE.get() ? Kind.CHASM : Kind.EDGE;
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            // The crack's point stays in its own cell, so a site found from any cell is the same site.
            int x = cx * CELL + 20 + random.nextInt(CELL - 40), z = cz * CELL + 20 + random.nextInt(CELL - 40);
            if (!probe.biomeAt(x, z).equals(GroveField.NURSELOG) || probe.regionWeight(x, z) < 0.99) {
                continue;
            }
            int ground = probe.surface(x, z);
            if (ground == Integer.MIN_VALUE) {
                continue;
            }
            Site site = kind == Kind.EDGE ? edge(random, x, z, ground) : chasm(random, x, z, ground);
            if (site != null) {
                return site;
            }
        }
        return null;
    }

    /** A bite of the island's edge, broken off toward the nearest void. */
    @Nullable
    private Site edge(RandomSource random, int x, int z, int ground) {
        double bestAngle = 0, bestDistance = Double.MAX_VALUE;
        for (int d = 0; d < 16; d++) {
            double angle = d * Mth.TWO_PI / 16;
            for (int r = 8; r <= 60; r += 4) {
                if (!land(x + Math.cos(angle) * r, z + Math.sin(angle) * r, ground)) {
                    if (r < bestDistance) {
                        bestDistance = r;
                        bestAngle = angle;
                    }
                    break;
                }
            }
        }
        if (bestDistance < 20 || bestDistance > 48) {
            return null;
        }
        double nx = Math.cos(bestAngle), nz = Math.sin(bestAngle);
        double radius = bestDistance * Mth.lerp(random.nextDouble(), 0.9, 1.4) + 8;
        Site site = new Site(Kind.EDGE, x, z, nx, nz, radius, Mth.lerp(random.nextDouble(), 8, 20),
                Mth.lerp(random.nextDouble(), -4, 4), Mth.lerp(random.nextDouble(), 3, 10) * vertical,
                random.nextDouble() * 0.12, random.nextDouble() * Mth.TWO_PI, ground, random.nextLong());
        return farBiome(site, bestDistance / 2) && rimClear(site, ground, 110) ? site : null;
    }

    /**
     * A fissure right across the island, splitting it in two with open sky between: the halves are too broad to have
     * drifted, so they stand where they were. It runs well past the island's edges, pinching shut out over the void.
     */
    @Nullable
    private Site chasm(RandomSource random, int x, int z, int ground) {
        double angle = random.nextDouble() * Mth.TWO_PI;
        double nx = Math.cos(angle), nz = Math.sin(angle);
        double gap = Mth.lerp(random.nextDouble(), 7, 13);
        if (!land(x - nx * 12, z - nz * 12, ground) || !land(x + nx * (gap + 12), z + nz * (gap + 12), ground)) {
            return null;
        }
        Site site = new Site(Kind.CHASM, x, z, nx, nz, 170, gap, 0, 0, 0, random.nextDouble() * Mth.TWO_PI, ground,
                random.nextLong());
        return farBiome(site, gap + 20) ? site : null;
    }

    /** Whether the moved side, a way out from the crack, still lies in the groves. */
    private boolean farBiome(Site site, double out) {
        String biome = probe.biomeAt((int) Math.round(site.x() + site.nx() * out),
                (int) Math.round(site.z() + site.nz() * out));
        return biome.equals(GroveField.NURSELOG) || biome.equals(GroveField.CATHEDRAL);
    }

    /**
     * Whether the circle round the moved piece crosses land only along the crack, within {@code arc} degrees of the
     * crack's heart either way, so everything that moves is whole. Checked a little inside and outside the circle,
     * since the break's edge is ragged and the piece moves.
     */
    private boolean rimClear(Site site, int ground, double arc) {
        double cx = site.centreX(), cz = site.centreZ();
        double back = Math.atan2(-site.nz(), -site.nx());
        int samples = (int) Math.ceil(Mth.TWO_PI * site.radius() / 10);
        for (int d = 0; d < samples; d++) {
            double angle = d * Mth.TWO_PI / samples;
            double off = Math.abs(Mth.wrapDegrees((angle - back) * Mth.RAD_TO_DEG));
            if (off <= arc) {
                continue;
            }
            for (double r : new double[]{site.radius() - site.gap() - 6, site.radius(), site.radius() + 6}) {
                if (land(cx + Math.cos(angle) * r, cz + Math.sin(angle) * r, ground)) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Whether there is any of the island in a column, within reach of a height. */
    private boolean land(double px, double pz, int ground) {
        return probe.surface(Mth.floor(px), Mth.floor(pz), ground + 40, ground - 90) != Integer.MIN_VALUE;
    }
}
