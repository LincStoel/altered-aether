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
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.linc.alteredaether.AlteredAether;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.Set;

import static com.linc.alteredaether.data.Df.add;
import static com.linc.alteredaether.data.Df.clamp;
import static com.linc.alteredaether.data.Df.flatCache;
import static com.linc.alteredaether.data.Df.min;
import static com.linc.alteredaether.data.Df.mul;
import static com.linc.alteredaether.data.Df.oldBlendedNoise;
import static com.linc.alteredaether.data.Df.ramp;
import static com.linc.alteredaether.data.Df.ridged;
import static com.linc.alteredaether.data.Df.square;
import static com.linc.alteredaether.data.Df.ref;
import static com.linc.alteredaether.data.Df.shiftedNoise;
import static com.linc.alteredaether.data.Df.yGradient;

/**
 * The Aether's terrain: our noises and density functions, and the {@code aether:skylands} noise settings that weave
 * our landforms into whichever island terrain Aeroblender supplies.
 *
 * <p>Runs once per height mode. The landforms were tuned for the tall Aether (noise band -64..127), so those values
 * are canonical; the stock-height versions (band 0..127) are derived by inverting the tall remap
 * {@code y = -64 + 1.5 * stockY}, which compresses every vertical measure by 1.5 and leaves y = 128 fixed. The stock
 * set ships in the mod's data; the tall set, plus the taller dimension type, ships in the built-in
 * {@code tall_aether} datapack, which overrides only the files whose shape depends on height.
 *
 * <p>Every landform mask is multiplied by {@code terrain/region_mask}, so crags, plateaus, dunes and the rift appear
 * only inside Altered Aether's own TerraBlender region.
 */
public final class AATerrainProvider extends JsonDataProvider {
    public enum HeightMode {
        STOCK, TALL;

        /** A tall-mode Y converted to this mode. */
        int y(int tallY) {
            return this == TALL ? tallY : (int) Math.round((tallY + 64) / 1.5);
        }

        /** A vertical size (old_blended_noise y_factor), tall-mode canonical. */
        double height(double tall) {
            return this == TALL ? tall : tall / 1.5;
        }

        /** A vertical frequency (noise y_scale), tall-mode canonical. */
        double frequency(double tall) {
            return this == TALL ? tall : tall * 1.5;
        }
    }

    private static final String NS = AlteredAether.MOD_ID + ":";
    private static final String TERRAIN = NS + "terrain/";
    /** Density functions whose shape depends on height, and so are overridden by the tall pack. */
    private static final Set<String> HEIGHT_DEPENDENT =
            Set.of("crag_3d_noise", "jag", "crags", "tempest", "tempest_spires", "dune_ridges", "dune_centre", "dune_sheet",
                    "plateau", "plateau_terraced", "island", "island_shift", "tall_base_3d_noise", "rift");

    private final HeightMode mode;

    public AATerrainProvider(PackOutput output, HeightMode mode) {
        super(output, "terrain (" + mode.name().toLowerCase() + ")");
        this.mode = mode;
    }

    @Override
    protected void generate() {
        if (mode == HeightMode.STOCK) {
            noises();
        }
        densityFunctions();
        skylands();
        shelfPatches();
        if (mode == HeightMode.TALL) {
            dimensionType();
            quicksoilShelf();
            aetherStructures();
            packMeta();
        }
    }

    private void packMeta() {
        JsonObject pack = new JsonObject();
        pack.addProperty("pack_format", 48);
        pack.addProperty("description", "A taller Aether (-64 to 319) with deeper islands");
        JsonObject json = new JsonObject();
        json.add("pack", pack);
        root("pack.mcmeta", json);
    }

    // ------------------------------------------------------------------------------------------------ noises --

