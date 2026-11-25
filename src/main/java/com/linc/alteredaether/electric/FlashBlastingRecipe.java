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

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;

/**
 * One of the Squall Generator's own flash-blasting recipes: an item the spark turns into another, as a blast furnace
 * would if it could (sand into glass, quicksoil into quicksoil glass). Anything with a vanilla blasting recipe flash
 * blasts too; see {@link FlashBlasting}.
 */
public record FlashBlastingRecipe(Ingredient ingredient, ItemStack result) implements Recipe<SingleRecipeInput> {
    @Override
    public boolean matches(SingleRecipeInput input, Level level) {
        return ingredient.test(input.item());
    }

    @Override
    public ItemStack assemble(SingleRecipeInput input, HolderLookup.Provider registries) {
        return result.copy();
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
        return ElectricRegistries.FLASH_BLASTING_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return ElectricRegistries.FLASH_BLASTING.get();
    }

    @Override
    public boolean isSpecial() {
        return true;
    }

    public static final class Serializer implements RecipeSerializer<FlashBlastingRecipe> {
        private static final MapCodec<FlashBlastingRecipe> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Ingredient.CODEC_NONEMPTY.fieldOf("ingredient").forGetter(FlashBlastingRecipe::ingredient),
                ItemStack.STRICT_CODEC.fieldOf("result").forGetter(FlashBlastingRecipe::result)
        ).apply(i, FlashBlastingRecipe::new));
        private static final StreamCodec<RegistryFriendlyByteBuf, FlashBlastingRecipe> STREAM_CODEC = StreamCodec.composite(
                Ingredient.CONTENTS_STREAM_CODEC, FlashBlastingRecipe::ingredient,
                ItemStack.STREAM_CODEC, FlashBlastingRecipe::result,
                FlashBlastingRecipe::new);

        @Override
        public MapCodec<FlashBlastingRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, FlashBlastingRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
