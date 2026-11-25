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
package com.linc.alteredaether.electric;

import com.linc.alteredaether.AATags;
import com.linc.alteredaether.config.AACommonConfig;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;

/**
 * An Aether Jar's charge. Lightning fills it a quarter at a time ({@link #strike}), a Squall Generator behind it tops
 * it up ({@link #charge}), and cables reach it through its bottom face alone ({@link #port}), which gives out at most
 * {@code jar_output} FE a tick and takes in at most {@code jar_input}. It also pushes its output into whatever below
 * takes energy.
 *
 * <p>Stood on copper, it electrifies the copper instead: every block of {@code #altered_aether:charged_copper} joined to
 * the one under it, up to {@code copper_max_blocks}, shocks whatever stands on it, and each one drains the jar a
 * little ({@code copper_drain} FE a tick). The live set is found again every couple of seconds, so copper laid or
 * broken joins or leaves it without the jar being told.
 */
public class AetherJarBlockEntity extends BlockEntity {
    /** Ticks between searches of the copper under the jar. */
    private static final int COPPER_RESCAN = 40;
    /** Ticks between shocks from live copper. */
    private static final int COPPER_ZAP = 10;

    private int energy;
    /** FE the cables have taken out and put in this tick, as of {@link #budgetTick}. */
    private int outThisTick, inThisTick;
    private long budgetTick = Long.MIN_VALUE;
    /** Copper's drain owed below one FE, carried between ticks. */
    private double copperOwed;
    private final LongOpenHashSet copper = new LongOpenHashSet();
    private long[] copperList = new long[0];
    @Nullable
    private AABB copperBounds;
    private int lastCharge = -1;
    @Nullable
    private BlockCapabilityCache<IEnergyStorage, Direction> below;
    private final Port port = new Port();

    public AetherJarBlockEntity(BlockPos pos, BlockState state) {
        super(ElectricRegistries.AETHER_JAR_BLOCK_ENTITY.get(), pos, state);
    }

    public static int capacity() {
        return AACommonConfig.JAR_CAPACITY.get();
    }

    public int energy() {
        return energy;
    }

    /** The jar's charge seen from its bottom face, for cables and machines. */
    public IEnergyStorage port() {
        return port;
    }

    /** Adds up to {@code amount} FE from a squall or a strike, past the cable limits; returns how much went in. */
    public int charge(int amount) {
        int added = Math.min(amount, capacity() - energy);
        if (added > 0) {
            energy += added;
            changed();
        }
        return Math.max(added, 0);
    }

    /** Takes up to {@code amount} FE to drive something, past the cable limits; returns how much came out. */
    public int draw(int amount, boolean simulate) {
        int taken = Math.min(amount, energy);
        if (taken > 0 && !simulate) {
            energy -= taken;
            changed();
        }
        return Math.max(taken, 0);
    }

    /** A lightning strike on the jar or on a rod on top of it. */
    public void strike() {
        charge(AACommonConfig.JAR_STRIKE_CHARGE.get());
        if (level instanceof ServerLevel server) {
            Zaps.sparks(server, Vec3.atCenterOf(worldPosition), 24);
        }
    }

    /** Whether the jar is electrifying copper right now. */
    public boolean live() {
        return !copper.isEmpty();
    }

    public int liveCopper() {
        return copper.size();
    }

    /** 0 when empty, else 1 to 4 by how full the jar is: the model's glow and the comparator follow it. */
    public static int chargeLevel(int energy) {
        return energy <= 0 ? 0 : Mth.clamp(Mth.ceil(energy * 4.0 / capacity()), 1, 4);
    }

    public int comparatorSignal() {
        return energy <= 0 ? 0 : Mth.clamp(Mth.ceil(energy * 15.0 / capacity()), 1, 15);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, AetherJarBlockEntity jar) {
        if (level instanceof ServerLevel server) {
            jar.tick(server, pos, state);
        }
    }

    private void tick(ServerLevel level, BlockPos pos, BlockState state) {
        long time = level.getGameTime();
        if (energy > 0 && level.getBlockState(pos.below()).is(AATags.CHARGED_COPPER)) {
            electrify(level, pos, time);
        } else {
            if (!copper.isEmpty()) {
                clearCopper();
            }
            push(level, pos);
        }
        int charge = chargeLevel(energy);
        if (charge != lastCharge) {
            lastCharge = charge;
            if (state.getValue(AetherJarBlock.CHARGE) != charge) {
                level.setBlock(pos, state.setValue(AetherJarBlock.CHARGE, charge), Block.UPDATE_ALL);
            }
            level.updateNeighbourForOutputSignal(pos, state.getBlock());
        }
    }

    /** Gives this tick's output to whatever below takes energy. */
    private void push(ServerLevel level, BlockPos pos) {
        if (energy <= 0) {
            return;
        }
        if (below == null) {
            below = BlockCapabilityCache.create(Capabilities.EnergyStorage.BLOCK, level, pos.below(), Direction.UP);
        }
        IEnergyStorage target = below.getCapability();
        if (target != null && target.canReceive()) {
            int offer = port.extractEnergy(Integer.MAX_VALUE, true);
            if (offer > 0) {
                int accepted = target.receiveEnergy(offer, false);
                if (accepted > 0) {
                    port.extractEnergy(accepted, false);
                }
            }
        }
    }

