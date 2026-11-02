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

import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.entity.PartEntity;

/**
 * One slab of a tamed aerwhale's back that players can stand and walk on. Its whale places it along its body every
 * tick (see {@link TamedAerwhale}). It is solid only while the whale is a saddled adult, isn't pickable, so clicks and
 * hits go to the whale's own hitbox, and has no state of its own to save or sync.
 */
public final class AerwhaleDeck extends PartEntity<TamedAerwhale> {
    /** How thick each slab is; only its top matters. */
    static final float THICKNESS = 0.5F;

    private final EntityDimensions size;

    AerwhaleDeck(TamedAerwhale whale, float width) {
        super(whale);
        this.size = EntityDimensions.scalable(width, THICKNESS);
        refreshDimensions();
    }

    /** Whether a deck slab of a walkable whale lies within {@code box}: a place to stand that isn't a block. */
    public static boolean anyUnder(Entity entity, AABB box) {
        return !entity.level().getEntities(entity, box,
                other -> other instanceof AerwhaleDeck deck && deck.canBeCollidedWith()).isEmpty();
    }

    /**
     * {@code collisions} plus the slabs {@code entity} would hit within {@code box}, chosen as vanilla's entity
     * collision lookup would choose them. Returns {@code collisions} itself when there are none.
     */
    public static List<VoxelShape> addCollisions(Entity entity, AABB box, List<VoxelShape> collisions) {
        if (entity instanceof PartEntity<?> || box.getSize() < 1.0E-7) {
            return collisions;
        }
        List<VoxelShape> withDecks = null;
        for (PartEntity<?> part : entity.level().getPartEntities()) {
            if (part instanceof AerwhaleDeck slab && slab.getParent() != entity
                    && slab.getBoundingBox().intersects(box) && entity.canCollideWith(slab)) {
                if (withDecks == null) {
                    withDecks = new ArrayList<>(collisions);
                }
                withDecks.add(Shapes.create(slab.getBoundingBox()));
            }
        }
        return withDecks != null ? withDecks : collisions;
    }

    @Override
    public boolean canBeCollidedWith() {
        return getParent().hasDeck();
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean is(Entity entity) {
        return this == entity || getParent() == entity;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return size;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }
}
