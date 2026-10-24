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
import com.linc.alteredaether.worldgen.basin.BasinField;
import com.linc.alteredaether.worldgen.basin.BasinSite;
import com.linc.alteredaether.worldgen.basin.BasinType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.KelpBlock;
import net.minecraft.world.level.block.SeaPickleBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

import java.util.List;

/**
 * Fills the basins that reach into this chunk, writing only this chunk's own columns, so a lake that spans several
 * chunks is filled piece by piece with no seams. Every chunk works from the same {@link BasinField} sites and reads
 * its neighbours' blocks without writing them.
 *
 * <p>In each column under a basin, the open blocks from the water line down to the first solid block become water.
 * Then any open block beside water that won't itself hold water (a cave mouth, a dip in the rim, a hole in the floor)
 * is sealed with stone, so nothing can leak when the water first ticks.
 */
public final class BasinFillFeature extends Feature<NoneFeatureConfiguration> {
    private static final TagKey<Block> AETHER_DIRT =
            TagKey.create(net.minecraft.core.registries.Registries.BLOCK, ResourceLocation.parse("aether:aether_dirt"));
    private static final long REEF_SALT = 0x7F4A7C15L, KIND_SALT = 0x632BE59BL, FLOOR_SALT = 0x1B873593L;
    private static final Block[] CORAL_BLOCKS = {Blocks.TUBE_CORAL_BLOCK, Blocks.BRAIN_CORAL_BLOCK,
            Blocks.BUBBLE_CORAL_BLOCK, Blocks.FIRE_CORAL_BLOCK, Blocks.HORN_CORAL_BLOCK};
    private static final Block[] CORALS = {Blocks.TUBE_CORAL, Blocks.BRAIN_CORAL, Blocks.BUBBLE_CORAL,
            Blocks.FIRE_CORAL, Blocks.HORN_CORAL};
    private static final Block[] CORAL_FANS = {Blocks.TUBE_CORAL_FAN, Blocks.BRAIN_CORAL_FAN,
            Blocks.BUBBLE_CORAL_FAN, Blocks.FIRE_CORAL_FAN, Blocks.HORN_CORAL_FAN};