    private void noises() {
        noise("continents", -9, 1.0, 1.0, 2.0, 2.0, 2.0, 1.0, 1.0, 1.0, 1.0);
        // Re-measured to give Frozen Crags patches the size of an ordinary Aether biome (area-weighted ~1,046
        // blocks); firstOctave -7 hit the right share but gave 338-block patches.
        noise("crags", -9, 1.0, 1.5, 1.2, 0.6);
        noise("crag_jag", -4, 1.0, 0.5);
        // Same recipe as the crags, an independent field, so CRAG_E's measured threshold carries over.
        noise("rift", -9, 1.0, 1.5, 1.2, 0.6);
        // Ridged landforms. Lattice sizes: -7 is 128 blocks, -6 is 64, -4 is 16, -3 is 8.
        noise("crag_ridges", -6, 1.0, 0.5);
        noise("crag_spurs", -4, 1.0);
        noise("crag_massif", -7, 1.0, 0.6);
        noise("tempest_spires", -3, 1.0);
        noise("rift_cells", -3, 1.0);
        noise("dune_warp", -6, 1.0);
        noise("dune_swell", -7, 1.0);
        // The dune sheets: ribbons along the zero contours of a 256-block noise, at heights that drift on another.
        noise("dune_path", -8, 1.0, 0.35);
        noise("dune_drift", -8, 1.0);
        noise("mesa", -5, 1.0, 0.5, 0.25);
        noise("mesa_height", -6, 1.0);
        noise("plateau_relief", -6, 1.0, 0.5);
        noise("path_region", -6, 1.0);
        noise("plateau_path", -8, 1.0, 1.2, 0.7, 1.2, 1.0);
        noise("plateau_quicksoil_vein", -7, 1.0, 0.5, 0.0, 0.5, 1.0);
    }

    private void noise(String name, int firstOctave, double... amplitudes) {
        JsonObject json = new JsonObject();
        json.addProperty("firstOctave", firstOctave);
        JsonArray list = new JsonArray();
        for (double amplitude : amplitudes) {
            list.add(amplitude);
        }
        json.add("amplitudes", list);
        data("worldgen/noise", AlteredAether.id(name), json);
    }

    // ------------------------------------------------------------------------------------- density functions --

