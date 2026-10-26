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
package com.linc.alteredaether.worldgen.grove;

import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The wood a colony is made of. Every state is placed the way the Aether places its own trees: with
 * {@code double_drops} set where the block has it, so naturally grown wood keeps its doubled harvest.
 */
public record GrovePalette(String name, Block log, Block wood, Block strippedLog, Block strippedWood, Block leaves,
                           Block heart) {
    public static final GrovePalette SKYROOT = of("skyroot", "aether:skyroot_log", "aether:skyroot_wood",
            "aether:stripped_skyroot_log", "aether:stripped_skyroot_wood", "aether:skyroot_leaves");

    /** Deep Aether's woods for each grove biome; empty without Deep Aether. */
    static final List<GrovePalette> DEEP_CATHEDRAL = present(deep("roseroot"), deep("sunroot"));
    static final List<GrovePalette> DEEP_NURSELOG = present(deep("cruderoot"), deep("yagroot"), deep("conberry"),
            deep("roseroot"));

    @Nullable
    private static GrovePalette deep(String wood) {
        return of(wood, "deep_aether:" + wood + "_log", "deep_aether:" + wood + "_wood",
                "deep_aether:stripped_" + wood + "_log", "deep_aether:stripped_" + wood + "_wood",
                "deep_aether:" + wood + "_leaves");
    }

    private static List<GrovePalette> present(GrovePalette... palettes) {
        List<GrovePalette> list = new ArrayList<>();
        for (GrovePalette palette : palettes) {
            if (palette != null) {
                list.add(palette);
            }
        }
        return List.copyOf(list);
    }

    /** A palette from block ids; null if any of them is missing. */
    @Nullable
    public static GrovePalette of(String name, String log, String wood, String strippedLog, String strippedWood,
                                  String leaves) {
        Block l = block(log), w = block(wood), sl = block(strippedLog), sw = block(strippedWood), lv = block(leaves);
        Block heart = block("aether:golden_oak_log");
        if (l == null || w == null || sl == null || sw == null || lv == null) {
            return null;
        }
        return new GrovePalette(name, l, w, sl, sw, lv, heart == null ? l : heart);
    }

    @Nullable
    private static Block block(String id) {
        return BuiltInRegistries.BLOCK.getOptional(ResourceLocation.parse(id)).orElse(null);
    }

    public BlockState log(Direction.Axis axis) {
        return natural(log.defaultBlockState().trySetValue(RotatedPillarBlock.AXIS, axis));
    }

    public BlockState bark() {
        return natural(wood.defaultBlockState());
    }

    public BlockState stripped(@Nullable Direction.Axis axis) {
        return natural(axis == null ? strippedWood.defaultBlockState()
                : strippedLog.defaultBlockState().trySetValue(RotatedPillarBlock.AXIS, axis));
    }

    public BlockState heart(@Nullable Direction.Axis axis) {
        return natural(heart.defaultBlockState().trySetValue(RotatedPillarBlock.AXIS,
                axis == null ? Direction.Axis.Y : axis));
    }

    /** The earth a tree's roots tear up: Aether dirt. */
    public BlockState soil() {
        Block dirt = block("aether:aether_dirt");
        return natural((dirt == null ? log : dirt).defaultBlockState());
    }

    /** Leaves {@code distance} steps from the nearest log, which may decay once the wood is gone. */
    public BlockState leaves(int distance) {
        BlockState state = leaves.defaultBlockState();
        if (state.hasProperty(LeavesBlock.DISTANCE)) {
            state = state.setValue(LeavesBlock.DISTANCE, Math.max(1, Math.min(LeavesBlock.DECAY_DISTANCE - 1,
                    distance)));
        }
        if (state.hasProperty(LeavesBlock.PERSISTENT)) {
            state = state.setValue(LeavesBlock.PERSISTENT, false);
        }
        return natural(state);
    }

    private static BlockState natural(BlockState state) {
        for (Property<?> property : state.getProperties()) {
            if (property.getName().equals("double_drops") && property instanceof BooleanProperty flag) {
                return state.setValue(flag, true);
            }
        }
        return state;
    }
}
