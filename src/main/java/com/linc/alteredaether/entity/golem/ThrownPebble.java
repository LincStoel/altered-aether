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
package com.linc.alteredaether.entity.golem;

import com.linc.alteredaether.registry.AARegistries;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * A storage pebble in the air, thrown like a snowball. A live one, being gravitite, falls upwards; an inert one (dull,
 * or thrown out of reach of its network) falls like any stone. It shatters on whatever it hits, a golem too, and
 * bursts when it leaves the world's height, top or bottom.
 */
public class ThrownPebble extends ThrowableItemProjectile {
    private static final EntityDataAccessor<Boolean> DATA_INERT = SynchedEntityData.defineId(ThrownPebble.class,
            EntityDataSerializers.BOOLEAN);

    public ThrownPebble(EntityType<? extends ThrownPebble> type, Level level) {
        super(type, level);
    }

    public ThrownPebble(Level level, LivingEntity thrower) {
        super(AARegistries.THROWN_PEBBLE.get(), thrower, level);
    }

    @Override
    protected Item getDefaultItem() {
        return AARegistries.STORAGE_PEBBLE.get();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_INERT, false);
    }

    public boolean isInert() {
        return entityData.get(DATA_INERT);
    }

    public void setInert(boolean inert) {
        entityData.set(DATA_INERT, inert);
    }

    /** A snowball's pull: upside down for a live pebble. */
    @Override
    protected double getDefaultGravity() {
        return isInert() ? 0.03 : -0.03;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("inert", isInert());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setInert(tag.getBoolean("inert"));
    }

    @Override
    public void tick() {
        super.tick();
        if (level() instanceof ServerLevel server && !isRemoved()
                && (getY() >= server.getMaxBuildHeight() || getY() < server.getMinBuildHeight())) {
            burst(server);
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        result.getEntity().hurt(damageSources().thrown(this, getOwner()), 0);
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (level() instanceof ServerLevel server && !isRemoved()) {
            burst(server);
        }
    }

    /** Shatters: chips of the pebble, a puff of gravitite, and it is gone. */
    private void burst(ServerLevel level) {
        level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, getItem()), getX(), getY(), getZ(), 8,
                0.1, 0.1, 0.1, 0.08);
        var link = getItem().get(AARegistries.PEBBLE_LINK.get());
        level.sendParticles(new DustParticleOptions(GravititeGolem.mote(
                        link == null ? GravititeGolem.DEFAULT_COLOR : link.color(), false), 1.0F), getX(), getY(), getZ(), 6,
                0.15, 0.15, 0.15, 0);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.NEUTRAL, 0.8F,
                1.3F + random.nextFloat() * 0.3F);
        discard();
    }
}
