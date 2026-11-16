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

import com.aetherteam.aether.block.AetherBlocks;
import com.linc.alteredaether.AlteredAether;
import com.linc.alteredaether.compat.SableLift;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.physics.floating_block.FloatingBlockCluster;
import dev.ryanhcode.sable.physics.floating_block.FloatingBlockController;
import dev.ryanhcode.sable.physics.floating_block.FloatingBlockData;
import dev.ryanhcode.sable.physics.floating_block.FloatingClusterContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Sable ships carrying gravitite, built in the sky near the world's origin (Sable struggles with the GameTest arena's
 * coordinates, millions of blocks out) and assembled into sub-levels:
 *
 * <ul>
 *   <li>"powered": enchanted gravitite capped with stone, a lever on it switched on before assembly. This is the
 *   path a ship loaded from disk takes: Sable is told of the block while its signal is already there.</li>
 *   <li>"unpowered": the same with the lever off.</li>
 *   <li>"toggled": assembled with the lever off, then switched on, then off again.</li>
 *   <li>"ore": gravitite ore with open air above it, which the Aether would make fall upward: it must stay and lift.</li>
 *   <li>"open": powered enchanted gravitite with open air above it, which must also stay and lift.</li>
 * </ul>
 *
 * <p>Each ship's lift is read from Sable itself, the blocks in its floating clusters, as well as from our gate; the
 * powered and unpowered ships' heights are compared once physics has run. Everything seen is logged under
 * "Gravitite Sable check". Loaded only with Sable.
 */
final class GravititeSableCheck {
    private static final int HEIGHT = 220;
    private static final BlockState STONE = Blocks.STONE.defaultBlockState();

    /** A ship: its sub-level, and the plot positions of the gravitite and the lever on it. */
    private record Ship(String name, ServerSubLevel subLevel, BlockPos gravitite, BlockPos lever) {
        SableLift.Tracked gate() {
            return (SableLift.Tracked) subLevel.getFloatingBlockController();
        }

        double y() {
            return subLevel.logicalPose().position().y();
        }
    }

    private GravititeSableCheck() {
    }

    static void run(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<String> failures = new ArrayList<>();
        Ship powered = build(level, 0, AetherBlocks.ENCHANTED_GRAVITITE.get(), true, true, "powered");
        Ship unpowered = build(level, 1, AetherBlocks.ENCHANTED_GRAVITITE.get(), true, false, "unpowered");
        Ship toggled = build(level, 2, AetherBlocks.ENCHANTED_GRAVITITE.get(), true, false, "toggled");
        Ship ore = build(level, 3, AetherBlocks.GRAVITITE_ORE.get(), false, false, "ore");
        Ship open = build(level, 4, AetherBlocks.ENCHANTED_GRAVITITE.get(), false, true, "open");
        double startPowered = powered.y(), startUnpowered = unpowered.y();

        helper.runAfterDelay(2, () -> log("powered ship after 2 ticks: " + describe(level, powered)));
        helper.runAfterDelay(20, () -> {
            log("powered ship after 20 ticks: " + describe(level, powered));
            expect(failures, powered, true, 1);
            expect(failures, unpowered, false, 0);
            expect(failures, toggled, false, 0);
            pull(level, toggled);
            log("ore ship after 20 ticks: " + describe(level, ore));
            log("open ship after 20 ticks: " + describe(level, open));
            stays(failures, level, ore, AetherBlocks.GRAVITITE_ORE.get());
            stays(failures, level, open, AetherBlocks.ENCHANTED_GRAVITITE.get());
            expect(failures, open, true, 1);
        });
        helper.runAfterDelay(40, () -> {
            expect(failures, toggled, true, 1);
            pull(level, toggled);
        });
        boolean[] reached = {false};
        helper.runAfterDelay(60, () -> {
            // GameTest can run a delayed step twice when another is added on the same tick
            if (reached[0]) {
                return;
            }
            reached[0] = true;
            expect(failures, toggled, false, 0);
            double fellPowered = startPowered - powered.y(), fellUnpowered = startUnpowered - unpowered.y();
            log(String.format("after 60 ticks the powered ship fell %.3f, the unpowered one %.3f", fellPowered,
                    fellUnpowered));
            if (fellPowered >= fellUnpowered - 0.05) {
                failures.add(String.format("the powered ship should fall slower than the unpowered one (%.3f vs %.3f)",
                        fellPowered, fellUnpowered));
            }
            String oreState = describe(level, ore);
            log("ore ship after 60 ticks: " + oreState);
            log("open ship after 60 ticks: " + describe(level, open));
            stays(failures, level, ore, AetherBlocks.GRAVITITE_ORE.get());
            stays(failures, level, open, AetherBlocks.ENCHANTED_GRAVITITE.get());
            for (int slot = 0; slot < 5; slot++) {
                level.setChunkForced(chunkX(slot), 0, false);
            }
            reload(helper, level, powered, failures);
        });
    }

