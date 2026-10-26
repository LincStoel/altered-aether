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
package com.linc.alteredaether.worldgen.grove;

import com.linc.alteredaether.AlteredAether;
import com.linc.alteredaether.config.AACommonConfig;
import com.linc.alteredaether.registry.AARegistries;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.storage.loot.LootTable;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * What the pilgrims who tended the groves left behind, and what grows on the dead: camps round an ambrosium campfire,
 * chests, lamp posts along the trails, lanterns hung from the bows, glowing growth and young life on fallen wood. No
 * ordinary flame lights any of it; fire goes out in the Aether. Lanterns are Deep Aether's skyjade when it is present
 * and soul lanterns without it; its tiki torches and glowing plants appear only with it.
 */
final class Furnish {
    static final ResourceKey<LootTable> CAMP = loot("camp"), SECRET = loot("secret"), HEARTWOOD = loot("heartwood"),
            STUMP = loot("stump"), VILLAGE = loot("village_crate");

    private static ResourceKey<LootTable> loot(String name) {
        return ResourceKey.create(Registries.LOOT_TABLE, AlteredAether.id("chests/grove/" + name));
    }

    @Nullable
    private static BlockState block(String id) {
        return BuiltInRegistries.BLOCK.getOptional(ResourceLocation.parse(id)).map(Block::defaultBlockState)
                .orElse(null);
    }

    /** A Deep Aether block, or null without Deep Aether or with its compat switch off. */
    @Nullable
    private static BlockState deep(String name) {
        return ModList.get().isLoaded("deep_aether") && AACommonConfig.compat("deep_aether")
                ? block("deep_aether:" + name) : null;
    }

    /** Sets a property by name, for blocks of other mods whose property classes we don't link against. */
    private static BlockState with(BlockState state, String name, String value) {
        for (Property<?> property : state.getProperties()) {
            if (property.getName().equals(name)) {
                return set(state, property, value);
            }
        }
        return state;
    }

    private static <T extends Comparable<T>> BlockState set(BlockState state, Property<T> property, String value) {
        Optional<T> parsed = property.getValue(value);
        return parsed.map(v -> state.setValue(property, v)).orElse(state);
    }

    static Direction horizontal(double angle) {
        return Direction.fromYRot(angle * Mth.RAD_TO_DEG - 90);
    }

    // ------------------------------------------------------------------------------------------------ camps --

