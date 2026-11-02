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

import com.linc.alteredaether.entity.TamedAerwhale;
import com.mojang.serialization.MapCodec;
import net.minecraft.advancements.critereon.EntitySubPredicate;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * An entity predicate's {@code type_specific} test for an aerwhale that belongs to someone: tamed, not a wild one being
 * broken in or a wild calf. Written {@code {"type": "altered_aether:owned_aerwhale"}}.
 */
public record OwnedAerwhalePredicate() implements EntitySubPredicate {
    public static final MapCodec<OwnedAerwhalePredicate> CODEC = MapCodec.unit(new OwnedAerwhalePredicate());

    @Override
    public MapCodec<OwnedAerwhalePredicate> codec() {
        return CODEC;
    }

    @Override
    public boolean matches(Entity entity, ServerLevel level, @Nullable Vec3 position) {
        return entity instanceof TamedAerwhale whale && whale.getOwnerUUID() != null;
    }
}
