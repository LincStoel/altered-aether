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
package com.linc.alteredaether.gametest;

import com.linc.alteredaether.AlteredAether;
import com.linc.alteredaether.worldgen.camp.Blueprint;
import com.linc.alteredaether.worldgen.camp.Blueprints;
import com.linc.alteredaether.worldgen.camp.Fallbacks;
import com.linc.alteredaether.worldgen.feature.CampFeature;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Map;

/**
 * The hand-built camps, farms and burrows: all of them read, their blocks swapped for stand-ins when a mod is missing,
 * and each placeable on level ground.
 */
@GameTestHolder(AlteredAether.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CampTests {
    private static final int SCAN = 12;

    @GameTest(template = "arena")
    public static void blueprintsAreRead(GameTestHelper helper) {
        String[] groups = {"aether_blue_berry_farm", "aether_camp", "aether_gleamberry_farm", "aether_tree_burrow"};
        int[] expected = {3, 2, 1, 3};
        for (int i = 0; i < groups.length; i++) {
            int found = Blueprints.group(groups[i]).size();
            if (found != expected[i]) {
                helper.fail(groups[i] + ": expected " + expected[i] + " structures, read " + found);
                return;
            }
            for (Blueprint blueprint : Blueprints.group(groups[i])) {
                if (blueprint.cells().isEmpty()) {
                    helper.fail(blueprint.name() + " has no cells");
                    return;
                }
            }
        }
        // Every burrow has a doorway on the middle of its west face, and the creature in the third is kept.
        for (Blueprint burrow : Blueprints.group("aether_tree_burrow")) {
            if (burrow.cellAt(new BlockPos(0, 0, burrow.size().getZ() / 2)) == null) {
                helper.fail(burrow.name() + " has no doorway cell");
                return;
            }
        }
        if (Blueprints.get("aether_tree_burrow_3").figures().size() != 1) {
            helper.fail("the third burrow lost its armour stand");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "arena")
    public static void missingBlocksGiveWay(GameTestHelper helper) {
        String problem = firstProblem();
        if (problem != null) {
            helper.fail(problem);
            return;
        }
        helper.succeed();
    }

    private static String firstProblem() {
        String wrong = expect(Fallbacks.fallback("ascended_quark:skyroot_stool", Map.of("waterlogged", "false")),
                "aether:skyroot_slab");
        wrong = wrong != null ? wrong : expect(Fallbacks.fallback("ascended_quark:skyroot_post", Map.of()),
                "aether:skyroot_fence");
        BlockState wall = Fallbacks.fallback("aether_beyond_parity:stripped_skyroot_log_wall",
                Map.of("north", "low", "south", "low", "east", "none", "west", "none", "up", "false"));
        wrong = wrong != null ? wrong : expect(wall, "aether:skyroot_fence");
        if (wrong != null) {
            return wrong;
        }
        if (!wall.getValue(FenceBlock.NORTH) || !wall.getValue(FenceBlock.SOUTH) || wall.getValue(FenceBlock.EAST)) {
            return "the wall's arms did not become the fence's connections";
        }
        BlockState lantern = Fallbacks.fallback("deep_aether:skyjade_lantern", Map.of("hanging", "true"));
        wrong = expect(lantern, "minecraft:soul_lantern");
        if (wrong != null) {
            return wrong;
        }
        if (!lantern.getValue(LanternBlock.HANGING)) {
            return "the lantern stopped hanging";
        }
        String bench = Fallbacks.available(ResourceLocation.parse("aether_beyond_parity:skyroot_crafting_table"))
                ? "aether_beyond_parity:skyroot_crafting_table" : "minecraft:crafting_table";
        String[][] others = {
                {"ascended_quark:skyroot_leaf_carpet", "", "minecraft:air"},
                {"deep_aether:ambrosium_tiki_torch", "lower", "aether:ambrosium_torch"},
                {"deep_aether:ambrosium_tiki_torch", "upper", "minecraft:air"},
                {"deep_aether:blue_squash", "", "aether:berry_bush"},
                {"deep_aether:squash_stem", "", "aether:berry_bush_stem"},
                {"visualworkbench:aether_beyond_parity/skyroot_crafting_table", "", bench}};
        for (String[] other : others) {
            Map<String, String> properties = other[1].isEmpty() ? Map.of() : Map.of("half", other[1]);
            wrong = expect(Fallbacks.fallback(other[0], properties), other[2]);
            if (wrong != null) {
                return wrong;
            }
        }
        BlockState chest = Fallbacks.fallback("ascended_quark:skyroot_chest", Map.of("facing", "west", "type", "left"));
        if (!(chest.getBlock() instanceof ChestBlock) || chest.getValue(ChestBlock.TYPE) != ChestType.LEFT) {
            return "the chest lost its side";
        }
        return null;
    }

    private static String expect(BlockState state, String id) {
        String actual = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        return actual.equals(id) ? null : "expected " + id + ", got " + actual;
    }

    @GameTest(template = "arena", timeoutTicks = 200)
    public static void campsAndFarmsStand(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos centre = helper.absolutePos(new BlockPos(24, 2, 24));
        String[][] groups = {{"aether_blue_berry_farm", "farm"}, {"aether_camp", "camp"},
                {"aether_gleamberry_farm", "farm"}};
        int placed = 0;
        for (String[] group : groups) {
            for (Blueprint blueprint : Blueprints.group(group[0])) {
                for (Rotation rotation : Rotation.values()) {
                    ground(level, centre);
                    int found = com.linc.alteredaether.worldgen.ruin.RuinPlan.groundOf(level, centre.getX(), centre.getZ());
                    if (found != centre.getY() - 1) {
                        helper.fail("ground reads " + found + ", built at " + (centre.getY() - 1));
                        return;
                    }
                    if (!CampFeature.place(level, RandomSource.create(7), centre, blueprint, rotation, Mirror.NONE,
                            group[1].equals("farm"), 2, false)) {
                        helper.fail(blueprint.name() + " " + rotation + " would not stand on level ground");
                        return;
                    }
                    int blocks = 0;
                    BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
                    for (int dx = -SCAN; dx <= SCAN; dx++) {
                        for (int dz = -SCAN; dz <= SCAN; dz++) {
                            for (int dy = 0; dy < 10; dy++) {
                                BlockState state = level.getBlockState(pos.setWithOffset(centre, dx, dy, dz));
                                if (!state.isAir() && !state.is(Blocks.DIRT) && !state.is(Blocks.GRASS_BLOCK)) {
                                    blocks++;
                                    if (state.getBlock() instanceof ChestBlock && level.getBlockEntity(pos)
                                            instanceof RandomizableContainerBlockEntity chest
                                            && chest.getLootTable() == null) {
                                        helper.fail(blueprint.name() + " has a chest with no loot");
                                        return;
                                    }
                                }
                            }
                        }
                    }
                    if (blocks < 5) {
                        helper.fail(blueprint.name() + " " + rotation + " placed only " + blocks + " blocks");
                        return;
                    }
                    placed += blocks;
                }
            }
        }
        // And not across a steep slope: a block of rise every three eastward.
        ground(level, centre);
        for (int dx = -SCAN; dx <= SCAN; dx++) {
            for (int dz = -SCAN; dz <= SCAN; dz++) {
                for (int y = 0; y <= (dx + SCAN) / 3; y++) {
                    level.setBlock(centre.offset(dx, y, dz), Blocks.DIRT.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
        }
        if (CampFeature.place(level, RandomSource.create(7), centre, Blueprints.get("aether_camp_1"), Rotation.NONE,
                Mirror.NONE, false, 2, false)) {
            helper.fail("a camp was set on a steep slope");
            return;
        }
        ground(level, centre);
        AlteredAether.LOGGER.info("Camps: {} blocks over every blueprint and turn", placed);
        helper.succeed();
    }

    /** Flat grass over dirt with its top below the centre, and the air above it cleared, arena roof and all. */
    private static void ground(ServerLevel level, BlockPos centre) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = -SCAN; dx <= SCAN; dx++) {
            for (int dz = -SCAN; dz <= SCAN; dz++) {
                for (int y = -3; y <= 64; y++) {
                    BlockState state = y < 0 ? Blocks.DIRT.defaultBlockState() : y == 0
                            ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.AIR.defaultBlockState();
                    level.setBlock(pos.setWithOffset(centre, dx, y - 1, dz), state, Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    private CampTests() {
    }
}