    /**
     * A pilgrims' camp round an ambrosium campfire at a hollow's floor: seats of stripped log, a bedroll, a chest, a
     * light. A tended camp's fire still burns; an abandoned one's is cold and its light is gone. {@code room} is
     * how far from the fire the furniture may stand; {@code facing} turns the layout, so a camp in a fallen log lies
     * along it.
     */
    static void camp(Sketch s, double cx, int floor, double cz, double facing, double room, boolean tended,
                     Prop.Anchor anchor) {
        int x = Mth.floor(cx), z = Mth.floor(cz);
        BlockState fire = AARegistries.AMBROSIUM_CAMPFIRE.get().defaultBlockState()
                .setValue(CampfireBlock.LIT, tended).setValue(CampfireBlock.FACING, horizontal(facing));
        s.prop(new Prop(x, floor, z, anchor, List.of(fire), null));
        s.point("camp", x, floor, z);

        double d = Math.min(room, 3);
        // A seat either side of the fire, across the layout.
        for (int side = -1; side <= 1; side += 2) {
            double a = facing + side * Mth.HALF_PI;
            Direction.Axis axis = Math.abs(Math.cos(facing)) > Math.abs(Math.sin(facing)) ? Direction.Axis.X
                    : Direction.Axis.Z;
            BlockState seat = s.palette == null ? null : natural(s.palette.strippedLog().defaultBlockState()
                    .trySetValue(RotatedPillarBlock.AXIS, axis));
            if (seat != null && room >= 2.5) {
                s.prop(new Prop(Mth.floor(cx + Math.cos(a) * 2), floor, Mth.floor(cz + Math.sin(a) * 2), anchor,
                        List.of(seat), null));
            }
        }
        // The bedroll beyond the fire, head away from it.
        // Only on a hollow's level floor: on open ground the bed's two halves could land at different heights.
        BlockState bed = block("aether:skyroot_bed");
        if (bed != null && anchor == Prop.Anchor.AT) {
            Direction toward = horizontal(facing);
            double fx = cx + Math.cos(facing) * d, fz = cz + Math.sin(facing) * d;
            BlockState foot = bed.setValue(BedBlock.FACING, toward).setValue(BedBlock.PART, BedPart.FOOT);
            int footX = Mth.floor(fx), footZ = Mth.floor(fz);
            s.prop(new Prop(footX, floor, footZ, anchor, List.of(foot), null));
            s.prop(new Prop(footX + toward.getStepX(), floor, footZ + toward.getStepZ(), anchor,
                    List.of(bed.setValue(BedBlock.FACING, toward).setValue(BedBlock.PART, BedPart.HEAD)), null));
        }
        // The chest on the near side, facing the fire.
        if (s.random.nextDouble() < 0.7) {
            double back = facing + Math.PI;
            chest(s, cx + Math.cos(back) * d, floor, cz + Math.sin(back) * d, horizontal(facing), CAMP, anchor);
        }
        // Light: a lantern hung over the fire, or an ambrosium torch on the floor if none can be hung.
        if (tended) {
            double a = facing + Mth.HALF_PI * 0.5;
            int lx = Mth.floor(cx + Math.cos(a) * 1.6), lz = Mth.floor(cz + Math.sin(a) * 1.6);
            if (!hanging(s, lx, floor + 2, lz, 1)) {
                BlockState torch = block("aether:ambrosium_torch");
                if (torch != null) {
                    s.prop(new Prop(lx, floor, lz, anchor, List.of(torch), null));
                }
            }
        }
    }

