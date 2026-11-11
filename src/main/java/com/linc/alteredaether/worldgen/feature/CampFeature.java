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

import com.linc.alteredaether.AlteredAether;
import com.linc.alteredaether.config.AACommonConfig;
import com.linc.alteredaether.worldgen.camp.Blueprint;
import com.linc.alteredaether.worldgen.camp.Blueprints;
import com.linc.alteredaether.worldgen.grove.Giant;
import com.linc.alteredaether.worldgen.grove.GroveField;
import com.linc.alteredaether.worldgen.grove.shape.Part;
import com.linc.alteredaether.worldgen.ruin.RuinPlan;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.loot.LootTable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A hand-built camp or farm ({@link Blueprints}) set on the ground: one variant of its group, turned and mirrored at
 * random, on ground level enough to have been built on and clear of the giant trees.
 *
 * <p>A camp stands on the ground, and levels the few columns under it that sit lower. A farm is a ground layer: its
 * grass and tilled earth take the place of the surface, cutting the higher columns down to it and filling the lower
 * ones up, with whatever grows on it above. Neither leaves air behind; the land round them is left as it was.
 */
public final class CampFeature extends Feature<CampFeature.Config> {
    /**
     * @param group the blueprints to choose from: those named {@code group}, or {@code group_1}, {@code group_2}...
     * @param farm  whether the blueprint's bottom layer replaces the surface (a farm) or stands on it (a camp)
     * @param slope how much the ground under it may rise and fall, in blocks
     */
    public record Config(String group, boolean farm, int slope) implements FeatureConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("group").forGetter(Config::group),
                Codec.BOOL.optionalFieldOf("farm", false).forGetter(Config::farm),
                Codec.intRange(0, 8).optionalFieldOf("slope", 2).forGetter(Config::slope)
        ).apply(i, Config::new));
    }

    public static final ResourceKey<LootTable> LOOT = ResourceKey.create(Registries.LOOT_TABLE,
            AlteredAether.id("chests/grove/camp"));

    public CampFeature() {
        super(Config.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<Config> context) {
        RandomSource random = context.random();
        if (random.nextDouble() >= AACommonConfig.CAMP_SITE_CHANCE.get()) {
            return false;
        }
        List<Blueprint> variants = Blueprints.group(context.config().group());
        if (variants.isEmpty()) {
            return false;
        }
        Blueprint blueprint = variants.get(random.nextInt(variants.size()));
        Rotation rotation = Rotation.getRandom(random);
        Mirror mirror = Mirror.values()[random.nextInt(Mirror.values().length)];
        return place(context.level(), random, context.origin(), blueprint, rotation, mirror, context.config().farm(),
                context.config().slope(), true);
    }

    /** One placed block: where it lies over the pivot, and the state. */
    private record Piece(int dx, int dy, int dz, BlockState state) {
    }

    /**
     * Places a blueprint with its centre at {@code origin}'s column, if the ground there suits it.
     *
     * @param checkGroves whether to keep clear of the giant trees' plans (a test world has none)
     */
    public static boolean place(WorldGenLevel level, RandomSource random, BlockPos origin, Blueprint blueprint,
                                Rotation rotation, Mirror mirror, boolean farm, int slope, boolean checkGroves) {
        BlockPos size = blueprint.size();
        BlockPos pivot = new BlockPos(size.getX() / 2, 0, size.getZ() / 2);
        List<Piece> pieces = new ArrayList<>();
        for (Blueprint.Cell cell : blueprint.cells()) {
            if (cell.air()) {
                continue;
            }
            BlockPos at = Blueprint.turn(cell.pos(), mirror, rotation, pivot).subtract(pivot);
            pieces.add(new Piece(at.getX(), at.getY(), at.getZ(), Blueprint.turn(cell.state(), mirror, rotation)));
        }
        // The footprint is the whole box the builder drew, turned.
        BlockPos a = Blueprint.turn(BlockPos.ZERO, mirror, rotation, pivot).subtract(pivot);
        BlockPos b = Blueprint.turn(new BlockPos(size.getX() - 1, 0, size.getZ() - 1), mirror, rotation, pivot)
                .subtract(pivot);
        int minX = origin.getX() + Math.min(a.getX(), b.getX()), maxX = origin.getX() + Math.max(a.getX(), b.getX());
        int minZ = origin.getZ() + Math.min(a.getZ(), b.getZ()), maxZ = origin.getZ() + Math.max(a.getZ(), b.getZ());

        int width = maxX - minX + 1, depth = maxZ - minZ + 1;
        int[] ground = new int[width * depth];
        int low = Integer.MAX_VALUE, high = Integer.MIN_VALUE;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                int g = RuinPlan.groundOf(level, x, z);
                if (g == Integer.MIN_VALUE || !level.getBlockState(pos.set(x, g, z)).isFaceSturdy(level, pos, Direction.UP)) {
                    return false;
                }
                ground[(x - minX) * depth + (z - minZ)] = g;
                low = Math.min(low, g);
                high = Math.max(high, g);
            }
        }
        if (high - low > slope) {
            return false;
        }
        // A camp stands on the highest of the ground and levels the lower columns up to it; a farm is laid at the
        // middle height, cut down above and built up below.
        int[] sorted = ground.clone();
        Arrays.sort(sorted);
        int reference = farm ? sorted[sorted.length / 2] : high;
        int lift = farm ? 0 : 1;

        if (checkGroves && !clearOfGiants(level, minX, minZ, maxX, maxZ, reference - 1,
                reference + lift + size.getY() + 1)) {
            return false;
        }

        boolean[] covered = new boolean[width * depth];
        for (Piece piece : pieces) {
            if (piece.dy() == 0) {
                covered[(origin.getX() + piece.dx() - minX) * depth + (origin.getZ() + piece.dz() - minZ)] = true;
            }
        }
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                int column = (x - minX) * depth + (z - minZ);
                if (!covered[column]) {
                    continue;
                }
                int g = ground[column];
                if (g < reference) {
                    levelUp(level, pos, x, z, g, reference);
                } else if (g > reference && farm) {
                    for (int y = reference + 1; y <= g; y++) {
                        level.setBlock(pos.set(x, y, z), Blocks.AIR.defaultBlockState(), 2);
                    }
                    // A plant on the old surface would hang in the air.
                    if (level.getBlockState(pos.set(x, g + 1, z)).canBeReplaced()) {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                    }
                }
            }
        }

        for (Piece piece : pieces) {
            pos.set(origin.getX() + piece.dx(), reference + lift + piece.dy(), origin.getZ() + piece.dz());
            BlockState here = level.getBlockState(pos);
            // The ground layer of a farm replaces what it lies on; everything else gives way only to air and plants.
            boolean replaces = farm && piece.dy() == 0;
            if (!replaces && !here.isAir() && !here.canBeReplaced()) {
                continue;
            }
            level.setBlock(pos, piece.state(), 2);
            if (!piece.state().getFluidState().isEmpty()) {
                level.scheduleTick(pos.immutable(), piece.state().getFluidState().getType(), 0);
            }
            if (piece.state().getBlock() instanceof ChestBlock
                    && piece.state().getValue(ChestBlock.TYPE) != ChestType.LEFT) {
                RandomizableContainer.setBlockEntityLootTable(level, random, pos, LOOT);
            }
        }
        FeatureStats.placed("camp/" + blueprint.name());
        return true;
    }

    /**
     * Whether no giant tree or mire reaches the box. The giants are read from their plans, because those of the
     * neighbouring chunks are not built yet when this one is decorated; the wood is kept two blocks off.
     */
    private static boolean clearOfGiants(WorldGenLevel level, int minX, int minZ, int maxX, int maxZ, int minY,
                                         int maxY) {
        GroveField groves = GroveField.get(level.getLevel().dimension());
        if (groves == null) {
            return true;
        }
        if (!groves.miresTouching(minX - 2, minZ - 2, maxX + 2, maxZ + 2).isEmpty()) {
            return false;
        }
        for (Giant giant : groves.touching(minX - 2, minZ - 2, maxX + 2, maxZ + 2)) {
            for (Part part : giant.wood()) {
                if (!part.bounds().touches(minX - 2, minZ - 2, maxX + 2, maxZ + 2)) {
                    continue;
                }
                for (int x = minX - 2; x <= maxX + 2; x++) {
                    for (int z = minZ - 2; z <= maxZ + 2; z++) {
                        if (!part.bounds().column(x, z)) {
                            continue;
                        }
                        for (int y = minY - 1; y <= maxY + 1; y++) {
                            if (part.bounds().height(y) && part.margin(x, y, z) > -2) {
                                return false;
                            }
                        }
                    }
                }
            }
        }
        return true;
    }

    /** Builds a column up from its ground to a height: earth under, the surface block on top. */
    private static void levelUp(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z, int ground, int to) {
        BlockState top = level.getBlockState(pos.set(x, ground, z));
        BlockState under = level.getBlockState(pos.set(x, ground - 1, z));
        if (under.isAir() || !under.getFluidState().isEmpty()) {
            under = top;
        }
        for (int y = ground; y < to; y++) {
            level.setBlock(pos.set(x, y, z), under, 2);
        }
        level.setBlock(pos.set(x, to, z), top, 2);
    }
}
