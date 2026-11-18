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
import com.linc.alteredaether.advancement.OwnedAerwhalePredicate;
import com.linc.alteredaether.advancement.VisitLandmarkTrigger;
import com.linc.alteredaether.block.AmbrosiumCampfireBlock;
import com.linc.alteredaether.block.GleamVinesBlock;
import com.linc.alteredaether.block.GleamVinesPlantBlock;
import com.linc.alteredaether.compat.ModEnabled;
import com.linc.alteredaether.entity.Squall;
import com.linc.alteredaether.entity.TamedAerwhale;
import com.linc.alteredaether.entity.golem.GravititeGolem;
import com.linc.alteredaether.entity.golem.ThrownPebble;
import com.aetherteam.aether.block.miscellaneous.FloatingBlock;
import com.linc.alteredaether.item.GravititeItem;
import com.linc.alteredaether.item.PebbleLink;
import com.linc.alteredaether.loot.GravititeOreDrops;
import com.linc.alteredaether.loot.GravititeReworkCondition;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.storage.loot.predicates.LootItemConditionType;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import com.linc.alteredaether.item.StoragePebbleItem;
import com.linc.alteredaether.item.WhaleWhistleItem;
import com.linc.alteredaether.item.WhistleLink;
import com.linc.alteredaether.loot.RumourMapFunction;
import com.linc.alteredaether.menu.AerwhaleInventoryMenu;
import com.linc.alteredaether.worldgen.density.Atoll;
import com.linc.alteredaether.worldgen.density.BasinCarve;
import com.linc.alteredaether.worldgen.density.ConfigValue;
import com.linc.alteredaether.worldgen.feature.BasinFillFeature;
import com.linc.alteredaether.worldgen.feature.BasinSealFeature;
import com.linc.alteredaether.worldgen.feature.GroveFeature;
import com.linc.alteredaether.worldgen.feature.HangingRootsFeature;
import com.linc.alteredaether.worldgen.feature.UnderstoryFeature;
import com.linc.alteredaether.worldgen.feature.CampFeature;
import com.linc.alteredaether.worldgen.feature.RuinFeature;
import com.linc.alteredaether.worldgen.feature.AercloudFeature;
import com.linc.alteredaether.worldgen.feature.AercloudBridgeFeature;
import com.linc.alteredaether.worldgen.feature.RuinedPillarFeature;
import com.linc.alteredaether.worldgen.feature.SnowDriftFeature;
import com.linc.alteredaether.worldgen.feature.StrikeCraterFeature;
import com.linc.alteredaether.worldgen.feature.TotemFeature;
import com.linc.alteredaether.worldgen.feature.SpawnEntityFeature;
import com.linc.alteredaether.worldgen.feature.LandmarkFeature;
import com.linc.alteredaether.worldgen.feature.AncientBoulderFeature;
import com.linc.alteredaether.worldgen.feature.BoulderFeature;
import com.linc.alteredaether.worldgen.feature.CopperSpikeFeature;
import com.linc.alteredaether.worldgen.feature.CopperVeinFeature;
import com.linc.alteredaether.worldgen.feature.CloudSkirtFeature;
import com.linc.alteredaether.worldgen.feature.EdgeSpringFeature;
import com.linc.alteredaether.worldgen.placement.ConfigCount;
import com.linc.alteredaether.worldgen.placement.ArchwoodRegion;
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
import com.linc.alteredaether.worldgen.modifier.ScaleSpawnCostModifier;
import com.linc.alteredaether.worldgen.provider.SubstituteStateProvider;
import com.linc.alteredaether.worldgen.surface.IslandPosition;
import com.linc.alteredaether.worldgen.surface.Steep;
import com.linc.alteredaether.worldgen.surface.Strata;
import com.linc.alteredaether.worldgen.surface.SubstituteBlock;
import com.aetherteam.aether.data.resources.AetherMobCategory;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.advancements.critereon.EntitySubPredicate;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctionType;
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
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.attachment.AttachmentType;
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

    /**
     * Gravitite as a modern ore (see data/AAGravititeProvider): the ore drops raw gravitite, which only enchanting
     * turns into the enchanted ingot. The raw block floats up as the ore does. Every item drifts upward when dropped
     * (see item/GravititeItem); crushed raw gravitite is for Create and the dusts for Oritech.
     */
    public static final DeferredBlock<FloatingBlock> RAW_GRAVITITE_BLOCK = BLOCKS.registerBlock("raw_gravitite_block",
            properties -> new FloatingBlock(false, properties),
            BlockBehaviour.Properties.ofFullCopy(Blocks.RAW_IRON_BLOCK).mapColor(MapColor.COLOR_PINK));
    public static final DeferredItem<BlockItem> RAW_GRAVITITE_BLOCK_ITEM = ITEMS.registerSimpleBlockItem(RAW_GRAVITITE_BLOCK);
    public static final DeferredItem<GravititeItem> RAW_GRAVITITE = gravitite("raw_gravitite");
    public static final DeferredItem<GravititeItem> ENCHANTED_GRAVITITE_INGOT = gravitite("enchanted_gravitite_ingot");
    public static final DeferredItem<GravititeItem> ENCHANTED_GRAVITITE_NUGGET = gravitite("enchanted_gravitite_nugget");
    public static final DeferredItem<GravititeItem> CRUSHED_RAW_GRAVITITE = gravitite("crushed_raw_gravitite");
    public static final DeferredItem<GravititeItem> GRAVITITE_DUST = gravitite("gravitite_dust");
    public static final DeferredItem<GravititeItem> SMALL_GRAVITITE_DUST = gravitite("small_gravitite_dust");

    private static DeferredItem<GravititeItem> gravitite(String name) {
        return ITEMS.register(name, () -> new GravititeItem(new Item.Properties()));
    }

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, AlteredAether.MOD_ID);

    /** Sized and tracked like the Aether's own aerwhale. */
    public static final DeferredHolder<EntityType<?>, EntityType<TamedAerwhale>> TAMED_AERWHALE = ENTITY_TYPES.register(
            "tamed_aerwhale", () -> EntityType.Builder.<TamedAerwhale>of(TamedAerwhale::new, MobCategory.CREATURE)
                    .fireImmune().sized(3.0F, 3.0F).clientTrackingRange(10)
                    // every tick, so a whale players stand on turns smoothly for them
                    .updateInterval(1).build("tamed_aerwhale"));

    /** A zephyr in storm colours, so sized and tracked like one; see entity/Squall. */
    public static final DeferredHolder<EntityType<?>, EntityType<Squall>> SQUALL = ENTITY_TYPES.register(
            "squall", () -> EntityType.Builder.<Squall>of(Squall::new, AetherMobCategory.AETHER_SKY_MONSTER)
                    .sized(4.5F, 3.5F).clientTrackingRange(10).build("squall"));
    public static final DeferredItem<DeferredSpawnEggItem> SQUALL_SPAWN_EGG = ITEMS.register("squall_spawn_egg",
            () -> new DeferredSpawnEggItem(SQUALL, 0x463C47, 0x9C838B, new Item.Properties()));

    /** Sorts the storage linked to it; see entity/golem/GravititeGolem. Seated, it is smaller (see its dimensions). */
    public static final DeferredHolder<EntityType<?>, EntityType<GravititeGolem>> GRAVITITE_GOLEM = ENTITY_TYPES.register(
            "gravitite_golem", () -> EntityType.Builder.<GravititeGolem>of(GravititeGolem::new, MobCategory.MISC)
                    .sized(0.9F, 2.0F).eyeHeight(1.75F).clientTrackingRange(10).build("gravitite_golem"));
    public static final DeferredItem<DeferredSpawnEggItem> GRAVITITE_GOLEM_SPAWN_EGG = ITEMS.register(
            "gravitite_golem_spawn_egg", () -> new DeferredSpawnEggItem(GRAVITITE_GOLEM, 0x8A8F94, 0xBD42B2, new Item.Properties()));
    /** A thrown storage pebble, sized and tracked like a snowball; see entity/golem/ThrownPebble. */
    public static final DeferredHolder<EntityType<?>, EntityType<ThrownPebble>> THROWN_PEBBLE = ENTITY_TYPES.register(
            "storage_pebble", () -> EntityType.Builder.<ThrownPebble>of(ThrownPebble::new, MobCategory.MISC)
                    .sized(0.25F, 0.25F).clientTrackingRange(4).updateInterval(10).build("storage_pebble"));

    public static final DeferredRegister.DataComponents DATA_COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, AlteredAether.MOD_ID);
    /** The aerwhale a whale whistle is linked to; see item/WhaleWhistleItem. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<WhistleLink>> WHISTLE_LINK =
            DATA_COMPONENTS.registerComponentType("whistle_link",
                    builder -> builder.persistent(WhistleLink.CODEC).networkSynchronized(WhistleLink.STREAM_CODEC));
    /** Calls the aerwhale it's linked to; see entity/AerwhaleWhistle. */
    public static final DeferredItem<WhaleWhistleItem> WHALE_WHISTLE = ITEMS.register("whale_whistle",
            () -> new WhaleWhistleItem(new Item.Properties().stacksTo(1)));

    /** The gravitite golem a storage pebble belongs to; see item/StoragePebbleItem. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<PebbleLink>> PEBBLE_LINK =
            DATA_COMPONENTS.registerComponentType("pebble_link",
                    builder -> builder.persistent(PebbleLink.CODEC).networkSynchronized(PebbleLink.STREAM_CODEC));
    /** Links storage and golems to the golem it came from. */
    public static final DeferredItem<StoragePebbleItem> STORAGE_PEBBLE = ITEMS.register("storage_pebble",
            () -> new StoragePebbleItem(new Item.Properties().stacksTo(1)));

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, AlteredAether.MOD_ID);

    /**
     * How close a wild aerwhale is to accepting a rider, 0 to {@code AerwhaleTemper.MAX}: kept on the Aether's wild
     * aerwhale and on one being broken in alike, since a whale can turn from one into the other and back.
     */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Integer>> AERWHALE_TEMPER =
            ATTACHMENT_TYPES.register("aerwhale_temper",
                    () -> AttachmentType.builder(() -> 0).serialize(Codec.INT).build());

    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, AlteredAether.MOD_ID);

    /** A tamed aerwhale's saddle and saddlebag; the client is sent which whale. */
    public static final DeferredHolder<MenuType<?>, MenuType<AerwhaleInventoryMenu>> AERWHALE_MENU =
            MENUS.register("aerwhale", () -> IMenuTypeExtension.create(AerwhaleInventoryMenu::fromNetwork));

    public static final DeferredRegister<MapCodec<? extends ICondition>> CONDITIONS =
            DeferredRegister.create(NeoForgeRegistries.Keys.CONDITION_CODECS, AlteredAether.MOD_ID);
    public static final DeferredHolder<MapCodec<? extends ICondition>, MapCodec<ModEnabled>> MOD_ENABLED =
            CONDITIONS.register("mod_enabled", () -> ModEnabled.CODEC);

    public static final DeferredRegister<CriterionTrigger<?>> TRIGGERS =
            DeferredRegister.create(Registries.TRIGGER_TYPE, AlteredAether.MOD_ID);
    public static final DeferredHolder<CriterionTrigger<?>, VisitLandmarkTrigger> VISIT_LANDMARK =
            TRIGGERS.register("visit_landmark", VisitLandmarkTrigger::new);

    public static final DeferredRegister<MapCodec<? extends EntitySubPredicate>> ENTITY_SUB_PREDICATES =
            DeferredRegister.create(Registries.ENTITY_SUB_PREDICATE_TYPE, AlteredAether.MOD_ID);
    /** Riding advancements count only aerwhales someone owns, not a wild one being broken in. */
    public static final DeferredHolder<MapCodec<? extends EntitySubPredicate>, MapCodec<OwnedAerwhalePredicate>> OWNED_AERWHALE =
            ENTITY_SUB_PREDICATES.register("owned_aerwhale", () -> OwnedAerwhalePredicate.CODEC);

    public static final DeferredRegister<LootItemFunctionType<?>> LOOT_FUNCTIONS =
            DeferredRegister.create(Registries.LOOT_FUNCTION_TYPE, AlteredAether.MOD_ID);
    public static final DeferredHolder<LootItemFunctionType<?>, LootItemFunctionType<RumourMapFunction>> RUMOUR_MAP =
            LOOT_FUNCTIONS.register("rumour_map", () -> new LootItemFunctionType<>(RumourMapFunction.CODEC));

    public static final DeferredRegister<LootItemConditionType> LOOT_CONDITIONS =
            DeferredRegister.create(Registries.LOOT_CONDITION_TYPE, AlteredAether.MOD_ID);
    /** Whether the gravitite rework is on, for our own tables that give gravitite (see GravititeReworkCondition). */
    public static final DeferredHolder<LootItemConditionType, LootItemConditionType> GRAVITITE_REWORK_CONDITION =
            LOOT_CONDITIONS.register("gravitite_rework", () -> new LootItemConditionType(GravititeReworkCondition.CODEC));

    public static final DeferredRegister<MapCodec<? extends IGlobalLootModifier>> LOOT_MODIFIERS =
            DeferredRegister.create(NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, AlteredAether.MOD_ID);
    /** Gravitite ore drops raw gravitite (see GravititeOreDrops). */
    public static final DeferredHolder<MapCodec<? extends IGlobalLootModifier>, MapCodec<GravititeOreDrops>> GRAVITITE_ORE_DROPS =
            LOOT_MODIFIERS.register("gravitite_ore_drops", () -> GravititeOreDrops.CODEC);

    public static final DeferredRegister<MapCodec<? extends BiomeModifier>> BIOME_MODIFIER_SERIALIZERS =
            DeferredRegister.create(NeoForgeRegistries.Keys.BIOME_MODIFIER_SERIALIZERS, AlteredAether.MOD_ID);

    public static final DeferredHolder<MapCodec<? extends BiomeModifier>, MapCodec<ConfiguredSpawnsModifier>> CONFIGURED_SPAWNS =
            BIOME_MODIFIER_SERIALIZERS.register("configured_spawns", () -> ConfiguredSpawnsModifier.CODEC);
    public static final DeferredHolder<MapCodec<? extends BiomeModifier>, MapCodec<ScaleSpawnCostModifier>> SCALE_SPAWN_COST =
            BIOME_MODIFIER_SERIALIZERS.register("scale_spawn_cost", () -> ScaleSpawnCostModifier.CODEC);
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
        DENSITY_FUNCTIONS.register("cloud_sea", () -> com.linc.alteredaether.worldgen.density.CloudSeaMarker.MAP_CODEC);
        DENSITY_FUNCTIONS.register("biome_edge", () -> com.linc.alteredaether.worldgen.density.BiomeEdge.MAP_CODEC);
        DENSITY_FUNCTIONS.register("island_mass", () -> com.linc.alteredaether.worldgen.density.IslandMass.MAP_CODEC);
        DENSITY_FUNCTIONS.register("cave_carve", () -> com.linc.alteredaether.worldgen.density.CaveCarve.MAP_CODEC);
    }

    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(Registries.SOUND_EVENT, AlteredAether.MOD_ID);
    /** The crackle before a Tempest strike hits a player; see storm/TempestStorms. */
    public static final DeferredHolder<SoundEvent, SoundEvent> INCOMING_LIGHTNING = SOUND_EVENTS.register(
            "storm.incoming_lightning", () -> SoundEvent.createVariableRangeEvent(AlteredAether.id("storm.incoming_lightning")));
    /** A whale whistle blown: the aerwhale's own call. */
    public static final DeferredHolder<SoundEvent, SoundEvent> WHALE_WHISTLE_SOUND = SOUND_EVENTS.register(
            "item.whale_whistle.blow", () -> SoundEvent.createVariableRangeEvent(AlteredAether.id("item.whale_whistle.blow")));

    public static final DeferredHolder<SoundEvent, SoundEvent> GOLEM_HUM = sound("entity.gravitite_golem.hum");
    public static final DeferredHolder<SoundEvent, SoundEvent> GOLEM_CHIME = sound("entity.gravitite_golem.chime");
    public static final DeferredHolder<SoundEvent, SoundEvent> GOLEM_LINK = sound("entity.gravitite_golem.link");
    public static final DeferredHolder<SoundEvent, SoundEvent> GOLEM_UNLINK = sound("entity.gravitite_golem.unlink");
    public static final DeferredHolder<SoundEvent, SoundEvent> GOLEM_SHAKE = sound("entity.gravitite_golem.shake");
    public static final DeferredHolder<SoundEvent, SoundEvent> GOLEM_REPEL = sound("entity.gravitite_golem.repel");

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(AlteredAether.id(name)));
    }

    public static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(Registries.FEATURE, AlteredAether.MOD_ID);

    public static final DeferredHolder<Feature<?>, BasinFillFeature> BASIN_FILL =
            FEATURES.register("basin_fill", BasinFillFeature::new);
    public static final DeferredHolder<Feature<?>, BasinSealFeature> BASIN_SEAL =
            FEATURES.register("basin_seal", BasinSealFeature::new);
    public static final DeferredHolder<Feature<?>, GroveFeature> GROVE =
            FEATURES.register("grove", GroveFeature::new);
    public static final DeferredHolder<Feature<?>, HangingRootsFeature> HANGING_ROOTS =
            FEATURES.register("hanging_roots", HangingRootsFeature::new);
    public static final DeferredHolder<Feature<?>, CampFeature> CAMP =
            FEATURES.register("camp", CampFeature::new);
    public static final DeferredHolder<Feature<?>, RuinFeature> RUIN =
            FEATURES.register("ruin", RuinFeature::new);
    public static final DeferredHolder<Feature<?>, UnderstoryFeature> UNDERSTORY =
            FEATURES.register("understory", UnderstoryFeature::new);
    public static final DeferredHolder<Feature<?>, CloudSkirtFeature> CLOUD_SKIRT =
            FEATURES.register("cloud_skirt", CloudSkirtFeature::new);
    public static final DeferredHolder<Feature<?>, com.linc.alteredaether.worldgen.feature.CloudSeaFeature> CLOUD_SEA =
            FEATURES.register("cloud_sea", com.linc.alteredaether.worldgen.feature.CloudSeaFeature::new);
    public static final DeferredHolder<Feature<?>, EdgeSpringFeature> EDGE_SPRING =
            FEATURES.register("edge_spring", EdgeSpringFeature::new);
    public static final DeferredHolder<Feature<?>, BoulderFeature> BOULDER =
            FEATURES.register("boulder", BoulderFeature::new);
    public static final DeferredHolder<Feature<?>, AncientBoulderFeature> ANCIENT_BOULDER =
            FEATURES.register("ancient_boulder", AncientBoulderFeature::new);
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
    public static final DeferredHolder<Feature<?>, com.linc.alteredaether.worldgen.feature.TundraDriftsFeature>
            TUNDRA_DRIFTS = FEATURES.register("tundra_drifts",
            com.linc.alteredaether.worldgen.feature.TundraDriftsFeature::new);
    public static final DeferredHolder<Feature<?>, com.linc.alteredaether.worldgen.feature.CanopyMossFeature>
            CANOPY_MOSS = FEATURES.register("canopy_moss",
            com.linc.alteredaether.worldgen.feature.CanopyMossFeature::new);
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
    public static final DeferredHolder<PlacementModifierType<?>, PlacementModifierType<ArchwoodRegion>> ARCHWOOD_REGION =
            PLACEMENT_MODIFIERS.register("archwood_region", () -> () -> ArchwoodRegion.CODEC);
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
        SURFACE_RULES.register("stone_patches", () -> com.linc.alteredaether.worldgen.surface.StonePatches.MAP_CODEC);
        SURFACE_RULES.register("substitute_block", () -> SubstituteBlock.MAP_CODEC);
    }

    public static void register(IEventBus modBus) {
        CONDITIONS.register(modBus);
        TRIGGERS.register(modBus);
        ENTITY_SUB_PREDICATES.register(modBus);
        LOOT_FUNCTIONS.register(modBus);
        LOOT_CONDITIONS.register(modBus);
        LOOT_MODIFIERS.register(modBus);
        BLOCKS.register(modBus);
        DATA_COMPONENTS.register(modBus);
        ITEMS.register(modBus);
        ENTITY_TYPES.register(modBus);
        MENUS.register(modBus);
        ATTACHMENT_TYPES.register(modBus);
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
