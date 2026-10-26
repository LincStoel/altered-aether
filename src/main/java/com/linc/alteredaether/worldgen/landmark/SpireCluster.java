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
package com.linc.alteredaether.worldgen.landmark;

import com.linc.alteredaether.worldgen.TerrainProbe;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Tempest Peaks' landmark: a crown of storm-black needles, the tallest at the centre and the rest leaning out from it,
 * rising far above the highest peaks, and ringed by copper crystals. Copper veins wind up each needle, scoured bright
 * toward the tips where the lightning finds them, and rods crown the tallest, so the storms strike here first.
 */
record SpireCluster(int x, int z, List<Needle> needles, long salt) implements Landmark {
    /** How far out the ring of copper crystals stands. */
    private static final double RING = 44;

    /**
     * One needle: rooted at {@code base} over (x, z), tapering to its tip, its axis leaning {@code leanX, leanZ}
     * blocks per block of height, fluted with {@code flutes} shallow grooves that twist gently as they rise.
     */
    record Needle(double x, double z, int base, int tip, double radius, double leanX, double leanZ, int flutes,
                  double phase, boolean rod) {
        /** How far inside the needle's surface a block lies, in blocks; negative outside. */
        double margin(int px, int py, int pz) {
            if (py > tip || py < base - ROOT) {
                return -1;
            }
            double rise = Math.max(0, py - base);
            double dx = px - (x + leanX * rise), dz = pz - (z + leanZ * rise);
            double s = (py - base) / (double) (tip - base);
            double r;
            if (s < 0) {
                r = radius * 1.3;
            } else {
                r = radius * Math.pow(1 - s, 0.85);
                if (s < 0.2) {
                    double flare = 1 - s / 0.2;
                    r += radius * 0.8 * flare * flare;
                }
            }
            double angle = Math.atan2(dz, dx);
            r *= 1 + 0.13 * Math.sin(flutes * angle + phase + py * 0.06);
            return r - Math.sqrt(dx * dx + dz * dz);
        }

        double reach() {
            return radius * 2.1 + Math.hypot(leanX, leanZ) * (tip - base);
        }
    }

    /** How far below its base a needle's root may reach down to find the ground. */
    private static final int ROOT = 16;

    @Nullable
    static SpireCluster tryAt(LandmarkField field, int x, int z, RandomSource random) {
        TerrainProbe probe = field.probe();
        double v = field.vertical();
        if (!field.ringSuits(LandmarkType.TEMPEST_SPIRES, x, z, 34, 6) || probe.surface(x, z) == Integer.MIN_VALUE) {
            return null;
        }
        // Heights are set from the highest ground around, not each needle's own footing, so the crown stands clear
        // above the peaks it rises from even when its roots are down in a hollow.
        int crown = Integer.MIN_VALUE;
        for (int d = 0; d < 16; d++) {
            double angle = d * Math.PI / 8, r = 10 + (d % 4) * 10;
            crown = Math.max(crown, probe.surface(x + (int) (Math.cos(angle) * r), z + (int) (Math.sin(angle) * r)));
        }
        List<Needle> needles = new ArrayList<>();
        int count = 8 + random.nextInt(6);
        for (int attempt = 0; attempt < 60 && needles.size() < count; attempt++) {
            boolean centre = needles.isEmpty();
            double angle = random.nextDouble() * Mth.TWO_PI;
            double distance = centre ? random.nextDouble() * 3 : 9 + random.nextDouble() * 25;
            double nx = x + Math.cos(angle) * distance, nz = z + Math.sin(angle) * distance;
            // How far the needle rises above the crown of the ground around.
            double rise = centre ? Mth.lerp(random.nextDouble(), 85, 115)
                    : Mth.lerp(random.nextDouble(), 15, 80) * (1 - 0.45 * distance / 34);
            double radius = 3 + rise / 16;
            boolean crowded = false;
            for (Needle other : needles) {
                if (Math.hypot(other.x() - nx, other.z() - nz) < radius + other.radius() + 3) {
                    crowded = true;
                    break;
                }
            }
            int ground = crowded ? Integer.MIN_VALUE : probe.surface((int) Math.floor(nx), (int) Math.floor(nz));
            if (ground == Integer.MIN_VALUE) {
                continue;
            }
            int base = ground - 2;
            int tip = Math.min(Math.max(crown, base) + (int) Math.round(rise * v), field.buildTop() - 6);
            if (tip - base < 24) {
                continue;
            }
            // Each leans out from the centre, so the cluster opens like a crown.
            double lean = centre ? random.nextDouble() * 0.03 : Mth.lerp(random.nextDouble(), 0.03, 0.09);
            double leanAngle = centre ? random.nextDouble() * Mth.TWO_PI : angle + (random.nextDouble() - 0.5) * 0.6;
            needles.add(new Needle(nx, nz, base, tip, radius * Math.sqrt(v), Math.cos(leanAngle) * lean,
                    Math.sin(leanAngle) * lean, 5 + random.nextInt(3), random.nextDouble() * Mth.TWO_PI,
                    centre || random.nextDouble() < 0.3));
        }
        if (needles.size() < 5) {
            return null;
        }
        return new SpireCluster(x, z, List.copyOf(needles), random.nextLong());
    }

    @Override
    public LandmarkType type() {
        return LandmarkType.TEMPEST_SPIRES;
    }

