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
import com.linc.alteredaether.worldgen.TerrainProbe;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
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
    /** Atoll cells are this times the configured atoll size: room for a double atoll or a wide ring. */
    static final int ATOLL_CELL = 320;
    /**
     * Cells of the second, finer atoll grid, for small single atolls in the gaps the big ones leave, so the Sunken
     * Atoll is crowded with lagoons. A small atoll stands only clear of every big one. The Sunken Atoll has no other
     * islands to blend into, so a small atoll's influence reaches only a little beyond its moat.
     */
    static final int MINOR_CELL = 72;
    private static final double MINOR_INFLUENCE = 8;
    /** Spots tried per cell before it is left dry. */
    private static final int ATTEMPTS = 10;
    /** Rim directions, in sixteenths of a turn, in the order they're checked. */
    private static final int[] RIM_ORDER = {0, 4, 8, 12, 2, 6, 10, 14, 1, 3, 5, 7, 9, 11, 13, 15};
    private static final String FROZEN_CRAGS = AABiomes.FROZEN_CRAGS.location().toString();
    /** Basin types that come from the small grid, in the order they're rolled: the rare one gets first refusal. */
    private static final BasinType[] SMALL = {BasinType.DEEP_LAKE, BasinType.POOL, BasinType.CASCADING_LAKE,
            BasinType.FROZEN_POOL, BasinType.POISON_POOL};
    private static final long BASIN_SALT = 0x5DEECE66DL, ATOLL_SALT = 0x2545F4914F6CDD1DL,
            MINOR_SALT = 0x9E3779B97F4A7C15L;
    /** The share of atolls that are double, before the landmark rarity multiplier. */
    private static final double DOUBLE_ATOLL_CHANCE = 0.45;
    /** The chance a fine-grid cell over the Sunken Atoll holds a small atoll. */
    private static final double MINOR_ATOLL_CHANCE = 0.9;
    /** The share of the other atolls that are rings with a river round them. Frozen lakes are never rings. */
    private static final double RING_CHANCE = 0.3;
    /** Spots tried per atoll cell: the atoll biomes are big but patchy, and a miss leaves a whole cell empty. */
    private static final int ATOLL_ATTEMPTS = 24;

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
    public record AtollSettings(int surface, int frozenSurface, double vertical) {
    }

    private final long seed;
    private final TerrainProbe probe;
    private final boolean carves;
    private final double vertical;
    @Nullable
    private final AtollSettings atoll;
    private final int minY, maxY;
    private final double[] chances = new double[BasinType.values().length];
    private final double atollSize;
    private final int atollCell, minorCell;

    /** Why candidates were turned down, for the debug tools' tuning output. */
    private final Map<String, java.util.concurrent.atomic.AtomicInteger> rejections = new ConcurrentHashMap<>();

    private final Cache<Long, Optional<BasinSite>> basins = CacheBuilder.newBuilder().maximumSize(1 << 14).build();
    private final Cache<Long, Optional<AtollSite>> atolls = CacheBuilder.newBuilder().maximumSize(1 << 12).build();
    private final Cache<Long, Optional<AtollSite>> minors = CacheBuilder.newBuilder().maximumSize(1 << 13).build();

    /**
     * @param probe    the terrain before basins are cut; when it has none, the world has no basin carve and only atoll
     *                 lagoons, which shape themselves, are filled
     * @param vertical vertical scale for the height mode: 1 in the tall Aether
     */
    public BasinField(long seed, TerrainProbe probe, double vertical, @Nullable AtollSettings atoll) {
        this.seed = seed;
        this.probe = probe;
        this.carves = probe.terrain() != null;
        this.vertical = vertical;
        this.atoll = atoll;
        this.minY = probe.minY();
        this.maxY = probe.maxY();
        for (BasinType type : BasinType.values()) {
            chances[type.ordinal()] = type.chance();
        }
        this.atollSize = AACommonConfig.ATOLL_SIZE.get();
        this.atollCell = (int) Math.round(ATOLL_CELL * atollSize);
        this.minorCell = (int) Math.round(MINOR_CELL * atollSize);
    }

    // ---------------------------------------------------------------------------------------------- queries --

    /** Every basin whose extent reaches into the given block rectangle, atoll lagoons included. */
    public List<BasinSite> sitesTouching(int minX, int minZ, int maxX, int maxZ) {
        List<BasinSite> found = new ArrayList<>(2);
        if (carves) {
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
            reach = minorCell / 2;
            for (int cx = Math.floorDiv(minX - reach, minorCell); cx <= Math.floorDiv(maxX + reach, minorCell); cx++) {
                for (int cz = Math.floorDiv(minZ - reach, minorCell); cz <= Math.floorDiv(maxZ + reach, minorCell); cz++) {
                    minor(cx, cz).map(AtollSite::lagoonBasin)
                            .filter(site -> touches(site.x(), site.z(), site.extent(), minX, minZ, maxX, maxZ))
                            .ifPresent(found::add);
                }
            }
        }
        return found;
    }

    /** The atoll in a grid cell, if it has one. */
    public Optional<AtollSite> atollInCell(int cx, int cz) {
        return atoll == null ? Optional.empty() : atoll(cx, cz);
    }

    public int atollCell() {
        return atollCell;
    }

    /**
     * The terrain density at a block with the atolls laid over it. Each atoll stays inside its own cell, and a small
     * one stands only clear of every big one, so at most one of the two touches any block.
     */
    public double atollDensity(int x, int y, int z, double terrain) {
        if (atoll == null) {
            return terrain;
        }
        Optional<AtollSite> major = atoll(Math.floorDiv(x, atollCell), Math.floorDiv(z, atollCell));
        double density = major.isEmpty() ? terrain : major.get().density(x, y, z, terrain);
        Optional<AtollSite> minor = minor(Math.floorDiv(x, minorCell), Math.floorDiv(z, minorCell));
        return minor.isEmpty() ? density : minor.get().density(x, y, z, density);
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
        // The water may sit at most 5 below the centre (no hill in the middle) and 9 below the highest rim, 12 for tarns (no cliff
        // left hanging over the bank cone), so the search stops as soon as either is out of reach. Compass points
        // go first: on a slope they fail soonest.
        int lowest = Integer.MAX_VALUE, highest = Integer.MIN_VALUE;
        // Mountain tarns may sit under a taller back wall: the crags are mostly slope, and the bank cone clears 12.
        int hill = type == BasinType.FROZEN_POOL ? 12 : 9;
        int[] rimX = new int[16], rimZ = new int[16];
        for (int i = 0; i < 16; i++) {
            int d = RIM_ORDER[i];
            double angle = d * Math.PI / 8;
            double r = radius * BasinSite.wobble(angle, phase1, phase2) + 3;
            rimX[d] = x + (int) Math.round(Math.cos(angle) * r);
            rimZ[d] = z + (int) Math.round(Math.sin(angle) * r);
            int top = surfaceNear(rimX[d], rimZ[d], centre, centre - 5, centre + hill + 1);
            if (top == Integer.MIN_VALUE) {
                return reject("no_rim");
            }
            lowest = Math.min(lowest, top);
            highest = Math.max(highest, top);
            if (highest - (lowest - 1) > hill) {
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

    /**
     * The cell's atoll or frozen lake. Its shape is rolled once for the cell: a double atoll or a ring with
     * a river round it, or a single ring around a lagoon. Each spot tried then takes whichever of the two types suits
     * its biome.
     */
    @Nullable
    private AtollSite findAtoll(int cx, int cz) {
        AtollSettings settings = atoll;
        if (settings == null) {
            return null;
        }
        RandomSource random = random(cx, cz, ATOLL_SALT);
        double roll = random.nextDouble();
        double r1 = random.nextDouble(), r2 = random.nextDouble(), r3 = random.nextDouble(), offset = random.nextDouble();
        double phase1 = random.nextDouble() * Mth.TWO_PI, phase2 = random.nextDouble() * Mth.TWO_PI;
        double v = settings.vertical();
        // A rare landmark: a wider lagoon holding a second ring island, with a hill of an islet at its heart.
        double rarity = AACommonConfig.LANDMARK_RARITY.get();
        boolean wantsDouble = rarity > 0 && random.nextDouble() < DOUBLE_ATOLL_CHANCE / rarity;
        boolean ring = !wantsDouble && random.nextDouble() < RING_CHANCE;
        RingPath path = ringPath(random);
        double wide = Mth.lerp(random.nextDouble(), 52, 62) * atollSize;
        double wideOuter = wide + Mth.lerp(random.nextDouble(), 14, 18) * atollSize;
        double wideDepth = Mth.lerp(random.nextDouble(), 11, 15) * v;
        AtollSite.Inner inner = new AtollSite.Inner(Mth.lerp(random.nextDouble(), 0.42, 0.52),
                Mth.lerp(random.nextDouble(), 5, 7) * atollSize, Mth.lerp(random.nextDouble(), 6, 8) * atollSize,
                random.nextDouble() * Mth.TWO_PI, Mth.lerp(random.nextDouble(), 0.5, 1.5) * Mth.PI);
        boolean doubleFits = wantsDouble && wideOuter * BasinSite.MAX_WOBBLE + AtollSite.INFLUENCE <= atollCell / 2.0 - 2;

        AtollSite[] shapes = new AtollSite[BasinType.values().length];
        double largest = 0;
        for (BasinType type : new BasinType[]{BasinType.ATOLL_LAGOON, BasinType.FROZEN_LAKE}) {
            if (roll >= chances[type.ordinal()]) {
                continue;
            }
            boolean frozen = type == BasinType.FROZEN_LAKE;
            int top = (frozen ? settings.frozenSurface() : settings.surface()) + (int) Math.round((offset * 12 - 6) * v);
            AtollSite shape;
            if (ring && !frozen) {
                // Half the ring's width: enough for a shore either side of the river. The line is shrunk if need be
                // so the ring and its moat stay inside the cell.
                double width = Mth.lerp(r2, 11, 16) * atollSize;
                double room = atollCell / 2.0 - 2 - AtollSite.INFLUENCE - width;
                RingPath line = path.furthest() > room ? path.scaled(room / path.furthest()) : path;
                shape = new AtollSite(type, 0, 0, width, line.furthest() + width, top, Mth.lerp(r3, 6, 9) * v, v,
                        phase1, phase2, null, line);
            } else if (doubleFits && !frozen) {
                shape = new AtollSite(type, 0, 0, wide, wideOuter, top, wideDepth, v, phase1, phase2, inner, null);
            } else {
                double lagoon = Mth.lerp(r1, type.minRadius, type.maxRadius) * atollSize;
                // The Sunken Atoll's ring is a narrow reef of land, so the lagoon takes most of the island.
                double outer = lagoon + (frozen ? Mth.lerp(r2, 12, 18) : Mth.lerp(r2, 9, 13)) * atollSize;
                shape = new AtollSite(type, 0, 0, lagoon, outer, top, Mth.lerp(r3, type.minDepth, type.maxDepth) * v,
                        v, phase1, phase2, null, null);
            }
            shapes[type.ordinal()] = shape;
            largest = Math.max(largest, shape.extent());
        }
        if (largest == 0) {
            return null;
        }
        // One jitter for both types, from the larger, so whichever is placed stays inside its cell.
        int jitter = Math.max(0, (int) (atollCell / 2.0 - largest));
        for (int attempt = 0; attempt < ATOLL_ATTEMPTS; attempt++) {
            int x = cx * atollCell + atollCell / 2 + random.nextInt(2 * jitter + 1) - jitter;
            int z = cz * atollCell + atollCell / 2 + random.nextInt(2 * jitter + 1) - jitter;
            String biome = biomeAt(x, z);
            for (AtollSite shape : shapes) {
                if (shape != null && shape.type().suits(biome) && atollFits(shape, x, z)) {
                    return new AtollSite(shape.type(), x, z, shape.lagoon(), shape.outer(), shape.top(),
                            shape.depth(), v, phase1, phase2, shape.inner(), shape.ring());
                }
            }
        }
        return null;
    }

    /**
     * A ring's centre line: anything from a small round loop to a long, twisting oval well over two hundred blocks
     * across. Small rings are the commonest.
     */
    private RingPath ringPath(RandomSource random) {
        double radius = Mth.lerp(Math.pow(random.nextDouble(), 1.4), 26, 105) * atollSize;
        double aspect = Mth.lerp(random.nextDouble(), 1.0, 1.9);
        double rotation = random.nextDouble() * Mth.PI;
        double[] amplitudes = new double[RingPath.harmonics()], phases = new double[RingPath.harmonics()];
        double[] most = {0.1, 0.13, 0.07};
        for (int k = 0; k < amplitudes.length; k++) {
            amplitudes[k] = random.nextDouble() * most[k];
            phases[k] = random.nextDouble() * Mth.TWO_PI;
        }
        // The radius describes the oval's mean size, so a stretched ring is that much bigger along its length.
        return new RingPath(radius / Math.sqrt(aspect), aspect, rotation, amplitudes, phases);
    }

    // ------------------------------------------------------------------------------------ small atoll sites --

    private Optional<AtollSite> minor(int cx, int cz) {
        return cached(minors, cx, cz, () -> timed(() -> Optional.ofNullable(findMinor(cx, cz))));
    }

    /**
     * A fine-grid cell's small single atoll: a lagoon 18 to 30 blocks across in a thin ring of land, standing clear
     * of every big atoll near it. A big ring's open middle counts as clear.
     */
    @Nullable
    private AtollSite findMinor(int cx, int cz) {
        AtollSettings settings = atoll;
        if (settings == null) {
            return null;
        }
        RandomSource random = random(cx, cz, MINOR_SALT);
        double roll = random.nextDouble();
        double v = settings.vertical();
        double lagoon = Mth.lerp(random.nextDouble(), 9, 15) * atollSize;
        double outer = lagoon + Mth.lerp(random.nextDouble(), 4, 7) * atollSize;
        double depth = Mth.lerp(random.nextDouble(), 8, 11) * v;
        double offset = random.nextDouble();
        double phase1 = random.nextDouble() * Mth.TWO_PI, phase2 = random.nextDouble() * Mth.TWO_PI;
        double extent = outer * BasinSite.MAX_WOBBLE + MINOR_INFLUENCE;
        int jitter = Math.max(0, (int) (minorCell / 2.0 - extent));
        for (int attempt = 0; attempt < ATOLL_ATTEMPTS / 2; attempt++) {
            int x = cx * minorCell + minorCell / 2 + random.nextInt(2 * jitter + 1) - jitter;
            int z = cz * minorCell + minorCell / 2 + random.nextInt(2 * jitter + 1) - jitter;
            String biome = biomeAt(x, z);
            BasinType type = BasinType.ATOLL_LAGOON;
            if (!type.suits(biome) || roll >= chances[type.ordinal()] * MINOR_ATOLL_CHANCE) {
                continue;
            }
            int top = settings.surface() + (int) Math.round((offset * 12 - 6) * v);
            AtollSite site = new AtollSite(type, x, z, lagoon, outer, top, depth, v, phase1, phase2, null, null,
                    MINOR_INFLUENCE);
            if (!clearOfMajors(site)) {
                reject("minor_crowded");
                continue;
            }
            if (atollFits(site, x, z)) {
                return site;
            }
        }
        return null;
    }

    /** Whether a small atoll's shore stays a moat's width from every big site's land. */
    private boolean clearOfMajors(AtollSite site) {
        int gx = Math.floorDiv(site.x(), atollCell), gz = Math.floorDiv(site.z(), atollCell);
        double reach = site.outer() * BasinSite.MAX_WOBBLE + 2 * AtollSite.MOAT;
        for (int cx = gx - 1; cx <= gx + 1; cx++) {
            for (int cz = gz - 1; cz <= gz + 1; cz++) {
                AtollSite major = atoll(cx, cz).orElse(null);
                if (major == null) {
                    continue;
                }
                double dx = site.x() - major.x(), dz = site.z() - major.z();
                boolean crowded = major.isRing()
                        ? major.ring().distance(dx, dz) < major.lagoon() + reach
                        : Math.hypot(dx, dz) < major.outer() * BasinSite.MAX_WOBBLE + reach;
                if (crowded) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * In the site's biome at the centre and on at least two of four points part way out (half way to the beach, or on
     * a ring's centre line), and wholly inside our region. A lake on a biome border keeps its shape and simply takes
     * the neighbouring biome's surface where it crosses over.
     */
    private boolean atollFits(AtollSite site, int x, int z) {
        if (probe.regionWeight(x, z) < 0.99) {
            return false;
        }
        double check = site.isRing() ? (site.ring().nearest() + site.ring().furthest()) / 2 : site.lagoon() * 0.5;
        double extent = site.extent();
        int outside = 0;
        for (int d = 0; d < 8; d++) {
            double angle = d * Math.PI / 4;
            double cos = Math.cos(angle), sin = Math.sin(angle);
            if (d % 2 == 0 && !site.type().suits(biomeAt(x + (int) (cos * check), z + (int) (sin * check)))) {
                outside++;
            }
            if (outside > 2 || probe.regionWeight(x + (int) (cos * extent), z + (int) (sin * extent)) < 0.99) {
                reject("atoll_edge");
                return false;
            }
        }
        return true;
    }

    // -------------------------------------------------------------------------------------------- sampling --

    private String biomeAt(int x, int z) {
        return probe.biomeAt(x, z);
    }

    private boolean solid(int x, int y, int z) {
        return probe.solid(x, y, z);
    }

    private int surface(int x, int z, int from, int to) {
        return probe.surface(x, z, from, to);
    }

    private int surfaceNear(int x, int z, int guess, int low, int high) {
        return probe.surfaceNear(x, z, guess, low, high);
    }

    /** A random source of its own for a grid point, for decoration that every chunk must build the same way. */
    public RandomSource randomAt(long salt, int x, int z) {
        return random(x, z, salt);
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

    public static long mix(long z) {
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
