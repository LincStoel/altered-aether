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

import com.linc.alteredaether.worldgen.tall.TallRemap;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.pools.JigsawPlacement;
import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/**
 * Tall-Aether Y remap, part 3: where heights must not be remapped. The exclusions hold the remap off only for the
 * call they wrap; TallRemap counts the depth, so nesting is safe.
 */
public final class TallRemapExclusionMixins {
    /** Structures on the exclusion list (Deep Aether's brass dungeon by default) generate with stock heights. */
    @Mixin(ChunkGenerator.class)
    public abstract static class Structures {
        @Inject(method = "tryGenerateStructure", at = @At("HEAD"))
        private void altered_aether$excludeStart(StructureSet.StructureSelectionEntry entry,
                                                 net.minecraft.world.level.StructureManager structures,
                                                 net.minecraft.core.RegistryAccess registries,
                                                 net.minecraft.world.level.levelgen.RandomState random,
                                                 net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager templates,
                                                 long seed, net.minecraft.world.level.chunk.ChunkAccess chunk,
                                                 net.minecraft.world.level.ChunkPos pos, net.minecraft.core.SectionPos section,
                                                 CallbackInfoReturnable<Boolean> cir) {
            if (entry.structure().unwrapKey().map(TallRemap::excluded).orElse(false)) {
                TallRemap.suppress();
            }
        }

        @Inject(method = "tryGenerateStructure", at = @At("RETURN"))
        private void altered_aether$excludeEnd(StructureSet.StructureSelectionEntry entry,
                                               net.minecraft.world.level.StructureManager structures,
                                               net.minecraft.core.RegistryAccess registries,
                                               net.minecraft.world.level.levelgen.RandomState random,
                                               net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager templates,
                                               long seed, net.minecraft.world.level.chunk.ChunkAccess chunk,
                                               net.minecraft.world.level.ChunkPos pos, net.minecraft.core.SectionPos section,
                                               CallbackInfoReturnable<Boolean> cir) {
            if (entry.structure().unwrapKey().map(TallRemap::excluded).orElse(false)) {
                TallRemap.unsuppress();
            }
        }
    }

    /** Placed features on the exclusion list place with stock heights. */
    @Mixin(PlacedFeature.class)
    public abstract static class Features {
        @Inject(method = "placeWithBiomeCheck", at = @At("HEAD"))
        private void altered_aether$excludeStart(WorldGenLevel level, ChunkGenerator generator, RandomSource random,
                                                 BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
            if (TallRemap.excluded((PlacedFeature) (Object) this)) {
                TallRemap.suppress();
            }
        }

        @Inject(method = "placeWithBiomeCheck", at = @At("RETURN"))
        private void altered_aether$excludeEnd(WorldGenLevel level, ChunkGenerator generator, RandomSource random,
                                               BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
            if (TallRemap.excluded((PlacedFeature) (Object) this)) {
                TallRemap.unsuppress();
            }
        }
    }

    /**
     * A jigsaw start projected onto a heightmap is an offset from the ground, not a height, so the remap its anchor
     * went through is undone. Every jigsaw structure type places through here, including mods' own copies of
     * vanilla's (Deep Aether's), so this one hook covers them all.
     */
    @Mixin(JigsawPlacement.class)
    public abstract static class Jigsaw {
        @ModifyVariable(method = "addPieces(Lnet/minecraft/world/level/levelgen/structure/Structure$GenerationContext;Lnet/minecraft/core/Holder;Ljava/util/Optional;ILnet/minecraft/core/BlockPos;ZLjava/util/Optional;ILnet/minecraft/world/level/levelgen/structure/pools/alias/PoolAliasLookup;Lnet/minecraft/world/level/levelgen/structure/pools/DimensionPadding;Lnet/minecraft/world/level/levelgen/structure/templatesystem/LiquidSettings;)Ljava/util/Optional;",
                at = @At("HEAD"), argsOnly = true)
        private static BlockPos altered_aether$unmapProjectedStart(BlockPos start,
                                                                   @Local(argsOnly = true) Structure.GenerationContext context,
                                                                   @Local(argsOnly = true, ordinal = 1) Optional<Heightmap.Types> projection) {
            if (projection.isPresent() && TallRemap.active(context)) {
                return new BlockPos(start.getX(), TallRemap.unmap(start.getY()), start.getZ());
            }
            return start;
        }
    }

    private TallRemapExclusionMixins() {
    }
}
