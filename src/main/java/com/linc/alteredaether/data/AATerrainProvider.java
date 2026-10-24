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
import static com.linc.alteredaether.data.Df.flatCache;
import static com.linc.alteredaether.data.Df.lerp;
import static com.linc.alteredaether.data.Df.min;
import static com.linc.alteredaether.data.Df.mul;
import static com.linc.alteredaether.data.Df.oldBlendedNoise;
import static com.linc.alteredaether.data.Df.ramp;
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
    /** Strength of the rift carve: far beyond anything the island expression reaches, so a full weight is air. */
    private static final double RIFT_CARVE_STRENGTH = 8.0;
    /** Density functions whose shape depends on height, and so are overridden by the tall pack. */
    private static final Set<String> HEIGHT_DEPENDENT =
            Set.of("crag_3d_noise", "crags", "dune_lean", "plateau", "rift_carve_noise", "jag", "tall_base_3d_noise");

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
        noise("rift_carve", -3, 1.0, 0.5);
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

        // Crag tier, on erosion: the same field drives the biome (erosion > 0.6125) and this mask, which ramps
        // 0 -> 1 across 0.4875..0.7375, so the biome edge sits mid-ramp. Tempest Peaks is the top slice of the same
        // tier, where the mask is already saturated.
        df("crag_noise", flatCache(shiftedNoise(NS + "crags", 0.25, 0.0)));
        df("crag_mask", ramp(terrain("crag_noise"), -1.95, 4.0));
        // The island noise made half as wide and twice as tall; same value distribution, taller thinner shapes.
        df("crag_3d_noise", oldBlendedNoise(0.25, 0.25, 40.0, mode.height(480.0), 8.0));
        // A fine wobble that roughens isosurfaces so carved and crag walls don't look stamped.
        df("jag", shiftedNoise(NS + "crag_jag", 1.0, mode.frequency(0.5)));
        // The island expression with its envelope stretched (full strength over y -24..70 in tall mode instead of
        // about 25 blocks). The taller envelope needs a higher solid threshold (-0.22, not -0.1) or the same fill
        // fraction over 95 blocks reads as a wall of stone rather than tall thin islands.
        df("crags", add(-0.15, mul(yGradient(mode.y(-64), 0.0, mode.y(-24), 1.0),
                add(-0.22, mul(yGradient(mode.y(70), 1.0, mode.y(128), 0.0),
                        add(0.07, add(terrain("crag_3d_noise"), mul(0.15, terrain("jag")))))))));

        // Shattered Rift, on weirdness. The carve uses a second, higher-frequency field so only about a fifth of
        // the rift's footprint is removed (cracks, not scattered islands), and it varies with height so the walls
        // look eroded rather than cut by a cookie cutter.
        df("rift_noise", flatCache(shiftedNoise(NS + "rift", 0.25, 0.0)));
        df("rift_mask", ramp(terrain("rift_noise"), -1.95, 4.0));
        df("rift_carve_noise", add(shiftedNoise(NS + "rift_carve", 0.25, mode.frequency(0.15)),
                mul(0.15, terrain("jag"))));
        df("rift_carve_mask", ramp(terrain("rift_carve_noise"), -1.58267, 8.3333));

        // Windswept Dunes: a box on the same temperature and humidity fields the biome selector reads, so the lean
        // lines up with the biome window exactly.
        df("dune_temperature", flatCache(shiftedNoise("aeroblender:temperature", 0.25, 0.0)));
        df("dune_humidity", flatCache(shiftedNoise("aether:vegetation", 0.25, 0.0)));
        df("dune_mask", mul(
                min(ramp(terrain("dune_temperature"), -1.6, 10.0), ramp(terrain("dune_temperature"), 4.6, -10.0)),
                min(ramp(terrain("dune_humidity"), 7.3, 10.0), ramp(terrain("dune_humidity"), -0.3, -10.0))));
        // Sampling the lean noise at a point that drifts sideways with height shears each island from base to top.
        df("dune_lean", mul(0.35, shiftedNoise(NS + "dune_lean",
                add(ref("minecraft:shift_x"), mul(32.0, yGradient(mode.y(-52), 0.0, mode.y(20), 1.0))),
                0.08, 0.0)));

        // Each landform's weight, confined to our region.
        df("plateau_weight", mul(terrain("region_mask"), terrain("plateau_mask")));
        df("crag_weight", mul(terrain("region_mask"), terrain("crag_mask")));
        df("rift_weight", mul(terrain("region_mask"), mul(terrain("rift_mask"), terrain("rift_carve_mask"))));
        df("dune_weight", mul(terrain("region_mask"), terrain("dune_mask")));

        if (mode == HeightMode.TALL) {
            // The Aether's own island noise, stretched to the taller band (y_factor 160 -> 240).
            df("tall_base_3d_noise", oldBlendedNoise(0.25, 0.25, 80.0, 240.0, 8.0));
        }
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
     *   {@code lerp(plateau, lerp(crags, island + dunes - rift, crags), plateau)}. The crag blend sits inside the
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
        JsonObject withDunes = add(island, mul(terrain("dune_weight"), terrain("dune_lean")));
        JsonObject rifted = add(withDunes, mul(-RIFT_CARVE_STRENGTH, terrain("rift_weight")));
        JsonObject islandTier = lerp(terrain("crag_weight"), rifted, terrain("crags"));
        blend.add("argument", lerp(terrain("plateau_weight"), islandTier, terrain("plateau")));

        data("worldgen/noise_settings", ResourceLocation.fromNamespaceAndPath("aether", "skylands"), settings);
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
