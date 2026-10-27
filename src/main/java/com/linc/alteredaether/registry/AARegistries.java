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
package com.linc.alteredaether.registry;

import com.linc.alteredaether.AlteredAether;
import com.linc.alteredaether.block.AmbrosiumCampfireBlock;
import com.linc.alteredaether.block.GleamVinesBlock;
import com.linc.alteredaether.block.GleamVinesPlantBlock;
import com.linc.alteredaether.compat.ModEnabled;
import com.linc.alteredaether.entity.TamedAerwhale;
import com.linc.alteredaether.menu.AerwhaleInventoryMenu;
import com.linc.alteredaether.worldgen.density.Atoll;
import com.linc.alteredaether.worldgen.density.BasinCarve;
import com.linc.alteredaether.worldgen.density.ConfigValue;
import com.linc.alteredaether.worldgen.feature.BasinFillFeature;
import com.linc.alteredaether.worldgen.feature.BasinSealFeature;
import com.linc.alteredaether.worldgen.feature.GroveFeature;
import com.linc.alteredaether.worldgen.feature.HangingRootsFeature;
import com.linc.alteredaether.worldgen.feature.UnderstoryFeature;
import com.linc.alteredaether.worldgen.feature.RuinFeature;
import com.linc.alteredaether.worldgen.feature.AercloudFeature;
import com.linc.alteredaether.worldgen.feature.AercloudBridgeFeature;
import com.linc.alteredaether.worldgen.feature.RuinedPillarFeature;
import com.linc.alteredaether.worldgen.feature.SnowDriftFeature;
import com.linc.alteredaether.worldgen.feature.StrikeCraterFeature;
import com.linc.alteredaether.worldgen.feature.TotemFeature;
import com.linc.alteredaether.worldgen.feature.SpawnEntityFeature;
import com.linc.alteredaether.worldgen.feature.LandmarkFeature;
import com.linc.alteredaether.worldgen.feature.BoulderFeature;
import com.linc.alteredaether.worldgen.feature.CopperSpikeFeature;
import com.linc.alteredaether.worldgen.feature.CopperVeinFeature;
import com.linc.alteredaether.worldgen.feature.EdgeSpringFeature;
import com.linc.alteredaether.worldgen.placement.ConfigCount;
import com.linc.alteredaether.worldgen.placement.BiomeEdgeFalloff;
import com.linc.alteredaether.worldgen.placement.IslandEdge;
import com.linc.alteredaether.worldgen.placement.JitteredGrid;
import com.linc.alteredaether.worldgen.placement.Overhang;
import com.linc.alteredaether.worldgen.placement.LandmarkPoints;
import com.linc.alteredaether.worldgen.placement.RiftGap;
import com.linc.alteredaether.worldgen.density.RegionMask;
import com.linc.alteredaether.worldgen.density.ShiftY;
import com.linc.alteredaether.worldgen.density.Terrace;
import com.linc.alteredaether.worldgen.density.VoronoiFracture;
import com.linc.alteredaether.worldgen.density.WindRidges;
import com.linc.alteredaether.worldgen.modifier.AetherExclusiveModifier;
import com.linc.alteredaether.worldgen.modifier.ClimateModifier;
import com.linc.alteredaether.worldgen.modifier.ConfiguredSpawnsModifier;
import com.linc.alteredaether.worldgen.modifier.OverworldArchwoodModifier;
import com.linc.alteredaether.worldgen.provider.SubstituteStateProvider;
import com.linc.alteredaether.worldgen.surface.IslandPosition;
import com.linc.alteredaether.worldgen.surface.Steep;
import com.linc.alteredaether.worldgen.surface.Strata;
import com.linc.alteredaether.worldgen.surface.SubstituteBlock;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.food.Foods;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemNameBlockItem;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.SurfaceRules;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProviderType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Every deferred register the mod owns. */
public final class AARegistries {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(AlteredAether.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(AlteredAether.MOD_ID);

    public static final DeferredBlock<AmbrosiumCampfireBlock> AMBROSIUM_CAMPFIRE = BLOCKS.registerBlock(
            "ambrosium_campfire", AmbrosiumCampfireBlock::new, BlockBehaviour.Properties.ofFullCopy(Blocks.CAMPFIRE));
    public static final DeferredItem<BlockItem> AMBROSIUM_CAMPFIRE_ITEM = ITEMS.registerSimpleBlockItem(AMBROSIUM_CAMPFIRE);

    /** Cave vines and glow berries in Aether colours; see block/GleamVines. */
    public static final DeferredBlock<GleamVinesBlock> GLEAM_VINES = BLOCKS.registerBlock(
            "gleam_vines", GleamVinesBlock::new, BlockBehaviour.Properties.ofFullCopy(Blocks.CAVE_VINES));
    public static final DeferredBlock<GleamVinesPlantBlock> GLEAM_VINES_PLANT = BLOCKS.registerBlock(
            "gleam_vines_plant", GleamVinesPlantBlock::new, BlockBehaviour.Properties.ofFullCopy(Blocks.CAVE_VINES_PLANT));
    /** Like the glow berry, the berry is the vine's item: it is eaten, and planted under a ceiling. */
    public static final DeferredItem<ItemNameBlockItem> GLEAMBERRIES = ITEMS.register("gleamberries",
            () -> new ItemNameBlockItem(GLEAM_VINES.get(), new Item.Properties().food(Foods.GLOW_BERRIES)));

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, AlteredAether.MOD_ID);

