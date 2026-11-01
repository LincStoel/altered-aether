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
package com.linc.alteredaether.worldgen.grove.shape;

import it.unimi.dsi.fastutil.longs.Long2FloatOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.Set;

/**
 * A rind of bark grown round a hand-shaped room, a block or two thick, so a room cut into the side of a trunk is
 * walled and floored in wood wherever the trunk itself is too thin to hold it. It stops at the plane of the room's
 * doorway, so the way in stays open.
 */
public final class Shell implements Part {
    private final Long2FloatOpenHashMap depth = new Long2FloatOpenHashMap();
    private final Bounds bounds;
    private final BlockPos door;
    private final Direction out;

    /**
     * @param room      the blocks of the room
     * @param door      a block of the doorway
     * @param out       the way out of the doorway
     * @param thickness how far from the room's blocks the bark reaches, in blocks
     */
    public Shell(Set<BlockPos> room, BlockPos door, Direction out, double thickness) {
        this.door = door;
        this.out = out;
        depth.defaultReturnValue(-1);
        int reach = (int) Math.ceil(thickness);
        Bounds b = Bounds.EMPTY;
        for (BlockPos cell : room) {
            for (int dx = -reach; dx <= reach; dx++) {
                for (int dy = -reach; dy <= reach; dy++) {
                    for (int dz = -reach; dz <= reach; dz++) {
                        float margin = (float) (thickness - Math.sqrt(dx * dx + dy * dy + dz * dz));
                        long key = BlockPos.asLong(cell.getX() + dx, cell.getY() + dy, cell.getZ() + dz);
                        if (margin > 0 && margin > depth.get(key)
                                && outward(cell.getX() + dx, cell.getZ() + dz) <= 0) {
                            depth.put(key, margin);
                        }
                    }
                }
            }
            b = b.union(new Bounds(cell.getX() - reach, cell.getY() - reach, cell.getZ() - reach,
                    cell.getX() + reach, cell.getY() + reach, cell.getZ() + reach));
        }
        this.bounds = b;
    }

    /** How far past the doorway's plane a column lies, in blocks; positive outside. */
    private int outward(int x, int z) {
        return (x - door.getX()) * out.getStepX() + (z - door.getZ()) * out.getStepZ();
    }

    @Override
    public Bounds bounds() {
        return bounds;
    }

    @Override
    public double margin(double x, double y, double z) {
        return depth.get(BlockPos.asLong((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z)));
    }

    @Override
    public Role role() {
        return Role.BARK;
    }
}