    private void electrify(ServerLevel level, BlockPos pos, long time) {
        if (copper.isEmpty() || (time + pos.asLong()) % COPPER_RESCAN == 0) {
            scanCopper(level, pos.below());
        }
        copperOwed += AACommonConfig.COPPER_DRAIN.get() * copper.size();
        int owed = (int) copperOwed;
        if (owed > 0) {
            copperOwed -= owed;
            draw(owed, false);
        }
        if (copperBounds == null || (time + pos.asLong()) % COPPER_ZAP != 0) {
            return;
        }
        float damage = AACommonConfig.ZAP_DAMAGE.get().floatValue();
        Vec3 from = Vec3.atCenterOf(pos);
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, copperBounds)) {
            if (entity.onGround() && copper.contains(entity.getOnPos().asLong())) {
                Zaps.zap(level, entity, from, damage);
            }
        }
        // a spark or two over the live floor, so it shows
        for (int i = 0; i < Math.min(2, copperList.length); i++) {
            BlockPos spot = BlockPos.of(copperList[level.random.nextInt(copperList.length)]);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, spot.getX() + level.random.nextDouble(), spot.getY() + 1.05,
                    spot.getZ() + level.random.nextDouble(), 1, 0, 0, 0, 0.05);
        }
    }

    /** Finds the copper joined to {@code start}, nearest first, up to the cap. */
    private void scanCopper(ServerLevel level, BlockPos start) {
        copper.clear();
        int cap = AACommonConfig.COPPER_MAX_BLOCKS.get();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        LongOpenHashSet seen = new LongOpenHashSet();
        queue.add(start);
        seen.add(start.asLong());
        int minX = start.getX(), minY = start.getY(), minZ = start.getZ();
        int maxX = minX, maxY = minY, maxZ = minZ;
        while (!queue.isEmpty() && copper.size() < cap) {
            BlockPos at = queue.poll();
            if (!level.isLoaded(at) || !level.getBlockState(at).is(AATags.CHARGED_COPPER)) {
                continue;
            }
            copper.add(at.asLong());
            minX = Math.min(minX, at.getX());
            minY = Math.min(minY, at.getY());
            minZ = Math.min(minZ, at.getZ());
            maxX = Math.max(maxX, at.getX());
            maxY = Math.max(maxY, at.getY());
            maxZ = Math.max(maxZ, at.getZ());
            for (Direction direction : Direction.values()) {
                BlockPos next = at.relative(direction);
                if (seen.add(next.asLong())) {
                    queue.add(next);
                }
            }
        }
        copperList = copper.toLongArray();
        // the blocks themselves and the space a body standing on them takes up
        copperBounds = copper.isEmpty() ? null : new AABB(minX, minY, minZ, maxX + 1, maxY + 2.5, maxZ + 1);
    }

    private void clearCopper() {
        copper.clear();
        copperList = new long[0];
        copperBounds = null;
        copperOwed = 0;
    }

    private void changed() {
        setChanged();
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        below = null;
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        energy = Mth.clamp(tag.getInt("energy"), 0, capacity());
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("energy", energy);
    }

    @Override
    protected void applyImplicitComponents(DataComponentInput input) {
        super.applyImplicitComponents(input);
        energy = Mth.clamp(input.getOrDefault(ElectricRegistries.STORED_ENERGY.get(), 0), 0, capacity());
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder builder) {
        super.collectImplicitComponents(builder);
        if (energy > 0) {
            builder.set(ElectricRegistries.STORED_ENERGY.get(), energy);
        }
    }

    @Override
    public void removeComponentsFromTag(CompoundTag tag) {
        tag.remove("energy");
    }

    /** Resets the per-tick cable budgets when a new tick starts. */
    private void budget() {
        long time = level == null ? 0 : level.getGameTime();
        if (time != budgetTick) {
            budgetTick = time;
            outThisTick = 0;
            inThisTick = 0;
        }
    }

    /** The bottom face, held to the configured rates per tick however many times it is asked. */
    private final class Port implements IEnergyStorage {
        @Override
        public int receiveEnergy(int toReceive, boolean simulate) {
            budget();
            int room = Math.min(capacity() - energy, AACommonConfig.JAR_INPUT.get() - inThisTick);
            int accepted = Math.max(0, Math.min(toReceive, room));
            if (!simulate && accepted > 0) {
                inThisTick += accepted;
                charge(accepted);
            }
            return accepted;
        }

        @Override
        public int extractEnergy(int toExtract, boolean simulate) {
            budget();
            int available = Math.min(energy, AACommonConfig.JAR_OUTPUT.get() - outThisTick);
            int given = Math.max(0, Math.min(toExtract, available));
            if (!simulate && given > 0) {
                outThisTick += given;
                draw(given, false);
            }
            return given;
        }

        @Override
        public int getEnergyStored() {
            return energy;
        }

        @Override
        public int getMaxEnergyStored() {
            return capacity();
        }

        @Override
        public boolean canExtract() {
            return AACommonConfig.JAR_OUTPUT.get() > 0;
        }

        @Override
        public boolean canReceive() {
            return AACommonConfig.JAR_INPUT.get() > 0;
        }
    }
}
