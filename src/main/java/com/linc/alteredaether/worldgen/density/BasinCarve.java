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
package com.linc.alteredaether.worldgen.density;

import com.linc.alteredaether.worldgen.basin.BasinField;
import com.linc.alteredaether.worldgen.basin.BasinSite;
import com.linc.alteredaether.worldgen.basin.BasinType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

import java.util.List;

/**
 * {@code argument} with every basin bowl and bank cut out of it. It wraps the whole final density, outside the
 * interpolation, so bowls are cut to the block and the water line the fill feature relies on is exact. The
 * {@link BasinField} that says where the bowls are is bound per world by {@link RegionBinding}, and validates each site
 * against this function's own argument: the terrain as it is before anything is carved.
 */
public final class BasinCarve implements DensityFunction {
    public static final MapCodec<BasinCarve> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("argument").forGetter(BasinCarve::argument),
            Codec.doubleRange(0.1, 4).optionalFieldOf("vertical_scale", 1.0).forGetter(BasinCarve::verticalScale)
    ).apply(i, (argument, vertical) -> new BasinCarve(argument, vertical, null)));
    public static final KeyDispatchDataCodec<BasinCarve> CODEC = KeyDispatchDataCodec.of(MAP_CODEC);

    private static final BasinSite[] NONE = new BasinSite[0];

    /** Shared by every copy made from one world's router; see {@link RegionMask#mapAll}. */
    public static final class Binding {
        private volatile BasinField field;

        public void bind(BasinField field) {
            this.field = field;
        }

        BasinField field() {
            return field;
        }
    }

    /**
     * The basins reaching into the last chunk this thread asked about. The noise filler visits a chunk's blocks in
     * cell order, so the column changes almost every block but the chunk rarely does.
     */
    private static final class Column {
        BasinField field;
        int x = Integer.MIN_VALUE, z = Integer.MIN_VALUE;
        BasinSite[] sites = NONE;
    }

    private static final ThreadLocal<Column> COLUMN = ThreadLocal.withInitial(Column::new);

    private final DensityFunction argument;
    private final double verticalScale;
    private final Binding binding;

    private BasinCarve(DensityFunction argument, double verticalScale, Binding binding) {
        this.argument = argument;
        this.verticalScale = verticalScale;
        this.binding = binding;
    }

    public DensityFunction argument() {
        return argument;
    }

    public double verticalScale() {
        return verticalScale;
    }

    public Binding binding() {
        return binding;
    }

    @Override
    public double compute(FunctionContext context) {
        double density = argument.compute(context);
        // Carving only ever opens blocks, so air needs no lookup at all.
        if (density <= 0 || binding == null) {
            return density;
        }
        BasinField field = binding.field;
        if (field == null) {
            return density;
        }
        int x = context.blockX(), y = context.blockY(), z = context.blockZ();
        for (BasinSite site : sitesAt(field, x, z)) {
            if (site.carves(x, y, z)) {
                return -1;
            }
        }
        return density;
    }

    private static BasinSite[] sitesAt(BasinField field, int x, int z) {
        Column column = COLUMN.get();
        int chunkX = x >> 4, chunkZ = z >> 4;
        if (column.field != field || column.x != chunkX || column.z != chunkZ) {
            List<BasinSite> sites = field.sitesTouching(chunkX << 4, chunkZ << 4, (chunkX << 4) + 15, (chunkZ << 4) + 15);
            sites.removeIf(site -> site.type() == BasinType.ATOLL_LAGOON);
            column.field = field;
            column.x = chunkX;
            column.z = chunkZ;
            column.sites = sites.isEmpty() ? NONE : sites.toArray(BasinSite[]::new);
        }
        return column.sites;
    }

    @Override
    public void fillArray(double[] array, ContextProvider provider) {
        provider.fillAllDirectly(array, this);
    }

    @Override
    public DensityFunction mapAll(Visitor visitor) {
        return visitor.apply(new BasinCarve(argument.mapAll(visitor), verticalScale,
                binding != null ? binding : new Binding()));
    }

    @Override
    public double minValue() {
        return Math.min(-1, argument.minValue());
    }

    @Override
    public double maxValue() {
        return argument.maxValue();
    }

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return CODEC;
    }
}