    static void chest(Sketch s, double x, int y, double z, Direction facing, ResourceKey<LootTable> loot,
                      Prop.Anchor anchor) {
        s.prop(new Prop(Mth.floor(x), y, Mth.floor(z), anchor,
                List.of(Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing)), loot));
        s.point("chest", x, y, z);
    }

    // ----------------------------------------------------------------------------------------------- houses --

    /** What a deserted house may still hold, one of each at most. */
    private enum Keepsake { BED, STATION, CRATE, POT }

    /** The Aether's workstations, the old villagers' tools. */
    private static final String[] STATIONS = {"aether:altar", "aether:freezer", "aether:incubator",
            "aether:skyroot_bookshelf", "aether:altar", "aether:freezer"};

    /**
     * What is left in a deserted house round (cx, cz) on its floor: one to three things, no more. A bed against the
     * back wall, head to the heartwood; one of the old workstations; a crate of odds and ends; a potted plant gone
     * wild; now and then a lantern still hanging. {@code out} is the bearing from the house to its door.
     */
    static void house(Sketch s, double cx, int floor, double cz, double out) {
        double in = out + Math.PI, side = out + Mth.HALF_PI;
        List<Keepsake> left = new ArrayList<>(List.of(Keepsake.values()));
        int items = s.range(1, 3);
        for (int n = 0; n < items && !left.isEmpty(); n++) {
            Keepsake keepsake = left.remove(s.random.nextInt(left.size()));
            switch (keepsake) {
                case BED -> {
                    BlockState bed = block("aether:skyroot_bed");
                    if (bed != null) {
                        Direction toward = horizontal(in);
                        int fx = Mth.floor(cx + Math.cos(in) * 0.6), fz = Mth.floor(cz + Math.sin(in) * 0.6);
                        s.prop(new Prop(fx, floor, fz, Prop.Anchor.AT, List.of(bed.setValue(BedBlock.FACING, toward)
                                .setValue(BedBlock.PART, BedPart.FOOT)), null));
                        s.prop(new Prop(fx + toward.getStepX(), floor, fz + toward.getStepZ(), Prop.Anchor.AT,
                                List.of(bed.setValue(BedBlock.FACING, toward).setValue(BedBlock.PART, BedPart.HEAD)),
                                null));
                    }
                }
                case STATION -> {
                    BlockState station = block(STATIONS[s.random.nextInt(STATIONS.length)]);
                    if (station != null) {
                        station = with(station, "facing", horizontal(out).getSerializedName());
                        s.prop(new Prop(Mth.floor(cx + Math.cos(side) * 1.7), floor,
                                Mth.floor(cz + Math.sin(side) * 1.7), Prop.Anchor.AT, List.of(station), null));
                    }
                }
                case CRATE -> crate(s, cx - Math.cos(side) * 1.7, floor, cz - Math.sin(side) * 1.7, out);
                case POT -> {
                    BlockState pot = block(s.random.nextBoolean() ? "aether:potted_skyroot_sapling"
                            : "aether:potted_white_flower");
                    if (pot != null) {
                        s.prop(new Prop(Mth.floor(cx + Math.cos(in) * 1.6 + Math.cos(side)),
                                floor, Mth.floor(cz + Math.sin(in) * 1.6 + Math.sin(side)), Prop.Anchor.AT,
                                List.of(pot), null));
                    }
                }
            }
        }
        if (s.random.nextDouble() < 0.35) {
            hanging(s, Mth.floor(cx), floor + 2, Mth.floor(cz), 1);
        }
        s.point("house", cx, floor, cz);
    }

    /** A crate of whatever the villagers left behind: a barrel, or now and then a chest. */
    static void crate(Sketch s, double x, int floor, double z, double facing) {
        BlockState crate = s.random.nextDouble() < 0.75
                ? Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP)
                : Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, horizontal(facing));
        s.prop(new Prop(Mth.floor(x), floor, Mth.floor(z), Prop.Anchor.AT, List.of(crate), VILLAGE));
    }

    /** A workstation standing out on a street, its users long gone. */
    static void station(Sketch s, double x, int floor, double z, double facing) {
        BlockState station = block(STATIONS[s.random.nextInt(STATIONS.length)]);
        if (station != null) {
            s.prop(new Prop(Mth.floor(x), floor, Mth.floor(z), Prop.Anchor.AT,
                    List.of(with(station, "facing", horizontal(facing).getSerializedName())), null));
        }
    }

    /** A post of a railing, of skyroot fence, {@code tall} blocks high. */
    static void rail(Sketch s, int x, int floor, int z, int tall) {
        BlockState fence = block("aether:skyroot_fence");
        if (fence != null) {
            List<BlockState> blocks = new ArrayList<>();
            for (int i = 0; i < tall; i++) {
                blocks.add(fence);
            }
            s.prop(new Prop(x, floor, z, Prop.Anchor.AT, blocks, null));
        }
    }

    /** One block set exactly where it is drawn, if it exists. */
    static void place(Sketch s, int x, int y, int z, String id) {
        BlockState state = block(id);
        if (state != null) {
            s.prop(new Prop(x, y, z, Prop.Anchor.AT, List.of(state), null));
        }
    }

    // ---------------------------------------------------------------------------------------- atrium, fields --

    /** One block of a long table of skyroot planks. */
    static void table(Sketch s, int x, int floor, int z) {
        place(s, x, floor, z, "aether:skyroot_planks");
    }

    /** A seat of skyroot stair at a table, its back to {@code back}. */
    static void seat(Sketch s, int x, int floor, int z, Direction back) {
        BlockState stair = block("aether:skyroot_stairs");
        if (stair != null) {
            s.prop(new Prop(x, floor, z, Prop.Anchor.AT, List.of(with(stair, "facing", back.getSerializedName())),
                    null));
        }
    }

    /** A lantern set down on a table or the floor. */
    static void standingLantern(Sketch s, int x, int y, int z) {
        s.prop(new Prop(x, y, z, Prop.Anchor.AT, List.of(with(lantern(), "hanging", "false")), null));
    }

    /** A drip of glowstone {@code length} blocks long from the first underside above (x, y, z). */
    static void glowstone(Sketch s, int x, int y, int z, int length) {
        List<BlockState> blocks = new ArrayList<>();
        for (int i = 0; i < length; i++) {
            blocks.add(Blocks.GLOWSTONE.defaultBlockState());
        }
        s.prop(new Prop(x, y, z, Prop.Anchor.HANG, blocks, null));
    }

    /** Deep Aether's totems, the villagers' carvings of the sky's creatures. */
    private static final String[] TOTEMS = {"moa_totem", "aerwhale_totem", "zephyr_totem"};

    /**
     * With Deep Aether, a totem set in a niche cut into the wall at (x, y, z), facing out of it; nothing without.
     * Returns whether there was one.
     */
    static boolean totem(Sketch s, int x, int y, int z, Direction facing) {
        BlockState totem = deep(TOTEMS[s.random.nextInt(TOTEMS.length)]);
        if (totem == null) {
            return false;
        }
        s.carve(com.linc.alteredaether.worldgen.grove.shape.Carve.chamber(x, y, z, 0.7, 0.7, 0.7, y));
        s.prop(new Prop(x, y, z, Prop.Anchor.AT, List.of(with(totem, "facing", facing.getSerializedName())), null));
        return true;
    }

    /** A ladder {@code tall} rungs high up from (x, floor, z), its back to the wall opposite {@code facing}. */
    static void ladder(Sketch s, int x, int floor, int z, int tall, Direction facing) {
        List<BlockState> blocks = new ArrayList<>();
        for (int i = 0; i < tall; i++) {
            blocks.add(Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, facing));
        }
        s.prop(new Prop(x, floor, z, Prop.Anchor.AT, blocks, null));
    }

    /** What one patch of the villagers' fields was given over to. */
    enum Crop {
        /** Deep Aether's squash, on vines across tilled earth. */
        SQUASH,
        /** Rows of the Aether's blue berry bushes. */
        BERRIES,
        /** Seed brought up from below, now and then: wheat, carrots or potatoes. */
        OVERWORLD
    }

    /** The overworld crops, rare up here. */
    private static final Block[] OVERWORLD = {Blocks.WHEAT, Blocks.CARROTS, Blocks.POTATOES};
    /** The squash a vine may bear. */
    private static final String[] SQUASH = {"blue_squash", "green_squash", "purple_squash"};

    /** What a patch grows: mostly the Aether's own plants, an overworld crop about one patch in ten. */
    static Crop crop(Sketch s) {
        double roll = s.random.nextDouble();
        if (roll < 0.1) {
            return Crop.OVERWORLD;
        }
        return deep("squash_stem") != null && roll < 0.6 ? Crop.SQUASH : Crop.BERRIES;
    }

    /**
     * One block of an old field on the solid top near {@code guess}, of the patch's {@code crop}: a squash vine on
     * tilled earth, now and then bearing its squash on the ground beside it; a berry bush, or a young one; or an
     * overworld crop at whatever stage it reached. Where the weeds have won, grass and whatever seeded itself.
     * {@code taken} holds the columns already planted, as {@code BlockPos.asLong(x, 0, z)}; this adds its own.
     */
    static void field(Sketch s, int x, int guess, int z, Crop crop, java.util.Set<Long> taken) {
        if (!taken.add(net.minecraft.core.BlockPos.asLong(x, 0, z))) {
            return;
        }
        BlockState farmland = block("aether:aether_farmland");
        BlockState grass = block("aether:aether_grass_block");
        double roll = s.random.nextDouble();
        if (farmland != null && roll < 0.65) {
            BlockState tilled = with(farmland, "moisture", "7");
            switch (crop) {
                case SQUASH -> {
                    BlockState stem = deep("squash_stem"), attached = deep("attached_squash_stem");
                    BlockState fruit = deep(SQUASH[s.random.nextInt(SQUASH.length)]);
                    Direction toward = Direction.Plane.HORIZONTAL.getRandomDirection(s.random);
                    if (attached != null && fruit != null && s.random.nextDouble() < 0.2 && taken.add(
                            net.minecraft.core.BlockPos.asLong(x + toward.getStepX(), 0, z + toward.getStepZ()))) {
                        s.prop(new Prop(x, guess, z, Prop.Anchor.TOPSOIL, List.of(tilled,
                                with(attached, "facing", toward.getSerializedName())), null));
                        s.prop(new Prop(x + toward.getStepX(), guess, z + toward.getStepZ(), Prop.Anchor.GROUND,
                                List.of(fruit), null));
                    } else if (stem != null) {
                        s.prop(new Prop(x, guess, z, Prop.Anchor.TOPSOIL,
                                List.of(tilled, with(stem, "age", String.valueOf(s.random.nextInt(8)))), null));
                    }
                    return;
                }
                case BERRIES -> {
                    BlockState bush = block(s.random.nextDouble() < 0.7 ? "aether:berry_bush"
                            : "aether:berry_bush_stem");
                    if (bush != null && grass != null) {
                        s.prop(new Prop(x, guess, z, Prop.Anchor.TOPSOIL, List.of(natural(grass), natural(bush)),
                                null));
                    }
                    return;
                }
                case OVERWORLD -> {
                    Block kind = OVERWORLD[s.random.nextInt(OVERWORLD.length)];
                    BlockState plant = kind.defaultBlockState().setValue(CropBlock.AGE, 2 + s.random.nextInt(6));
                    s.prop(new Prop(x, guess, z, Prop.Anchor.TOPSOIL, List.of(tilled, plant), null));
                    return;
                }
            }
        }
        if (grass == null) {
            return;
        }
        double weed = s.random.nextDouble();
        List<BlockState> blocks = new ArrayList<>(List.of(natural(grass)));
        if (weed < 0.3) {
            BlockState feather = deep("tall_feather_grass");
            BlockState tall = feather != null && s.random.nextBoolean() ? feather : Blocks.TALL_GRASS.defaultBlockState();
            blocks.add(with(tall, "half", "lower"));
            blocks.add(with(tall, "half", "upper"));
        } else if (weed < 0.7) {
            BlockState feather = deep("feather_grass");
            blocks.add(feather != null && s.random.nextBoolean() ? feather : Blocks.SHORT_GRASS.defaultBlockState());
        } else {
            String[] flowers = {"aether:white_flower", "aether:purple_flower", "deep_aether:aerlavender",
                    "deep_aether:sky_tulips", "deep_aether:radiant_orchid"};
            String id = flowers[s.random.nextInt(flowers.length)];
            BlockState flower = id.startsWith("deep_aether:") ? deep(id.substring(12)) : block(id);
            if (flower == null) {
                flower = block("aether:white_flower");
            }
            if (flower != null) {
                blocks.add(flower);
            }
        }
        s.prop(new Prop(x, guess, z, Prop.Anchor.TOPSOIL, blocks, null));
    }

    // ------------------------------------------------------------------------------------------------ light --

    /** Deep Aether's skyjade lantern, or a soul lantern without it. */
    private static BlockState lantern() {
        BlockState skyjade = deep("skyjade_lantern");
        return skyjade != null ? skyjade : Blocks.SOUL_LANTERN.defaultBlockState();
    }

    /** Deep Aether's skyjade chain, or an iron chain without it. */
    private static BlockState chainLink() {
        BlockState skyjade = deep("skyjade_chain");
        return skyjade != null ? skyjade : Blocks.CHAIN.defaultBlockState();
    }

    /**
     * A lantern hung on a chain of {@code chain} links from the first underside above (x, y, z): skyjade with Deep
     * Aether, soul lantern and iron chain without. Always true now that a lantern is always at hand.
     */
    static boolean hanging(Sketch s, int x, int y, int z, int chain) {
        BlockState lantern = lantern(), link = chainLink();
        List<BlockState> blocks = new ArrayList<>();
        for (int i = 0; i < chain; i++) {
            blocks.add(link.trySetValue(RotatedPillarBlock.AXIS, Direction.Axis.Y));
        }
        blocks.add(with(lantern, "hanging", "true"));
        s.prop(new Prop(x, y, z, Prop.Anchor.HANG, blocks, null));
        return true;
    }

    /**
     * A lamp beside a trail, standing on the ground: an ambrosium torch on a skyroot fence post, sometimes a lantern
     * on the post or one set on the ground, and with Deep Aether a tiki torch. Unlit, the post stands bare.
     */
    static void lamp(Sketch s, double x, int guess, double z, boolean lit) {
        BlockState post = block("aether:skyroot_fence");
        BlockState torch = block("aether:ambrosium_torch");
        if (post == null) {
            return;
        }
        int px = Mth.floor(x), pz = Mth.floor(z);
        if (!lit) {
            // Some posts of a forgotten trail have long since fallen.
            if (s.random.nextDouble() < 0.6) {
                s.prop(new Prop(px, guess, pz, Prop.Anchor.GROUND, List.of(post), null));
            }
            return;
        }
        BlockState tiki = deep("ambrosium_tiki_torch"), lantern = lantern();
        // Without Deep Aether there is no tiki torch: the post's torch takes its share.
        int kind = s.random.nextInt(4);
        if (kind == 1 && tiki == null) {
            kind = 0;
        }
        List<BlockState> blocks = switch (kind) {
            case 1 -> List.of(with(tiki, "half", "lower"), with(tiki, "half", "upper"));
            case 2 -> List.of(post, with(lantern, "hanging", "false"));
            case 3 -> List.of(with(lantern, "hanging", "false"));
            default -> torch == null ? List.of(post) : List.of(post, torch);
        };
        s.prop(new Prop(px, guess, pz, Prop.Anchor.GROUND, blocks, null));
    }

    // ------------------------------------------------------------------------------------------------ growth --

    /** A glowing vine under a bow or a hollow's ceiling, with Deep Aether. */
    static void glowVine(Sketch s, int x, int y, int z) {
        BlockState vine = deep("glowing_vine");
        if (vine != null) {
            s.prop(new Prop(x, y, z, Prop.Anchor.HANG, List.of(with(vine, "up", "true")), null));
        }
    }

    /**
     * Life taking hold on dead wood: a patch of soil gathered on top, and in it Aether grass, a flower or now and then
     * a skyroot sapling; with Deep Aether, often just a carpet of moss.
     */
    static void growth(Sketch s, double x, int guess, double z) {
        BlockState moss = deep("aether_moss_carpet");
        BlockState soil = block("aether:aether_grass_block");
        int px = Mth.floor(x), pz = Mth.floor(z);
        double roll = s.random.nextDouble();
        if (moss != null && roll < 0.4) {
            s.prop(new Prop(px, guess, pz, Prop.Anchor.GROUND, List.of(moss), null));
            return;
        }
        if (soil == null) {
            return;
        }
        BlockState plant = roll < 0.55 ? block("aether:skyroot_sapling")
                : roll < 0.75 ? block(s.random.nextBoolean() ? "aether:white_flower" : "aether:purple_flower")
                : Blocks.SHORT_GRASS.defaultBlockState();
        soil = GrovePalette.natural(soil);
        s.prop(new Prop(px, guess, pz, Prop.Anchor.TOPSOIL, plant == null ? List.of(soil) : List.of(soil, plant),
                null));
    }

    private static BlockState natural(BlockState state) {
        return GrovePalette.natural(state);
    }

    private Furnish() {
    }
}
