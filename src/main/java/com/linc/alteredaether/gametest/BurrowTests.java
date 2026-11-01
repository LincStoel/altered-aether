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
import com.linc.alteredaether.worldgen.grove.Giant;
import com.linc.alteredaether.worldgen.grove.GiantType;
import com.linc.alteredaether.worldgen.grove.GroveSamples;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Burrows are cut where a giant's wood is deep enough for them. Over many drawn giants of each type, some must take
 * one, and the room must lie inside the giant's own reach.
 */
@GameTestHolder(AlteredAether.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BurrowTests {
    private static final int SAMPLES = 60;

    @GameTest(template = "arena", timeoutTicks = 400)
    public static void giantsTakeBurrows(GameTestHelper helper) {
        List<String> report = new ArrayList<>();
        int total = 0;
        for (GiantType type : GiantType.values()) {
            int burrowed = 0;
            for (int sample = 0; sample < SAMPLES; sample++) {
                Giant giant = GroveSamples.burrowed(type, RandomSource.create(0xB0770L * 31 + sample), 0, 64, 0);
                if (giant == null) {
                    continue;
                }
                burrowed++;
                BlockPos door = giant.points("burrow").getFirst();
                if (!giant.bounds().touches(door.getX(), door.getZ(), door.getX(), door.getZ())) {
                    helper.fail(type.id() + " has its burrow outside its own reach");
                    return;
                }
            }
            report.add(type.id() + " " + burrowed + "/" + SAMPLES);
            total += burrowed;
        }
        AlteredAether.LOGGER.info("Burrows by type: {}", report);
        if (total == 0) {
            helper.fail("no giant took a burrow: " + report);
            return;
        }
        helper.succeed();
    }

    private BurrowTests() {
    }
}
