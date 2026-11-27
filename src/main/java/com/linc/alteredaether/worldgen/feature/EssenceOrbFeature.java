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

import com.linc.alteredaether.essence.EssenceRegistries;
import com.linc.alteredaether.worldgen.essence.EssenceField;
import com.linc.alteredaether.worldgen.landmark.Landmark;
import com.linc.alteredaether.worldgen.landmark.LandmarkField;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

import java.util.Optional;

/**
 * Hangs the essence sources that fall in this chunk: an Essence Channel over a Highlands islet where the chunk holds its
 * cell's spot ({@link EssenceField}), and an Essence Wellspring over each landmark's highest point (its {@code orb}
 * point). Runs last of the decoration, so the trees and everything else on the islet or landmark are in place; the orb
 * goes a few blocks above the highest of them near its column, looking only at this chunk's own columns, which every
 * chunk has generated alike by now.
 */
public final class EssenceOrbFeature extends Feature<NoneFeatureConfiguration> {
    /** How far above the ground or treetops below it an orb hangs. */
    private static final int CLEARANCE = 3;
    /** How far round its column an orb looks for what is below it. */
    private static final int LOOK = 2;
    /** How far above where an orb is meant to hang it looks for what stands there; higher up is someone else's. */
    private static final int REACH = 40;
    /** A channel whose islet's top reads this far below the field's estimate has drifted off it: trust the estimate. */
    private static final int MISREAD = 12;

    public EssenceOrbFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        ChunkPos chunk = new ChunkPos(context.origin());
        boolean placed = false;
        EssenceField essence = EssenceField.get(level.getLevel().dimension());
        if (essence != null) {
            int cx = Math.floorDiv(chunk.getMinBlockX(), EssenceField.CELL);
            int cz = Math.floorDiv(chunk.getMinBlockZ(), EssenceField.CELL);
            Optional<EssenceField.Site> site = essence.site(cx, cz);
            if (site.isPresent() && inChunk(chunk, site.get().x(), site.get().z())) {
                int ground = groundNear(level, chunk, site.get().x(), site.get().z(), site.get().top() + REACH);
                int y = ground < site.get().top() - MISREAD ? site.get().top() + CLEARANCE : ground + CLEARANCE;
                placed |= hang(level, new BlockPos(site.get().x(), y, site.get().z()),
                        EssenceRegistries.ESSENCE_CHANNEL.get().defaultBlockState());
                FeatureStats.placed("essence/channel");
            }
        }
        LandmarkField landmarks = LandmarkField.get(level.getLevel().dimension());
        if (landmarks != null) {
            for (Landmark landmark : landmarks.touching(chunk.getMinBlockX(), chunk.getMinBlockZ(),
                    chunk.getMaxBlockX(), chunk.getMaxBlockZ())) {
                for (BlockPos point : landmark.points(Landmark.ORB)) {
                    if (!inChunk(chunk, point.getX(), point.getZ())) {
                        continue;
                    }
                    int y = Math.max(point.getY(),
                            groundNear(level, chunk, point.getX(), point.getZ(), point.getY() + REACH) + CLEARANCE);
                    placed |= hang(level, point.atY(y), EssenceRegistries.ESSENCE_WELLSPRING.get().defaultBlockState());
                    FeatureStats.placed("essence/wellspring");
                }
            }
        }
        return placed;
    }

    private static boolean inChunk(ChunkPos chunk, int x, int z) {
        return x >> 4 == chunk.x && z >> 4 == chunk.z;
    }

    /**
     * The top of the highest block round (x, z) no higher than {@code ceiling}, over this chunk's own columns (so a
     * Highlands islet floating far over a landmark doesn't count). Read off the blocks, top down a section at a time
     * past the empty ones, since the world-generation heightmaps still hold the ground as the noise left it: they don't
     * see the landmarks built since, or the trees.
     */
    private static int groundNear(WorldGenLevel level, ChunkPos chunk, int x, int z, int ceiling) {
        ChunkAccess access = level.getChunk(chunk.x, chunk.z);
        LevelChunkSection[] sections = access.getSections();
        int top = level.getMinBuildHeight();
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int index = sections.length - 1; index >= 0; index--) {
            LevelChunkSection section = sections[index];
            if (section.hasOnlyAir()) {
                continue;
            }
            int base = access.getSectionYFromSectionIndex(index) << 4;
            if (base > ceiling) {
                continue;
            }
            for (int dy = Math.min(15, ceiling - base); dy >= 0; dy--) {
                for (int dx = -LOOK; dx <= LOOK; dx++) {
                    for (int dz = -LOOK; dz <= LOOK; dz++) {
                        if (inChunk(chunk, x + dx, z + dz)
                                && !section.getBlockState((x + dx) & 15, dy, (z + dz) & 15).isAir()) {
                            at.set(x + dx, base + dy, z + dz);
                            return at.getY() + 1;
                        }
                    }
                }
            }
        }
        return top;
    }

    /** Sets the orb at {@code pos}, or the first open block above it, inside the world. */
    private static boolean hang(WorldGenLevel level, BlockPos pos, BlockState orb) {
        int top = level.getMaxBuildHeight() - 1;
        BlockPos at = pos.getY() > top ? pos.atY(top) : pos;
        for (int up = 0; up < 8 && at.getY() <= top; up++, at = at.above()) {
            if (level.getBlockState(at).isAir()) {
                return level.setBlock(at, orb, Block.UPDATE_CLIENTS);
            }
        }
        return false;
    }
}
