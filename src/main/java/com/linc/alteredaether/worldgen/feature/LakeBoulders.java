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
package com.linc.alteredaether.worldgen.feature;

import com.linc.alteredaether.registry.AARegistries;
import com.linc.alteredaether.worldgen.basin.BasinField;
import com.linc.alteredaether.worldgen.basin.BasinSite;
import com.linc.alteredaether.worldgen.basin.BasinType;
import com.linc.alteredaether.worldgen.boulder.BoulderShape;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Map;

/**
 * Boulders of sylicite in the lakes and pools, as opal turns up in the beds of water: mostly sunk out of sight on the
 * bed, now and then a stack standing up through the surface, and the odd one on the shore. Atoll lagoons and the
 * poisonous lakes get none.
 *
 * <p>The boulders in the water are worked out from the basin's shape alone (its {@link BasinSite#bed bed}, never the
 * blocks), each from its own seeded random source, so every chunk one reaches builds the same boulder and places the
 * blocks in its own columns, whichever order the chunks run in. A shore boulder is small and is placed whole by the
 * chunk its centre falls in, resting on the ground it finds there.
 */
final class LakeBoulders {
    private static final long SPOT_SALT = 0x6A09E667L, SHORE_SALT = 0x3C6EF372FE94F82BL;
    /** In a big lake, a boulder may stand in each cell of this many blocks, at this chance. */
    private static final int LAKE_CELL = 18;
    private static final float LAKE_CHANCE = 0.45f;
    /** Shore boulders round a big lake, one cell at a time, at this chance. */
    private static final int SHORE_CELL = 32;
    private static final float SHORE_CHANCE = 0.3f;
    /** How far past the water's edge a shore boulder sits at the least, so it stays on dry ground. */
    private static final double SHORE_GAP = 2.5;
    /** A small basin's chance of a boulder in its water, and of one on its shore. */
    private static final float POOL_CHANCE = 0.4f, POOL_SHORE_CHANCE = 0.12f;
    /** The share of the boulders in the water that stand up through the surface, where it is shallow enough. */
    private static final float EMERGE_CHANCE = 0.22f;
    private static final int EMERGE_MAX_DEPTH = 9;
    /** How far a boulder in the water can reach from its centre, lobes and all. */
    private static final int REACH = 12;

    /** Whether a basin of this kind gets boulders: every lake and pool but the poisonous ones and atoll lagoons. */
    static boolean suits(BasinType type) {
        return !type.atollShaped() && type.fluid() != BasinType.Fluid.POISON;
    }

