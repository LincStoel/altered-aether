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
package com.linc.alteredaether.worldgen;

import it.unimi.dsi.fastutil.HashCommon;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.LongFunction;

/**
 * A bounded, thread-safe cache from a long key (a grid cell, mostly) to a value worked out from the world seed, for
 * the per-world fields worldgen reads hundreds of times a chunk.
 * <p>
 * It does what the fields used Guava's cache for, at a fraction of the cost per read:
 * <ul>
 *     <li>each key is loaded once: a thread asking for a key another is still loading waits for that load rather than
 *     repeating it (site searches are dear);</li>
 *     <li>it holds at most about {@code maximumSize} values, dropping the least recently read.</li>
 * </ul>
 * Keys are spread with a 64-bit mix before choosing a shard and a slot, so packed {@code (x << 32) ^ z} keys, whose
 * plain {@code Long.hashCode} is {@code x ^ z}, don't pile up along diagonals. Reads touch one shard's lock and its
 * linked map, with no recency queue to drain.
 */
public final class CellCache<V> {
    private static final int SHARD_BITS = 6, SHARDS = 1 << SHARD_BITS;

    private final Shard<V>[] shards;

    @SuppressWarnings("unchecked")
    public CellCache(int maximumSize) {
        int perShard = Math.max(16, maximumSize / SHARDS);
        shards = new Shard[SHARDS];
        for (int i = 0; i < SHARDS; i++) {
            shards[i] = new Shard<>(perShard);
        }
    }

    /** The value for {@code key}, loading it (once, whichever thread asks first) if it isn't held. Never null. */
    public V get(long key, LongFunction<V> loader) {
        return shard(key).get(key, loader);
    }

    /** The value for {@code key} if it is held and loaded, else null; never loads. */
    @Nullable
    public V getIfPresent(long key) {
        return shard(key).getIfPresent(key);
    }

    private Shard<V> shard(long key) {
        return shards[(int) (HashCommon.mix(key) >>> (64 - SHARD_BITS))];
    }

    /** A load under way: the thread doing it, and where its result will land. */
    private static final class Loading<V> {
        final Thread owner = Thread.currentThread();
        final CompletableFuture<V> result = new CompletableFuture<>();
    }

    private static final class Shard<V> {
        /** Values and loads in progress, least recently read first. */
        private final Long2ObjectLinkedOpenHashMap<Object> entries;
        private final int capacity;

        Shard(int capacity) {
            this.capacity = capacity;
            entries = new Long2ObjectLinkedOpenHashMap<>(capacity + 1);
        }

        @SuppressWarnings("unchecked")
        @Nullable
        synchronized V getIfPresent(long key) {
            Object held = entries.getAndMoveToLast(key);
            return held instanceof Loading ? null : (V) held;
        }

        @SuppressWarnings("unchecked")
        V get(long key, LongFunction<V> loader) {
            Loading<V> mine = null, other = null;
            synchronized (this) {
                Object held = entries.getAndMoveToLast(key);
                if (held == null) {
                    mine = new Loading<>();
                    entries.putAndMoveToLast(key, mine);
                    trim();
                } else if (held instanceof Loading<?> loading) {
                    if (loading.owner == Thread.currentThread()) {
                        throw new IllegalStateException("Recursive load of cell " + key);
                    }
                    other = (Loading<V>) loading;
                } else {
                    return (V) held;
                }
            }
            // Load, or wait for the thread that is, outside the lock.
            return mine != null ? load(key, loader, mine) : await(other);
        }

        private V load(long key, LongFunction<V> loader, Loading<V> mine) {
            V value;
            try {
                value = loader.apply(key);
                if (value == null) {
                    throw new NullPointerException("Cell " + key + " loaded null");
                }
            } catch (Throwable t) {
                synchronized (this) {
                    if (entries.get(key) == mine) {
                        entries.remove(key);
                    }
                }
                mine.result.completeExceptionally(t);
                throw t;
            }
            synchronized (this) {
                Object held = entries.get(key);
                // Put it back even if the load was trimmed meanwhile: it's the freshest value there is.
                if (held == mine || held == null) {
                    entries.putAndMoveToLast(key, value);
                    trim();
                }
            }
            mine.result.complete(value);
            return value;
        }

        private static <V> V await(Loading<V> loading) {
            try {
                return loading.result.join();
            } catch (CompletionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof RuntimeException runtime) {
                    throw runtime;
                }
                if (cause instanceof Error error) {
                    throw error;
                }
                throw new IllegalStateException(cause);
            }
        }

        /** Drops the least recently read values while over capacity; loads in progress stay. */
        private void trim() {
            int guard = entries.size();
            while (entries.size() > capacity && guard-- > 0) {
                long first = entries.firstLongKey();
                Object held = entries.get(first);
                if (held instanceof Loading) {
                    entries.getAndMoveToLast(first);
                } else {
                    entries.removeFirst();
                }
            }
        }
    }
}
