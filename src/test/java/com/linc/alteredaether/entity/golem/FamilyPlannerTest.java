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
package com.linc.alteredaether.entity.golem;

import com.linc.alteredaether.entity.golem.sort.Move;
import com.linc.alteredaether.entity.golem.sort.Planner;
import com.linc.alteredaether.entity.golem.sort.SortMode;
import com.linc.alteredaether.entity.golem.sort.SortSnapshot;
import com.linc.alteredaether.entity.golem.sort.SortSnapshot.Kind;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tidy mode over a real family tree: families get chests as fine-grained as the storage allows, small ones stay with
 * the miscellaneous items, crates take those first, drawers keep what they hold and take the items filling the most
 * stacks, sinks are fed only what they name, and items with nowhere at all to go are reported stuck.
 */
class FamilyPlannerTest {
    /**
     * root ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡Ãƒâ€šÃ‚Â¬ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Âº overworld ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡Ãƒâ€šÃ‚Â¬ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Âº blocks ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡Ãƒâ€šÃ‚Â¬ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Âº wood ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡Ãƒâ€šÃ‚Â¬ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Âº {spruce, oak}; ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡Ãƒâ€šÃ‚Â¬ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Âº stone ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡Ãƒâ€šÃ‚Â¬ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Âº andesite; overworld ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡Ãƒâ€šÃ‚Â¬ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Âº resources ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡Ãƒâ€šÃ‚Â¬ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Âº iron;
     * root ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡Ãƒâ€šÃ‚Â¬ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Âº aether ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡Ãƒâ€šÃ‚Â¬ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Âº blocks ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡Ãƒâ€šÃ‚Â¬ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Âº other ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡Ãƒâ€šÃ‚Â¬ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Âº gravitite.
     */
    private static final List<SortSnapshot.Node> NODES = List.of(
            new SortSnapshot.Node(-1, "misc"),          // 0
            new SortSnapshot.Node(0, "overworld"),      // 1
            new SortSnapshot.Node(1, "blocks"),         // 2
            new SortSnapshot.Node(2, "wood"),           // 3
            new SortSnapshot.Node(3, "spruce"),         // 4
            new SortSnapshot.Node(3, "oak"),            // 5
            new SortSnapshot.Node(2, "stone"),          // 6
            new SortSnapshot.Node(6, "andesite"),       // 7
            new SortSnapshot.Node(1, "resources"),      // 8
            new SortSnapshot.Node(8, "iron"),           // 9
            new SortSnapshot.Node(0, "aether"),         // 10
            new SortSnapshot.Node(10, "blocks"),        // 11
            new SortSnapshot.Node(11, "other"),         // 12
            new SortSnapshot.Node(12, "gravitite"));    // 13

    private static final int PLANKS = 0, STAIRS = 1, STRIPPED = 2, HOLLOW = 3, GRAVITITE = 4, OAK = 5, ANDESITE = 6,
            IRON = 7, COAL = 8;
    private static final List<SortSnapshot.Key> KEYS = List.of(
            key(4, 0), key(4, 1), key(4, 2), key(4, 3), key(13, 4), key(5, 5), key(7, 6), key(9, 7), key(9, 8));

    /** As the config has it by default: a family needs a quarter of a chest to get one. */
    private static final Planner.Settings SETTINGS = new Planner.Settings(3, 0.25, 2);

    private static SortSnapshot.Key key(int node, int id) {
        return new SortSnapshot.Key(64, node, ((long) node << 20) | id);
    }

    private static SortSnapshot.Inv sized(int size, int... keyCount) {
        int[] key = new int[size], count = new int[size], limit = new int[size];
        Arrays.fill(key, -1);
        Arrays.fill(limit, 64);
        for (int i = 0; i + 1 < keyCount.length; i += 2) {
            key[i / 2] = keyCount[i];
            count[i / 2] = keyCount[i + 1];
        }
        return new SortSnapshot.Inv(false, true, limit, key, count, Set.of());
    }

