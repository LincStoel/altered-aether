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

import com.linc.alteredaether.compat.GravititeRework;
import com.linc.alteredaether.registry.AARegistries;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemConditionType;

/**
 * Holds while the gravitite rework is on (see GravititeRework). Our own loot gives enchanted gravitite ingots with it
 * and the Aether's enchanted gravitite blocks without it, from one table.
 */
public record GravititeReworkCondition() implements LootItemCondition {
    public static final GravititeReworkCondition INSTANCE = new GravititeReworkCondition();
    public static final MapCodec<GravititeReworkCondition> CODEC = MapCodec.unit(INSTANCE);

    @Override
    public LootItemConditionType getType() {
        return AARegistries.GRAVITITE_REWORK_CONDITION.get();
    }

    @Override
    public boolean test(LootContext context) {
        return GravititeRework.active();
    }
}
