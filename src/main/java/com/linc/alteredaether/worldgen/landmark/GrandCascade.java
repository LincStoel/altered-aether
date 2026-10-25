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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The grand cascade: a staircase of pools marching down to an island's edge. Each pool is a bowl in a raised mound
 * with a grassy rim, and spills through a notch in its rim into the next one below, tucked in under its lip, so a fall
 * pours from every step; the last pool hangs out over the edge and pours into the open sky.
 *
 * <p>The pools are built, not carved: each column of the cascade belongs to the highest pool whose footprint covers it,
 * and is built down from that pool's floor or rim until it meets the ground, so every bowl is walled all the way round
 * by its own rim or by the mound of the pool above, and the water can only leave through the notches.
 */
record GrandCascade(int x, int z, List<Tier> tiers, double vertical, long salt) implements Landmark {
    /** Width of a pool's rim, outside its shore. */
    private static final double RIM = 2.5;
    /** Half the width of an overflow notch, in blocks. */
    private static final double NOTCH = 2.2;
    private static final Block HOLYSTONE = block("aether:holystone", Blocks.STONE);
    private static final Block MOSSY = block("aether:mossy_holystone", Blocks.MOSSY_COBBLESTONE);
    private static final Block GRASS = block("aether:aether_grass_block", Blocks.GRASS_BLOCK);
    private static final Block DIRT = block("aether:aether_dirt", Blocks.DIRT);

    /**
     * One pool: a bowl of {@code radius} (with a wobbling shore) holding water to {@code water}, {@code depth} deep at
     * the centre, overflowing through a notch at bearing {@code notch}.
     */
    record Tier(double x, double z, double radius, int water, double depth, double phase1, double phase2,
                double notch) {
        double shore(double angle) {
            return radius * (1 + 0.15 * Math.sin(3 * angle + phase1) + 0.07 * Math.sin(5 * angle + phase2));
        }

        Tier withNotch(double bearing) {
            return new Tier(x, z, radius, water, depth, phase1, phase2, bearing);
        }
    }

    @Nullable
    static GrandCascade tryAt(LandmarkField field, int x, int z, RandomSource random) {
        TerrainProbe probe = field.probe();
        double v = field.vertical();
        int ground = probe.surface(x, z);
        if (ground == Integer.MIN_VALUE) {
            return null;
        }
        // Look for an island edge 40 to 100 blocks off, with no hill in the way.
        List<Double> bearings = new ArrayList<>();
        double offset = random.nextDouble() * Mth.TWO_PI;
        for (int b = 0; b < 16; b++) {
            double angle = offset + b * Math.PI / 8;
            int previous = ground, edge = -1;
            for (int r = 6; r <= 100; r += 3) {
                int px = x + (int) Math.round(Math.cos(angle) * r), pz = z + (int) Math.round(Math.sin(angle) * r);
                int h = probe.surface(px, pz, Math.min(probe.maxY() - 1, previous + 12), previous - 40);
                if (h == Integer.MIN_VALUE || h < previous - 14) {
                    edge = r;
                    break;
                }
                if (h > previous + 8) {
                    break;
                }
                previous = h;
            }
            if (edge >= 40) {
                bearings.add(angle);
            }
        }
        if (bearings.isEmpty()) {
            return null;
        }
        double bearing = bearings.get(random.nextInt(bearings.size()));

        List<Tier> tiers = new ArrayList<>();
        tiers.add(new Tier(x, z, Mth.lerp(random.nextDouble(), 9, 12), ground + (int) Math.round(Mth.lerp(
                random.nextDouble(), 3, 5) * v), Mth.lerp(random.nextDouble(), 2.5, 4) * v,
                random.nextDouble() * Mth.TWO_PI, random.nextDouble() * Mth.TWO_PI, Double.NaN));
        boolean overEdge = false;
        while (tiers.size() < 7) {
            Tier previous = tiers.getLast();
            bearing += (random.nextDouble() - 0.5) * 0.5;
            Tier next = new Tier(0, 0, Mth.lerp(random.nextDouble(), 8, 12), 0, 0, random.nextDouble() * Mth.TWO_PI,
                    random.nextDouble() * Mth.TWO_PI, Double.NaN);
            // Close enough that the fall from the notch lands half way into the next pool.
            double step = previous.shore(bearing) + RIM + 0.5 + 0.5 * next.shore(bearing + Math.PI);
            double cx = previous.x() + Math.cos(bearing) * step, cz = previous.z() + Math.sin(bearing) * step;
            int drop = Math.max(3, (int) Math.round(Mth.lerp(random.nextDouble(), 5, 8) * v));
            int water = previous.water() - drop;
            double depth = Math.min(drop - 1, Mth.lerp(random.nextDouble(), 2.5, 4) * v);
            tiers.set(tiers.size() - 1, previous.withNotch(bearing));
            next = new Tier(cx, cz, next.radius(), water, depth, next.phase1(), next.phase2(), Double.NaN);
            int below = probe.surface((int) Math.floor(cx), (int) Math.floor(cz), Math.min(probe.maxY() - 1,
                    previous.water() + 10), water - 60);
            if (below != Integer.MIN_VALUE && below > water + 6) {
                return null;
            }
            if (below == Integer.MIN_VALUE || below < water - 25) {
                // Out over the edge: the last pool, pouring into the sky.
                tiers.add(next.withNotch(bearing));
                overEdge = true;
                break;
            }
            tiers.add(next);
        }
        if (!overEdge || tiers.size() < 3) {
            return null;
        }
        // Nothing just under the last lip, or the water would spread over the ground instead of falling.
        Tier last = tiers.getLast();
        double out = last.shore(bearing) + RIM + 1.5;
        int px = (int) Math.floor(last.x() + Math.cos(bearing) * out), pz = (int) Math.floor(last.z() + Math.sin(bearing) * out);
        int under = probe.surface(px, pz, Math.min(probe.maxY() - 1, last.water()), last.water() - 40);
        if (under != Integer.MIN_VALUE) {
            return null;
        }
        return new GrandCascade(x, z, List.copyOf(tiers), v, random.nextLong());
    }