    private static SortSnapshot.Inv chest(int... keyCount) {
        return sized(27, keyCount);
    }

    /** {@code stacks} full stacks of {@code key}, as key/count pairs. */
    private static int[] stacks(int key, int stacks) {
        int[] out = new int[stacks * 2];
        for (int s = 0; s < stacks; s++) {
            out[s * 2] = key;
            out[s * 2 + 1] = 64;
        }
        return out;
    }

    private static int[] concat(int[]... parts) {
        int length = 0;
        for (int[] part : parts) {
            length += part.length;
        }
        int[] out = new int[length];
        int at = 0;
        for (int[] part : parts) {
            System.arraycopy(part, 0, out, at, part.length);
            at += part.length;
        }
        return out;
    }

    /** The first or second half of key/count pairs. */
    private static int[] half(int[] pairs, int which) {
        int mid = pairs.length / 4 * 2;
        return which == 0 ? Arrays.copyOfRange(pairs, 0, mid) : Arrays.copyOfRange(pairs, mid, pairs.length);
    }

    private static int[] fill(int size, int value) {
        int[] out = new int[size];
        Arrays.fill(out, value);
        return out;
    }

    private static SortSnapshot.Inv crate(int capacity, int... keyCount) {
        SortSnapshot.Inv inv = sized(40, keyCount);
        return new SortSnapshot.Inv(Kind.MISC, false, inv.limit(), inv.key(), inv.count(), fill(40, SortSnapshot.ANY),
                capacity, Set.of(), false, false, -1);
    }

    private static SortSnapshot.Inv drawer(Kind kind, int slots, int limit, int... keyCount) {
        SortSnapshot.Inv inv = sized(slots, keyCount);
        Arrays.fill(inv.limit(), limit);
        return new SortSnapshot.Inv(kind, false, inv.limit(), inv.key(), inv.count(), fill(slots, SortSnapshot.ANY),
                Integer.MAX_VALUE, Set.of(), false, false, -1);
    }

    private static SortSnapshot snapshot(SortSnapshot.Inv... invs) {
        return new SortSnapshot(KEYS, List.of(invs), NODES);
    }

    /** Applies plans until one is empty; returns the rounds taken. */
    private static int settle(SortSnapshot snapshot, SortMode mode) {
        Map<Integer, Long> before = totals(snapshot);
        for (int round = 1; round <= 8; round++) {
            Planner.Plan plan = Planner.plan(snapshot, mode, Planner.Focus.ALL, SETTINGS);
            if (plan.isEmpty()) {
                return round;
            }
            for (Move move : plan.moves()) {
                assertTrue(Planner.apply(snapshot, move), "move doesn't fit: " + move);
                SortSnapshot.Inv src = snapshot.invs().get(move.srcInv());
                SortSnapshot.Inv dst = snapshot.invs().get(move.dstInv());
                assertTrue(dst.count()[move.dstSlot()] <= dst.limit()[move.dstSlot()], "overfilled a slot: " + move);
                assertFalse(dst.source(), "moved into a source: " + move);
                assertTrue(src.kind() != Kind.SINK && src.kind() != Kind.POOLED, "moved out of " + src.kind() + ": " + move);
                assertTrue(dst.total() <= dst.capacity(), "over capacity: " + move);
            }
            assertEquals(before, totals(snapshot), "a plan made or lost items");
        }
        return Integer.MAX_VALUE;
    }

    private static Map<Integer, Long> totals(SortSnapshot snapshot) {
        Map<Integer, Long> totals = new HashMap<>();
        for (SortSnapshot.Inv inv : snapshot.invs()) {
            for (int slot = 0; slot < inv.size(); slot++) {
                if (inv.key()[slot] >= 0) {
                    totals.merge(inv.key()[slot], (long) inv.count()[slot], Long::sum);
                }
            }
        }
        return totals;
    }

    private static Set<Integer> keysIn(SortSnapshot.Inv inv) {
        Set<Integer> keys = new HashSet<>();
        for (int k : inv.key()) {
            if (k >= 0) {
                keys.add(k);
            }
        }
        return keys;
    }

