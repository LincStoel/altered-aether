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

import com.linc.alteredaether.worldgen.camp.Blueprint;
import com.linc.alteredaether.worldgen.camp.Blueprints;
import com.linc.alteredaether.worldgen.grove.shape.Carve;
import com.linc.alteredaether.worldgen.grove.shape.Part;
import com.linc.alteredaether.worldgen.grove.shape.Shell;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A burrow: a furnished room (a hand-built structure, {@code aether_tree_burrow_N}) set into the side of a giant's
 * trunk, in at a doorway on the ground. It is drawn into the giant's plan as a hand-shaped {@link Carve} and the props
 * that stand in it, so it builds chunk by chunk like the rest of the giant.
 *
 * <p>A burrow is only cut where at least half of the room is already in the trunk, the doorway looks out on open
 * ground and nothing else of the giant (a hollow, a tunnel) comes near it. A rind of bark ({@link Shell}) grown round
 * the room walls it in wherever the trunk is too thin to.
 */
final class Burrow {
    static final String GROUP = "aether_tree_burrow";
    private static final int ATTEMPTS = 16;
    private static final double WALL = 0.3;
    /** The share of a room that must lie in wood already, and the thickness of the bark grown round it. */
    private static final double EMBEDDED = 0.5, RIND = 1.9;

    /** Cuts a burrow into the giant, if it can find a place, drawing on {@code random}. */
    static void sketch(Sketch s, RandomSource random) {
        List<Blueprint> variants = Blueprints.group(GROUP);
        if (variants.isEmpty()) {
            return;
        }
        Blueprint blueprint = variants.get(random.nextInt(variants.size()));
        // The doorway is the cell in the middle of the structure's west face; the room lies east of it.
        BlockPos anchor = new BlockPos(0, 0, blueprint.size().getZ() / 2);
        if (blueprint.cellAt(anchor) == null) {
            return;
        }
        double radius = Math.max(s.footRadius, 5);
        List<Direction> directions = new ArrayList<>(Direction.Plane.HORIZONTAL.stream().toList());
        Collections.shuffle(directions, new java.util.Random(random.nextLong()));
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            Direction out = directions.get(attempt % 4);
            int reach = (int) Math.round(radius * 0.55);
            int lateral = reach == 0 ? 0 : random.nextInt(2 * reach + 1) - reach;
            Mirror mirror = random.nextBoolean() ? Mirror.NONE : Mirror.LEFT_RIGHT;
            Rotation rotation = switch (out) {
                case NORTH -> Rotation.CLOCKWISE_90;
                case EAST -> Rotation.CLOCKWISE_180;
                case SOUTH -> Rotation.COUNTERCLOCKWISE_90;
                default -> Rotation.NONE;
            };
            Direction across = out.getClockWise();
            // Walk in from beyond the trunk to the first wood a doorway could be cut in.
            for (int r = (int) Math.ceil(radius * 2.5) + 4; r >= 1; r--) {
                int dx = s.x + out.getStepX() * r + across.getStepX() * lateral;
                int dz = s.z + out.getStepZ() * r + across.getStepZ() * lateral;
                int ox = dx + out.getStepX(), oz = dz + out.getStepZ();
                int ground = s.groundAt(ox, oz);
                if (ground == Integer.MIN_VALUE) {
                    continue;
                }
                int y = ground + 1;
                if (wood(s, dx, y, dz) > WALL && wood(s, dx, y + 1, dz) > WALL
                        && wood(s, ox, y, oz) <= 0 && wood(s, ox, y + 1, oz) <= 0) {
                    if (cut(s, blueprint, anchor, new BlockPos(dx, y, dz), out, mirror, rotation)) {
                        return;
                    }
                    break;
                }
            }
        }
    }

    /** Cuts the room with its doorway cell at {@code door} if it fits there, and furnishes it. */
    private static boolean cut(Sketch s, Blueprint blueprint, BlockPos anchor, BlockPos door, Direction out,
                               Mirror mirror, Rotation rotation) {
        Set<BlockPos> room = new HashSet<>();
        Set<BlockPos> doorway = new HashSet<>();
        List<BlockPos> world = new ArrayList<>();
        for (Blueprint.Cell cell : blueprint.cells()) {
            BlockPos at = door.offset(Blueprint.turn(cell.pos(), mirror, rotation, anchor).subtract(anchor));
            world.add(at);
            room.add(at);
            if (cell.pos().getX() == 0) {
                doorway.add(at);
            }
        }
        BlockPos outside = door.relative(out);
        int embedded = 0;
        for (BlockPos at : room) {
            if (carved(s, at)) {
                return false;
            }
            if (!doorway.contains(at) && wood(s, at.getX(), at.getY(), at.getZ()) > WALL) {
                embedded++;
            }
            for (Direction side : Direction.values()) {
                BlockPos next = at.relative(side);
                if (!room.contains(next) && !next.equals(outside) && !next.equals(outside.above())
                        && carved(s, next)) {
                    return false;
                }
            }
        }
        // Set into the trunk, not stuck on it: at least half the room is already in wood. The rind of bark grown
        // round the rest closes it in where the trunk is thin.
        if (embedded < (room.size() - doorway.size()) * EMBEDDED) {
            return false;
        }

        s.part(new Shell(room, door, out, RIND));
        s.carve(Carve.cells(room, door.getY()));
        int i = 0;
        for (Blueprint.Cell cell : blueprint.cells()) {
            BlockPos at = world.get(i++);
            if (cell.air()) {
                continue;
            }
            BlockState state = Blueprint.turn(cell.state(), mirror, rotation);
            boolean chest = state.getBlock() instanceof ChestBlock && state.getValue(ChestBlock.TYPE) != ChestType.LEFT;
            s.prop(new Prop(at.getX(), at.getY(), at.getZ(), Prop.Anchor.AT, List.of(state),
                    chest ? Furnish.CAMP : null));
        }
        for (Blueprint.Figure figure : blueprint.figures()) {
            BlockPos block = BlockPos.containing(figure.pos());
            BlockPos at = door.offset(Blueprint.turn(block, mirror, rotation, anchor).subtract(anchor));
            CompoundTag nbt = figure.nbt().copy();
            ListTag facing = new ListTag();
            facing.add(FloatTag.valueOf(Mth.wrapDegrees(Blueprint.turn(figure.yaw(), mirror, rotation))));
            facing.add(FloatTag.valueOf(0));
            nbt.put("Rotation", facing);
            s.prop(new Prop(at.getX(), at.getY(), at.getZ(), Prop.Anchor.AT, List.of(), null, nbt));
        }
        s.point("burrow", door.getX(), door.getY(), door.getZ());
        return true;
    }

    /** How deep a block lies in the giant's wood: the greatest margin of any piece; negative outside it. */
    private static double wood(Sketch s, int x, int y, int z) {
        double best = -Double.MAX_VALUE;
        for (Part part : s.wood) {
            if (part.bounds().column(x, z) && part.bounds().height(y)) {
                best = Math.max(best, part.margin(x, y, z));
            }
        }
        return best;
    }

    /** Whether a block is in, or within a block and a half of, a space already cut from the giant. */
    private static boolean carved(Sketch s, BlockPos at) {
        for (Carve carve : s.carves) {
            if (carve.bounds().column(at.getX(), at.getZ()) && carve.bounds().height(at.getY())
                    && carve.margin(at.getX(), at.getY(), at.getZ()) > -1.5) {
                return true;
            }
        }
        return false;
    }

    private Burrow() {
    }
}
