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
package com.linc.alteredaether.worldgen.placement;

import com.linc.alteredaether.registry.AARegistries;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;

import java.util.stream.Stream;

/**
 * Exactly one candidate per square cell of {@code cell_chunks} chunks, in a chunk picked by the world seed, so rare
 * things are spread out evenly instead of clumping the way an independent per-chunk roll does. {@code chance} lets
 * only that fraction of cells hold a site at all. Put it first in the list, before any in_square: it passes the chunk
 * origin through, and later modifiers scatter it.
 *
 * <p>Different {@code salt} values give unrelated grids, so two landmarks never share a cell by design.
 */
public class JitteredGrid extends PlacementModifier {
    public static final MapCodec<JitteredGrid> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.intRange(1, 64).fieldOf("cell_chunks").forGetter(g -> g.cellChunks),
            Codec.INT.optionalFieldOf("salt", 0).forGetter(g -> g.salt),
            Codec.floatRange(0, 1).optionalFieldOf("chance", 1f).forGetter(g -> g.chance)
    ).apply(i, JitteredGrid::new));

    private final int cellChunks;
    private final int salt;
    private final float chance;

    public JitteredGrid(int cellChunks, int salt, float chance) {
        this.cellChunks = cellChunks;
        this.salt = salt;
        this.chance = chance;
    }

    @Override
    public Stream<BlockPos> getPositions(PlacementContext context, RandomSource random, BlockPos pos) {
        int chunkX = pos.getX() >> 4, chunkZ = pos.getZ() >> 4;
        int cellX = Math.floorDiv(chunkX, cellChunks), cellZ = Math.floorDiv(chunkZ, cellChunks);
        long hash = mix(context.level.getSeed() ^ mix(salt * 0x632BE59BD9B4E019L)
                ^ mix(cellX * 0x9E3779B97F4A7C15L + cellZ * 0xC2B2AE3D27D4EB4FL));
        if (chance < 1 && ((hash >>> 40) & 0xFFFFFF) / (double) 0x1000000 >= chance) {
            return Stream.empty();
        }
        int siteX = cellX * cellChunks + (int) Math.floorMod(hash, (long) cellChunks);
        int siteZ = cellZ * cellChunks + (int) Math.floorMod(hash >>> 20, (long) cellChunks);
        return siteX == chunkX && siteZ == chunkZ ? Stream.of(pos) : Stream.empty();
    }

    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    @Override
    public PlacementModifierType<?> type() {
        return AARegistries.JITTERED_GRID.get();
    }
}
