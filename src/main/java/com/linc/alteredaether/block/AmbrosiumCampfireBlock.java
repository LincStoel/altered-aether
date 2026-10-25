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
package com.linc.alteredaether.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.CampfireBlock;

/**
 * A campfire of skyroot logs burning with ambrosium's golden flame. It is a campfire in every way that matters: it
 * cooks, smokes, signals and burns what stands in it, and takes the vanilla campfire's block entity (it is added to
 * that block entity type's valid blocks in AABlockEvents), so cooking recipes and hoppers see nothing unusual.
 */
public final class AmbrosiumCampfireBlock extends CampfireBlock {
    public static final MapCodec<CampfireBlock> CODEC = simpleCodec(AmbrosiumCampfireBlock::new);

    public AmbrosiumCampfireBlock(Properties properties) {
        super(true, 1, properties);
    }

    @Override
    public MapCodec<CampfireBlock> codec() {
        return CODEC;
    }
}
