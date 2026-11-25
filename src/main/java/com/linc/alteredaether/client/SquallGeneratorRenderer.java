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

import com.aetherteam.aether.client.renderer.AetherModelLayers;
import com.aetherteam.aether.client.renderer.entity.model.ZephyrModel;
import com.linc.alteredaether.AlteredAether;
import com.linc.alteredaether.electric.ElectricRegistries;
import com.linc.alteredaether.electric.SquallGeneratorBlock;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * The squall caught in a Squall Generator: the zephyr's model, small, in the squall's texture, turning slowly in the
 * bowl and bobbing, its open top whichever way the block faces. Lit, it glows at full brightness.
 */
public class SquallGeneratorRenderer implements BlockEntityRenderer<BlockEntity> {
    private static final ResourceLocation TEXTURE = AlteredAether.id("textures/entity/mobs/squall/squall.png");
    private static final float SCALE = 0.2F;
    private final ZephyrModel model;

    public SquallGeneratorRenderer(BlockEntityRendererProvider.Context context) {
        model = new ZephyrModel(context.bakeLayer(AetherModelLayers.ZEPHYR));
    }

    @Override
    public void render(BlockEntity entity, float partialTick, PoseStack pose, MultiBufferSource buffers, int light,
                       int overlay) {
        BlockState state = entity.getBlockState();
        if (!state.hasProperty(SquallGeneratorBlock.FACING) || entity.getLevel() == null) {
            return;
        }
        float time = entity.getLevel().getGameTime() + partialTick;
        boolean lit = state.getValue(SquallGeneratorBlock.LIT);
        pose.pushPose();
        pose.translate(0.5, 0.5, 0.5);
        turnTo(pose, state.getValue(SquallGeneratorBlock.FACING));
        // down into the bowl, bobbing faster while it works
        pose.translate(0, -0.12 + Mth.sin(time * (lit ? 0.25F : 0.08F)) * 0.025, 0);
        pose.mulPose(Axis.YP.rotationDegrees(time * (lit ? 3 : 1)));
        // entity models are drawn upside down from their feet, as LivingEntityRenderer sets them up
        pose.scale(-SCALE, -SCALE, SCALE);
        pose.translate(0, -1.0, 0);
        model.renderToBuffer(pose, buffers.getBuffer(RenderType.entityTranslucent(TEXTURE)),
                lit ? LightTexture.FULL_BRIGHT : light, OverlayTexture.NO_OVERLAY, lit ? 0xFFFFFFFF : 0xFFB0B0C0);
        pose.popPose();
    }

    /** Turns the bowl's up toward {@code facing}, as the blockstate's rotations turn its model. */
    private static void turnTo(PoseStack pose, Direction facing) {
        switch (facing) {
            case DOWN -> pose.mulPose(Axis.XP.rotationDegrees(180));
            case NORTH -> pose.mulPose(Axis.XP.rotationDegrees(-90));
            case EAST -> {
                pose.mulPose(Axis.YP.rotationDegrees(-90));
                pose.mulPose(Axis.XP.rotationDegrees(-90));
            }
            case SOUTH -> {
                pose.mulPose(Axis.YP.rotationDegrees(-180));
                pose.mulPose(Axis.XP.rotationDegrees(-90));
            }
            case WEST -> {
                pose.mulPose(Axis.YP.rotationDegrees(-270));
                pose.mulPose(Axis.XP.rotationDegrees(-90));
            }
            default -> {
            }
        }
    }

    @EventBusSubscriber(modid = AlteredAether.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Registration {
        @SubscribeEvent
        public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerBlockEntityRenderer(ElectricRegistries.SQUALL_GENERATOR_BLOCK_ENTITY.get(),
                    SquallGeneratorRenderer::new);
        }

        private Registration() {
        }
    }
}
