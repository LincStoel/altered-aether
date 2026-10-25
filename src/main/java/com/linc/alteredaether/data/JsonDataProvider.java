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
package com.linc.alteredaether.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Writes hand-built JSON files. Worldgen is generated this way rather than through RegistrySetBuilder because nearly
 * everything references The Aether's and Deep Aether's worldgen entries, which are plain JSON in their jars and so
 * absent from datagen's registry lookup.
 */
public abstract class JsonDataProvider implements DataProvider {
    private final PackOutput output;
    private final String name;
    private final Map<Path, JsonElement> files = new LinkedHashMap<>();

    protected JsonDataProvider(PackOutput output, String name) {
        this.output = output;
        this.name = name;
    }

    /** Adds every file this provider owns through {@link #data} and {@link #asset}. */
    protected abstract void generate();

    /**
     * Adds a data file. {@code requiredMods} become a {@code neoforge:mod_loaded} condition on the file, so it only
     * loads when all of them are present.
     */
    protected void data(String folder, ResourceLocation id, JsonObject json, String... requiredMods) {
        add(PackOutput.Target.DATA_PACK, folder, id, json, requiredMods);
    }

    /** Adds a file at the pack root, such as pack.mcmeta. */
    protected void root(String fileName, JsonObject json) {
        Path path = output.getOutputFolder().resolve(fileName);
        if (files.put(path, json) != null) {
            throw new IllegalStateException("Duplicate data file " + path);
        }
    }

    protected void asset(String folder, ResourceLocation id, JsonObject json) {
        add(PackOutput.Target.RESOURCE_PACK, folder, id, json);
    }

    private void add(PackOutput.Target target, String folder, ResourceLocation id, JsonObject json,
                     String... requiredMods) {
        if (requiredMods.length > 0) {
            json.add("neoforge:conditions", modsLoaded(requiredMods));
        }
        Path path = output.getOutputFolder(target).resolve(id.getNamespace()).resolve(folder)
                .resolve(id.getPath() + ".json");
        if (files.put(path, json) != null) {
            throw new IllegalStateException("Duplicate data file " + path);
        }
    }

    private static JsonArray modsLoaded(String... mods) {
        JsonArray conditions = new JsonArray();
        for (String mod : mods) {
            JsonObject condition = new JsonObject();
            condition.addProperty("type", "altered_aether:mod_enabled");
            condition.addProperty("modid", mod);
            conditions.add(condition);
        }
        return conditions;
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        files.clear();
        generate();
        List<CompletableFuture<?>> writes = new ArrayList<>(files.size());
        files.forEach((path, json) -> writes.add(DataProvider.saveStable(cache, json, path)));
        return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new));
    }

    @Override
    public String getName() {
        return "Altered Aether " + name;
    }
}
