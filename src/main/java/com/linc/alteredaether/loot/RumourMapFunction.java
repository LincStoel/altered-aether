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
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
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
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * {@code altered_aether:rumour_map}: turns the item into an explorer's map of a landmark near the chest, centred on
 * it and marked with a red X, like the vanilla treasure maps. The landmark types are tried in a random order and the
 * first with a site within {@code search_radius} blocks wins; with none, the item is dropped rather than left a blank
 * map. Outside the Aether there are no landmarks, so nothing is ever found there.
 */
public final class RumourMapFunction extends LootItemConditionalFunction {
    /** The custom data key that marks a rumour map, holding the landmark type's id; the advancements look for it. */
    public static final String TAG = AlteredAether.MOD_ID;

    public static final MapCodec<RumourMapFunction> CODEC = RecordCodecBuilder.mapCodec(instance -> commonFields(instance)
            .and(instance.group(
                    Codec.STRING.listOf().optionalFieldOf("landmarks", List.of()).forGetter(f -> f.landmarks),
                    Codec.INT.optionalFieldOf("search_radius", 6000).forGetter(f -> f.searchRadius),
                    Codec.BYTE.optionalFieldOf("zoom", (byte) 2).forGetter(f -> f.zoom)))
            .apply(instance, RumourMapFunction::new));

    private final List<String> landmarks;
    private final int searchRadius;
    private final byte zoom;

    private RumourMapFunction(List<LootItemCondition> conditions, List<String> landmarks, int searchRadius,
                              byte zoom) {
        super(conditions);
        this.landmarks = landmarks;
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
        return find(field, types(), (int) origin.x, (int) origin.z, searchRadius, context.getRandom())
                .map(site -> create(level, site, zoom))
                .orElse(ItemStack.EMPTY);
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
        for (int i = order.size() - 1; i > 0; i--) {
            order.set(i, order.set(random.nextInt(i + 1), order.get(i)));
        }
        for (LandmarkType type : order) {
            Optional<Landmark> site = field.nearest(type, x, z, radius)
                    .filter(found -> Math.hypot(found.x() - x, found.z() - z) <= radius);
            if (site.isPresent()) {
                return site;
            }
        }
        return Optional.empty();
    }

    /** A filled map centred on the landmark, with a red X on it and the landmark's name. */
    public static ItemStack create(ServerLevel level, Landmark site, byte zoom) {
        ItemStack map = MapItem.create(level, site.x(), site.z(), zoom, true, true);
        MapItemSavedData.addTargetDecoration(map, new BlockPos(site.x(), 0, site.z()), "+", MapDecorationTypes.RED_X);
        String id = site.type().id();
        map.set(DataComponents.ITEM_NAME, Component.translatable("item." + AlteredAether.MOD_ID + ".rumour_map." + id));
        CompoundTag tag = new CompoundTag();
        CompoundTag rumour = new CompoundTag();
        rumour.putString("rumour", id);
        tag.put(TAG, rumour);
        CustomData.set(DataComponents.CUSTOM_DATA, map, tag);
        return map;
    }
}
