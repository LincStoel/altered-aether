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
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The Frozen Crags' landmark: one colossal spire of ice, its three-lobed body twisting as it climbs far above the
 * peaks, ringed with snow-laden ledges, its lobes edged with blue ice that spirals with the twist. Lesser shards lean
 * out around its foot like a crystal cluster.
 */
record FrozenSpire(int x, int z, int base, int tip, double radius, double twist, double phase, double leanX,
                   double leanZ, int ledgeSpacing, List<Shard> shards, long salt) implements Landmark {
    private static final int ROOT = 20;
    private static final Block ICESTONE = BuiltInRegistries.BLOCK.getOptional(ResourceLocation.parse("aether:icestone"))
            .orElse(Blocks.SNOW_BLOCK);

    /** A lesser shard: a straight leaning prism of ice, square-ish in section. */
    record Shard(double x, double z, int base, int tip, double radius, double leanX, double leanZ, double phase) {
        double margin(int px, int py, int pz) {
            if (py > tip || py < base - ROOT) {
                return -1;
            }
            double rise = Math.max(0, py - base);
            double dx = px - (x + leanX * rise), dz = pz - (z + leanZ * rise);
            double s = Math.max(0, (py - base) / (double) (tip - base));
            double r = radius * Math.pow(1 - s, 0.7);
            // A faceted section: four flat faces, turned to the shard's own angle.
            double angle = Math.atan2(dz, dx) - phase;
            r /= Math.max(Math.abs(Math.cos(angle)), Math.abs(Math.sin(angle))) * 0.3 + 0.7;
            return r - Math.sqrt(dx * dx + dz * dz);
        }

        double reach() {
            return radius * 1.5 + Math.hypot(leanX, leanZ) * (tip - base);
        }
    }

    @Nullable
    static FrozenSpire tryAt(LandmarkField field, int x, int z, RandomSource random) {
        TerrainProbe probe = field.probe();
        double v = field.vertical();
        int ground = probe.surface(x, z);
        if (ground == Integer.MIN_VALUE || !field.ringSuits(LandmarkType.FROZEN_SPIRE, x, z, 28, 6)) {
            return null;
        }
        double radius = Mth.lerp(random.nextDouble(), 14, 19);
        // It must stand on a massif, not over a gap between two.
        int footed = 0;
        for (int d = 0; d < 8; d++) {
            double angle = d * Math.PI / 4;
            int g = probe.surface(x + (int) (Math.cos(angle) * (radius + 6)), z + (int) (Math.sin(angle) * (radius + 6)));
            if (g != Integer.MIN_VALUE && Math.abs(g - ground) < 30) {
                footed++;
            }
        }
        if (footed < 6) {
            return null;
        }
        int base = ground - 3;
        int tip = Math.min(base + (int) Math.round(Mth.lerp(random.nextDouble(), 135, 175) * v), field.buildTop() - 4);
        double leanAngle = random.nextDouble() * Mth.TWO_PI, lean = random.nextDouble() * 0.05;
        List<Shard> shards = new ArrayList<>();
        int count = 5 + random.nextInt(4);
        for (int attempt = 0; attempt < 30 && shards.size() < count; attempt++) {
            double angle = random.nextDouble() * Mth.TWO_PI;
            double distance = radius + 3 + random.nextDouble() * 14;
            double sx = x + Math.cos(angle) * distance, sz = z + Math.sin(angle) * distance;
            int g = probe.surface((int) Math.floor(sx), (int) Math.floor(sz));
            if (g == Integer.MIN_VALUE) {
                continue;
            }
            double outward = Mth.lerp(random.nextDouble(), 0.12, 0.3);
            shards.add(new Shard(sx, sz, g - 2, g - 2 + (int) Math.round(Mth.lerp(random.nextDouble(), 22, 55) * v),
                    Mth.lerp(random.nextDouble(), 2.5, 5), Math.cos(angle) * outward, Math.sin(angle) * outward,
                    random.nextDouble() * Mth.TWO_PI));
        }
        return new FrozenSpire(x, z, base, tip, radius, Mth.lerp(random.nextDouble(), 0.5, 1.1),
                random.nextDouble() * Mth.TWO_PI, Math.cos(leanAngle) * lean, Math.sin(leanAngle) * lean,
                Math.max(10, (int) Math.round(18 * v)), List.copyOf(shards), random.nextLong());
    }

    @Override
    public LandmarkType type() {
        return LandmarkType.FROZEN_SPIRE;
    }

    @Override
    public double extent() {
        double extent = radius * 1.6 + 4 + Math.hypot(leanX, leanZ) * (tip - base);
        for (Shard shard : shards) {
            extent = Math.max(extent, Math.hypot(shard.x() - x, shard.z() - z) + shard.reach());
        }
        return extent;
    }

    @Override
    public int visitY() {
        return tip + 4;
    }

    /** The body's twist at a height: how far its lobes have turned. */
    private double turn(double s) {
        return twist * Mth.TWO_PI * s + phase;
    }

    /** How far inside the main spire a block lies, in blocks; negative outside. */
    double margin(int px, int py, int pz) {
        if (py > tip || py < base - ROOT) {
            return -1;
        }
        double rise = Math.max(0, py - base);
        double dx = px - (x + leanX * rise), dz = pz - (z + leanZ * rise);
        double s = (py - base) / (double) (tip - base);
        double r;
        if (s < 0) {
            r = radius * 1.25;
        } else {
            r = radius * Math.pow(1 - s, 0.95);
            if (s < 0.12) {
                double flare = 1 - s / 0.12;
                r += radius * 0.5 * flare * flare;
            }
        }
        double angle = Math.atan2(dz, dx);
        r *= 1 + 0.22 * Math.cos(3 * (angle - turn(Math.max(0, s))));
        r *= 1 + 0.04 * Math.sin(11 * angle + py * 0.3);
        // Ledges: thin shelves ringing the spire at intervals, reaching further out lower down.
        if (s > 0.08 && s < 0.85 && Math.floorMod(py - base, ledgeSpacing) < 2) {
            r += 2.4 * (1 - s);
        }
        return r - Math.sqrt(dx * dx + dz * dz);
    }

    @Override
    public void build(WorldGenLevel level, ChunkPos chunk) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        double mainReach = radius * 1.6 + 4 + Math.hypot(leanX, leanZ) * (tip - base);
        List<Shard> near = new ArrayList<>();
        for (int bx = chunk.getMinBlockX(); bx <= chunk.getMaxBlockX(); bx++) {
            for (int bz = chunk.getMinBlockZ(); bz <= chunk.getMaxBlockZ(); bz++) {
                boolean main = Math.abs(bx - x) <= mainReach && Math.abs(bz - z) <= mainReach;
                near.clear();
                int top = main ? tip : Integer.MIN_VALUE, bottom = main ? base - ROOT : Integer.MAX_VALUE;
                for (Shard shard : shards) {
                    if (Math.abs(bx - shard.x()) <= shard.reach() && Math.abs(bz - shard.z()) <= shard.reach()) {
                        near.add(shard);
                        top = Math.max(top, shard.tip());
                        bottom = Math.min(bottom, shard.base() - ROOT);
                    }
                }
                if (!main && near.isEmpty()) {
                    continue;
                }
                boolean rooted = false;
                boolean above = false;
                for (int y = top; y >= bottom && !rooted; y--) {
                    double m = main ? margin(bx, y, bz) : -1;
                    Shard shard = null;
                    for (Shard s : near) {
                        double sm = s.margin(bx, y, bz);
                        if (sm > m) {
                            m = sm;
                            shard = s;
                        }
                    }
                    if (m <= 0) {
                        above = false;
                        continue;
                    }
                    int footing = shard == null ? base : shard.base();
                    pos.set(bx, y, bz);
                    BlockState here = level.getBlockState(pos);
                    if (y < footing) {
                        if (!here.isAir()) {
                            rooted = true;
                            continue;
                        }
                    } else if (!here.isAir() && !here.canBeReplaced()) {
                        above = true;
                        continue;
                    }
                    BlockState block = shard == null ? material(bx, y, bz, m, !above) : shardMaterial(shard, bx, y, bz, m);
                    level.setBlock(pos, block, 2);
                    above = true;
                }
            }
        }
    }

    /**
     * The spire's ice: packed ice through and through, banded with icestone, its lobe edges blue ice spiralling with
     * the twist, and snow wherever it faces the sky below the summit.
     */
    private BlockState material(int bx, int y, int bz, double margin, boolean open) {
        if (margin > 2) {
            return Blocks.PACKED_ICE.defaultBlockState();
        }
        double s = Mth.clamp((y - base) / (double) (tip - base), 0, 1);
        // Snow settles where the surface turns flat enough to hold it: the ledges and the shoulders of the lobes, not
        // the sheer flanks, where each column's top is only a sliver.
        if (open && s < 0.92 && margin(bx, y + 1, bz) <= 0 && margin - margin(bx, y + 2, bz) > 1.2) {
            return Blocks.SNOW_BLOCK.defaultBlockState();
        }
        double rise = Math.max(0, y - base);
        double angle = Math.atan2(bz - (z + leanZ * rise), bx - (x + leanX * rise));
        if (Math.cos(3 * (angle - turn(s))) > 0.86) {
            return Blocks.BLUE_ICE.defaultBlockState();
        }
        double band = (y - base + 3 * Math.sin(2 * angle + phase)) / 9.0;
        if ((Mth.floor(band) & 1) == 0 && LandmarkField.hash(salt, bx, y >> 2, bz) < 0.8) {
            return ICESTONE.defaultBlockState();
        }
        return Blocks.PACKED_ICE.defaultBlockState();
    }

    private BlockState shardMaterial(Shard shard, int bx, int y, int bz, double margin) {
        if (margin < 1.2 && LandmarkField.hash(salt, bx, y, bz) < 0.35) {
            return Blocks.BLUE_ICE.defaultBlockState();
        }
        return Blocks.PACKED_ICE.defaultBlockState();
    }

    /** "foot": spots around the spire's foot, for snow drifts. */
    @Override
    public List<BlockPos> points(String kind) {
        if (!kind.equals("foot")) {
            return List.of();
        }
        List<BlockPos> foot = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            double angle = (i + LandmarkField.hash(salt, i, 1, 0) * 0.7) * Mth.TWO_PI / 10;
            double distance = radius * 1.4 + 3 + LandmarkField.hash(salt, i, 2, 0) * 10;
            foot.add(new BlockPos(x + (int) Math.round(Math.cos(angle) * distance), Integer.MIN_VALUE,
                    z + (int) Math.round(Math.sin(angle) * distance)));
        }
        return foot;
    }
}
