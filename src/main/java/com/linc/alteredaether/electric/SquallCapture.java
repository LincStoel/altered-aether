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

import com.linc.alteredaether.AlteredAether;
import com.linc.alteredaether.advancement.AAAdvancements;
import com.linc.alteredaether.entity.Squall;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Catching a squall in a Squall Insulator, two ways. Lure it: stand on or beside an empty Insulator, and when the
 * squall's own bolt lands within a block of the bowl (or on a lightning rod stood on it), the squall is pulled down
 * into it, which becomes a Squall Generator facing the same way. Or wear it down until it is spent (see
 * {@link Squall#isSpent}) and use an Insulator on it (see {@link SquallInsulatorItem}).
 */
public final class SquallCapture {
    /** Bolts a squall has called down, and the squall. */
    private static final Map<LightningBolt, Squall> BOLTS = new WeakHashMap<>();
    /** Players this close to a catch have caught the squall. */
    private static final double WITNESS = 16;

    /** Notes that {@code squall} called {@code bolt} down, so where it lands can catch it. */
    public static void called(LightningBolt bolt, Squall squall) {
        BOLTS.put(bolt, squall);
    }

    static void onBolt(LightningBolt bolt, ServerLevel level, BlockPos at) {
        Squall squall = BOLTS.remove(bolt);
        if (squall == null || !squall.isAlive() || squall.level() != level) {
            return;
        }
        BlockPos bowl = insulatorNear(level, at);
        if (bowl == null) {
            return;
        }
        BlockState empty = level.getBlockState(bowl);
        level.setBlockAndUpdate(bowl, ElectricRegistries.SQUALL_GENERATOR.get().defaultBlockState()
                .setValue(SquallGeneratorBlock.FACING, empty.getValue(SquallInsulatorBlock.FACING)));
        Vec3 into = Vec3.atCenterOf(bowl);
        SquallGeneratorCore.arc(level, squall.position(), into);
        caught(squall, null);
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, new AABB(bowl).inflate(WITNESS))) {
            AAAdvancements.award(player, AAAdvancements.CAUGHT_IN_A_BOWL, "caught");
            player.displayClientMessage(Component.translatable("message." + AlteredAether.MOD_ID + ".squall_caught"), true);
        }
    }

    /** An empty Insulator within a block of where a bolt landed, or under a lightning rod it landed on. */
    @Nullable
    private static BlockPos insulatorNear(ServerLevel level, BlockPos at) {
        if (level.getBlockState(at).is(Blocks.LIGHTNING_ROD) && isInsulator(level.getBlockState(at.below()))) {
            return at.below();
        }
        BlockPos nearest = null;
        double best = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(at.offset(-1, -1, -1), at.offset(1, 1, 1))) {
            if (isInsulator(level.getBlockState(pos))) {
                double distance = pos.distSqr(at);
                if (distance < best) {
                    best = distance;
                    nearest = pos.immutable();
                }
            }
        }
        return nearest;
    }

    private static boolean isInsulator(BlockState state) {
        return state.is(ElectricRegistries.SQUALL_INSULATOR.get());
    }

    /** The squall goes into the bowl: gone from the world, with a flash and a crackle; {@code by} gets the credit. */
    static void caught(Squall squall, @Nullable Player by) {
        if (squall.level() instanceof ServerLevel level) {
            Zaps.sparks(level, squall.position().add(0, squall.getBbHeight() * 0.5, 0), 30);
            level.playSound(null, squall.blockPosition(), SoundEvents.TRIDENT_THUNDER.value(), SoundSource.NEUTRAL, 0.6F,
                    1.6F);
        }
        squall.discard();
        if (by != null) {
            AAAdvancements.award(by, AAAdvancements.CAUGHT_IN_A_BOWL, "caught");
        }
    }

    private SquallCapture() {
    }
}
