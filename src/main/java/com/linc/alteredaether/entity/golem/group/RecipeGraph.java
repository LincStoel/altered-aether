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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Which item each item is made from, followed back to the material at the root: spruce stairs are made from spruce
 * planks, made from spruce logs. Pure: recipes come in as plain {@link Recipe}s of item ids.
 *
 * <p>An item's parent is the ingredient filling more than half its recipe's slots, if that ingredient is one of at
 * most {@value #MAX_OPTIONS} items: a fence (four planks, two sticks) is made from planks, but andesite alloy (two
 * andesite, two nuggets) and a chest (eight of any planks) are made from nothing in particular. Of several recipes,
 * stonecutting counts first, then the one with the fewest different ingredients, then the first by id.
 */
public final class RecipeGraph {
    /** The most items an ingredient may allow and still count as one material. */
    public static final int MAX_OPTIONS = 4;

    /**
     * One recipe.
     *
     * @param id          its id, to break ties
     * @param stonecutter whether it is a stonecutting recipe
     * @param output      the item it makes
     * @param ingredients each filled slot's choice of items (an ingredient matching a tag lists the tag's items)
     */
    public record Recipe(String id, boolean stonecutter, String output, List<List<String>> ingredients) {
    }

    private final Map<String, String> parents = new HashMap<>();
    private final Map<String, String> roots = new HashMap<>();

    public RecipeGraph(List<Recipe> recipes) {
        Map<String, Candidate> best = new HashMap<>();
        for (Recipe recipe : recipes) {
            Candidate candidate = candidate(recipe);
            if (candidate == null) {
                continue;
            }
            Candidate current = best.get(recipe.output());
            if (current == null || candidate.compareTo(current) < 0) {
                best.put(recipe.output(), candidate);
            }
        }
        best.forEach((output, candidate) -> parents.put(output, candidate.parent));
    }

    private record Candidate(String parent, int rank, int distinct, String id) implements Comparable<Candidate> {
        @Override
        public int compareTo(Candidate o) {
            if (rank != o.rank) {
                return Integer.compare(rank, o.rank);
            }
            if (distinct != o.distinct) {
                return Integer.compare(distinct, o.distinct);
            }
            return id.compareTo(o.id);
        }
    }

    private static Candidate candidate(Recipe recipe) {
        Map<List<String>, Integer> counts = new LinkedHashMap<>();
        int filled = 0;
        for (List<String> options : recipe.ingredients()) {
            if (options.isEmpty()) {
                continue;
            }
            counts.merge(options, 1, Integer::sum);
            filled++;
        }
        for (Map.Entry<List<String>, Integer> entry : counts.entrySet()) {
            List<String> options = entry.getKey();
            if (entry.getValue() * 2 > filled && options.size() <= MAX_OPTIONS && !options.contains(recipe.output())) {
                return new Candidate(options.get(0), recipe.stonecutter() ? 0 : 1, counts.size(), recipe.id());
            }
        }
        return null;
    }

    /** What {@code item} is made from, or null. */
    public String parent(String item) {
        return parents.get(item);
    }

    /**
     * The material {@code item} is made of at root: its parent's root, or itself if it is made from nothing in
     * particular. Items made from each other (a block from ingots, ingots from the block) share the root that sorts
     * first.
     */
    public String root(String item) {
        String cached = roots.get(item);
        if (cached != null) {
            return cached;
        }
        List<String> path = new ArrayList<>();
        String at = item;
        String root;
        while (true) {
            int seen = path.indexOf(at);
            if (seen >= 0) {
                root = at;
                for (String member : path.subList(seen, path.size())) {
                    if (member.compareTo(root) < 0) {
                        root = member;
                    }
                }
                break;
            }
            String known = roots.get(at);
            if (known != null) {
                root = known;
                break;
            }
            path.add(at);
            String parent = parents.get(at);
            if (parent == null) {
                root = at;
                break;
            }
            at = parent;
        }
        for (String member : path) {
            roots.put(member, root);
        }
        return root;
    }

    /** {@code item} and what it is made from, back to its root. */
    public List<String> chain(String item) {
        List<String> chain = new ArrayList<>();
        String at = item;
        while (at != null && !chain.contains(at)) {
            chain.add(at);
            at = parents.get(at);
        }
        return chain;
    }
}
