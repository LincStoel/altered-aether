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

import com.linc.alteredaether.entity.golem.sort.SortTriggers;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * When a golem network sorts: after the delay, pushed back by more changes but never past the cap, not while a chest
 * is open, one whole sort for many requests, stuck items looked at again when room frees, and rounds that stop.
 */
class SortTriggersTest {
    private static final int DELAY = 60;

    @Test
    void aChangeIsSortedAfterTheDelay() {
        SortTriggers triggers = new SortTriggers();
        assertTrue(triggers.changed(7, 0, DELAY, false, false), "the first change isn't news");
        assertNull(triggers.next(59));
        SortTriggers.Round round = triggers.next(60);
        assertNotNull(round);
        assertFalse(round.full());
        assertEquals(Set.of(7L), round.focus());
        assertNull(triggers.next(61), "sorted twice");
    }

    @Test
    void moreChangesPushTheSortBack() {
        SortTriggers triggers = new SortTriggers();
        triggers.changed(7, 0, DELAY, false, false);
        assertFalse(triggers.changed(7, 30, DELAY, false, false), "a second change is news");
        assertNull(triggers.next(60));
        assertNotNull(triggers.next(90));
    }

    @Test
    void aSteadyTrickleCantHoldTheSortOff() {
        SortTriggers triggers = new SortTriggers();
        for (long t = 0; t < 1000; t += 10) {
            triggers.changed(7, t, DELAY, false, false);
            SortTriggers.Round round = triggers.next(t);
            if (round != null) {
                assertTrue(t <= SortTriggers.CAP * DELAY, "held off until " + t);
                return;
            }
        }
        throw new AssertionError("never sorted");
    }

    @Test
    void anOpenChestWaitsUntilItIsClosed() {
        SortTriggers triggers = new SortTriggers();
        triggers.changed(7, 0, DELAY, true, false);
        assertTrue(triggers.waitingForClose(7));
        assertNull(triggers.next(1000), "sorted while open");
        triggers.closed(7, 1000, DELAY);
        assertNull(triggers.next(1059));
        assertEquals(Set.of(7L), triggers.next(1060).focus());
    }

    @Test
    void severalWholeSortRequestsMakeOne() {
        SortTriggers triggers = new SortTriggers();
        triggers.requestFullSort(0, DELAY);
        triggers.requestFullSort(20, DELAY);
        triggers.changed(7, 25, DELAY, false, false);
        assertNull(triggers.next(79));
        SortTriggers.Round round = triggers.next(80);
        assertTrue(round.full());
        assertNull(triggers.next(1000), "a second sort for the same requests, or for a change it covered");
    }

    @Test
    void stuckItemsAreLookedAtAgainWhenRoomFrees() {
        SortTriggers triggers = new SortTriggers();
        triggers.stuck(List.of(3L, 4L));
        assertNull(triggers.next(500));
        triggers.spaceFreed(500, DELAY);
        assertEquals(Set.of(3L, 4L), triggers.next(560).focus());
    }

    @Test
    void pinChangesGather() {
        SortTriggers triggers = new SortTriggers();
        triggers.changed(9, 0, DELAY, false, true);
        SortTriggers.Round round = triggers.next(60);
        assertEquals(Set.of(9L), round.gather());
    }

    @Test
    void roundsFollowWhereItemsWentAndStop() {
        SortTriggers triggers = new SortTriggers();
        triggers.changed(1, 0, DELAY, false, false);
        SortTriggers.Round round = triggers.next(60);
        assertTrue(triggers.planned(round, Set.of(2L, 3L), true));
        SortTriggers.Round next = triggers.next(61);
        assertEquals(Set.of(2L, 3L), next.focus(), "the next round isn't over where items went");
        assertFalse(triggers.planned(next, Set.of(), false));
        assertNull(triggers.next(62), "went on after a round with nothing to do");
    }

    @Test
    void roundsGoingInCirclesStop() {
        SortTriggers triggers = new SortTriggers();
        triggers.requestFullSort(0, DELAY);
        SortTriggers.Round round = triggers.next(DELAY);
        int rounds = 1;
        while (triggers.planned(round, Set.of(1L), true)) {
            round = triggers.next(DELAY + rounds);
            rounds++;
            assertTrue(rounds <= SortTriggers.MAX_ROUNDS, "didn't stop");
        }
        assertNull(triggers.next(10_000));
    }
}
