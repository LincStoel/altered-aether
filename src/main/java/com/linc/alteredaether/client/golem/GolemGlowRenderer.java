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
package com.linc.alteredaether.client.golem;

import com.linc.alteredaether.AlteredAether;
import com.linc.alteredaether.config.AAClientConfig;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.OptionalDouble;

/**
 * Draws {@link GolemGlow}'s glows: a softly pulsing pane over each block or golem with a brighter edge, in gravitite
 * pink, hidden by walls unless the client config says otherwise. Drawn as Spectral Enchantments draws its bookshelf
 * glow: after particles, into the item-entity target, without depth writes.
 */
final class GolemGlowRenderer {
    private static final float FILL_ALPHA = 0.35F;
    private static final float EDGE_ALPHA = 0.9F;
    /** The fill is drawn in gravitite's deeper tone, the edges in the configured (lighter) one. */
    private static final int FILL_COLOR = 0xBD42B2;
    /** The fill under a strictly pinned chest's yellow edge. */
    private static final int STRICT_FILL_COLOR = 0xD9A520;

    private static final RenderType FILL_THROUGH = fill("golem_glow_fill_through", RenderStateShard.NO_DEPTH_TEST, ThroughWalls.SHARD);
    private static final RenderType FILL = fill("golem_glow_fill", RenderStateShard.LEQUAL_DEPTH_TEST, RenderStateShard.NO_LAYERING);
    private static final RenderType EDGE_THROUGH = lines("golem_glow_edge_through", RenderStateShard.NO_DEPTH_TEST, ThroughWalls.SHARD);
    private static final RenderType EDGE = lines("golem_glow_edge", RenderStateShard.LEQUAL_DEPTH_TEST, RenderStateShard.NO_LAYERING);

    private GolemGlowRenderer() {
    }

    private static RenderType fill(String name, RenderStateShard.DepthTestStateShard depth, RenderStateShard.LayeringStateShard layering) {
        return RenderType.create(AlteredAether.MOD_ID + "_" + name, DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS,
                1536, false, true, RenderType.CompositeState.builder()
                        .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
                        .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                        .setOutputState(RenderStateShard.ITEM_ENTITY_TARGET)
                        .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                        .setDepthTestState(depth)
                        .setLayeringState(layering)
                        .setCullState(RenderStateShard.NO_CULL)
                        .createCompositeState(false));
    }

    /** Like vanilla's lines type, without depth writes and with the given depth test. */
    private static RenderType lines(String name, RenderStateShard.DepthTestStateShard depth, RenderStateShard.LayeringStateShard layering) {
        return RenderType.create(AlteredAether.MOD_ID + "_" + name, DefaultVertexFormat.POSITION_COLOR_NORMAL, VertexFormat.Mode.LINES,
                1536, false, false, RenderType.CompositeState.builder()
                        .setShaderState(RenderStateShard.RENDERTYPE_LINES_SHADER)
                        .setLineState(new RenderStateShard.LineStateShard(OptionalDouble.empty()))
                        .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                        .setOutputState(RenderStateShard.ITEM_ENTITY_TARGET)
                        .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                        .setDepthTestState(depth)
                        .setLayeringState(layering)
                        .setCullState(RenderStateShard.NO_CULL)
                        .createCompositeState(false));
    }

    static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || GolemGlow.GLOWS.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        boolean through = AAClientConfig.GOLEM_GLOW_THROUGH_WALLS.get();
        PoseStack poseStack = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        float now = GolemGlow.ticks + partial;
        // a slow breath, so the glow catches the eye without flashing
        float pulse = 0.7F + 0.3F * Mth.sin(now * 0.2F);
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();

