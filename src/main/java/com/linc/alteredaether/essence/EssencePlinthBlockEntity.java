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
package com.linc.alteredaether.essence;

import com.linc.alteredaether.electric.AetherJarBlockEntity;
import com.linc.alteredaether.electric.AetherJarItem;
import com.linc.alteredaether.electric.ElectricRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * An essence plinth's items, held as a Create depot holds them: a stack waiting ({@link #input}), the one item being
 * worked ({@link #work}), shown lying on the plinth's top, and what is done ({@link #output}). The orb above feeds it
 * ({@link #feed}): a jar is charged one at a time and moved to the output once full; anything else with an essence
 * infusion recipe is infused one at a time. Hoppers and funnels put things in through the input and take them from the
 * output ({@link #items}).
 */
public class EssencePlinthBlockEntity extends BlockEntity {
    private ItemStack input = ItemStack.EMPTY;
    private ItemStack work = ItemStack.EMPTY;
    private ItemStack output = ItemStack.EMPTY;
    /** FE poured into the item being infused so far. */
    private int progress;
    @Nullable
    private ResourceLocation recipeId;
    private final Handler handler = new Handler();

    public EssencePlinthBlockEntity(BlockPos pos, BlockState state) {
        super(EssenceRegistries.PLINTH_BLOCK_ENTITY.get(), pos, state);
    }

    public ItemStack input() {
        return input;
    }

    public ItemStack work() {
        return work;
    }

    public ItemStack output() {
        return output;
    }

    public int progress() {
        return progress;
    }

    /** The cost of the recipe working now, or 0. */
    public int cost() {
        return recipe().map(holder -> holder.value().cost()).orElse(0);
    }

    public IItemHandler items() {
        return handler;
    }

    /** Whether the plinth can take {@code stack} at all: an Aether Jar or something with an infusion recipe. */
    public boolean accepts(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (stack.getItem() instanceof AetherJarItem) {
            return true;
        }
        return level != null && EssenceInfusionRecipe.find(level, stack).isPresent();
    }

    /** Puts as much of {@code stack} as fits into the input; returns what is left. */
    public ItemStack insert(ItemStack stack, boolean simulate) {
        if (!accepts(stack)) {
            return stack;
        }
        int room;
        if (input.isEmpty()) {
            room = stack.getMaxStackSize();
        } else if (ItemStack.isSameItemSameComponents(input, stack)) {
            room = input.getMaxStackSize() - input.getCount();
        } else {
            return stack;
        }
        int moved = Math.min(room, stack.getCount());
        if (moved <= 0) {
            return stack;
        }
        if (!simulate) {
            if (input.isEmpty()) {
                input = stack.copyWithCount(moved);
            } else {
                input.grow(moved);
            }
            settle();
            changed();
        }
        return stack.copyWithCount(stack.getCount() - moved);
    }

    /** Takes the plinth's contents back for a player: the output first, then the item worked, then the input. */
    public ItemStack takeForPlayer() {
        ItemStack taken;
        if (!output.isEmpty()) {
            taken = output;
            output = ItemStack.EMPTY;
        } else if (!work.isEmpty()) {
            taken = work;
            work = ItemStack.EMPTY;
            progress = 0;
            recipeId = null;
        } else {
            taken = input;
            input = ItemStack.EMPTY;
        }
        if (!taken.isEmpty()) {
            settle();
            changed();
        }
        return taken;
    }

    /** Everything on the plinth, for dropping it when the plinth breaks. */
    public ItemStack[] contents() {
        return new ItemStack[]{input, work, output};
    }

    /**
     * {@code fe} FE from a {@code source} above; returns whether any of it went to work. A full jar or a finished
     * infusion moves to the output, and the next item comes up from the input.
     */
    public boolean feed(int fe, EssenceKind source) {
        settle();
        if (work.isEmpty() || fe <= 0) {
            return false;
        }
        boolean used;
        if (work.getItem() instanceof AetherJarItem) {
            used = chargeJar(fe);
        } else {
            used = infuse(fe, source);
        }
        settle();
        if (used) {
            setChanged();
        }
        return used;
    }

    private boolean chargeJar(int fe) {
        int energy = AetherJarItem.energy(work);
        int room = AetherJarBlockEntity.capacity() - energy;
        if (room <= 0) {
            return false;
        }
        int added = Math.min(room, fe);
        work.set(ElectricRegistries.STORED_ENERGY.get(), energy + added);
        return true;
    }

    private boolean infuse(int fe, EssenceKind source) {
        Optional<RecipeHolder<EssenceInfusionRecipe>> holder = recipe();
        if (holder.isEmpty()) {
            return false;
        }
        EssenceInfusionRecipe recipe = holder.get().value();
        ItemStack made = recipe.make(source);
        if (!fits(made)) {
            return false;
        }
        progress += fe;
        if (progress >= recipe.cost()) {
            progress -= recipe.cost();
            addOutput(made);
            work = ItemStack.EMPTY;
            changed();
            if (!(input.isEmpty() || recipe.ingredient().test(input))) {
                // the next item is something else: what is left over does not carry into it
                progress = 0;
            }
            progress = Math.min(progress, recipe.cost() - 1);
        }
        return true;
    }

    private Optional<RecipeHolder<EssenceInfusionRecipe>> recipe() {
        if (level == null || work.isEmpty()) {
            return Optional.empty();
        }
        if (recipeId != null) {
            Optional<RecipeHolder<?>> known = level.getRecipeManager().byKey(recipeId);
            if (known.isPresent() && known.get().value() instanceof EssenceInfusionRecipe recipe
                    && recipe.ingredient().test(work)) {
                @SuppressWarnings("unchecked")
                RecipeHolder<EssenceInfusionRecipe> holder = (RecipeHolder<EssenceInfusionRecipe>) known.get();
                return Optional.of(holder);
            }
        }
        Optional<RecipeHolder<EssenceInfusionRecipe>> found = EssenceInfusionRecipe.find(level, work);
        recipeId = found.map(RecipeHolder::id).orElse(null);
        return found;
    }

    /** Moves a finished jar to the output and brings the next item up from the input. */
    private void settle() {
        if (!work.isEmpty() && work.getItem() instanceof AetherJarItem
                && (AetherJarItem.energy(work) >= AetherJarBlockEntity.capacity() || creativeJar(work))
                && fits(work)) {
            addOutput(work);
            work = ItemStack.EMPTY;
            changed();
        }
        if (work.isEmpty() && !input.isEmpty()) {
            work = input.split(1);
            if (input.isEmpty()) {
                input = ItemStack.EMPTY;
            }
            recipeId = null;
            changed();
        }
    }

    private static boolean creativeJar(ItemStack stack) {
        return stack.is(ElectricRegistries.CREATIVE_AETHER_JAR_ITEM.get());
    }

    private boolean fits(ItemStack stack) {
        return output.isEmpty() || ItemStack.isSameItemSameComponents(output, stack)
                && output.getCount() + stack.getCount() <= output.getMaxStackSize();
    }

    private void addOutput(ItemStack stack) {
        if (output.isEmpty()) {
            output = stack.copy();
        } else {
            output.grow(stack.getCount());
        }
    }

    /** Saves and shows the change to players near the plinth. */
    private void changed() {
        setChanged();
        if (level != null && !level.isClientSide) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
            level.updateNeighbourForOutputSignal(worldPosition, state.getBlock());
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        input = stack(tag, "input", registries);
        work = stack(tag, "work", registries);
        output = stack(tag, "output", registries);
        progress = tag.getInt("progress");
        recipeId = tag.contains("recipe") ? ResourceLocation.tryParse(tag.getString("recipe")) : null;
    }

    private static ItemStack stack(CompoundTag tag, String key, HolderLookup.Provider registries) {
        return tag.contains(key) ? ItemStack.parseOptional(registries, tag.getCompound(key)) : ItemStack.EMPTY;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        put(tag, "input", input, registries);
        put(tag, "work", work, registries);
        put(tag, "output", output, registries);
        tag.putInt("progress", progress);
        if (recipeId != null) {
            tag.putString("recipe", recipeId.toString());
        }
    }

    private static void put(CompoundTag tag, String key, ItemStack stack, HolderLookup.Provider registries) {
        if (!stack.isEmpty()) {
            tag.put(key, stack.save(registries));
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        // never empty: the client skips loading an empty data packet, so an emptied plinth would keep showing its items
        tag.putBoolean("synced", true);
        put(tag, "input", input, registries);
        put(tag, "work", work, registries);
        put(tag, "output", output, registries);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** Slot 0 takes things in (the input), slot 1 gives them out (the output). */
    private final class Handler implements IItemHandler {
        @Override
        public int getSlots() {
            return 2;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return slot == 0 ? input : slot == 1 ? output : ItemStack.EMPTY;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return slot == 0 ? insert(stack, simulate) : stack;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot != 1 || output.isEmpty() || amount <= 0) {
                return ItemStack.EMPTY;
            }
            int taken = Math.min(amount, output.getCount());
            ItemStack out = output.copyWithCount(taken);
            if (!simulate) {
                output.shrink(taken);
                if (output.isEmpty()) {
                    output = ItemStack.EMPTY;
                }
                settle();
                changed();
            }
            return out;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 64;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot == 0 && accepts(stack);
        }
    }
}
