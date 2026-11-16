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
package com.linc.alteredaether.debug;

import com.aetherteam.aether.block.AetherBlocks;
import com.linc.alteredaether.compat.boulder.FallingBoulders;
import com.linc.alteredaether.worldgen.boulder.BoulderOres;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;

/** {@code /altered_aether boulder [stone] [ore]}: drops a boulder thirty blocks ahead of the caller, forty up. Needs Sable. */
final class BoulderDebug {
    static int drop(CommandSourceStack source, BlockState stone, BlockState ore) {
        if (!ModList.get().isLoaded(FallingBoulders.MOD)) {
            source.sendFailure(Component.literal("Falling boulders need Sable."));
            return 0;
        }
        ServerLevel level = source.getLevel();
        Vec3 look = source.getRotation().y == 0 && source.getRotation().x == 0 ? new Vec3(0, 0, 1)
                : Vec3.directionFromRotation(source.getRotation());
        Vec3 flat = new Vec3(look.x, 0, look.z).normalize();
        Vec3 target = source.getPosition().add(flat.scale(30));
        BlockPos origin = BlockPos.containing(target.x, source.getPosition().y + 40, target.z);
        BlockState chosenStone = stone != null ? stone : BoulderOres.pickStone(level.getRandom()).defaultBlockState();
        BlockState chosenOre = ore != null ? ore : chooseOre(level, origin);
        var sub = FallingBoulders.spawn(level, origin, chosenStone, chosenOre, 0.25f, level.getRandom());
        if (sub == null) {
            source.sendFailure(Component.literal("No room for a boulder at " + origin.toShortString()));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Dropped a boulder of " + chosenStone.getBlock().getName().getString()
                + " with " + chosenOre.getBlock().getName().getString() + " at " + origin.toShortString()), true);
        return 1;
    }

    private static BlockState chooseOre(ServerLevel level, BlockPos at) {
        var table = BoulderOres.table(level.getChunkSource().getGenerator().getBiomeSource());
        var biome = level.getBiome(at).unwrapKey().orElseThrow();
        return BoulderOres.pickOre(table, biome, level.getRandom()).defaultBlockState();
    }

    /** {@code /altered_aether boulderores}: the ores the biome the caller stands in has, and the ones a boulder there may carry. */
    static int ores(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        BlockPos at = BlockPos.containing(source.getPosition());
        var table = BoulderOres.table(level.getChunkSource().getGenerator().getBiomeSource());
        var biome = level.getBiome(at).unwrapKey().orElseThrow();
        var own = table.owned().getOrDefault(biome, java.util.Set.of());
        java.util.List<String> foreign = new java.util.ArrayList<>();
        for (var block : table.all()) {
            if (!own.contains(block)) {
                foreign.add(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block).toString());
            }
        }
        java.util.List<String> ownNames = own.stream()
                .map(block -> net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block).toString()).sorted().toList();
        source.sendSuccess(() -> Component.literal(biome.location() + " has " + ownNames), false);
        source.sendSuccess(() -> Component.literal("Other biomes' ores a boulder here may carry: " + foreign
                + " (and the Aether's own three)"), false);
        return 1;
    }

    private BoulderDebug() {
    }
}
