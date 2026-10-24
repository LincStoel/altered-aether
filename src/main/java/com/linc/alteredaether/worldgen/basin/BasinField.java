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
package com.linc.alteredaether.worldgen.basin;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.linc.alteredaether.config.AACommonConfig;
import com.linc.alteredaether.worldgen.AABiomes;
import net.minecraft.core.QuartPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;

/**
 * Every basin and atoll in one world, as a pure function of the seed: the carve density function, the atoll density
 * function and the basin fill feature all ask this field, so they agree on where the water is without any of them
 * writing outside its own chunk.
 *
 * <p>Sites sit on jittered grids, at most one per cell, kept far enough from the cell's edge that neighbours never
 * overlap. A candidate becomes a site only if it passes validation against the terrain as it is before any basin is
 * cut: the right biome, ground at the centre, a rim that is solid at the water line all the way round, and enough
 * island under the floor. Validation samples the exact, uninterpolated terrain, so it gives the same answer whichever
 * chunk asks first, and the result is cached.
 */
public final class BasinField {
    /** Grid cell for lakes and pools, in blocks. Big enough that the largest lake and its banks fit with room to move. */
    static final int CELL = 96;
    /** Atoll cells are this times the configured atoll size. */
    static final int ATOLL_CELL = 256;
    /** Spots tried per cell before it is left dry. */
    private static final int ATTEMPTS = 10;
    /** Coarse step of the column surface search. */
    private static final int SCAN_STEP = 6;
    /** Rim directions, in sixteenths of a turn, in the order they're checked. */
    private static final int[] RIM_ORDER = {0, 4, 8, 12, 2, 6, 10, 14, 1, 3, 5, 7, 9, 11, 13, 15};
    private static final String FROZEN_CRAGS = AABiomes.FROZEN_CRAGS.location().toString();
    /** Basin types that come from the small grid, in the order they're rolled: the rare one gets first refusal. */
    private static final BasinType[] SMALL = {BasinType.DEEP_LAKE, BasinType.POOL, BasinType.CASCADING_LAKE,
            BasinType.FROZEN_POOL, BasinType.POISON_POOL};
    private static final long BASIN_SALT = 0x5DEECE66DL, ATOLL_SALT = 0x2545F4914F6CDD1DL;

    private static final Map<ResourceKey<Level>, BasinField> FIELDS = new ConcurrentHashMap<>();

    public static void put(ResourceKey<Level> dimension, @Nullable BasinField field) {
        if (field == null) {
            FIELDS.remove(dimension);
        } else {
            FIELDS.put(dimension, field);
        }
    }

    @Nullable
    public static BasinField get(ResourceKey<Level> dimension) {
        return FIELDS.get(dimension);
    }

    /** The parameters of the atoll density function, when the terrain has one. */
    public record AtollSettings(DensityFunction regionWeight, int surface, double vertical) {
    }

    private final long seed;
    private final BiomeSource biomes;
    private final Climate.Sampler sampler;
    @Nullable
    private final DensityFunction terrain;
    private final double vertical;
    @Nullable
    private final AtollSettings atoll;
    private final int minY, maxY;
    private final double[] chances = new double[BasinType.values().length];
    private final double atollSize;
    private final int atollCell;

    /** Why candidates were turned down, for the debug tools' tuning output. */
    private final Map<String, java.util.concurrent.atomic.AtomicInteger> rejections = new ConcurrentHashMap<>();

    private final Cache<Long, Optional<BasinSite>> basins = CacheBuilder.newBuilder().maximumSize(1 << 14).build();
    private final Cache<Long, Optional<AtollSite>> atolls = CacheBuilder.newBuilder().maximumSize(1 << 12).build();

    /**
     * @param terrain  the terrain before basins are cut, or null when the world has no basin carve (then only atoll
     *                 lagoons, which shape themselves, are filled)
     * @param vertical vertical scale for the height mode: 1 in the tall Aether
     */
    public BasinField(long seed, BiomeSource biomes, Climate.Sampler sampler, @Nullable DensityFunction terrain,
                      double vertical, @Nullable AtollSettings atoll, int minY, int maxY) {
        this.seed = seed;
        this.biomes = biomes;
        this.sampler = sampler;
        this.terrain = terrain;
        this.vertical = vertical;
        this.atoll = atoll;
        this.minY = minY;
        this.maxY = maxY;
        for (BasinType type : BasinType.values()) {
            chances[type.ordinal()] = type.chance();
        }
        this.atollSize = AACommonConfig.ATOLL_SIZE.get();
        this.atollCell = (int) Math.round(ATOLL_CELL * atollSize);
    }

    // ---------------------------------------------------------------------------------------------- queries --

