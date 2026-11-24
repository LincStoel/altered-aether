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
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The golem's planner settles (planning again after its moves finds nothing), never makes or loses an item, respects
 * slot sizes, un-mixes a dump chest, follows pins (strict ones too), and empties source inventories.
 */
class PlannerTest {
    /** Keys 0-1 stone (group 0), 2-3 logs (group 1), 4-9 one-offs (groups 10-15), 10 a tool (stack 1, group 2). */
    private static final List<SortSnapshot.Key> KEYS = List.of(
            key(64, 0), key(64, 0), key(64, 1), key(64, 1),
            key(64, 10), key(64, 11), key(64, 12), key(64, 13), key(64, 14), key(64, 15),
            key(1, 2));
    /** The nodes a flat snapshot of {@link #KEYS} gives stone and logs. */
    private static final int STONE = 1, LOGS = 2;

    private static SortSnapshot.Key key(int stack, int group) {
        return new SortSnapshot.Key(stack, group, group * 1000L);
    }

    private static SortSnapshot.Inv inv(int size, boolean source, Set<Integer> pins, int... keyCount) {
        int[] key = new int[size];
        int[] count = new int[size];
        int[] limit = new int[size];
        Arrays.fill(key, -1);
        Arrays.fill(limit, 64);
        for (int i = 0; i + 1 < keyCount.length; i += 2) {
            key[i / 2] = keyCount[i];
            count[i / 2] = keyCount[i + 1];
        }
        return new SortSnapshot.Inv(source, true, limit, key, count, pins);
    }

    private static SortSnapshot.Inv chest(int... keyCount) {
        return inv(27, false, Set.of(), keyCount);
    }

    /** Applies the plan (and each plain chest's reorder) and returns how many rounds it took to settle. */
    private static int settle(SortSnapshot snapshot, SortMode mode) {
        Map<Integer, Long> before = totals(snapshot);
        for (int round = 1; round <= 6; round++) {
            Planner.Plan plan = Planner.plan(snapshot, mode);
            if (plan.isEmpty()) {
                List<SortSnapshot.Inv> invs = new ArrayList<>(snapshot.invs());
                boolean changed = false;
                for (int i = 0; i < invs.size(); i++) {
                    SortSnapshot.Inv ordered = Planner.orderWithin(invs.get(i), snapshot.keys());
                    if (ordered != null) {
                        copyInto(ordered, invs.get(i));
                        changed = true;
                    }
                }
                assertEquals(before, totals(snapshot), "reordering made or lost items");
                if (!changed || Planner.plan(snapshot, mode).isEmpty()) {
                    return round;
                }
                continue;
            }
            for (Move move : plan.moves()) {
                assertTrue(Planner.apply(snapshot, move), "move doesn't fit: " + move);
                SortSnapshot.Inv dst = snapshot.invs().get(move.dstInv());
                int cap = Math.min(dst.limit()[move.dstSlot()], snapshot.keys().get(move.key()).maxStack());
                assertTrue(dst.count()[move.dstSlot()] <= cap, "overfilled a slot: " + move);
                assertFalse(dst.source(), "moved into a source: " + move);
            }
            assertEquals(before, totals(snapshot), "a plan made or lost items");
        }
        return Integer.MAX_VALUE;
    }

