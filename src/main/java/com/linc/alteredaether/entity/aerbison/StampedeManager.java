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
package com.linc.alteredaether.entity.aerbison;

import com.linc.alteredaether.AlteredAether;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Ticks the stampedes in each level once a tick, whichever bison is leading. */
@EventBusSubscriber(modid = AlteredAether.MOD_ID)
public final class StampedeManager {
    private static final List<Stampede> ACTIVE = new ArrayList<>();

    static void add(Stampede stampede) {
        ACTIVE.add(stampede);
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        for (Iterator<Stampede> it = ACTIVE.iterator(); it.hasNext(); ) {
            Stampede stampede = it.next();
            if (stampede.level != level) {
                continue;
            }
            if (stampede.finished()) {
                it.remove();
                continue;
            }
            stampede.tick();
            if (stampede.finished()) {
                it.remove();
            }
        }
    }

    @SubscribeEvent
    public static void onStopping(ServerStoppingEvent event) {
        ACTIVE.clear();
    }

    private StampedeManager() {
    }
}
