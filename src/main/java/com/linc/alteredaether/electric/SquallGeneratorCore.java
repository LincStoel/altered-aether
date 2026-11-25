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

import com.linc.alteredaether.AlteredAether;
import com.linc.alteredaether.config.AACommonConfig;
import com.linc.alteredaether.entity.Squall;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.LightningRodBlock;
import net.minecraft.world.level.block.WeatheringCopper;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * What a Squall Generator does, shared by its plain block entity and the one it has with Create (which adds holding
 * items on a belt or depot under it; see compat/CreateElectric).
 *
 * <p>It runs on ambrosium, a shard keeping it going as long as a lump of coal keeps a furnace (the Aether gives them the
 * same burn time), or else on FE, {@code generator_fe_cost} a tick, taken in on any side but its front and back. Like a
 * blaze burner, once lit it burns whether or not anything uses it. While running it
 * <ul>
 * <li>charges an Aether Jar standing behind it by {@code generator_charge_rate} FE a tick;</li>
 * <li>counts as heated for a Create basin above it, or superheated while a storm or sterling aercloud fed to it lasts;</li>
 * <li>every {@code generator_zap_interval} ticks shocks what it faces, {@link #REACH} blocks out through open space, or
 * as far as {@link #ROD_REACH} to a lightning rod in line with it, which the arc then makes for: the block it ends on,
 * if oxidised copper, loses a stage (as a lightning rod does), mobs along the way are hurt and thrown back
 * ({@link Zaps}), dropped items there flash blast ({@link FlashBlasting}), a rod it reaches is powered as if struck,
 * and the block in front gets a short redstone pulse.</li>
 * </ul>
 */
public final class SquallGeneratorCore {
    public static final TagKey<Item> FUEL = TagKey.create(Registries.ITEM, AlteredAether.id("squall_generator_fuel"));
    public static final TagKey<Item> SUPERHEATERS =
            TagKey.create(Registries.ITEM, AlteredAether.id("superheats_squall_generator"));
    public static final int FUEL_SLOT = 0, SUPERHEAT_SLOT = 1;
    /** How long the redstone pulse of a shock lasts. */
    private static final int PULSE_TICKS = 2;
    /** Ticks of running the FE buffer holds. */
    private static final int BUFFER_TICKS = 100;
    /** How far a shock reaches through open space. */
    public static final int REACH = 2;
    /** How far it reaches to a lightning rod in line with the generator's face. */
    public static final int ROD_REACH = 8;

    private final BlockEntity owner;
    private int burnTime, burnTotal, superheat, zapTimer, pulse, energy;
    private final ItemStackHandler items = new ItemStackHandler(2) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot == FUEL_SLOT ? isFuel(stack) : isSuperheater(stack);
        }

        @Override
        protected void onContentsChanged(int slot) {
            owner.setChanged();
        }
    };
    private final IEnergyStorage energyPort = new IEnergyStorage() {
        @Override
        public int receiveEnergy(int toReceive, boolean simulate) {
            int accepted = Math.max(0, Math.min(toReceive, bufferSize() - energy));
            if (!simulate && accepted > 0) {
                energy += accepted;
                owner.setChanged();
            }
            return accepted;
        }

        @Override
        public int extractEnergy(int toExtract, boolean simulate) {
            return 0;
        }

        @Override
        public int getEnergyStored() {
            return energy;
        }

        @Override
        public int getMaxEnergyStored() {
            return bufferSize();
        }

        @Override
        public boolean canExtract() {
            return false;
        }

        @Override
        public boolean canReceive() {
            return true;
        }
    };

    public SquallGeneratorCore(BlockEntity owner) {
        this.owner = owner;
    }

    public static boolean isFuel(ItemStack stack) {
        return stack.is(FUEL) && stack.getBurnTime(RecipeType.SMELTING) > 0;
    }

    public static boolean isSuperheater(ItemStack stack) {
        return stack.is(SUPERHEATERS);
    }

    private static int bufferSize() {
        return Math.max(1, AACommonConfig.GENERATOR_FE_COST.get() * BUFFER_TICKS);
    }

    public ItemStackHandler items() {
        return items;
    }

    /** Its energy face on {@code side}: every side but the front and the back, where the jar goes. */
    @Nullable
    public IEnergyStorage energy(@Nullable Direction side, Direction facing) {
        return side == facing || side == facing.getOpposite() ? null : energyPort;
    }

    public int burnTime() {
        return burnTime;
    }

    public int superheat() {
        return superheat;
    }

    public int storedEnergy() {
        return energy;
    }

    /** Puts {@code stack} (or what of it fits) in, as if from a hopper; returns what is left. */
    public ItemStack insert(ItemStack stack) {
        int slot = isFuel(stack) ? FUEL_SLOT : isSuperheater(stack) ? SUPERHEAT_SLOT : -1;
        return slot < 0 ? stack : items.insertItem(slot, stack, false);
    }

    public void tick(ServerLevel level, BlockPos pos, BlockState state) {
        Direction facing = state.getValue(SquallGeneratorBlock.FACING);
        boolean running = burn();
        if (running) {
            if (superheat > 0) {
                superheat--;
            }
            if (superheat <= 0) {
                ItemStack cloud = items.extractItem(SUPERHEAT_SLOT, 1, false);
                if (!cloud.isEmpty()) {
                    superheat = AACommonConfig.GENERATOR_SUPERHEAT_TICKS.get();
                }
            }
            if (level.getBlockEntity(pos.relative(facing.getOpposite())) instanceof AetherJarBlockEntity jar) {
                jar.charge(AACommonConfig.GENERATOR_CHARGE_RATE.get());
            }
            if (++zapTimer >= AACommonConfig.GENERATOR_ZAP_INTERVAL.get()) {
                zapTimer = 0;
                zap(level, pos, facing);
                state = level.getBlockState(pos);
            }
            owner.setChanged();
        } else {
            zapTimer = 0;
        }
        if (pulse > 0 && --pulse == 0) {
            state = SquallGeneratorBlock.setPowered(level, pos, level.getBlockState(pos), false);
        }
        SquallGeneratorBlock.Heat heat = !running ? SquallGeneratorBlock.Heat.NONE
                : superheat > 0 ? SquallGeneratorBlock.Heat.SUPERHEATED : SquallGeneratorBlock.Heat.HEATED;
        if (state.hasProperty(SquallGeneratorBlock.LIT)
                && (state.getValue(SquallGeneratorBlock.LIT) != running || state.getValue(SquallGeneratorBlock.HEAT) != heat)) {
            level.setBlock(pos, state.setValue(SquallGeneratorBlock.LIT, running).setValue(SquallGeneratorBlock.HEAT, heat),
                    Block.UPDATE_ALL);
        }
    }

    /** Spends a tick of fuel, lighting more ambrosium or drawing FE if need be; false if there is none. */
    private boolean burn() {
        if (burnTime > 0) {
            burnTime--;
            return true;
        }
        ItemStack fuel = items.getStackInSlot(FUEL_SLOT);
        int time = fuel.isEmpty() ? 0 : fuel.getBurnTime(RecipeType.SMELTING);
        if (time > 0) {
            items.extractItem(FUEL_SLOT, 1, false);
            burnTotal = time;
            burnTime = time - 1;
            return true;
        }
        int cost = AACommonConfig.GENERATOR_FE_COST.get();
        if (energy >= cost) {
            energy -= cost;
            return true;
        }
        return false;
    }

    /** One shock out along the way the generator faces. */
    private void zap(ServerLevel level, BlockPos pos, Direction facing) {
        Vec3 from = Vec3.atCenterOf(pos);
        BlockPos rod = rodInReach(level, pos, facing);
        int reach = rod != null ? pos.distManhattan(rod) : REACH;
        // the arc runs through open space and ends on the first block in its way (or the rod)
        BlockPos end = pos;
        for (int i = 1; i <= reach; i++) {
            end = pos.relative(facing, i);
            if (rod == null && !open(level, end)) {
                break;
            }
        }
        Optional<BlockState> scraped = WeatheringCopper.getPrevious(level.getBlockState(end));
        if (scraped.isPresent()) {
            level.setBlockAndUpdate(end, scraped.get());
            // the spark a struck rod gives the copper round it
            level.levelEvent(LevelEvent.PARTICLES_ELECTRIC_SPARK, end, -1);
        }
        if (rod != null && level.getBlockState(rod).getBlock() instanceof LightningRodBlock struck) {
            struck.onLightningStrike(level.getBlockState(rod), level, rod);
        }
        AABB space = new AABB(pos.relative(facing)).minmax(new AABB(end));
        float damage = AACommonConfig.ZAP_DAMAGE.get().floatValue();
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, space, e -> !(e instanceof Squall))) {
            Zaps.zap(level, entity, from, damage);
        }
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, space)) {
            flash(level, item);
        }
        pulse = PULSE_TICKS;
        SquallGeneratorBlock.setPowered(level, pos, level.getBlockState(pos), true);
        arc(level, from, rod != null ? Vec3.atCenterOf(rod) : Vec3.atCenterOf(end));
    }

    /** A lightning rod within {@link #ROD_REACH} straight out from the face, with open space up to it; else null. */
    @Nullable
    private static BlockPos rodInReach(ServerLevel level, BlockPos pos, Direction facing) {
        for (int i = 1; i <= ROD_REACH; i++) {
            BlockPos at = pos.relative(facing, i);
            if (level.getBlockState(at).is(Blocks.LIGHTNING_ROD)) {
                return at;
            }
            if (!open(level, at)) {
                return null;
            }
        }
        return null;
    }

    /** Whether a shock passes through {@code at}: nothing solid there to stop it. */
    private static boolean open(ServerLevel level, BlockPos at) {
        return level.isLoaded(at) && level.getBlockState(at).getCollisionShape(level, at).isEmpty();
    }

    /** Flash blasts a dropped item where it lies. */
    private static void flash(ServerLevel level, ItemEntity item) {
        List<ItemStack> out = FlashBlasting.process(level, item.getItem());
        if (out == null) {
            return;
        }
        Vec3 at = item.position();
        item.discard();
        for (ItemStack stack : out) {
            ItemEntity made = new ItemEntity(level, at.x, at.y, at.z, stack, 0, 0, 0);
            made.setDefaultPickUpDelay();
            level.addFreshEntity(made);
        }
        level.sendParticles(ParticleTypes.FLASH, at.x, at.y + 0.2, at.z, 1, 0, 0, 0, 0);
    }

    /** Flash blasts {@code stack} held on a belt or depot, for Create; null if nothing comes of it. */
    @Nullable
    public List<ItemStack> flash(ServerLevel level, ItemStack stack, Vec3 at) {
        List<ItemStack> out = FlashBlasting.process(level, stack);
        if (out != null) {
            level.sendParticles(ParticleTypes.FLASH, at.x, at.y, at.z, 1, 0, 0, 0, 0);
            arc(level, Vec3.atCenterOf(owner.getBlockPos()), at);
        }
        return out;
    }

    /** Sparks along the line from {@code from} to {@code to}, and a crackle. */
    public static void arc(ServerLevel level, Vec3 from, Vec3 to) {
        int steps = Math.max(6, (int) Math.ceil(from.distanceTo(to) * 4));
        Vec3 step = to.subtract(from).scale(1.0 / steps);
        for (int i = 1; i <= steps; i++) {
            Vec3 at = from.add(step.scale(i));
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 2, 0.08, 0.08, 0.08, 0.02);
        }
        level.playSound(null, to.x, to.y, to.z, SoundEvents.COPPER_BULB_TURN_ON, SoundSource.BLOCKS, 0.5F,
                1.7F + level.random.nextFloat() * 0.3F);
    }

    public void save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put("items", items.serializeNBT(registries));
        tag.putInt("burn", burnTime);
        tag.putInt("burn_total", burnTotal);
        tag.putInt("superheat", superheat);
        tag.putInt("energy", energy);
        tag.putInt("zap", zapTimer);
    }

    public void load(CompoundTag tag, HolderLookup.Provider registries) {
        items.deserializeNBT(registries, tag.getCompound("items"));
        burnTime = tag.getInt("burn");
        burnTotal = tag.getInt("burn_total");
        superheat = tag.getInt("superheat");
        energy = Math.min(tag.getInt("energy"), bufferSize());
        zapTimer = tag.getInt("zap");
    }
}
