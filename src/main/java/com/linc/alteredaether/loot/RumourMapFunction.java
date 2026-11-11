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
package com.linc.alteredaether.loot;

import com.linc.alteredaether.AlteredAether;
import com.linc.alteredaether.registry.AARegistries;
import com.linc.alteredaether.worldgen.landmark.Landmark;
import com.linc.alteredaether.worldgen.landmark.LandmarkField;
import com.linc.alteredaether.worldgen.landmark.LandmarkType;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.LootItemConditionalFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctionType;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * {@code altered_aether:rumour_map}: turns the item into an explorer's map of a landmark near the chest, centred on
 * it and marked with a red X, like the vanilla treasure maps. The landmark types are tried in a random order and the
 * first with a site within {@code search_radius} blocks wins; with none, the item is dropped rather than left a blank
 * map. The listed biomes (the archwood groves) are candidates too, found by a biome search from the chest's
 * {@link #BIOME_CELL} cell and marked at the nearest spot of them; ones whose mod is absent aren't registered and are
 * skipped. Outside the Aether there are no landmarks, so nothing is ever found there.
 */
public final class RumourMapFunction extends LootItemConditionalFunction {
    /** The custom data key that marks a rumour map, holding the landmark type's id; the advancements look for it. */
    public static final String TAG = AlteredAether.MOD_ID;
    /** How far a rumour reaches, in blocks, unless a loot table says otherwise. */
    public static final int DEFAULT_RADIUS = 6000;
    /** Ars Nouveau's archwood groves, which a rumour can lead to as well; registered only when Ars is loaded. */
    public static final List<ResourceLocation> ARCHWOOD_GROVES = java.util.stream.Stream.of("blazing", "cascading",
            "flashing", "flourishing", "vexing").map(grove -> AlteredAether.id(grove + "_archwood_grove")).toList();
    /**
     * The grid grove searches are made and remembered on, in blocks: every chest in a cell searches from the cell's
     * centre, so {@link RumourWarmup} can search for it ahead of time.
     */
    public static final int BIOME_CELL = 512;
    /** Grove searches already made: the nearest spot of the biome to the cell's centre, if any is in range. */
    private static final Cache<BiomeSearch, Optional<BlockPos>> BIOME_SEARCHES =
            CacheBuilder.newBuilder().maximumSize(1 << 12).build();

    private record BiomeSearch(ResourceKey<Level> dimension, ResourceLocation biome, int radius, int cx, int cz) {
    }
    /** A landmark type's id, checked as the loot table loads rather than failing each time a chest is opened. */
    private static final Codec<String> LANDMARK_ID = Codec.STRING.validate(id ->
            Arrays.stream(LandmarkType.values()).anyMatch(type -> type.id().equals(id))
                    ? DataResult.success(id) : DataResult.error(() -> "Unknown landmark type: " + id));

    public static final MapCodec<RumourMapFunction> CODEC = RecordCodecBuilder.mapCodec(instance -> commonFields(instance)
            .and(instance.group(
                    LANDMARK_ID.listOf().optionalFieldOf("landmarks", List.of()).forGetter(f -> f.landmarks),
                    ResourceLocation.CODEC.listOf().optionalFieldOf("biomes", List.of()).forGetter(f -> f.biomes),
                    Codec.INT.optionalFieldOf("search_radius", DEFAULT_RADIUS).forGetter(f -> f.searchRadius),
                    Codec.BYTE.optionalFieldOf("zoom", (byte) 2).forGetter(f -> f.zoom)))
            .apply(instance, RumourMapFunction::new));

    private final List<String> landmarks;
    private final List<ResourceLocation> biomes;
    private final int searchRadius;
    private final byte zoom;

    private RumourMapFunction(List<LootItemCondition> conditions, List<String> landmarks,
                              List<ResourceLocation> biomes, int searchRadius, byte zoom) {
        super(conditions);
        this.landmarks = landmarks;
        this.biomes = biomes;
        this.searchRadius = searchRadius;
        this.zoom = zoom;
    }

    @Override
    public LootItemFunctionType<RumourMapFunction> getType() {
        return AARegistries.RUMOUR_MAP.get();
    }

    @Override
    protected ItemStack run(ItemStack stack, LootContext context) {
        ServerLevel level = context.getLevel();
        Vec3 origin = context.getParamOrNull(LootContextParams.ORIGIN);
        LandmarkField field = LandmarkField.get(level.dimension());
        if (origin == null || field == null) {
            return ItemStack.EMPTY;
        }
        int x = Mth.floor(origin.x), z = Mth.floor(origin.z);
        RandomSource random = context.getRandom();
        // Landmarks and biomes are tried together in a random order, so a grove is as likely a rumour as a landmark
        List<Object> order = new ArrayList<>(types());
        for (ResourceLocation biome : biomes) {
            if (registered(level, biome)) {
                order.add(biome);
            }
        }
        shuffle(order, random);
        for (Object kind : order) {
            if (kind instanceof LandmarkType type) {
                Optional<Landmark> site = find(field, List.of(type), x, z, searchRadius, random);
                if (site.isPresent()) {
                    return create(level, site.get(), zoom);
                }
            } else {
                ResourceLocation biome = (ResourceLocation) kind;
                if (level.getBiome(BlockPos.containing(origin)).is(biome)) {
                    continue;
                }
                Optional<BlockPos> found = nearestBiome(level, biome, x, z, searchRadius);
                if (found.isPresent()) {
                    return create(level, biome.getPath(), found.get().getX(), found.get().getZ(), zoom);
                }
            }
        }
        return ItemStack.EMPTY;
    }

    private static void shuffle(List<?> list, RandomSource random) {
        Collections.shuffle(list, new java.util.Random(random.nextLong()));
    }

    private List<LandmarkType> types() {
        if (landmarks.isEmpty()) {
            return Arrays.asList(LandmarkType.values());
        }
        return landmarks.stream().map(id -> LandmarkType.valueOf(id.toUpperCase(Locale.ROOT))).toList();
    }

    /** A landmark of one of the types within {@code radius} blocks, the types tried in a random order. */
    public static Optional<Landmark> find(LandmarkField field, List<LandmarkType> types, int x, int z, int radius,
                                          RandomSource random) {
        List<LandmarkType> order = new ArrayList<>(types);
        shuffle(order, random);
        for (LandmarkType type : order) {
            Optional<Landmark> site = nearest(field, type, x, z, radius);
            if (site.isPresent()) {
                return site;
            }
        }
        return Optional.empty();
    }

    /**
     * The landmark of a type a rumour from (x, z) would lead to, if there's one within {@code radius} blocks. Also run
     * ahead of time by {@link RumourWarmup}, so that by the time a chest is opened its search is all cache hits.
     */
    public static Optional<Landmark> nearest(LandmarkField field, LandmarkType type, int x, int z, int radius) {
        // The landmark the chest is in doesn't count, or it would always be the nearest
        return field.nearest(type, x, z, radius, here -> Math.hypot(here.x() - x, here.z() - z) > here.extent())
                .filter(found -> Math.hypot(found.x() - x, found.z() - z) <= radius);
    }

    /** Whether a biome exists in this world: the archwood groves only do with Ars Nouveau. */
    public static boolean registered(ServerLevel level, ResourceLocation biome) {
        return level.registryAccess().registryOrThrow(Registries.BIOME).containsKey(biome);
    }

    /**
     * The spot of a biome a rumour from (x, z) would lead to, searched for within half of {@code radius} blocks of the
     * centre of the {@link #BIOME_CELL} cell holding (x, z). A biome search scans the climate over thousands of points,
     * up to about 80 ms, so each is made once per cell and remembered; {@link RumourWarmup} makes them ahead of time.
     */
    public static Optional<BlockPos> nearestBiome(ServerLevel level, ResourceLocation biome, int x, int z, int radius) {
        int cx = Math.floorDiv(x, BIOME_CELL), cz = Math.floorDiv(z, BIOME_CELL);
        try {
            return BIOME_SEARCHES.get(new BiomeSearch(level.dimension(), biome, radius, cx, cz), () -> {
                BlockPos centre = new BlockPos(cx * BIOME_CELL + BIOME_CELL / 2, level.getSeaLevel(),
                        cz * BIOME_CELL + BIOME_CELL / 2);
                Pair<BlockPos, Holder<Biome>> found = level.findClosestBiome3d(holder -> holder.is(biome), centre,
                        radius / 2, 64, 96);
                return Optional.ofNullable(found).map(Pair::getFirst);
            });
        } catch (java.util.concurrent.ExecutionException e) {
            throw new IllegalStateException(e.getCause());
        }
    }

    /** Forgets the grove searches, which belong to the world they were made in. */
    static void forgetBiomeSearches() {
        BIOME_SEARCHES.invalidateAll();
    }

    /** A filled map centred on the landmark, with a red X on it and the landmark's name. */
    public static ItemStack create(ServerLevel level, Landmark site, byte zoom) {
        return create(level, site.type().id(), site.x(), site.z(), zoom);
    }

    /** A filled map centred on a place, with a red X on it and the name that goes with the id. */
    public static ItemStack create(ServerLevel level, String id, int x, int z, byte zoom) {
        ItemStack map = MapItem.create(level, x, z, zoom, true, true);
        MapItemSavedData.addTargetDecoration(map, new BlockPos(x, 0, z), "+", MapDecorationTypes.RED_X);
        map.set(DataComponents.ITEM_NAME, Component.translatable("item." + AlteredAether.MOD_ID + ".rumour_map." + id));
        CompoundTag tag = new CompoundTag();
        CompoundTag rumour = new CompoundTag();
        rumour.putString("rumour", id);
        tag.put(TAG, rumour);
        CustomData.set(DataComponents.CUSTOM_DATA, map, tag);
        return map;
    }
}
