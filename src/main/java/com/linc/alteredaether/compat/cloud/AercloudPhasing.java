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
package com.linc.alteredaether.compat.cloud;

import com.linc.alteredaether.block.PhasedAercloudBlock;
import com.linc.alteredaether.config.AACommonConfig;
import com.linc.alteredaether.registry.AARegistries;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * Aerclouds give way to Sable bodies. Sable lets every body through every cloud (AercloudPassCallback), and the clouds
 * it touches, with a block's margin round each, turn into phased clouds that fade back in over about thirty seconds
 * (PhasedAercloudBlock). A body still inside keeps touching them, which keeps them phased. Players riding a body open
 * the same tunnel round themselves, so a cloud above an open deck gives way too.
 *
 * <p>Contacts arrive from inside Sable's physics step, so they are only queued there; the clouds change at the end of
 * the level's tick.
 */
public final class AercloudPhasing {
    public static final String MOD = "sable";
    private static final TagKey<Block> AERCLOUDS =
            TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("aether", "aerclouds"));
    private static final boolean SABLE = ModList.get().isLoaded(MOD);

    private static final Map<ResourceKey<Level>, LongOpenHashSet> QUEUED = new HashMap<>();

    private AercloudPhasing() {
    }

    /** Whether clouds give way at all: Sable is installed and its compat switch is on. */
    public static boolean enabled() {
        return SABLE && AACommonConfig.compat(MOD);
    }

    /** Called from the mod's constructor, only when Sable is loaded. */
    public static void register() {
        NeoForge.EVENT_BUS.addListener(AercloudPhasing::onLevelTick);
        NeoForge.EVENT_BUS.addListener(AercloudPhasing::onPlayerTick);
    }

    /** What Sable bodies pass through: every Aether and Deep Aether cloud, phased or not. */
    public static boolean passes(BlockState state) {
        return state.is(AARegistries.PHASED_AERCLOUD.get()) || state.is(AERCLOUDS)
                || PhasedAercloudBlock.Variant.of(state.getBlock()) != null;
    }

    /** Queues the clouds at {@code pos} and within {@code margin} blocks of it to phase at the end of this tick. */
    public static void queue(ServerLevel level, BlockPos pos, int margin) {
        LongOpenHashSet queued = QUEUED.computeIfAbsent(level.dimension(), key -> new LongOpenHashSet());
        for (int dx = -margin; dx <= margin; dx++) {
            for (int dy = -margin; dy <= margin; dy++) {
                for (int dz = -margin; dz <= margin; dz++) {
                    queued.add(BlockPos.asLong(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz));
                }
            }
        }
    }

    /** Queues every cloud in a box. */
    public static void queue(ServerLevel level, AABB box) {
        LongOpenHashSet queued = QUEUED.computeIfAbsent(level.dimension(), key -> new LongOpenHashSet());
        for (int x = Mth.floor(box.minX); x <= Mth.floor(box.maxX); x++) {
            for (int y = Mth.floor(box.minY); y <= Mth.floor(box.maxY); y++) {
                for (int z = Mth.floor(box.minZ); z <= Mth.floor(box.maxZ); z++) {
                    queued.add(BlockPos.asLong(x, y, z));
                }
            }
        }
    }

    /**
     * Phases the cloud at {@code pos} now, or starts a phased one fading from the faintest again. Returns whether
     * anything changed.
     */
    public static boolean phase(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        PhasedAercloudBlock phased = AARegistries.PHASED_AERCLOUD.get();
        if (state.is(phased)) {
            if (state.getValue(PhasedAercloudBlock.FADE) == 0) {
                return false;
            }
            return level.setBlock(pos, state.setValue(PhasedAercloudBlock.FADE, 0), Block.UPDATE_CLIENTS);
        }
        BlockState phasedState = phased.phase(state);
        return phasedState != null && level.setBlock(pos, phasedState, Block.UPDATE_CLIENTS);
    }

    private static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        LongOpenHashSet queued = QUEUED.get(level.dimension());
        if (queued == null || queued.isEmpty()) {
            return;
        }
        for (LongIterator it = queued.iterator(); it.hasNext(); ) {
            BlockPos pos = BlockPos.of(it.nextLong());
            if (level.isLoaded(pos)) {
                phase(level, pos);
            }
        }
        queued.clear();
    }

    private static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && !player.isSpectator() && enabled()
                && ShipRiders.onShip(player)) {
            queue(player.serverLevel(), player.getBoundingBox().inflate(1));
        }
    }
}
