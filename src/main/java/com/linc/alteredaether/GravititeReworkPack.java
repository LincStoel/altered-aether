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

import com.linc.alteredaether.compat.GravititeRework;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.BuiltInPackSource;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddPackFindersEvent;

import java.util.Optional;

/**
 * The built-in "Gravitite Rework" datapack (see compat/GravititeRework): every change to the Aether's gravitite that
 * can be data. It is required, so it is always on while {@code gravitite.rework} is; that switch, not the Data Packs
 * screen, is how to turn it off. It sits on top, so its tags and its trim material win over the Aether's.
 */
@EventBusSubscriber(modid = AlteredAether.MOD_ID)
public final class GravititeReworkPack {
    /** Where the pack lives inside the mod's resources. */
    public static final String PATH = "datapacks/gravitite_rework";

    @SubscribeEvent
    public static void addPack(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.SERVER_DATA) {
            return;
        }
        // Built by hand, as TallAetherPack is, so the server sends its entries rather than the client using its own.
        var file = ModList.get().getModContainerById(AlteredAether.MOD_ID).orElseThrow().getModInfo().getOwningFile()
                .getFile();
        var root = file.findResource(PATH);
        Pack pack = Pack.readMetaAndCreate(
                new PackLocationInfo("mod/" + AlteredAether.id(PATH),
                        Component.translatableWithFallback("pack.altered_aether.gravitite_rework",
                                "Altered Aether: Gravitite Rework"),
                        PackSource.BUILT_IN, Optional.empty()),
                BuiltInPackSource.fromName(path -> new PathPackResources(path, root)),
                PackType.SERVER_DATA, new PackSelectionConfig(true, Pack.Position.TOP, false));
        if (pack != null) {
            // read when the packs are listed (each time a world loads), not once at startup
            event.addRepositorySource(consumer -> {
                if (GravititeRework.active()) {
                    consumer.accept(pack);
                }
            });
        }
    }

    private GravititeReworkPack() {
    }
}
