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
package com.linc.alteredaether.compat.boulder;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The falling boulders in a level, by their sub-level's id, so one that Sable puts away when its chunks unload (and
 * loads again later) is still settled, and still passes through the end sea, when it comes back.
 */
public final class BoulderRecords extends SavedData {
    private static final String ID = "altered_aether_falling_boulders";

    /** What is known of one boulder. */
    public static final class Entry {
        /** The game time it was spawned at. */
        final long started;
        /** The block count at spawn: a different count later means someone has taken it apart or added to it. */
        final int blocks;
        /** Set once it has been seen moving, so the zero speed of a body that has just been made isn't read as rest. */
        boolean moved;
        /** How many ticks in a row it has been still; not saved. */
        int restTicks;
        /** Its velocity a tick ago, to see when something has stopped it short; not saved. */
        org.joml.Vector3d previous;

        Entry(long started, int blocks, boolean moved) {
            this.started = started;
            this.blocks = blocks;
            this.moved = moved;
        }
    }

    private final Map<UUID, Entry> entries = new LinkedHashMap<>();

    public static BoulderRecords get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(BoulderRecords::new, BoulderRecords::load), ID);
    }

    Map<UUID, Entry> entries() {
        return entries;
    }

    /**
     * How many boulders started within the last {@code ticks}. One left in land nobody has been to since counts for a
     * while and then not, so it can't hold up new ones for good.
     */
    int recent(long now, int ticks) {
        int count = 0;
        for (Entry entry : entries.values()) {
            if (now - entry.started < ticks) {
                count++;
            }
        }
        return count;
    }

    void add(UUID id, long started, int blocks) {
        entries.put(id, new Entry(started, blocks, false));
        setDirty();
    }

    void remove(UUID id) {
        if (entries.remove(id) != null) {
            setDirty();
        }
    }

    void markMoved(Entry entry) {
        if (!entry.moved) {
            entry.moved = true;
            setDirty();
        }
    }

    private static BoulderRecords load(CompoundTag tag, HolderLookup.Provider registries) {
        BoulderRecords records = new BoulderRecords();
        for (Tag raw : tag.getList("boulders", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) raw;
            records.entries.put(NbtUtils.loadUUID(entry.get("id")),
                    new Entry(entry.getLong("started"), entry.getInt("blocks"), entry.getBoolean("moved")));
        }
        return records;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Map.Entry<UUID, Entry> entry : entries.entrySet()) {
            CompoundTag item = new CompoundTag();
            item.put("id", NbtUtils.createUUID(entry.getKey()));
            item.putLong("started", entry.getValue().started);
            item.putInt("blocks", entry.getValue().blocks);
            item.putBoolean("moved", entry.getValue().moved);
            list.add(item);
        }
        tag.put("boulders", list);
        return tag;
    }
}