    public BasinFillFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        BasinField field = BasinField.get(level.getLevel().dimension());
        if (field == null) {
            return false;
        }
        ChunkPos chunk = new ChunkPos(context.origin());
        List<BasinSite> sites = field.sitesTouching(chunk.getMinBlockX(), chunk.getMinBlockZ(), chunk.getMaxBlockX(),
                chunk.getMaxBlockZ());
        boolean placed = false;
        for (BasinSite site : sites) {
            placed |= fill(level, field, chunk, site, context.random());
        }
        return placed;
    }

    private boolean fill(WorldGenLevel level, BasinField field, ChunkPos chunk, BasinSite site, RandomSource random) {
        int water = site.waterLevel();
        int bottom = water - site.maxDepth() - 4;
        BlockState liquid = liquid(site.type());
        BlockState seal = seal(site.type());
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        boolean placed = false;
        // Water first, so the seal pass below sees this chunk's columns as they'll finally be.
        for (int x = chunk.getMinBlockX(); x <= chunk.getMaxBlockX(); x++) {
            for (int z = chunk.getMinBlockZ(); z <= chunk.getMaxBlockZ(); z++) {
                if (!site.inFootprint(x, z)) {
                    continue;
                }
                int waterBottom = waterBottom(level, site, x, z, bottom, pos);
                if (waterBottom > water) {
                    continue;
                }
                for (int y = water; y >= waterBottom; y--) {
                    BlockState state = y == water && site.type().fluid() == BasinType.Fluid.FROZEN
                            ? Blocks.ICE.defaultBlockState() : liquid;
                    level.setBlock(pos.set(x, y, z), state, Block.UPDATE_CLIENTS);
                }
                placed = true;
                pos.set(x, waterBottom - 1, z);
                if (waterBottom <= bottom) {
                    // The column fell through the island: give the water a floor.
                    level.setBlock(pos, seal, Block.UPDATE_CLIENTS);
                } else {
                    dressFloor(level, field, site, pos, water - waterBottom + 1, random);
                }
            }
        }
        if (!placed) {
            return false;
        }
        for (int x = chunk.getMinBlockX(); x <= chunk.getMaxBlockX(); x++) {
            for (int z = chunk.getMinBlockZ(); z <= chunk.getMaxBlockZ(); z++) {
                if (site.reach(x, z) > 1.3) {
                    continue;
                }
                int own = site.inFootprint(x, z) ? waterBottom(level, site, x, z, bottom, pos) : water + 1;
                for (int y = water; y > bottom; y--) {
                    if (y >= own || !open(level.getBlockState(pos.set(x, y, z)))) {
                        continue;
                    }
                    if (holdsWater(level, site, x + 1, y, z, bottom) || holdsWater(level, site, x - 1, y, z, bottom)
                            || holdsWater(level, site, x, y, z + 1, bottom) || holdsWater(level, site, x, y, z - 1, bottom)) {
                        level.setBlock(pos.set(x, y, z), seal, Block.UPDATE_CLIENTS);
                    }
                }
            }
        }
        return true;
    }

    /**
     * The lowest block of the column's open run down from the water line: everything from here up holds water. The
     * run is measured on blocks as the noise left them, counting water already placed as open, so a neighbouring
     * chunk that runs before or after this one gets the same answer.
     */
    private static int waterBottom(WorldGenLevel level, BasinSite site, int x, int z, int bottom,
                                   BlockPos.MutableBlockPos pos) {
        int y = site.waterLevel();
        while (y > bottom && open(level.getBlockState(pos.set(x, y, z)))) {
            y--;
        }
        return y + 1;
    }

    private static boolean holdsWater(WorldGenLevel level, BasinSite site, int x, int y, int z, int bottom) {
        if (!site.inFootprint(x, z)) {
            return false;
        }
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        return y >= waterBottom(level, site, x, z, bottom, pos);
    }

    private static boolean open(BlockState state) {
        return state.isAir() || !state.getFluidState().isEmpty() || state.is(Blocks.ICE);
    }

    private static BlockState liquid(BasinType type) {
        if (type.fluid() == BasinType.Fluid.POISON && AACommonConfig.COMPAT_ENABLED.get("deep_aether").get()) {
            Block poison = BuiltInRegistries.BLOCK.getOptional(ResourceLocation.parse("deep_aether:poison")).orElse(null);
            if (poison != null) {
                return poison.defaultBlockState();
            }
        }
        return Blocks.WATER.defaultBlockState();
    }

    private static BlockState seal(BasinType type) {
        Block holystone = BuiltInRegistries.BLOCK.getOptional(ResourceLocation.parse("aether:holystone"))
                .orElse(Blocks.STONE);
        return (type == BasinType.ATOLL_LAGOON ? Blocks.SANDSTONE : holystone).defaultBlockState();
    }

    /** The floor under the water: soil becomes lake bed, and lagoons get sand and their reef. */
    private void dressFloor(WorldGenLevel level, BasinField field, BasinSite site, BlockPos.MutableBlockPos floor,
                            int depth, RandomSource random) {
        BlockState ground = level.getBlockState(floor);
        boolean soil = ground.is(BlockTags.DIRT) || ground.is(AETHER_DIRT) || ground.is(Blocks.SNOW_BLOCK)
                || ground.is(BlockTags.SAND);
        if (site.type() == BasinType.ATOLL_LAGOON) {
            if (soil) {
                level.setBlock(floor, Blocks.SAND.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
            reef(level, field, floor.immutable(), depth, random);
            return;
        }
        if (!soil) {
            return;
        }
        double patch = field.noise(FLOOR_SALT, floor.getX(), floor.getZ(), 6);
        Block bed = switch (site.type()) {
            case FROZEN_POOL -> block("aether:holystone", Blocks.STONE);
            default -> patch > 0.45 ? Blocks.CLAY : patch < -0.5 ? block("aether:mossy_holystone", Blocks.MOSSY_COBBLESTONE)
                    : block("aether:aether_dirt", Blocks.DIRT);
        };
        level.setBlock(floor, bed.defaultBlockState(), Block.UPDATE_CLIENTS);
    }

    /**
     * A lagoon reef: coral mounds in patches, each patch mostly one kind, with fans and sea pickles on top; seagrass
     * on the open sand between; kelp swaying up from the deep middle.
     */
    private void reef(WorldGenLevel level, BasinField field, BlockPos floor, int depth, RandomSource random) {
        double density = AACommonConfig.CORAL_DENSITY.get();
        if (density <= 0 || depth < 2) {
            return;
        }
        int x = floor.getX(), z = floor.getZ();
        double reef = field.noise(REEF_SALT, x, z, 10) + 0.25 * field.noise(REEF_SALT + 1, x, z, 3);
        // Density 1 covers roughly a third of the lagoon floor in coral; 4 covers nearly all of it.
        double threshold = 0.35 - 0.25 * Math.log(density) / Math.log(2);
        int kind = Math.floorMod((int) Math.floor((field.noise(KIND_SALT, x, z, 14) + 1) * 2.5), CORALS.length);
        BlockPos.MutableBlockPos pos = floor.mutable();
        if (depth >= 3 && reef > threshold) {
            level.setBlock(pos, CORAL_BLOCKS[kind].defaultBlockState(), Block.UPDATE_CLIENTS);
            // Mounds build up where the patch is strongest, never closer than two blocks to the surface.
            int mound = Math.min(depth - 2, (int) ((reef - threshold) * 6));
            for (int i = 1; i <= mound; i++) {
                level.setBlock(pos.setY(floor.getY() + i), CORAL_BLOCKS[kind].defaultBlockState(), Block.UPDATE_CLIENTS);
            }
            pos.setY(floor.getY() + mound + 1);
            float roll = random.nextFloat();
            if (roll < 0.06f) {
                level.setBlock(pos, Blocks.SEA_PICKLE.defaultBlockState()
                        .setValue(SeaPickleBlock.PICKLES, 1 + random.nextInt(4)), Block.UPDATE_CLIENTS);
            } else if (roll < 0.36f) {
                level.setBlock(pos, CORALS[kind].defaultBlockState(), Block.UPDATE_CLIENTS);
            } else if (roll < 0.62f) {
                level.setBlock(pos, CORAL_FANS[kind].defaultBlockState(), Block.UPDATE_CLIENTS);
            }
            return;
        }
        pos.setY(floor.getY() + 1);
        float roll = random.nextFloat();
        if (depth >= 6 && roll < 0.05f * density) {
            int height = 2 + random.nextInt(depth - 3);
            for (int i = 0; i < height - 1; i++) {
                level.setBlock(pos.setY(floor.getY() + 1 + i), Blocks.KELP_PLANT.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
            level.setBlock(pos.setY(floor.getY() + height), Blocks.KELP.defaultBlockState()
                    .setValue(KelpBlock.AGE, random.nextInt(20)), Block.UPDATE_CLIENTS);
        } else if (depth >= 3 && roll < 0.12f) {
            level.setBlock(pos, Blocks.TALL_SEAGRASS.defaultBlockState()
                    .setValue(DoublePlantBlock.HALF, DoubleBlockHalf.LOWER), Block.UPDATE_CLIENTS);
            level.setBlock(pos.setY(floor.getY() + 2), Blocks.TALL_SEAGRASS.defaultBlockState()
                    .setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER), Block.UPDATE_CLIENTS);
        } else if (roll < 0.4f) {
            level.setBlock(pos, Blocks.SEAGRASS.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    private static Block block(String id, Block fallback) {
        return BuiltInRegistries.BLOCK.getOptional(ResourceLocation.parse(id)).orElse(fallback);
    }
}
