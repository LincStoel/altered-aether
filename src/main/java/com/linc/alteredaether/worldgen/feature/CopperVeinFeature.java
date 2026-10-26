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

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * A web of thin copper veins through the rock: one-block threads that wander out from a point, forking as they go, so
 * a cliff that cuts one shows a spidery tracery of copper rather than a stripe. Threads replace only
 * {@code replaceable} rock. The copper is left unwaxed and mostly part-oxidized, so the veins go on greening in the
 * world. Copper crystals grow roots of the same webs from their feet (see {@link CopperSpikeFeature}), so the crystals
 * read as outcrops of the same lodes.
 *
 * <p>{@code length} is the length of the first thread; forks are shorter. {@code thick_chance} is the chance per block
 * that a thread forks.
 */
public final class CopperVeinFeature extends Feature<CopperVeinFeature.Config> {
    /** How far a web may spread from its start, so it stays within the area a feature may write to. */
    private static final int MAX_REACH = 12;
    /** Forks of forks, at most. */
    private static final int MAX_GENERATIONS = 3;

    public record Config(IntProvider length, float thickChance, List<BlockState> blocks, TagKey<Block> replaceable)
            implements FeatureConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(i -> i.group(
                IntProvider.codec(1, 32).fieldOf("length").forGetter(Config::length),
                Codec.floatRange(0, 1).optionalFieldOf("thick_chance", 0.2f).forGetter(Config::thickChance),
                BlockState.CODEC.listOf(1, 8).fieldOf("blocks").forGetter(Config::blocks),
                TagKey.codec(Registries.BLOCK).fieldOf("replaceable").forGetter(Config::replaceable)
        ).apply(i, Config::new));
    }

    public CopperVeinFeature() {
        super(Config.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<Config> context) {
        Config config = context.config();
        RandomSource random = context.random();
        BlockPos origin = context.origin();
        if (!context.level().getBlockState(origin).is(config.replaceable())) {
            return false;
        }
        // Two to four threads leaving the start in different directions, mostly level, some sinking.
        int placed = 0;
        int threads = 2 + random.nextInt(3);
        double start = random.nextDouble() * Math.PI * 2;
        for (int i = 0; i < threads; i++) {
            double angle = start + i * Math.PI * 2 / threads + (random.nextDouble() - 0.5);
            Vec3 heading = new Vec3(Math.cos(angle), (random.nextDouble() - 0.6) * 1.2, Math.sin(angle));
            placed += web(context.level(), random, origin, heading, config.length().sample(random),
                    config.thickChance(), config.blocks(), config.replaceable(), origin, MAX_REACH);
        }
        if (placed > 0) {
            FeatureStats.placed("copper_vein");
        }
        return placed > 0;
    }

    /**
     * Runs one thread {@code length} blocks from {@code from} along {@code heading}, wandering as it goes and forking
     * with {@code forkChance} per block into shorter threads that leave at an angle. Writes only rock in
     * {@code replaceable}, within {@code reach} blocks of {@code centre} horizontally. A thread that runs out of rock
     * stops.
     *
     * @return the number of blocks written
     */
    static int web(WorldGenLevel level, RandomSource random, BlockPos from, Vec3 heading, int length,
                   float forkChance, List<BlockState> blocks, TagKey<Block> replaceable, BlockPos centre, int reach) {
        return thread(level, random, Vec3.atCenterOf(from), heading.normalize(), length, forkChance, blocks,
                replaceable, centre, reach, 0);
    }

    private static int thread(WorldGenLevel level, RandomSource random, Vec3 at, Vec3 heading, int length,
                              float forkChance, List<BlockState> blocks, TagKey<Block> replaceable, BlockPos centre,
                              int reach, int generation) {
        int placed = 0, misses = 0;
        BlockPos last = null;
        for (int i = 0; i < length; i++) {
            BlockPos pos = BlockPos.containing(at);
            if (Math.abs(pos.getX() - centre.getX()) > reach || Math.abs(pos.getZ() - centre.getZ()) > reach) {
                break;
            }
            if (!pos.equals(last)) {
                // Diagonal steps get a joining block, so the thread stays connected face to face.
                if (last != null && pos.distManhattan(last) > 1) {
                    placed += set(level, random, new BlockPos(pos.getX(), last.getY(), last.getZ()), blocks, replaceable);
                }
                int set = set(level, random, pos, blocks, replaceable);
                placed += set;
                misses = set == 0 ? misses + 1 : 0;
                if (misses > 2) {
                    break;
                }
                last = pos;
            }
            if (generation < MAX_GENERATIONS && i > 1 && random.nextFloat() < forkChance) {
                double turn = (random.nextBoolean() ? 1 : -1) * (0.5 + random.nextDouble() * 0.7);
                Vec3 fork = new Vec3(heading.x * Math.cos(turn) - heading.z * Math.sin(turn),
                        heading.y - random.nextDouble() * 0.3,
                        heading.x * Math.sin(turn) + heading.z * Math.cos(turn)).normalize();
                placed += thread(level, random, at, fork, (int) ((length - i) * (0.4 + random.nextDouble() * 0.4)),
                        forkChance, blocks, replaceable, centre, reach, generation + 1);
            }
            heading = heading.add((random.nextDouble() - 0.5) * 0.6, (random.nextDouble() - 0.55) * 0.3,
                    (random.nextDouble() - 0.5) * 0.6).normalize();
            at = at.add(heading);
        }
        return placed;
    }

    private static int set(WorldGenLevel level, RandomSource random, BlockPos pos, List<BlockState> blocks,
                           TagKey<Block> replaceable) {
        if (!level.getBlockState(pos).is(replaceable)) {
            return 0;
        }
        // Two rolls averaged: the ends of the oxidation scale turn up, the middle stages most.
        int index = (random.nextInt(blocks.size()) + random.nextInt(blocks.size()) + random.nextInt(2)) / 2;
        level.setBlock(pos, blocks.get(Math.min(blocks.size() - 1, index)), Block.UPDATE_CLIENTS);
        return 1;
    }
}
