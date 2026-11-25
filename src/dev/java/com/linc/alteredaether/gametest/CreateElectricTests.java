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
import com.linc.alteredaether.compat.CreateCompat;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The Tempest Peaks electricity with Create: a Squall Generator heating a basin and flash blasting on a depot, and the
 * Aerogel Wheel's speed, stress, drain and stops. The checks are in {@link CreateElectricCheck}, which names Create's
 * types, so this holder loads without Create (when each test passes at once). Run with
 * {@code -PoptionalMods=create-1.21.1}.
 */
@GameTestHolder(AlteredAether.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CreateElectricTests {
    @GameTest(template = "arena")
    public static void aGeneratorHeatsABasin(GameTestHelper helper) {
        if (create(helper)) {
            CreateElectricCheck.basinHeat(helper);
        }
    }

    @GameTest(template = "arena", timeoutTicks = 200)
    public static void aGeneratorOverADepotFlashBlastsWhatIsOnIt(GameTestHelper helper) {
        if (create(helper)) {
            CreateElectricCheck.depot(helper);
        }
    }

    @GameTest(template = "arena", timeoutTicks = 300)
    public static void theWheelTurnsOnItsJarsCharge(GameTestHelper helper) {
        if (create(helper)) {
            CreateElectricCheck.wheel(helper);
        }
    }

    @GameTest(template = "arena", timeoutTicks = 200)
    public static void theWheelStopsWhenAJarRunsDry(GameTestHelper helper) {
        if (create(helper)) {
            CreateElectricCheck.wheelDrained(helper);
        }
    }

    private static boolean create(GameTestHelper helper) {
        if (ModList.get().isLoaded(CreateCompat.MOD)) {
            return true;
        }
        helper.succeed();
        return false;
    }

    private CreateElectricTests() {
    }
}
