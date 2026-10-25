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
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightningRodBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A spire of copper, tapering to a point and leaning a little, different every time: the height, thickness, lean and
 * branches are rolled per spike. Its blocks run from fresh copper to fully oxidized in {@code blocks}; the tip is
 * always fresh, as if scoured by strikes, and the oxidation deepens toward the base and on faces open to the sky. The
 * base is carried down to the ground so the spike never hovers over a slope, and a lightning rod may crown the tip.
 *
 * <p>The horizontal reach (lean plus branches) is capped at 13 blocks so a spike stays within the area a feature may
 * write to.
 */
public final class CopperSpikeFeature extends Feature<CopperSpikeFeature.Config> {
    private static final int MAX_REACH = 13;
    private static final int MAX_BASE_FILL = 14;

    public record Config(IntProvider height, float baseRadius, float lean, float taper, float branchChance,
                         float rodChance, List<BlockState> blocks) implements FeatureConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(i -> i.group(
                IntProvider.codec(4, 64).fieldOf("height").forGetter(Config::height),
                Codec.floatRange(0.5f, 6f).optionalFieldOf("base_radius", 2.5f).forGetter(Config::baseRadius),
                Codec.floatRange(0, 1f).optionalFieldOf("lean", 0.2f).forGetter(Config::lean),
                Codec.floatRange(0.4f, 4f).optionalFieldOf("taper", 1.3f).forGetter(Config::taper),
                Codec.floatRange(0, 1f).optionalFieldOf("branch_chance", 0.12f).forGetter(Config::branchChance),
                Codec.floatRange(0, 1f).optionalFieldOf("rod_chance", 0.5f).forGetter(Config::rodChance),
                BlockState.CODEC.listOf(2, 8).fieldOf("blocks").forGetter(Config::blocks)
        ).apply(i, Config::new));
    }

    public CopperSpikeFeature() {
        super(Config.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<Config> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        Config config = context.config();
        BlockPos origin = context.origin();
        BlockState ground = level.getBlockState(origin.below());
        if (ground.isAir() || !ground.getFluidState().isEmpty() || !level.getBlockState(origin).isAir()) {
            return false;
        }

        int height = config.height().sample(random);
        double baseRadius = config.baseRadius() * (0.8 + random.nextDouble() * 0.4);
        double angle = random.nextDouble() * Math.PI * 2;
        double lean = Math.min(MAX_REACH - baseRadius - 2, height * config.lean() * (0.4 + random.nextDouble() * 0.6));
        double leanX = Math.cos(angle) * lean, leanZ = Math.sin(angle) * lean;
        double wobbleX = random.nextDouble() * 6, wobbleZ = random.nextDouble() * 6;

        // blocks of the spike and the height fraction each sits at (0 at the base, 1 at the tip)
        Map<BlockPos, Double> shape = new HashMap<>();
        BlockPos tip = origin;
        for (int y = 0; y <= height; y++) {
            double t = y / (double) height;
            double curve = Math.pow(t, 1.4);
            double cx = leanX * curve + Math.sin(t * 5 + wobbleX) * 0.6 * t;
            double cz = leanZ * curve + Math.sin(t * 5 + wobbleZ) * 0.6 * t;
            double radius = baseRadius * Math.pow(1 - t, config.taper());
            int extent = (int) Math.ceil(radius);
            BlockPos center = origin.offset((int) Math.round(cx), y, (int) Math.round(cz));
            for (int dx = -extent; dx <= extent; dx++) {
                for (int dz = -extent; dz <= extent; dz++) {
                    double distance = Math.sqrt(dx * dx + dz * dz);
                    if (distance <= radius + (random.nextDouble() - 0.5) * 0.5 || dx == 0 && dz == 0) {
                        shape.put(center.offset(dx, 0, dz), t);
                    }
                }
            }
            tip = center;
            if (y > height * 0.25 && y < height * 0.85 && random.nextFloat() < config.branchChance() / 3) {
                branch(shape, random, center, t, Math.max(3, (int) (height * (0.15 + random.nextDouble() * 0.2))),
                        origin);
            }
        }

        // carry the footprint down to solid ground
        Map<BlockPos, Double> base = new HashMap<>();
        for (Map.Entry<BlockPos, Double> entry : shape.entrySet()) {
            if (entry.getValue() > 0.02) {
                continue;
            }
            BlockPos.MutableBlockPos cursor = entry.getKey().mutable().move(Direction.DOWN);
            int steps = 0;
            while (steps++ < MAX_BASE_FILL && replaceable(level, cursor)) {
                base.put(cursor.immutable(), 0.0);
                cursor.move(Direction.DOWN);
            }
        }
        shape.putAll(base);

        List<BlockState> blocks = config.blocks();
        int top = blocks.size() - 1;
        boolean placed = false;
        for (Map.Entry<BlockPos, Double> entry : shape.entrySet()) {
            BlockPos pos = entry.getKey();
            if (!replaceable(level, pos)) {
                continue;
            }
            double t = entry.getValue();
            int index = (int) Math.round(top * (1 - t) * 0.85 + (random.nextDouble() - 0.5) * 0.9);
            if (!shape.containsKey(pos.above())) {
                index++;
            }
            if (t > 0.9) {
                index = 0;
            }
            level.setBlock(pos, blocks.get(Math.max(0, Math.min(top, index))), Block.UPDATE_CLIENTS);
            placed = true;
        }
        if (placed && random.nextFloat() < config.rodChance()) {
            BlockPos above = tip.above();
            if (level.getBlockState(above).isAir()) {
                level.setBlock(above, Blocks.LIGHTNING_ROD.defaultBlockState()
                        .setValue(LightningRodBlock.FACING, Direction.UP), Block.UPDATE_CLIENTS);
            }
        }
        if (placed) {
            FeatureStats.placed("copper_spike");
        }
        return placed;
    }

    /** A thin prong leaving the spike sideways and curving up, ending in a point. */
    private static void branch(Map<BlockPos, Double> shape, RandomSource random, BlockPos from, double t, int length,
                               BlockPos origin) {
        Direction direction = Direction.Plane.HORIZONTAL.getRandomDirection(random);
        BlockPos.MutableBlockPos cursor = from.mutable();
        for (int i = 0; i < length; i++) {
            if (i % 2 == 0) {
                cursor.move(direction);
            } else {
                cursor.move(Direction.UP);
            }
            if (Math.abs(cursor.getX() - origin.getX()) > MAX_REACH || Math.abs(cursor.getZ() - origin.getZ()) > MAX_REACH) {
                return;
            }
            shape.putIfAbsent(cursor.immutable(), Math.min(1.0, t + i * 0.01));
        }
    }

    private static boolean replaceable(WorldGenLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.isAir() || state.canBeReplaced() && state.getFluidState().isEmpty();
    }
}
