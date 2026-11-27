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

import com.linc.alteredaether.AATags;
import com.linc.alteredaether.essence.EssencePlinthBlock;
import com.linc.alteredaether.essence.EssencePlinthBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Draws what lies on an essence plinth, as a Create depot shows its items: the item being worked flat in the middle
 * (or the next one waiting), and what is done set down beside it. Items in Create's {@code upright_on_belt} tag
 * stand up and turn to face the camera instead, as they do on a depot.
 */
public class EssencePlinthRenderer implements BlockEntityRenderer<EssencePlinthBlockEntity> {
    private static final float SCALE = 0.45F;
    /** How much higher an upright item's middle sits than a lying one's: the depot's 3/16, at our smaller scale. */
    private static final double UPRIGHT_LIFT = 3 / 16.0 * SCALE / 0.5;

    public EssencePlinthRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(EssencePlinthBlockEntity plinth, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int light, int overlay) {
        ItemStack middle = plinth.work().isEmpty() ? plinth.input() : plinth.work();
        int seed = (int) plinth.getBlockPos().asLong();
        if (!middle.isEmpty()) {
            setDown(plinth, middle, 0.5, 0.5, 20, pose, buffers, light, seed);
        }
        if (!plinth.output().isEmpty()) {
            setDown(plinth, plinth.output(), 0.66, 0.36, -35, pose, buffers, light, seed + 1);
        }
    }

    private static void setDown(EssencePlinthBlockEntity plinth, ItemStack stack, double x, double z, float turn,
                                PoseStack pose, MultiBufferSource buffers, int light, int seed) {
        pose.pushPose();
        if (stack.is(AATags.UPRIGHT_ON_BELT)) {
            // Create's depot: stood a little higher than a lying item, yawed to the camera and nudged back
            pose.translate(x, EssencePlinthBlock.TOP + 1 / 32.0 + UPRIGHT_LIFT, z);
            Vec3 at = Vec3.atLowerCornerOf(plinth.getBlockPos()).add(x, EssencePlinthBlock.TOP, z);
            Vec3 diff = at.subtract(Minecraft.getInstance().gameRenderer.getMainCamera().getPosition());
            pose.mulPose(Axis.YP.rotation((float) (Mth.atan2(diff.x, diff.z) + Math.PI)));
            pose.translate(0, 0, -SCALE / 8);
        } else {
            pose.translate(x, EssencePlinthBlock.TOP + 1 / 32.0, z);
            pose.mulPose(Axis.YP.rotationDegrees(turn));
            pose.mulPose(Axis.XP.rotationDegrees(90));
        }
        pose.scale(SCALE, SCALE, SCALE);
        Minecraft.getInstance().getItemRenderer().renderStatic(stack, ItemDisplayContext.FIXED, light,
                OverlayTexture.NO_OVERLAY, pose, buffers, plinth.getLevel(), seed);
        pose.popPose();
    }
}