    private static void copyInto(SortSnapshot.Inv from, SortSnapshot.Inv to) {
        System.arraycopy(from.key(), 0, to.key(), 0, from.size());
        System.arraycopy(from.count(), 0, to.count(), 0, from.size());
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

    @Test
    void tidySeparatesMixedChests() {
        SortSnapshot snapshot = new SortSnapshot(KEYS, List.of(
                chest(0, 64, 0, 64, 0, 64, 2, 10, 2, 10),
                chest(2, 64, 2, 64, 2, 64, 0, 5),
                chest()));
        assertTrue(settle(snapshot, SortMode.TIDY) <= 2);
        assertEquals(Set.of(0), keysIn(snapshot.invs().get(0)));
        assertEquals(Set.of(2), keysIn(snapshot.invs().get(1)));
    }

    @Test
    void tidyUnmixesADumpChest() {
        SortSnapshot snapshot = new SortSnapshot(KEYS, List.of(
                chest(0, 64, 0, 64, 2, 64, 2, 64, 4, 1, 5, 1, 6, 1, 7, 1),
                chest(), chest(), chest()));
        assertTrue(settle(snapshot, SortMode.TIDY) <= 3);
        // stone and logs (two stacks each, enough for a spare chest) get a chest of their own, the one-offs share one
        int stone = -1, logs = -1, misc = -1;
        for (int i = 0; i < snapshot.invs().size(); i++) {
            Set<Integer> keys = keysIn(snapshot.invs().get(i));
            if (keys.equals(Set.of(0))) {
                stone = i;
            } else if (keys.equals(Set.of(2))) {
                logs = i;
            } else if (keys.equals(Set.of(4, 5, 6, 7))) {
                misc = i;
            }
        }
        assertTrue(stone >= 0 && logs >= 0 && misc >= 0, "not un-mixed: " + snapshot.invs());
    }

    @Test
    void aPinnedEmptyChestDrawsItsItemAndGroup() {
        for (SortMode mode : SortMode.values()) {
            SortSnapshot snapshot = new SortSnapshot(KEYS, List.of(
                    chest(0, 64, 1, 32, 2, 64),
                    inv(27, false, Set.of(0))));
            settle(snapshot, mode);
            SortSnapshot.Inv pinned = snapshot.invs().get(1);
            assertEquals(64, count(pinned, 0), mode + ": the pinned item");
            if (mode == SortMode.TIDY) {
                assertEquals(32, count(pinned, 1), "the pinned item's group");
                assertEquals(0, count(pinned, 2), "nothing else");
            }
        }
    }

    private static SortSnapshot.Inv strict(Set<Integer> pins, int... keyCount) {
        SortSnapshot.Inv inv = inv(27, false, pins, keyCount);
        return new SortSnapshot.Inv(false, true, inv.limit(), inv.key(), inv.count(), pins, true);
    }

    @Test
    void aStrictChestTakesOnlyItsItemAndSendsTheRestAway() {
        for (SortMode mode : SortMode.values()) {
            SortSnapshot snapshot = new SortSnapshot(KEYS, List.of(
                    chest(0, 64, 1, 32, 2, 64),
                    strict(Set.of(0), 3, 10, 4, 1, 1, 5),
                    chest()));
            assertTrue(settle(snapshot, mode) <= 4, mode + " didn't settle");
            SortSnapshot.Inv pinned = snapshot.invs().get(1);
            assertEquals(Set.of(0), keysIn(pinned), mode + ": " + snapshot.invs());
            assertEquals(64, count(pinned, 0), mode + ": the pinned item");
        }
    }

    @Test
    void aStrictChestKeepsWhatHasNowhereElseToGo() {
        for (SortMode mode : SortMode.values()) {
            SortSnapshot snapshot = new SortSnapshot(KEYS, List.of(
                    inv(1, false, Set.of(), 4, 64),
                    strict(Set.of(0), 0, 10, 2, 7)));
            assertTrue(settle(snapshot, mode) <= 2, mode + " didn't settle");
            assertEquals(7, count(snapshot.invs().get(1), 2), mode + ": the logs had nowhere to go");
        }
    }

    @Test
    void aStrictChestIsNoHomeForItsItemsFamily() {
        SortSnapshot snapshot = new SortSnapshot(KEYS, List.of(
                chest(1, 64, 1, 64),
                strict(Set.of(0), 0, 5)));
        settle(snapshot, SortMode.TIDY);
        assertEquals(128, count(snapshot.invs().get(0), 1));
        assertEquals(Set.of(0), keysIn(snapshot.invs().get(1)));
    }

    @Test
    void aPinBeatsAMajority() {
        SortSnapshot snapshot = new SortSnapshot(KEYS, List.of(
                chest(0, 64, 0, 64, 0, 64),
                inv(27, false, Set.of(0), 2, 64)));
        settle(snapshot, SortMode.TIDY);
        assertEquals(192, count(snapshot.invs().get(1), 0));
    }

    @Test
    void compactMergesAndFreesChests() {
        SortSnapshot snapshot = new SortSnapshot(KEYS, List.of(
                chest(0, 20, 0, 20, 2, 5),
                chest(0, 30, 2, 40),
                chest(4, 1)));
        assertTrue(settle(snapshot, SortMode.COMPACT) <= 3);
        int used = 0;
        for (SortSnapshot.Inv inv : snapshot.invs()) {
            if (!inv.isEmpty()) {
                used++;
            }
        }
        assertEquals(1, used, "everything fits in one chest: " + snapshot.invs());
    }

    @Test
    void sourcesEmptyAndNeverFill() {
        for (SortMode mode : SortMode.values()) {
            SortSnapshot snapshot = new SortSnapshot(KEYS, List.of(
                    inv(5, true, Set.of(), 0, 10, 5, 3, 10, 1),
                    chest(0, 60)));
            settle(snapshot, mode);
            assertTrue(snapshot.invs().get(0).isEmpty(), mode + ": the hopper still holds " + snapshot.invs().get(0));
        }
    }

    @Test
    void aFullNetworkReportsWhatDidNotFit() {
        SortSnapshot snapshot = new SortSnapshot(KEYS, List.of(
                inv(1, true, Set.of(), 4, 10),
                inv(1, false, Set.of(), 0, 64)));
        Planner.Plan plan = Planner.plan(snapshot, SortMode.TIDY);
        assertEquals(10, plan.unplaced());
    }

    @Test
    void tiesBreakTheSameWayEveryTime() {
        SortSnapshot snapshot = new SortSnapshot(KEYS, List.of(chest(0, 5, 2, 5), chest(0, 5, 2, 5), chest()));
        List<Move> first = Planner.plan(snapshot, SortMode.TIDY).moves();
        for (int i = 0; i < 5; i++) {
            assertEquals(first, Planner.plan(snapshot, SortMode.TIDY).moves());
        }
    }

    @Test
    void randomStorageSettlesQuickly() {
        Random random = new Random(424242);
        for (int trial = 0; trial < 40; trial++) {
            for (SortMode mode : SortMode.values()) {
                List<SortSnapshot.Inv> invs = new ArrayList<>();
                int chests = 2 + random.nextInt(8);
                for (int c = 0; c < chests; c++) {
                    int size = 27;
                    int[] key = new int[size], count = new int[size], limit = new int[size];
                    Arrays.fill(limit, 64);
                    Arrays.fill(key, -1);
                    for (int s = 0; s < size; s++) {
                        if (random.nextInt(3) == 0) {
                            key[s] = random.nextInt(KEYS.size());
                            count[s] = 1 + random.nextInt(KEYS.get(key[s]).maxStack());
                        }
                    }
                    Set<Integer> pins = random.nextInt(6) == 0 ? Set.of(random.nextInt(KEYS.size())) : Set.of();
                    invs.add(new SortSnapshot.Inv(random.nextInt(8) == 0, random.nextBoolean(), limit, key, count, pins,
                            !pins.isEmpty() && random.nextBoolean()));
                }
                SortSnapshot snapshot = new SortSnapshot(KEYS, invs);
                int rounds = settle(snapshot, mode);
                assertTrue(rounds <= 5, mode + " trial " + trial + " didn't settle");
            }
        }
    }

    @Test
    void largeStoragePlansQuickly() {
        Random random = new Random(7);
        List<SortSnapshot.Key> keys = new ArrayList<>();
        for (int k = 0; k < 400; k++) {
            keys.add(new SortSnapshot.Key(64, k / 8, k));
        }
        List<SortSnapshot.Inv> invs = new ArrayList<>();
        for (int c = 0; c < 64; c++) {
            int[] key = new int[54], count = new int[54], limit = new int[54];
            Arrays.fill(limit, 64);
            for (int s = 0; s < 54; s++) {
                key[s] = random.nextInt(keys.size());
                count[s] = 1 + random.nextInt(64);
            }
            invs.add(new SortSnapshot.Inv(false, true, limit, key, count, Set.of()));
        }
        SortSnapshot snapshot = new SortSnapshot(keys, invs);
        for (SortMode mode : SortMode.values()) {
            Planner.plan(snapshot, mode);
            long start = System.nanoTime();
            for (int i = 0; i < 5; i++) {
                Planner.plan(snapshot, mode);
            }
            double ms = (System.nanoTime() - start) / 5 / 1.0e6;
            assertTrue(ms < 25, mode + " took " + ms + " ms");
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Focus, claims, frozen chests, shared strict chests

    @Test
    void aFocusedPlanOnlyMovesWhatLeftTheFocus() {
        for (SortMode mode : SortMode.values()) {
            SortSnapshot snapshot = new SortSnapshot(KEYS, List.of(
                    chest(0, 64, 0, 64, 2, 10),
                    chest(2, 64, 2, 64, 0, 5),
                    chest(4, 1, 2, 3)));
            Planner.Plan plan = Planner.plan(snapshot, mode, new Planner.Focus(Set.of(0), Set.of()), 0);
            assertFalse(plan.isEmpty(), mode + ": nothing planned");
            Set<Integer> left = new HashSet<>();
            for (Move move : plan.moves()) {
                if (move.srcInv() == 0 && move.dstInv() != 0) {
                    left.add(move.key());
                }
            }
            for (Move move : plan.moves()) {
                assertTrue(move.srcInv() == 0 || left.contains(move.key()),
                        mode + ": moved something from outside the focus that never left it: " + move);
            }
        }
    }

    @Test
    void anItemMovedOutOfTheFocusComesTogetherAcrossTheNetwork() {
        // one stone dropped in a chest of its own goes to stone's chest, and so does the stray stone among the logs
        SortSnapshot snapshot = new SortSnapshot(KEYS, List.of(
                chest(0, 1),
                chest(0, 64, 0, 64),
                chest(2, 64, 2, 64, 2, 64, 0, 20, 4, 1)));
        Planner.Plan plan = Planner.plan(snapshot, SortMode.TIDY, new Planner.Focus(Set.of(0), Set.of()), 0);
        long stone = 0;
        for (Move move : plan.moves()) {
            assertEquals(0, move.key(), "moved something other than the stone: " + move);
            assertEquals(1, move.dstInv(), "the stone went somewhere other than its chest: " + move);
            stone += move.count();
        }
        assertEquals(21, stone, "not all the stone came together");
        // in compact, the stone's partial stacks merge across the network once one has moved
        SortSnapshot partials = new SortSnapshot(KEYS, List.of(
                chest(0, 10, 4, 1, 5, 1, 6, 1),
                chest(0, 30, 2, 64),
                chest(2, 64, 0, 7, 7, 1)));
        plan = Planner.plan(partials, SortMode.COMPACT, new Planner.Focus(Set.of(0), Set.of()), 0);
        boolean fromFocus = false, fromElsewhere = false;
        for (Move move : plan.moves()) {
            if (move.key() == 0) {
                fromFocus |= move.srcInv() == 0;
                fromElsewhere |= move.srcInv() == 2;
            }
        }
        assertTrue(fromFocus && fromElsewhere, "the stone's partial stacks weren't all merged: " + plan.moves());
    }

    @Test
    void gatheringPullsPinnedItemsFromAnywhere() {
        SortSnapshot snapshot = new SortSnapshot(KEYS, List.of(
                chest(0, 64, 2, 20),
                chest(4, 1, 2, 30),
                inv(27, false, Set.of(2))));
        Planner.Plan plan = Planner.plan(snapshot, SortMode.TIDY, new Planner.Focus(Set.of(2), Set.of(2)), 0);
        long logs = 0;
        for (Move move : plan.moves()) {
            assertEquals(2, move.dstInv(), "gathered somewhere else: " + move);
            logs += move.count();
        }
        assertEquals(50, logs, "not every log was gathered");
    }

    private static SortSnapshot.Inv claimed(int claim, int... keyCount) {
        return chest(keyCount).with(false, claim);
    }

    @Test
    void aClaimHoldsAgainstARivalUnderTheTolerance() {
        // stone fills 2 slots, logs 4: under a tolerance of 3 the chest stays stone's, and the logs go (a flat
        // snapshot's group g is node g + 1)
        SortSnapshot keep = new SortSnapshot(KEYS, List.of(
                claimed(STONE, 0, 64, 0, 64, 2, 64, 2, 64, 2, 64, 2, 64), chest()));
        Planner.Plan plan = Planner.plan(keep, SortMode.TIDY, Planner.Focus.ALL, 3);
        assertEquals(STONE, plan.claims()[0], "the claim was lost");
        for (Move move : plan.moves()) {
            assertEquals(2, move.key(), "stone moved out of its own chest: " + move);
        }
        // with a fifth stack of logs the logs fill the tolerance more, and take the chest
        SortSnapshot flip = new SortSnapshot(KEYS, List.of(
                claimed(STONE, 0, 64, 0, 64, 2, 64, 2, 64, 2, 64, 2, 64, 2, 64), chest()));
        plan = Planner.plan(flip, SortMode.TIDY, Planner.Focus.ALL, 3);
        assertEquals(LOGS, plan.claims()[0], "the logs didn't take the chest");
        for (Move move : plan.moves()) {
            assertEquals(0, move.key(), "logs moved out of what is now their chest: " + move);
        }
    }

    @Test
    void onlyAChestChangingWhatItIsForShiftsTheLayout() {
        Planner.Focus focus = new Planner.Focus(Set.of(1), Set.of());
        // one more stone goes to stone's chest: nothing shifts
        SortSnapshot more = new SortSnapshot(KEYS, List.of(claimed(STONE, 0, 64, 0, 64), chest(0, 1), chest()));
        assertFalse(Planner.layoutChanged(more, Planner.plan(more, SortMode.TIDY, focus, 0)), "one more stone");
        // logs put in an empty chest make it the logs' chest
        SortSnapshot logs = new SortSnapshot(KEYS, List.of(claimed(STONE, 0, 64, 0, 64), chest(2, 64, 2, 64), chest()));
        assertTrue(Planner.layoutChanged(logs, Planner.plan(logs, SortMode.TIDY, focus, 0)), "a new family's chest");
        // a chest emptied by hand loses its claim, which shifts nothing
        SortSnapshot emptied = new SortSnapshot(KEYS, List.of(claimed(STONE, 0, 64, 0, 64), claimed(LOGS), chest()));
        assertFalse(Planner.layoutChanged(emptied, Planner.plan(emptied, SortMode.TIDY, focus, 0)), "an emptied chest");
        // compact keeps every claim as it is
        assertFalse(Planner.layoutChanged(logs, Planner.plan(logs, SortMode.COMPACT, focus, 0)), "compact");
    }

    @Test
    void aFrozenChestKeepsItsClaimAndNothingMovesThroughIt() {
        for (SortMode mode : SortMode.values()) {
            List<SortSnapshot.Inv> invs = List.of(
                    chest(0, 64, 0, 64, 0, 64, 2, 5).with(true, -1),
                    chest(0, 30, 2, 64, 2, 64),
                    chest());
            Planner.Plan plan = Planner.plan(new SortSnapshot(KEYS, invs), mode);
            for (Move move : plan.moves()) {
                assertTrue(move.srcInv() != 0 && move.dstInv() != 0, mode + ": moved through a frozen chest: " + move);
                if (mode == SortMode.TIDY) {
                    // stone's chest is the frozen one, so the stray stone has nowhere better: it stays
                    assertTrue(move.key() != 0, "stone given a new home though its own is only frozen: " + move);
                }
            }
            if (mode == SortMode.TIDY) {
                assertTrue(plan.stuck().contains(1), "the stray stone isn't reported stuck");
            }
        }
    }

    @Test
    void chestsSharingAStrictPinAreEvenedOut() {
        for (SortMode mode : SortMode.values()) {
            SortSnapshot snapshot = new SortSnapshot(KEYS, List.of(
                    strict(Set.of(0), 0, 64, 0, 64, 0, 64, 0, 64, 0, 64, 0, 64, 0, 64, 0, 64, 0, 64, 0, 64),
                    strict(Set.of(0)),
                    strict(Set.of(0), 0, 64)));
            assertTrue(settle(snapshot, mode) <= 3, mode + " didn't settle");
            long total = 11 * 64, average = total / 3;
            for (SortSnapshot.Inv inv : snapshot.invs()) {
                long here = count(inv, 0);
                assertTrue(Math.abs(here - average) <= 64, mode + ": not within a stack of the average: " + here);
            }
        }
    }

    @Test
    void newItemsGoToTheSharedChestHoldingFewest() {
        for (SortMode mode : SortMode.values()) {
            SortSnapshot snapshot = new SortSnapshot(KEYS, List.of(
                    strict(Set.of(0), 0, 64, 0, 64, 0, 64),
                    strict(Set.of(0), 0, 64, 0, 64),
                    chest(0, 40)));
            Planner.Plan plan = Planner.plan(snapshot, mode, new Planner.Focus(Set.of(2), Set.of()), 0);
            assertFalse(plan.isEmpty(), mode + ": the stone stayed put");
            for (Move move : plan.moves()) {
                assertEquals(1, move.dstInv(), mode + ": stone went to the fuller chest: " + move);
            }
        }
    }

    /** A strict chest of {@code size} slots whose pin of stone (key 0) lets through {@code breadth} items. */
    private static SortSnapshot.Inv ranked(int size, int breadth, int... keyCount) {
        SortSnapshot.Inv inv = inv(size, false, Set.of(0), keyCount);
        return new SortSnapshot.Inv(false, true, inv.limit(), inv.key(), inv.count(), Set.of(0), true)
                .withRanks(breadth == 1 ? Map.of() : Map.of(0, breadth));
    }

    @Test
    void theNarrowestPinWinsASharedItem() {
        for (SortMode mode : SortMode.values()) {
            SortSnapshot snapshot = new SortSnapshot(KEYS, List.of(
                    ranked(27, 200, 0, 64, 0, 64, 0, 64),
                    ranked(27, 12),
                    chest(0, 64)));
            assertTrue(settle(snapshot, mode) <= 3, mode + " didn't settle");
            assertEquals(0, count(snapshot.invs().get(0), 0), mode + ": the broad chest kept stone");
            assertEquals(4 * 64, count(snapshot.invs().get(1), 0), mode + ": the narrow chest didn't take it all");
        }
    }

    @Test
    void theBroaderChestTakesOnlyWhatOverflows() {
        for (SortMode mode : SortMode.values()) {
            SortSnapshot snapshot = new SortSnapshot(KEYS, List.of(
                    ranked(27, 200),
                    ranked(2, 1),
                    chest(0, 64, 0, 64, 0, 64, 0, 64)));
            assertTrue(settle(snapshot, mode) <= 3, mode + " didn't settle");
            assertEquals(2 * 64, count(snapshot.invs().get(1), 0), mode + ": the narrow chest isn't full");
            assertEquals(2 * 64, count(snapshot.invs().get(0), 0), mode + ": the broad chest didn't take the rest");
            assertEquals(0, count(snapshot.invs().get(2), 0), mode + ": stone left behind");
        }
    }

    @Test
    void overflowGoesBackOnceTheNarrowestHasRoom() {
        for (SortMode mode : SortMode.values()) {
            SortSnapshot snapshot = new SortSnapshot(KEYS, List.of(
                    ranked(27, 200, 0, 64, 0, 64),
                    ranked(3, 1, 0, 64)));
            assertTrue(settle(snapshot, mode) <= 3, mode + " didn't settle");
            assertEquals(3 * 64, count(snapshot.invs().get(1), 0), mode + ": the narrow chest wasn't refilled");
            assertEquals(0, count(snapshot.invs().get(0), 0), mode + ": the broad chest kept stone");
        }
    }

    @Test
    void stillCountingSharesAsBefore() {
        for (SortMode mode : SortMode.values()) {
            SortSnapshot snapshot = new SortSnapshot(KEYS, List.of(
                    ranked(27, SortSnapshot.Inv.COUNTING, 0, 64, 0, 64, 0, 64, 0, 64, 0, 64, 0, 64),
                    ranked(27, 1)));
            assertTrue(settle(snapshot, mode) <= 3, mode + " didn't settle");
            for (SortSnapshot.Inv inv : snapshot.invs()) {
                long here = count(inv, 0);
                assertTrue(Math.abs(here - 3 * 64) <= 64, mode + ": not shared while counting: " + here);
            }
        }
    }
}
