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
package com.linc.alteredaether.mixin;

import com.aetherteam.aether.world.structure.SilverDungeonStructure;
import com.linc.alteredaether.worldgen.AABiomes;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * In the tall Aether the silver dungeon floats 30 to 75 blocks above the highest ground or water anywhere under it,
 * and over open void takes a height from the tall pack's {@code minY}/{@code rangeY} band (100..135). The stock Aether
 * is left as the Aether places it.
 *
 * <p>The dungeon's rotation isn't chosen until its pieces are built, and it reaches about 60 blocks from its origin, so
 * the ground is sampled over a square that covers every rotation. Trees and other features don't exist yet when the
 * height is chosen; the 30-block floor is what keeps it clear of ordinary canopies. Over the giant-tree biomes it
 * rises higher, clear of the giants: 70 to 110 blocks over Nurselog Hollow's snags, 140 to 175 over the Skyroot
 * Cathedral's crowns. It always stays low enough to fit under the build limit.
 */
@Mixin(SilverDungeonStructure.class)
public abstract class SilverDungeonStructureMixin {
    private static final int REACH = 64;
    private static final int STEP = 32;
    private static final int MIN_CLEARANCE = 30;
    private static final int MAX_CLEARANCE = 75;
    private static final int NURSELOG_MIN_CLEARANCE = 70;
    private static final int NURSELOG_MAX_CLEARANCE = 110;
    private static final int CATHEDRAL_MIN_CLEARANCE = 140;
    private static final int CATHEDRAL_MAX_CLEARANCE = 175;
    /** Room under the build limit for the dungeon itself. */
    private static final int HEADROOM = 48;

    @Shadow
    @Final
    private int minY;

    @Shadow
    @Final
    private int rangeY;

    @ModifyArg(method = "findGenerationPoint", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/core/BlockPos;<init>(III)V"), index = 1)
    private int altered_aether$floatAboveGround(int y, @Local(argsOnly = true) Structure.GenerationContext context) {
        int bottom = context.heightAccessor().getMinBuildHeight();
        if (bottom >= 0) {
            return y;
        }
        int x = context.chunkPos().getMiddleBlockX(), z = context.chunkPos().getMiddleBlockZ();
        int ground = bottom;
        boolean nurselog = false, cathedral = false;
        for (int dx = -REACH; dx <= REACH; dx += STEP) {
            for (int dz = -REACH; dz <= REACH; dz += STEP) {
                int height = context.chunkGenerator().getBaseHeight(x + dx, z + dz, Heightmap.Types.WORLD_SURFACE_WG,
                        context.heightAccessor(), context.randomState());
                if (height <= bottom) {
                    continue;
                }
                ground = Math.max(ground, height);
                Holder<Biome> biome = context.biomeSource().getNoiseBiome(QuartPos.fromBlock(x + dx),
                        QuartPos.fromBlock(height), QuartPos.fromBlock(z + dz), context.randomState().sampler());
                cathedral |= biome.is(AABiomes.SKYROOT_CATHEDRAL);
                nurselog |= biome.is(AABiomes.NURSELOG_HOLLOW);
            }
        }
        if (ground <= bottom) {
            return minY + context.random().nextInt(rangeY);
        }
        int min = cathedral ? CATHEDRAL_MIN_CLEARANCE : nurselog ? NURSELOG_MIN_CLEARANCE : MIN_CLEARANCE;
        int max = cathedral ? CATHEDRAL_MAX_CLEARANCE : nurselog ? NURSELOG_MAX_CLEARANCE : MAX_CLEARANCE;
        int floated = ground + min + context.random().nextInt(max - min + 1);
        return Math.min(floated, context.heightAccessor().getMaxBuildHeight() - HEADROOM);
    }
}