    /** Sized and tracked like the Aether's own aerwhale. */
    public static final DeferredHolder<EntityType<?>, EntityType<TamedAerwhale>> TAMED_AERWHALE = ENTITY_TYPES.register(
            "tamed_aerwhale", () -> EntityType.Builder.<TamedAerwhale>of(TamedAerwhale::new, MobCategory.CREATURE)
                    .fireImmune().sized(3.0F, 3.0F).clientTrackingRange(10)
                    // every tick, so a whale players stand on turns smoothly for them
                    .updateInterval(1).build("tamed_aerwhale"));

    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, AlteredAether.MOD_ID);

    /** A tamed aerwhale's saddle and saddlebag; the client is sent which whale. */
    public static final DeferredHolder<MenuType<?>, MenuType<AerwhaleInventoryMenu>> AERWHALE_MENU =
            MENUS.register("aerwhale", () -> IMenuTypeExtension.create(AerwhaleInventoryMenu::fromNetwork));

    public static final DeferredRegister<MapCodec<? extends ICondition>> CONDITIONS =
            DeferredRegister.create(NeoForgeRegistries.Keys.CONDITION_CODECS, AlteredAether.MOD_ID);
    public static final DeferredHolder<MapCodec<? extends ICondition>, MapCodec<ModEnabled>> MOD_ENABLED =
            CONDITIONS.register("mod_enabled", () -> ModEnabled.CODEC);

    public static final DeferredRegister<MapCodec<? extends BiomeModifier>> BIOME_MODIFIER_SERIALIZERS =
            DeferredRegister.create(NeoForgeRegistries.Keys.BIOME_MODIFIER_SERIALIZERS, AlteredAether.MOD_ID);

    public static final DeferredHolder<MapCodec<? extends BiomeModifier>, MapCodec<ConfiguredSpawnsModifier>> CONFIGURED_SPAWNS =
            BIOME_MODIFIER_SERIALIZERS.register("configured_spawns", () -> ConfiguredSpawnsModifier.CODEC);
    public static final DeferredHolder<MapCodec<? extends BiomeModifier>, MapCodec<AetherExclusiveModifier>> AETHER_EXCLUSIVE =
            BIOME_MODIFIER_SERIALIZERS.register("aether_exclusive", () -> AetherExclusiveModifier.CODEC);
    public static final DeferredHolder<MapCodec<? extends BiomeModifier>, MapCodec<ClimateModifier>> CLIMATE =
            BIOME_MODIFIER_SERIALIZERS.register("climate", () -> ClimateModifier.CODEC);
    public static final DeferredHolder<MapCodec<? extends BiomeModifier>, MapCodec<OverworldArchwoodModifier>> OVERWORLD_ARCHWOOD =
            BIOME_MODIFIER_SERIALIZERS.register("overworld_archwood", () -> OverworldArchwoodModifier.CODEC);

    public static final DeferredRegister<BlockStateProviderType<?>> BLOCK_STATE_PROVIDERS =
            DeferredRegister.create(Registries.BLOCK_STATE_PROVIDER_TYPE, AlteredAether.MOD_ID);

    public static final DeferredHolder<BlockStateProviderType<?>, BlockStateProviderType<SubstituteStateProvider>> SUBSTITUTE_PROVIDER =
            BLOCK_STATE_PROVIDERS.register("substitute", () -> new BlockStateProviderType<>(SubstituteStateProvider.CODEC));

    public static final DeferredRegister<MapCodec<? extends DensityFunction>> DENSITY_FUNCTIONS =
            DeferredRegister.create(Registries.DENSITY_FUNCTION_TYPE, AlteredAether.MOD_ID);

    public static final DeferredHolder<MapCodec<? extends DensityFunction>, MapCodec<RegionMask>> REGION_MASK =
            DENSITY_FUNCTIONS.register("region_mask", () -> RegionMask.MAP_CODEC);

    static {
        DENSITY_FUNCTIONS.register("shift_y", () -> ShiftY.MAP_CODEC);
        DENSITY_FUNCTIONS.register("choose", () -> com.linc.alteredaether.worldgen.density.Choose.MAP_CODEC);
        DENSITY_FUNCTIONS.register("terrace", () -> Terrace.MAP_CODEC);
        DENSITY_FUNCTIONS.register("wind_ridges", () -> WindRidges.MAP_CODEC);
        DENSITY_FUNCTIONS.register("voronoi_fracture", () -> VoronoiFracture.MAP_CODEC);
        DENSITY_FUNCTIONS.register("config", () -> ConfigValue.MAP_CODEC);
        DENSITY_FUNCTIONS.register("basin_carve", () -> BasinCarve.MAP_CODEC);
        DENSITY_FUNCTIONS.register("grove_break", () -> com.linc.alteredaether.worldgen.density.GroveBreak.MAP_CODEC);
        DENSITY_FUNCTIONS.register("atoll", () -> Atoll.MAP_CODEC);
    }

    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(Registries.SOUND_EVENT, AlteredAether.MOD_ID);
    /** The crackle before a Tempest strike hits a player; see storm/TempestStorms. */
    public static final DeferredHolder<SoundEvent, SoundEvent> INCOMING_LIGHTNING = SOUND_EVENTS.register(
            "storm.incoming_lightning", () -> SoundEvent.createVariableRangeEvent(AlteredAether.id("storm.incoming_lightning")));

    public static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(Registries.FEATURE, AlteredAether.MOD_ID);

    public static final DeferredHolder<Feature<?>, BasinFillFeature> BASIN_FILL =
            FEATURES.register("basin_fill", BasinFillFeature::new);
    public static final DeferredHolder<Feature<?>, BasinSealFeature> BASIN_SEAL =
            FEATURES.register("basin_seal", BasinSealFeature::new);
    public static final DeferredHolder<Feature<?>, GroveFeature> GROVE =
            FEATURES.register("grove", GroveFeature::new);
    public static final DeferredHolder<Feature<?>, HangingRootsFeature> HANGING_ROOTS =
            FEATURES.register("hanging_roots", HangingRootsFeature::new);
    public static final DeferredHolder<Feature<?>, RuinFeature> RUIN =
            FEATURES.register("ruin", RuinFeature::new);
    public static final DeferredHolder<Feature<?>, UnderstoryFeature> UNDERSTORY =
            FEATURES.register("understory", UnderstoryFeature::new);
    public static final DeferredHolder<Feature<?>, EdgeSpringFeature> EDGE_SPRING =
            FEATURES.register("edge_spring", EdgeSpringFeature::new);
    public static final DeferredHolder<Feature<?>, BoulderFeature> BOULDER =
            FEATURES.register("boulder", BoulderFeature::new);
    public static final DeferredHolder<Feature<?>, CopperSpikeFeature> COPPER_SPIKE =
            FEATURES.register("copper_spike", CopperSpikeFeature::new);
    public static final DeferredHolder<Feature<?>, CopperVeinFeature> COPPER_VEIN =
            FEATURES.register("copper_vein", CopperVeinFeature::new);
    public static final DeferredHolder<Feature<?>, AercloudFeature> AERCLOUD =
            FEATURES.register("aercloud", AercloudFeature::new);
    public static final DeferredHolder<Feature<?>, AercloudBridgeFeature> AERCLOUD_BRIDGE =
            FEATURES.register("aercloud_bridge", AercloudBridgeFeature::new);
    public static final DeferredHolder<Feature<?>, RuinedPillarFeature> RUINED_PILLAR =
            FEATURES.register("ruined_pillar", RuinedPillarFeature::new);
    public static final DeferredHolder<Feature<?>, SnowDriftFeature> SNOW_DRIFT =
            FEATURES.register("snow_drift", SnowDriftFeature::new);
    public static final DeferredHolder<Feature<?>, StrikeCraterFeature> STRIKE_CRATER =
            FEATURES.register("strike_crater", StrikeCraterFeature::new);
    public static final DeferredHolder<Feature<?>, TotemFeature> TOTEM =
            FEATURES.register("totem", TotemFeature::new);
    public static final DeferredHolder<Feature<?>, SpawnEntityFeature> SPAWN_ENTITY =
            FEATURES.register("spawn_entity", SpawnEntityFeature::new);
    public static final DeferredHolder<Feature<?>, LandmarkFeature> LANDMARK =
            FEATURES.register("landmark", LandmarkFeature::new);

    public static final DeferredRegister<PlacementModifierType<?>> PLACEMENT_MODIFIERS =
            DeferredRegister.create(Registries.PLACEMENT_MODIFIER_TYPE, AlteredAether.MOD_ID);

    public static final DeferredHolder<PlacementModifierType<?>, PlacementModifierType<ConfigCount>> CONFIG_COUNT =
            PLACEMENT_MODIFIERS.register("config_count", () -> () -> ConfigCount.CODEC);
    public static final DeferredHolder<PlacementModifierType<?>, PlacementModifierType<IslandEdge>> ISLAND_EDGE =
            PLACEMENT_MODIFIERS.register("island_edge", () -> () -> IslandEdge.CODEC);
    public static final DeferredHolder<PlacementModifierType<?>, PlacementModifierType<JitteredGrid>> JITTERED_GRID =
            PLACEMENT_MODIFIERS.register("jittered_grid", () -> () -> JitteredGrid.CODEC);
    public static final DeferredHolder<PlacementModifierType<?>, PlacementModifierType<BiomeEdgeFalloff>> BIOME_EDGE_FALLOFF =
            PLACEMENT_MODIFIERS.register("biome_edge_falloff", () -> () -> BiomeEdgeFalloff.CODEC);
    public static final DeferredHolder<PlacementModifierType<?>, PlacementModifierType<Overhang>> OVERHANG =
            PLACEMENT_MODIFIERS.register("overhang", () -> () -> Overhang.CODEC);
    public static final DeferredHolder<PlacementModifierType<?>, PlacementModifierType<LandmarkPoints>> LANDMARK_POINTS =
            PLACEMENT_MODIFIERS.register("landmark_points", () -> () -> LandmarkPoints.CODEC);
    public static final DeferredHolder<PlacementModifierType<?>, PlacementModifierType<RiftGap>> RIFT_GAP =
            PLACEMENT_MODIFIERS.register("rift_gap", () -> () -> RiftGap.CODEC);

    public static final DeferredRegister<MapCodec<? extends SurfaceRules.ConditionSource>> SURFACE_CONDITIONS =
            DeferredRegister.create(Registries.MATERIAL_CONDITION, AlteredAether.MOD_ID);
    public static final DeferredRegister<MapCodec<? extends SurfaceRules.RuleSource>> SURFACE_RULES =
            DeferredRegister.create(Registries.MATERIAL_RULE, AlteredAether.MOD_ID);

    static {
        SURFACE_CONDITIONS.register("steep", () -> Steep.MAP_CODEC);
        SURFACE_CONDITIONS.register("island_position", () -> IslandPosition.MAP_CODEC);
        SURFACE_RULES.register("strata", () -> Strata.MAP_CODEC);
        SURFACE_RULES.register("substitute_block", () -> SubstituteBlock.MAP_CODEC);
    }

    public static void register(IEventBus modBus) {
        CONDITIONS.register(modBus);
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        ENTITY_TYPES.register(modBus);
        MENUS.register(modBus);
        SURFACE_CONDITIONS.register(modBus);
        SURFACE_RULES.register(modBus);
        DENSITY_FUNCTIONS.register(modBus);
        BIOME_MODIFIER_SERIALIZERS.register(modBus);
        BLOCK_STATE_PROVIDERS.register(modBus);
        FEATURES.register(modBus);
        PLACEMENT_MODIFIERS.register(modBus);
        SOUND_EVENTS.register(modBus);
    }

    private AARegistries() {
    }
}