    /**
     * Places this chunk's share of the site's boulders. Runs once the chunk's water is in and sealed, so the blocks
     * it reads are the ones the fill left.
     */
    static void place(WorldGenLevel level, BasinField field, ChunkPos chunk, BasinSite site) {
        if (!suits(site.type())) {
            return;
        }
        Block sylicite = AARegistries.SYLICITE.natural.block().get();
        long salt = SPOT_SALT ^ BasinField.mix(site.x() * 0x9E3779B97F4A7C15L + site.z());
        int minX = chunk.getMinBlockX(), minZ = chunk.getMinBlockZ(), maxX = chunk.getMaxBlockX(), maxZ = chunk.getMaxBlockZ();
        if (site.isLake()) {
            for (int gx = Math.floorDiv(minX - REACH, LAKE_CELL); gx <= Math.floorDiv(maxX + REACH, LAKE_CELL); gx++) {
                for (int gz = Math.floorDiv(minZ - REACH, LAKE_CELL); gz <= Math.floorDiv(maxZ + REACH, LAKE_CELL); gz++) {
                    RandomSource random = field.randomAt(salt, gx, gz);
                    if (random.nextFloat() >= LAKE_CHANCE) {
                        continue;
                    }
                    int x = gx * LAKE_CELL + random.nextInt(LAKE_CELL), z = gz * LAKE_CELL + random.nextInt(LAKE_CELL);
                    submerged(level, chunk, site, sylicite, random, x, z, 2 + random.nextInt(3));
                }
            }
            for (int gx = Math.floorDiv(minX, SHORE_CELL); gx <= Math.floorDiv(maxX, SHORE_CELL); gx++) {
                for (int gz = Math.floorDiv(minZ, SHORE_CELL); gz <= Math.floorDiv(maxZ, SHORE_CELL); gz++) {
                    RandomSource random = field.randomAt(salt ^ SHORE_SALT, gx, gz);
                    if (random.nextFloat() >= SHORE_CHANCE) {
                        continue;
                    }
                    int x = gx * SHORE_CELL + random.nextInt(SHORE_CELL), z = gz * SHORE_CELL + random.nextInt(SHORE_CELL);
                    double beyond = (site.reach(x, z) - 1) * site.radius();
                    if (beyond >= SHORE_GAP && beyond <= 8 && !site.inFootprint(x, z)) {
                        shore(level, chunk, site, sylicite, random, x, z, 1 + random.nextInt(2));
                    }
                }
            }
            return;
        }
        RandomSource random = field.randomAt(salt, 0, 0);
        if (random.nextFloat() < POOL_CHANCE) {
            double angle = random.nextDouble() * Math.PI * 2, out = random.nextDouble() * site.radius() * 0.5;
            int x = site.x() + (int) Math.round(Math.cos(angle) * out), z = site.z() + (int) Math.round(Math.sin(angle) * out);
            int size = Mth.clamp((int) (site.radius() / 4), 1, 3);
            submerged(level, chunk, site, sylicite, random, x, z, 1 + random.nextInt(size));
        }
        random = field.randomAt(salt ^ SHORE_SALT, 0, 0);
        if (random.nextFloat() < POOL_SHORE_CHANCE) {
            double angle = random.nextDouble() * Math.PI * 2;
            double cos = Math.cos(angle), sin = Math.sin(angle);
            for (int step = (int) site.radius(); step < site.radius() * 2 + 8; step++) {
                int x = site.x() + (int) Math.round(cos * step), z = site.z() + (int) Math.round(sin * step);
                if ((site.reach(x, z) - 1) * site.radius() >= SHORE_GAP) {
                    shore(level, chunk, site, sylicite, random, x, z, 1 + random.nextInt(2));
                    break;
                }
            }
        }
    }

