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
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Boulders that fall out of the sky (see FallingBoulderCheck); each passes without Sable. */
@GameTestHolder(AlteredAether.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FallingBoulderTests {
    /** A boulder dropped onto a platform comes to rest and becomes blocks again, none lost or added. */
    @GameTest(template = "arena", batch = "boulder_settle", timeoutTicks = 1500)
    public static void boulderFallsAndSettles(GameTestHelper helper) {
        if (!ModList.get().isLoaded("sable")) {
            helper.succeed();
            return;
        }
        FallingBoulderCheck.settles(helper);
    }

    /** Gravitite ore, which Sable lifts, doesn't keep a boulder up: it carries it down like any other. */
    @GameTest(template = "arena", batch = "boulder_gravitite", timeoutTicks = 1500)
    public static void gravititeBoulderStillFalls(GameTestHelper helper) {
        if (!ModList.get().isLoaded("sable")) {
            helper.succeed();
            return;
        }
        FallingBoulderCheck.settles(helper,
                com.aetherteam.aether.block.AetherBlocks.GRAVITITE_ORE.get().defaultBlockState(), 1.0f);
    }

    /** Land somebody has protected is left alone: a boulder that comes to rest there leaves no blocks. */
    @GameTest(template = "arena", batch = "boulder_protected", timeoutTicks = 1500)
    public static void boulderLeavesProtectedLandAlone(GameTestHelper helper) {
        if (!ModList.get().isLoaded("sable")) {
            helper.succeed();
            return;
        }
        FallingBoulderCheck.protectedLanding(helper);
    }

    /** A boulder somebody breaks a block of is no longer one, and is left to Sable. */
    @GameTest(template = "arena", batch = "boulder_tampered", timeoutTicks = 600)
    public static void tamperedBoulderIsLetGo(GameTestHelper helper) {
        if (!ModList.get().isLoaded("sable")) {
            helper.succeed();
            return;
        }
        FallingBoulderCheck.tampered(helper);
    }

    /** The timer's attempt picks a spot away from the player, announces it, and the boulder is made and falls. */
    @GameTest(template = "arena", batch = "boulder_natural", timeoutTicks = 1500)
    public static void boulderIsAnnouncedAndDropped(GameTestHelper helper) {
        if (!ModList.get().isLoaded("sable")) {
            helper.succeed();
            return;
        }
        FallingBoulderCheck.natural(helper);
    }

    /** A boulder falls through a layer of aercloud without slowing, breaking the clouds in its path. */
    @GameTest(template = "arena", batch = "boulder_aercloud", timeoutTicks = 1500)
    public static void boulderPunchesThroughAerclouds(GameTestHelper helper) {
        if (!ModList.get().isLoaded("sable")) {
            helper.succeed();
            return;
        }
        FallingBoulderCheck.throughAerclouds(helper, false);
    }

    /** One that meets the clouds at a crawl, just under where it starts, still falls through them unslowed. */
    @GameTest(template = "arena", batch = "boulder_aercloud_slow", timeoutTicks = 1500)
    public static void slowBoulderPunchesThroughAerclouds(GameTestHelper helper) {
        if (!ModList.get().isLoaded("sable")) {
            helper.succeed();
            return;
        }
        FallingBoulderCheck.throughAerclouds(helper, true);
    }

    /**
     * Create Simulated's end sea holds an ordinary ship up, and lets a boulder fall through it: both start above an end
     * sea registered for the overworld, which is set well above the ground.
     */
    @GameTest(template = "arena", batch = "boulder_end_sea", timeoutTicks = 1500)
    public static void boulderFallsThroughTheEndSea(GameTestHelper helper) {
        if (!ModList.get().isLoaded("sable") || !ModList.get().isLoaded("simulated")) {
            helper.succeed();
            return;
        }
        FallingBoulderCheck.endSea(helper);
    }
}
