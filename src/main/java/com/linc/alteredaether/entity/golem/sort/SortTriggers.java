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
package com.linc.alteredaether.entity.golem.sort;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * When a golem network sorts, and what. Pure: ticks are longs and inventories are their long keys.
 *
 * <p>A network sorts what changed, not everything. An inventory whose contents change is sorted a delay after its last
 * change (so a chest being filled is sorted once, when the filling stops), but never more than {@link #CAP} delays
 * after its first, so a hopper's steady trickle can't hold it off for ever. One a player has open waits until they
 * close it. A whole-network sort is asked for by a mode switch, a redstone pulse, a new link or two networks joining,
 * and several asked for within the delay make one.
 *
 * <p>A sort runs in rounds. After a round's moves have all landed, the next round looks at the inventories that
 * received items, in case they now hold something that belongs elsewhere, and so on until a round finds nothing to do
 * or {@link #MAX_ROUNDS} have run. Items that found no room are remembered and looked at again once room is freed.
 */
public final class SortTriggers {
    /** The longest a run of changes can put a sort off, in delays after the first change. */
    public static final int CAP = 5;
    /** Rounds one sort may run before it rests, in case it is going round in circles. */
    public static final int MAX_ROUNDS = 12;

    /**
     * One round of sorting.
     *
     * @param full   true for the whole network, when {@code focus} is ignored
     * @param focus  the inventories whose items may move
     * @param gather the inventories whose pins changed: their pinned items are pulled in from anywhere
     */
    public record Round(boolean full, Set<Long> focus, Set<Long> gather) {
    }

    private final Map<Long, Long> ready = new HashMap<>();
    private final Map<Long, Long> first = new HashMap<>();
    private final Set<Long> gather = new HashSet<>();
    private final Set<Long> waitingClose = new HashSet<>();
    private final Set<Long> stuck = new HashSet<>();
    private long fullReady = -1, fullFirst = -1;
    private Round cascade;
    private int depth;

    /**
     * The inventory {@code key} holds something new, or its pins changed.
     *
     * @return true if it wasn't already waiting to be sorted, for the golem to show it noticed
     */
    public boolean changed(long key, long now, int delay, boolean inUse, boolean pinsChanged) {
        if (pinsChanged) {
            gather.add(key);
        }
        if (inUse) {
            return waitingClose.add(key) && !ready.containsKey(key);
        }
        return queue(key, now, delay);
    }

    private boolean queue(long key, long now, int delay) {
        boolean fresh = !ready.containsKey(key);
        long start = first.computeIfAbsent(key, k -> now);
        ready.put(key, Math.min(now + delay, start + (long) CAP * Math.max(1, delay)));
        return fresh;
    }

    /** A player closed the inventory {@code key}: if it changed while open, it is sorted after the delay. */
    public void closed(long key, long now, int delay) {
        if (waitingClose.remove(key)) {
            queue(key, now, delay);
        }
    }

    public boolean waitingForClose(long key) {
        return waitingClose.contains(key);
    }

    /** Asks for the whole network to be sorted, after the delay. */
    public void requestFullSort(long now, int delay) {
        if (fullFirst < 0) {
            fullFirst = now;
        }
        fullReady = Math.min(now + delay, fullFirst + (long) CAP * Math.max(1, delay));
    }

    /** These inventories hold items that belong elsewhere but found no room there. */
    public void stuck(Collection<Long> keys) {
        stuck.addAll(keys);
    }

    /** Room was freed somewhere: the inventories holding stuck items are looked at again, after the delay. */
    public void spaceFreed(long now, int delay) {
        for (long key : stuck) {
            queue(key, now, delay);
        }
        stuck.clear();
    }

    /** Forgets an inventory that is no longer linked. */
    public void forget(long key) {
        ready.remove(key);
        first.remove(key);
        gather.remove(key);
        waitingClose.remove(key);
        stuck.remove(key);
    }

    /** Whether anything waits to be sorted, now or later. */
    public boolean pending() {
        return !ready.isEmpty() || fullReady >= 0 || cascade != null;
    }

    /**
     * The round to plan now, if any: the next of a sort in progress, else a whole-network sort that is due, else every
     * inventory that is due. Called only when no moves are waiting or in the air.
     */
    public Round next(long now) {
        if (cascade != null) {
            Round round = cascade;
            cascade = null;
            return round;
        }
        depth = 0;
        if (fullReady >= 0 && now >= fullReady) {
            Round round = new Round(true, Set.of(), Set.copyOf(gather));
            fullReady = -1;
            fullFirst = -1;
            ready.clear();
            first.clear();
            gather.clear();
            return round;
        }
        Set<Long> focus = new HashSet<>();
        for (Iterator<Map.Entry<Long, Long>> it = ready.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Long, Long> entry = it.next();
            if (now >= entry.getValue()) {
                focus.add(entry.getKey());
                first.remove(entry.getKey());
                it.remove();
            }
        }
        if (focus.isEmpty()) {
            return null;
        }
        Set<Long> gathering = new HashSet<>();
        for (long key : focus) {
            if (gather.remove(key)) {
                gathering.add(key);
            }
        }
        return new Round(false, Set.copyOf(focus), Set.copyOf(gathering));
    }

    /**
     * What planning {@code round} came to: whether it planned any moves, and where they go. A round that planned
     * moves is followed, once they land, by one over their destinations (or the whole network again, for a whole
     * sort); one that planned nothing ends the sort.
     *
     * @return true if the sort goes on to another round
     */
    public boolean planned(Round round, Set<Long> destinations, boolean moved) {
        if (!moved || destinations.isEmpty() && !round.full() || ++depth >= MAX_ROUNDS) {
            depth = 0;
            return false;
        }
        cascade = round.full() ? new Round(true, Set.of(), Set.of()) : new Round(false, Set.copyOf(destinations), Set.of());
        return true;
    }

    /** Whether a sort is part-way through its rounds. */
    public boolean cascading() {
        return cascade != null;
    }

    /** Drops a sort part-way through, as when the network can't work. */
    public void stopCascade() {
        cascade = null;
        depth = 0;
    }
}
