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

import com.linc.alteredaether.worldgen.region.BiomeGroups;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

import java.util.Objects;

/**
 * How far a column lies from the nearest biome of another class, in blocks, up to {@code reach}: 0 on the border,
 * {@code reach} well inside. The classes come from a {@link BiomeGroups} classifier, and the world's biome map from a
 * {@link BiomeEdgeField} that {@link RegionBinding} binds per world. With {@code inside} set, the distance counts only
 * inside that class and is 0 everywhere else; with {@code signed} too, it is negative outside that class, the
 * distance in to its edge. Unbound, it reads {@code reach} (as if no border were near), so a world
 * this mod can't bind keeps its terrain whole.
 */
public final class BiomeEdge implements DensityFunction {
    public static final MapCodec<BiomeEdge> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.xmap(s -> BiomeGroups.valueOf(s.toUpperCase(java.util.Locale.ROOT)), BiomeGroups::id)
                    .fieldOf("classifier").forGetter(BiomeEdge::classifier),
            Codec.intRange(8, 128).fieldOf("reach").forGetter(BiomeEdge::reach),
            Codec.intRange(-1, 15).optionalFieldOf("inside", -1).forGetter(BiomeEdge::inside),
            Codec.BOOL.optionalFieldOf("signed", false).forGetter(BiomeEdge::signed)
    ).apply(i, (classifier, reach, inside, signed) -> new BiomeEdge(classifier, reach, inside, signed, null)));
    public static final KeyDispatchDataCodec<BiomeEdge> CODEC = KeyDispatchDataCodec.of(MAP_CODEC);

    /** Shared by every copy made from one router entry; see {@link RegionMask#mapAll}. */
    public static final class Binding {
        private volatile BiomeEdgeField.Solver solver;

        public void bind(BiomeEdgeField.Solver solver) {
            this.solver = solver;
        }
    }

    private final BiomeGroups classifier;
    private final int reach;
    private final int inside;
    private final boolean signed;
    private final Binding binding;

    private BiomeEdge(BiomeGroups classifier, int reach, int inside, boolean signed, Binding binding) {
        this.classifier = classifier;
        this.reach = reach;
        this.inside = inside;
        this.signed = signed && inside >= 0;
        this.binding = binding;
    }

    public BiomeGroups classifier() {
        return classifier;
    }

    public int reach() {
        return reach;
    }

    public int inside() {
        return inside;
    }

    public boolean signed() {
        return signed;
    }

    public Binding binding() {
        return binding;
    }

    @Override
    public double compute(FunctionContext context) {
        BiomeEdgeField.Solver solver = binding == null ? null : binding.solver;
        if (solver == null) {
            return signed ? -reach : inside >= 0 ? 0 : reach;
        }
        if (signed) {
            return solver.signed(context.blockX(), context.blockZ(), inside);
        }
        return inside >= 0 ? solver.inside(context.blockX(), context.blockZ(), inside)
                : solver.distance(context.blockX(), context.blockZ());
    }

    @Override
    public void fillArray(double[] array, ContextProvider provider) {
        provider.fillAllDirectly(array, this);
    }

    @Override
    public DensityFunction mapAll(Visitor visitor) {
        return visitor.apply(new BiomeEdge(classifier, reach, inside, signed, binding != null ? binding : new Binding()));
    }

    /** Copies reading the same solver are the same function, so NoiseChunk shares their caches. */
    @Override
    public boolean equals(Object other) {
        if (!(other instanceof BiomeEdge edge) || edge.classifier != classifier || edge.reach != reach
                || edge.inside != inside || edge.signed != signed) {
            return false;
        }
        if (edge.binding == binding) {
            return true;
        }
        BiomeEdgeField.Solver solver = binding == null ? null : binding.solver;
        return solver != null && edge.binding != null && edge.binding.solver == solver;
    }

    @Override
    public int hashCode() {
        return Objects.hash(classifier, reach, inside, signed);
    }

    @Override
    public double minValue() {
        return signed ? -reach : 0;
    }

    @Override
    public double maxValue() {
        return reach;
    }

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return CODEC;
    }
}
