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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Which of Deep Aether's mixins to cancel when our aerwhales are in use, and a record of what was cancelled, so a Deep
 * Aether update that renames, adds or reshapes its aerwhale patches is noticed (see
 * {@link #problems(Collection, Collection, Collection)}).
 *
 * <p>A Deep Aether mixin is cancelled if it is one of the {@link #KNOWN} patches, or if every class it targets is one
 * of the Aether's aerwhale classes: a new aerwhale patch is cancelled too, rather than layered over our whales. One
 * that targets an aerwhale class along with others is left alone, since cancelling it would undo its other changes,
 * and reported instead.
 *
 * <p>Plain Java, with no Minecraft types: MixinSquared asks about each mixin as its target class loads, before most of
 * the game exists.
 */
public final class DeepAetherWhalePatches {
    public static final String PACKAGE = "io.github.razordevs.deep_aether.";
    private static final String MIXINS = PACKAGE + "mixin.entity.";

    public static final String AERWHALE = "com.aetherteam.aether.entity.passive.Aerwhale";
    public static final String AERWHALE_MODEL = "com.aetherteam.aether.client.renderer.entity.model.AerwhaleModel";
    public static final String CLASSIC_AERWHALE_MODEL =
            "com.aetherteam.aether.client.renderer.entity.model.ClassicAerwhaleModel";
    public static final String AERWHALE_RENDERER = "com.aetherteam.aether.client.renderer.entity.AerwhaleRenderer";

    /** The Aether's aerwhale classes: the entity, its two models and its renderer. */
    public static final Set<String> TARGETS = Set.of(AERWHALE, AERWHALE_MODEL, CLASSIC_AERWHALE_MODEL,
            AERWHALE_RENDERER);

    /** One of Deep Aether's aerwhale mixins, the class it patches, and whether it exists only on the client. */
    public record Patch(String mixin, String target, boolean client) {
    }

    /** Deep Aether's aerwhale patches as of 1.1.5.1. */
    public static final List<Patch> KNOWN = List.of(
            new Patch(MIXINS + "AerwhaleMixin", AERWHALE, false),
            new Patch(MIXINS + "AerwhaleModelMixin", AERWHALE_MODEL, true),
            new Patch(MIXINS + "ClassicAerwhaleModelMixin", CLASSIC_AERWHALE_MODEL, true),
            new Patch(MIXINS + "AerwhaleRendererMixin", AERWHALE_RENDERER, true));

    private static final Set<String> CANCELLED = ConcurrentHashMap.newKeySet();
    private static final Set<String> SPARED = ConcurrentHashMap.newKeySet();

    /**
     * MixinSquared's question, asked of every mixin as its target loads: whether to drop it. Records the answer for
     * {@link #cancelled()} and {@link #spared()}.
     */
    public static boolean shouldCancel(List<String> targets, String mixin) {
        if (!mixin.startsWith(PACKAGE)) {
            return false;
        }
        boolean known = KNOWN.stream().anyMatch(patch -> patch.mixin().equals(mixin));
        if (known || !targets.isEmpty() && TARGETS.containsAll(targets)) {
            CANCELLED.add(mixin);
            return true;
        }
        if (targets.stream().anyMatch(TARGETS::contains)) {
            SPARED.add(mixin);
        }
        return false;
    }

    /** The Deep Aether mixins cancelled so far. */
    public static Set<String> cancelled() {
        return Set.copyOf(CANCELLED);
    }

    /** The Deep Aether mixins that patch an aerwhale class along with others, so were left in place. */
    public static Set<String> spared() {
        return Set.copyOf(SPARED);
    }

    /**
     * What doesn't match {@link #KNOWN}, one line each, or nothing if all is as expected.
     *
     * @param expected  the known patches whose targets have loaded on this side
     * @param cancelled what was cancelled
     * @param spared    what was left in place though it touches an aerwhale class
     */
    public static List<String> problems(Collection<Patch> expected, Collection<String> cancelled,
                                        Collection<String> spared) {
        List<String> problems = new ArrayList<>();
        for (Patch patch : expected) {
            if (!cancelled.contains(patch.mixin())) {
                problems.add(simpleName(patch.mixin()) + " was not found: Deep Aether has renamed or removed it");
            }
        }
        for (String mixin : cancelled) {
            if (KNOWN.stream().noneMatch(patch -> patch.mixin().equals(mixin))) {
                problems.add(simpleName(mixin) + " is new; it patches only the aerwhale, so it was cancelled too");
            }
        }
        for (String mixin : spared) {
            problems.add(simpleName(mixin) + " patches the aerwhale along with other classes, so it was left in place");
        }
        return problems;
    }

    private static String simpleName(String className) {
        return className.substring(className.lastIndexOf('.') + 1);
    }

    private DeepAetherWhalePatches() {
    }
}
