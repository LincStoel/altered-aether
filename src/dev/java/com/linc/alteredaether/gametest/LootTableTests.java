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
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.ReloadableServerRegistries;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.ValidationContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Our loot tables, checked the way the game checks them as they load. A table that points at one that doesn't exist
 * only logs a warning there, and the pool quietly rolls nothing; here it fails.
 */
@GameTestHolder(AlteredAether.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LootTableTests {
    @GameTest(template = "arena")
    public static void lootTablesValidate(GameTestHelper helper) {
        ReloadableServerRegistries.Holder registries = helper.getLevel().getServer().reloadableRegistries();
        ProblemReporter.Collector problems = new ProblemReporter.Collector();
        ValidationContext context = new ValidationContext(problems, LootContextParamSets.ALL_PARAMS, registries.lookup());
        int checked = 0;
        for (ResourceLocation id : registries.getKeys(Registries.LOOT_TABLE)) {
            if (!id.getNamespace().equals(AlteredAether.MOD_ID)) {
                continue;
            }
            ResourceKey<LootTable> key = ResourceKey.create(Registries.LOOT_TABLE, id);
            LootTable table = registries.getLootTable(key);
            table.validate(context.setParams(table.getParamSet()).enterElement("{" + id + "}", key));
            checked++;
        }
        helper.assertTrue(checked > 0, "none of our loot tables loaded");
        problems.getReport().ifPresent(report -> helper.fail("Loot table problems:\n" + report));
        helper.succeed();
    }

    private LootTableTests() {
    }
}
