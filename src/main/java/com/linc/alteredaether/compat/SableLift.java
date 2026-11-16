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
package com.linc.alteredaether.compat;

import com.aetherteam.aether.block.AetherBlocks;
import com.linc.alteredaether.config.AACommonConfig;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.physics.floating_block.FloatingBlockController;
import net.minecraft.world.level.ChunkPos;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Under Sable, enchanted gravitite lifts only while it has a redstone signal, as in the Aether it only floats when
 * powered. Sable decides lift by block state alone, and power isn't part of the Aether block's state, so
 * mixin/SableGravititeLiftMixin holds back Sable's own registration of every enchanted gravitite block on a sub-level
 * and hands it here; this registers it with Sable while it is powered and takes it away again when it isn't, checking
 * every few ticks. Its floating material and strength are data (see data/AAGravititeProvider).
 *
 * <p>Loaded only when Sable is: nothing outside this class and its mixin names a Sable type.
 */
public final class SableLift {
    public static final String MOD = "sable";
    /** How often power is checked, in ticks. */
    private static final int INTERVAL = 4;
    /** Sable's controllers that hold enchanted gravitite; weak, so a sub-level that goes away is let go. */
    private static final Set<Tracked> WATCHED = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
    /** Set while this registers a block itself, so the mixin lets Sable's own handling run. */
    private static final ThreadLocal<Boolean> DIRECT = ThreadLocal.withInitial(() -> false);

    /** What the mixin adds to Sable's floating block controller. */
    public interface Tracked {
        /** Every enchanted gravitite block Sable has been told of. */
        LongSet altered_aether$known();

        /** Those of them registered with Sable as lifting. */
        LongSet altered_aether$lifting();

        ServerLevel altered_aether$level();
    }

    public static boolean active() {
        return ModList.get().isLoaded(MOD) && AACommonConfig.compat(MOD) && GravititeRework.active();
    }

    private static boolean gated(BlockState state) {
        return !DIRECT.get() && state.is(AetherBlocks.ENCHANTED_GRAVITITE.get()) && active();
    }

    /** Whether Sable may register a block now; enchanted gravitite waits for this to check its power. */
    public static boolean allowAdd(Tracked controller, BlockState state, BlockPos pos) {
        if (!gated(state)) {
            return true;
        }
        synchronized (controller) {
            controller.altered_aether$known().add(pos.asLong());
        }
        WATCHED.add(controller);
        return false;
    }

    /** Whether Sable may unregister a block now; only enchanted gravitite that was registered as lifting is. */
    public static boolean allowRemove(Tracked controller, BlockState state, BlockPos pos) {
        if (!gated(state)) {
            return true;
        }
        synchronized (controller) {
            controller.altered_aether$known().remove(pos.asLong());
            return controller.altered_aether$lifting().remove(pos.asLong());
        }
    }

    /** Called from the mod's constructor, only when Sable is loaded. */
    public static void register() {
        NeoForge.EVENT_BUS.addListener(SableLift::onTick);
    }

    private static void onTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % INTERVAL != 0) {
            return;
        }
        List<Tracked> controllers;
        synchronized (WATCHED) {
            controllers = new ArrayList<>(WATCHED);
        }
        for (Tracked controller : controllers) {
            update(controller);
        }
    }

    /** Registers each powered enchanted gravitite block with Sable, and unregisters each that has lost power. */
    private static void update(Tracked controller) {
        ServerLevel level = controller.altered_aether$level();
        long[] known;
        synchronized (controller) {
            known = controller.altered_aether$known().toLongArray();
        }
        BlockState gravitite = AetherBlocks.ENCHANTED_GRAVITITE.get().defaultBlockState();
        FloatingBlockController sable = (FloatingBlockController) controller;
        for (long packed : known) {
            BlockPos pos = BlockPos.of(packed);
            if (!level.isLoaded(pos)) {
                continue;
            }
            boolean lifting;
            synchronized (controller) {
                lifting = controller.altered_aether$lifting().contains(packed);
            }
            boolean powered = level.getBlockState(pos).is(gravitite.getBlock()) && level.hasNeighborSignal(pos);
            if (powered == lifting) {
                continue;
            }
            DIRECT.set(true);
            try {
                if (powered) {
                    sable.queueAddFloatingBlock(gravitite, pos);
                } else {
                    sable.queueRemoveFloatingBlock(gravitite, pos);
                }
            } finally {
                DIRECT.set(false);
            }
            synchronized (controller) {
                if (powered) {
                    controller.altered_aether$lifting().add(packed);
                } else {
                    controller.altered_aether$lifting().remove(packed);
                }
            }
        }
    }

    /**
     * Whether a block is part of a Sable ship (a sub-level's plot). Gravitite there lifts the ship rather than falling
     * upward out of it, as the Aether has it do in the world (see mixin/SableFloatingBlockMixin).
     */
    public static boolean onShip(ServerLevel level, BlockPos pos) {
        return active() && SubLevelContainer.getContainer(level).getChunkHolder(new ChunkPos(pos)) != null;
    }

    /** A fresh set for the mixin's fields. */
    public static LongSet newSet() {
        return new LongOpenHashSet();
    }

    private SableLift() {
    }
}
