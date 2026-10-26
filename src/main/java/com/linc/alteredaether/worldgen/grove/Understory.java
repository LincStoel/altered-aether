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
package com.linc.alteredaether.worldgen.grove;

import com.linc.alteredaether.worldgen.AABiomes;
import com.linc.alteredaether.worldgen.grove.shape.Part;
import com.linc.alteredaether.worldgen.grove.shape.Trunk;
import com.linc.alteredaether.worldgen.grove.shape.Tube;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The growth between the giants, so the ground under them reads as old forest rather than meadow: a midstory of young
 * skyroots in clumps, drawn in the giants' own vocabulary, and a floor of ferns, grass and moss, thickest in the shade.
 * A slow noise opens glades where neither grows, so the forest still has clearings to walk into. The Nurselog Hollow
 * gets a thinner share of both. Everything keeps clear of the giants, their roots and their trails.
 */
public final class Understory {
    /** Young trees are tried on a grid of cells this many blocks wide, at most one to a cell. */
    private static final int CELL = 8;
    /** Where the clump noise falls below this, a glade: no young trees, little cover. */
    private static final double GLADE = -0.35;
    /** Per-cell odds of a young tree in the thick of a clump. */
    private static final double TREE_CHANCE = 0.6;
    /** The Nurselog Hollow's share of the Cathedral's growth. */
    private static final double NURSELOG_SHARE = 0.4;
    private static final double MIN_SPACING = 6.5;

    private record Noises(long seed, SimplexNoise clumps, SimplexNoise patches) {
    }

    @Nullable
    private static volatile Noises noises;

