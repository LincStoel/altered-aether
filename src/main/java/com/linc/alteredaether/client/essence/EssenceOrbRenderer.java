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
package com.linc.alteredaether.client.essence;

import com.linc.alteredaether.AlteredAether;
import com.linc.alteredaether.essence.EssenceOrbBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.joml.Quaternionf;

/**
 * Draws an essence source: a small white orb of light bobbing in its block, and about it, for each pair of opposite
 * gemmed capstones, one ring of an armillary sphere, a soft band of the same white light turning slowly, faster as the
 * source's breath swells. Everything is full bright and translucent, drawn with the entity shader (as the squall in a
 * generator is), which shaderpacks keep glowing.
 */
public class EssenceOrbRenderer implements BlockEntityRenderer<EssenceOrbBlockEntity> {
    private static final ResourceLocation ORB = AlteredAether.id("textures/entity/essence_orb.png");
    private static final ResourceLocation RING = AlteredAether.id("textures/entity/essence_ring.png");
    /** The rings, by the pair of capstones that lights them (N-S, NE-SW, E-W, SE-NW): radius and tilt. */
    private static final float[] RADIUS = {0.62F, 0.54F, 0.70F, 0.78F};
    private static final Quaternionf[] TILT = {
            Axis.XP.rotationDegrees(90),                                   // a colure, upright north to south
            new Quaternionf(),                                             // the equator
            Axis.YP.rotationDegrees(90).mul(Axis.XP.rotationDegrees(90)),  // the other colure, upright east to west
            Axis.XP.rotationDegrees(23.4F),                                // the ecliptic
    };
    private static final float BAND = 0.07F;
    private static final int SEGMENTS = 48;
    private static final int RING_ALPHA = 80;

    public EssenceOrbRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(EssenceOrbBlockEntity orb, float partialTick, PoseStack pose, MultiBufferSource buffers, int light,
                       int overlay) {
        Level level = orb.getLevel();
        if (level == null) {
            return;
        }
        long gameTime = level.getGameTime();
        float time = gameTime + partialTick;
        float seed = (orb.getBlockPos().asLong() & 0xFF) * 0.37F;
        double breath = orb.breath(gameTime);
        pose.pushPose();
        pose.translate(0.5, 0.5 + Mth.sin(time * 0.045F + seed) * 0.07F, 0.5);

        int gems = orb.pairedGems();
        if (gems != 0) {
            VertexConsumer ring = buffers.getBuffer(RenderType.entityTranslucentEmissive(RING));
            float spin = time * (0.6F + 0.25F * (float) breath);
            for (int pair = 0; pair < 4; pair++) {
                if ((gems >> pair & 1) == 1 && (gems >> (pair + 4) & 1) == 1) {
                    pose.pushPose();
                    pose.mulPose(TILT[pair]);
                    pose.mulPose(Axis.YP.rotationDegrees(spin * (pair % 2 == 0 ? 1 : -1) + pair * 45));
                    band(pose, ring, RADIUS[pair]);
                    pose.popPose();
                }
            }
        }

        // the orb itself: a glow facing the camera, and a smaller, brighter core in it
        pose.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
        VertexConsumer glow = buffers.getBuffer(RenderType.entityTranslucentEmissive(ORB));
        float pulse = 1 + 0.06F * Mth.sin(time * 0.1F + seed);
        quad(pose, glow, 0.42F * pulse, 170);
        quad(pose, glow, 0.2F * pulse, 255);
        pose.popPose();
    }

    /** A flat ring band of radius {@code radius} about the y axis, its width standing along y. */
    private static void band(PoseStack pose, VertexConsumer buffer, float radius) {
        PoseStack.Pose last = pose.last();
        float half = BAND / 2;
        for (int i = 0; i < SEGMENTS; i++) {
            float a0 = Mth.TWO_PI * i / SEGMENTS, a1 = Mth.TWO_PI * (i + 1) / SEGMENTS;
            float x0 = Mth.cos(a0) * radius, z0 = Mth.sin(a0) * radius;
            float x1 = Mth.cos(a1) * radius, z1 = Mth.sin(a1) * radius;
            // the band's texture is the same all along it, so each segment takes all of it
            float u0 = 0, u1 = 1;
            float nx = Mth.cos((a0 + a1) / 2), nz = Mth.sin((a0 + a1) / 2);
            vertex(buffer, last, x0, -half, z0, u0, 0, RING_ALPHA, nx, 0, nz);
            vertex(buffer, last, x0, half, z0, u0, 1, RING_ALPHA, nx, 0, nz);
            vertex(buffer, last, x1, half, z1, u1, 1, RING_ALPHA, nx, 0, nz);
            vertex(buffer, last, x1, -half, z1, u1, 0, RING_ALPHA, nx, 0, nz);
        }
    }

    /** A square of half-size {@code half} in the pose's xy plane. */
    private static void quad(PoseStack pose, VertexConsumer buffer, float half, int alpha) {
        PoseStack.Pose last = pose.last();
        vertex(buffer, last, -half, -half, 0, 0, 1, alpha, 0, 0, 1);
        vertex(buffer, last, half, -half, 0, 1, 1, alpha, 0, 0, 1);
        vertex(buffer, last, half, half, 0, 1, 0, alpha, 0, 0, 1);
        vertex(buffer, last, -half, half, 0, 0, 0, alpha, 0, 0, 1);
    }

    private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, float x, float y, float z, float u, float v,
                               int alpha, float nx, float ny, float nz) {
        buffer.addVertex(pose, x, y, z).setColor(255, 255, 255, alpha).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(pose, nx, ny, nz);
    }

    @Override
    public AABB getRenderBoundingBox(EssenceOrbBlockEntity orb) {
        return new AABB(orb.getBlockPos()).inflate(0.5);
    }

    @Override
    public int getViewDistance() {
        return 96;
    }
}
