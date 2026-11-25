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
package com.linc.alteredaether.electric;

import com.linc.alteredaether.advancement.AAAdvancements;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * Where a lightning bolt lands, as it powers a rod (see mixin/LightningBoltMixin): once per bolt, for the vanilla
 * storm's, Tempest Peaks' and a squall's alike. A bolt on an Aether Jar, or on a lightning rod stood on one, charges the
 * jar; a squall's bolt beside an empty Squall Insulator pulls the squall into it (see {@link SquallCapture}).
 */
public final class ElectricStrikes {
    /** Players this close to a struck jar have bottled lightning. */
    private static final double WITNESS = 16;

    public static void onBolt(LightningBolt bolt, ServerLevel level, BlockPos at) {
        BlockPos jar = jarAt(level, at);
        if (jar != null && level.getBlockEntity(jar) instanceof AetherJarBlockEntity entity) {
            entity.strike();
            for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, new AABB(jar).inflate(WITNESS))) {
                AAAdvancements.award(player, AAAdvancements.BOTTLED_LIGHTNING, "struck");
            }
        }
        SquallCapture.onBolt(bolt, level, at);
    }

    /** The jar a bolt landing at {@code at} charges: one there, or one with a lightning rod there on its top. */
    @Nullable
    private static BlockPos jarAt(ServerLevel level, BlockPos at) {
        BlockState state = level.getBlockState(at);
        if (state.getBlock() instanceof AetherJarBlock) {
            return at;
        }
        return state.is(Blocks.LIGHTNING_ROD) ? AetherJarBlock.jarUnder(level, at) : null;
    }

    private ElectricStrikes() {
    }
}
