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

import com.linc.alteredaether.worldgen.grove.BreakField;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

import java.util.List;

/**
 * The grove islands with their breaks ({@link BreakField}): within each edge break, the piece of island inside its
 * circle is moved out across the gap, sunk and tipped, and the gap it left is open sky; a chasm is a fissure of open
 * sky right through the island. Where the moved piece comes to lie
 * over the island's own ground, at the ends of the crack, the two simply join. The field that says where the breaks
 * are is bound per world by {@link RegionBinding}, and checks each site against this function's own argument.
 */
public final class GroveBreak implements DensityFunction {
    public static final MapCodec<GroveBreak> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("argument").forGetter(GroveBreak::argument),
            Codec.doubleRange(0.1, 4).optionalFieldOf("vertical_scale", 1.0).forGetter(GroveBreak::verticalScale)
    ).apply(i, (argument, vertical) -> new GroveBreak(argument, vertical, null)));
    public static final KeyDispatchDataCodec<GroveBreak> CODEC = KeyDispatchDataCodec.of(MAP_CODEC);

    private static final BreakField.Site[] NONE = new BreakField.Site[0];

    /** Shared by every copy made from one world's router; see {@link RegionMask#mapAll}. */
    public static final class Binding {
        private volatile BreakField field;

        public void bind(BreakField field) {
            this.field = field;
        }
    }

    /** The breaks reaching into the last chunk this thread asked about. */
    private static final class Column {
        BreakField field;
        int x = Integer.MIN_VALUE, z = Integer.MIN_VALUE;
        BreakField.Site[] sites = NONE;
    }

    private static final ThreadLocal<Column> COLUMN = ThreadLocal.withInitial(Column::new);

    private final DensityFunction argument;
    private final double verticalScale;
    private final Binding binding;

    private GroveBreak(DensityFunction argument, double verticalScale, Binding binding) {
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
        BreakField field = binding == null ? null : binding.field;
        if (field == null) {
            return argument.compute(context);
        }
        int x = context.blockX(), y = context.blockY(), z = context.blockZ();
        for (BreakField.Site site : sitesAt(field, x, z)) {
            if (site.kind() == BreakField.Kind.CHASM) {
                if (site.fissure(x, y, z) > 0) {
                    return -1;
                }
                continue;
            }
            double[] from = site.source(x, y, z);
            boolean moved = site.inside(from[0], from[1], from[2]) > 0;
            boolean left = site.inside(x, y, z) > 0;
            if (!moved && !left) {
                continue;
            }
            double piece = moved ? argument.compute(new SinglePointContext((int) Math.round(from[0]),
                    (int) Math.round(from[1]), (int) Math.round(from[2]))) : -1;
            // Where it left, open sky; where it came to rest over the island's own ground, the two together.
            return left ? piece : Math.max(piece, argument.compute(context));
        }
        return argument.compute(context);
    }

    private static BreakField.Site[] sitesAt(BreakField field, int x, int z) {
        Column column = COLUMN.get();
        int chunkX = x >> 4, chunkZ = z >> 4;
        if (column.field != field || column.x != chunkX || column.z != chunkZ) {
            List<BreakField.Site> sites = field.touching(chunkX << 4, chunkZ << 4, (chunkX << 4) + 15,
                    (chunkZ << 4) + 15);
            column.field = field;
            column.x = chunkX;
            column.z = chunkZ;
            column.sites = sites.isEmpty() ? NONE : sites.toArray(BreakField.Site[]::new);
        }
        return column.sites;
    }

    @Override
    public void fillArray(double[] array, ContextProvider provider) {
        provider.fillAllDirectly(array, this);
    }

    @Override
    public DensityFunction mapAll(Visitor visitor) {
        return visitor.apply(new GroveBreak(argument.mapAll(visitor), verticalScale,
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
