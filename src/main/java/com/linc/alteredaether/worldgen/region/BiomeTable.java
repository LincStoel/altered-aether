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
package com.linc.alteredaether.worldgen.region;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The climate table for Altered Aether's region, as plain data so it can be unit-tested without Minecraft.
 *
 * <p>TerraBlender looks biomes up by nearest point in an R-tree, so overlapping boxes don't error: both score distance
 * 0 and the winner is arbitrary and seed-dependent. The table is therefore built as a strict partition. Climate
 * windows are punched out of the stock skyroot rows as exclusive rectangles, and each insert is also clipped against
 * the inserts placed before it.
 *
 * <p>Four landform tiers split the space, and the terrain masks in the noise router read the same axes, so a biome
 * always sits on its own terrain:
 * <ul>
 *   <li>continentalness &gt; {@link #PLATEAU_C}: Aether Plateau (any erosion)</li>
 *   <li>erosion in [{@link #CRAG_E}, {@link #TEMPEST_E}): Frozen Crags</li>
 *   <li>erosion &ge; {@link #TEMPEST_E}: Tempest Peaks</li>
 *   <li>otherwise the island tier, the T/H table below, with weirdness &ge; {@link #RIFT_W} overlaid as Shattered
 *   Rift</li>
 * </ul>
 * Each threshold sits mid-ramp of its terrain mask. The shares were measured against the exact noises with
 * {@code noise_stats.py} in the original World Traveler 2 generators.
 */
public final class BiomeTable {
    public static final String MEADOW = "aether:skyroot_meadow";
    public static final String FOREST = "aether:skyroot_forest";
    public static final String GROVE = "aether:skyroot_grove";
    public static final String WOODLAND = "aether:skyroot_woodland";

    public static final String PLATEAU = "altered_aether:aether_plateau";
    public static final String CRAGS = "altered_aether:frozen_crags";
    public static final String TEMPEST = "altered_aether:tempest_peaks";
    public static final String RIFT = "altered_aether:shattered_rift";
    public static final String DUNES = "altered_aether:windswept_dunes";
    public static final String FROZEN_LAKES = "altered_aether:frozen_lakes";
    public static final String CATHEDRAL = "altered_aether:skyroot_cathedral";
    public static final String NURSELOG = "altered_aether:nurselog_hollow";
    public static final String BLAZING = "altered_aether:blazing_archwood_grove";
    public static final String CASCADING = "altered_aether:cascading_archwood_grove";
    public static final String FLASHING = "altered_aether:flashing_archwood_grove";
    public static final String FLOURISHING = "altered_aether:flourishing_archwood_grove";
    public static final String VEXING = "altered_aether:vexing_archwood_grove";

    /** Plateau tier, mid-ramp of plateau_mask. P(continents &gt; this) = 6.1%. */
    public static final float PLATEAU_C = 0.545f;
    /** Frozen Crags tier, mid-ramp of crag_mask. P(erosion &gt; this) = 4.0%. */
    public static final float CRAG_E = 0.6125f;
    /** Tempest Peaks, past the top of the crag_mask ramp. P(erosion &gt; this) = 1.0%. */
    public static final float TEMPEST_E = 0.8044f;
    /** Shattered Rift overlay on weirdness, same noise recipe as the crags. 4.0% of the island tier. */
    public static final float RIFT_W = 0.6125f;
    /** Bounds wide enough to contain every value the router can produce. */
    public static final float LOW = -2.0f;
    public static final float HIGH = 2.0f;

    /** An inclusive-exclusive span on one climate axis. */
    public record Span(float min, float max) {
        public static final Span ALL = new Span(LOW, HIGH);

        public boolean contains(double v) {
            return v >= min && v < max;
        }

        boolean overlaps(Span o) {
            return o.max > min && o.min < max;
        }
    }

    /** A biome and the climate box it owns. Depth is always the full range; the Aether has no depth axis in use. */
    public record Entry(String biome, Span temperature, Span humidity, Span continentalness, Span erosion,
                        Span weirdness) {
        public boolean contains(double t, double h, double c, double e, double w) {
            return temperature.contains(t) && humidity.contains(h) && continentalness.contains(c)
                    && erosion.contains(e) && weirdness.contains(w);
        }
    }

    private record Rect(Span t, Span h, String biome) {
    }

    /** The stock Aether T/H table, a complete partition of [-1,1]^2 (row 14's temperature typo fixed). */
    private static final List<Rect> STOCK = List.of(
            rect(-1.00f, -0.80f, -1.00f, 1.00f, MEADOW),
            rect(-0.80f, 0.00f, -1.00f, 0.00f, MEADOW),
            rect(-0.80f, 0.00f, 0.00f, 1.00f, FOREST),
            rect(0.00f, 0.40f, -1.00f, 0.00f, GROVE),
            rect(0.00f, 0.40f, 0.00f, 0.80f, FOREST),
            rect(0.00f, 0.40f, 0.80f, 1.00f, GROVE),
            rect(0.40f, 0.93f, -1.00f, -0.10f, GROVE),
            rect(0.40f, 0.93f, -0.10f, 1.00f, FOREST),
            rect(0.93f, 0.94f, -1.00f, -0.60f, MEADOW),
            rect(0.93f, 0.94f, -0.60f, -0.30f, GROVE),
            rect(0.93f, 0.94f, -0.30f, 1.00f, FOREST),
            rect(0.94f, 1.00f, -1.00f, -0.10f, MEADOW),
            rect(0.94f, 1.00f, -0.10f, 0.80f, WOODLAND),
            rect(0.94f, 1.00f, 0.80f, 1.00f, FOREST));

    /**
     * Windows punched into the island tier, in priority order: an earlier insert keeps its full window where two
     * collide. Shares are measured with {@code -PaaReport} and {@code -PaaClimate}.
     */
    private static final List<Rect> INSERTS = List.of(
            // Narrow on the slow temperature axis, wide on the fast humidity axis: bigger, more contiguous patches
            // than a balanced box of the same area. 4.8% of the region, all on the dry side.
            rect(0.21f, 0.41f, -0.68f, -0.08f, DUNES),
            // The cold middle, between the flashing and cascading groves: high frozen lakes. Uncommon, but ahead of the
            // crags, peaks and rift (the middle of the humidity range is far denser than its tails). Only big enough
            // for a few lakes at a time; there are no rings to make room for.
            rect(-0.45f, -0.29f, -0.13f, 0.13f, FROZEN_LAKES),
            // Temperate and humid, in the open middle between the frozen lakes and the vexing grove.
            rect(-0.44f, -0.04f, 0.24f, 0.56f, CATHEDRAL),
            // The same box grown by 0.05 on every side. The cathedral above keeps its window, so what is left is a
            // frame: walking out of the cathedral, the climate passes through its fallen elders first.
            rect(-0.49f, 0.01f, 0.19f, 0.61f, NURSELOG),
            // A cooler, drier solo window, for the occasional dead colony with no living one beside it.
            rect(-0.58f, -0.46f, -0.42f, -0.22f, NURSELOG),
            // The archwood groves hold the four corners and the humid middle. Humidity rarely passes +/-0.4 here (its
            // 5% tails), so each window reaches well inside that: about 2% of the island tier apiece. The flashing
            // grove gives up a corner to the solo nurselog, so it reaches a little further toward the middle.
            rect(0.45f, 1.00f, -1.00f, -0.20f, BLAZING),
            rect(-1.00f, -0.50f, -1.00f, -0.15f, FLASHING),
            rect(-1.00f, -0.50f, 0.20f, 1.00f, CASCADING),
            rect(0.45f, 1.00f, 0.20f, 1.00f, FLOURISHING),
            rect(0.08f, 0.40f, 0.33f, 1.00f, VEXING));

    /**
     * Builds the region's table.
     *
     * @param present every optional biome that should be placed. Stock skyroot biomes are always present. A missing
     *                insert's window simply stays with the skyroot biome it would have been cut from.
     */
    public static List<Entry> build(Set<String> present) {
        List<Rect> rows = new ArrayList<>(STOCK);
        List<Rect> placed = new ArrayList<>();
        for (Rect insert : INSERTS) {
            if (!present.contains(insert.biome)) {
                continue;
            }
            List<Rect> pieces = List.of(insert);
            for (Rect prior : placed) {
                pieces = subtractAll(pieces, prior);
            }
            rows = subtractAll(rows, insert);
            placed.addAll(pieces);
        }
        rows.addAll(placed);

        Span islandC = new Span(LOW, PLATEAU_C);
        Span islandE = new Span(LOW, CRAG_E);
        boolean rift = present.contains(RIFT);
        Span islandW = rift ? new Span(LOW, RIFT_W) : Span.ALL;

        List<Entry> entries = new ArrayList<>();
        for (Rect r : rows) {
            entries.add(new Entry(r.biome, stretch(r.t), stretch(r.h), islandC, islandE, islandW));
        }
        if (rift) {
            entries.add(new Entry(RIFT, Span.ALL, Span.ALL, islandC, islandE, new Span(RIFT_W, HIGH)));
        }
        // A disabled landform hands its tier back to the tier beneath it: the crags to the island table (one
        // erosion band wider), tempest to the crags, the plateau to everything below it.
        boolean crags = present.contains(CRAGS);
        boolean tempest = present.contains(TEMPEST);
        Span cragE = new Span(CRAG_E, tempest ? TEMPEST_E : HIGH);
        Span tempestE = new Span(TEMPEST_E, HIGH);
        if (crags) {
            entries.add(new Entry(CRAGS, Span.ALL, Span.ALL, islandC, cragE, Span.ALL));
        }
        if (tempest) {
            entries.add(new Entry(TEMPEST, Span.ALL, Span.ALL, islandC, tempestE, Span.ALL));
        }
        if (!crags) {
            // Re-cover the erosion band the crags would have owned with the island table.
            Span band = tempest ? new Span(CRAG_E, TEMPEST_E) : new Span(CRAG_E, HIGH);
            for (Rect r : rows) {
                entries.add(new Entry(r.biome, stretch(r.t), stretch(r.h), islandC, band, Span.ALL));
            }
        }
        if (present.contains(PLATEAU)) {
            entries.add(new Entry(PLATEAU, Span.ALL, Span.ALL, new Span(PLATEAU_C, HIGH), Span.ALL, Span.ALL));
        } else {
            for (Entry e : List.copyOf(entries)) {
                if (e.continentalness.equals(islandC)) {
                    entries.add(new Entry(e.biome, e.temperature, e.humidity, new Span(PLATEAU_C, HIGH), e.erosion,
                            e.weirdness));
                }
            }
        }
        return entries;
    }

    /** Pushes table edges at +/-1 out to +/-2, so values the noise can reach past 1 are still owned. */
    private static Span stretch(Span s) {
        return new Span(s.min <= -1.0f ? LOW : s.min, s.max >= 1.0f ? HIGH : s.max);
    }

    private static List<Rect> subtractAll(List<Rect> rows, Rect cut) {
        List<Rect> out = new ArrayList<>();
        for (Rect row : rows) {
            out.addAll(subtract(row, cut));
        }
        return out;
    }

    /** Removes {@code cut} from {@code row}, leaving 0-4 rectangles. */
    private static List<Rect> subtract(Rect row, Rect cut) {
        if (!row.t.overlaps(cut.t) || !row.h.overlaps(cut.h)) {
            return List.of(row);
        }
        List<Rect> out = new ArrayList<>(4);
        if (cut.t.min > row.t.min) {
            out.add(new Rect(new Span(row.t.min, cut.t.min), row.h, row.biome));
        }
        if (cut.t.max < row.t.max) {
            out.add(new Rect(new Span(cut.t.max, row.t.max), row.h, row.biome));
        }
        Span mid = new Span(Math.max(row.t.min, cut.t.min), Math.min(row.t.max, cut.t.max));
        if (cut.h.min > row.h.min) {
            out.add(new Rect(mid, new Span(row.h.min, cut.h.min), row.biome));
        }
        if (cut.h.max < row.h.max) {
            out.add(new Rect(mid, new Span(cut.h.max, row.h.max), row.biome));
        }
        return out;
    }

    private static Rect rect(float t0, float t1, float h0, float h1, String biome) {
        return new Rect(new Span(t0, t1), new Span(h0, h1), biome);
    }

    private BiomeTable() {
    }
}
