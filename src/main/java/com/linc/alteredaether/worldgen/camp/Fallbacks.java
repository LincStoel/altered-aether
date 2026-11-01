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
package com.linc.alteredaether.worldgen.camp;

import com.linc.alteredaether.AlteredAether;
import com.linc.alteredaether.config.AACommonConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Which block stands in for a hand-built structure's block. The structures were built with several optional mods'
 * blocks in them; each one is used as built when its mod is loaded (and its {@code [compat]} switch is on), and
 * otherwise gives way to the closest Aether block, then the closest vanilla one, or to nothing where there is none
 * that fits. Properties the stand-in has too (facing, waterlogging, a chest's side) are carried over.
 */
public final class Fallbacks {
    private static final Set<String> REPORTED = new HashSet<>();

    /** Whether a block can be used as it is: registered, and its mod's integration not switched off. */
    public static boolean available(ResourceLocation id) {
        return BuiltInRegistries.BLOCK.containsKey(id) && AACommonConfig.compat(id.getNamespace());
    }

    /** The state a structure's palette entry becomes; air where nothing fits. */
    public static BlockState resolve(String name, Map<String, String> properties) {
        ResourceLocation id = ResourceLocation.tryParse(name);
        if (id == null) {
            return Blocks.AIR.defaultBlockState();
        }
        if (available(id)) {
            return carry(BuiltInRegistries.BLOCK.get(id).defaultBlockState(), properties);
        }
        return fallback(name, properties);
    }

    /** What stands in for a block that is not available, whether or not it is. */
    public static BlockState fallback(String name, Map<String, String> properties) {
        return switch (name) {
            // The stools are slabs; the posts and the walls of stripped logs are fence.
            case "ascended_quark:skyroot_stool" -> first(properties, "aether:skyroot_slab");
            case "ascended_quark:skyroot_post", "ascended_quark:stripped_skyroot_post" ->
                    first(Map.of("waterlogged", properties.getOrDefault("waterlogged", "false")),
                            "aether:skyroot_fence");
            case "aether_beyond_parity:skyroot_log_wall", "aether_beyond_parity:stripped_skyroot_log_wall" ->
                    first(fenceSides(properties), "aether:skyroot_fence");
            // A leaf carpet lies on the ground as a texture; there is nothing of the Aether's to put there.
            case "ascended_quark:skyroot_leaf_carpet" -> Blocks.AIR.defaultBlockState();
            // Chests: Beyond Parity's skyroot chest, else a plain one.
            case "ascended_quark:skyroot_chest", "aether_beyond_parity:skyroot_chest" ->
                    first(properties, "aether_beyond_parity:skyroot_chest", "minecraft:chest");
            case "visualworkbench:aether_beyond_parity/skyroot_crafting_table" ->
                    first(properties, "aether_beyond_parity:skyroot_crafting_table", "minecraft:crafting_table");
            // No fire lights the Aether: the tiki torch is an ambrosium torch, the lower half only.
            case "deep_aether:ambrosium_tiki_torch" -> "lower".equals(properties.get("half"))
                    ? first(Map.of(), "aether:ambrosium_torch") : Blocks.AIR.defaultBlockState();
            case "deep_aether:skyjade_lantern" -> first(properties, "minecraft:soul_lantern");
            // Squash grows as blue berries: the fruit is a bush, its stems the bush's stem.
            case "deep_aether:blue_squash" -> first(Map.of(), "aether:berry_bush");
            case "deep_aether:squash_stem" -> first(Map.of(), "aether:berry_bush_stem");
            case "deep_aether:attached_squash_stem" -> Blocks.AIR.defaultBlockState();
            default -> {
                if (REPORTED.add(name)) {
                    AlteredAether.LOGGER.warn("No stand-in for block {} in a camp structure; leaving it out", name);
                }
                yield Blocks.AIR.defaultBlockState();
            }
        };
    }

    /** A wall's arms as a fence's: any arm that is not "none" is a connection. */
    private static Map<String, String> fenceSides(Map<String, String> wall) {
        Map<String, String> fence = new HashMap<>();
        fence.put("waterlogged", wall.getOrDefault("waterlogged", "false"));
        for (String side : new String[]{"north", "east", "south", "west"}) {
            fence.put(side, String.valueOf(!"none".equals(wall.getOrDefault(side, "none"))));
        }
        return fence;
    }

    /** The first of the blocks that can be used, with the properties it shares; air if none can. */
    private static BlockState first(Map<String, String> properties, String... ids) {
        for (String id : ids) {
            Optional<ResourceLocation> location = Optional.ofNullable(ResourceLocation.tryParse(id))
                    .filter(Fallbacks::available);
            if (location.isPresent()) {
                return carry(BuiltInRegistries.BLOCK.get(location.get()).defaultBlockState(), properties);
            }
        }
        return Blocks.AIR.defaultBlockState();
    }

    /** Sets whichever of the properties the state has, by name. */
    private static BlockState carry(BlockState state, Map<String, String> properties) {
        for (Property<?> property : state.getProperties()) {
            String value = properties.get(property.getName());
            if (value != null) {
                state = set(state, property, value);
            }
        }
        return state;
    }

    private static <T extends Comparable<T>> BlockState set(BlockState state, Property<T> property, String value) {
        return property.getValue(value).map(v -> state.setValue(property, v)).orElse(state);
    }

    private Fallbacks() {
    }
}
