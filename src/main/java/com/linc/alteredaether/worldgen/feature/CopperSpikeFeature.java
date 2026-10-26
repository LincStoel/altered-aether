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
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightningRodBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A growth of native copper, like a dendrite crystal: a knobbly stem that rises, leaning, and keeps forking into
 * thinner arms that turn upward, so the whole reads as a branching, lumpy copper tree rather than a smooth spike. The
 * height, lean and branching are rolled per crystal. Its blocks run from fresh copper to fully oxidized in
 * {@code blocks}; the tips are always fresh, as if scoured by strikes, and the oxidation deepens toward the base and
 * on faces open to the sky. The foot is carried down to the ground so the crystal never hovers over a slope, and a
 * lightning rod may crown its highest tip.
 *
 * <p>From the foot, thin roots of copper spread through the rock beneath (see {@link CopperVeinFeature#web}): a web
 * of one-block threads that creep out just under the ground and fork as they sink, so the crystal reads as an outcrop
 * of a lode.
 *
 * <p>{@code base_radius} is the stem's half thickness at the foot, {@code taper} how quickly the arms thin toward
 * their ends, and {@code branch_chance} how often an arm forks per block of its length. The horizontal reach is capped
 * at 13 blocks so a crystal stays within the area a feature may write to.
 */
public final class CopperSpikeFeature extends Feature<CopperSpikeFeature.Config> {
    private static final int MAX_REACH = 13;
    private static final int MAX_BASE_FILL = 14;
    /** How many times an arm may fork from an arm that forked. */
    private static final int MAX_GENERATIONS = 3;

    public record Config(IntProvider height, float baseRadius, float lean, float taper, float branchChance,
                         float rodChance, List<BlockState> blocks, IntProvider veins, IntProvider veinLength,
                         Optional<TagKey<Block>> veinRock) implements FeatureConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(i -> i.group(
                IntProvider.codec(4, 64).fieldOf("height").forGetter(Config::height),
                Codec.floatRange(0.5f, 6f).optionalFieldOf("base_radius", 1.3f).forGetter(Config::baseRadius),
                Codec.floatRange(0, 1f).optionalFieldOf("lean", 0.2f).forGetter(Config::lean),
                Codec.floatRange(0.4f, 4f).optionalFieldOf("taper", 1.3f).forGetter(Config::taper),
                Codec.floatRange(0, 1f).optionalFieldOf("branch_chance", 0.12f).forGetter(Config::branchChance),
                Codec.floatRange(0, 1f).optionalFieldOf("rod_chance", 0.5f).forGetter(Config::rodChance),
                BlockState.CODEC.listOf(2, 8).fieldOf("blocks").forGetter(Config::blocks),
                IntProvider.codec(0, 8).optionalFieldOf("veins", ConstantInt.ZERO).forGetter(Config::veins),
                IntProvider.codec(1, 32).optionalFieldOf("vein_length", ConstantInt.of(8)).forGetter(Config::veinLength),
                TagKey.codec(Registries.BLOCK).optionalFieldOf("vein_rock").forGetter(Config::veinRock)
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
        // blocks of the crystal and the height fraction each sits at (0 at the foot, 1 at the top)
        Growth growth = new Growth(random, config, origin, height);
        double angle = random.nextDouble() * Math.PI * 2;
        double lean = config.lean() * (0.4 + random.nextDouble() * 0.6);
        Vec3 up = new Vec3(Math.cos(angle) * lean, 1, Math.sin(angle) * lean).normalize();
        growth.arm(Vec3.atBottomCenterOf(origin), up, height, config.baseRadius(), 0);
        // Bigger crystals grow a second, shorter stem from the same foot, splayed the other way.
        if (height > 12 && random.nextFloat() < 0.6f) {
            Vec3 other = new Vec3(-Math.cos(angle) * 0.5, 1, -Math.sin(angle) * 0.5).normalize();
            growth.arm(Vec3.atBottomCenterOf(origin), other, height * (0.4 + random.nextDouble() * 0.3),
                    config.baseRadius() * 0.75, 1);
        }
        Map<BlockPos, Double> shape = growth.shape;

        // carry the foot down to solid ground
        Map<BlockPos, Double> base = new HashMap<>();
        for (Map.Entry<BlockPos, Double> entry : shape.entrySet()) {
            if (entry.getKey().getY() != origin.getY()) {
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
        BlockPos tip = null;
        for (Map.Entry<BlockPos, Double> entry : shape.entrySet()) {
            BlockPos pos = entry.getKey();
            if (!replaceable(level, pos)) {
                continue;
            }
            double t = entry.getValue();
            // Oxidation gathers low down: the arms are still bright, the stem and foot green.
            int index = (int) Math.round(top * Math.pow(1 - t, 1.4) * 0.95 + (random.nextDouble() - 0.5) * 0.9);
            if (t < 0.5 && !shape.containsKey(pos.above())) {
                index++;
            }
            if (t > 0.85 || growth.tips.contains(pos)) {
                index = 0;
            }
            level.setBlock(pos, blocks.get(Math.max(0, Math.min(top, index))), Block.UPDATE_CLIENTS);
            placed = true;
            if (tip == null || pos.getY() > tip.getY()) {
                tip = pos;
            }
        }
        if (placed && config.veinRock().isPresent()) {
            roots(level, random, origin, config, config.veinRock().get());
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

    /** The crystal's arms, collected before anything is placed. */
    private static final class Growth {
        final Map<BlockPos, Double> shape = new HashMap<>();
        final java.util.Set<BlockPos> tips = new java.util.HashSet<>();
        private final RandomSource random;
        private final Config config;
        private final BlockPos origin;
        private final int height;

        Growth(RandomSource random, Config config, BlockPos origin, int height) {
            this.random = random;
            this.config = config;
            this.origin = origin;
            this.height = height;
        }

        /**
         * One arm, {@code length} blocks long, starting {@code radius} thick and thinning toward its end. It wanders
         * as it goes and is pulled upward, more so near its end; along the way it sheds knobs and short twigs, and
         * forks into thinner arms that leave at a steep angle and turn up in their turn.
         */
        void arm(Vec3 start, Vec3 direction, double length, double radius, int generation) {
            Vec3 at = start, heading = direction;
            int steps = (int) Math.round(length);
            for (int i = 0; i <= steps; i++) {
                double along = i / Math.max(1.0, steps);
                double r = Math.max(0.5, radius * Math.pow(1 - 0.7 * along, config.taper()));
                blob(at, r);
                if (i == steps) {
                    tips.add(BlockPos.containing(at));
                    return;
                }
                // a crystalline knob: a lone block stuck to the side
                if (random.nextFloat() < 0.45f) {
                    Direction side = Direction.getRandom(random);
                    put(BlockPos.containing(at).relative(side, (int) Math.ceil(r)));
                }
                // a twig: a short spur of single blocks, angled out and up
                if (i > 0 && random.nextFloat() < 0.3f) {
                    Vec3 twig = splay(heading, Mth.lerp(random.nextFloat(), 0.8f, 1.3f));
                    Vec3 tip = at;
                    for (int t = 2 + random.nextInt(3); t > 0; t--) {
                        tip = tip.add(twig);
                        put(BlockPos.containing(tip));
                    }
                    tips.add(BlockPos.containing(tip));
                }
                if (generation < MAX_GENERATIONS && i > 1 && random.nextFloat() < config.branchChance()) {
                    double remaining = steps - i;
                    Vec3 fork = splay(heading, Mth.lerp(random.nextFloat(), 0.5f, 1.1f));
                    arm(at, fork, remaining * Mth.lerp(random.nextDouble(), 0.45, 0.8), r * 0.75, generation + 1);
                }
                double pull = 0.12 + 0.2 * along;
                heading = heading.add((random.nextDouble() - 0.5) * 0.5, pull, (random.nextDouble() - 0.5) * 0.5)
                        .normalize();
                at = at.add(heading);
            }
        }

        /** A direction {@code angle} radians off {@code from}, turned about a random horizontal axis. */
        private Vec3 splay(Vec3 from, double angle) {
            double bearing = random.nextDouble() * Math.PI * 2;
            Vec3 side = new Vec3(Math.cos(bearing), 0, Math.sin(bearing));
            Vec3 turned = from.scale(Math.cos(angle)).add(side.scale(Math.sin(angle)));
            // never downward: a fork always climbs a little
            return new Vec3(turned.x, Math.max(0.25, turned.y), turned.z).normalize();
        }

        private void blob(Vec3 at, double r) {
            int extent = (int) Math.ceil(r);
            BlockPos centre = BlockPos.containing(at);
            for (int dx = -extent; dx <= extent; dx++) {
                for (int dy = -extent; dy <= extent; dy++) {
                    for (int dz = -extent; dz <= extent; dz++) {
                        if (dx * dx + dy * dy + dz * dz <= r * r + random.nextDouble() * 0.6 || dx == 0 && dy == 0 && dz == 0) {
                            put(centre.offset(dx, dy, dz));
                        }
                    }
                }
            }
        }

        private void put(BlockPos pos) {
            if (Math.abs(pos.getX() - origin.getX()) > MAX_REACH || Math.abs(pos.getZ() - origin.getZ()) > MAX_REACH
                    || pos.getY() < origin.getY()) {
                return;
            }
            shape.putIfAbsent(pos, Mth.clamp((pos.getY() - origin.getY()) / (double) height, 0, 1));
        }
    }

    /**
     * Thin roots spreading from the foot through the rock: each starts at the edge of the foot and creeps outward just
     * under the ground, sinking and forking as it goes.
     */
    private static void roots(WorldGenLevel level, RandomSource random, BlockPos origin, Config config,
                              TagKey<Block> rock) {
        int count = 3 + config.veins().sample(random) * 2;
        double start = random.nextDouble() * Math.PI * 2;
        for (int i = 0; i < count; i++) {
            double angle = start + i * Math.PI * 2 / count + (random.nextDouble() - 0.5) * 0.8;
            Vec3 heading = new Vec3(Math.cos(angle), -Mth.lerp(random.nextDouble(), 0.1, 0.5), Math.sin(angle));
            BlockPos from = origin.offset((int) Math.round(Math.cos(angle) * config.baseRadius()), -1,
                    (int) Math.round(Math.sin(angle) * config.baseRadius()));
            CopperVeinFeature.web(level, random, from, heading, config.veinLength().sample(random) + 4, 0.12f,
                    config.blocks(), rock, origin, MAX_REACH);
        }
    }

    private static boolean replaceable(WorldGenLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.isAir() || state.canBeReplaced() && state.getFluidState().isEmpty();
    }
}
