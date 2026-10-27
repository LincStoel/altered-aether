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
package com.linc.alteredaether.advancement;

import com.linc.alteredaether.AlteredAether;
import com.linc.alteredaether.registry.AARegistries;
import com.linc.alteredaether.worldgen.basin.BasinField;
import com.linc.alteredaether.worldgen.landmark.Landmark;
import com.linc.alteredaether.worldgen.landmark.LandmarkField;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Once a second, tells the {@link VisitLandmarkTrigger} which landmarks each player in the Aether stands within. The
 * landmarks round a player were all worked out when their chunks generated, so the lookups are cache hits.
 */
@EventBusSubscriber(modid = AlteredAether.MOD_ID)
public final class LandmarkVisits {
    /** The trigger's name for any atoll's lagoon or shore, which is not a landmark of its own. */
    public static final String ATOLL = "atoll";
    private static final ResourceKey<Level> THE_AETHER =
            ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath("aether", "the_aether"));
    private static final int INTERVAL = 20;
    /** How far onto an atoll's shore still counts as having found it. */
    private static final double SHORE = 6;

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !level.dimension().equals(THE_AETHER)) {
            return;
        }
        LandmarkField landmarks = LandmarkField.get(THE_AETHER);
        BasinField basins = BasinField.get(THE_AETHER);
        if (landmarks == null && basins == null) {
            return;
        }
        long time = level.getGameTime();
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator() || (time + player.getId()) % INTERVAL != 0) {
                continue;
            }
            int x = player.getBlockX(), z = player.getBlockZ();
            if (landmarks != null) {
                for (Landmark site : landmarks.touching(x, z, x, z)) {
                    AARegistries.VISIT_LANDMARK.get().trigger(player, site.type().id());
                }
            }
            if (basins != null && basins.nearLagoon(x, z, SHORE)) {
                AARegistries.VISIT_LANDMARK.get().trigger(player, ATOLL);
            }
        }
    }

    private LandmarkVisits() {
    }
}