    /**
     * A boulder on the bed at (x, z): sunk a little into it and kept a block under the surface, or, at
     * {@link #EMERGE_CHANCE} where the water is shallow enough, a stack from the bed up through the surface. Its
     * blocks only go into this chunk's columns: anything below the surface without a block entity, and above it only
     * air and replaceable plants.
     */
    private static void submerged(WorldGenLevel level, ChunkPos chunk, BasinSite site, Block sylicite,
                                  RandomSource random, int x, int z, int radius) {
        // Kept clear of the shore by more than the boulder's own size, so it never cuts into the bank.
        double margin = (radius * 1.6 + 2) / Math.max(site.radius(), 1);
        if (!site.inFootprint(x, z) || site.reach(x, z) > 1 - margin) {
            return;
        }
        int water = site.waterLevel(), bed = site.bed(x, z), depth = water - bed;
        if (depth < 2 || x + REACH < chunk.getMinBlockX() || x - REACH > chunk.getMaxBlockX()
                || z + REACH < chunk.getMinBlockZ() || z - REACH > chunk.getMaxBlockZ()) {
            return;
        }
        boolean emerges = depth <= EMERGE_MAX_DEPTH && random.nextFloat() < EMERGE_CHANCE;
        double height, squash;
        int centerY, top;
        if (emerges) {
            // From a block into the bed to a block or three over the surface.
            top = water + 1 + random.nextInt(site.isLake() ? 3 : 2);
            height = (top - bed + 1) / 2.0;
            radius = Mth.clamp(Math.max(radius, (int) Math.ceil(height / 1.2)), 1, 5);
            squash = height / radius;
            centerY = Mth.floor(bed - 1 + height);
        } else {
            squash = 0.55 + 0.3 * random.nextDouble();
            height = BoulderShape.height(radius, squash);
            // Shrink it until it fits under the surface with its foot still in the bed.
            while (radius > 1 && bed + height > water - 1) {
                radius--;
                height = BoulderShape.height(radius, squash);
            }
            if (bed + height > water - 1) {
                return;
            }
            centerY = bed + random.nextInt(2);
            top = water - 1;
        }
        List<double[]> lobes = BoulderShape.lobes(random, radius, height, squash);
        BlockPos center = new BlockPos(x, centerY, z);
        Map<BlockPos, BlockState> shape = BoulderShape.carve(random, lobes, center, center, REACH, radius,
                at -> at.getY() <= top, at -> sylicite.defaultBlockState());
        int placed = 0;
        for (Map.Entry<BlockPos, BlockState> entry : shape.entrySet()) {
            BlockPos pos = entry.getKey();
            if (pos.getX() >> 4 != chunk.x || pos.getZ() >> 4 != chunk.z) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            // Over the surface only into the open; under it anything but a block entity, though the lower half never
            // goes into air, which there is a hole under the island rather than the basin.
            boolean fits = pos.getY() > water ? replaceable(state)
                    : !state.hasBlockEntity() && (pos.getY() >= centerY || !state.isAir());
            if (fits) {
                level.setBlock(pos, entry.getValue(), Block.UPDATE_CLIENTS);
                placed++;
            }
        }
        // Counted once, by the chunk holding the centre, so the tally is one per boulder.
        if (placed > 0 && x >> 4 == chunk.x && z >> 4 == chunk.z) {
            FeatureStats.placed(emerges ? "sylicite_stack" : "sylicite_boulder");
        }
    }

    /**
     * A small boulder resting on the shore at (x, z), placed whole by the chunk its centre is in. It sits on the first
     * ground under the bank's top, half sunk, and only takes the place of air and plants. Nothing is placed on trees
     * or where the ground stands far above the water.
     */
    private static void shore(WorldGenLevel level, ChunkPos chunk, BasinSite site, Block sylicite, RandomSource random,
                              int x, int z, int radius) {
        if (x >> 4 != chunk.x || z >> 4 != chunk.z) {
            return;
        }
        int water = site.waterLevel();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, water + 12, z);
        if (!replaceable(level.getBlockState(pos))) {
            return;
        }
        while (pos.getY() > water && replaceable(level.getBlockState(pos))) {
            pos.move(0, -1, 0);
        }
        BlockState ground = level.getBlockState(pos);
        if (pos.getY() <= water || ground.is(BlockTags.LOGS) || ground.is(BlockTags.LEAVES)
                || !ground.getFluidState().isEmpty() || !level.getBlockState(pos.above()).getFluidState().isEmpty()) {
            return;
        }
        double squash = 0.6 + 0.3 * random.nextDouble();
        double height = BoulderShape.height(radius, squash);
        BlockPos center = pos.above((int) Math.round(height * 0.2));
        List<double[]> lobes = BoulderShape.lobes(random, radius, height, squash);
        Map<BlockPos, BlockState> shape = BoulderShape.carve(random, lobes, center, center, radius * 2 + 2, radius,
                at -> replaceable(level.getBlockState(at)), at -> sylicite.defaultBlockState());
        if (shape.isEmpty()) {
            return;
        }
        for (Map.Entry<BlockPos, BlockState> entry : shape.entrySet()) {
            level.setBlock(entry.getKey(), entry.getValue(), Block.UPDATE_CLIENTS);
        }
        FeatureStats.placed("sylicite_shore");
    }

    private static boolean replaceable(BlockState state) {
        return state.isAir() || state.canBeReplaced() && state.getFluidState().isEmpty();
    }

    private LakeBoulders() {
    }
}
