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
package com.linc.alteredaether.compat;

import com.simibubi.create.content.logistics.filter.FilterItem;
import com.simibubi.create.content.logistics.filter.FilterItemStack;
import com.simibubi.create.content.logistics.funnel.AbstractFunnelBlock;
import com.simibubi.create.content.logistics.funnel.BrassFunnelBlock;
import com.simibubi.create.content.logistics.funnel.FunnelBlock;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Create's brass funnels as golem sinks: a brass funnel set to put items into the block behind it (a machine, say) is
 * fed by the golem with what its filter lets through, and nothing else; a funnel with no filter is fed nothing.
 * Create's filters, wherever they are (in a funnel, an item frame, on a placard), are read by Create's own rules: an
 * item on its own lets just that item through, a list filter its list (or all but it), an attribute filter whatever
 * has the attributes. Only called with Create loaded.
 */
public final class CreateFunnels {
    private CreateFunnels() {
    }

    /** Whether {@code state} is a brass funnel putting items into the block behind it. */
    public static boolean isSink(BlockState state) {
        return state.getBlock() instanceof BrassFunnelBlock && state.hasProperty(FunnelBlock.EXTRACTING)
                && !state.getValue(FunnelBlock.EXTRACTING);
    }

    /** Whether a redstone signal holds the funnel shut. */
    public static boolean powered(BlockState state) {
        return state.hasProperty(AbstractFunnelBlock.POWERED) && state.getValue(AbstractFunnelBlock.POWERED);
    }

    /** The block the funnel at {@code pos} puts items into. */
    public static BlockPos target(BlockState state, BlockPos pos) {
        return pos.relative(AbstractFunnelBlock.getFunnelFacing(state).getOpposite());
    }

    /** The funnel's filter, or empty. */
    public static ItemStack filter(Level level, BlockPos pos) {
        FilteringBehaviour filtering = BlockEntityBehaviour.get(level, pos, FilteringBehaviour.TYPE);
        return filtering == null ? ItemStack.EMPTY : filtering.getFilter();
    }

    /** Whether {@code stack} is one of Create's filter items, which pins what it lets through rather than itself. */
    public static boolean isFilter(ItemStack stack) {
        return stack.getItem() instanceof FilterItem;
    }

    /** Whether {@code filter} lets {@code stack} through; an empty filter lets nothing through. */
    public static boolean accepts(Level level, ItemStack filter, ItemStack stack) {
        return !filter.isEmpty() && FilterItemStack.of(filter).test(level, stack);
    }

    /** The items {@code filter} names outright: itself if it is a plain item, a list filter's list if it isn't a blacklist. */
    public static List<ItemStack> listed(ItemStack filter) {
        List<ItemStack> out = new ArrayList<>();
        if (filter.isEmpty()) {
            return out;
        }
        if (!isFilter(filter)) {
            out.add(filter);
            return out;
        }
        addListed(FilterItemStack.of(filter), out);
        return out;
    }

    private static void addListed(FilterItemStack stack, List<ItemStack> out) {
        if (stack instanceof FilterItemStack.ListFilterItemStack list) {
            if (!list.isBlacklist) {
                for (FilterItemStack inner : list.containedItems) {
                    addListed(inner, out);
                }
            }
        } else if (!stack.item().isEmpty() && !isFilter(stack.item())) {
            out.add(stack.item());
        }
    }

    /**
     * The funnel at {@code pos} as the golem fills it: only what its filter lets through goes in, into the block behind
     * it through the face the funnel is on, as the funnel itself would put it; nothing ever comes out. Its slots show
     * what that block holds, so the golem notices when the machine uses it up. Null if the block behind takes nothing.
     */
    @Nullable
    public static IItemHandler sink(Level level, BlockPos pos, BlockState state) {
        Direction face = AbstractFunnelBlock.getFunnelFacing(state);
        if (face == null) {
            return null;
        }
        IItemHandler target = level.getCapability(Capabilities.ItemHandler.BLOCK, pos.relative(face.getOpposite()), face);
        if (target == null) {
            return null;
        }
        return new Sink(level, pos, target);
    }

    private record Sink(Level level, BlockPos pos, IItemHandler target) implements IItemHandler {
        @Override
        public int getSlots() {
            return target.getSlots();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return slot < target.getSlots() ? target.getStackInSlot(slot) : ItemStack.EMPTY;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (stack.isEmpty() || powered(level.getBlockState(pos)) || !accepts(level, filter(level, pos), stack)) {
                return stack;
            }
            return ItemHandlerHelper.insertItem(target, stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return slot < target.getSlots() ? target.getSlotLimit(slot) : 64;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return accepts(level, filter(level, pos), stack);
        }
    }
}