    private static long count(SortSnapshot.Inv inv, int key) {
        long n = 0;
        for (int slot = 0; slot < inv.size(); slot++) {
            if (inv.key()[slot] == key) {
                n += inv.count()[slot];
            }
        }
        return n;
    }

    private static String dump(SortSnapshot snapshot) {
        StringBuilder out = new StringBuilder();
        for (SortSnapshot.Inv inv : snapshot.invs()) {
            Map<Integer, Long> counts = new java.util.TreeMap<>();
            for (int slot = 0; slot < inv.size(); slot++) {
                if (inv.key()[slot] >= 0) {
                    counts.merge(inv.key()[slot], (long) inv.count()[slot], Long::sum);
                }
            }
            out.append("\n  ").append(inv.kind()).append(' ').append(counts);
        }
        return out.toString();
    }

    private static int chestWith(SortSnapshot snapshot, Set<Integer> keys) {
        for (int i = 0; i < snapshot.invs().size(); i++) {
            if (keysIn(snapshot.invs().get(i)).equals(keys)) {
                return i;
            }
        }
        return -1;
    }

    @Test
    void theSpruceComesTogetherAndTheOddsAndEndsShareAChest() {
        // as the bug report had it: spruce strewn across three chests with enchanted gravitite and odds and ends
        SortSnapshot snapshot = snapshot(
                chest(STAIRS, 64, STAIRS, 64, PLANKS, 64, HOLLOW, 32, GRAVITITE, 16),
                chest(STRIPPED, 64, STRIPPED, 64, STRIPPED, 64),
                chest(PLANKS, 40, OAK, 3, ANDESITE, 5, IRON, 7));
        assertTrue(settle(snapshot, SortMode.TIDY) <= 4, "didn't settle");
        int spruce = chestWith(snapshot, Set.of(PLANKS, STAIRS, STRIPPED, HOLLOW));
        assertTrue(spruce >= 0, "the spruce isn't together: " + dump(snapshot));
        assertEquals(104, count(snapshot.invs().get(spruce), PLANKS), "planks left behind");
        assertTrue(chestWith(snapshot, Set.of(GRAVITITE, OAK, ANDESITE, IRON)) >= 0,
                "the odds and ends don't share a chest: " + dump(snapshot));
    }

    @Test
    void moreChestsSortFiner() {
        int[] everything = concat(stacks(PLANKS, 10), stacks(OAK, 10), stacks(ANDESITE, 10), stacks(IRON, 10),
                stacks(GRAVITITE, 10));
        // three chests: room only to keep the aether apart from the overworld
        List<SortSnapshot.Inv> few = new ArrayList<>();
        few.add(sized(27, half(everything, 0)));
        few.add(sized(27, half(everything, 1)));
        few.add(sized(27));
        SortSnapshot coarse = new SortSnapshot(KEYS, few, NODES);
        settle(coarse, SortMode.TIDY);
        assertTrue(chestWith(coarse, Set.of(GRAVITITE)) >= 0, "the aether isn't apart: " + dump(coarse));

        // eight: a chest for each material
        List<SortSnapshot.Inv> many = new ArrayList<>();
        many.add(sized(27, half(everything, 0)));
        many.add(sized(27, half(everything, 1)));
        for (int i = 0; i < 6; i++) {
            many.add(sized(27));
        }
        SortSnapshot fine = new SortSnapshot(KEYS, many, NODES);
        assertTrue(settle(fine, SortMode.TIDY) <= 4, "didn't settle");
        for (int key : List.of(PLANKS, OAK, ANDESITE, IRON, GRAVITITE)) {
            assertTrue(chestWith(fine, Set.of(key)) >= 0, "key " + key + " isn't on its own: " + dump(fine));
        }
    }

