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

import com.aetherteam.aether.entity.passive.Aerwhale;
import com.linc.alteredaether.AlteredAether;
import com.linc.alteredaether.compat.DeepAetherWhaleCheck;
import com.linc.alteredaether.compat.DeepAetherWhalePatches;
import com.linc.alteredaether.entity.AerwhaleMode;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Member;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

/**
 * With Deep Aether installed and our aerwhales in use, its aerwhale patches are all cancelled: none of what they add
 * is on the Aether's aerwhale, and the startup check finds nothing amiss. Passes trivially without Deep Aether.
 */
@GameTestHolder(AlteredAether.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DeepAetherWhaleTests {
    @GameTest(template = "arena")
    public static void deepAetherWhalePatchesAreCancelled(GameTestHelper helper) {
        if (!AerwhaleMode.deepAetherCancelled()) {
            helper.succeed();
            return;
        }
        List<String> problems = DeepAetherWhaleCheck.check();
        helper.assertTrue(problems.isEmpty(), "patch check: " + problems);
        helper.assertTrue(DeepAetherWhalePatches.cancelled().contains(DeepAetherWhalePatches.KNOWN.getFirst().mixin()),
                "the aerwhale patch wasn't cancelled: " + DeepAetherWhalePatches.cancelled());
        for (Class<?> type : Aerwhale.class.getInterfaces()) {
            helper.assertFalse(type.getName().startsWith(DeepAetherWhalePatches.PACKAGE),
                    "the aerwhale implements " + type.getName());
        }
        List<String> added = Stream.concat(Arrays.stream(Aerwhale.class.getDeclaredFields()),
                        Arrays.stream(Aerwhale.class.getDeclaredMethods()))
                .map(Member::getName)
                .filter(name -> name.toLowerCase(java.util.Locale.ROOT).contains("deep_aether"))
                .toList();
        helper.assertTrue(added.isEmpty(), "the aerwhale has Deep Aether's " + added);
        helper.succeed();
    }

    private DeepAetherWhaleTests() {
    }
}
