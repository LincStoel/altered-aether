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
            Set.of("crag_3d_noise", "jag", "crags", "tempest", "tempest_spires", "dune_lean", "dune_ridges", "plateau",
                    "plateau_terraced", "island", "tall_base_3d_noise");

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
        noise("dune_lean", -6, 1.0);
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
        df("dune_mask", mul(
                min(ramp(terrain("dune_temperature"), -1.6, 10.0), ramp(terrain("dune_temperature"), 4.6, -10.0)),
                min(ramp(terrain("dune_humidity"), 7.3, 10.0), ramp(terrain("dune_humidity"), -0.3, -10.0))));
        // Dune crests across the prevailing wind: gentle windward slopes, steep lee faces, up to 7 blocks high.
        JsonObject ridges = Df.type(NS + "wind_ridges");
        ridges.addProperty("wavelength", 40.0);
        ridges.addProperty("lee", 0.25);
        ridges.addProperty("warp", NS + "dune_warp");
        ridges.addProperty("warp_amount", 24.0);
        ridges.addProperty("swell", NS + "dune_swell");
        df("dune_ridges", flatCache(mul(mul(Df.config("dune_height"), mode.height(7)), ridges)));
        // Sampling the lean noise at a point that drifts sideways with height shears each island from base to top.
        df("dune_lean", mul(0.35, shiftedNoise(NS + "dune_lean",
                add(ref("minecraft:shift_x"), mul(32.0, yGradient(mode.y(-52), 0.0, mode.y(20), 1.0))),
                0.08, 0.0)));

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
        // Dunes: the island sheared by the lean, then raised along the wind ridges.
        JsonObject leaning = add(terrain("island"), mul(terrain("dune_weight"), terrain("dune_lean")));
        JsonObject shifted = Df.type(NS + "shift_y");
        shifted.add("argument", leaning);
        shifted.add("offset", mul(terrain("dune_weight"), terrain("dune_ridges")));
        df("island_dunes", shifted);
        // The rift: the same island broken into drifting slabs, so the slabs carry the dunes too.
        rift(terrain("island_dunes"));
        JsonObject islandTier = Df.choose(terrain("rift_weight"), terrain("island_dunes"), terrain("rift"));
        JsonObject withCrags = Df.choose(terrain("crag_weight"), islandTier, terrain("crag_tier"));
        // Atolls stand over whatever tier is beneath them, each kept wholly inside our region by its site check.
        JsonObject atoll = Df.type(NS + "atoll");
        atoll.add("argument", Df.choose(terrain("plateau_weight"), withCrags, terrain("plateau_terraced")));
        atoll.add("weight", terrain("region_mask"));
        atoll.addProperty("surface", mode.y(32));
        atoll.addProperty("vertical_scale", vertical());
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