    private void densityFunctions() {
        df("region_mask", flatCache(Df.type(NS + "region_mask")));

        // Plateau tier, on continentalness. The biome takes over at continents 0.545, mid-ramp of this mask.
        df("continentalness", flatCache(shiftedNoise(NS + "continents", 1.0, 0.0)));
        df("plateau_mask", ramp(terrain("continentalness"), -1.68, 4.0));
        df("mesa_mask", ramp(flatCache(shiftedNoise(NS + "mesa", 0.25, 0.0)), -9.0, 20.0));
        df("plateau", add(
                min(add(yGradient(mode.y(4), 1.0, mode.y(84), -1.0),
                                mul(terrain("mesa_mask"), add(0.25, mul(0.125,
                                        flatCache(shiftedNoise(NS + "mesa_height", 0.25, 0.0)))))),
                        yGradient(mode.y(-40), -1.0, mode.y(0), 1.0)),
                mul(0.125, shiftedNoise(NS + "plateau_relief", 0.35, 0.0))));

        // Stepped mesas: the plateau sampled at stepped heights. The config's step height is in tall-Aether blocks.
        JsonObject terraced = Df.type(NS + "terrace");
        terraced.add("argument", terrain("plateau"));
        terraced.addProperty("sharpness", 4.0);
        terraced.addProperty("step_scale", mode == HeightMode.TALL ? 1.0 : 1 / 1.5);
        df("plateau_terraced", terraced);

        // Crag tier, on erosion: the same field drives the biome (erosion > 0.6125) and this mask, which ramps
        // 0 -> 1 across 0.4875..0.7375, so the biome edge sits mid-ramp. Tempest Peaks is the top slice of the same
        // tier (erosion > 0.8044), where the crag mask is saturated and the tempest mask takes over.
        df("crag_noise", flatCache(shiftedNoise(NS + "crags", 0.25, 0.0)));
        df("crag_mask", ramp(terrain("crag_noise"), -1.95, 4.0));
        df("tempest_mask", ramp(terrain("crag_noise"), -0.76 / 0.09, 1 / 0.09));
        // The island noise made half as wide and twice as tall, for overhangs and hollows inside the massifs.
        df("crag_3d_noise", oldBlendedNoise(0.25, 0.25, 40.0, mode.height(480.0), 8.0));
        // A fine wobble that roughens isosurfaces so cliffs and crack walls don't look stamped.
        df("jag", shiftedNoise(NS + "crag_jag", 1.0, mode.frequency(0.5)));

        // Ridgelines: sharp crests along the zero contours of a 64-block noise, with 16-block spurs branching off
        // them. 1 on a crest, falling to 0 between crests.
        JsonObject crest = ridged(shiftedNoise(NS + "crag_ridges", 1.0, 0.0));
        JsonObject spur = ridged(shiftedNoise(NS + "crag_spurs", 1.0, 0.0));
        df("crag_ridges", flatCache(clamp(add(mul(0.8, crest), mul(0.4, mul(crest, spur))), 0.0, 1.0)));
        // Where the crags stand at all: separate floating massifs about 128 blocks across, open sky between them.
        df("crag_massif", flatCache(shiftedNoise(NS + "crag_massif", 1.0, 0.0)));
        // Frozen Crags: massifs whose summits follow the ridgelines up to 90 blocks above their base, with roots
        // hanging below the highest peaks, so each massif looks like a broken-off mountain range.
        df("crags", massif(terrain("crag_ridges"), 5, 90, "crag_strength", -30, 32, 0.0, 0.3, 0.2));
        // Tempest Peaks: the ridges squared (thinner, sharper crests), taller, and studded with needle spires.
        df("tempest_spires", flatCache(mul(mode.height(48), square(square(
                clamp(mul(1.6, shiftedNoise(NS + "tempest_spires", 1.0, 0.0)), 0.0, 1.0))))));
        df("tempest", massif(mul(terrain("crag_ridges"), terrain("crag_ridges")), 10, 105, "tempest_strength",
                -24, 36, 1.0, 0.4, 0.15));
        df("crag_tier", Df.choose(terrain("tempest_mask"), terrain("crags"), terrain("tempest")));

        // Shattered Rift, on weirdness: inside the rift the island breaks into Voronoi slabs about 48 blocks
        // across, each lifted or dropped and tilted, with long straight cracks between them.
        df("rift_noise", flatCache(shiftedNoise(NS + "rift", 0.25, 0.0)));
        df("rift_mask", ramp(terrain("rift_noise"), -1.95, 4.0));

        // Windswept Dunes: a box on the same temperature and humidity fields the biome selector reads, so the lean
        // lines up with the biome window exactly.
        df("dune_temperature", flatCache(shiftedNoise("aeroblender:temperature", 0.25, 0.0)));
        df("dune_humidity", flatCache(shiftedNoise("aether:vegetation", 0.25, 0.0)));
        df("dune_mask", climateBox(0.21, 0.41, -0.68, -0.08));
        // Long, low dune crests across the prevailing wind: gentle windward slopes, steep lee faces, up to 5 blocks.
        JsonObject ridges = Df.type(NS + "wind_ridges");
        ridges.addProperty("wavelength", 64.0);
        ridges.addProperty("lee", 0.3);
        ridges.addProperty("warp", NS + "dune_warp");
        ridges.addProperty("warp_amount", 32.0);
        ridges.addProperty("swell", NS + "dune_swell");
        df("dune_ridges", flatCache(mul(mul(Df.config("dune_height"), mode.height(5)), ridges)));
        // The dunes are thin sheets of sand high in the sky, not islands: ribbons a hundred-odd blocks wide that wind
        // along the zero contours of a 256-block noise, rising and falling on another as they go. Across a ribbon
        // the sheet is a lens, about 16 blocks thick down its middle and tapering to a feather edge. The ribbon is
        // where the ridged noise passes 0.62.
        df("dune_reach", flatCache(add(-1.63, mul(2.63, ridged(shiftedNoise(NS + "dune_path", 1.0, 0.0))))));
        df("dune_centre", flatCache(add(mode.y(92), mul(mode.height(14),
                shiftedNoise(NS + "dune_drift", 1.0, 0.0)))));
        JsonObject crown = clamp(mul(2.5, terrain("dune_reach")), -4.0, 1.0);
        JsonObject summit = add(add(terrain("dune_centre"), add(mul(mode.height(4), crown), -mode.height(2))),
                mul(clamp(crown, 0.0, 1.0), terrain("dune_ridges")));
        JsonObject underside = add(terrain("dune_centre"),
                add(-mode.height(2), mul(-mode.height(12), terrain("dune_reach"))));
        df("dune_sheet", clamp(min(mul(0.125, add(summit, mul(-1.0, Df.y()))),
                mul(1.0 / 6, add(Df.y(), mul(-1.0, underside)))), -1.0, 1.0));

        // The Sunken Atoll's islands sink and the Frozen Lakes' rise, each on a box over its own climate window.
        df("atoll_mask", climateBox(0.16, 0.52, 0.06, 0.95));
        df("frozen_lakes_mask", climateBox(-0.45, -0.29, -0.13, 0.13));
        df("island_shift", flatCache(mul(terrain("region_mask"), add(mul(-mode.height(20), terrain("atoll_mask")),
                mul(mode.height(24), terrain("frozen_lakes_mask"))))));

        // Each landform's weight, confined to our region.
        df("plateau_weight", mul(terrain("region_mask"), terrain("plateau_mask")));
        df("crag_weight", mul(terrain("region_mask"), terrain("crag_mask")));
        df("rift_weight", mul(terrain("region_mask"), terrain("rift_mask")));
        df("dune_weight", mul(terrain("region_mask"), terrain("dune_mask")));

        if (mode == HeightMode.TALL) {
            // The Aether's own island noise, stretched to the taller band (y_factor 160 -> 240).
            df("tall_base_3d_noise", oldBlendedNoise(0.25, 0.25, 80.0, 240.0, 8.0));
        }
    }

