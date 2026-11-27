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
package com.linc.alteredaether.mixin;

import com.linc.alteredaether.AlteredAether;
import com.linc.alteredaether.fix.UnloadRetries;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BooleanSupplier;

/**
 * Works round a vanilla 1.21.1 chunk-system hang (see {@link UnloadRetries}). A chunk on its way out that a generation
 * task still holds isn't ready to save, so vanilla's unload task schedules itself again; its save future is already
 * done, so the retry lands straight back on the unload queue, which processUnloads drains for as long as the tick has
 * time to spare. Generation tasks only run in that same spare time, so the one holding the chunk never finishes and the
 * server thread spins. The usual way in: a chunk loaded for a moment (a portal search, a teleport, something built
 * there), let go, then wanted again when the player's own ticket arrives, so its neighbours' generation holds it while
 * its stale unload task keeps retrying. Here a retry for a chunk no longer waiting to unload is dropped (vanilla's would
 * end in nothing once let go), and one for a chunk still waiting waits for the next processUnloads: the queue empties,
 * the tasks run, and the chunk unloads a tick or two later. The chunk stays in pendingUnloads meanwhile, so everything that waits on it
 * (shutdown included) still does. Left out when Moonrise replaces the chunk system (see AAMixinPlugin); it fails
 * quietly if a NeoForge update moves the call, leaving vanilla's behaviour.
 */
@Mixin(ChunkMap.class)
public abstract class ChunkUnloadRetryMixin {
    @Shadow
    @Final
    ServerLevel level;
    @Shadow
    @Final
    private Long2ObjectLinkedOpenHashMap<ChunkHolder> pendingUnloads;

    @Unique
    private final Long2ObjectLinkedOpenHashMap<ChunkHolder> altered_aether$deferred = new Long2ObjectLinkedOpenHashMap<>();
    /** When each chunk now waiting first had its unload put off, by System.nanoTime. */
    @Unique
    private final Long2LongOpenHashMap altered_aether$since = new Long2LongOpenHashMap();
    @Unique
    private final LongOpenHashSet altered_aether$warned = new LongOpenHashSet();

    @Shadow
    private void scheduleUnload(long chunkPos, ChunkHolder holder) {
        throw new AssertionError();
    }

    /** The unload task's retry, when its chunk isn't ready to save yet: wait for the next processUnloads. */
    @WrapOperation(method = "/^lambda\\$scheduleUnload\\$\\d+$/", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ChunkMap;scheduleUnload(JLnet/minecraft/server/level/ChunkHolder;)V"))
    private void altered_aether$deferRetry(ChunkMap map, long chunkPos, ChunkHolder holder, Operation<Void> original) {
        if (!UnloadRetries.enabled()) {
            UnloadRetries.retried(0);
            original.call(map, chunkPos, holder);
            return;
        }
        if (pendingUnloads.get(chunkPos) != holder) {
            // wanted back since (or already gone): vanilla's retry would only find that out once nothing holds it, so
            // it's dropped here; if the chunk is let go again, that schedules a fresh unload
            UnloadRetries.retried(0);
            return;
        }
        altered_aether$deferred.put(chunkPos, holder);
        long now = System.nanoTime();
        long since = altered_aether$since.computeIfAbsent(chunkPos, key -> now);
        UnloadRetries.retried(now - since);
        if (now - since > UnloadRetries.STUCK_WARNING && altered_aether$warned.add(chunkPos)) {
            AlteredAether.LOGGER.warn("Chunk {} in {} has waited {} s to unload; {} generation task(s) still hold it",
                    new ChunkPos(chunkPos), level.dimension().location(), (now - since) / 1_000_000_000L,
                    holder.getGenerationRefCount());
        }
    }

    /** Retries the unloads put off last time, once each, before this round's own. */
    @Inject(method = "processUnloads", at = @At("HEAD"))
    private void altered_aether$retryDeferred(BooleanSupplier hasMoreTime, CallbackInfo ci) {
        if (altered_aether$deferred.isEmpty()) {
            if (!altered_aether$since.isEmpty()) {
                altered_aether$since.clear();
                altered_aether$warned.clear();
            }
            return;
        }
        // a chunk not put off again last time has unloaded or been wanted back: stop timing it
        altered_aether$since.keySet().removeIf(pos -> !altered_aether$deferred.containsKey(pos));
        altered_aether$warned.removeIf(pos -> !altered_aether$deferred.containsKey(pos));
        // copied out first: scheduling one can't touch this map, but its entries are views that clear() empties
        long[] positions = new long[altered_aether$deferred.size()];
        ChunkHolder[] holders = new ChunkHolder[positions.length];
        int index = 0;
        for (Long2ObjectMap.Entry<ChunkHolder> retry : altered_aether$deferred.long2ObjectEntrySet()) {
            positions[index] = retry.getLongKey();
            holders[index++] = retry.getValue();
        }
        altered_aether$deferred.clear();
        for (int i = 0; i < positions.length; i++) {
            scheduleUnload(positions[i], holders[i]);
        }
    }
}
