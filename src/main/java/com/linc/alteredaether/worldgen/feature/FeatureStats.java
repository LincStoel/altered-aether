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
package com.linc.alteredaether.worldgen.feature;

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * How many times each feature has placed something since the server started, and how long the costly ones took, for
 * the debug tools.
 */
public final class FeatureStats {
    private static final Map<String, AtomicInteger> COUNTS = new ConcurrentHashMap<>();
    private static final Map<String, AtomicLong> NANOS = new ConcurrentHashMap<>();

    public static void placed(String feature) {
        COUNTS.computeIfAbsent(feature, k -> new AtomicInteger()).incrementAndGet();
    }

    /** Adds time spent in a feature, summed over all worker threads; reported in milliseconds as "ms/" + key. */
    public static void timed(String key, long nanos) {
        NANOS.computeIfAbsent(key, k -> new AtomicLong()).addAndGet(nanos);
    }

    public static Map<String, Integer> snapshot() {
        Map<String, Integer> copy = new TreeMap<>();
        COUNTS.forEach((k, v) -> copy.put(k, v.get()));
        NANOS.forEach((k, v) -> copy.put("ms/" + k, (int) (v.get() / 1_000_000)));
        return copy;
    }

    private FeatureStats() {
    }
}
