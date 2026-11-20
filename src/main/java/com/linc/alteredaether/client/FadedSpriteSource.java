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
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.atlas.SpriteSource;
import net.minecraft.client.renderer.texture.atlas.SpriteSourceType;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceMetadata;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterSpriteSourceTypesEvent;

import java.util.List;
import java.util.Optional;

/**
 * An atlas source that makes faded copies of another mod's texture as the game loads, one per alpha, named
 * {@code prefix + index}: the phased aercloud's steps (see block/PhasedAercloudBlock). The Aether's and Deep Aether's art
 * isn't ours to ship altered, so it's altered on the player's machine instead. An animated texture stays animated. Where
 * the texture's mod isn't installed, each copy is a clear placeholder, so no model goes looking for a missing texture.
 */
@EventBusSubscriber(modid = AlteredAether.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public record FadedSpriteSource(ResourceLocation source, ResourceLocation prefix, List<Float> alphas)
        implements SpriteSource {
    public static final MapCodec<FadedSpriteSource> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("source").forGetter(FadedSpriteSource::source),
            ResourceLocation.CODEC.fieldOf("prefix").forGetter(FadedSpriteSource::prefix),
            Codec.FLOAT.listOf().fieldOf("alphas").forGetter(FadedSpriteSource::alphas)
    ).apply(instance, FadedSpriteSource::new));

    private static SpriteSourceType type;

    @SubscribeEvent
    public static void register(RegisterSpriteSourceTypesEvent event) {
        type = event.register(AlteredAether.id("faded"), CODEC);
    }

    @Override
    public void run(ResourceManager resources, Output output) {
        Optional<Resource> resource = resources.getResource(TEXTURE_ID_CONVERTER.idToFile(source));
        for (int i = 0; i < alphas.size(); i++) {
            ResourceLocation id = prefix.withSuffix(String.valueOf(i));
            float alpha = alphas.get(i);
            output.add(id, loader -> {
                SpriteContents contents = resource.map(found -> loader.loadSprite(id, found)).orElse(null);
                if (contents == null) {
                    return new SpriteContents(id, new FrameSize(16, 16), new NativeImage(16, 16, true),
                            ResourceMetadata.EMPTY);
                }
                fade(contents.getOriginalImage(), alpha);
                return contents;
            });
        }
    }

    /** Scales every pixel's alpha, every frame of an animation with it. */
    private static void fade(NativeImage image, float alpha) {
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int abgr = image.getPixelRGBA(x, y);
                int a = Math.round((abgr >>> 24) * alpha);
                image.setPixelRGBA(x, y, a << 24 | abgr & 0xFFFFFF);
            }
        }
    }

    @Override
    public SpriteSourceType type() {
        return type;
    }
}
