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
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
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
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/** The advancement tab loads whole, and rumour maps lead somewhere in the Aether and nowhere else. */
@GameTestHolder(AlteredAether.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RumourTests {
    private static final ResourceKey<Level> THE_AETHER = ResourceKey.create(Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath("aether", "the_aether"));
    private static final List<String> ADVANCEMENTS = List.of("root", "frozen_crags", "frozen_spire",
            "tempest_peaks", "thunderstruck", "tempest_crown", "shattered_rift", "great_fracture", "windswept_dunes", "aether_plateau",
            "grand_cascade", "skyroot_cathedral", "grandfather", "village_tree", "nurselog_hollow", "stump_cache",
            "atoll", "double_atoll", "sky_rider", "ride_the_storm", "rumour", "seven_wonders",
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

    private static List<ItemStack> roll(ServerLevel level, Vec3 origin) {
        LootTable table = level.getServer().reloadableRegistries().getLootTable(
                ResourceKey.create(Registries.LOOT_TABLE, AlteredAether.id("chests/rumour_map")));
        return table.getRandomItems(new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, origin)
                .create(LootContextParamSets.CHEST));
    }

    private RumourTests() {
    }
}
