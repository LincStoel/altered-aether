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
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.ChestBlock;
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
 * flame lights any of it; fire goes out in the Aether. Deep Aether's lanterns and glowing plants appear only when it is
 * present; without it the lamps are ambrosium torches.
 */
final class Furnish {
    static final ResourceKey<LootTable> CAMP = loot("camp"), SECRET = loot("secret"), HEARTWOOD = loot("heartwood");

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

    private static Direction horizontal(double angle) {
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
        // Light: a hanging lantern with Deep Aether, an ambrosium torch on the floor without.
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

    // ------------------------------------------------------------------------------------------------ light --

    /**
     * A Deep Aether skyjade lantern hung on a chain of {@code chain} links from the first underside above (x, y, z).
     * False without Deep Aether, when there is nothing to hang.
     */
    static boolean hanging(Sketch s, int x, int y, int z, int chain) {
        BlockState lantern = deep("skyjade_lantern"), link = deep("skyjade_chain");
        if (lantern == null || link == null) {
            return false;
        }
        List<BlockState> blocks = new ArrayList<>();
        for (int i = 0; i < chain; i++) {
            blocks.add(link.trySetValue(RotatedPillarBlock.AXIS, Direction.Axis.Y));
        }
        blocks.add(with(lantern, "hanging", "true"));
        s.prop(new Prop(x, y, z, Prop.Anchor.HANG, blocks, null));
        return true;
    }

    /**
     * A lamp beside a trail, standing on the ground: an ambrosium torch on a skyroot fence post, or with Deep Aether
     * sometimes a tiki torch, a skyjade lantern on the post or one set on the ground. Unlit, the post stands bare.
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
        BlockState tiki = deep("ambrosium_tiki_torch"), lantern = deep("skyjade_lantern");
        int kind = tiki == null ? 0 : s.random.nextInt(4);
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
