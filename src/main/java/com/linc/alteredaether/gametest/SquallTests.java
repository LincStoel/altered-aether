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
import com.linc.alteredaether.entity.Squall;
import com.linc.alteredaether.registry.AARegistries;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/** Squalls fly up over their target and call lightning down on it. */
@GameTestHolder(AlteredAether.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SquallTests {
    @GameTest(template = "arena", timeoutTicks = 600)
    public static void squallsStrikeFromAbove(GameTestHelper helper) {
        // a fake player isn't ticked, so it stays where it is put
        FakePlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "squall-target"));
        player.moveTo(helper.absoluteVec(new Vec3(24, 4, 24)));
        helper.getLevel().addNewPlayer(player);
        Squall squall = helper.spawn(AARegistries.SQUALL.get(), new Vec3(32, 6, 32));
        squall.setPersistenceRequired();
        squall.setTarget(player);
        float health = squall.getHealth();
        double[] heightAtStrike = {Double.NaN};
        helper.onEachTick(() -> {
            if (Double.isNaN(heightAtStrike[0]) && !helper.getLevel().getEntitiesOfClass(LightningBolt.class,
                    player.getBoundingBox().inflate(1.5)).isEmpty()) {
                heightAtStrike[0] = squall.getY() - player.getY();
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(!Double.isNaN(heightAtStrike[0]), "no strike on the player yet");
            helper.assertTrue(heightAtStrike[0] >= 5, "struck from only " + heightAtStrike[0] + " above");
            helper.assertTrue(squall.isAlive() && squall.getHealth() == health, "the squall's own bolt hurt it");
            helper.getLevel().removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
        });
    }

    @GameTest(template = "arena", timeoutTicks = 600)
    public static void squallsBreakOffAfterStriking(GameTestHelper helper) {
        FakePlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "squall-target"));
        player.moveTo(helper.absoluteVec(new Vec3(24, 4, 24)));
        helper.getLevel().addNewPlayer(player);
        Squall squall = helper.spawn(AARegistries.SQUALL.get(), new Vec3(32, 6, 32));
        squall.setPersistenceRequired();
        squall.setTarget(player);
        // where it was at the strike, then the furthest out and highest it got in the two seconds after
        Vec3[] atStrike = {null};
        int[] since = {0};
        double[] best = {0, 0};
        helper.onEachTick(() -> {
            if (atStrike[0] == null) {
                if (!helper.getLevel().getEntitiesOfClass(LightningBolt.class, player.getBoundingBox().inflate(1.5))
                        .isEmpty()) {
                    atStrike[0] = squall.position();
                }
            } else if (since[0]++ < 40) {
                best[0] = Math.max(best[0], Math.hypot(squall.getX() - player.getX(), squall.getZ() - player.getZ()));
                best[1] = Math.max(best[1], squall.getY() - atStrike[0].y);
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(atStrike[0] != null && since[0] >= 40, "no strike yet, or still retreating");
            helper.assertTrue(best[0] >= 8, "only got " + best[0] + " out from the target");
            helper.assertTrue(best[1] >= 4, "only climbed " + best[1]);
            helper.getLevel().removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
        });
    }

    private SquallTests() {
    }
}
