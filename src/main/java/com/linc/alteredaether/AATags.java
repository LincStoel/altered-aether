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
import net.minecraft.world.item.Item;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;

/** Tags this mod reads. */
public final class AATags {
    /** Blocks breezes may spawn on naturally. */
    public static final TagKey<Block> BREEZE_SPAWNABLE_ON =
            TagKey.create(Registries.BLOCK, AlteredAether.id("breeze_spawnable_on"));
    /** Ground Ars familiars may spawn on naturally in the Aether. */
    public static final TagKey<Block> FAMILIAR_SPAWNABLE_ON =
            TagKey.create(Registries.BLOCK, AlteredAether.id("familiar_spawnable_on"));

    /** Ground nagas may spawn on in the Aether: Aether soil and holystone, as grass and stone in the overworld. */
    public static final TagKey<Block> NAGA_SPAWNABLE_ON =
            TagKey.create(Registries.BLOCK, AlteredAether.id("naga_spawnable_on"));
    /** What Mowzie's lanterns may spawn over in the Aether: soil, leaves and logs, as in the overworld. */
    public static final TagKey<Block> LANTERN_SPAWNABLE_ON =
            TagKey.create(Registries.BLOCK, AlteredAether.id("lantern_spawnable_on"));
    /** Aether biomes where Mowzie's lanterns drift: Skyroot Cathedral, and Deep Aether's Luminescent Forest. */
    public static final TagKey<Biome> HAS_LANTERNS = TagKey.create(Registries.BIOME, AlteredAether.id("has_lanterns"));

    /** Biomes where Tempest storms gather and strike. */
    public static final TagKey<Biome> HAS_STORMS = TagKey.create(Registries.BIOME, AlteredAether.id("has_storms"));
    /** Biomes where fireflies drift: the giant-tree groves. */
    public static final TagKey<Biome> HAS_FIREFLIES = TagKey.create(Registries.BIOME, AlteredAether.id("has_fireflies"));
    /** Ground a storm strike may scorch, besides dirt and sand. */
    public static final TagKey<Block> SCORCHABLE = TagKey.create(Registries.BLOCK, AlteredAether.id("scorchable"));

    /** Overworld-metal armour and tools that draw Tempest lightning when worn or carried in the hotbar. */
    public static final TagKey<Item> ATTRACTS_LIGHTNING =
            TagKey.create(Registries.ITEM, AlteredAether.id("attracts_lightning"));
    /** Copper that calls a strike down on a player standing on it in a storm biome without boots. */
    public static final TagKey<Block> CHARGED_COPPER = TagKey.create(Registries.BLOCK, AlteredAether.id("charged_copper"));

    /** Food that tames a wild aerwhale. */
    public static final TagKey<Item> AERWHALE_TAMING_FOOD =
            TagKey.create(Registries.ITEM, AlteredAether.id("aerwhale_taming_food"));
    /** Food that breeds tamed aerwhales and speeds up their calves' growth. */
    public static final TagKey<Item> AERWHALE_BREEDING_FOOD =
            TagKey.create(Registries.ITEM, AlteredAether.id("aerwhale_breeding_food"));

    private AATags() {
    }
}
