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

import com.linc.alteredaether.AlteredAether;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;
import java.util.List;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Essence Channels and Wellsprings: the orbs, the capstones and plinth that gather them, the infusion they work, the
 * Quintessence Phial and its effect, the Dowsing Rod, and the motes of light. Kept apart from registry/AARegistries so
 * the set reads as one piece.
 */
public final class EssenceRegistries {
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
    public static final DeferredRegister<MobEffect> MOB_EFFECTS =
            DeferredRegister.create(Registries.MOB_EFFECT, AlteredAether.MOD_ID);
    public static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES =
            DeferredRegister.create(Registries.PARTICLE_TYPE, AlteredAether.MOD_ID);

    /** Which kind of source made an infused item, for those that keep it (the Quintessence Phial). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<EssenceKind>> ESSENCE_SOURCE =
            DATA_COMPONENTS.registerComponentType("essence_source",
                    builder -> builder.persistent(EssenceKind.CODEC).networkSynchronized(EssenceKind.STREAM_CODEC));
    /** The source a Dowsing Rod is drawn to, kept up to date while it is held. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<BlockPos>> DOWSING_TARGET =
            DATA_COMPONENTS.registerComponentType("dowsing_target",
                    builder -> builder.persistent(BlockPos.CODEC).networkSynchronized(BlockPos.STREAM_CODEC));

    public static final DeferredBlock<EssenceOrbBlock> ESSENCE_CHANNEL = BLOCKS.registerBlock("essence_channel",
            properties -> new EssenceOrbBlock(properties, EssenceKind.CHANNEL), orb());
    public static final DeferredBlock<EssenceOrbBlock> ESSENCE_WELLSPRING = BLOCKS.registerBlock("essence_wellspring",
            properties -> new EssenceOrbBlock(properties, EssenceKind.WELLSPRING), orb());

    /** Unbreakable, unmovable, without collision: nothing but light. */
    private static BlockBehaviour.Properties orb() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.SNOW).strength(-1.0F, 3_600_000.0F).noLootTable()
                .noCollission().noOcclusion().lightLevel(state -> 15).sound(SoundType.AMETHYST)
                .pushReaction(PushReaction.BLOCK).isValidSpawn((state, level, pos, type) -> false)
                .isRedstoneConductor((state, level, pos) -> false).isSuffocating((state, level, pos) -> false)
                .isViewBlocking((state, level, pos) -> false);
    }

    /** Celezite, as polished celezite is. */
    public static final DeferredBlock<EssenceCapstoneBlock> ESSENCE_CAPSTONE = BLOCKS.registerBlock("essence_capstone",
            EssenceCapstoneBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_WHITE)
                    .instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(2.5F, 6.0F)
                    .sound(SoundType.CALCITE));
    public static final DeferredItem<BlockItem> ESSENCE_CAPSTONE_ITEM = ITEMS.register("essence_capstone",
            () -> new TooltipBlockItem(ESSENCE_CAPSTONE.get(), new Item.Properties()));
    public static final DeferredBlock<EssencePlinthBlock> ESSENCE_PLINTH = BLOCKS.registerBlock("essence_plinth",
            EssencePlinthBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_WHITE)
                    .instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(2.5F, 6.0F)
                    .sound(SoundType.CALCITE).noOcclusion());
    public static final DeferredItem<BlockItem> ESSENCE_PLINTH_ITEM = ITEMS.register("essence_plinth",
            () -> new TooltipBlockItem(ESSENCE_PLINTH.get(), new Item.Properties()));

    public static final DeferredItem<QuintessencePhialItem> QUINTESSENCE_PHIAL = ITEMS.register("quintessence_phial",
            () -> new QuintessencePhialItem(new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON)));
    public static final DeferredItem<DowsingRodItem> DOWSING_ROD = ITEMS.register("dowsing_rod",
            () -> new DowsingRodItem(new Item.Properties().stacksTo(1)));

    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<EssenceOrbBlockEntity>> ORB_BLOCK_ENTITY =
            BLOCK_ENTITY_TYPES.register("essence_orb", () -> BlockEntityType.Builder.of(EssenceOrbBlockEntity::new,
                    ESSENCE_CHANNEL.get(), ESSENCE_WELLSPRING.get()).build(null));
    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<EssenceCapstoneBlockEntity>>
            CAPSTONE_BLOCK_ENTITY = BLOCK_ENTITY_TYPES.register("essence_capstone",
            () -> BlockEntityType.Builder.of(EssenceCapstoneBlockEntity::new, ESSENCE_CAPSTONE.get()).build(null));
    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<EssencePlinthBlockEntity>>
            PLINTH_BLOCK_ENTITY = BLOCK_ENTITY_TYPES.register("essence_plinth",
            () -> BlockEntityType.Builder.of(EssencePlinthBlockEntity::new, ESSENCE_PLINTH.get()).build(null));

    public static final DeferredHolder<RecipeType<?>, RecipeType<EssenceInfusionRecipe>> ESSENCE_INFUSION =
            RECIPE_TYPES.register("essence_infusion", () -> RecipeType.simple(AlteredAether.id("essence_infusion")));
    public static final DeferredHolder<RecipeSerializer<?>, EssenceInfusionRecipe.Serializer> ESSENCE_INFUSION_SERIALIZER =
            RECIPE_SERIALIZERS.register("essence_infusion", EssenceInfusionRecipe.Serializer::new);

    public static final DeferredHolder<MobEffect, QuintessenceEffect> QUINTESSENCE =
            MOB_EFFECTS.register("quintessence", QuintessenceEffect::new);

    /** A slow, flitting note of light about an orb. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> ESSENCE_NOTE =
            PARTICLE_TYPES.register("essence_note", () -> new SimpleParticleType(false));
    /** A mote drifting from an orb to what it feeds. */
    public static final DeferredHolder<ParticleType<?>, EssenceMoteOptions.Type> ESSENCE_MOTE =
            PARTICLE_TYPES.register("essence_mote", EssenceMoteOptions.Type::new);

    /** A block's item with what it does on its tooltip ({@code tooltip.altered_aether.<block>}). */
    public static final class TooltipBlockItem extends BlockItem {
        TooltipBlockItem(Block block, Properties properties) {
            super(block, properties);
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
            tooltip.add(Component.translatable("tooltip." + AlteredAether.MOD_ID + "."
                    + BuiltInRegistries.BLOCK.getKey(getBlock()).getPath()).withStyle(ChatFormatting.GRAY));
        }
    }

    /** Lists the set in the mod's creative tab (see registry/AACreativeTab). */
    public static void creativeTab(CreativeModeTab.Output output) {
        output.accept(ESSENCE_CAPSTONE_ITEM.get());
        output.accept(ESSENCE_PLINTH_ITEM.get());
        output.accept(DOWSING_ROD.get());
        output.accept(QUINTESSENCE_PHIAL.get());
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITY_TYPES.register(modBus);
        DATA_COMPONENTS.register(modBus);
        RECIPE_TYPES.register(modBus);
        RECIPE_SERIALIZERS.register(modBus);
        MOB_EFFECTS.register(modBus);
        PARTICLE_TYPES.register(modBus);
    }

    private EssenceRegistries() {
    }
}
