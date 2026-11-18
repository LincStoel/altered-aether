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

import com.linc.alteredaether.worldgen.landmark.StackField;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

import java.util.List;

/**
 * {@code argument} with the plateau's sea stacks ({@link StackField}) stood in the sky beside it. The field is bound
 * per world by {@link RegionBinding}; unbound, this is its argument.
 */
public final class PlateauStacks implements DensityFunction {
    public static final MapCodec<PlateauStacks> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("argument").forGetter(PlateauStacks::argument)
    ).apply(i, argument -> new PlateauStacks(argument, null)));
    public static final KeyDispatchDataCodec<PlateauStacks> CODEC = KeyDispatchDataCodec.of(MAP_CODEC);

    private static final StackField.Stack[] NONE = new StackField.Stack[0];

    /** Shared by every copy made from one router entry; see {@link RegionMask#mapAll}. */
    public static final class Binding {
        private volatile StackField field;

        public void bind(StackField field) {
            this.field = field;
        }
    }

    /** The stacks reaching into the last chunk this thread asked about. */
    private static final class Column {
        java.lang.ref.WeakReference<StackField> field = new java.lang.ref.WeakReference<>(null);
        int x = Integer.MIN_VALUE, z = Integer.MIN_VALUE;
        StackField.Stack[] stacks = NONE;
    }

    private static final ThreadLocal<Column> COLUMN = ThreadLocal.withInitial(Column::new);

    private final DensityFunction argument;
    private final Binding binding;

    private PlateauStacks(DensityFunction argument, Binding binding) {
        this.argument = argument;
        this.binding = binding;
    }

    public DensityFunction argument() {
        return argument;
    }

    public Binding binding() {
        return binding;
    }

    @Override
    public double compute(FunctionContext context) {
        double ground = argument.compute(context);
        StackField field = binding == null ? null : binding.field;
        if (field == null || ground > 0) {
            return ground;
        }
        int x = context.blockX(), y = context.blockY(), z = context.blockZ();
        double best = ground;
        for (StackField.Stack stack : stacksAt(field, x, z)) {
            best = Math.max(best, stack.inside(x + 0.5, y, z + 0.5) / 4);
        }
        return best;
    }

    private static StackField.Stack[] stacksAt(StackField field, int x, int z) {
        Column column = COLUMN.get();
        int chunkX = x >> 4, chunkZ = z >> 4;
        if (column.field.get() != field || column.x != chunkX || column.z != chunkZ) {
            List<StackField.Stack> stacks = field.touching(chunkX << 4, chunkZ << 4, (chunkX << 4) + 15,
                    (chunkZ << 4) + 15);
            column.field = new java.lang.ref.WeakReference<>(field);
            column.x = chunkX;
            column.z = chunkZ;
            column.stacks = stacks.isEmpty() ? NONE : stacks.toArray(StackField.Stack[]::new);
        }
        return column.stacks;
    }

    @Override
    public void fillArray(double[] array, ContextProvider provider) {
        provider.fillAllDirectly(array, this);
    }

    @Override
    public DensityFunction mapAll(Visitor visitor) {
        return visitor.apply(new PlateauStacks(argument.mapAll(visitor), binding != null ? binding : new Binding()));
    }

    @Override
    public double minValue() {
        return argument.minValue();
    }

    @Override
    public double maxValue() {
        return Math.max(argument.maxValue(), 4);
    }

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return CODEC;
    }
}
