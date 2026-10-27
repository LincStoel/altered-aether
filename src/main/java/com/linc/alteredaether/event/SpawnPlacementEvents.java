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
package com.linc.alteredaether.event;

import com.linc.alteredaether.AATags;
import com.linc.alteredaether.AlteredAether;
import com.linc.alteredaether.compat.AddedMob;
import com.linc.alteredaether.worldgen.basin.BasinField;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacementType;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;

/**
 * Spawn rules for mobs this mod brings into the Aether.
 *
 * <p>Most are Aether-only alternatives ORed onto the mob's own rule: they pass only in {@code aether:the_aether}, so
 * the mob's behaviour everywhere else is untouched. They exist because the original rules are tied to things the
 * Aether doesn't have, mostly sea level (-64 here, far below every island) and vanilla grass.
 */
@EventBusSubscriber(modid = AlteredAether.MOD_ID)
public final class SpawnPlacementEvents {
    private static final ResourceKey<Level> THE_AETHER =
            ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                    ResourceLocation.fromNamespaceAndPath("aether", "the_aether"));
    /** Vanilla's surface-water band: a water mob spawns at most this far below the water's surface. */
    private static final int SURFACE_BAND = 13;
    /** Glow squid keep to water at least this deep, where sunlight has mostly faded. */
    private static final int GLOW_SQUID_DEPTH = 6;
    private static final int GLOW_SQUID_MAX_LIGHT = 7;

    /** Lowest priority, so every other mod's own rules are registered before ours attach to them. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void register(RegisterSpawnPlacementsEvent event) {
        // Vanilla never spawns breezes naturally, so they have no spawn placement. In the Aether they spawn only
        // standing on #altered_aether:breeze_spawnable_on (cold aerclouds). Aerclouds have no solid collision, so
        // the placement type can't be ON_GROUND; the block below is tested directly. Other spawn reasons, such as
        // trial spawners, are left as vanilla had them.
        event.register(EntityType.BREEZE, SpawnPlacementTypes.NO_RESTRICTIONS, Heightmap.Types.MOTION_BLOCKING,
                (type, level, reason, pos, random) -> !isWorldSpawn(reason)
                        || level.getBlockState(pos.below()).is(AATags.BREEZE_SPAWNABLE_ON),
                RegisterSpawnPlacementsEvent.Operation.REPLACE);

        // The Aether registers no spawn placement for valkyries, so the ones Frozen Crags spawns could appear in
        // mid-air or inside aerclouds. ON_GROUND holds them to a solid block with room to stand.
        entity("aether:valkyrie").ifPresent(type -> event.register(type, SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (t, level, reason, pos, random) -> true,
                RegisterSpawnPlacementsEvent.Operation.REPLACE));

        // Lagoon life. The host biomes spawn it (see the lagoon_life biome modifier), but only an atoll's lagoon will
        // have it, so the forests' other water stays empty.
        orInAether(event, EntityType.TROPICAL_FISH, (level, pos) -> surfaceWater(level, pos)
                && nearLagoon(level, pos, 0));
        orInAether(event, EntityType.GLOW_SQUID, (level, pos) -> inWater(level, pos)
                && depthBelowSurface(level, pos) >= GLOW_SQUID_DEPTH
                && level.getRawBrightness(pos, 0) <= GLOW_SQUID_MAX_LIGHT
                && nearLagoon(level, pos, 0));

        // Mowzie's mobs check their own spawn config, which names the overworld, a height band below Y 68 and
        // vanilla ground, so none of them can pass in the Aether. Their light rules are dropped here: the Aether's
        // eternal day would otherwise keep them out until the Sun Spirit falls. Spawn costs keep them rare.
        entity("mowziesmobs:naga").ifPresent(type -> orInAether(event, type, (level, pos) ->
                level.getBlockState(pos.below()).is(AATags.NAGA_SPAWNABLE_ON) && level.canSeeSky(pos)));
        // Lanterns only where we list them: Mowzie's own biome modifier may add them to other Aether forests by tag.
        entity("mowziesmobs:lantern").ifPresent(type -> orInAether(event, type, (level, pos) ->
                level.getBiome(pos).is(AATags.HAS_LANTERNS)
                        && level.getBlockState(pos.below()).is(AATags.LANTERN_SPAWNABLE_ON)));

        // Ars Nouveau's familiars test for minecraft:grass_block by identity, which no Aether soil is. In the Aether
        // they spawn like any passive animal: on Aether soil, in daylight. The siren lives in water.
        for (AddedMob mob : AddedMob.ALL) {
            if (mob.method() != AddedMob.Method.NATURAL_AND_GENERATION) {
                continue;
            }
            boolean water = mob.key().equals("siren");
            entity(mob.entityId()).ifPresent(type -> orInAether(event, type, water
                    ? SpawnPlacementEvents::surfaceWater
                    : (level, pos) -> level.getBlockState(pos.below()).is(AATags.FAMILIAR_SPAWNABLE_ON)
                            && daylit(level, pos),
                    water ? SpawnPlacementTypes.IN_WATER : SpawnPlacementTypes.ON_GROUND));
        }
    }

    @FunctionalInterface
    private interface AetherRule {
        boolean test(ServerLevelAccessor level, BlockPos pos);
    }

    /** Adds {@code rule} as an alternative to the entity's own rule, passing only in the Aether. */
    private static <T extends Entity> void orInAether(RegisterSpawnPlacementsEvent event, EntityType<T> type,
                                                      AetherRule rule) {
        orInAether(event, type, rule, null);
    }

    /**
     * As above. {@code fallbackPlacement} covers a mob that registered no rule at all, which NeoForge can't OR onto:
     * it gets a full placement that applies {@code rule} in the Aether and stays unrestricted everywhere else, which
     * is what having no rule meant.
     */
    private static <T extends Entity> void orInAether(RegisterSpawnPlacementsEvent event, EntityType<T> type,
                                                      AetherRule rule, SpawnPlacementType fallbackPlacement) {
        SpawnPlacements.SpawnPredicate<T> predicate = (t, level, reason, pos, random) -> inAether(level)
                && rule.test(level, pos);
        try {
            event.register(type, predicate, RegisterSpawnPlacementsEvent.Operation.OR);
        } catch (NullPointerException noExistingRule) {
            if (fallbackPlacement == null) {
                throw noExistingRule;
            }
            event.register(type, fallbackPlacement, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    (t, level, reason, pos, random) -> !inAether(level) || rule.test(level, pos),
                    RegisterSpawnPlacementsEvent.Operation.REPLACE);
        }
    }

    private static boolean inAether(ServerLevelAccessor level) {
        return level.getLevel().dimension() == THE_AETHER;
    }

    private static boolean isWorldSpawn(MobSpawnType reason) {
        return reason == MobSpawnType.NATURAL || reason == MobSpawnType.CHUNK_GENERATION;
    }

    /** In an atoll's lagoon, or within {@code margin} blocks of its water. */
    private static boolean nearLagoon(ServerLevelAccessor level, BlockPos pos, double margin) {
        BasinField field = BasinField.get(level.getLevel().dimension());
        return field != null && field.nearLagoon(pos.getX(), pos.getZ(), margin);
    }

    /** Animal.isBrightEnoughToSpawn: the light level passive animals need. */
    private static boolean daylit(LevelAccessor level, BlockPos pos) {
        return level.getRawBrightness(pos, 0) > 8;
    }

    /** Vanilla's surface water rule, measured from the local water surface instead of sea level. */
    private static boolean surfaceWater(ServerLevelAccessor level, BlockPos pos) {
        return inWater(level, pos) && depthBelowSurface(level, pos) <= SURFACE_BAND;
    }

    /** Water below and a water block above, as vanilla requires. */
    private static boolean inWater(LevelAccessor level, BlockPos pos) {
        return level.getFluidState(pos.below()).is(FluidTags.WATER) && level.getBlockState(pos.above()).is(Blocks.WATER);
    }

    /** Water blocks between {@code pos} and open air above it, capped just past the deepest band any rule uses. */
    private static int depthBelowSurface(LevelAccessor level, BlockPos pos) {
        BlockPos.MutableBlockPos cursor = pos.mutable();
        int depth = 0;
        while (depth <= SURFACE_BAND && level.getFluidState(cursor.move(0, 1, 0)).is(FluidTags.WATER)) {
            depth++;
        }
        return depth;
    }

    private static java.util.Optional<EntityType<?>> entity(String id) {
        return BuiltInRegistries.ENTITY_TYPE.getOptional(ResourceLocation.parse(id));
    }

    private SpawnPlacementEvents() {
    }
}