    /** Every basin whose extent reaches into the given block rectangle, atoll lagoons included. */
    public List<BasinSite> sitesTouching(int minX, int minZ, int maxX, int maxZ) {
        List<BasinSite> found = new ArrayList<>(2);
        if (terrain != null) {
            int reach = CELL / 2;
            for (int cx = Math.floorDiv(minX - reach, CELL); cx <= Math.floorDiv(maxX + reach, CELL); cx++) {
                for (int cz = Math.floorDiv(minZ - reach, CELL); cz <= Math.floorDiv(maxZ + reach, CELL); cz++) {
                    basin(cx, cz).filter(site -> touches(site.x(), site.z(), site.extent(), minX, minZ, maxX, maxZ))
                            .ifPresent(found::add);
                }
            }
        }
        if (atoll != null) {
            int reach = atollCell / 2;
            for (int cx = Math.floorDiv(minX - reach, atollCell); cx <= Math.floorDiv(maxX + reach, atollCell); cx++) {
                for (int cz = Math.floorDiv(minZ - reach, atollCell); cz <= Math.floorDiv(maxZ + reach, atollCell); cz++) {
                    atoll(cx, cz).map(AtollSite::lagoonBasin)
                            .filter(site -> touches(site.x(), site.z(), site.extent(), minX, minZ, maxX, maxZ))
                            .ifPresent(found::add);
                }
            }
        }
        return found;
    }

    /** The atoll whose influence covers this column, if any. Each atoll stays inside its own cell. */
    public Optional<AtollSite> atollAt(int x, int z) {
        if (atoll == null) {
            return Optional.empty();
        }
        return atoll(Math.floorDiv(x, atollCell), Math.floorDiv(z, atollCell));
    }

    private static boolean touches(int x, int z, double extent, int minX, int minZ, int maxX, int maxZ) {
        double dx = Math.max(0, Math.max(minX - x, x - maxX)), dz = Math.max(0, Math.max(minZ - z, z - maxZ));
        return dx * dx + dz * dz <= extent * extent;
    }

    /** Rejection counts by reason since the world loaded. */
    public Map<String, Integer> rejections() {
        Map<String, Integer> counts = new java.util.TreeMap<>();
        rejections.forEach((reason, count) -> counts.put(reason, count.get()));
        return counts;
    }

    @Nullable
    private <T> T reject(String reason) {
        rejections.computeIfAbsent(reason, r -> new java.util.concurrent.atomic.AtomicInteger()).incrementAndGet();
        return null;
    }

    // ------------------------------------------------------------------------------------------ basin sites --

    private Optional<BasinSite> basin(int cx, int cz) {
        return cached(basins, cx, cz, () -> timed(() -> Optional.ofNullable(findBasin(cx, cz))));
    }

    /** Total time spent finding and validating sites, for the debug tools. */
    private final java.util.concurrent.atomic.AtomicLong searchNanos = new java.util.concurrent.atomic.AtomicLong();

    public double searchMillis() {
        return searchNanos.get() / 1e6;
    }

    private <T> T timed(java.util.function.Supplier<T> search) {
        long start = System.nanoTime();
        try {
            return search.get();
        } finally {
            searchNanos.addAndGet(System.nanoTime() - start);
        }
    }

    /**
     * The cell's basin. Up to {@link #ATTEMPTS} spots are tried; each takes the first basin type that suits its biome
     * and whose chance beats the cell's one roll, so a cell's odds of holding a basin are that type's chance, however
     * small the patch of its biome inside the cell.
     */
    @Nullable
    private BasinSite findBasin(int cx, int cz) {
        RandomSource random = random(cx, cz, BASIN_SALT);
        double roll = random.nextDouble();
        // The largest lake's footprint sets the jitter for every type, which keeps the grid's spacing guarantee simple.
        double margin = BasinType.DEEP_LAKE.maxRadius * BasinSite.MAX_WOBBLE + BasinSite.BANK_HEIGHT * BasinSite.BANK_SLOPE + 2;
        int jitter = Math.max(0, (int) (CELL / 2.0 - margin));
        // Mountain biomes are mostly slope, so their tarns get more tries at finding a ledge.
        int attempts = biomeAt(cx * CELL + CELL / 2, cz * CELL + CELL / 2).equals(FROZEN_CRAGS) ? ATTEMPTS * 3 : ATTEMPTS;
        for (int attempt = 0; attempt < attempts; attempt++) {
            int x = cx * CELL + CELL / 2 + random.nextInt(2 * jitter + 1) - jitter;
            int z = cz * CELL + CELL / 2 + random.nextInt(2 * jitter + 1) - jitter;
            double r1 = random.nextDouble(), r2 = random.nextDouble();
            double phase1 = random.nextDouble() * Mth.TWO_PI, phase2 = random.nextDouble() * Mth.TWO_PI;
            String biome = biomeAt(x, z);
            for (BasinType type : SMALL) {
                if (!type.suits(biome) || roll >= chances[type.ordinal()]) {
                    continue;
                }
                double radius = Mth.lerp(r1, type.minRadius, type.maxRadius);
                double depth = Mth.lerp(r2, type.minDepth, type.maxDepth) * vertical;
                BasinSite site = validate(type, x, z, radius, depth, phase1, phase2);
                if (site != null) {
                    return site;
                }
                break;
            }
        }
        return null;
    }

