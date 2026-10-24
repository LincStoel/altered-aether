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
package com.linc.alteredaether.worldgen.tall;

import com.linc.alteredaether.AlteredAether;
import com.linc.alteredaether.config.AACommonConfig;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * Height remapping for the tall Aether. Every mod's Aether content (ores, dungeons, Deep Aether's structures, village
 * mods) states its heights for the stock Aether, 0 to 255. In the tall Aether those heights are stretched onto
 * -64..319 with {@code y' = -64 + 1.5 * y}, the same map the terrain uses, so content sits where its authors meant
 * it relative to the islands. Heights measured from the top ({@code below_top}) already land in the same place in
 * both and are left alone.
 *
 * <p>The mixins that apply this only read from here. A height is left unmapped while a structure or placed feature
 * on the exclusion list generates, and a jigsaw structure's start height is unmapped again when it is projected onto
 * a heightmap (then it is an offset from the ground, not a height). Heights that mods read as plain numbers rather
 * than anchors are remapped separately: the Aether's in the tall pack's copies of its files, Deep Aether's structure
 * height limits by a mixin.
 */
@EventBusSubscriber(modid = AlteredAether.MOD_ID)
public final class TallRemap {
    private static final ThreadLocal<int[]> SUPPRESSED = ThreadLocal.withInitial(() -> new int[1]);
    private static volatile Set<String> excludedStructures = Set.of();
    private static volatile Set<PlacedFeature> excludedFeatures = Set.of();

    /** Whether a generation context belongs to the tall Aether. Implemented on WorldGenerationContext by a mixin. */
    public interface Context {
        boolean altered_aether$isTallAether();
    }

    private static final ResourceKey<NoiseGeneratorSettings> SKYLANDS = ResourceKey.create(Registries.NOISE_SETTINGS,
            ResourceLocation.fromNamespaceAndPath("aether", "skylands"));

    /** Whether a generator and its height range are the tall Aether: the tall pack moves the Aether's floor to -64. */
    public static boolean isTallAether(ChunkGenerator generator, int minY) {
        return minY == -64 && generator instanceof NoiseBasedChunkGenerator noise
                && noise.generatorSettings().is(SKYLANDS);
    }

    public static boolean active(Object context) {
        return context instanceof Context tall && tall.altered_aether$isTallAether() && SUPPRESSED.get()[0] == 0;
    }

    /** Whether heights are being remapped for a structure generating in this context right now. */
    public static boolean active(Structure.GenerationContext context) {
        return SUPPRESSED.get()[0] == 0 && isTallAether(context.chunkGenerator(),
                Math.max(context.heightAccessor().getMinBuildHeight(), context.chunkGenerator().getMinY()));
    }

    /** The inverse of {@link #remap}. */
    public static int unmap(int y) {
        return (int) Math.round((y + 64) / 1.5);
    }

    /** A stock-Aether height, as a tall-Aether height. */
    public static int remap(int y) {
        return -64 + Math.floorDiv(3 * y, 2);
    }

    /** A height above the bottom of the world, stretched the same way. */
    public static int remapOffset(int offset) {
        return Math.floorDiv(3 * offset, 2);
    }

    public static void suppress() {
        SUPPRESSED.get()[0]++;
    }

    public static void unsuppress() {
        int[] depth = SUPPRESSED.get();
        depth[0] = Math.max(0, depth[0] - 1);
    }

    public static boolean excluded(ResourceKey<Structure> structure) {
        return excludedStructures.contains(structure.location().toString());
    }

    public static boolean excluded(PlacedFeature feature) {
        return excludedFeatures.contains(feature);
    }

    /** Resolves the configured exclusions against this server's registries. */
    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        Registry<PlacedFeature> features = event.getServer().registryAccess().registryOrThrow(Registries.PLACED_FEATURE);
        Set<PlacedFeature> placed = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<String> structures = new java.util.HashSet<>();
        for (String id : AACommonConfig.TALL_MODE_EXCLUSIONS.get()) {
            ResourceLocation location = ResourceLocation.tryParse(id);
            if (location == null) {
                AlteredAether.LOGGER.warn("Ignoring tall-mode exclusion '{}': not a valid id", id);
                continue;
            }
            structures.add(location.toString());
            features.getOptional(location).ifPresent(placed::add);
        }
        excludedStructures = Set.copyOf(structures);
        excludedFeatures = placed;
    }

    private TallRemap() {
    }
}
