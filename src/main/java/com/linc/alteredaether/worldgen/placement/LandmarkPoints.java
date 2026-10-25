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
import com.linc.alteredaether.worldgen.landmark.Landmark;
import com.linc.alteredaether.worldgen.landmark.LandmarkField;
import com.linc.alteredaether.worldgen.landmark.LandmarkType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;

import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Replaces the position with the decoration points of one {@code kind} on every landmark of type {@code landmark}
 * that lie in this chunk, each kept with probability {@code chance}. Points keep the incoming height unless
 * {@code hang} is given: then each is hung that many blocks (a range) below the highest ground within 16 blocks of
 * it, for things that float in a gap between walls, and dropped if there is none.
 *
 * <p>Points come from the landmark's own fixed parameters, so a feature placed at them lands in the same spot
 * whichever chunk is decorated first.
 */
public class LandmarkPoints extends PlacementModifier {
    private static final Codec<LandmarkType> TYPE = Codec.STRING.xmap(
            name -> LandmarkType.valueOf(name.toUpperCase(Locale.ROOT)), LandmarkType::id);
    public static final MapCodec<LandmarkPoints> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            TYPE.fieldOf("landmark").forGetter(p -> p.landmark),
            Codec.STRING.fieldOf("kind").forGetter(p -> p.kind),
            Codec.floatRange(0, 1).optionalFieldOf("chance", 1f).forGetter(p -> p.chance),
            Codec.INT.listOf(2, 2).optionalFieldOf("hang").forGetter(p -> p.hang.map(h -> java.util.List.of(h[0], h[1])))
    ).apply(i, (landmark, kind, chance, hang) -> new LandmarkPoints(landmark, kind, chance,
            hang.map(h -> new int[]{h.get(0), h.get(1)}))));

    private final LandmarkType landmark;
    private final String kind;
    private final float chance;
    private final Optional<int[]> hang;

    public LandmarkPoints(LandmarkType landmark, String kind, float chance, Optional<int[]> hang) {
        this.landmark = landmark;
        this.kind = kind;
        this.chance = chance;
        this.hang = hang;
    }

    @Override
    public Stream<BlockPos> getPositions(PlacementContext context, RandomSource random, BlockPos pos) {
        LandmarkField field = LandmarkField.get(context.getLevel().getLevel().dimension());
        if (field == null) {
            return Stream.empty();
        }
        ChunkPos chunk = new ChunkPos(pos);
        Stream.Builder<BlockPos> points = Stream.builder();
        for (Landmark site : field.touching(chunk.getMinBlockX(), chunk.getMinBlockZ(), chunk.getMaxBlockX(),
                chunk.getMaxBlockZ())) {
            if (site.type() != landmark) {
                continue;
            }
            for (BlockPos point : site.points(kind)) {
                if (point.getX() >> 4 != chunk.x || point.getZ() >> 4 != chunk.z || random.nextFloat() >= chance) {
                    continue;
                }
                if (hang.isEmpty()) {
                    points.add(new BlockPos(point.getX(), point.getY() == Integer.MIN_VALUE ? pos.getY() : point.getY(),
                            point.getZ()));
                    continue;
                }
                int walls = Integer.MIN_VALUE;
                for (int d = 0; d < 8; d++) {
                    double angle = d * Math.PI / 4;
                    walls = Math.max(walls, context.getHeight(Heightmap.Types.WORLD_SURFACE_WG,
                            point.getX() + (int) Math.round(Math.cos(angle) * 16),
                            point.getZ() + (int) Math.round(Math.sin(angle) * 16)));
                }
                int y = walls - hang.get()[0] - random.nextInt(Math.max(1, hang.get()[1] - hang.get()[0] + 1));
                if (walls > context.getMinBuildHeight() + 1
                        && context.getHeight(Heightmap.Types.WORLD_SURFACE_WG, point.getX(), point.getZ()) < y - 4) {
                    points.add(new BlockPos(point.getX(), y, point.getZ()));
                }
            }
        }
        return points.build();
    }

    @Override
    public PlacementModifierType<?> type() {
        return AARegistries.LANDMARK_POINTS.get();
    }
}
