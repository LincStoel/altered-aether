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
package com.linc.alteredaether.client;

import com.linc.alteredaether.AlteredAether;
import com.linc.alteredaether.block.GleamVines;
import com.linc.alteredaether.config.AAClientConfig;
import com.linc.alteredaether.registry.AARegistries;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.TextureAtlasStitchedEvent;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tints the leaves of gleam vines (tint index 0 of their models) to the leaves they hang from: the colour of the leaf's
 * texture, times the biome's tint if the leaf has one, run through {@link GleamVineTint}. Vines hanging from anything
 * but leaves, and items, get the default Aether blue-green.
 */
@EventBusSubscriber(modid = AlteredAether.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class GleamVineColors {
    /** How far up a column of vines to look for what holds it; longer than any vine the game grows. */
    private static final int MAX_VINE_LENGTH = 64;

    /** A leaf's average texture colour, and whether its model is tinted (by the biome). */
    private record Leaf(int average, boolean tinted) {
    }

    // meshing runs on several threads
    private static final Map<BlockState, Leaf> LEAVES = new ConcurrentHashMap<>();

    @SubscribeEvent
    public static void registerColors(RegisterColorHandlersEvent.Block event) {
        event.register(GleamVineColors::color, AARegistries.GLEAM_VINES.get(), AARegistries.GLEAM_VINES_PLANT.get());
    }

    /** Leaf textures may be replaced by a resource pack, so what was averaged from them is dropped with the atlas. */
    @SubscribeEvent
    public static void atlasStitched(TextureAtlasStitchedEvent event) {
        if (event.getAtlas().location().equals(InventoryMenu.BLOCK_ATLAS)) {
            LEAVES.clear();
        }
    }

    /** Rebuilds the chunks when the setting is flipped, so the vines in view change at once. */
    @SubscribeEvent
    public static void configChanged(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() == AAClientConfig.SPEC) {
            Minecraft minecraft = Minecraft.getInstance();
            minecraft.execute(() -> {
                if (minecraft.levelRenderer != null && minecraft.level != null) {
                    minecraft.levelRenderer.allChanged();
                }
            });
        }
    }

    private static int color(BlockState state, @Nullable BlockAndTintGetter level, @Nullable BlockPos pos, int tintIndex) {
        if (level == null || pos == null || tintIndex != 0 || !AAClientConfig.GLEAM_VINE_LEAF_TINT.get()) {
            return GleamVineTint.DEFAULT;
        }
        BlockPos.MutableBlockPos holder = pos.mutable();
        BlockAndTintGetter seen = level;
        BlockState support = climb(level, holder);
        if (support.isAir() && Minecraft.getInstance().level != null) {
            // The mesher may see only part of a long column (Sodium copies a few sections around the chunk), and
            // reads outside it give air; air over a vine is never real, since vines need holding.
            seen = Minecraft.getInstance().level;
            holder.set(pos);
            support = climb(seen, holder);
        }
        if (!support.is(BlockTags.LEAVES)) {
            return GleamVineTint.DEFAULT;
        }
        Leaf leaf = LEAVES.computeIfAbsent(support, GleamVineColors::describe);
        int average = leaf.average();
        if (leaf.tinted()) {
            int biome = Minecraft.getInstance().getBlockColors().getColor(support, seen, holder, 0);
            if (biome != -1) {
                average = GleamVineTint.multiply(average, biome);
            }
        }
        return GleamVineTint.fromLeaf(average);
    }

    /** Moves {@code pos} up the column of vines it is in, to what they hang from, and returns that. */
    private static BlockState climb(BlockAndTintGetter level, BlockPos.MutableBlockPos pos) {
        BlockState state = level.getBlockState(pos);
        for (int i = 0; i < MAX_VINE_LENGTH && GleamVines.is(state); i++) {
            pos.move(Direction.UP);
            state = level.getBlockState(pos);
        }
        return state;
    }

    private static Leaf describe(BlockState state) {
        BakedModel model = Minecraft.getInstance().getBlockRenderer().getBlockModel(state);
        TextureAtlasSprite sprite = model.getParticleIcon(ModelData.EMPTY);
        var contents = sprite.contents();
        // an animated texture's frames are stacked in the image; the first is on top
        int width = contents.width(), height = contents.height();
        int[] pixels = new int[width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                pixels[y * width + x] = contents.getOriginalImage().getPixelRGBA(x, y);
            }
        }
        boolean tinted = false;
        RandomSource random = RandomSource.create(42);
        for (Direction side : new Direction[]{null, Direction.UP, Direction.DOWN, Direction.NORTH, Direction.SOUTH,
                Direction.WEST, Direction.EAST}) {
            tinted |= model.getQuads(state, side, random, ModelData.EMPTY, null).stream().anyMatch(quad -> quad.isTinted());
        }
        return new Leaf(GleamVineTint.average(pixels), tinted);
    }

    private GleamVineColors() {
    }
}