    /**
     * A relog, as near as a server with no players gets: the powered ship's chunk is let go, so Sable puts the ship
     * away; then it is loaded again, and Sable rebuilds the ship from storage, telling its new controller of the
     * gravitite already powered. It must lift again.
     */
    private static void reload(GameTestHelper helper, ServerLevel level, Ship ship, List<String> failures) {
        UUID id = ship.subLevel().getUniqueId();
        int chunkX = chunkX(0);
        int[] phase = {0};
        int[] ticks = {0};
        ServerSubLevel[] back = {null};
        helper.onEachTick(() -> {
            if (phase[0] == 3) {
                return;
            }
            ticks[0]++;
            if (phase[0] == 0 && ship.subLevel().isRemoved()) {
                log("powered ship put away by Sable after " + ticks[0] + " ticks; loading it again");
                level.setChunkForced(chunkX, 0, true);
                phase[0] = 1;
                ticks[0] = 0;
            } else if (phase[0] == 1 && SubLevelContainer.getContainer(level).getSubLevel(id) instanceof ServerSubLevel loaded
                    && !loaded.isRemoved()) {
                log("powered ship loaded again after " + ticks[0] + " ticks");
                back[0] = loaded;
                phase[0] = 2;
                ticks[0] = 0;
            } else if (phase[0] == 2 && ticks[0] >= 20) {
                Ship reloaded = new Ship("powered (reloaded)", back[0],
                        find(level, back[0], AetherBlocks.ENCHANTED_GRAVITITE.get()), null);
                expect(failures, reloaded, true, 1);
                finish(helper, level, failures);
                phase[0] = 3;
            } else if (ticks[0] > 400) {
                failures.add(phase[0] == 0 ? "Sable never put the powered ship away" : "the powered ship never loaded again");
                finish(helper, level, failures);
                phase[0] = 3;
            }
        });
    }

    private static void finish(GameTestHelper helper, ServerLevel level, List<String> failures) {
        level.setChunkForced(chunkX(0), 0, false);
        if (failures.isEmpty()) {
            helper.succeed();
        } else {
            helper.fail(String.join("; ", failures));
        }
    }

    /** Ships stand well away from the spawn chunks, which never unload. */
    private static int chunkX(int slot) {
        return (256 + slot * 24) >> 4;
    }

    /**
     * Builds a ship at slot {@code slot}: a stone base, the gravitite on it, a stone cap over it if {@code capped},
     * and a lever on its east face, on if {@code on}; then assembles it.
     */
    private static Ship build(ServerLevel level, int slot, Block gravitite, boolean capped, boolean on, String name) {
        BlockPos base = new BlockPos(256 + slot * 24, HEIGHT, 0);
        // With no player about, Sable puts away ships over chunks that aren't loaded; these stay loaded for the check
        // (and the ships fall straight down, so they stay over them)
        level.setChunkForced(base.getX() >> 4, base.getZ() >> 4, true);
        BlockPos block = base.above();
        BlockPos lever = block.east();
        List<BlockPos> blocks = new ArrayList<>(List.of(base, block, lever));
        level.setBlock(base, STONE, Block.UPDATE_ALL);
        level.setBlock(block, gravitite.defaultBlockState(), Block.UPDATE_ALL);
        if (capped) {
            level.setBlock(block.above(), STONE, Block.UPDATE_ALL);
            blocks.add(block.above());
        }
        level.setBlock(lever, Blocks.LEVER.defaultBlockState().setValue(LeverBlock.FACE, AttachFace.WALL)
                .setValue(LeverBlock.FACING, Direction.EAST).setValue(LeverBlock.POWERED, on), Block.UPDATE_ALL);
        ServerSubLevel subLevel = SubLevelAssemblyHelper.assembleBlocks(level, block, blocks,
                new BoundingBox3i(base, block.above().east()));
        BlockPos at = find(level, subLevel, gravitite);
        BlockPos leverAt = find(level, subLevel, Blocks.LEVER);
        log(name + ": assembled, gravitite at " + at + ", lever at " + leverAt);
        return new Ship(name, subLevel, at, leverAt);
    }

