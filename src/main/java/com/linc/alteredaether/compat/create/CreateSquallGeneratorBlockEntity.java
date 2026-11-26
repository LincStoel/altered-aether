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
package com.linc.alteredaether.compat.create;

import com.linc.alteredaether.config.AACommonConfig;
import com.linc.alteredaether.electric.ElectricRegistries;
import com.linc.alteredaether.electric.FlashBlasting;
import com.linc.alteredaether.electric.SquallGeneratorBlock;
import com.linc.alteredaether.electric.SquallGeneratorCore;
import com.linc.alteredaether.electric.SquallGeneratorHost;
import com.simibubi.create.content.kinetics.belt.behaviour.BeltProcessingBehaviour;
import com.simibubi.create.content.kinetics.belt.behaviour.BeltProcessingBehaviour.ProcessingResult;
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour;
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour.TransportedResult;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A Squall Generator with Create there: the same {@link SquallGeneratorCore}, and while it hangs face down two blocks
 * over a belt or depot (where Create looks for a processor, as for a press), it holds each stack it can flash blast
 * under it for a shock's interval, zaps it, and lets it go on; each shock it also zaps what already sits there.
 */
public class CreateSquallGeneratorBlockEntity extends SmartBlockEntity implements SquallGeneratorHost {
    private final SquallGeneratorCore core = new SquallGeneratorCore(this);
    /** Ticks left on the stack being held. */
    private int holding;

    public CreateSquallGeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(ElectricRegistries.SQUALL_GENERATOR_BLOCK_ENTITY.get(), pos, state);
    }

    @Override
    public SquallGeneratorCore core() {
        return core;
    }

    @Override
    public void hostTick() {
        tick();
    }

    @Override
    public void tick() {
        super.tick();
        if (level instanceof ServerLevel server) {
            core.tick(server, worldPosition, getBlockState());
            if (server.getGameTime() % AACommonConfig.GENERATOR_ZAP_INTERVAL.get() == 0) {
                sweep(server);
            }
        }
    }

    /**
     * Flash blasts whatever sits centred on the belt or depot below that it can. A depot asks its processor only as an
     * item arrives, so one put there before the generator was lit would otherwise wait forever.
     */
    private void sweep(ServerLevel server) {
        if (!working()) {
            return;
        }
        TransportedItemStackHandlerBehaviour below = BlockEntityBehaviour.get(server, worldPosition.below(2),
                TransportedItemStackHandlerBehaviour.TYPE);
        if (below == null) {
            return;
        }
        below.handleCenteredProcessingOnAllItems(0.5F, item -> {
            if (item.locked) {
                return TransportedResult.doNothing();
            }
            List<TransportedItemStack> made = convert(server, item, below);
            return made == null ? TransportedResult.doNothing()
                    : TransportedResult.convertTo(made);
        });
    }

    /** {@code item} flash blasted, as stacks for the belt; null if nothing comes of it. */
    @Nullable
    private List<TransportedItemStack> convert(ServerLevel server, TransportedItemStack item,
                                               TransportedItemStackHandlerBehaviour handler) {
        List<ItemStack> out = core.flash(server, item.stack, handler.getWorldPositionOf(item));
        if (out == null) {
            return null;
        }
        List<TransportedItemStack> made = new ArrayList<>();
        for (ItemStack stack : out) {
            TransportedItemStack copy = item.copy();
            copy.stack = stack;
            made.add(copy);
        }
        return made;
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        behaviours.add(new BeltProcessingBehaviour(this).whenItemEnters(this::onItemEnter).whileItemHeld(this::whileHeld));
    }

    /** Face down and running: the only way it works a belt. */
    private boolean working() {
        BlockState state = getBlockState();
        return state.hasProperty(SquallGeneratorBlock.FACING) && state.getValue(SquallGeneratorBlock.FACING) == Direction.DOWN
                && state.getValue(SquallGeneratorBlock.LIT);
    }

    private ProcessingResult onItemEnter(TransportedItemStack item, TransportedItemStackHandlerBehaviour handler) {
        if (!working() || !FlashBlasting.canProcess(level, item.stack)) {
            return ProcessingResult.PASS;
        }
        holding = AACommonConfig.GENERATOR_ZAP_INTERVAL.get();
        return ProcessingResult.HOLD;
    }

    private ProcessingResult whileHeld(TransportedItemStack item, TransportedItemStackHandlerBehaviour handler) {
        if (!working() || !FlashBlasting.canProcess(level, item.stack)) {
            return ProcessingResult.PASS;
        }
        if (!(level instanceof ServerLevel server)) {
            return ProcessingResult.HOLD;
        }
        if (--holding > 0) {
            return ProcessingResult.HOLD;
        }
        List<TransportedItemStack> made = convert(server, item, handler);
        if (made == null) {
            return ProcessingResult.PASS;
        }
        handler.handleProcessingOnItem(item, TransportedResult.convertTo(made));
        holding = AACommonConfig.GENERATOR_ZAP_INTERVAL.get();
        return ProcessingResult.PASS;
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        if (!clientPacket) {
            core.save(tag, registries);
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        if (!clientPacket) {
            core.load(tag, registries);
        }
    }
}
