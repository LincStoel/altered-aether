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

import com.linc.alteredaether.AlteredAether;
import com.linc.alteredaether.compat.CreateCompat;
import com.linc.alteredaether.compat.CreatePlacards;
import com.linc.alteredaether.config.AACommonConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.decoration.GlowItemFrame;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.CrafterBlockEntity;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.AABB;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Which blocks a golem can link and how it may use them, and the small facts about storage the network needs: the
 * other half of a double chest, whether a player has it open, what is pinned to it.
 */
public final class GolemStorage {
    /** Blocks the golem only empties, never fills: hoppers, depots. */
    public static final TagKey<Block> EMPTY_ONLY = TagKey.create(Registries.BLOCK, AlteredAether.id("golem_empty_only"));
    /** Blocks that can't be linked: machines and every inventory that isn't storage. */
    public static final TagKey<Block> BLACKLIST = TagKey.create(Registries.BLOCK, AlteredAether.id("golem_link_blacklist"));
    /** Storage that may be linked whatever else says it can't, for pack makers. */
    public static final TagKey<Block> LINKABLE = TagKey.create(Registries.BLOCK, AlteredAether.id("golem_linkable"));
    /** What a golem's head may be. */
    public static final TagKey<Block> HEADS = TagKey.create(Registries.BLOCK, AlteredAether.id("golem_heads"));

    public enum Role {
        /** Filled and emptied. */
        STORAGE,
        /** Only emptied. */
        SOURCE,
        /** Can't be linked. */
        NONE
    }

    private GolemStorage() {
    }

