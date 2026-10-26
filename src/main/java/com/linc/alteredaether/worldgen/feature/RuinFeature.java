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

import com.linc.alteredaether.config.AACommonConfig;
import com.linc.alteredaether.worldgen.ruin.RuinPlan;
import com.linc.alteredaether.worldgen.ruin.Ruins;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;

/**
 * A grove ruin ({@link Ruins}): a cottage shell, a broken keep or a ring of standing stones; or with {@code head}, a
 * row of one to three great stone heads, as on the Aether Plateau. It only stands on ground level enough to have been
 * built on, and never inside a giant.
 */
public final class RuinFeature extends Feature<RuinFeature.Config> {
    /** @param kind a {@link Ruins.Kind}'s id, or {@code any} for a house, keep or circle by the odds */
    public record Config(String kind) implements FeatureConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("kind").forGetter(Config::kind)
        ).apply(i, Config::new));
    }

    public RuinFeature() {
        super(Config.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<Config> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        Ruins.Kind kind = Ruins.kind(context.config().kind());
        if (kind == null) {
            double r = random.nextDouble();
            kind = r < 0.55 ? Ruins.Kind.COTTAGE : r < 0.72 ? Ruins.Kind.KEEP : Ruins.Kind.CIRCLE;
        }
        double chance = kind == Ruins.Kind.HEAD ? AACommonConfig.STONE_HEAD_CHANCE.get()
                : AACommonConfig.RUIN_CHANCE.get();
        if (random.nextDouble() >= chance) {
            return false;
        }
        BlockPos origin = context.origin();
        if (kind == Ruins.Kind.HEAD) {
            return heads(level, random, origin);
        }
        RuinPlan plan = Ruins.draw(kind, random, origin.getX(), origin.getZ());
        if (!suits(level, plan, kind)) {
            return false;
        }
        ChunkPos chunk = new ChunkPos(origin);
        plan.place(level, chunk.getMinBlockX() - 16, chunk.getMinBlockZ() - 16, chunk.getMaxBlockX() + 16,
                chunk.getMaxBlockZ() + 16, (x, z) -> RuinPlan.groundOf(level, x, z));
        FeatureStats.placed("ruin/" + kind.id());
        return true;
    }

    /**
     * A row of one to three stone heads side by side, all gazing the same way, each sunk where the ground suits it.
     * The row stays within reach of the chunk, so no head is cut off at its edge.
     */
    private static boolean heads(WorldGenLevel level, RandomSource random, BlockPos origin) {
        double roll = random.nextDouble();
        int count = roll < 0.4 ? 1 : roll < 0.75 ? 2 : 3;
        Direction facing = Direction.Plane.HORIZONTAL.getRandomDirection(random);
        Direction side = facing.getClockWise();
        int spacing = 9 + random.nextInt(2);
        ChunkPos chunk = new ChunkPos(origin);
        boolean any = false;
        for (int i = 0; i < count; i++) {
            int along = (i - (count - 1) / 2) * spacing;
            int back = random.nextInt(3) - 1;
            int x = origin.getX() + side.getStepX() * along - facing.getStepX() * back;
            int z = origin.getZ() + side.getStepZ() * along - facing.getStepZ() * back;
            RuinPlan plan = Ruins.head(random, x, z, facing);
            if (!suits(level, plan, Ruins.Kind.HEAD)) {
                continue;
            }
            plan.place(level, chunk.getMinBlockX() - 16, chunk.getMinBlockZ() - 16, chunk.getMaxBlockX() + 16,
                    chunk.getMaxBlockZ() + 16, (px, pz) -> RuinPlan.groundOf(level, px, pz));
            FeatureStats.placed("ruin/head");
            any = true;
        }
        return any;
    }

    /** Whether the ground under a ruin is level enough and clear of the giants. */
    private static boolean suits(WorldGenLevel level, RuinPlan plan, Ruins.Kind kind) {
        int low = Integer.MAX_VALUE, high = Integer.MIN_VALUE, wooded = 0, columns = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (long[] anchor : plan.anchors()) {
            int x = (int) anchor[0], z = (int) anchor[1];
            int g = RuinPlan.groundOf(level, x, z);
            if (g == Integer.MIN_VALUE) {
                return false;
            }
            low = Math.min(low, g);
            high = Math.max(high, g);
            columns++;
            int top = level.getHeight(level instanceof net.minecraft.server.level.WorldGenRegion
                    ? Heightmap.Types.WORLD_SURFACE_WG : Heightmap.Types.WORLD_SURFACE, x, z);
            for (int y = g + 1; y < Math.min(top, g + 24); y++) {
                if (level.getBlockState(pos.set(x, y, z)).is(BlockTags.LOGS)) {
                    wooded++;
                    break;
                }
            }
        }
        // A head stands on one column, so test the ground round it too.
        if (kind == Ruins.Kind.HEAD) {
            int centre = low;
            for (int dx = -4; dx <= 4; dx += 4) {
                for (int dz = -4; dz <= 4; dz += 4) {
                    int g = RuinPlan.groundOf(level, (int) plan.anchors().getFirst()[0] + dx,
                            (int) plan.anchors().getFirst()[1] + dz);
                    if (g == Integer.MIN_VALUE || Math.abs(g - centre) > 3) {
                        return false;
                    }
                }
            }
        }
        int slope = switch (kind) {
            case CIRCLE -> 6;
            case HEAD -> 3;
            default -> 4;
        };
        return high - low <= slope && wooded <= columns * 0.15;
    }
}