    private static Noises noises(long seed) {
        Noises n = noises;
        if (n == null || n.seed != seed) {
            WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(seed ^ 0x5DEECE66DL * 31));
            n = new Noises(seed, new SimplexNoise(random), new SimplexNoise(random));
            noises = n;
        }
        return n;
    }

    /** Grows the understory in one chunk. False if nothing grew. */
    public static boolean place(WorldGenLevel level, RandomSource random, int minX, int minZ, double density) {
        GroveField field = GroveField.get(level.getLevel().dimension());
        if (field == null) {
            return false;
        }
        Noises n = noises(level.getSeed());
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int grown = 0;
        List<int[]> trees = new ArrayList<>();
        for (int cx = 0; cx < 16; cx += CELL) {
            for (int cz = 0; cz < 16; cz += CELL) {
                int x = minX + cx + 1 + random.nextInt(CELL - 2), z = minZ + cz + 1 + random.nextInt(CELL - 2);
                double roll = random.nextDouble();
                double share = share(level, pos.set(x, level.getSeaLevel(), z)) * density;
                double thick = Mth.clamp((clump(n, x, z) - GLADE) / 0.7, 0, 1);
                if (share <= 0 || roll >= TREE_CHANCE * share * thick) {
                    continue;
                }
                if (trees.stream().anyMatch(t -> Math.hypot(t[0] - x, t[1] - z) < MIN_SPACING)) {
                    continue;
                }
                if (tree(level, random, field, pos, x, z)) {
                    trees.add(new int[]{x, z});
                    grown++;
                }
            }
        }
        BlockState moss = deep("aether_moss_carpet");
        for (int x = minX; x < minX + 16; x++) {
            for (int z = minZ; z < minZ + 16; z++) {
                if (cover(level, random, n, pos, x, z, density, moss)) {
                    grown++;
                }
            }
        }
        return grown > 0;
    }

    private static double clump(Noises n, int x, int z) {
        return n.clumps.getValue(x / 44.0, z / 44.0);
    }

    /** How much of the growth a column's biome takes: all in the Cathedral, a share in the Hollow, none elsewhere. */
    private static double share(WorldGenLevel level, BlockPos pos) {
        Holder<Biome> biome = level.getBiome(pos);
        return biome.is(AABiomes.SKYROOT_CATHEDRAL) ? 1 : biome.is(AABiomes.NURSELOG_HOLLOW) ? NURSELOG_SHARE : 0;
    }

    /**
     * The ground in a column: the first soil from the top that is open to the air, looking down past leaves and logs,
     * so the floor under a canopy or a limb is found too. MIN_VALUE where the first thing met is anything else.
     * {@code shade[0]} is set when leaves hang somewhere over it.
     */
    private static int ground(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z, boolean[] shade) {
        shade[0] = false;
        int bottom = level.getMinBuildHeight();
        boolean open = true;
        for (int y = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1; y > bottom; y--) {
            BlockState state = level.getBlockState(pos.set(x, y, z));
            if (state.is(BlockTags.LEAVES)) {
                shade[0] = true;
                open = false;
                continue;
            }
            if (state.is(BlockTags.LOGS)) {
                open = false;
                continue;
            }
            if (state.isAir() || state.canBeReplaced() && state.getFluidState().isEmpty()) {
                open = true;
                continue;
            }
            return open && state.is(BlockTags.DIRT) && !state.is(DIRT_PATH) ? y : Integer.MIN_VALUE;
        }
        return Integer.MIN_VALUE;
    }

    private static final Block DIRT_PATH = BuiltInRegistries.BLOCK
            .getOptional(ResourceLocation.parse("aether:aether_dirt_path")).orElse(Blocks.DIRT_PATH);

    // ------------------------------------------------------------------------------------------------- trees --

    /**
     * A young skyroot, twelve to twenty-five blocks tall: a tapering trunk with a little flare and a few short roots,
     * and a small crown of the giants' own kind. Only where the ground is soil, the trunk's footing is clear of every
     * other log and trail, and nothing but leaves stands in the way of its rise.
     */
    private static boolean tree(WorldGenLevel level, RandomSource random, GroveField field,
                                BlockPos.MutableBlockPos pos, int x, int z) {
        boolean[] shade = new boolean[1];
        int ground = ground(level, pos, x, z, shade);
        if (ground == Integer.MIN_VALUE) {
            return false;
        }
        double v = field.vertical();
        double radius = Mth.lerp(random.nextDouble(), 1.0, 2.0);
        int height = (int) Math.round(Mth.lerp(random.nextDouble(), 12, 25) * Math.max(v, 0.7));
        // Its footing: soil all round at about its own height, and no wood or trail of anything else.
        double clear = radius + 1.5;
        for (int d = 0; d < 6; d++) {
            double angle = d * Math.PI / 3;
            int px = Mth.floor(x + 0.5 + Math.cos(angle) * clear), pz = Mth.floor(z + 0.5 + Math.sin(angle) * clear);
            BlockState below = level.getBlockState(pos.set(px, ground, pz));
            if (!below.is(BlockTags.DIRT) || below.is(DIRT_PATH)) {
                return false;
            }
            for (int y = ground + 1; y <= ground + 4; y++) {
                if (level.getBlockState(pos.set(px, y, pz)).is(BlockTags.LOGS)) {
                    return false;
                }
            }
        }
        // Its rise: open air, or only leaves high up, where it grows into the giants' canopy.
        for (int y = ground + 1; y <= ground + height + 4; y++) {
            BlockState state = level.getBlockState(pos.set(x, y, z));
            boolean high = y > ground + height * 0.6;
            if (!state.isAir() && !state.canBeReplaced() && !(high && state.is(BlockTags.LEAVES))) {
                return false;
            }
        }
        String biome = level.getBiome(pos.set(x, ground, z)).is(AABiomes.SKYROOT_CATHEDRAL) ? GroveField.CATHEDRAL
                : GroveField.NURSELOG;
        Sketch s = new Sketch(null, random, v, level.getMaxBuildHeight(), x, z, ground);
        s.palette = field.paletteAt(x, z, biome);
        double angle = random.nextDouble() * Mth.TWO_PI, lean = s.range(0.0, 0.06);
        double cx = x + 0.5, cz = z + 0.5;
        int top = ground + height;
        Trunk trunk = s.trunk(new Trunk(cx, cz, ground, top, radius, 0.55, 1.2, Math.cos(angle) * lean,
                Math.sin(angle) * lean, 0, 0, 0, 0, 0.45, 0.18, 4, Part.Role.LOG));
        s.footRadius = radius;
        s.crown(trunk.centreX(top), top, trunk.centreZ(top), s.range(5.0, 8.5), Math.max(0.85, radius * 0.5),
                Math.cos(angle) * 0.15, Math.sin(angle) * 0.15);
        // A side branch or two low down, so the crown doesn't sit on a bare pole.
        int sides = s.range(0, 2);
        for (int i = 0; i < sides; i++) {
            double y = Mth.lerp(s.range(0.45, 0.75), ground, top);
            double a = random.nextDouble() * Mth.TWO_PI;
            s.twig(trunk.centreX(y), y, trunk.centreZ(y), Math.cos(a), s.range(0.2, 0.6), Math.sin(a),
                    s.range(3.0, 5.0));
        }
        int roots = s.range(3, 5);
        double phase = random.nextDouble() * Mth.TWO_PI;
        for (int i = 0; i < roots; i++) {
            double a = phase + i * Mth.TWO_PI / roots + s.range(-0.3, 0.3);
            double reach = radius + s.range(1.5, 3.5);
            s.part(Tube.line(cx + Math.cos(a) * radius * 0.5, ground + radius * 0.7, cz + Math.sin(a) * radius * 0.5,
                    cx + Math.cos(a) * reach, ground - 0.4, cz + Math.sin(a) * reach, radius * 0.6, 0.75,
                    Part.Role.LOG));
        }
        Giant young = s.done(GiantType.YOUNG, random.nextLong());
        // A feature may write to the chunks round its own; the tree stands inside its chunk and reaches no further.
        int chunkX = Math.floorDiv(x, 16) * 16, chunkZ = Math.floorDiv(z, 16) * 16;
        GroveBuilder.build(level, chunkX - 16, chunkZ - 16, chunkX + 31, chunkZ + 31, young);
        return true;
    }

    // ------------------------------------------------------------------------------------------------- cover --

    /**
     * The floor in one column. Under leaves, ferns in drifts and grass between them, and with Deep Aether, moss; in the
     * sun of a gap, grass, flowers and the odd sapling come up; in a glade, a little grass and the odd flower.
     */
    private static boolean cover(WorldGenLevel level, RandomSource random, Noises n, BlockPos.MutableBlockPos pos,
                                 int x, int z, double density, @Nullable BlockState moss) {
        boolean[] shade = new boolean[1];
        int ground = ground(level, pos, x, z, shade);
        if (ground == Integer.MIN_VALUE) {
            return false;
        }
        double share = share(level, pos.set(x, ground + 1, z)) * density;
        if (share <= 0) {
            return false;
        }
        double roll = random.nextDouble() / share;
        double patch = n.patches.getValue(x / 14.0, z / 14.0);
        BlockState plant;
        boolean tall = false;
        if (clump(n, x, z) < GLADE) {
            plant = roll < 0.16 ? Blocks.SHORT_GRASS.defaultBlockState() : roll < 0.19 ? flower(random) : null;
        } else if (shade[0]) {
            if (patch > 0.15) {
                tall = roll < 0.12;
                plant = tall ? Blocks.LARGE_FERN.defaultBlockState() : roll < 0.6 ? Blocks.FERN.defaultBlockState()
                        : roll < 0.7 ? Blocks.SHORT_GRASS.defaultBlockState() : null;
            } else if (moss != null && patch < -0.3) {
                plant = roll < 0.7 ? moss : null;
            } else {
                tall = roll < 0.04;
                plant = tall ? Blocks.TALL_GRASS.defaultBlockState() : roll < 0.32 ? Blocks.SHORT_GRASS.defaultBlockState()
                        : roll < 0.4 ? Blocks.FERN.defaultBlockState() : roll < 0.41 ? sapling() : null;
            }
        } else {
            tall = roll < 0.1;
            plant = tall ? Blocks.TALL_GRASS.defaultBlockState() : roll < 0.45 ? Blocks.SHORT_GRASS.defaultBlockState()
                    : roll < 0.53 ? flower(random) : roll < 0.57 ? sapling() : null;
        }
        if (plant == null) {
            return false;
        }
        pos.set(x, ground + 1, z);
        if (!level.getBlockState(pos).isAir() || !plant.canSurvive(level, pos)) {
            return false;
        }
        if (tall) {
            if (!level.getBlockState(pos.above()).isAir()) {
                return false;
            }
            DoublePlantBlock.placeAt(level, plant, pos, 2);
        } else {
            level.setBlock(pos, plant, 2);
        }
        return true;
    }

    @Nullable
    private static BlockState flower(RandomSource random) {
        return block(random.nextBoolean() ? "aether:white_flower" : "aether:purple_flower");
    }

    @Nullable
    private static BlockState sapling() {
        return block("aether:skyroot_sapling");
    }

    @Nullable
    private static BlockState block(String id) {
        return BuiltInRegistries.BLOCK.getOptional(ResourceLocation.parse(id)).map(Block::defaultBlockState)
                .orElse(null);
    }

    /** A Deep Aether block, or null without Deep Aether or with its compat switch off. */
    @Nullable
    private static BlockState deep(String name) {
        return ModList.get().isLoaded("deep_aether") && com.linc.alteredaether.config.AACommonConfig.compat("deep_aether")
                ? block("deep_aether:" + name) : null;
    }

    private Understory() {
    }
}