    @Nullable
    public static IItemHandler handler(Level level, BlockPos pos) {
        return level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null);
    }

    /** How a golem may use the block at {@code pos}; the chunk must be loaded. */
    public static Role role(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (handler(level, pos) == null) {
            return Role.NONE;
        }
        if (state.is(LINKABLE)) {
            return Role.STORAGE;
        }
        if (state.is(EMPTY_ONLY)) {
            return Role.SOURCE;
        }
        if (state.is(BLACKLIST) || isMachine(level.getBlockEntity(pos))) {
            return Role.NONE;
        }
        return Role.STORAGE;
    }

    /** Machines no tag names, caught by what their block entity is. Hoppers, being empty-only by tag, come first. */
    private static boolean isMachine(@Nullable BlockEntity be) {
        return be instanceof AbstractFurnaceBlockEntity || be instanceof BrewingStandBlockEntity
                || be instanceof HopperBlockEntity || be instanceof DispenserBlockEntity || be instanceof CrafterBlockEntity;
    }

    /** The other half of a double chest at {@code pos}, or null. */
    @Nullable
    public static BlockPos partner(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof ChestBlock && state.hasProperty(ChestBlock.TYPE)
                && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
            BlockPos other = pos.relative(ChestBlock.getConnectedDirection(state));
            BlockState otherState = level.getBlockState(other);
            if (otherState.is(state.getBlock())) {
                return other;
            }
        }
        return null;
    }

    /** One position standing for the whole inventory at {@code pos}: a double chest is seen as one. */
    public static long inventoryKey(Level level, BlockPos pos) {
        BlockPos other = partner(level, pos);
        return other == null ? pos.asLong() : Math.min(pos.asLong(), other.asLong());
    }

    /**
     * Whether a vanilla-style container the golem may reorder in place: a chest (single or double), a barrel or a
     * shulker box, whose slots are all alike.
     */
    @Nullable
    public static Container plainContainer(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof ChestBlockEntity && state.getBlock() instanceof ChestBlock chest) {
            return ChestBlock.getContainer(chest, state, level, pos, true);
        }
        if (be instanceof BarrelBlockEntity || be instanceof ShulkerBoxBlockEntity) {
            return (Container) be;
        }
        return null;
    }

    /** Whether a player has the inventory at {@code pos} open, or the lid is up for some other reason. */
    public static boolean inUse(ServerLevel level, BlockPos pos) {
        if (openHere(level, pos)) {
            return true;
        }
        BlockPos other = partner(level, pos);
        return other != null && openHere(level, other);
    }

    private static boolean openHere(ServerLevel level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof ChestBlockEntity && ChestBlockEntity.getOpenCount(level, pos) > 0) {
            return true;
        }
        BlockState state = level.getBlockState(pos);
        if (state.hasProperty(BarrelBlock.OPEN) && state.getValue(BarrelBlock.OPEN)) {
            return true;
        }
        if (be instanceof ShulkerBoxBlockEntity shulker
                && shulker.getAnimationStatus() != ShulkerBoxBlockEntity.AnimationStatus.CLOSED) {
            return true;
        }
        if (!(be instanceof Container container)) {
            return false;
        }
        for (ServerPlayer player : level.players()) {
            if (player.containerMenu == player.inventoryMenu
                    || player.distanceToSqr(pos.getCenter()) > 12 * 12) {
                continue;
            }
            for (Slot slot : player.containerMenu.slots) {
                if (slot.container == container
                        || slot.container instanceof CompoundContainer compound && compound.contains(container)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * What is pinned to an inventory.
     *
     * @param items  the pinned items
     * @param strict true if any pin is a glow item frame or a placard: then the inventory takes only its pinned items,
     *               not their families, and the golem sends everything else away
     */
    public record Pins(List<ItemStack> items, boolean strict) {
        public boolean isEmpty() {
            return items.isEmpty();
        }
    }

    /**
     * What is pinned to the inventory at {@code pos} (and its other half): the items in item frames hanging on it,
     * and on Create's placards.
     */
    public static Pins pins(ServerLevel level, BlockPos pos) {
        List<ItemStack> pins = new ArrayList<>();
        boolean strict = addPins(level, pos, pins);
        BlockPos other = partner(level, pos);
        if (other != null) {
            strict |= addPins(level, other, pins);
        }
        return new Pins(pins, strict && !pins.isEmpty());
    }

    /** Those of {@code positions} that are storage pinned strictly, for their glow. */
    public static List<BlockPos> strict(ServerLevel level, Collection<BlockPos> positions) {
        List<BlockPos> strict = new ArrayList<>();
        for (BlockPos pos : positions) {
            if (level.isLoaded(pos) && pins(level, pos).strict()) {
                strict.add(pos);
            }
        }
        return strict;
    }

    /** Adds what is pinned to the block at {@code pos}; true if a pin there is strict. */
    private static boolean addPins(ServerLevel level, BlockPos pos, List<ItemStack> pins) {
        boolean strict = false;
        for (ItemFrame frame : level.getEntitiesOfClass(ItemFrame.class, new AABB(pos).inflate(0.75))) {
            BlockPos attached = frame.getPos().relative(frame.getDirection().getOpposite());
            if (attached.equals(pos) && !frame.getItem().isEmpty()) {
                pins.add(frame.getItem());
                strict |= frame instanceof GlowItemFrame;
            }
        }
        if (ModList.get().isLoaded(CreateCompat.MOD) && AACommonConfig.compat(CreateCompat.MOD)) {
            for (Direction side : Direction.values()) {
                ItemStack held = CreatePlacards.heldOn(level, pos, side);
                if (!held.isEmpty()) {
                    pins.add(held);
                    strict = true;
                }
            }
        }
        return strict;
    }

    /** Whether {@code pos} is within reach of a golem sitting at {@code home}: {@code range} out, half that up and down. */
    public static boolean inReach(BlockPos home, BlockPos pos, int range) {
        double dx = pos.getX() - home.getX(), dz = pos.getZ() - home.getZ();
        return dx * dx + dz * dz <= (double) range * range && Math.abs(pos.getY() - home.getY()) <= range / 2.0 + 1;
    }

    /** A hash of what the inventory holds, to notice changes. */
    public static int contentHash(IItemHandler handler) {
        int hash = handler.getSlots();
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            hash = hash * 31 + (stack.isEmpty() ? 0 : ItemStack.hashItemAndComponents(stack) * 31 + stack.getCount());
        }
        return hash;
    }
}