    /**
     * A floating mountain massif as a density: solid below a summit surface that follows {@code ridges}, above a
     * root surface that hangs deepest under the highest ridges, and inside the massif footprint. Heights are in
     * tall-Aether blocks.
     *
     * @param base     summit height where the ridge value is 0
     * @param peak     summit rise on a full ridge, scaled by the {@code strength} config value
     * @param root     underside height where the ridge value is 0
     * @param rootDrop how much further the underside hangs on a full ridge
     * @param spires   multiplier on the tempest spires added to the summit (0 for none)
     * @param jag      weight of the fine 3D roughening
     * @param hollows  weight of the coarse 3D noise that cuts overhangs and hollows
     */
    private JsonObject massif(JsonElement ridges, int base, int peak, String strength, int root, int rootDrop,
                              double spires, double jag, double hollows) {
        JsonObject summit = add(mode.y(base), mul(mul(Df.config(strength), mode.height(peak)), ridges));
        if (spires > 0) {
            summit = add(summit, mul(spires, terrain("tempest_spires")));
        }
        // Clamped just under the top of the noise band so no summit is sliced flat by the band's ceiling.
        summit = clamp(summit, mode.y(-64), mode.y(124));
        JsonObject underside = add(mode.y(root), mul(-mode.height(rootDrop), ridges));
        JsonObject below = mul(0.125, add(summit, mul(-1.0, Df.y())));
        JsonObject above = mul(1.0 / 6, add(Df.y(), mul(-1.0, underside)));
        JsonObject footprint = mul(6.0, add(terrain("crag_massif"), 0.05));
        return add(add(min(min(below, above), footprint), mul(jag, terrain("jag"))),
                mul(hollows, terrain("crag_3d_noise")));
    }

    /**
     * 1 inside a temperature and humidity window of the biome table, ramping to 0 across 0.05 either side of each
     * edge, so the window's edge sits mid-ramp. Reads the same fields the biome selector does.
     */
    private static JsonObject climateBox(double t0, double t1, double h0, double h1) {
        return mul(
                min(ramp(terrain("dune_temperature"), -10 * (t0 - 0.05), 10.0),
                        ramp(terrain("dune_temperature"), 10 * (t1 + 0.05), -10.0)),
                min(ramp(terrain("dune_humidity"), -10 * (h0 - 0.05), 10.0),
                        ramp(terrain("dune_humidity"), 10 * (h1 + 0.05), -10.0)));
    }

