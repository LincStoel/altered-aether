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

import com.linc.alteredaether.AlteredAether;
import com.linc.alteredaether.compat.create.AerogelWheelBlock;
import com.linc.alteredaether.compat.create.AerogelWheelBlockEntity;
import com.linc.alteredaether.compat.create.CreateElectricClient;
import com.linc.alteredaether.compat.create.CreateSquallGeneratorBlockEntity;
import com.linc.alteredaether.config.AACommonConfig;
import com.simibubi.create.api.stress.BlockStressValues;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;

/**
 * The Tempest Peaks electricity with Create: the Aerogel Wheel, Franklin's electric wheel, a kinetic source spun by two
 * charged Aether Jars (see compat/create/AerogelWheelBlockEntity), and the Squall Generator's Create block entity,
 * which holds items on a belt or depot under it to flash blast them, as a press does. The generator heats a basin over
 * it through mixin/BasinHeatMixin.
 *
 * <p>Loaded only when Create is: nothing outside this class and compat/create names a Create type.
 */
public final class CreateElectric {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(AlteredAether.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(AlteredAether.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, AlteredAether.MOD_ID);

    public static final DeferredBlock<AerogelWheelBlock> AEROGEL_WHEEL = BLOCKS.registerBlock("aerogel_wheel",
            AerogelWheelBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(1.5F, 3.0F)
                    .sound(SoundType.WOOD).noOcclusion().isRedstoneConductor((state, level, pos) -> false));
    public static final DeferredItem<BlockItem> AEROGEL_WHEEL_ITEM = ITEMS.register("aerogel_wheel",
            () -> new WheelItem(AEROGEL_WHEEL.get(), new Item.Properties()));
    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AerogelWheelBlockEntity>> AEROGEL_WHEEL_BLOCK_ENTITY =
            BLOCK_ENTITY_TYPES.register("aerogel_wheel",
                    () -> BlockEntityType.Builder.of(AerogelWheelBlockEntity::new, AEROGEL_WHEEL.get()).build(null));

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITY_TYPES.register(modBus);
        modBus.addListener(CreateElectric::setup);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            CreateElectricClient.register(modBus);
        }
    }

    private static void setup(FMLCommonSetupEvent event) {
        // what goggles and JEI show; the wheel's own capacity comes from the same number
        event.enqueueWork(() -> BlockStressValues.CAPACITIES.register(AEROGEL_WHEEL.get(),
                () -> AACommonConfig.WHEEL_STRESS_PER_RPM.get()));
    }

    /** A Squall Generator's block entity with Create there (see electric/SquallGeneratorHost). */
    public static BlockEntity generator(BlockPos pos, BlockState state) {
        return new CreateSquallGeneratorBlockEntity(pos, state);
    }

    /** Lists the wheel in the mod's creative tab, after the rest of the set. */
    public static void creativeTab(net.minecraft.world.item.CreativeModeTab.Output output) {
        output.accept(AEROGEL_WHEEL_ITEM.get());
    }

    private static final class WheelItem extends BlockItem {
        WheelItem(Block block, Properties properties) {
            super(block, properties);
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
            tooltip.add(Component.translatable("tooltip." + AlteredAether.MOD_ID + ".aerogel_wheel")
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    private CreateElectric() {
    }
}
