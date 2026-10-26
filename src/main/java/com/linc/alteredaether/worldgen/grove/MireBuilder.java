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

import com.linc.alteredaether.config.AACommonConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Digs, fills and dresses the {@link Mire}s that reach into a block rectangle, usually a chunk, writing only its own
 * columns, after the manner of the basin fill. Lakes that overlap are read as one water ({@link Waters}): a column on
 * any islet is dry, otherwise it holds the highest water of the lakes over it.
 *
 * <p>How far down a column's water goes is decided from the plan alone (see {@link #waterBottom}), so a neighbour
 * chunk gets the same answer whether it runs before or after this one, and whether or not the giants there are built
 * yet. After everything is placed, any open block beside water that wouldn't
 * itself hold water is sealed, so nothing can leak when the water first ticks; where two connected lakes stand at
 * different levels, that leaves a low levee between them.
 */
public final class MireBuilder {
    private static final long SALT = 0x3243F6A8885A308DL;
    /** How far above the water dug columns and banks are cleared. */
    private static final int CLEAR = 16;
    private static final BlockState WATER = Blocks.WATER.defaultBlockState();
    private static final Block STONE = block("aether:holystone", Blocks.STONE);
    private static final TagKey<Block> AETHER_DIRT =
            TagKey.create(Registries.BLOCK, ResourceLocation.parse("aether:aether_dirt"));

    /** The lakes round a rectangle, read as one water. */
    public static final class Waters {
        public static final Waters NONE = new Waters(List.of());
        private final List<Mire> mires;

        public Waters(List<Mire> mires) {
            this.mires = mires;
        }

        public boolean isEmpty() {
            return mires.isEmpty();
        }

        public boolean islet(int x, int z) {
            for (Mire mire : mires) {
                if (mire.islet(x, z)) {
                    return true;
                }
            }
            return false;
        }

        /** The water line over a column, or MIN_VALUE where no lake holds water. */
        public int water(int x, int z) {
            int water = Integer.MIN_VALUE;
            for (Mire mire : mires) {
                if (mire.islet(x, z)) {
                    return Integer.MIN_VALUE;
                }
                if (mire.reach(x + 0.5, z + 0.5) < 1) {
                    water = Math.max(water, mire.water());
                }
            }
            return water;
        }

        /** The planned bed in a wet column: the lowest of the lakes over it. */
        int bed(int x, int z) {
            int bed = Integer.MAX_VALUE;
            for (Mire mire : mires) {
                if (mire.reach(x + 0.5, z + 0.5) < 1) {
                    bed = Math.min(bed, mire.bed(x, z));
                }
            }
            return bed;
        }

        /** The ground an islet column is raised to: a block over the highest water it stands in. */
        int isletTop(int x, int z) {
            int top = Integer.MIN_VALUE;
            for (Mire mire : mires) {
                if (mire.islet(x, z)) {
                    top = Math.max(top, mire.water() + 1);
                }
            }
            return top;
        }

        /** The highest the ground may stand in a bank column; MAX_VALUE off every bank. */
        int bankTop(int x, int z) {
            int top = Integer.MAX_VALUE;
            for (Mire mire : mires) {
                top = Math.min(top, mire.bankTop(x, z));
            }
            return top;
        }

        /** Blocks off the nearest islet's shore, out over the water. */
        double offIslet(int x, int z) {
            double nearest = Double.MAX_VALUE;
            for (Mire mire : mires) {
                for (Mire.Islet islet : mire.islets()) {
                    nearest = Math.min(nearest, islet.past(x + 0.5, z + 0.5));
                }
            }
            return nearest;
        }

        /** Whether a block lies in the water: a giant's carve opening it fills with water, not air. */
        public boolean flooded(int x, int y, int z) {
            int water = water(x, z);
            return water != Integer.MIN_VALUE && y <= water;
        }
    }

    // --------------------------------------------------------------------------------------------- digging --