    /** Where a block went in the sub-level's plot: Sable places the assembly around the plot's centre. */
    private static BlockPos find(ServerLevel level, ServerSubLevel subLevel, Block block) {
        BlockPos centre = subLevel.getPlot().getCenterBlock();
        for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-6, -6, -6), centre.offset(6, 6, 6))) {
            if (level.getBlockState(pos).is(block)) {
                return pos.immutable();
            }
        }
        return null;
    }

    private static void pull(ServerLevel level, Ship ship) {
        BlockState lever = level.getBlockState(ship.lever());
        if (lever.getBlock() instanceof LeverBlock leverBlock) {
            leverBlock.pull(lever, level, ship.lever(), null);
            log(ship.name() + ": lever now " + level.getBlockState(ship.lever()).getValue(LeverBlock.POWERED));
        } else {
            log(ship.name() + ": no lever to pull at " + ship.lever() + ", found " + lever + "; " + describe(level, ship));
        }
    }

    /** Checks our gate and Sable's own clusters agree that the ship's gravitite is, or isn't, lifting. */
    private static void expect(List<String> failures, Ship ship, boolean lifting, int floating) {
        boolean gate = ship.gravitite() != null && ship.gate().altered_aether$lifting().contains(ship.gravitite().asLong());
        int registered = floatingBlocks(ship.subLevel().getFloatingBlockController());
        log(ship.name() + ": gate says " + (gate ? "lifting" : "not lifting") + ", Sable has " + registered
                + " floating block(s)");
        if (gate != lifting || registered != floating) {
            failures.add(ship.name() + " should " + (lifting ? "" : "not ") + "lift (gate " + gate + ", Sable "
                    + registered + ")");
        }
    }

    /**
     * Gravitite with open air above it stays on its ship (the Aether would have it fall upward out of it), and Sable
     * lifts it: the ore always, so one floating block.
     */
    private static void stays(List<String> failures, ServerLevel level, Ship ship, Block block) {
        if (ship.gravitite() == null || !level.getBlockState(ship.gravitite()).is(block)) {
            failures.add(ship.name() + ": the gravitite should stay on its ship");
        } else if (block == AetherBlocks.GRAVITITE_ORE.get() && floatingBlocks(ship.subLevel().getFloatingBlockController()) != 1) {
            failures.add(ship.name() + ": Sable should lift the ore");
        }
    }

    /** How many blocks Sable has in the controller's floating clusters, read from its private state. */
    private static int floatingBlocks(FloatingBlockController controller) {
        try {
            Field container = FloatingBlockController.class.getDeclaredField("sublevelContainer");
            container.setAccessible(true);
            Field count = FloatingBlockData.class.getDeclaredField("blockCount");
            count.setAccessible(true);
            int total = 0;
            for (FloatingBlockCluster cluster : ((FloatingClusterContainer) container.get(controller)).clusters) {
                total += count.getInt(cluster.getBlockData());
            }
            return total;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Sable's floating block internals changed", e);
        }
    }

    private static String describe(ServerLevel level, Ship ship) {
        BlockPos pos = ship.gravitite();
        BlockState state = pos == null ? null : level.getBlockState(pos);
        return "block now " + state + " (ship removed " + ship.subLevel().isRemoved() + ", gate knows "
                + ship.gate().altered_aether$known().size()
                + "), Sable floating blocks " + floatingBlocks(ship.subLevel().getFloatingBlockController())
                + ", ship y " + String.format("%.3f", ship.y());
    }

    private static void log(String message) {
        AlteredAether.LOGGER.info("Gravitite Sable check: {}", message);
    }
}
