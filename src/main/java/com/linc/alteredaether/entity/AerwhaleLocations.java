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
package com.linc.alteredaether.entity;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Which chunk of which dimension each owned aerwhale was last in, so a whale whistle can load it there and call it
 * from however far away it was left. Each whale reports itself as it crosses into a new chunk.
 */
public final class AerwhaleLocations extends SavedData {
    private static final String NAME = "altered_aether_aerwhales";
    private static final Factory<AerwhaleLocations> FACTORY = new Factory<>(AerwhaleLocations::new, AerwhaleLocations::load);

    public record Location(ResourceKey<Level> dimension, ChunkPos chunk) {
    }

    private final Map<UUID, Location> whales = new HashMap<>();

    public static AerwhaleLocations get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    @Nullable
    public Location find(UUID whale) {
        return whales.get(whale);
    }

    public void put(UUID whale, ResourceKey<Level> dimension, ChunkPos chunk) {
        Location location = new Location(dimension, chunk);
        if (!location.equals(whales.put(whale, location))) {
            setDirty();
        }
    }

    public void remove(UUID whale) {
        if (whales.remove(whale) != null) {
            setDirty();
        }
    }

    private static AerwhaleLocations load(CompoundTag tag, HolderLookup.Provider registries) {
        AerwhaleLocations locations = new AerwhaleLocations();
        ListTag list = tag.getList("Whales", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            ResourceLocation dimension = ResourceLocation.tryParse(entry.getString("Dimension"));
            if (entry.hasUUID("Whale") && dimension != null) {
                locations.whales.put(entry.getUUID("Whale"), new Location(
                        ResourceKey.create(Registries.DIMENSION, dimension), new ChunkPos(entry.getLong("Chunk"))));
            }
        }
        return locations;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        whales.forEach((whale, location) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Whale", whale);
            entry.putString("Dimension", location.dimension().location().toString());
            entry.putLong("Chunk", location.chunk().toLong());
            list.add(entry);
        });
        tag.put("Whales", list);
        return tag;
    }
}