    /**
     * Shapes the ground: raises islets that would sit under the water, clears the lake down to its bed and fills it,
     * lays the bed, and cuts the banks back to a gentle slope. Runs before the giants are built.
     */
    public static void dig(WorldGenLevel level, int minX, int minZ, int maxX, int maxZ, Waters waters) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        Block grass = block("aether:aether_grass_block", Blocks.GRASS_BLOCK);
        Block dirt = block("aether:aether_dirt", Blocks.DIRT);
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                int top = waters.isletTop(x, z);
                if (top != Integer.MIN_VALUE) {
                    raise(level, pos, x, z, top, grass, dirt);
                    continue;
                }
                int water = waters.water(x, z);
                if (water == Integer.MIN_VALUE) {
                    int bank = waters.bankTop(x, z);
                    if (bank != Integer.MAX_VALUE) {
                        cutBank(level, pos, x, z, bank, bank + CLEAR, grass, dirt);
                    }
                    continue;
                }
                int bed = waters.bed(x, z);
                for (int y = water + CLEAR; y > bed; y--) {
                    if (!level.getBlockState(pos.set(x, y, z)).isAir()) {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                    }
                }
                for (int y = water; y > bed; y--) {
                    level.setBlock(pos.set(x, y, z), WATER, Block.UPDATE_CLIENTS);
                }
                // The water never follows the ground down: a dip, a cave or a dungeon roof under the bed is
                // covered over, so the lake can't drain into it.
                if (!solid(level.getBlockState(pos.set(x, bed, z)))) {
                    level.setBlock(pos, natural(dirt), Block.UPDATE_CLIENTS);
                }
                layBed(level, pos, x, z);
            }
        }
    }

    /** Builds an islet column up to {@code top} if the lake has left it lower, burying any grass it covers. */
    private static void raise(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z, int top, Block grass,
                              Block dirt) {
        int ground = top + CLEAR;
        while (ground > top - CLEAR && !solid(level.getBlockState(pos.set(x, ground, z)))) {
            ground--;
        }
        if (ground >= top || ground <= top - CLEAR) {
            return;
        }
        if (level.getBlockState(pos.set(x, ground, z)).is(grass)) {
            level.setBlock(pos, natural(dirt), Block.UPDATE_CLIENTS);
        }
        for (int y = ground + 1; y <= top; y++) {
            level.setBlock(pos.set(x, y, z), natural(y == top ? grass : dirt), Block.UPDATE_CLIENTS);
        }
    }

    /** Clears a bank column above its slope, and grasses over the dirt that leaves bare. */
    private static void cutBank(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z, int bank, int from,
                                Block grass, Block dirt) {
        boolean cut = false;
        for (int y = from; y > bank; y--) {
            if (!level.getBlockState(pos.set(x, y, z)).isAir()) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                cut = true;
            }
        }
        if (!cut) {
            return;
        }
        BlockState top = level.getBlockState(pos.set(x, bank, z));
        if (top.is(dirt)) {
            level.setBlock(pos, natural(grass), Block.UPDATE_CLIENTS);
        } else if (top.is(STONE)) {
            // Cut deep into a rise, the bank lays bare the stone under its soil: it grasses over that too.
            level.setBlock(pos, natural(grass), Block.UPDATE_CLIENTS);
            for (int y = bank - 1; y >= bank - 2 && level.getBlockState(pos.set(x, y, z)).is(STONE); y--) {
                level.setBlock(pos, natural(dirt), Block.UPDATE_CLIENTS);
            }
        }
    }

    /** Soil under the water becomes mud, or with no Deep Aether, dirt and clay; here and there, a mat of roots. */
    private static void layBed(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z) {
        BlockState ground = level.getBlockState(pos);
        if (!ground.is(BlockTags.DIRT) && !ground.is(AETHER_DIRT)) {
            return;
        }
        double patch = noise(SALT + 3, x, z, 5);
        BlockState bed;
        if (patch > 0.55 && GroveBuilder.hash(SALT, x, 4, z) < 0.6) {
            bed = deepOr("muddy_yagroot_roots", Blocks.MUDDY_MANGROVE_ROOTS);
        } else {
            BlockState mud = deep("aether_mud");
            bed = mud != null ? mud : patch < -0.45 ? Blocks.CLAY.defaultBlockState()
                    : natural(block("aether:aether_dirt", Blocks.DIRT));
        }
        level.setBlock(pos, bed, Block.UPDATE_CLIENTS);
    }

    /** What a flooded hollow's bed is made of: mud, or with no Deep Aether, dirt and clay. */
    static BlockState bed(int x, int z) {
        BlockState mud = deep("aether_mud");
        return mud != null ? mud : noise(SALT + 3, x, z, 5) < -0.45 ? Blocks.CLAY.defaultBlockState()
                : natural(block("aether:aether_dirt", Blocks.DIRT));
    }

    /**
     * The lowest block of a column's water: always the block over the planned bed, which digging makes solid. It
     * comes from the plan alone, so a neighbour chunk gets the same answer whatever has been built there yet.
     */
    static int waterBottom(Waters waters, int x, int z) {
        return waters.bed(x, z) + 1;
    }

    private static boolean solid(BlockState state) {
        return !state.isAir() && !state.canBeReplaced() && !state.is(BlockTags.LOGS) && !state.is(BlockTags.LEAVES);
    }

    // -------------------------------------------------------------------------------------------- dressing --

    /**
     * The swamp's growth: clumps of roots standing up through the water off the islets' shores and here and there in
     * open water, a scattering of lily pads, seagrass in meadows on the bed, and with Deep Aether, cattails along the
     * shores. Runs after the giants are built, so only water is taken.
     */
    public static void dress(WorldGenLevel level, int minX, int minZ, int maxX, int maxZ, Waters waters) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockState roots = deepOr("yagroot_roots", Blocks.MANGROVE_ROOTS);
        BlockState muddy = deepOr("muddy_yagroot_roots", Blocks.MUDDY_MANGROVE_ROOTS);
        BlockState cattails = deep("aether_cattails"), tallCattails = deep("tall_aether_cattails");
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                int water = waters.water(x, z);
                if (water == Integer.MIN_VALUE) {
                    if (cattails != null && tallCattails != null) {
                        shoreGrowth(level, waters, pos, x, z, cattails, tallCattails);
                    }
                    continue;
                }
                int bottom = waterBottom(waters, x, z);
                double off = waters.offIslet(x, z);
                double r = GroveBuilder.hash(SALT, x, 1, z);
                boolean clump = off < 3.5 ? r < 0.05 + 0.4 * (1 - off / 3.5) && noise(SALT + 1, x, z, 4) > -0.3
                        : noise(SALT + 2, x, z, 7) > 0.6 && r < 0.35;
                if (clump) {
                    rootColumn(level, pos, x, z, bottom, water, roots, muddy);
                    continue;
                }
                if (noise(SALT + 4, x, z, 6) > 0.25 && GroveBuilder.hash(SALT, x, 2, z) < 0.09
                        && level.getBlockState(pos.set(x, water + 1, z)).isAir()
                        && level.getBlockState(pos.set(x, water, z)).is(Blocks.WATER)) {
                    level.setBlock(pos.set(x, water + 1, z), Blocks.LILY_PAD.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
                seagrass(level, pos, x, z, bottom, water);
            }
        }
    }

    /** A clump of roots from the bed up through the water, sometimes standing a block or two clear of it. */
    private static void rootColumn(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z, int bottom,
                                   int water, BlockState roots, BlockState muddy) {
        BlockState bed = level.getBlockState(pos.set(x, bottom - 1, z));
        if (bed.is(BlockTags.DIRT) || bed.is(AETHER_DIRT) || bed.is(Blocks.CLAY) || bed.is(block("deep_aether:aether_mud", Blocks.MUD))) {
            level.setBlock(pos, muddy, Block.UPDATE_CLIENTS);
        }
        double r = GroveBuilder.hash(SALT, x, 3, z);
        int above = r < 0.15 ? 2 : r < 0.45 ? 1 : 0;
        for (int y = bottom; y <= water + above; y++) {
            BlockState here = level.getBlockState(pos.set(x, y, z));
            if (y <= water && here.is(Blocks.WATER)) {
                level.setBlock(pos, waterlogged(roots, true), Block.UPDATE_CLIENTS);
            } else if (y > water && here.isAir()) {
                level.setBlock(pos, waterlogged(roots, false), Block.UPDATE_CLIENTS);
            } else if (y > water) {
                break;
            }
        }
    }

    /** Seagrass on the bed, in meadows, tall where the water is deep enough. */
    private static void seagrass(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z, int bottom,
                                 int water) {
        BlockState below = level.getBlockState(pos.set(x, bottom - 1, z));
        if (!below.isFaceSturdy(level, pos, Direction.UP) || !level.getBlockState(pos.set(x, bottom, z)).is(Blocks.WATER)) {
            return;
        }
        double meadow = noise(SALT + 5, x, z, 8);
        double r = GroveBuilder.hash(SALT, x, 5, z);
        if (bottom + 1 <= water - 1 && meadow > 0.1 && r < 0.12
                && level.getBlockState(pos.set(x, bottom + 1, z)).is(Blocks.WATER)) {
            level.setBlock(pos.set(x, bottom, z), Blocks.TALL_SEAGRASS.defaultBlockState()
                    .setValue(DoublePlantBlock.HALF, DoubleBlockHalf.LOWER), Block.UPDATE_CLIENTS);
            level.setBlock(pos.set(x, bottom + 1, z), Blocks.TALL_SEAGRASS.defaultBlockState()
                    .setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER), Block.UPDATE_CLIENTS);
        } else if (r < (meadow > 0 ? 0.35 : 0.08)) {
            level.setBlock(pos.set(x, bottom, z), Blocks.SEAGRASS.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /** Deep Aether's cattails in the grass along a shore, a block or two from the water. */
    private static void shoreGrowth(WorldGenLevel level, Waters waters, BlockPos.MutableBlockPos pos, int x, int z,
                                    BlockState cattails, BlockState tall) {
        double r = GroveBuilder.hash(SALT, x, 6, z);
        if (r >= 0.3 || noise(SALT + 6, x, z, 5) < -0.1) {
            return;
        }
        int water = Integer.MIN_VALUE;
        for (int dx = -2; dx <= 2 && water == Integer.MIN_VALUE; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                water = Math.max(water, waters.water(x + dx, z + dz));
            }
        }
        if (water == Integer.MIN_VALUE) {
            return;
        }
        for (int y = water + 3; y >= water; y--) {
            BlockState ground = level.getBlockState(pos.set(x, y, z));
            if (ground.isAir()) {
                continue;
            }
            if (!ground.is(block("aether:aether_grass_block", Blocks.GRASS_BLOCK))
                    || !level.getBlockState(pos.set(x, y + 1, z)).isAir()) {
                return;
            }
            if (r < 0.12 && level.getBlockState(pos.set(x, y + 2, z)).isAir()) {
                level.setBlock(pos.set(x, y + 1, z), with(tall, DoubleBlockHalf.LOWER), Block.UPDATE_CLIENTS);
                level.setBlock(pos.set(x, y + 2, z), with(tall, DoubleBlockHalf.UPPER), Block.UPDATE_CLIENTS);
            } else {
                level.setBlock(pos.set(x, y + 1, z), cattails, Block.UPDATE_CLIENTS);
            }
            return;
        }
    }

    // ------------------------------------------------------------------------------------------------ seal --

    /**
     * Seals every open block beside the water that wouldn't itself hold water: a dip in a bank, a carve's edge, the
     * step between two lakes at different levels. Dirt near the surface, grassed where it is bare; holystone deeper.
     */
    public static void seal(WorldGenLevel level, int minX, int minZ, int maxX, int maxZ, Waters waters) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockState dirt = natural(block("aether:aether_dirt", Blocks.DIRT));
        BlockState grass = natural(block("aether:aether_grass_block", Blocks.GRASS_BLOCK));
        int[][] sides = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        int[] tops = new int[4], bottoms = new int[4];
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                int highest = Integer.MIN_VALUE, lowest = Integer.MAX_VALUE;
                for (int i = 0; i < 4; i++) {
                    tops[i] = waters.water(x + sides[i][0], z + sides[i][1]);
                    if (tops[i] != Integer.MIN_VALUE) {
                        bottoms[i] = waterBottom(waters, x + sides[i][0], z + sides[i][1]);
                        highest = Math.max(highest, tops[i]);
                        lowest = Math.min(lowest, bottoms[i]);
                    }
                }
                if (highest == Integer.MIN_VALUE) {
                    continue;
                }
                int own = waters.water(x, z);
                int ownBottom = own == Integer.MIN_VALUE ? Integer.MAX_VALUE : waterBottom(waters, x, z);
                for (int y = highest; y >= lowest; y--) {
                    if (own != Integer.MIN_VALUE && y <= own && y >= ownBottom) {
                        continue;
                    }
                    BlockState state = level.getBlockState(pos.set(x, y, z));
                    if (!state.isAir() && state.getFluidState().isEmpty() && !state.canBeReplaced()) {
                        continue;
                    }
                    for (int i = 0; i < 4; i++) {
                        if (tops[i] != Integer.MIN_VALUE && y <= tops[i] && y >= bottoms[i]) {
                            boolean bare = y >= highest - 1 && level.getBlockState(pos.set(x, y + 1, z)).isAir();
                            level.setBlock(pos.set(x, y, z), y < highest - 1 ? holystone() : bare ? grass : dirt,
                                    Block.UPDATE_CLIENTS);
                            break;
                        }
                    }
                }
            }
        }
    }

    // --------------------------------------------------------------------------------------------- helpers --

    /** Smooth value noise in [-1, 1] with features about {@code scale} blocks across. */
    static double noise(long salt, double x, double z, double scale) {
        double fx = x / scale, fz = z / scale;
        int ix = Mth.floor(fx), iz = Mth.floor(fz);
        double tx = fx - ix, tz = fz - iz;
        tx = tx * tx * (3 - 2 * tx);
        tz = tz * tz * (3 - 2 * tz);
        return Mth.lerp2(tx, tz, lattice(salt, ix, iz), lattice(salt, ix + 1, iz), lattice(salt, ix, iz + 1),
                lattice(salt, ix + 1, iz + 1)) * 2 - 1;
    }

    private static double lattice(long salt, int x, int z) {
        return GroveBuilder.hash(salt, x, 0, z);
    }

    private static BlockState waterlogged(BlockState state, boolean wet) {
        return state.hasProperty(BlockStateProperties.WATERLOGGED)
                ? state.setValue(BlockStateProperties.WATERLOGGED, wet) : state;
    }

    private static BlockState with(BlockState state, DoubleBlockHalf half) {
        return state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                ? state.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, half) : state;
    }

    private static BlockState holystone() {
        return natural(block("aether:holystone", Blocks.STONE));
    }

    private static BlockState natural(Block block) {
        return GrovePalette.natural(block.defaultBlockState());
    }

    private static BlockState natural(BlockState state) {
        return GrovePalette.natural(state);
    }

    private static Block block(String id, Block fallback) {
        return BuiltInRegistries.BLOCK.getOptional(ResourceLocation.parse(id)).orElse(fallback);
    }

    /** A Deep Aether block, or null without Deep Aether or with its compat switch off. */
    @Nullable
    private static BlockState deep(String name) {
        if (!ModList.get().isLoaded("deep_aether") || !AACommonConfig.compat("deep_aether")) {
            return null;
        }
        return BuiltInRegistries.BLOCK.getOptional(ResourceLocation.parse("deep_aether:" + name))
                .map(Block::defaultBlockState).orElse(null);
    }

    private static BlockState deepOr(String name, Block fallback) {
        BlockState state = deep(name);
        return state != null ? state : fallback.defaultBlockState();
    }

    private MireBuilder() {
    }
}
