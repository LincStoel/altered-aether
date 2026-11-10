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
 * Create's fans enchant through a lit ambrosium campfire (see CreateCompat). The checks themselves are in
 * {@link CreateFanCheck}, which names Create's types, so this holder loads without Create.
 */
@GameTestHolder(AlteredAether.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CreateFanTests {
    @GameTest(template = "arena")
    public static void fansEnchantThroughAmbrosium(GameTestHelper helper) {
        if (!ModList.get().isLoaded(CreateCompat.MOD)) {
            helper.succeed();
            return;
        }
        String failure = CreateFanCheck.run(helper);
        if (failure != null) {
            helper.fail(failure);
        }
        helper.succeed();
    }

    private CreateFanTests() {
    }
}
