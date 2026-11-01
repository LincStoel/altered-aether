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

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A hand-built structure (a vanilla structure-block {@code .nbt}) read into plain data, with its blocks already
 * resolved through {@link Fallbacks}. It holds only the cells the builder saved: where one left structure voids, there
 * is no cell, and nothing is placed or cleared there. Air is a cell like any other, so a room carved into a tree
 * is the air it holds.
 */
public final class Blueprint {
    /** One block of the structure. */
    public record Cell(BlockPos pos, BlockState state) {
        public boolean air() {
            return state.isAir();
        }
    }

    /**
     * A creature the builder left standing in it.
     *
     * @param pos the position inside the structure
     * @param yaw its facing, in degrees
     * @param nbt just what it wears and holds; nothing else of what the builder's world and mods saved with it
     */
    public record Figure(Vec3 pos, float yaw, CompoundTag nbt) {
    }

    private final String name;
    private final BlockPos size;
    private final List<Cell> cells;
    private final List<Figure> figures;

    private Blueprint(String name, BlockPos size, List<Cell> cells, List<Figure> figures) {
        this.name = name;
        this.size = size;
        this.cells = List.copyOf(cells);
        this.figures = List.copyOf(figures);
    }

    public String name() {
        return name;
    }

    public BlockPos size() {
        return size;
    }

    public List<Cell> cells() {
        return cells;
    }

    public List<Figure> figures() {
        return figures;
    }

    /** Reads a structure file's tag. */
    public static Blueprint read(String name, CompoundTag tag) {
        ListTag sizeTag = tag.getList("size", Tag.TAG_INT);
        BlockPos size = new BlockPos(sizeTag.getInt(0), sizeTag.getInt(1), sizeTag.getInt(2));
        List<BlockState> palette = new ArrayList<>();
        for (Tag entry : tag.getList("palette", Tag.TAG_COMPOUND)) {
            CompoundTag block = (CompoundTag) entry;
            Map<String, String> properties = new HashMap<>();
            CompoundTag props = block.getCompound("Properties");
            for (String key : props.getAllKeys()) {
                properties.put(key, props.getString(key));
            }
            palette.add(Fallbacks.resolve(block.getString("Name"), properties));
        }
        List<Cell> cells = new ArrayList<>();
        for (Tag entry : tag.getList("blocks", Tag.TAG_COMPOUND)) {
            CompoundTag block = (CompoundTag) entry;
            ListTag pos = block.getList("pos", Tag.TAG_INT);
            cells.add(new Cell(new BlockPos(pos.getInt(0), pos.getInt(1), pos.getInt(2)),
                    palette.get(block.getInt("state"))));
        }
        List<Figure> figures = new ArrayList<>();
        for (Tag entry : tag.getList("entities", Tag.TAG_COMPOUND)) {
            CompoundTag saved = (CompoundTag) entry;
            ListTag at = saved.getList("pos", Tag.TAG_DOUBLE);
            CompoundTag nbt = saved.getCompound("nbt");
            Figure figure = figure(new Vec3(at.getDouble(0), at.getDouble(1), at.getDouble(2)), nbt);
            if (figure != null) {
                figures.add(figure);
            }
        }
        return new Blueprint(name, size, cells, figures);
    }

    /**
     * What an armour stand (the only creature a structure uses) keeps: its pose-less self, what it wears and holds.
     * The builder's world saved a great deal else with it, from other mods' attachments to its own coordinates.
     */
    @Nullable
    private static Figure figure(Vec3 pos, CompoundTag saved) {
        if (!"minecraft:armor_stand".equals(saved.getString("id"))) {
            return null;
        }
        CompoundTag nbt = new CompoundTag();
        nbt.putString("id", "minecraft:armor_stand");
        for (String key : new String[]{"ArmorItems", "HandItems"}) {
            ListTag items = new ListTag();
            for (Tag item : saved.getList(key, Tag.TAG_COMPOUND)) {
                CompoundTag slot = new CompoundTag();
                CompoundTag from = (CompoundTag) item;
                if (from.contains("id")) {
                    slot.putString("id", from.getString("id"));
                    slot.putInt("count", Math.max(1, from.getInt("count")));
                }
                items.add(slot);
            }
            nbt.put(key, items);
        }
        for (String flag : new String[]{"ShowArms", "NoBasePlate", "Small", "Invisible"}) {
            if (saved.contains(flag)) {
                nbt.putBoolean(flag, saved.getInt(flag) != 0 || saved.getBoolean(flag));
            }
        }
        ListTag rotation = saved.getList("Rotation", Tag.TAG_FLOAT);
        return new Figure(pos, rotation.isEmpty() ? 0 : rotation.getFloat(0), nbt);
    }

    // ----------------------------------------------------------------------------------------------- turning --

    /** Where a position of the structure lands when the structure is mirrored and turned about a pivot. */
    public static BlockPos turn(BlockPos pos, Mirror mirror, Rotation rotation, BlockPos pivot) {
        return StructureTemplate.transform(pos, mirror, rotation, pivot);
    }

    /** A block state as the structure's mirroring and turning leave it. */
    public static BlockState turn(BlockState state, Mirror mirror, Rotation rotation) {
        return state.mirror(mirror).rotate(rotation);
    }

    /** A facing as the structure's mirroring and turning leave it (the way a placed entity's is). */
    public static float turn(float yaw, Mirror mirror, Rotation rotation) {
        float mirrored = switch (mirror) {
            case LEFT_RIGHT -> 180 - yaw;
            case FRONT_BACK -> -yaw;
            default -> yaw;
        };
        return mirrored + switch (rotation) {
            case CLOCKWISE_90 -> 90;
            case CLOCKWISE_180 -> 180;
            case COUNTERCLOCKWISE_90 -> 270;
            default -> 0;
        };
    }

    /** A position inside the structure, turned about a pivot block's centre. */
    public static Vec3 turn(Vec3 pos, Mirror mirror, Rotation rotation, BlockPos pivot) {
        return StructureTemplate.transform(pos, mirror, rotation, pivot);
    }

    /** The cell at a position, or null where the builder left a void. */
    @Nullable
    public Cell cellAt(BlockPos pos) {
        for (Cell cell : cells) {
            if (cell.pos().equals(pos)) {
                return cell;
            }
        }
        return null;
    }
}