    @Nullable
    private BasinSite validate(BasinType type, int x, int z, double radius, double depth, double phase1,
                               double phase2) {
        int centre = surface(x, z, maxY - 1, minY);
        if (centre == Integer.MIN_VALUE) {
            return reject("no_ground");
        }
        // The rim, just outside the shore all the way round, sets the water level: one block under its lowest point.
        // The water may sit at most 5 below the centre (no hill in the middle) and 9 below the highest rim (no cliff
        // left hanging over the bank cone), so the search stops as soon as either is out of reach. Compass points
        // go first: on a slope they fail soonest.
        int lowest = Integer.MAX_VALUE, highest = Integer.MIN_VALUE;
        int[] rimX = new int[16], rimZ = new int[16];
        for (int i = 0; i < 16; i++) {
            int d = RIM_ORDER[i];
            double angle = d * Math.PI / 8;
            double r = radius * BasinSite.wobble(angle, phase1, phase2) + 3;
            rimX[d] = x + (int) Math.round(Math.cos(angle) * r);
            rimZ[d] = z + (int) Math.round(Math.sin(angle) * r);
            int top = surfaceNear(rimX[d], rimZ[d], centre, centre - 5, centre + 10);
            if (top == Integer.MIN_VALUE) {
                return reject("no_rim");
            }
            lowest = Math.min(lowest, top);
            highest = Math.max(highest, top);
            if (highest - (lowest - 1) > 9) {
                return reject("hill");
            }
        }
        int water = lowest - 1;
        for (int d = 0; d < 16; d++) {
            if (!solid(rimX[d], water, rimZ[d]) || !solid(rimX[d], water - 2, rimZ[d])) {
                return reject("rim_open");
            }
        }
        // Enough island under the floor that the bowl can't punch through it.
        int floor = (int) Math.floor(water + 1 - depth);
        if (!solid(x, floor - 3, z) || !solid(x, floor - 6, z)) {
            return reject("thin_floor");
        }
        for (int d = 0; d < 4; d++) {
            double angle = d * Math.PI / 2 + phase2;
            double r = radius * 0.5 * BasinSite.wobble(angle, phase1, phase2);
            int px = x + (int) (Math.cos(angle) * r), pz = z + (int) (Math.sin(angle) * r);
            double halfFloor = water + 1 - depth * Math.pow(0.75, 0.7);
            if (!solid(px, (int) Math.floor(halfFloor) - 3, pz)) {
                return reject("thin_shelf");
            }
        }
        return new BasinSite(type, x, z, radius, depth, water, phase1, phase2);
    }

    // ------------------------------------------------------------------------------------------ atoll sites --

    private Optional<AtollSite> atoll(int cx, int cz) {
        return cached(atolls, cx, cz, () -> timed(() -> Optional.ofNullable(findAtoll(cx, cz))));
    }

