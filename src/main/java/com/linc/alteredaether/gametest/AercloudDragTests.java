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

import com.aetherteam.aether.block.AetherBlocks;
import com.aetherteam.aether.entity.AetherEntityTypes;
import com.linc.alteredaether.AlteredAether;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Zephyrs and their like aren't held up inside an aercloud, but still land on one (see AercloudDragMixin). */
@GameTestHolder(AlteredAether.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AercloudDragTests {
    @GameTest(template = "arena")
    public static void zephyrsPassThroughAerclouds(GameTestHelper helper) {
        // a cloud three deep, from y 3 to 5
        for (int x = 8; x <= 24; x++) {
            for (int z = 8; z <= 24; z++) {
                for (int y = 3; y <= 5; y++) {
                    helper.setBlock(new BlockPos(x, y, z), AetherBlocks.COLD_AERCLOUD.get());
                }
            }
        }
        Entity inside = helper.spawn(AetherEntityTypes.ZEPHYR.get(), new Vec3(16, 3.5, 16));
        inside.setNoGravity(true);
        helper.assertTrue(helper.getLevel().noCollision(inside, inside.getBoundingBox().move(1, 0, 0)),
                "a zephyr inside a cloud can't steer through it");
        Entity above = helper.spawn(AetherEntityTypes.ZEPHYR.get(), new Vec3(16, 6, 16));
        helper.assertFalse(helper.getLevel().noCollision(above, above.getBoundingBox().move(0, -1, 0)),
                "a zephyr above a cloud sinks into it");
        Entity pig = helper.spawn(EntityType.PIG, new Vec3(16, 3.5, 16));
        helper.assertFalse(helper.getLevel().noCollision(pig, pig.getBoundingBox().move(1, 0, 0)),
                "the cloud no longer holds anything else");
        helper.succeed();
    }

    private AercloudDragTests() {
    }
}
