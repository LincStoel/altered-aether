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
package com.linc.alteredaether.debug;

import com.linc.alteredaether.compat.CreateElectric;
import com.linc.alteredaether.electric.AetherJarBlockEntity;
import com.linc.alteredaether.electric.ElectricRegistries;
import net.createmod.catnip.gui.ScreenOpener;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;

/** The Create side of {@link ElectricTour}: the wheel in its frame, a generator over a depot, the Ponder scene. */
final class ElectricTourCreate {
    static void stage(ServerLevel level, BlockPos o) {
        BlockPos wheel = o.offset(ElectricTour.WHEEL);
        for (int side : new int[]{-1, 1}) {
            BlockPos jar = wheel.east(2 * side).below();
            level.setBlockAndUpdate(jar, ElectricRegistries.AETHER_JAR.get().defaultBlockState());
            if (level.getBlockEntity(jar) instanceof AetherJarBlockEntity entity) {
                entity.charge(AetherJarBlockEntity.capacity());
            }
            level.setBlockAndUpdate(jar.above(), Blocks.LIGHTNING_ROD.defaultBlockState());
        }
        level.setBlockAndUpdate(wheel, CreateElectric.AEROGEL_WHEEL.get().defaultBlockState());
        level.setBlockAndUpdate(wheel.above(), block("shaft").defaultBlockState()
                .setValue(BlockStateProperties.AXIS, Direction.Axis.Y));
        level.setBlockAndUpdate(wheel.above(2), block("millstone").defaultBlockState());

        BlockPos depot = o.offset(ElectricTour.DEPOT);
        level.setBlockAndUpdate(depot, block("depot").defaultBlockState());
        ElectricTour.generator(level, depot.above(2), Direction.DOWN);
        level.setBlockAndUpdate(depot.above(3), Blocks.STONE.defaultBlockState());
        IItemHandler held = level.getCapability(Capabilities.ItemHandler.BLOCK, depot, Direction.UP);
        if (held != null) {
            held.insertItem(0, new ItemStack(Items.RAW_COPPER, 4), false);
        }
    }

    private static net.minecraft.world.level.block.Block block(String name) {
        return BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("create", name));
    }

    /** What the depot holds now, and what the generator over it is: it should have flash blasted the raw copper. */
    static void logDepot(ServerLevel level) {
        BlockPos depot = ElectricTour.origin().offset(ElectricTour.DEPOT);
        IItemHandler held = level.getCapability(Capabilities.ItemHandler.BLOCK, depot, Direction.UP);
        var generator = level.getBlockEntity(depot.above(2));
        ElectricTour.log("depot holds {}; over it {} in state {}", held == null ? "?" : held.getStackInSlot(0),
                generator == null ? "nothing" : generator.getClass().getSimpleName(), level.getBlockState(depot.above(2)));
    }

    static void ponder() {
        ScreenOpener.open(PonderUI.of(new ItemStack(CreateElectric.AEROGEL_WHEEL_ITEM.get())));
    }

    private ElectricTourCreate() {
    }
}
