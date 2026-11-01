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
package com.linc.alteredaether.worldgen.camp;

import com.linc.alteredaether.AlteredAether;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The hand-built structures in {@code data/altered_aether/structure/camp/}, read on every data reload. They are read
 * here, not through the structure template manager, because that one turns a block it does not know into air; these
 * choose a stand-in for it first ({@link Fallbacks}). A data pack can replace any of them or add variants.
 */
public final class Blueprints extends SimplePreparableReloadListener<Map<String, CompoundTag>> {
    private static final String FOLDER = "structure/camp";
    private static volatile Map<String, Blueprint> loaded = Map.of();

    /** One blueprint by file name, without the extension. */
    @Nullable
    public static Blueprint get(String name) {
        return loaded.get(name);
    }

    /** The blueprints named {@code group}, or {@code group_1}, {@code group_2} and so on, in name order. */
    public static List<Blueprint> group(String group) {
        List<Blueprint> out = new ArrayList<>();
        loaded.forEach((name, blueprint) -> {
            if (name.equals(group) || name.startsWith(group + "_") && name.substring(group.length() + 1).chars()
                    .allMatch(Character::isDigit)) {
                out.add(blueprint);
            }
        });
        return out;
    }

    @Override
    protected Map<String, CompoundTag> prepare(ResourceManager manager, ProfilerFiller profiler) {
        Map<String, CompoundTag> tags = new TreeMap<>();
        for (Map.Entry<ResourceLocation, Resource> entry : manager.listResources(FOLDER,
                id -> id.getPath().endsWith(".nbt")).entrySet()) {
            ResourceLocation id = entry.getKey();
            if (!id.getNamespace().equals(AlteredAether.MOD_ID)) {
                continue;
            }
            String path = id.getPath();
            String name = path.substring(FOLDER.length() + 1, path.length() - ".nbt".length());
            try (InputStream in = entry.getValue().open()) {
                tags.put(name, NbtIo.readCompressed(in, NbtAccounter.unlimitedHeap()));
            } catch (IOException | RuntimeException e) {
                AlteredAether.LOGGER.error("Could not read the structure {}", id, e);
            }
        }
        return tags;
    }

    @Override
    protected void apply(Map<String, CompoundTag> tags, ResourceManager manager, ProfilerFiller profiler) {
        Map<String, Blueprint> read = new TreeMap<>();
        tags.forEach((name, tag) -> {
            try {
                read.put(name, Blueprint.read(name, tag));
            } catch (RuntimeException e) {
                AlteredAether.LOGGER.error("Could not use the structure {}", name, e);
            }
        });
        loaded = Map.copyOf(read);
        AlteredAether.LOGGER.info("Read {} camp structures", read.size());
    }

    @EventBusSubscriber(modid = AlteredAether.MOD_ID)
    public static final class Events {
        @SubscribeEvent
        public static void onReload(AddReloadListenerEvent event) {
            event.addListener(new Blueprints());
        }

        private Events() {
        }
    }
}
