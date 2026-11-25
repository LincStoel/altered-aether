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
import net.minecraft.core.Direction;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/** The energy and item faces of the electric blocks. */
@EventBusSubscriber(modid = AlteredAether.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class ElectricCapabilities {
    @SubscribeEvent
    public static void register(RegisterCapabilitiesEvent event) {
        // power goes into a jar at its bottom and out at its top, whichever way it is turned; a side-less query (a
        // probe, a tooltip) sees both
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ElectricRegistries.AETHER_JAR_BLOCK_ENTITY.get(),
                (jar, side) -> side == null ? jar.port() : side == jar.facing() ? jar.output()
                        : side == jar.facing().getOpposite() ? jar.input() : null);
        // a generator takes FE on any side but its front, and fuel on any
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ElectricRegistries.SQUALL_GENERATOR_BLOCK_ENTITY.get(),
                (entity, side) -> entity instanceof SquallGeneratorHost host
                        ? host.core().energy(side, entity.getBlockState().getValue(SquallGeneratorBlock.FACING)) : null);
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ElectricRegistries.SQUALL_GENERATOR_BLOCK_ENTITY.get(),
                (entity, side) -> entity instanceof SquallGeneratorHost host ? host.core().items() : null);
    }

    private ElectricCapabilities() {
    }
}
