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
package com.linc.alteredaether.fix;

import com.linc.alteredaether.config.AACommonConfig;

import java.util.concurrent.atomic.AtomicLong;

/**
 * The switch and counters for ChunkUnloadRetryMixin, which works round a vanilla 1.21.1 hang: a chunk waiting to unload
 * while a generation task still holds it is retried straight away, in a loop that eats every spare moment of the tick,
 * and the generation task that would free it only runs in that spare time. The server thread spins, new chunks stop
 * arriving (after a long teleport or a change of dimension) and only a relog clears it. The mixin retries such a chunk
 * once a tick instead.
 */
public final class UnloadRetries {
    /** How long a chunk may wait on generation before it's worth a warning, in nanoseconds. */
    public static final long STUCK_WARNING = 30_000_000_000L;

    private static final AtomicLong RETRIES = new AtomicLong();
    private static final AtomicLong LONGEST_WAIT = new AtomicLong();

    private UnloadRetries() {
    }

    /** Whether stuck unloads wait for the next tick ({@code fixes.defer_stuck_unloads}); on until the config loads. */
    public static boolean enabled() {
        return !AACommonConfig.SPEC.isLoaded() || AACommonConfig.DEFER_STUCK_UNLOADS.get();
    }

    /**
     * Counts one retry of an unload whose chunk generation still holds: put off to the next tick when the fix is on,
     * from a chunk that has waited {@code waited} nanoseconds so far.
     */
    public static void retried(long waited) {
        RETRIES.incrementAndGet();
        LONGEST_WAIT.accumulateAndGet(waited, Math::max);
    }

    /** Unload retries since the server started (or the last {@link #reset}). */
    public static long retries() {
        return RETRIES.get();
    }

    /** The longest any one chunk has waited on generation to unload, in nanoseconds. */
    public static long longestWait() {
        return LONGEST_WAIT.get();
    }

    public static void reset() {
        RETRIES.set(0);
        LONGEST_WAIT.set(0);
    }
}