    @Test
    void aFamilyTooSmallForAChestStaysWithTheOddsAndEnds() {
        SortSnapshot snapshot = snapshot(
                chest(concat(stacks(PLANKS, 10), new int[]{OAK, 20, IRON, 5})),
                chest(), chest(), chest(), chest());
        settle(snapshot, SortMode.TIDY);
        int used = 0;
        for (SortSnapshot.Inv inv : snapshot.invs()) {
            used += inv.isEmpty() ? 0 : 1;
        }
        assertEquals(2, used, "a one-slot family took a chest of its own: " + dump(snapshot));
        assertTrue(chestWith(snapshot, Set.of(OAK, IRON)) >= 0, dump(snapshot));
    }

    @Test
    void cratesTakeTheOddsAndEndsFirstUpToTheirCapacity() {
        SortSnapshot snapshot = snapshot(
                chest(concat(stacks(PLANKS, 10), new int[]{OAK, 5, ANDESITE, 9, IRON, 7})),
                crate(15),
                chest());
        settle(snapshot, SortMode.TIDY);
        SortSnapshot.Inv crate = snapshot.invs().get(1);
        assertTrue(crate.total() <= 15, "over the crate's capacity");
        assertTrue(keysIn(crate).size() >= 2, "the odds and ends didn't go to the crate: " + dump(snapshot));
        assertFalse(keysIn(crate).contains(PLANKS), "spruce in the crate");
    }

    @Test
    void whatHasAHomeLeavesTheCrate() {
        SortSnapshot snapshot = snapshot(crate(640, stacks(PLANKS, 10)), chest());
        settle(snapshot, SortMode.TIDY);
        assertEquals(640, count(snapshot.invs().get(1), PLANKS), "the spruce stayed in the crate");
    }

    @Test
    void aDrawerKeepsItsItemAndTakesTheRest() {
        for (SortMode mode : SortMode.values()) {
            SortSnapshot snapshot = snapshot(
                    drawer(Kind.BULK, 1, 2048, IRON, 100),
                    chest(IRON, 64, IRON, 30, OAK, 5));
            settle(snapshot, mode);
            assertEquals(194, count(snapshot.invs().get(0), IRON), mode + ": the iron didn't go to its drawer");
            assertEquals(Set.of(OAK), keysIn(snapshot.invs().get(1)), mode + "");
        }
    }

    @Test
    void anEmptyDrawerTakesTheItemsFillingTheMostStacks() {
        for (SortMode mode : SortMode.values()) {
            SortSnapshot.Inv drawer = drawer(Kind.BULK, 3, 2048);
            drawer.only()[2] = SortSnapshot.NONE; // a locked slot
            SortSnapshot snapshot = snapshot(drawer,
                    chest(concat(stacks(ANDESITE, 5), stacks(PLANKS, 3), new int[]{GRAVITITE, 64})));
            settle(snapshot, mode);
            SortSnapshot.Inv after = snapshot.invs().get(0);
            assertEquals(320, count(after, ANDESITE), mode + ": the andesite isn't in the drawer");
            assertEquals(192, count(after, PLANKS), mode + ": the planks aren't in the drawer");
            assertEquals(-1, after.key()[2], mode + ": the locked slot was filled");
            assertEquals(64, count(snapshot.invs().get(1), GRAVITITE), mode + ": one stack isn't worth a drawer");
        }
    }

    @Test
    void aCompactingDrawerOnlyTakesIn() {
        for (SortMode mode : SortMode.values()) {
            SortSnapshot snapshot = snapshot(
                    drawer(Kind.POOLED, 2, 4096, IRON, 500, ANDESITE, 3),
                    chest(IRON, 20),
                    chest());
            settle(snapshot, mode);
            assertEquals(520, count(snapshot.invs().get(0), IRON), mode + ": the iron didn't pool");
            assertEquals(3, count(snapshot.invs().get(0), ANDESITE), mode + ": a compacting drawer gave something up");
        }
    }

