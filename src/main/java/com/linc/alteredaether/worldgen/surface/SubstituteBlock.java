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
package com.linc.alteredaether.worldgen.surface;

import com.linc.alteredaether.worldgen.provider.SubstituteStateProvider;
import com.mojang.serialization.MapCodec;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.SurfaceRules;

import java.util.List;

/**
 * Places the first available of several blocks, typically a Deep Aether block followed by its Aether and vanilla
 * stand-ins. When none is available (or fallbacks are turned off and Deep Aether is missing) the rule doesn't apply,
 * so the surface rule sequence moves on to whatever comes next.
 */
public record SubstituteBlock(List<SubstituteStateProvider.Candidate> candidates) implements SurfaceRules.RuleSource {
    public static final MapCodec<SubstituteBlock> MAP_CODEC = SubstituteStateProvider.Candidate.CODEC.listOf()
            .fieldOf("candidates").xmap(SubstituteBlock::new, SubstituteBlock::candidates);
    public static final KeyDispatchDataCodec<SubstituteBlock> CODEC = KeyDispatchDataCodec.of(MAP_CODEC);

    @Override
    public SurfaceRules.SurfaceRule apply(SurfaceRules.Context context) {
        BlockState state = new SubstituteStateProvider(candidates).resolved();
        return state.isAir() ? (x, y, z) -> null : (x, y, z) -> state;
    }

    @Override
    public KeyDispatchDataCodec<? extends SurfaceRules.RuleSource> codec() {
        return CODEC;
    }
}
