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
import com.linc.alteredaether.compat.CreateElectric;
import com.mojang.serialization.Codec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;

/**
 * Everything the Tempest Peaks electricity adds that stands without Create: the Aether Jar, the Squall Insulator and
 * Generator, the storm aercloud and the Static Boots, with their block entities, the jar's stored charge and flash
 * blasting. The Aerogel Wheel, which needs Create, is in compat/CreateElectric. Kept apart from registry/AARegistries
 * so the set reads as one piece.
 */
public final class ElectricRegistries {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(AlteredAether.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(AlteredAether.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, AlteredAether.MOD_ID);
    public static final DeferredRegister.DataComponents DATA_COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, AlteredAether.MOD_ID);
    public static final DeferredRegister<RecipeType<?>> RECIPE_TYPES =
            DeferredRegister.create(Registries.RECIPE_TYPE, AlteredAether.MOD_ID);
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, AlteredAether.MOD_ID);

    /** FE held in an Aether Jar item, so a broken jar keeps its charge. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> STORED_ENERGY =
            DATA_COMPONENTS.registerComponentType("stored_energy",
                    builder -> builder.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));

    public static final DeferredBlock<AetherJarBlock> AETHER_JAR = BLOCKS.registerBlock("aether_jar",
            AetherJarBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.ICE).strength(0.5F)
                    .sound(SoundType.GLASS).noOcclusion()
                    .lightLevel(state -> state.getValue(AetherJarBlock.CHARGE) * 2)
                    .isRedstoneConductor((state, level, pos) -> false)
                    .isViewBlocking((state, level, pos) -> false));
    public static final DeferredItem<AetherJarItem> AETHER_JAR_ITEM = ITEMS.register("aether_jar",
            () -> new AetherJarItem(AETHER_JAR.get(), new Item.Properties().stacksTo(16)));

    /** A squall's cloud, made like the Aether's (see block/PhasedAercloudBlock's properties). */
    public static final DeferredBlock<StormAercloudBlock> STORM_AERCLOUD = BLOCKS.registerBlock("storm_aercloud",
            StormAercloudBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY)
                    .instrument(NoteBlockInstrument.FLUTE).strength(0.3F).sound(SoundType.WOOL).noOcclusion()
                    .isValidSpawn((state, level, pos, type) -> false).isRedstoneConductor((state, level, pos) -> false)
                    .isSuffocating((state, level, pos) -> false).isViewBlocking((state, level, pos) -> false));
    public static final DeferredItem<BlockItem> STORM_AERCLOUD_ITEM = ITEMS.registerSimpleBlockItem(STORM_AERCLOUD);

    /** The empty bowl, and with a squall caught in it the Generator; both aerogel on skyroot. */
    public static final DeferredBlock<SquallInsulatorBlock> SQUALL_INSULATOR = BLOCKS.registerBlock("squall_insulator",
            SquallInsulatorBlock::new, bowl().lightLevel(state -> 0));
    public static final DeferredItem<SquallInsulatorItem> SQUALL_INSULATOR_ITEM = ITEMS.register("squall_insulator",
            () -> new SquallInsulatorItem(SQUALL_INSULATOR.get(), new Item.Properties()));
    public static final DeferredBlock<SquallGeneratorBlock> SQUALL_GENERATOR = BLOCKS.registerBlock("squall_generator",
            SquallGeneratorBlock::new, bowl().lightLevel(state -> state.getValue(SquallGeneratorBlock.LIT) ? 10 : 4));
    public static final DeferredItem<GeneratorItem> SQUALL_GENERATOR_ITEM = ITEMS.register("squall_generator",
            () -> new GeneratorItem(SQUALL_GENERATOR.get(), new Item.Properties()));

    private static BlockBehaviour.Properties bowl() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(1.5F, 3.0F).sound(SoundType.WOOD)
                .noOcclusion().isRedstoneConductor((state, level, pos) -> false);
    }

    public static final DeferredItem<StaticBootsItem> STATIC_BOOTS = ITEMS.register("static_boots",
            () -> new StaticBootsItem(new Item.Properties().stacksTo(1)));

    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AetherJarBlockEntity>> AETHER_JAR_BLOCK_ENTITY =
            BLOCK_ENTITY_TYPES.register("aether_jar",
                    () -> BlockEntityType.Builder.of(AetherJarBlockEntity::new, AETHER_JAR.get()).build(null));

    /** A Squall Generator's block entity: Create's kind when Create is loaded (see {@link SquallGeneratorHost}). */
    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BlockEntity>> SQUALL_GENERATOR_BLOCK_ENTITY =
            BLOCK_ENTITY_TYPES.register("squall_generator",
                    () -> BlockEntityType.Builder.of(SquallGeneratorHost::create, SQUALL_GENERATOR.get()).build(null));

    public static final DeferredHolder<RecipeType<?>, RecipeType<FlashBlastingRecipe>> FLASH_BLASTING =
            RECIPE_TYPES.register("flash_blasting", () -> RecipeType.simple(AlteredAether.id("flash_blasting")));
    public static final DeferredHolder<RecipeSerializer<?>, FlashBlastingRecipe.Serializer> FLASH_BLASTING_SERIALIZER =
            RECIPE_SERIALIZERS.register("flash_blasting", FlashBlastingRecipe.Serializer::new);

    /** The generator's item, with what it does on its tooltip. */
    public static final class GeneratorItem extends BlockItem {
        GeneratorItem(Block block, Properties properties) {
            super(block, properties);
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
            tooltip.add(Component.translatable("tooltip." + AlteredAether.MOD_ID + ".squall_generator")
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    /** Lists the set in the mod's creative tab (see registry/AACreativeTab). */
    public static void creativeTab(CreativeModeTab.Output output) {
        output.accept(AETHER_JAR_ITEM.get());
        output.accept(SQUALL_INSULATOR_ITEM.get());
        output.accept(SQUALL_GENERATOR_ITEM.get());
        output.accept(STORM_AERCLOUD_ITEM.get());
        output.accept(STATIC_BOOTS.get());
        if (SquallGeneratorHost.createKind()) {
            CreateElectric.creativeTab(output);
        }
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITY_TYPES.register(modBus);
        DATA_COMPONENTS.register(modBus);
        RECIPE_TYPES.register(modBus);
        RECIPE_SERIALIZERS.register(modBus);
    }

    private ElectricRegistries() {
    }
}
