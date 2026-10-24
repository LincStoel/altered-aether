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
package com.linc.alteredaether.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.linc.alteredaether.AlteredAether;
import net.minecraft.data.PackOutput;

import java.util.Map;

/** Configured and placed features. */
public final class AAFeatureProvider extends JsonDataProvider {
    private static final String CONFIGURED = "worldgen/configured_feature";
    private static final String PLACED = "worldgen/placed_feature";

    public AAFeatureProvider(PackOutput output) {
        super(output, "features");
    }

    @Override
    protected void generate() {
        groveTrees();
    }

    /**
     * The archwood groves' canopy: Ars Nouveau's natural trees at four per chunk, gated on the sapling surviving, the
     * same recipe Ars Elemental uses for its clusters. Owning the placement means these groves only need Ars Nouveau.
     */
    private void groveTrees() {
        Map<String, String> saplings = Map.of(
                "blazing", "ars_nouveau:red_archwood_sapling",
                "cascading", "ars_nouveau:blue_archwood_sapling",
                "flourishing", "ars_nouveau:green_archwood_sapling",
                "vexing", "ars_nouveau:purple_archwood_sapling");
        saplings.forEach((colour, sapling) -> placed("grove_trees/" + colour,
                "ars_nouveau:natural_" + colour + "_tree",
                Placement.list(
                        Placement.count(4),
                        Placement.inSquare(),
                        Placement.surfaceWaterDepth(0),
                        Placement.heightmap("OCEAN_FLOOR"),
                        Placement.biome(),
                        Placement.wouldSurvive(sapling, Map.of("stage", "0"))),
                "ars_nouveau"));
    }

    private void placed(String name, String feature, JsonArray placement, String... requiredMods) {
        JsonObject json = new JsonObject();
        json.addProperty("feature", feature);
        json.add("placement", placement);
        data(PLACED, AlteredAether.id(name), json, requiredMods);
    }

    @SuppressWarnings("unused")
    private void configured(String name, String type, JsonObject config, String... requiredMods) {
        JsonObject json = new JsonObject();
        json.addProperty("type", type);
        json.add("config", config);
        data(CONFIGURED, AlteredAether.id(name), json, requiredMods);
    }
}
