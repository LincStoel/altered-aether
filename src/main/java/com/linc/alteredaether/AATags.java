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
package com.linc.alteredaether;

import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

/** Tags this mod reads. */
public final class AATags {
    /** Blocks breezes may spawn on naturally. */
    public static final TagKey<Block> BREEZE_SPAWNABLE_ON =
            TagKey.create(Registries.BLOCK, AlteredAether.id("breeze_spawnable_on"));
    /** Ground Ars familiars may spawn on naturally in the Aether. */
    public static final TagKey<Block> FAMILIAR_SPAWNABLE_ON =
            TagKey.create(Registries.BLOCK, AlteredAether.id("familiar_spawnable_on"));

    private AATags() {
    }
}
