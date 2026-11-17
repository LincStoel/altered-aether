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
package com.linc.alteredaether.gametest;

import com.aetherteam.aether.block.AetherBlocks;
import com.aetherteam.aether.item.AetherItems;
import com.aetherteam.aether.recipe.AetherRecipeTypes;
import com.linc.alteredaether.AlteredAether;
import com.linc.alteredaether.compat.GravititeRework;
import com.linc.alteredaether.registry.AARegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * The gravitite rework (on by default): the ingot only comes from enchanting, the Aether's ore-to-block enchanting is
 * gone, gear takes ingots, the ore drops raw gravitite (the ore itself with silk touch, more with Fortune), and
 * dropped gravitite hovers, except over a hopper, which still collects it.
 */
@GameTestHolder(AlteredAether.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GravititeOreTests {
    @GameTest(template = "arena")
    public static void ingotsOnlyComeFromEnchanting(GameTestHelper helper) {
        if (!GravititeRework.active()) {
            helper.succeed();
            return;
        }
        ServerLevel level = helper.getLevel();
        var recipes = level.getRecipeManager();
        Item ingot = AARegistries.ENCHANTED_GRAVITITE_INGOT.get();
        Item nugget = AARegistries.ENCHANTED_GRAVITITE_NUGGET.get();
        List<RecipeType<?>> heat = List.of(RecipeType.SMELTING, RecipeType.BLASTING, RecipeType.SMOKING,
                RecipeType.CAMPFIRE_COOKING);
        for (RecipeHolder<?> holder : recipes.getRecipes()) {
            if (!heat.contains(holder.value().getType())) {
                continue;
            }
            Item result = holder.value().getResultItem(level.registryAccess()).getItem();
            if (result == ingot || result == nugget) {
                helper.fail(holder.id() + " makes enchanted gravitite with heat");
                return;
            }
        }
        if (recipes.byKey(ResourceLocation.fromNamespaceAndPath("aether", "enchanted_gravitite_enchanting")).isPresent()) {
            helper.fail("The Aether's ore-to-block enchanting should be removed");
            return;
        }
        for (Item input : List.of(AetherBlocks.GRAVITITE_ORE.get().asItem(), AARegistries.RAW_GRAVITITE.get())) {
            ItemStack out = enchant(level, new ItemStack(input));
            if (!out.is(ingot)) {
                helper.fail("Enchanting " + input + " should give an ingot, gave " + out);
                return;
            }
        }
        if (!enchant(level, new ItemStack(AARegistries.RAW_GRAVITITE_BLOCK_ITEM.get())).is(AetherBlocks.ENCHANTED_GRAVITITE.get().asItem())) {
            helper.fail("Enchanting a raw gravitite block should give the enchanted gravitite block");
            return;
        }
        // Gear: the pickaxe crafts from three ingots, not three blocks
        if (!pickaxe(level, new ItemStack(ingot)) || pickaxe(level, new ItemStack(AetherBlocks.ENCHANTED_GRAVITITE.get()))) {
            helper.fail("A gravitite pickaxe should craft from ingots and not from blocks");
            return;
        }
        TagKey<Item> repairing = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("aether", "gravitite_repairing"));
        if (!new ItemStack(ingot).is(repairing) || new ItemStack(AetherBlocks.ENCHANTED_GRAVITITE.get()).is(repairing)) {
            helper.fail("Gravitite gear should repair with ingots, not blocks");
            return;
        }
        helper.succeed();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ItemStack enchant(ServerLevel level, ItemStack input) {
        SingleRecipeInput recipeInput = new SingleRecipeInput(input);
        for (RecipeHolder<?> holder : level.getRecipeManager().getRecipes()) {
            if (holder.value().getType() == AetherRecipeTypes.ENCHANTING.get()
                    && ((net.minecraft.world.item.crafting.Recipe) holder.value()).matches(recipeInput, level)) {
                return holder.value().getResultItem(level.registryAccess());
            }
        }
        return ItemStack.EMPTY;
    }

    private static boolean pickaxe(ServerLevel level, ItemStack material) {
        List<ItemStack> grid = new ArrayList<>(List.of(material, material, material,
                ItemStack.EMPTY, new ItemStack(AetherItems.SKYROOT_STICK.get()), ItemStack.EMPTY,
                ItemStack.EMPTY, new ItemStack(AetherItems.SKYROOT_STICK.get()), ItemStack.EMPTY));
        return level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, CraftingInput.of(3, 3, grid), level)
                .map(holder -> holder.value().getResultItem(level.registryAccess()).is(AetherItems.GRAVITITE_PICKAXE.get()))
                .orElse(false);
    }

    @GameTest(template = "arena")
    public static void oreDropsRawGravitite(GameTestHelper helper) {
        if (!GravititeRework.active()) {
            helper.succeed();
            return;
        }
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(20, 2, 20));
        var enchantments = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        ItemStack plain = new ItemStack(Items.DIAMOND_PICKAXE);
        ItemStack silk = new ItemStack(Items.DIAMOND_PICKAXE);
        silk.enchant(enchantments.getOrThrow(Enchantments.SILK_TOUCH), 1);
        ItemStack fortune = new ItemStack(Items.DIAMOND_PICKAXE);
        fortune.enchant(enchantments.getOrThrow(Enchantments.FORTUNE), 3);
        var ore = AetherBlocks.GRAVITITE_ORE.get().defaultBlockState();

        List<ItemStack> drops = Block.getDrops(ore, level, pos, null, null, plain);
        if (drops.size() != 1 || !drops.get(0).is(AARegistries.RAW_GRAVITITE.get()) || drops.get(0).getCount() != 1) {
            helper.fail("Gravitite ore should drop one raw gravitite, dropped " + drops);
            return;
        }
        drops = Block.getDrops(ore, level, pos, null, null, silk);
        if (drops.size() != 1 || !drops.get(0).is(AetherBlocks.GRAVITITE_ORE.get().asItem())) {
            helper.fail("Gravitite ore should drop itself with silk touch, dropped " + drops);
            return;
        }
        int total = 0;
        for (int i = 0; i < 200; i++) {
            for (ItemStack stack : Block.getDrops(ore, level, pos, null, null, fortune)) {
                if (stack.is(AARegistries.RAW_GRAVITITE.get())) {
                    total += stack.getCount();
                }
            }
        }
        // Fortune III averages 2.2 per ore
        if (total < 340) {
            helper.fail("Fortune III should average about 2.2 raw gravitite per ore, got " + total / 200.0);
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "arena", timeoutTicks = 200)
    public static void droppedGravititeHoversButHoppersCollectIt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos ground = helper.absolutePos(new BlockPos(10, 2, 10));
        level.setBlockAndUpdate(ground, Blocks.STONE.defaultBlockState());
        ItemEntity floating = new ItemEntity(level, ground.getX() + 0.5, ground.getY() + 1.05, ground.getZ() + 0.5,
                new ItemStack(AARegistries.ENCHANTED_GRAVITITE_INGOT.get()));
        floating.setDeltaMovement(0, 0, 0);
        level.addFreshEntity(floating);

        BlockPos hopper = helper.absolutePos(new BlockPos(14, 2, 14));
        level.setBlockAndUpdate(hopper, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.ENABLED, true));
        ItemEntity collected = new ItemEntity(level, hopper.getX() + 0.5, hopper.getY() + 1.5, hopper.getZ() + 0.5,
                new ItemStack(AARegistries.RAW_GRAVITITE.get()));
        collected.setDeltaMovement(0, 0, 0);
        level.addFreshEntity(collected);

        helper.runAfterDelay(120, () -> {
            double height = floating.getY() - (ground.getY() + 1);
            if (floating.isRemoved() || height < 1.4 || height > 1.9) {
                helper.fail("A dropped ingot should hover about 1.75 above the ground, was at " + height);
                return;
            }
            if (!(level.getBlockEntity(hopper) instanceof HopperBlockEntity hopperEntity)
                    || hopperEntity.getItem(0).getCount() + hopperEntity.getItem(1).getCount() == 0
                    && !collected.isRemoved()) {
                helper.fail("A hopper should still collect raw gravitite dropped over it");
                return;
            }
            helper.succeed();
        });
    }

    /** Each mod's gravitite recipes load when it's there (a bad recipe is dropped at load, so it would be missing). */
    @GameTest(template = "arena")
    public static void optionalModRecipesLoad(GameTestHelper helper) throws ClassNotFoundException {
        if (!GravititeRework.active()) {
            helper.succeed();
            return;
        }
        var recipes = helper.getLevel().getRecipeManager();
        java.util.Map<String, List<String>> byMod = java.util.Map.of(
                "create", List.of("create/crushing_raw", "create/crushing_ore", "create/crushing_raw_block",
                        "create/splashing_crushed", "enchanting/crushed"),
                "oritech", List.of("oritech/pulverizer_ore", "oritech/pulverizer_raw", "enchanting/dust",
                        "enchanting/small_dust", "dust_from_small_dust"),
                "ars_nouveau", List.of("ars_nouveau/imbuement_raw", "ars_nouveau/crush_ore"),
                "deep_aether", List.of("deep_aether/ore_from_poison"));
        for (var mod : byMod.entrySet()) {
            if (!net.neoforged.fml.ModList.get().isLoaded(mod.getKey())) {
                continue;
            }
            for (String path : mod.getValue()) {
                if (recipes.byKey(AlteredAether.id("gravitite/" + path)).isEmpty()) {
                    helper.fail("With " + mod.getKey() + ", recipe gravitite/" + path + " should load");
                    return;
                }
            }
        }
        if (net.neoforged.fml.ModList.get().isLoaded("deep_aether") && recipes.byKey(
                ResourceLocation.fromNamespaceAndPath("deep_aether", "gravitite_ore_from_poison")).isPresent()) {
            helper.fail("Deep Aether's block-poisoning recipe should be replaced by the ingot's");
            return;
        }
        if (net.neoforged.fml.ModList.get().isLoaded("sable") && !com.linc.alteredaether.compat.SableLift.Tracked.class
                .isAssignableFrom(Class.forName("dev.ryanhcode.sable.physics.floating_block.FloatingBlockController"))) {
            helper.fail("Sable's floating block controller should carry the enchanted gravitite gate");
            return;
        }
        helper.succeed();
    }

    /** Sable ships carrying gravitite (see GravititeSableCheck); passes without Sable. */
    @GameTest(template = "arena", timeoutTicks = 1000)
    public static void sableShipsLiftOnlyWhenPowered(GameTestHelper helper) {
        if (!net.neoforged.fml.ModList.get().isLoaded("sable") || !GravititeRework.active()) {
            helper.succeed();
            return;
        }
        GravititeSableCheck.run(helper);
    }

    /**
     * The Aether's ingredients tab lists the new items, crushed raw gravitite only with Create and the dusts only with
     * Oritech; its natural blocks tab, the raw block.
     */
    @GameTest(template = "arena")
    public static void creativeTabsListGravitite(GameTestHelper helper) {
        if (!GravititeRework.active()) {
            helper.succeed();
            return;
        }
        ServerLevel level = helper.getLevel();
        net.minecraft.world.item.CreativeModeTabs.tryRebuildTabContents(level.enabledFeatures(), true, level.registryAccess());
        var ingredients = com.aetherteam.aether.item.AetherCreativeTabs.AETHER_INGREDIENTS.get().getDisplayItems();
        var natural = com.aetherteam.aether.item.AetherCreativeTabs.AETHER_NATURAL_BLOCKS.get().getDisplayItems();
        var loaded = net.neoforged.fml.ModList.get();
        java.util.Map<Item, Boolean> expected = java.util.Map.of(
                AARegistries.RAW_GRAVITITE.get(), true,
                AARegistries.ENCHANTED_GRAVITITE_INGOT.get(), true,
                AARegistries.ENCHANTED_GRAVITITE_NUGGET.get(), true,
                AARegistries.CRUSHED_RAW_GRAVITITE.get(), loaded.isLoaded("create"),
                AARegistries.GRAVITITE_DUST.get(), loaded.isLoaded("oritech"),
                AARegistries.SMALL_GRAVITITE_DUST.get(), loaded.isLoaded("oritech"));
        for (var entry : expected.entrySet()) {
            boolean listed = ingredients.stream().anyMatch(stack -> stack.is(entry.getKey()));
            if (listed != entry.getValue()) {
                helper.fail(entry.getKey() + " should " + (entry.getValue() ? "" : "not ") + "be in the Aether's ingredients tab");
                return;
            }
        }
        if (natural.stream().noneMatch(stack -> stack.is(AARegistries.RAW_GRAVITITE_BLOCK_ITEM.get()))) {
            helper.fail("The raw gravitite block should be in the Aether's natural blocks tab");
            return;
        }
        helper.succeed();
    }

    private GravititeOreTests() {
    }
}