    private void df(String name, JsonElement function) {
        if (mode == HeightMode.TALL && !HEIGHT_DEPENDENT.contains(name)) {
            return;
        }
        JsonObject json = function instanceof JsonObject object ? object : wrapConstant(function);
        data("worldgen/density_function", AlteredAether.id("terrain/" + name), json);
    }

    /** A density function file must be an object; wrap references in a no-op so they can stand alone. */
    private static JsonObject wrapConstant(JsonElement function) {
        return add(function, 0.0);
    }

    private static JsonElement terrain(String name) {
        return ref(TERRAIN + name);
    }

    // -------------------------------------------------------------------------------------- noise settings --

    /**
     * Starts from Aeroblender's {@code aether:skylands} (the copy that actually loads), so the default blocks, the
     * temperature and vegetation routers and the island expression stay identical, then:
     * <ul>
     *   <li>wires continentalness, erosion and weirdness to our landform noises (all three were a constant 0, so
     *   unusable for biome selection; Deep Aether's regions span them fully, so their biomes are unaffected);</li>
     *   <li>weaves our landforms into the island expression:
     *   {@code choose(plateau, choose(crags, choose(rift, island + dunes, rift), crags), plateau)}. The crag blend sits inside the
     *   plateau blend so crag terrain can never appear under a plateau biome.</li>
     * </ul>
     * Sea level stays at -64: the generator places lava below min(-54, sea level), so it must never rise.
     */
    private void skylands() {
        JsonObject settings = ModResources.json("aeroblender", "data", "aether", "worldgen", "noise_settings",
                "skylands.json");
        JsonObject router = settings.getAsJsonObject("noise_router");
        router.add("continents", terrain("continentalness"));
        router.add("erosion", terrain("crag_noise"));
        router.add("ridges", terrain("rift_noise"));

        JsonObject squeeze = router.getAsJsonObject("final_density");
        JsonObject interpolated = squeeze.getAsJsonObject("argument");
        JsonObject blend = interpolated.getAsJsonObject("argument");
        require(squeeze, "minecraft:squeeze");
        require(interpolated, "minecraft:interpolated");
        require(blend, "minecraft:blend_density");

        JsonElement island = blend.get("argument");
        if (mode == HeightMode.TALL) {
            JsonObject noise = settings.getAsJsonObject("noise");
            noise.addProperty("min_y", -64);
            noise.addProperty("height", 192);
            island = remapIsland(island);
        }
        // The island expression gets a file of its own, so every blend below refers to it rather than copying it.
        df("island", island);
        // The island sunk under the atolls and raised under the frozen lakes, then given over to the dune sheets.
        JsonObject shifted = Df.type(NS + "shift_y");
        shifted.add("argument", terrain("island"));
        shifted.add("offset", terrain("island_shift"));
        df("island_shifted", shifted);
        df("island_dunes", Df.choose(terrain("dune_weight"), terrain("island_shifted"), terrain("dune_sheet")));
        // The rift: the same island broken into drifting slabs, so the slabs carry the dunes too.
        rift(terrain("island_dunes"));
        JsonObject islandTier = Df.choose(terrain("rift_weight"), terrain("island_dunes"), terrain("rift"));
        JsonObject withCrags = Df.choose(terrain("crag_weight"), islandTier, terrain("crag_tier"));
        // Atolls stand over whatever tier is beneath them, each kept wholly inside our region by its site check.
        JsonObject atoll = Df.type(NS + "atoll");
        atoll.add("argument", Df.choose(terrain("plateau_weight"), withCrags, terrain("plateau_terraced")));
        atoll.add("weight", terrain("region_mask"));
        // Atolls lie low, sunk toward the bottom of the islands; the frozen lakes stand high above them.
        atoll.addProperty("surface", mode.y(8));
        atoll.addProperty("frozen_surface", mode.y(88));
        atoll.addProperty("vertical_scale", vertical());
        // The Sunken Atoll is its atolls alone: the islands between them are taken away.
        atoll.add("clear", flatCache(mul(terrain("region_mask"), terrain("atoll_mask"))));
        blend.add("argument", atoll);
        // Basins are cut last, outside the interpolation, so their bowls and water lines are exact to the block.
        JsonObject carve = Df.type(NS + "basin_carve");
        carve.add("argument", squeeze);
        carve.addProperty("vertical_scale", vertical());
        router.add("final_density", carve);

        data("worldgen/noise_settings", ResourceLocation.fromNamespaceAndPath("aether", "skylands"), settings);
    }

