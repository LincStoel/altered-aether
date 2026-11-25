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

import com.aetherteam.aether.block.natural.AercloudBlock;
import com.linc.alteredaether.config.AACommonConfig;
import com.linc.alteredaether.entity.Squall;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * A squall's own cloud: it catches a fall as a cold aercloud does, but it is charged, and now and then shocks whatever
 * is inside it (about once every two seconds for each block it is in). Squalls themselves pass through unharmed. It is
 * also what superheats a Squall Generator, and the Static Boots are made with it.
 */
public class StormAercloudBlock extends AercloudBlock {
    /** One tick in this many, a body inside is shocked. */
    private static final int ZAP_CHANCE = 40;

    public StormAercloudBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        super.entityInside(state, level, pos, entity);
        if (level instanceof ServerLevel server && entity instanceof LivingEntity living && !(entity instanceof Squall)
                && server.random.nextInt(ZAP_CHANCE) == 0) {
            Zaps.zap(server, living, Vec3.atCenterOf(pos), AACommonConfig.STORM_AERCLOUD_DAMAGE.get().floatValue());
        }
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(6) == 0) {
            level.addParticle(ParticleTypes.ELECTRIC_SPARK, pos.getX() + random.nextDouble(), pos.getY() + random.nextDouble(),
                    pos.getZ() + random.nextDouble(), 0, 0, 0);
        }
    }
}
