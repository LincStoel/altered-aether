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
package com.linc.alteredaether.worldgen.landmark;

import com.linc.alteredaether.worldgen.TerrainProbe;
import com.linc.alteredaether.worldgen.grove.Giant;
import com.linc.alteredaether.worldgen.grove.GrandfatherTree;
import com.linc.alteredaether.worldgen.grove.GroveBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The Skyroot Cathedral's landmark, the Grandfather tree. It is drawn and built the way the grove's giants are (see
 * {@link GrandfatherTree}); this only finds it a broad, level stand in the heart of a cathedral.
 */
record Grandfather(int x, int z, Giant giant) implements Landmark {
    @Nullable
    static Grandfather tryAt(LandmarkField field, int x, int z, RandomSource random) {
        TerrainProbe probe = field.probe();
        if (!field.ringSuits(LandmarkType.GRANDFATHER, x, z, 44, 7)) {
            return null;
        }
        int centre = probe.surface(x, z);
        if (centre == Integer.MIN_VALUE) {
            return null;
        }
        int low = centre, held = 0;
        int tolerance = (int) Math.round(16 * field.vertical());
        for (int d = 0; d < 8; d++) {
            double angle = d * Math.PI / 4;
            int g = probe.surfaceNear(x + (int) Math.round(Math.cos(angle) * 24),
                    z + (int) Math.round(Math.sin(angle) * 24), centre, centre - 40, centre + 40);
            if (g != Integer.MIN_VALUE && Math.abs(g - centre) <= tolerance) {
                held++;
                low = Math.min(low, g);
            }
        }
        if (held < 7) {
            return null;
        }
        return new Grandfather(x, z, GrandfatherTree.sketch(probe, random, field.vertical(), field.buildTop(), x, z,
                low));
    }

    @Override
    public LandmarkType type() {
        return LandmarkType.GRANDFATHER;
    }

    @Override
    public double extent() {
        return giant.extent();
    }

    @Override
    public void build(WorldGenLevel level, ChunkPos chunk) {
        int x0 = chunk.getMinBlockX(), z0 = chunk.getMinBlockZ(), x1 = chunk.getMaxBlockX(), z1 = chunk.getMaxBlockZ();
        GroveBuilder.paint(level, x0, z0, x1, z1, giant);
        GroveBuilder.build(level, x0, z0, x1, z1, giant);
        GroveBuilder.furnish(level, x0, z0, x1, z1, giant);
    }

    @Override
    public List<BlockPos> points(String kind) {
        return giant.points(kind);
    }

    @Override
    public String detail() {
        return giant.points("heartwood").stream().findFirst().map(p -> "heartwood room at y " + p.getY()).orElse("");
    }

    @Override
    public int visitY() {
        return giant.base() + 2;
    }
}
