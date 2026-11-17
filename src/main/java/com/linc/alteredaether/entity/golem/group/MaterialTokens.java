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
package com.linc.alteredaether.entity.golem.group;

import java.util.Collection;
import java.util.List;

/**
 * The material an item's name says it is made of: {@code stripped_spruce_log} and {@code spruce_stairs} are both
 * {@code spruce}, {@code polished_andesite_wall} is {@code andesite}. Pure: works on registry paths alone.
 */
public final class MaterialTokens {
    /** Words in front of a material that say how it was worked, not what it is. */
    private static final List<String> PREFIXES = List.of(
            "stripped_", "polished_", "smooth_", "cut_", "chiseled_", "cracked_", "mossy_", "hollow_", "carved_",
            "waxed_", "exposed_", "weathered_", "oxidized_", "infested_", "chipped_", "damaged_");

    /** Shapes a material is made into, longest first so {@code _fence_gate} goes before {@code _fence}. */
    private static final List<String> SUFFIXES = List.of(
            "_pressure_plate", "_hanging_sign", "_wall_sign", "_chest_boat", "_fence_gate", "_trapdoor", "_button",
            "_stairs", "_planks", "_hyphae", "_sapling", "_pillar", "_leaves", "_bricks", "_fence", "_brick", "_tiles",
            "_slab", "_wall", "_door", "_sign", "_boat", "_raft", "_tile", "_stem", "_wood", "_log", "_block");

    private MaterialTokens() {
    }

    /**
     * The material in {@code path} (a registry path, no namespace): a known wood type it names if any, the longest
     * such, else the path with its working words and shape stripped off.
     */
    public static String material(String path, Collection<String> woodTypes) {
        String best = null;
        for (String wood : woodTypes) {
            if (names(path, wood) && (best == null || wood.length() > best.length())) {
                best = wood;
            }
        }
        if (best != null) {
            return best;
        }
        return strip(path);
    }

    /** {@code path} with working words and shapes taken off until none is left; never empty. */
    public static String strip(String path) {
        String out = path;
        boolean changed = true;
        while (changed) {
            changed = false;
            for (String prefix : PREFIXES) {
                if (out.startsWith(prefix) && out.length() > prefix.length()) {
                    out = out.substring(prefix.length());
                    changed = true;
                }
            }
            for (String suffix : SUFFIXES) {
                if (out.endsWith(suffix) && out.length() > suffix.length()) {
                    out = out.substring(0, out.length() - suffix.length());
                    changed = true;
                }
            }
        }
        return out;
    }

    /** Whether {@code path} has {@code word} as whole underscore-separated words. */
    private static boolean names(String path, String word) {
        if (word.isEmpty()) {
            return false;
        }
        int at = path.indexOf(word);
        while (at >= 0) {
            int end = at + word.length();
            boolean starts = at == 0 || path.charAt(at - 1) == '_';
            boolean ends = end == path.length() || path.charAt(end) == '_';
            if (starts && ends) {
                return true;
            }
            at = path.indexOf(word, at + 1);
        }
        return false;
    }
}