    @Override
    public LandmarkType type() {
        return LandmarkType.GRAND_CASCADE;
    }

    @Override
    public double extent() {
        double extent = 0;
        for (Tier tier : tiers) {
            extent = Math.max(extent, Math.hypot(tier.x() - x, tier.z() - z) + tier.radius() * 1.22 + RIM + 1);
        }
        return extent;
    }

    @Override
    public int visitY() {
        return tiers.getFirst().water() + 8;
    }

    @Override
    public void build(WorldGenLevel level, ChunkPos chunk) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockState water = Blocks.WATER.defaultBlockState();
        for (int bx = chunk.getMinBlockX(); bx <= chunk.getMaxBlockX(); bx++) {
            for (int bz = chunk.getMinBlockZ(); bz <= chunk.getMaxBlockZ(); bz++) {
                Tier tier = null;
                double distance = 0, shore = 0, angle = 0;
                for (Tier t : tiers) {
                    double dx = bx + 0.5 - t.x(), dz = bz + 0.5 - t.z();
                    distance = Math.sqrt(dx * dx + dz * dz);
                    angle = Math.atan2(dz, dx);
                    shore = t.shore(angle);
                    if (distance < shore + RIM) {
                        tier = t;
                        break;
                    }
                }
                if (tier == null) {
                    continue;
                }
                boolean bowl = distance < shore;
                boolean notch = !bowl && !Double.isNaN(tier.notch())
                        && Math.abs(Mth.wrapDegrees(Math.toDegrees(angle - tier.notch()))) < Math.toDegrees(NOTCH / shore);
                int top;
                if (bowl) {
                    double reach = distance / shore;
                    top = Mth.floor(tier.water() - tier.depth() * Math.pow(1 - reach * reach, 0.7));
                } else {
                    top = notch ? tier.water() - 1 : tier.water() + 1;
                }
                // Open the air above, then the water, then the body down to the ground.
                for (int y = top + 1; y <= top + 24; y++) {
                    if (!level.getBlockState(pos.set(bx, y, bz)).isAir()) {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                    }
                }
                for (int y = top + 1; y <= tier.water(); y++) {
                    level.setBlock(pos.set(bx, y, bz), water, 2);
                    if (notch) {
                        level.scheduleTick(pos.immutable(), Fluids.WATER, 0);
                    }
                }
                if (bowl && distance < shore * 0.8 && LandmarkField.hash(salt, bx, 7, bz) < 0.025) {
                    level.setBlock(pos.set(bx, tier.water() + 1, bz), Blocks.LILY_PAD.defaultBlockState(), 2);
                }
                double t = distance / (shore + RIM);
                int underside = top - (int) Math.round((5 + 18 * (1 - t * t)) * vertical);
                for (int y = top; y >= underside; y--) {
                    pos.set(bx, y, bz);
                    BlockState here = level.getBlockState(pos);
                    if (y <= top - 3 && !here.isAir() && here.getFluidState().isEmpty()) {
                        break;
                    }
                    level.setBlock(pos, material(bx, y, bz, top - y, bowl, notch), 2);
                }
            }
        }
    }

    /** Grass rims over dirt, a lake bed of clay and dirt in the bowls, and holystone mossy where the water runs. */
    private BlockState material(int bx, int y, int bz, int depth, boolean bowl, boolean notch) {
        double patch = LandmarkField.hash(salt, bx >> 1, y / 3, bz >> 1);
        if (bowl) {
            if (depth == 0) {
                return patch < 0.25 ? Blocks.CLAY.defaultBlockState() : patch < 0.4 ? MOSSY.defaultBlockState()
                        : DIRT.defaultBlockState();
            }
            return depth == 1 ? DIRT.defaultBlockState() : HOLYSTONE.defaultBlockState();
        }
        if (notch) {
            return MOSSY.defaultBlockState();
        }
        if (depth == 0) {
            return GRASS.defaultBlockState();
        }
        if (depth <= 2) {
            return DIRT.defaultBlockState();
        }
        return patch < 0.3 ? MOSSY.defaultBlockState() : HOLYSTONE.defaultBlockState();
    }

    private static Block block(String id, Block fallback) {
        return BuiltInRegistries.BLOCK.getOptional(ResourceLocation.parse(id)).orElse(fallback);
    }
}
