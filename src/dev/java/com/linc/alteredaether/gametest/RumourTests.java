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
package com.linc.alteredaether.gametest;

import com.linc.alteredaether.AlteredAether;
import com.linc.alteredaether.loot.RumourMapFunction;
import com.linc.alteredaether.loot.RumourWarmup;
import com.linc.alteredaether.worldgen.landmark.LandmarkField;
import com.linc.alteredaether.worldgen.landmark.LandmarkType;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/** The advancement tab loads whole, and rumour maps lead somewhere in the Aether and nowhere else. */
@GameTestHolder(AlteredAether.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RumourTests {
    private static final ResourceKey<Level> THE_AETHER = ResourceKey.create(Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath("aether", "the_aether"));
    private static final List<String> ADVANCEMENTS = List.of("root", "frozen_crags", "frozen_spire",
            "tempest_peaks", "living_lightning_rod", "thunderstruck", "tempest_crown", "shattered_rift", "great_fracture", "windswept_dunes", "aether_plateau",
            "grand_cascade", "skyroot_cathedral", "grandfather", "village_tree", "nurselog_hollow", "stump_cache",
            "atoll", "double_atoll", "favorite_snack", "sky_rider", "airborne_cargo", "ride_the_storm", "take_a_load_off", "rumour", "seven_wonders",
            "altered_cartographer");

    @GameTest(template = "arena")
    public static void advancementsLoad(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        for (String name : ADVANCEMENTS) {
            if (server.getAdvancements().get(AlteredAether.id(name)) == null) {
                helper.fail("Advancement " + name + " didn't load");
            }
        }
        boolean ars = ModList.get().isLoaded("ars_nouveau");
        if ((server.getAdvancements().get(AlteredAether.id("archwood_grove")) != null) != ars) {
            helper.fail("The archwood grove advancement should load only with Ars Nouveau");
        }
        helper.succeed();
    }

    @GameTest(template = "arena", timeoutTicks = 400)
    public static void rumourMapLeadsToALandmark(GameTestHelper helper) {
        ServerLevel aether = helper.getLevel().getServer().getLevel(THE_AETHER);
        if (aether == null) {
            helper.succeed();
            return;
        }
        List<ItemStack> loot = roll(aether, new Vec3(20_000, 100, -15_000));
        if (loot.size() != 1 || !loot.getFirst().is(Items.FILLED_MAP)) {
            helper.fail("Expected one filled map, got " + loot);
        }
        CustomData data = loot.getFirst().getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        if (!data.copyTag().getCompound(RumourMapFunction.TAG).contains("rumour")) {
            helper.fail("The map isn't marked as a rumour: " + data);
        }
        if (!roll(helper.getLevel(), new Vec3(0, 100, 0)).isEmpty()) {
            helper.fail("A rumour map outside the Aether should come to nothing");
        }
        helper.succeed();
    }

    /**
     * The chests a player actually opens hold rumour maps: the Aether's dungeon chests through the loot modifiers, and
     * the groves' chests through their own pools. Each is rolled many times; at the odds set, none coming up would be
     * all but impossible.
     */
    @GameTest(template = "arena", timeoutTicks = 400)
    public static void chestsHoldRumourMaps(GameTestHelper helper) {
        ServerLevel aether = helper.getLevel().getServer().getLevel(THE_AETHER);
        if (aether == null) {
            helper.succeed();
            return;
        }
        Vec3 origin = new Vec3(20_000, 100, -15_000);
        for (ResourceLocation table : List.of(
                ResourceLocation.fromNamespaceAndPath("aether", "chests/dungeon/bronze/bronze_dungeon"),
                ResourceLocation.fromNamespaceAndPath("aether", "chests/dungeon/silver/silver_dungeon"),
                AlteredAether.id("chests/grove/camp"), AlteredAether.id("chests/grove/stump_poor"),
                AlteredAether.id("chests/double_atoll"))) {
            int maps = 0;
            for (int i = 0; i < 60; i++) {
                maps += (int) roll(aether, origin, table).stream().filter(RumourTests::isRumour).count();
            }
            if (maps == 0) {
                helper.fail("No rumour map in 60 rolls of " + table);
            }
        }
        helper.succeed();
    }

    /**
     * A player in the Aether has the rumour searches from where they stand made ahead of time, off the server thread,
     * so a chest opened there finds its landmark from the cache. The cold search, on ground nobody has been near, is
     * logged alongside for comparison.
     */
    @GameTest(template = "arena", timeoutTicks = 6000)
    public static void rumourSearchesAreWarmedAroundPlayers(GameTestHelper helper) {
        ServerLevel aether = helper.getLevel().getServer().getLevel(THE_AETHER);
        LandmarkField field = LandmarkField.get(THE_AETHER);
        if (aether == null || field == null) {
            AlteredAether.LOGGER.info("Rumour warm-up test skipped: aether {}, landmark field {}", aether, field);
            helper.succeed();
            return;
        }
        List<LandmarkType> all = Arrays.asList(LandmarkType.values());
        long start = System.nanoTime();
        RumourMapFunction.find(field, all, -60_000, 45_000, RumourMapFunction.DEFAULT_RADIUS, RandomSource.create(1));
        double cold = (System.nanoTime() - start) / 1e6;
        int x = 60_000, z = -45_000;
        FakePlayer scout = FakePlayerFactory.get(aether, new GameProfile(UUID.randomUUID(), "rumour-scout"));
        scout.moveTo(x + 0.5, 200, z + 0.5);
        aether.addNewPlayer(scout);
        helper.succeedWhen(() -> {
            helper.assertTrue(RumourWarmup.settled(aether, field, scout.getUUID(), x, z), "still searching ahead");
            long from = System.nanoTime();
            RumourMapFunction.find(field, all, x, z, RumourMapFunction.DEFAULT_RADIUS, RandomSource.create(1));
            double warm = (System.nanoTime() - from) / 1e6;
            AlteredAether.LOGGER.info("Rumour search: {} ms cold, {} ms warmed", String.format("%.1f", cold),
                    String.format("%.2f", warm));
            aether.removePlayerImmediately(scout, Entity.RemovalReason.DISCARDED);
            helper.assertTrue(warm < 50, "a warmed search should be cache hits, took " + warm + " ms");
        });
    }

    private static boolean isRumour(ItemStack stack) {
        return stack.is(Items.FILLED_MAP) && stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag()
                .getCompound(RumourMapFunction.TAG).contains("rumour");
    }

    private static List<ItemStack> roll(ServerLevel level, Vec3 origin) {
        return roll(level, origin, AlteredAether.id("chests/rumour_map"));
    }

    private static List<ItemStack> roll(ServerLevel level, Vec3 origin, ResourceLocation id) {
        LootTable table = level.getServer().reloadableRegistries().getLootTable(
                ResourceKey.create(Registries.LOOT_TABLE, id));
        return table.getRandomItems(new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, origin)
                .create(LootContextParamSets.CHEST));
    }

    private RumourTests() {
    }
}