    private double vertical() {
        return mode == HeightMode.TALL ? 1.0 : 1 / 1.5;
    }

    /** The fractured island under the rift. Written here because it wraps the island expression itself. */
    private void rift(JsonElement island) {
        JsonObject fracture = Df.type(NS + "voronoi_fracture");
        fracture.add("argument", island);
        fracture.addProperty("seed", NS + "rift_cells");
        fracture.addProperty("cell_size", 48.0);
        fracture.addProperty("crack_width", 4.0);
        fracture.addProperty("max_offset", mode.height(16));
        fracture.addProperty("max_tilt", 0.15);
        fracture.add("roughness", terrain("jag"));
        // The rift is an island torn apart: a sparse field of tall, blocky columns, each standing alone with open sky
        // between it and the next, and the debris of the break hanging in the gaps.
        JsonObject pillars = new JsonObject();
        pillars.addProperty("top", mode.y(84));
        pillars.addProperty("min_height", mode.height(64));
        pillars.addProperty("max_height", mode.height(112));
        pillars.addProperty("empty_chance", 0.3);
        pillars.addProperty("step", (int) Math.round(mode.height(7)));
        pillars.addProperty("gap", 10.0);
        fracture.add("pillars", pillars);
        df("rift", fracture);
    }

    /** The island expression's height anchors, remapped to the tall band, and its base noise stretched to match. */
    private static JsonElement remapIsland(JsonElement node) {
        Map<Integer, Integer> anchors = Map.of(8, -52, 40, -4, 56, 20, 128, 128);
        if (node instanceof JsonObject object) {
            JsonObject copy = new JsonObject();
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                copy.add(entry.getKey(), remapIsland(entry.getValue()));
            }
            if (copy.has("type") && copy.get("type").getAsString().equals("minecraft:y_clamped_gradient")) {
                for (String key : new String[]{"from_y", "to_y"}) {
                    Integer to = anchors.get(copy.get(key).getAsInt());
                    if (to == null) {
                        throw new IllegalStateException("Unexpected island height anchor " + copy.get(key));
                    }
                    copy.addProperty(key, to);
                }
            }
            return copy;
        }
        if (node instanceof JsonPrimitive primitive && primitive.isString()
                && primitive.getAsString().equals("aether:base_3d_noise_aether")) {
            return terrain("tall_base_3d_noise");
        }
        if (node instanceof JsonArray array) {
            JsonArray copy = new JsonArray();
            array.forEach(e -> copy.add(remapIsland(e)));
            return copy;
        }
        return node.deepCopy();
    }

    /**
     * Patches of soft ground in two groves: quicksoil among the cascading grove's coarse dirt, and virulent quicksand
     * in the vexing grove's mud. The Aether's shelf feature reads its height range as plain numbers, which is why these
     * are written for both height modes. The range is the island band, 0 to 128 in the stock Aether, which is -64 to
     * 128 in the tall one.
     */
    private void shelfPatches() {
        shelf("cascading", "aether:quicksoil[double_drops=true]", "deep_aether:aether_coarse_dirt", 6);
        shelf("vexing", "deep_aether:virulent_quicksand", "deep_aether:aether_mud", 10);
    }

    private void shelf(String grove, String block, String validOn, int rarity) {
        JsonObject range = new JsonObject();
        range.addProperty("min_inclusive", mode == HeightMode.TALL ? -64 : 0);
        range.addProperty("max_inclusive", 128);
        JsonObject config = new JsonObject();
        config.add("block", Providers.simple(block));
        config.addProperty("radius", 3.4641016);
        config.addProperty("valid_blocks", validOn);
        config.add("y_range", range);
        JsonObject feature = new JsonObject();
        feature.addProperty("type", "aether:shelf");
        feature.add("config", config);
        data("worldgen/configured_feature", AlteredAether.id("shelf/" + grove), feature, "deep_aether");

        JsonObject placed = new JsonObject();
        placed.addProperty("feature", AlteredAether.MOD_ID + ":shelf/" + grove);
        placed.add("placement", Placement.list(Placement.rarity(rarity), Placement.heightmap("WORLD_SURFACE_WG"),
                Placement.biome()));
        data("worldgen/placed_feature", AlteredAether.id("shelf/" + grove), placed, "deep_aether");

        // added to the grove by a modifier, as the soil it needs comes from Deep Aether
        if (mode == HeightMode.STOCK) {
            JsonObject modifier = new JsonObject();
            modifier.addProperty("type", "neoforge:add_features");
            modifier.addProperty("biomes", AlteredAether.MOD_ID + ":" + grove + "_archwood_grove");
            modifier.addProperty("features", AlteredAether.MOD_ID + ":shelf/" + grove);
            modifier.addProperty("step", "top_layer_modification");
            data("neoforge/biome_modifier/decoration", AlteredAether.id("shelf_" + grove), modifier, "deep_aether",
                    "ars_nouveau");
        }
    }

    /**
     * The Aether's quicksoil shelves read their {@code y_range} as absolute heights in Java, where the anchor remap
     * can't reach, so the tall pack carries the Aether's own file with that range stretched the same way.
     */
    private void quicksoilShelf() {
        JsonObject feature = ModResources.json("aether", "data", "aether", "worldgen", "configured_feature",
                "quicksoil_shelf.json");
        JsonObject range = feature.getAsJsonObject("config").getAsJsonObject("y_range");
        for (String key : new String[]{"min_inclusive", "max_inclusive"}) {
            range.addProperty(key, -64 + Math.floorDiv(3 * range.get(key).getAsInt(), 2));
        }
        data("worldgen/configured_feature", ResourceLocation.fromNamespaceAndPath("aether", "quicksoil_shelf"), feature);
    }

    /**
     * The Aether's dungeons and large aerclouds take their heights as plain numbers read in Java, which the anchor
     * remap can't reach, so the tall pack carries the Aether's own files with those numbers stretched: heights mapped
     * like the terrain, and spans and offsets scaled by 1.5. These are the same values the old datapack used.
     */
    private void aetherStructures() {
        Set<String> heights = Set.of("minY", "maxY");
        Set<String> spans = Set.of("rangeY", "aboveTerrain", "belowTerrain", "aboveBottom", "belowTop");
        for (String name : new String[]{"bronze_dungeon", "silver_dungeon", "gold_dungeon", "large_aercloud"}) {
            JsonObject structure = ModResources.json("aether", "data", "aether", "worldgen", "structure", name + ".json");
            for (String key : heights) {
                if (structure.has(key)) {
                    structure.addProperty(key, Math.round(1.5 * structure.get(key).getAsInt()) - 64);
                }
            }
            for (String key : spans) {
                if (structure.has(key)) {
                    structure.addProperty(key, Math.round(1.5 * structure.get(key).getAsInt()));
                }
            }
            data("worldgen/structure", ResourceLocation.fromNamespaceAndPath("aether", name), structure);
        }
    }

    /** The taller Aether: -64..319, matching the Overworld. */
    private void dimensionType() {
        JsonObject type = ModResources.json("aether", "data", "aether", "dimension_type", "the_aether.json");
        type.addProperty("min_y", -64);
        type.addProperty("height", 384);
        type.addProperty("logical_height", 384);
        data("dimension_type", ResourceLocation.fromNamespaceAndPath("aether", "the_aether"), type);
    }

    private static void require(JsonObject node, String type) {
        if (!node.get("type").getAsString().equals(type)) {
            throw new IllegalStateException("Aeroblender's skylands changed shape: expected " + type + ", found "
                    + node.get("type"));
        }
    }
}
