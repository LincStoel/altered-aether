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

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;

import java.util.Optional;

/**
 * Essence infusion: an item set on an essence plinth turns into another once the source has poured {@code cost} FE into
 * it, so a stronger source works it faster. With {@code keep_source} the result remembers which kind of source made it
 * (the Quintessence Phial is stronger from a Wellspring).
 */
public record EssenceInfusionRecipe(Ingredient ingredient, ItemStack result, int cost, boolean keepSource)
        implements Recipe<SingleRecipeInput> {
    /** The recipe for {@code stack}, if any. */
    public static Optional<RecipeHolder<EssenceInfusionRecipe>> find(Level level, ItemStack stack) {
        if (stack.isEmpty()) {
            return Optional.empty();
        }
        return level.getRecipeManager().getRecipeFor(EssenceRegistries.ESSENCE_INFUSION.get(),
                new SingleRecipeInput(stack), level);
    }

    @Override
    public boolean matches(SingleRecipeInput input, Level level) {
        return ingredient.test(input.item());
    }

    @Override
    public ItemStack assemble(SingleRecipeInput input, HolderLookup.Provider registries) {
        return result.copy();
    }

    /** The result, stamped with {@code source} if it keeps it. */
    public ItemStack make(EssenceKind source) {
        ItemStack made = result.copy();
        if (keepSource) {
            made.set(EssenceRegistries.ESSENCE_SOURCE.get(), source);
        }
        return made;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return result;
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        return NonNullList.of(Ingredient.EMPTY, ingredient);
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return EssenceRegistries.ESSENCE_INFUSION_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return EssenceRegistries.ESSENCE_INFUSION.get();
    }

    @Override
    public boolean isSpecial() {
        return true;
    }

    public static final class Serializer implements RecipeSerializer<EssenceInfusionRecipe> {
        private static final MapCodec<EssenceInfusionRecipe> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Ingredient.CODEC_NONEMPTY.fieldOf("ingredient").forGetter(EssenceInfusionRecipe::ingredient),
                ItemStack.STRICT_CODEC.fieldOf("result").forGetter(EssenceInfusionRecipe::result),
                ExtraCodecs.POSITIVE_INT.fieldOf("cost").forGetter(EssenceInfusionRecipe::cost),
                Codec.BOOL.optionalFieldOf("keep_source", false).forGetter(EssenceInfusionRecipe::keepSource)
        ).apply(i, EssenceInfusionRecipe::new));
        private static final StreamCodec<RegistryFriendlyByteBuf, EssenceInfusionRecipe> STREAM_CODEC =
                StreamCodec.composite(
                        Ingredient.CONTENTS_STREAM_CODEC, EssenceInfusionRecipe::ingredient,
                        ItemStack.STREAM_CODEC, EssenceInfusionRecipe::result,
                        ByteBufCodecs.VAR_INT, EssenceInfusionRecipe::cost,
                        ByteBufCodecs.BOOL, EssenceInfusionRecipe::keepSource,
                        EssenceInfusionRecipe::new);

        @Override
        public MapCodec<EssenceInfusionRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, EssenceInfusionRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
