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
package com.linc.alteredaether.worldgen.provider;

import com.linc.alteredaether.AlteredAether;
import com.linc.alteredaether.config.AACommonConfig;
import com.linc.alteredaether.registry.AARegistries;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProviderType;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A block state chosen from an ordered list of candidates: the first one whose block exists is used. Candidates are
 * kept as raw ids so a missing mod's block never fails to decode. With Deep Aether fallbacks turned off in config,
 * only the first candidate counts.
 *
 * <p>When nothing resolves, this provides air. Our own features treat air as "skip"; vanilla features that use this
 * provider must sit behind an air-only placement predicate, where placing air is a no-op.
 */
public final class SubstituteStateProvider extends BlockStateProvider {

    /** A block id plus string properties, resolved lazily. */
    public record Candidate(ResourceLocation block, Map<String, String> properties) {
        public static final Codec<Candidate> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.fieldOf("Name").forGetter(Candidate::block),
                Codec.unboundedMap(Codec.STRING, Codec.STRING).optionalFieldOf("Properties", Map.of())
                        .forGetter(Candidate::properties)
        ).apply(i, Candidate::new));

        Optional<BlockState> resolve() {
            Optional<Block> block = BuiltInRegistries.BLOCK.getOptional(this.block);
            if (block.isEmpty()) {
                return Optional.empty();
            }
            BlockState state = block.get().defaultBlockState();
            for (Map.Entry<String, String> entry : properties.entrySet()) {
                Property<?> property = block.get().getStateDefinition().getProperty(entry.getKey());
                if (property == null) {
                    AlteredAether.LOGGER.warn("Block {} has no property {}", this.block, entry.getKey());
                    continue;
                }
                state = withValue(state, property, entry.getValue());
            }
            return Optional.of(state);
        }

        private static <T extends Comparable<T>> BlockState withValue(BlockState state, Property<T> property,
                                                                      String value) {
            return property.getValue(value).map(v -> state.setValue(property, v)).orElse(state);
        }
    }

    public static final MapCodec<SubstituteStateProvider> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Candidate.CODEC.listOf().fieldOf("candidates").forGetter(p -> p.candidates)
    ).apply(i, SubstituteStateProvider::new));

    private final List<Candidate> candidates;
    private volatile BlockState resolved;

    public SubstituteStateProvider(List<Candidate> candidates) {
        this.candidates = List.copyOf(candidates);
    }

    /** The state this provider places, or air when no candidate is available. */
    public BlockState resolved() {
        BlockState state = resolved;
        if (state == null) {
            List<Candidate> usable = AACommonConfig.DEEP_AETHER_FALLBACKS.get() ? candidates : candidates.subList(0, 1);
            state = usable.stream()
                    .map(Candidate::resolve)
                    .flatMap(Optional::stream)
                    .findFirst()
                    .orElse(Blocks.AIR.defaultBlockState());
            resolved = state;
        }
        return state;
    }

    @Override
    public BlockState getState(RandomSource random, BlockPos pos) {
        return resolved();
    }

    @Override
    protected BlockStateProviderType<?> type() {
        return AARegistries.SUBSTITUTE_PROVIDER.get();
    }
}
