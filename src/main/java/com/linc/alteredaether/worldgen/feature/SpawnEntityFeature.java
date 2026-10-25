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
package com.linc.alteredaether.worldgen.feature;

import com.linc.alteredaether.config.AACommonConfig;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.level.levelgen.blockpredicates.BlockPredicate;

import java.util.Optional;

/**
 * Puts a small group of one mob into the world as a chunk is generated, so a place is populated the first time
 * anyone sees it. This skips {@code SpawnPlacements} entirely, which is the point: several mods' ground mobs insist
 * on a vanilla grass block by identity and can never pass their own spawn rule in the Aether. It does run
 * {@code finalizeSpawn} with the chunk-generation spawn type, as vanilla's own spawns at generation do.
 *
 * <p>Each member of the group is dropped within {@code spread} blocks of the origin, onto the first standing spot the
 * entity fits: on {@code ground} with open air above for a land mob, or on the surface of water for {@code water}.
 * Never in leaves, never inside a block, and always within 4 blocks of the origin's height, so a group stays on one
 * piece of ground. If the entity's mod isn't loaded, or its config entry is disabled, nothing happens.
 */
public final class SpawnEntityFeature extends Feature<SpawnEntityFeature.Config> {
    public enum Medium implements StringRepresentable {
        GROUND("ground"), WATER("water");

        public static final Codec<Medium> CODEC = StringRepresentable.fromEnum(Medium::values);
        private final String name;

        Medium(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    /**
     * @param mob         the key of this mob's entry under {@code [spawns]} in the common config, if it has one; the
     *                    entry's {@code enabled} switch applies
     * @param scaled      whether {@code features.familiar_density} scales the group size
     * @param persistent  whether the mob is kept from despawning
     */
    public record Config(ResourceLocation entity, IntProvider count, int spread, Medium medium,
                         Optional<BlockPredicate> ground, boolean persistent, Optional<String> mob,
                         boolean scaled) implements FeatureConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.fieldOf("entity").forGetter(Config::entity),
                IntProvider.codec(1, 16).optionalFieldOf("count", net.minecraft.util.valueproviders.ConstantInt.of(1))
                        .forGetter(Config::count),
                Codec.intRange(0, 12).optionalFieldOf("spread", 4).forGetter(Config::spread),
                Medium.CODEC.optionalFieldOf("medium", Medium.GROUND).forGetter(Config::medium),
                BlockPredicate.CODEC.optionalFieldOf("ground").forGetter(Config::ground),
                Codec.BOOL.optionalFieldOf("persistent", true).forGetter(Config::persistent),
                Codec.STRING.optionalFieldOf("mob").forGetter(Config::mob),
                Codec.BOOL.optionalFieldOf("scaled", true).forGetter(Config::scaled)
        ).apply(i, Config::new));
    }

    public SpawnEntityFeature() {
        super(Config.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<Config> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        Config config = context.config();
        Optional<EntityType<?>> type = BuiltInRegistries.ENTITY_TYPE.getOptional(config.entity());
        if (type.isEmpty()) {
            return false;
        }
        if (config.mob().isPresent()) {
            AACommonConfig.MobSettings settings = AACommonConfig.mob(config.mob().get());
            if (settings != null && !settings.enabled().get()) {
                return false;
            }
        }

        double scale = config.scaled() ? AACommonConfig.FAMILIAR_DENSITY.get() : 1;
        double wanted = config.count().sample(random) * scale;
        int count = (int) wanted + (random.nextDouble() < wanted - (int) wanted ? 1 : 0);
        int spawned = 0;
        for (int i = 0; i < count; i++) {
            int x = context.origin().getX() + random.nextInt(config.spread() * 2 + 1) - config.spread();
            int z = context.origin().getZ() + random.nextInt(config.spread() * 2 + 1) - config.spread();
            BlockPos spot = standingSpot(level, config, x, z, context.origin().getY());
            if (spot != null && spawn(level, random, config, type.get(), spot)) {
                spawned++;
            }
        }
        if (spawned > 0) {
            FeatureStats.placed("spawn_entity");
        }
        return spawned > 0;
    }

    /** The air block a mob would stand in at this column, or null when there isn't a suitable one. */
    private static BlockPos standingSpot(WorldGenLevel level, Config config, int x, int z, int originY) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos(x, originY + 4, z);
        for (int y = originY + 4; y >= originY - 4; y--) {
            cursor.setY(y);
            BlockPos below = cursor.below();
            BlockState state = level.getBlockState(cursor);
            BlockState floor = level.getBlockState(below);
            if (config.medium() == Medium.WATER) {
                // the air block above the surface of a water column at least two deep
                if (state.isAir() && floor.getFluidState().is(FluidTags.WATER)
                        && level.getBlockState(below.below()).getFluidState().is(FluidTags.WATER)) {
                    return cursor.immutable();
                }
                continue;
            }
            if (state.isAir() && level.getBlockState(cursor.above()).isAir() && !floor.isAir()
                    && floor.getFluidState().isEmpty() && !floor.is(BlockTags.LEAVES)
                    && (config.ground().isEmpty() || config.ground().get().test(level, below))) {
                return cursor.immutable();
            }
        }
        return null;
    }

    private static boolean spawn(WorldGenLevel level, RandomSource random, Config config, EntityType<?> type, BlockPos pos) {
        Entity entity = type.create(level.getLevel());
        if (entity == null) {
            return false;
        }
        entity.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, random.nextFloat() * 360f, 0f);
        if (!level.noCollision(entity)) {
            entity.discard();
            return false;
        }
        if (entity instanceof Mob mob) {
            mob.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.CHUNK_GENERATION, null);
            if (config.persistent()) {
                mob.setPersistenceRequired();
            }
        }
        level.addFreshEntityWithPassengers(entity);
        return true;
    }
}