    @Test
    void aSinkIsFedOnlyWhatItNamesUpToItsRoom() {
        for (SortMode mode : SortMode.values()) {
            int[] limit = {32}, key = {-1}, count = {0}, only = {COAL};
            SortSnapshot.Inv sink = new SortSnapshot.Inv(Kind.SINK, false, limit, key, count, only, Integer.MAX_VALUE,
                    Set.of(COAL), true, false, -1);
            SortSnapshot snapshot = snapshot(sink, chest(COAL, 64, IRON, 64), chest(COAL, 10));
            Planner.Plan plan = Planner.plan(snapshot, mode, Planner.Focus.ALL, SETTINGS);
            long fed = 0;
            for (Move move : plan.moves()) {
                if (move.dstInv() == 0) {
                    assertEquals(COAL, move.key(), mode + ": the sink was fed something else");
                    fed += move.count();
                }
                assertTrue(move.srcInv() != 0, mode + ": taken out of a sink");
            }
            assertEquals(32, fed, mode + ": not topped up");
            settle(snapshot, mode);
            assertEquals(32, count(snapshot.invs().get(0), COAL), mode + ": overfed");
        }
    }

    @Test
    void anItemWithNowhereToGoIsReportedStuck() {
        SortSnapshot.Inv spruce = chest(PLANKS, 64, IRON, 10);
        SortSnapshot.Inv stone = chest(ANDESITE, 64);
        SortSnapshot snapshot = snapshot(
                new SortSnapshot.Inv(false, true, spruce.limit(), spruce.key(), spruce.count(), Set.of(PLANKS)),
                new SortSnapshot.Inv(false, true, stone.limit(), stone.key(), stone.count(), Set.of(ANDESITE)));
        Planner.Plan plan = Planner.plan(snapshot, SortMode.TIDY, Planner.Focus.ALL, SETTINGS);
        assertTrue(plan.stuck().contains(0), "the iron has no home but isn't reported stuck");
    }

    @Test
    void spareChestsGoToSmallFamiliesBeforeTheyMix() {
        // five families of three stacks, under a quarter of a chest each: with chests to spare, each gets its own
        SortSnapshot snapshot = snapshot(
                chest(concat(stacks(PLANKS, 3), stacks(OAK, 3), stacks(ANDESITE, 3), stacks(IRON, 3),
                        stacks(GRAVITITE, 3))),
                chest(), chest(), chest(), chest(), chest());
        assertTrue(settle(snapshot, SortMode.TIDY) <= 4, "didn't settle");
        for (int key : List.of(PLANKS, OAK, ANDESITE, IRON, GRAVITITE)) {
            assertTrue(chestWith(snapshot, Set.of(key)) >= 0, "key " + key + " isn't on its own: " + dump(snapshot));
        }
        // with only two chests they share, coarsely: the blocks together, the rest with the odds and ends
        SortSnapshot few = snapshot(
                chest(concat(stacks(PLANKS, 3), stacks(OAK, 3), stacks(ANDESITE, 3), stacks(IRON, 3),
                        stacks(GRAVITITE, 3))),
                chest());
        settle(few, SortMode.TIDY);
        assertTrue(chestWith(few, Set.of(PLANKS, OAK, ANDESITE)) >= 0, "the blocks aren't together: " + dump(few));
    }

    @Test
    void aClaimedFamilyHoldsOnWhenItShrinks() {
        // spruce fills a slot: less than the two a spare chest asks, but a chest already kept for it keeps it while
        // it fills half that
        SortSnapshot fresh = snapshot(chest(PLANKS, 64, IRON, 64), chest());
        assertTrue(Planner.plan(fresh, SortMode.TIDY, Planner.Focus.ALL, SETTINGS).claims()[0] != 4,
                "too small for a chest of its own");
        SortSnapshot kept = snapshot(chest(PLANKS, 64, IRON, 64).with(false, 4), chest());
        assertEquals(4, Planner.plan(kept, SortMode.TIDY, Planner.Focus.ALL, SETTINGS).claims()[0],
                "the spruce chest was given up");
    }
}