        RenderType fillType = through ? FILL_THROUGH : FILL;
        VertexConsumer fill = buffers.getBuffer(fillType);
        for (GolemGlow.Glow glow : GolemGlow.GLOWS) {
            float alpha = FILL_ALPHA * pulse * GolemGlow.fade(glow, now);
            AABB box = alpha > 0 ? GolemGlow.box(mc.level, glow, partial) : null;
            if (box != null) {
                int color = glow.color() == GolemGlow.STRICT_COLOR ? STRICT_FILL_COLOR : FILL_COLOR;
                fillBox(poseStack.last(), fill, box.move(-cam.x, -cam.y, -cam.z), color, alpha);
            }
        }
        buffers.endBatch(fillType);

        RenderType edgeType = through ? EDGE_THROUGH : EDGE;
        VertexConsumer edge = buffers.getBuffer(edgeType);
        for (GolemGlow.Glow glow : GolemGlow.GLOWS) {
            float alpha = EDGE_ALPHA * GolemGlow.fade(glow, now);
            AABB box = alpha > 0 ? GolemGlow.box(mc.level, glow, partial) : null;
            if (box != null) {
                int color = glow.color();
                edges(poseStack.last(), edge, box.move(-cam.x, -cam.y, -cam.z), FastColor.ARGB32.red(color) / 255F,
                        FastColor.ARGB32.green(color) / 255F, FastColor.ARGB32.blue(color) / 255F, alpha);
            }
        }
        buffers.endBatch(edgeType);
    }

    private static void fillBox(PoseStack.Pose pose, VertexConsumer consumer, AABB box, int color, float alpha) {
        float x0 = (float) box.minX, y0 = (float) box.minY, z0 = (float) box.minZ;
        float x1 = (float) box.maxX, y1 = (float) box.maxY, z1 = (float) box.maxZ;
        int argb = FastColor.ARGB32.color(Mth.floor(alpha * 255F), color);
        quad(pose, consumer, argb, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0);
        quad(pose, consumer, argb, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1);
        quad(pose, consumer, argb, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0);
        quad(pose, consumer, argb, x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0);
        quad(pose, consumer, argb, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
        quad(pose, consumer, argb, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1);
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer consumer, int argb,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz) {
        consumer.addVertex(pose, ax, ay, az).setColor(argb);
        consumer.addVertex(pose, bx, by, bz).setColor(argb);
        consumer.addVertex(pose, cx, cy, cz).setColor(argb);
        consumer.addVertex(pose, dx, dy, dz).setColor(argb);
    }

    /** The box's twelve edges as lines. */
    private static void edges(PoseStack.Pose pose, VertexConsumer consumer, AABB box, float r, float g, float b, float a) {
        float x0 = (float) box.minX, y0 = (float) box.minY, z0 = (float) box.minZ;
        float x1 = (float) box.maxX, y1 = (float) box.maxY, z1 = (float) box.maxZ;
        float[][] lines = {
                {x0, y0, z0, x1, y0, z0}, {x0, y1, z0, x1, y1, z0}, {x0, y0, z1, x1, y0, z1}, {x0, y1, z1, x1, y1, z1},
                {x0, y0, z0, x0, y1, z0}, {x1, y0, z0, x1, y1, z0}, {x0, y0, z1, x0, y1, z1}, {x1, y0, z1, x1, y1, z1},
                {x0, y0, z0, x0, y0, z1}, {x1, y0, z0, x1, y0, z1}, {x0, y1, z0, x0, y1, z1}, {x1, y1, z0, x1, y1, z1}};
        for (float[] line : lines) {
            float dx = line[3] - line[0], dy = line[4] - line[1], dz = line[5] - line[2];
            float length = Mth.sqrt(dx * dx + dy * dy + dz * dz);
            consumer.addVertex(pose, line[0], line[1], line[2]).setColor(r, g, b, a).setNormal(pose, dx / length, dy / length, dz / length);
            consumer.addVertex(pose, line[3], line[4], line[5]).setColor(r, g, b, a).setNormal(pose, dx / length, dy / length, dz / length);
        }
    }
}