    @Override
    public double extent() {
        double extent = 0;
        for (Needle needle : needles) {
            extent = Math.max(extent, Math.hypot(needle.x() - x, needle.z() - z) + needle.reach());
        }
        return extent;
    }

    @Override
    public int visitY() {
        return needles.getFirst().tip() + 4;
    }

    @Override
    public void build(WorldGenLevel level, ChunkPos chunk) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        List<Needle> near = new ArrayList<>(needles.size());
        for (int bx = chunk.getMinBlockX(); bx <= chunk.getMaxBlockX(); bx++) {
            for (int bz = chunk.getMinBlockZ(); bz <= chunk.getMaxBlockZ(); bz++) {
                near.clear();
                int top = Integer.MIN_VALUE, bottom = Integer.MAX_VALUE;
                for (Needle needle : needles) {
                    if (Math.abs(bx - needle.x()) <= needle.reach() && Math.abs(bz - needle.z()) <= needle.reach()) {
                        near.add(needle);
                        top = Math.max(top, needle.tip());
                        bottom = Math.min(bottom, needle.base() - ROOT);
                    }
                }
                if (near.isEmpty()) {
                    continue;
                }
                boolean rooted = false;
                int[] highest = new int[near.size()];
                java.util.Arrays.fill(highest, Integer.MIN_VALUE);
                for (int y = top; y >= bottom && !rooted; y--) {
                    Needle best = null;
                    double margin = 0;
                    for (Needle needle : near) {
                        double m = needle.margin(bx, y, bz);
                        if (m > margin) {
                            margin = m;
                            best = needle;
                        }
                    }
                    if (best == null) {
                        continue;
                    }
                    pos.set(bx, y, bz);
                    BlockState here = level.getBlockState(pos);
                    if (y < best.base()) {
                        // Below the base the needle only fills the air down to the ground it stands on.
                        if (!here.isAir()) {
                            rooted = true;
                            continue;
                        }
                    } else if (!here.isAir() && !here.canBeReplaced()) {
                        continue;
                    }
                    level.setBlock(pos, material(best, bx, y, bz, margin), 2);
                    int index = near.indexOf(best);
                    highest[index] = Math.max(highest[index], y);
                }
                // A rod on the tallest point of each crowned needle, so the storms strike it first.
                for (int i = 0; i < near.size(); i++) {
                    Needle needle = near.get(i);
                    double rise = needle.tip() - needle.base();
                    if (needle.rod() && highest[i] != Integer.MIN_VALUE
                            && Mth.floor(needle.x() + needle.leanX() * rise) == bx
                            && Mth.floor(needle.z() + needle.leanZ() * rise) == bz) {
                        level.setBlock(pos.set(bx, highest[i] + 1, bz), Blocks.LIGHTNING_ROD.defaultBlockState(), 2);
                    }
                }
            }
        }
    }

    /**
     * The needle's rock: vertical bands of blackstone and deepslate following the flutes, basalt here and there, and a
     * copper vein winding up it, its oxidation set by height, bright where the lightning scours the tips.
     */
    private BlockState material(Needle needle, int bx, int y, int bz, double margin) {
        if (margin > 1.8) {
            return Blocks.DEEPSLATE.defaultBlockState();
        }
        double rise = Math.max(0, y - needle.base());
        double angle = Math.atan2(bz - (needle.z() + needle.leanZ() * rise), bx - (needle.x() + needle.leanX() * rise));
        double s = Mth.clamp((y - needle.base()) / (double) (needle.tip() - needle.base()), 0, 1);
        if (Math.sin(2 * angle + y * 0.16 + needle.phase()) > 0.92) {
            return s > 0.82 ? Blocks.COPPER_BLOCK.defaultBlockState()
                    : s > 0.6 ? Blocks.EXPOSED_COPPER.defaultBlockState()
                    : s > 0.35 ? Blocks.WEATHERED_COPPER.defaultBlockState() : Blocks.OXIDIZED_COPPER.defaultBlockState();
        }
        double grain = LandmarkField.hash(salt, bx >> 1, y / 3, bz >> 1);
        if (grain < 0.08) {
            return Blocks.BASALT.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y);
        }
        int band = Mth.floor((angle / Mth.TWO_PI + 0.5) * needle.flutes() * 2 + needle.phase());
        return (band & 1) == 0 ? Blocks.BLACKSTONE.defaultBlockState() : Blocks.COBBLED_DEEPSLATE.defaultBlockState();
    }

    /** "ring": the copper crystals around the cluster, at the ground beneath each point. */
    @Override
    public List<BlockPos> points(String kind) {
        if (!kind.equals("ring")) {
            return List.of();
        }
        List<BlockPos> ring = new ArrayList<>();
        int count = 15;
        for (int i = 0; i < count; i++) {
            double jitter = LandmarkField.hash(salt, i, 0, 1) - 0.5;
            double angle = (i + jitter * 0.6) * Mth.TWO_PI / count;
            double radius = RING + (LandmarkField.hash(salt, i, 0, 2) - 0.5) * 8;
            ring.add(new BlockPos(x + (int) Math.round(Math.cos(angle) * radius), Integer.MIN_VALUE,
                    z + (int) Math.round(Math.sin(angle) * radius)));
        }
        return ring;
    }
}
