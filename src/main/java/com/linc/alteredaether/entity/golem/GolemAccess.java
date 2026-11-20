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
package com.linc.alteredaether.entity.golem;

import com.linc.alteredaether.config.AACommonConfig;
import com.linc.alteredaether.entity.golem.route.Router;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * How golems get at storage: an item can only leave or enter through a face with open space beside it, so a chest
 * walled in on every side can't be used (it is <b>encased</b>). There is no line-of-sight rule: a golem reaches
 * storage behind a wall as long as the item has a way round. An open door or trapdoor is a way through.
 */
public final class GolemAccess {
    public enum Access {
        OK,
        /** No golem of the network sits close enough. */
        OUT_OF_RANGE,
        /** Walled in on every side. */
        ENCASED
    }

    /**
     * An open face of a storage block: the open cell beside it, and the face's centre, where an item leaves or
     * enters.
     */
    public record Face(BlockPos cell, Vec3 centre, Direction side) {
    }

    private GolemAccess() {
    }

    /** Whether an item can pass through the block at {@code pos}: nothing there to collide with, or an open door. */
    public static boolean open(Level level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        if ((state.getBlock() instanceof DoorBlock || state.getBlock() instanceof TrapDoorBlock)
                && state.hasProperty(BlockStateProperties.OPEN)) {
            // a door keeps a thin collision box when open, but an item slips past it as a player does
            return state.getValue(BlockStateProperties.OPEN) || state.getCollisionShape(level, pos).isEmpty();
        }
        return state.getCollisionShape(level, pos).isEmpty();
    }

    /** The open faces of the storage at {@code pos}, both halves of a double chest; none means it is encased. */
    public static List<Face> openFaces(Level level, BlockPos pos) {
        List<Face> faces = new ArrayList<>();
        BlockPos partner = GolemStorage.partner(level, pos);
        addFaces(level, pos, partner, faces);
        if (partner != null) {
            addFaces(level, partner, pos, faces);
        }
        return faces;
    }

    private static void addFaces(Level level, BlockPos pos, @Nullable BlockPos partner, List<Face> faces) {
        for (Direction side : Direction.values()) {
            BlockPos beside = pos.relative(side);
            if (!beside.equals(partner) && open(level, beside)) {
                faces.add(new Face(beside.immutable(), Vec3.atCenterOf(pos).relative(side, 0.5), side));
            }
        }
    }

    public static boolean encased(Level level, BlockPos pos) {
        BlockPos partner = GolemStorage.partner(level, pos);
        return !anyOpenFace(level, pos, partner) && (partner == null || !anyOpenFace(level, partner, pos));
    }

    private static boolean anyOpenFace(Level level, BlockPos pos, @Nullable BlockPos partner) {
        BlockPos.MutableBlockPos beside = new BlockPos.MutableBlockPos();
        for (Direction side : Direction.values()) {
            beside.setWithOffset(pos, side);
            if (!beside.equals(partner) && open(level, beside)) {
                return true;
            }
        }
        return false;
    }

    /** The cells beside the open faces of the storage at {@code pos}, where an item leaves or enters it. */
    public static List<Router.Cell> faceCells(Level level, BlockPos pos) {
        List<Router.Cell> cells = new ArrayList<>();
        for (Face face : openFaces(level, pos)) {
            cells.add(cell(face.cell()));
        }
        return cells;
    }

    /** Whether a golem sitting at {@code home} can use the storage at {@code pos}. */
    public static Access access(Level level, BlockPos home, BlockPos pos) {
        if (!GolemStorage.inReach(home, pos, AACommonConfig.GOLEM_RANGE.get())) {
            return Access.OUT_OF_RANGE;
        }
        return encased(level, pos) ? Access.ENCASED : Access.OK;
    }

    /**
     * The world as the router sees it: open cells and closed ones. Each cell is looked at once and remembered, so a grid
     * is for one tick's routing only.
     */
    public static Router.CellGrid grid(Level level) {
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        Long2ByteOpenHashMap seen = new Long2ByteOpenHashMap();
        seen.defaultReturnValue((byte) -1);
        return (x, y, z) -> {
            long key = BlockPos.asLong(x, y, z);
            byte known = seen.get(key);
            if (known < 0) {
                known = open(level, at.set(x, y, z)) ? (byte) 1 : (byte) 0;
                seen.put(key, known);
            }
            return known == 1;
        };
    }

    public static boolean open(Router.CellGrid grid, BlockPos pos) {
        return grid.isOpen(pos.getX(), pos.getY(), pos.getZ());
    }

    public static Router.Cell cell(BlockPos pos) {
        return new Router.Cell(pos.getX(), pos.getY(), pos.getZ());
    }

    public static BlockPos pos(Router.Cell cell) {
        return new BlockPos(cell.x(), cell.y(), cell.z());
    }

    /** The router's box around golems sitting at {@code homes}: their reach, and a little more. */
    public static Router.Bounds bounds(List<BlockPos> homes) {
        int range = AACommonConfig.GOLEM_RANGE.get();
        int up = range / 2 + 3;
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockPos home : homes) {
            minX = Math.min(minX, home.getX() - range - 2);
            maxX = Math.max(maxX, home.getX() + range + 2);
            minY = Math.min(minY, home.getY() - up);
            maxY = Math.max(maxY, home.getY() + up);
            minZ = Math.min(minZ, home.getZ() - range - 2);
            maxZ = Math.max(maxZ, home.getZ() + range + 2);
        }
        return new Router.Bounds(minX, minY, minZ, maxX, maxY, maxZ);
    }
}
