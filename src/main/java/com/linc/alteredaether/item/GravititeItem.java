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
package com.linc.alteredaether.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;

/**
 * Gravitite, in any form, pulls away from the ground. Dropped, it drifts up to hover a little under two blocks above
 * the ground below it, within a standing player's reach, and stays there. With no ground in sight (dropped over the
 * void), it rises a block from where it fell and hovers. Over anything that takes items from above (a hopper, chute,
 * belt or depot) it falls as any item does, so collection keeps working.
 *
 * <p>Run on the server only: the client follows the synced no-gravity flag and motion.
 */
public class GravititeItem extends Item {
    /** How high above the ground it settles: under a player's pick-up reach (1.8 + 0.5). */
    private static final double HOVER = 1.75;
    /** How far down it looks for ground. */
    private static final int SEARCH = 8;
    /** Over the void, how far it rises from where it was dropped. */
    private static final double VOID_RISE = 1.0;
    private static final String TOP = "altered_aether:hover";
    private static final String START = "altered_aether:hover_start";
    /** Ground is looked for again this often, in ticks. */
    private static final int RESCAN = 10;

    public GravititeItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean onEntityItemUpdate(ItemStack stack, ItemEntity entity) {
        Level level = entity.level();
        if (level.isClientSide) {
            return false;
        }
        CompoundTag data = entity.getPersistentData();
        if (!data.contains(START)) {
            data.putDouble(START, entity.getY());
        }
        if (!data.contains(TOP) || entity.tickCount % RESCAN == 0) {
            double top = hoverHeight(level, entity, data.getDouble(START));
            if (Double.isNaN(top)) {
                entity.setNoGravity(false);
                data.remove(TOP);
                return false;
            }
            data.putDouble(TOP, top);
        }
        entity.setNoGravity(true);
        double offset = data.getDouble(TOP) - entity.getY();
        Vec3 motion = entity.getDeltaMovement();
        // eases toward the hover height and settles there, slowing sideways drift as it goes
        double vy = motion.y * 0.8 + Mth.clamp(offset * 0.04, -0.02, 0.03);
        entity.setDeltaMovement(motion.x * 0.9, vy, motion.z * 0.9);
        return false;
    }

    /** Where the item should hover, or NaN when it should fall into a collector below it. */
    private static double hoverHeight(Level level, ItemEntity entity, double start) {
        BlockPos.MutableBlockPos pos = entity.blockPosition().mutable();
        for (int i = 0; i <= SEARCH; i++) {
            BlockState state = level.getBlockState(pos);
            if (collects(level, pos, state)) {
                return Double.NaN;
            }
            if (!state.getCollisionShape(level, pos).isEmpty()) {
                return pos.getY() + state.getCollisionShape(level, pos).max(Direction.Axis.Y) + HOVER;
            }
            pos.move(Direction.DOWN);
        }
        return start + VOID_RISE;
    }

    /** Whether the block takes items in from above. */
    private static boolean collects(Level level, BlockPos pos, BlockState state) {
        return state.hasBlockEntity() && level.getCapability(Capabilities.ItemHandler.BLOCK, pos, state,
                level.getBlockEntity(pos), Direction.UP) != null;
    }
}
