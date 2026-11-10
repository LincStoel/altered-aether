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

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static com.linc.alteredaether.compat.DeepAetherWhalePatches.AERWHALE;
import static com.linc.alteredaether.compat.DeepAetherWhalePatches.AERWHALE_MODEL;
import static com.linc.alteredaether.compat.DeepAetherWhalePatches.KNOWN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Deep Aether's aerwhale patches are cancelled by name or by target, and any change from the known set is reported. */
class DeepAetherWhalePatchesTest {
    private static final String DA = DeepAetherWhalePatches.PACKAGE + "mixin.";

    @Test
    void cancelsTheKnownPatches() {
        for (DeepAetherWhalePatches.Patch patch : KNOWN) {
            assertTrue(DeepAetherWhalePatches.shouldCancel(List.of(patch.target()), patch.mixin()), patch.mixin());
        }
    }

    @Test
    void cancelsANewPatchOfOnlyTheAerwhale() {
        assertTrue(DeepAetherWhalePatches.shouldCancel(List.of(AERWHALE), DA + "whale.NewAerwhaleMixin"));
        assertTrue(DeepAetherWhalePatches.shouldCancel(List.of(AERWHALE, AERWHALE_MODEL), DA + "BothMixin"));
    }

    @Test
    void sparesOtherMixins() {
        String moa = "com.aetherteam.aether.entity.passive.Moa";
        assertFalse(DeepAetherWhalePatches.shouldCancel(List.of(moa), DA + "MoaMixin"));
        assertFalse(DeepAetherWhalePatches.shouldCancel(List.of(AERWHALE, moa), DA + "MixedMixin"));
        assertTrue(DeepAetherWhalePatches.spared().contains(DA + "MixedMixin"));
        assertFalse(DeepAetherWhalePatches.spared().contains(DA + "MoaMixin"));
        // Another mod's aerwhale mixin is none of our business.
        assertFalse(DeepAetherWhalePatches.shouldCancel(List.of(AERWHALE), "com.example.mixin.AerwhaleMixin"));
    }

    @Test
    void reportsNothingWhenAllMatch() {
        Set<String> cancelled = Set.copyOf(KNOWN.stream().map(DeepAetherWhalePatches.Patch::mixin).toList());
        assertEquals(List.of(), DeepAetherWhalePatches.problems(KNOWN, cancelled, Set.of()));
    }

    @Test
    void reportsAMissingANewAndASparedPatch() {
        DeepAetherWhalePatches.Patch first = KNOWN.getFirst();
        List<String> problems = DeepAetherWhalePatches.problems(List.of(first), Set.of(DA + "NewAerwhaleMixin"),
                Set.of(DA + "MixedMixin"));
        assertEquals(3, problems.size(), problems.toString());
        assertTrue(problems.get(0).startsWith("AerwhaleMixin was not found"), problems.get(0));
        assertTrue(problems.get(1).startsWith("NewAerwhaleMixin is new"), problems.get(1));
        assertTrue(problems.get(2).startsWith("MixedMixin patches the aerwhale along with other classes"),
                problems.get(2));
    }

    @Test
    void onlyChecksPatchesOfTheSideThatLoaded() {
        // A dedicated server has no models or renderer: only the entity's patch is expected there.
        List<DeepAetherWhalePatches.Patch> server = KNOWN.stream().filter(patch -> !patch.client()).toList();
        assertEquals(List.of(), DeepAetherWhalePatches.problems(server, Set.of(server.getFirst().mixin()), Set.of()));
    }
}