    @Nullable
    private AtollSite findAtoll(int cx, int cz) {
        AtollSettings settings = atoll;
        BasinType type = BasinType.ATOLL_LAGOON;
        RandomSource random = random(cx, cz, ATOLL_SALT);
        if (settings == null || random.nextDouble() >= chances[type.ordinal()]) {
            return null;
        }
        double lagoon = Mth.lerp(random.nextDouble(), type.minRadius, type.maxRadius) * atollSize;
        double outer = lagoon + Mth.lerp(random.nextDouble(), 12, 18) * atollSize;
        double v = settings.vertical();
        int top = settings.surface() + (int) Math.round((random.nextDouble() * 12 - 6) * v);
        double depth = Mth.lerp(random.nextDouble(), type.minDepth, type.maxDepth) * v;
        double phase1 = random.nextDouble() * Mth.TWO_PI, phase2 = random.nextDouble() * Mth.TWO_PI;
        double extent = outer * BasinSite.MAX_WOBBLE + AtollSite.INFLUENCE;
        int jitter = Math.max(0, (int) (atollCell / 2.0 - extent));
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            int x = cx * atollCell + atollCell / 2 + random.nextInt(2 * jitter + 1) - jitter;
            int z = cz * atollCell + atollCell / 2 + random.nextInt(2 * jitter + 1) - jitter;
            if (atollFits(settings, x, z, lagoon, extent)) {
                return new AtollSite(x, z, lagoon, outer, top, depth, v, phase1, phase2);
            }
        }
        return null;
    }

    /** In the atoll biome at the centre and half way to the beach, and wholly inside our region. */
    private boolean atollFits(AtollSettings settings, int x, int z, double lagoon, double extent) {
        BasinType type = BasinType.ATOLL_LAGOON;
        if (!type.suits(biomeAt(x, z)) || regionWeight(settings, x, z) < 0.99) {
            return false;
        }
        for (int d = 0; d < 8; d++) {
            double angle = d * Math.PI / 4;
            double cos = Math.cos(angle), sin = Math.sin(angle);
            if (d % 2 == 0 && !type.suits(biomeAt(x + (int) (cos * lagoon * 0.5), z + (int) (sin * lagoon * 0.5)))
                    || regionWeight(settings, x + (int) (cos * extent), z + (int) (sin * extent)) < 0.99) {
                reject("atoll_edge");
                return false;
            }
        }
        return true;
    }

    private static double regionWeight(AtollSettings settings, int x, int z) {
        return settings.regionWeight().compute(new DensityFunction.SinglePointContext(x, 0, z));
    }

    // -------------------------------------------------------------------------------------------- sampling --

    private String biomeAt(int x, int z) {
        return biomes.getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock((minY + maxY) / 2), QuartPos.fromBlock(z),
                        sampler)
                .unwrapKey().map(key -> key.location().toString()).orElse("");
    }

    private boolean solid(int x, int y, int z) {
        return terrain != null && terrain.compute(new DensityFunction.SinglePointContext(x, y, z)) > 0;
    }

    /**
     * The highest solid block in a column between {@code from} and {@code to}, or MIN_VALUE if there is none. Steps
     * down coarsely, then bisects back up to the exact top; a ledge thinner than a step can be missed, which only
     * costs a candidate.
     */
    private int surface(int x, int z, int from, int to) {
        if (solid(x, from, z)) {
            return from;
        }
        for (int y = from - SCAN_STEP; y >= to - SCAN_STEP + 1; y -= SCAN_STEP) {
            int probe = Math.max(y, to);
            if (solid(x, probe, z)) {
                // Solid at probe, open at probe + step (or above): bisect for the boundary.
                int solidY = probe, openY = Math.min(from, probe + SCAN_STEP);
                while (openY - solidY > 1) {
                    int mid = (solidY + openY) >>> 1;
                    if (solid(x, mid, z)) {
                        solidY = mid;
                    } else {
                        openY = mid;
                    }
                }
                return solidY;
            }
        }
        return Integer.MIN_VALUE;
    }

    /**
     * The top of the ground in a column, searched outward from {@code guess}: up while solid, or down until solid.
     * Returns {@code high} if the ground reaches it and MIN_VALUE if there is none down to {@code low}.
     */
    private int surfaceNear(int x, int z, int guess, int low, int high) {
        if (solid(x, guess, z)) {
            int y = guess;
            while (y < high && solid(x, y + 1, z)) {
                y++;
            }
            return y;
        }
        for (int y = guess - 1; y >= low; y--) {
            if (solid(x, y, z)) {
                return y;
            }
        }
        return Integer.MIN_VALUE;
    }

    private RandomSource random(int cx, int cz, long salt) {
        return new XoroshiroRandomSource(mix(seed ^ salt) ^ mix(cx * 0x9E3779B97F4A7C15L + cz));
    }

    private static <T> T cached(Cache<Long, T> cache, int cx, int cz, Callable<T> loader) {
        long key = ((long) cx << 32) ^ (cz & 0xFFFFFFFFL);
        try {
            return cache.get(key, loader);
        } catch (ExecutionException e) {
            throw new IllegalStateException(e.getCause());
        }
    }

    // --------------------------------------------------------------------------------------------- helpers --

    static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /** Smooth value noise in [-1, 1] with features about {@code scale} blocks across, for patchy decoration. */
    public double noise(long salt, double x, double z, double scale) {
        double fx = x / scale, fz = z / scale;
        int ix = Mth.floor(fx), iz = Mth.floor(fz);
        double tx = fx - ix, tz = fz - iz;
        tx = tx * tx * (3 - 2 * tx);
        tz = tz * tz * (3 - 2 * tz);
        return Mth.lerp2(tx, tz, lattice(salt, ix, iz), lattice(salt, ix + 1, iz), lattice(salt, ix, iz + 1),
                lattice(salt, ix + 1, iz + 1));
    }

    private double lattice(long salt, int x, int z) {
        long h = mix(seed ^ salt ^ mix(x * 0x9E3779B97F4A7C15L + z));
        return (h >>> 11) * 0x1.0p-53 * 2 - 1;
    }
}
