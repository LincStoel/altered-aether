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
import com.linc.alteredaether.worldgen.grove.GrovePalette;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.BaseCoralWallFanBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.KelpBlock;
import net.minecraft.world.level.block.SeaPickleBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fills the basins that reach into this chunk, writing only this chunk's own columns, so a lake that spans several
 * chunks is filled piece by piece with no seams. Every chunk works from the same {@link BasinField} sites and reads
 * its neighbours' blocks without writing them.
 *
 * <p>In each column under a basin, the open blocks from the water line down to the first solid block become water.
 * Then any open block beside water that won't itself hold water (a cave mouth, a dip in the rim, a hole in the floor)
 * is sealed with stone, so nothing can leak when the water first ticks. Frozen basins freeze only at the surface: a
 * column that would be ice lying straight on the bed is dug one block deeper, so there is always water under the ice.
 * Frozen lakes also get a light dusting of snow along their banks, and the holystone under every basin's bed is
 * richer in zanite and gravitite.
 *
 * <p>Lagoons get a reef (see {@link #colonies}): branching coral colonies built from a seeded grid, each chunk placing
 * the blocks of every colony that reaches into it.
 */
public final class BasinFillFeature extends Feature<NoneFeatureConfiguration> {
    private static final TagKey<Block> AETHER_DIRT =
            TagKey.create(net.minecraft.core.registries.Registries.BLOCK, ResourceLocation.parse("aether:aether_dirt"));
    private static final long REEF_SALT = 0x7F4A7C15L, KIND_SALT = 0x632BE59BL, FLOOR_SALT = 0x1B873593L,
            SNOW_SALT = 0x4CF5AD43L, GLOW_SALT = 0x2545F491L, COLONY_SALT = 0x3C6EF372L, SHORE_SALT = 0x5BE0CD19L;
    /** A lagoon's unbroken beach reaches this many blocks past the water; its patches reach on to the shore's end. */
    private static final int BEACH = 4;
    /** How far above the water a lagoon's shore is looked for: the crest, and the land trimmed back beyond it. */
    private static final int SHORE_RISE = 16;
    /** Coral colonies stand at most one per cell of this many blocks, and reach at most {@link #COLONY_REACH} out. */
    private static final int COLONY_CELL = 4, COLONY_REACH = 4;
    /** How far above the water bank snow is looked for. */
    private static final int BANK_RISE = 8;
    /** Single ores tried under a basin's bed, per 256 columns of water in this chunk. */
    private static final int ORE_TRIES = 24;
    /** The share of those that are gravitite rather than zanite. */
    private static final float GRAVITITE_SHARE = 0.15f;
    private static final Block[] CORAL_BLOCKS = {Blocks.TUBE_CORAL_BLOCK, Blocks.BRAIN_CORAL_BLOCK,
            Blocks.BUBBLE_CORAL_BLOCK, Blocks.FIRE_CORAL_BLOCK, Blocks.HORN_CORAL_BLOCK};
    private static final Block[] CORALS = {Blocks.TUBE_CORAL, Blocks.BRAIN_CORAL, Blocks.BUBBLE_CORAL,
            Blocks.FIRE_CORAL, Blocks.HORN_CORAL};
    private static final Block[] CORAL_FANS = {Blocks.TUBE_CORAL_FAN, Blocks.BRAIN_CORAL_FAN,
            Blocks.BUBBLE_CORAL_FAN, Blocks.FIRE_CORAL_FAN, Blocks.HORN_CORAL_FAN};
    private static final Block[] CORAL_WALL_FANS = {Blocks.TUBE_CORAL_WALL_FAN, Blocks.BRAIN_CORAL_WALL_FAN,
            Blocks.BUBBLE_CORAL_WALL_FAN, Blocks.FIRE_CORAL_WALL_FAN, Blocks.HORN_CORAL_WALL_FAN};

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
        List<BlockPos> beds = new ArrayList<>();
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
                    beds.add(pos.immutable());
                    dressFloor(level, field, site, pos, water - waterBottom + 1, random);
                }
            }
        }
        if (site.type() == BasinType.ATOLL_LAGOON) {
            // A chunk may hold only shore, so this goes before the check for water.
            placed |= shore(level, field, chunk, site, pos);
        }
        if (!placed) {
            return false;
        }
        if (site.type() == BasinType.FROZEN_LAKE) {
            bankSnow(level, field, chunk, site, random, pos);
        }
        if (site.type() == BasinType.ATOLL_LAGOON) {
            colonies(level, field, chunk, site);
            seabed(level, field, chunk, site, bottom, random);
        }
        bedOres(level, beds, random);
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
     * Snow in drifts along a frozen lake's banks: on open ground just beyond the shore, in patches rather than a
     * ring, and thinning out with distance from the water.
     */
    private static void bankSnow(WorldGenLevel level, BasinField field, ChunkPos chunk, BasinSite site,
                                 RandomSource random, BlockPos.MutableBlockPos pos) {
        int water = site.waterLevel();
        for (int x = chunk.getMinBlockX(); x <= chunk.getMaxBlockX(); x++) {
            for (int z = chunk.getMinBlockZ(); z <= chunk.getMaxBlockZ(); z++) {
                double reach = site.reach(x, z);
                // Distance past the shore, as a fraction of the radius, on whichever side of the water this is.
                double beyond = reach - 1;
                // A river's banks are narrow, so its drifts reach as far out in blocks as a lake's.
                if (beyond < 0 || beyond > (site.isRiver() ? 1.2 : 0.3)) {
                    continue;
                }
                double patch = field.noise(SNOW_SALT, x, z, 5);
                if (patch < 0.1 + beyond * 2 || random.nextFloat() < 0.35f) {
                    continue;
                }
                for (int y = water + BANK_RISE; y > water; y--) {
                    BlockState ground = level.getBlockState(pos.set(x, y - 1, z));
                    if (ground.isAir()) {
                        continue;
                    }
                    if (level.getBlockState(pos.set(x, y, z)).isAir()
                            && Blocks.SNOW.defaultBlockState().canSurvive(level, pos)) {
                        level.setBlock(pos, Blocks.SNOW.defaultBlockState()
                                .setValue(SnowLayerBlock.LAYERS, patch > 0.5 ? 2 : 1), Block.UPDATE_CLIENTS);
                    }
                    break;
                }
            }
        }
    }

    /**
     * Quicksoil shores round an atoll's lagoon, whatever biome it lies in: a beach running up from the water to the
     * crest, breaking into patches further out and giving way to the land's own ground. The soil is turned a few
     * blocks deep, so digging into the beach doesn't hit dirt at once; rock is left alone.
     */
    private static boolean shore(WorldGenLevel level, BasinField field, ChunkPos chunk, BasinSite site,
                                 BlockPos.MutableBlockPos pos) {
        BlockState quicksoil = GrovePalette.natural(block("aether:quicksoil", Blocks.SAND).defaultBlockState());
        int water = site.waterLevel();
        boolean placed = false;
        for (int x = chunk.getMinBlockX(); x <= chunk.getMaxBlockX(); x++) {
            for (int z = chunk.getMinBlockZ(); z <= chunk.getMaxBlockZ(); z++) {
                double beyond = (site.reach(x, z) - 1) * site.radius();
                if (beyond < 0 || beyond > BasinSite.SHORE_REACH) {
                    continue;
                }
                if (beyond > BEACH && field.noise(SHORE_SALT, x, z, 5)
                        < 0.1 + 0.8 * (beyond - BEACH) / (BasinSite.SHORE_REACH - BEACH)) {
                    continue;
                }
                for (int y = water + SHORE_RISE; y >= water; y--) {
                    if (level.getBlockState(pos.set(x, y, z)).isAir()) {
                        continue;
                    }
                    for (int d = 0; d < 3 && soil(level.getBlockState(pos.set(x, y - d, z))); d++) {
                        level.setBlock(pos, quicksoil, Block.UPDATE_CLIENTS);
                        placed = true;
                    }
                    break;
                }
            }
        }
        return placed;
    }

    private static boolean soil(BlockState state) {
        return state.is(BlockTags.DIRT) || state.is(AETHER_DIRT);
    }

    /**
     * Zanite and gravitite scattered one block at a time through the holystone a little under the bed, so digging out
     * a basin pays. Gravitite is the rarer, and is only set where it is buried on every side, as it would otherwise
     * float off.
     */
    private static void bedOres(WorldGenLevel level, List<BlockPos> beds, RandomSource random) {
        if (beds.isEmpty()) {
            return;
        }
        Block holystone = block("aether:holystone", null);
        Block zanite = block("aether:zanite_ore", null);
        Block gravitite = block("aether:gravitite_ore", null);
        if (holystone == null || zanite == null || gravitite == null) {
            return;
        }
        int tries = beds.size() * ORE_TRIES / 256 + (random.nextInt(256) < beds.size() * ORE_TRIES % 256 ? 1 : 0);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int t = 0; t < tries; t++) {
            BlockPos bed = beds.get(random.nextInt(beds.size()));
            boolean rare = random.nextFloat() < GRAVITITE_SHARE;
            pos.set(bed.getX(), bed.getY() - 2 - random.nextInt(8), bed.getZ());
            if (level.getBlockState(pos).is(holystone) && (!rare || buried(level, pos))) {
                level.setBlock(pos, (rare ? gravitite : zanite).defaultBlockState(), Block.UPDATE_CLIENTS);
                FeatureStats.placed(rare ? "basin_gravitite" : "basin_zanite");
            }
        }
    }

    private static boolean buried(WorldGenLevel level, BlockPos pos) {
        for (Direction side : Direction.values()) {
            BlockPos next = pos.relative(side);
            BlockState state = level.getBlockState(next);
            if (open(state) || !state.isSolidRender(level, next)) {
                return false;
            }
        }
        return true;
    }

    /**
     * The lowest block of the column's open run down from the water line: everything from here up holds water. The
     * run is measured on blocks as the noise left them, counting water already placed as open, and the reef grown in
     * it too, so a neighbouring chunk that runs before or after this one gets the same answer.
     */
    private static int waterBottom(WorldGenLevel level, BasinSite site, int x, int z, int bottom,
                                   BlockPos.MutableBlockPos pos) {
        int water = site.waterLevel();
        int y = water;
        while (y > bottom && reefOpen(level.getBlockState(pos.set(x, y, z)))) {
            y--;
        }
        // Under ice the water goes at least one block deep: the bed is dug out where it would touch the ice, as long
        // as there is ground beneath it to hold the water. Once dug, the scan above finds the same bottom on its own.
        if (y == water - 1 && site.type().fluid() == BasinType.Fluid.FROZEN
                && !open(level.getBlockState(pos.set(x, water - 2, z)))) {
            return water - 1;
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

    /** Open as far as the reef is concerned: the reef's own blocks count as the water they stand in. */
    private static boolean reefOpen(BlockState state) {
        return open(state) || state.is(BlockTags.CORAL_BLOCKS) || state.is(Blocks.GLOWSTONE);
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

    static BlockState seal(BasinType type) {
        Block holystone = BuiltInRegistries.BLOCK.getOptional(ResourceLocation.parse("aether:holystone"))
                .orElse(Blocks.STONE);
        return switch (type) {
            case ATOLL_LAGOON -> holystone.defaultBlockState();
            case FROZEN_LAKE -> block("aether:icestone", Blocks.PACKED_ICE).defaultBlockState();
            default -> holystone.defaultBlockState();
        };
    }

    /** The floor under the water: soil becomes lake bed, and lagoons get quicksoil and their glowing thickets. */
    private void dressFloor(WorldGenLevel level, BasinField field, BasinSite site, BlockPos.MutableBlockPos floor,
                            int depth, RandomSource random) {
        BlockState ground = level.getBlockState(floor);
        boolean soil = ground.is(BlockTags.DIRT) || ground.is(AETHER_DIRT) || ground.is(Blocks.SNOW_BLOCK)
                || ground.is(BlockTags.SAND) || ground.is(block("aether:quicksoil", Blocks.SAND));
        if (site.type() == BasinType.ATOLL_LAGOON) {
            if (soil) {
                level.setBlock(floor, block("aether:quicksoil", Blocks.SAND).defaultBlockState(), Block.UPDATE_CLIENTS);
            }
            int x = floor.getX(), z = floor.getZ();
            if (depth >= 3 && reef(field, x, z) > reefThreshold() && field.noise(GLOW_SALT, x, z, 9) > 0.55) {
                glowThicket(level, field, floor.immutable(), depth, random);
            }
            return;
        }
        if (!soil) {
            return;
        }
        double patch = field.noise(FLOOR_SALT, floor.getX(), floor.getZ(), 6);
        Block bed = switch (site.type()) {
            case FROZEN_POOL -> block("aether:holystone", Blocks.STONE);
            case FROZEN_LAKE -> patch > 0.4 ? Blocks.CLAY : patch < -0.4 ? Blocks.GRAVEL : block("aether:icestone", Blocks.PACKED_ICE);
            default -> patch > 0.45 ? Blocks.CLAY : patch < -0.5 ? block("aether:mossy_holystone", Blocks.MOSSY_COBBLESTONE)
                    : block("aether:aether_dirt", Blocks.DIRT);
        };
        level.setBlock(floor, bed.defaultBlockState(), Block.UPDATE_CLIENTS);
    }

    /** Where the reef grows: patches of colonies with lanes of open floor between them. */
    private static double reef(BasinField field, int x, int z) {
        return field.noise(REEF_SALT, x, z, 12) + 0.3 * field.noise(REEF_SALT + 1, x, z, 4);
    }

    /** Density 1 puts about two thirds of a lagoon's floor under reef; 4 covers nearly all of it. */
    private static double reefThreshold() {
        return -0.25 - 0.25 * Math.log(AACommonConfig.CORAL_DENSITY.get()) / Math.log(2);
    }

    /**
     * The lagoon's coral, as in a warm ocean: on a grid of {@link #COLONY_CELL}-block cells, every cell over reef
     * holds a colony at a jittered spot, mostly of the patch's kind of coral. A colony is a branching tree, a claw of
     * curving arms or a mushroom cap, built in full from its cell's own random source, so every chunk it reaches
     * builds the same one and places the blocks in its own columns. A block goes only into water that is part of
     * the lagoon, so colonies stop short of the surface and never cut into the shore.
     */
    private static void colonies(WorldGenLevel level, BasinField field, ChunkPos chunk, BasinSite site) {
        double density = AACommonConfig.CORAL_DENSITY.get();
        if (density <= 0) {
            return;
        }
        double threshold = reefThreshold();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int minX = chunk.getMinBlockX(), minZ = chunk.getMinBlockZ(), maxX = chunk.getMaxBlockX(), maxZ = chunk.getMaxBlockZ();
        for (int gx = Math.floorDiv(minX - COLONY_REACH, COLONY_CELL); gx <= Math.floorDiv(maxX + COLONY_REACH, COLONY_CELL); gx++) {
            for (int gz = Math.floorDiv(minZ - COLONY_REACH, COLONY_CELL); gz <= Math.floorDiv(maxZ + COLONY_REACH, COLONY_CELL); gz++) {
                RandomSource random = field.randomAt(COLONY_SALT, gx, gz);
                int x = gx * COLONY_CELL + random.nextInt(COLONY_CELL), z = gz * COLONY_CELL + random.nextInt(COLONY_CELL);
                if (!site.inFootprint(x, z) || reef(field, x, z) < threshold || random.nextFloat() > 0.8f
                        || field.noise(GLOW_SALT, x, z, 9) > 0.55) {
                    continue;
                }
                int floor = reefFloor(level, site, x, z, pos);
                if (site.waterLevel() - floor < 3) {
                    continue;
                }
                int patch = Math.floorMod((int) Math.floor((field.noise(KIND_SALT, x, z, 14) + 1) * 2.5), CORALS.length);
                int kind = random.nextFloat() < 0.7f ? patch : random.nextInt(CORALS.length);
                Colony colony = new Colony(level, site, kind, random, pos);
                BlockPos base = new BlockPos(x, floor + 1, z);
                float shape = random.nextFloat();
                if (shape < 0.5f) {
                    colony.tree(base);
                } else if (shape < 0.75f) {
                    colony.claw(base);
                } else {
                    colony.mushroom(base);
                }
                colony.dress();
                colony.place(chunk);
            }
        }
    }

    /** The first block above the lagoon's bed in a column, the reef's own blocks counting as water. */
    private static int reefFloor(WorldGenLevel level, BasinSite site, int x, int z, BlockPos.MutableBlockPos pos) {
        int y = site.waterLevel();
        int bottom = y - site.maxDepth() - 4;
        while (y > bottom && reefOpen(level.getBlockState(pos.set(x, y, z)))) {
            y--;
        }
        return y;
    }

    /**
     * One coral colony, collected block by block before any is placed. Its skeleton is coral blocks; once that is
     * done, coral and fans grow on its upper faces and wall fans on its sides, as on vanilla's reefs.
     */
    private static final class Colony {
        private final WorldGenLevel level;
        private final BasinSite site;
        private final int kind;
        private final RandomSource random;
        private final BlockPos.MutableBlockPos scratch;
        private final Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        private final List<BlockPos> skeleton = new ArrayList<>();
        private final Map<Long, Integer> floors = new HashMap<>();

        Colony(WorldGenLevel level, BasinSite site, int kind, RandomSource random, BlockPos.MutableBlockPos scratch) {
            this.level = level;
            this.site = site;
            this.kind = kind;
            this.random = random;
            this.scratch = scratch;
        }

        /** Lagoon water, at least one block under the surface. */
        private boolean water(BlockPos pos) {
            if (pos.getY() >= site.waterLevel() || !site.inFootprint(pos.getX(), pos.getZ())) {
                return false;
            }
            int floor = floors.computeIfAbsent(ChunkPos.asLong(pos.getX(), pos.getZ()),
                    k -> reefFloor(level, site, pos.getX(), pos.getZ(), scratch));
            return pos.getY() > floor;
        }

        /** Adds a block of the skeleton, if it lands in open water. */
        private boolean coral(BlockPos pos) {
            if (blocks.containsKey(pos)) {
                return true;
            }
            if (!water(pos)) {
                return false;
            }
            blocks.put(pos, CORAL_BLOCKS[kind].defaultBlockState());
            skeleton.add(pos);
            return true;
        }

        /** Vanilla's coral tree: a short trunk, then two to four arms out and up, each jinking sideways as it rises. */
        void tree(BlockPos base) {
            BlockPos.MutableBlockPos cursor = base.mutable();
            int trunk = 1 + random.nextInt(3);
            for (int i = 0; i < trunk; i++) {
                if (!coral(cursor)) {
                    return;
                }
                cursor.move(Direction.UP);
            }
            BlockPos top = cursor.immutable();
            List<Direction> sides = new ArrayList<>(Direction.Plane.HORIZONTAL.stream().toList());
            net.minecraft.Util.shuffle(sides, random);
            for (Direction side : sides.subList(0, 2 + random.nextInt(3))) {
                cursor.set(top).move(side);
                int length = 2 + random.nextInt(5), since = 0;
                for (int i = 0; i < length && coral(cursor); i++) {
                    since++;
                    cursor.move(Direction.UP);
                    if (i == 0 || since >= 2 && random.nextFloat() < 0.25f) {
                        cursor.move(side);
                        since = 0;
                    }
                }
            }
        }

        /** Vanilla's coral claw: a knot of coral with arms curling out of it sideways and then up. */
        void claw(BlockPos base) {
            if (!coral(base)) {
                return;
            }
            Direction facing = Direction.Plane.HORIZONTAL.getRandomDirection(random);
            List<Direction> sides = new ArrayList<>(List.of(facing, facing.getClockWise(), facing.getCounterClockWise()));
            net.minecraft.Util.shuffle(sides, random);
            BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
            for (Direction side : sides.subList(0, 2 + random.nextInt(2))) {
                cursor.set(base);
                int out = random.nextInt(2) + 2;
                Direction lean = random.nextBoolean() ? side : facing;
                cursor.move(side);
                for (int i = 0; i < out && coral(cursor); i++) {
                    cursor.move(lean == side ? side : Direction.UP);
                    lean = random.nextBoolean() ? side : facing;
                }
                cursor.move(Direction.UP);
                int up = 2 + random.nextInt(3);
                for (int i = 0; i < up && coral(cursor); i++) {
                    cursor.move(Direction.UP);
                }
            }
        }

        /** Vanilla's coral mushroom: a squat cap of coral on a stub, hollow and open at the corners. */
        void mushroom(BlockPos base) {
            int width = 3 + random.nextInt(2), depth = 3 + random.nextInt(2), height = 2 + random.nextInt(3);
            int lift = random.nextInt(2);
            BlockPos corner = base.offset(-width / 2, lift, -depth / 2);
            for (int i = 0; i < lift; i++) {
                coral(base.above(i));
            }
            BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
            for (int dx = 0; dx <= width; dx++) {
                for (int dy = 0; dy <= height; dy++) {
                    for (int dz = 0; dz <= depth; dz++) {
                        int edges = (dx == 0 || dx == width ? 1 : 0) + (dy == 0 || dy == height ? 1 : 0)
                                + (dz == 0 || dz == depth ? 1 : 0);
                        // Faces and edges but not corners, and a crumbling bottom face.
                        if (edges == 0 || edges == 3 || dy == 0 && random.nextFloat() < 0.5f) {
                            continue;
                        }
                        coral(cursor.setWithOffset(corner, dx, dy, dz).immutable());
                    }
                }
            }
        }

        /** Coral and fans on the skeleton's upper faces, the odd sea pickle, and fans on its sides. */
        void dress() {
            for (BlockPos pos : skeleton) {
                BlockPos above = pos.above();
                if (!blocks.containsKey(above) && water(above)) {
                    float roll = random.nextFloat();
                    if (roll < 0.05f) {
                        blocks.put(above, Blocks.SEA_PICKLE.defaultBlockState()
                                .setValue(SeaPickleBlock.PICKLES, 1 + random.nextInt(4)));
                    } else if (roll < 0.3f) {
                        blocks.put(above, (random.nextBoolean() ? CORALS : CORAL_FANS)[kind].defaultBlockState());
                    }
                }
                for (Direction side : Direction.Plane.HORIZONTAL) {
                    BlockPos beside = pos.relative(side);
                    if (random.nextFloat() < 0.2f && !blocks.containsKey(beside) && water(beside)) {
                        blocks.put(beside, CORAL_WALL_FANS[kind].defaultBlockState()
                                .setValue(BaseCoralWallFanBlock.FACING, side));
                    }
                }
            }
        }

        /** Places the colony's blocks that fall in this chunk, into water only. */
        void place(ChunkPos chunk) {
            for (Map.Entry<BlockPos, BlockState> entry : blocks.entrySet()) {
                BlockPos pos = entry.getKey();
                if (pos.getX() >> 4 == chunk.x && pos.getZ() >> 4 == chunk.z
                        && level.getBlockState(pos).is(Blocks.WATER)) {
                    level.setBlock(pos, entry.getValue(), Block.UPDATE_CLIENTS);
                }
            }
            if (!skeleton.isEmpty()) {
                FeatureStats.placed("coral_colony");
            }
        }
    }

    /**
     * The open floor between colonies, column by column: seagrass in meadows, kelp swaying up from the deep middle,
     * and sea pickles here and there.
     */
    private static void seabed(WorldGenLevel level, BasinField field, ChunkPos chunk, BasinSite site, int bottom,
                               RandomSource random) {
        double density = AACommonConfig.CORAL_DENSITY.get();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = chunk.getMinBlockX(); x <= chunk.getMaxBlockX(); x++) {
            for (int z = chunk.getMinBlockZ(); z <= chunk.getMaxBlockZ(); z++) {
                if (!site.inFootprint(x, z)) {
                    continue;
                }
                int y = site.waterLevel();
                while (y > bottom && level.getBlockState(pos.set(x, y, z)).is(Blocks.WATER)) {
                    y--;
                }
                int floor = y, depth = site.waterLevel() - floor;
                BlockState ground = level.getBlockState(pos.set(x, floor, z));
                if (depth < 2 || !ground.isFaceSturdy(level, pos, Direction.UP) || ground.is(BlockTags.CORAL_BLOCKS)) {
                    continue;
                }
                double meadow = field.noise(FLOOR_SALT, x, z, 7);
                float roll = random.nextFloat();
                pos.setY(floor + 1);
                if (depth >= 6 && roll < 0.04f * Math.max(density, 0.5)) {
                    int height = 2 + random.nextInt(depth - 3);
                    for (int i = 0; i < height - 1; i++) {
                        level.setBlock(pos.setY(floor + 1 + i), Blocks.KELP_PLANT.defaultBlockState(), Block.UPDATE_CLIENTS);
                    }
                    level.setBlock(pos.setY(floor + height), Blocks.KELP.defaultBlockState()
                            .setValue(KelpBlock.AGE, random.nextInt(20)), Block.UPDATE_CLIENTS);
                } else if (roll < 0.05f) {
                    level.setBlock(pos, Blocks.SEA_PICKLE.defaultBlockState()
                            .setValue(SeaPickleBlock.PICKLES, 1 + random.nextInt(4)), Block.UPDATE_CLIENTS);
                } else if (depth >= 3 && meadow > 0 && roll < 0.2f) {
                    level.setBlock(pos, Blocks.TALL_SEAGRASS.defaultBlockState()
                            .setValue(DoublePlantBlock.HALF, DoubleBlockHalf.LOWER), Block.UPDATE_CLIENTS);
                    level.setBlock(pos.setY(floor + 2), Blocks.TALL_SEAGRASS.defaultBlockState()
                            .setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER), Block.UPDATE_CLIENTS);
                } else if (roll < (meadow > 0 ? 0.7f : 0.3f)) {
                    level.setBlock(pos, Blocks.SEAGRASS.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    /**
     * One column of a glowstone thicket. Fine noise picks out the stalks, so neighbouring columns rise to different
     * heights and fork apart like branching coral; stalks stop two blocks short of the surface, and a few end in a
     * knob set off by a block of water. Columns between the stalks are a patchy glowing bed.
     */
    private static void glowThicket(WorldGenLevel level, BasinField field, BlockPos floor, int depth,
                                    RandomSource random) {
        BlockState glow = Blocks.GLOWSTONE.defaultBlockState();
        int x = floor.getX(), z = floor.getZ();
        BlockPos.MutableBlockPos pos = floor.mutable();
        double stalk = field.noise(GLOW_SALT + 1, x, z, 1.6) + 0.35 * field.noise(GLOW_SALT + 2, x, z, 0.8);
        if (stalk < 0.15) {
            if (random.nextFloat() < 0.6f) {
                level.setBlock(pos, glow, Block.UPDATE_CLIENTS);
            }
            return;
        }
        level.setBlock(pos, glow, Block.UPDATE_CLIENTS);
        int room = depth - 2;
        int height = Math.min(room, 1 + (int) ((stalk - 0.15) * 1.6 * room) + random.nextInt(2));
        for (int i = 1; i <= height; i++) {
            level.setBlock(pos.setY(floor.getY() + i), glow, Block.UPDATE_CLIENTS);
        }
        if (height + 2 <= room && random.nextFloat() < 0.25f) {
            level.setBlock(pos.setY(floor.getY() + height + 2), glow, Block.UPDATE_CLIENTS);
        }
    }

    private static Block block(String id, Block fallback) {
        return BuiltInRegistries.BLOCK.getOptional(ResourceLocation.parse(id)).orElse(fallback);
    }
}
