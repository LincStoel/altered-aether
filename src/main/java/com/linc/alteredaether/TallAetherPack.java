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
package com.linc.alteredaether;

import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.BuiltInPackSource;
import net.neoforged.fml.ModList;

import java.util.Optional;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddPackFindersEvent;

/**
 * The built-in "Tall Aether" datapack: the Aether at -64..319 (like the Overworld) instead of 0..255, with the island
 * band stretched downward for deeper islands. Built-in packs are enabled automatically, so it is on unless a player
 * turns it off in the Data Packs screen when creating a world. Height is fixed when a world's Aether is first
 * generated, so the choice belongs at world creation.
 */
@EventBusSubscriber(modid = AlteredAether.MOD_ID)
public final class TallAetherPack {
    /** Where the pack lives inside the mod's resources. */
    public static final String PATH = "datapacks/tall_aether";

    @SubscribeEvent
    public static void addPack(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.SERVER_DATA) {
            return;
        }
        // Built by hand rather than with event.addPackFinders, which marks the pack as one the client also has. A
        // client then takes registry entries from such a pack out of its own copy of the data instead of the
        // server's, and its own copy puts the Aether's stock dimension type on top: it would think the Aether ends
        // at 256 and draw nothing above. With no known-pack identity, the server sends these entries in full.
        var file = ModList.get().getModContainerById(AlteredAether.MOD_ID).orElseThrow().getModInfo().getOwningFile()
                .getFile();
        var root = file.findResource(PATH);
        Pack pack = Pack.readMetaAndCreate(
                new PackLocationInfo("mod/" + AlteredAether.id(PATH),
                        Component.translatableWithFallback("pack.altered_aether.tall_aether", "Altered Aether: Tall Aether"),
                        PackSource.BUILT_IN, Optional.empty()),
                BuiltInPackSource.fromName(path -> new PathPackResources(path, root)),
                PackType.SERVER_DATA, new PackSelectionConfig(false, Pack.Position.TOP, false));
        if (pack != null) {
            event.addRepositorySource(consumer -> consumer.accept(pack));
        }
    }

    private TallAetherPack() {
    }
}
