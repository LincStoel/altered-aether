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

/** How many times each feature has placed something since the server started, for the debug tools. */
public final class FeatureStats {
    private static final Map<String, AtomicInteger> COUNTS = new ConcurrentHashMap<>();

    public static void placed(String feature) {
        COUNTS.computeIfAbsent(feature, k -> new AtomicInteger()).incrementAndGet();
    }

    public static Map<String, Integer> snapshot() {
        Map<String, Integer> copy = new TreeMap<>();
        COUNTS.forEach((k, v) -> copy.put(k, v.get()));
        return copy;
    }

    private FeatureStats() {
    }
}
