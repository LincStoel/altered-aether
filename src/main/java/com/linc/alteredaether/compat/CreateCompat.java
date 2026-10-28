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

import com.aetherteam.aether.recipe.AetherRecipeTypes;
import com.aetherteam.aether.recipe.recipes.item.AbstractAetherCookingRecipe;
import com.aetherteam.aether.recipe.recipes.item.AltarRepairRecipe;
import com.linc.alteredaether.AlteredAether;
import com.linc.alteredaether.config.AACommonConfig;
import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.api.registry.CreateRegistries;
import com.simibubi.create.content.kinetics.fan.processing.FanProcessingType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Create's bulk processing through an ambrosium campfire: an encased fan blowing through a lit one enchants what it
 * carries as the Aether's altar does, by its {@code aether:enchanting} recipes. The altar's repairs, which share that
 * recipe type, stay at the altar. The campfires are {@code #altered_aether:fan_processing_catalysts/enchanting}, and
 * this outranks Create's smoking, which would otherwise claim them as campfires.
 *
 * <p>Loaded only when Create is: nothing outside this class names a Create type. The {@code compat.create} switch
 * turns the processing off without a restart.
 */
public final class CreateCompat {
    public static final String MOD = "create";
    public static final TagKey<Block> ENCHANTING_CATALYSTS =
            TagKey.create(Registries.BLOCK, AlteredAether.id("fan_processing_catalysts/enchanting"));
    /** Between smoking (200) and haunting (300), so it wins over smoking at a campfire. */
    private static final int PRIORITY = 250;
    /** The ambrosium's glow, for the air the fan pushes through it. */
    private static final int COLOUR = 0xF2D86B;

    private static final DeferredRegister<FanProcessingType> FAN_TYPES =
            DeferredRegister.create(CreateRegistries.FAN_PROCESSING_TYPE, AlteredAether.MOD_ID);

    static {
        FAN_TYPES.register("enchanting", Enchanting::new);
    }

    public static void register(IEventBus modBus) {
        FAN_TYPES.register(modBus);
    }

    /** The altar's enchanting, done in the fan's air stream. */
    static final class Enchanting implements FanProcessingType {
        @Override
        public boolean isValidAt(Level level, BlockPos pos) {
            if (!AACommonConfig.compat(MOD)) {
                return false;
            }
            BlockState state = level.getBlockState(pos);
            return state.is(ENCHANTING_CATALYSTS)
                    && (!state.hasProperty(CampfireBlock.LIT) || state.getValue(CampfireBlock.LIT));
        }

        @Override
        public int getPriority() {
            return PRIORITY;
        }

        @Override
        public boolean canProcess(ItemStack stack, Level level) {
            return recipe(stack, level).isPresent();
        }

        /** The whole stack at once. */
        @Override
        public List<ItemStack> process(ItemStack stack, Level level) {
            Optional<RecipeHolder<AbstractAetherCookingRecipe>> found = recipe(stack, level);
            if (found.isEmpty()) {
                return null;
            }
            List<ItemStack> out = new ArrayList<>();
            ItemStack result = found.get().value().assemble(new SingleRecipeInput(stack.copyWithCount(1)),
                    level.registryAccess());
            if (result.isEmpty()) {
                return out;
            }
            int total = result.getCount() * stack.getCount();
            while (total > 0) {
                int count = Math.min(total, result.getMaxStackSize());
                out.add(result.copyWithCount(count));
                total -= count;
            }
            return out;
        }

        /** The altar's enchanting recipe for the stack. Its repairs are left to the altar. */
        @SuppressWarnings("unchecked")
        private static Optional<RecipeHolder<AbstractAetherCookingRecipe>> recipe(ItemStack stack, Level level) {
            return level.getRecipeManager()
                    .getRecipeFor((RecipeType<AbstractAetherCookingRecipe>) AetherRecipeTypes.ENCHANTING.get(),
                            new SingleRecipeInput(stack), level)
                    .filter(holder -> AllRecipeTypes.CAN_BE_AUTOMATED.test(holder))
                    .filter(holder -> !(holder.value() instanceof AltarRepairRecipe));
        }

        @Override
        public void spawnProcessingParticles(Level level, Vec3 pos) {
            if (level.random.nextInt(8) == 0) {
                level.addParticle(ParticleTypes.ENCHANT, pos.x, pos.y + 0.25, pos.z, 0, 1 / 16f, 0);
            }
        }

        @Override
        public void morphAirFlow(AirFlowParticleAccess particle, RandomSource random) {
            particle.setColor(COLOUR);
            particle.setAlpha(0.5f);
            if (random.nextFloat() < 1 / 32f) {
                particle.spawnExtraParticle(ParticleTypes.ENCHANT, 0.125f);
            }
        }

        /** Ambrosium is gentle: the air through it touches no one. */
        @Override
        public void affectEntity(Entity entity, Level level) {
        }
    }

    private CreateCompat() {
    }
}
