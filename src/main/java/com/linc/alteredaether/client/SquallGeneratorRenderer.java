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
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * The squall caught in a Squall Generator: the zephyr's model, half size, in the squall's texture, bobbing in the bowl
 * with its tail curled up out of it, its open top whichever way the block faces. Like the blaze in Create's blaze
 * burner, it turns to look at the player, about the bowl's own up. Lit, it glows at full brightness and bobs faster.
 */
public class SquallGeneratorRenderer implements BlockEntityRenderer<BlockEntity> {
    private static final ResourceLocation TEXTURE = AlteredAether.id("textures/entity/mobs/squall/squall.png");
    private static final float SCALE = 0.5F;
    /** The bowl's floor, below the block's centre (with its open top up). */
    private static final double FLOOR = -6 / 16.0;
    /** How far the model's lowest point (its body's underside) sits above its feet, in model pixels. */
    private static final float BODY_LIFT = 11;
    /** The share of the way to the player the squall turns each tick, as the blaze burner's head chases. */
    private static final float TURN_SPEED = 0.25F;
    /** After this many ticks unseen the squall just faces the player, rather than swinging round. */
    private static final long SNAP_TICKS = 20;
    private final ZephyrModel model;
    /** Where each generator's squall is looking; render-thread only, gone with the block entity. */
    private final Map<BlockEntity, Look> looks = new WeakHashMap<>();

    public SquallGeneratorRenderer(BlockEntityRendererProvider.Context context) {
        model = new ZephyrModel(context.bakeLayer(AetherModelLayers.ZEPHYR));
        // the tail tucked into the cloud's back and curled up, so a squall this size stays in its block
        model.tailBase.z = 6;
        model.tailBase.xRot = 1.2F;
        model.tailMiddle.xRot = 0.5F;
        model.tailEnd.xRot = 0.3F;
    }

    @Override
    public void render(BlockEntity entity, float partialTick, PoseStack pose, MultiBufferSource buffers, int light,
                       int overlay) {
        BlockState state = entity.getBlockState();
        Level level = entity.getLevel();
        if (!state.hasProperty(SquallGeneratorBlock.FACING) || level == null) {
            return;
        }
        float time = level.getGameTime() + partialTick;
        boolean lit = state.getValue(SquallGeneratorBlock.LIT);
        Quaternionf facing = facingRotation(state.getValue(SquallGeneratorBlock.FACING));
        Look look = looks.computeIfAbsent(entity, key -> new Look());
        look.update(level.getGameTime(), target(entity.getBlockPos(), facing, look.yaw));
        pose.pushPose();
        pose.translate(0.5, 0.5, 0.5);
        pose.mulPose(facing);
        // its underside just off the bowl's floor, bobbing faster while it works
        float bob = (1 + Mth.sin(time * (lit ? 0.25F : 0.08F))) * 0.02F;
        pose.translate(0, FLOOR + 0.03 - BODY_LIFT / 16 * SCALE + bob, 0);
        pose.mulPose(Axis.YP.rotation(Mth.lerp(partialTick, look.yawO, look.yaw)));
        // entity models are drawn upside down from their feet, as LivingEntityRenderer sets them up
        pose.scale(-SCALE, -SCALE, SCALE);
        pose.translate(0, -1.501, 0);
        // the zephyr's idle sway, its faces and tail drifting; the model never reads the entity
        model.setupAnim(null, time * 0.25F, lit ? 0.4F : 0.2F, time, 0, 0);
        model.renderToBuffer(pose, buffers.getBuffer(RenderType.entityTranslucent(TEXTURE)),
                lit ? LightTexture.FULL_BRIGHT : light, OverlayTexture.NO_OVERLAY, lit ? 0xFFFFFFFF : 0xFFB0B0C0);
        pose.popPose();
    }

    /**
     * The turn, about the bowl's up, that puts the squall's face toward the player's eyes, or {@code current} with no
     * one to look at (or the player straight along the bowl's up). The face is the model's -z, so a turn of {@code a}
     * points it along (-sin a, -cos a) in the bowl's frame.
     */
    private static float target(BlockPos pos, Quaternionf facing, float current) {
        Player player = Minecraft.getInstance().player;
        if (player == null || player.isInvisible()) {
            return current;
        }
        Vector3f local = new Vector3f((float) (player.getX() - pos.getX() - 0.5),
                (float) (player.getEyeY() - pos.getY() - 0.5), (float) (player.getZ() - pos.getZ() - 0.5));
        facing.conjugate(new Quaternionf()).transform(local);
        if (local.x * local.x + local.z * local.z < 0.01F) {
            return current;
        }
        return (float) Mth.atan2(-local.x, -local.z);
    }

    /** A squall's turn, chased a tick at a time toward the player, as Create chases the blaze burner's head. */
    private static final class Look {
        private float yaw, yawO;
        private long tick;
        private boolean seen;

        void update(long now, float target) {
            if (seen && now == tick) {
                return;
            }
            // the short way round, kept continuous so the lerp between ticks never spins
            float goal = yaw + Mth.wrapDegrees((target - yaw) * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD;
            if (!seen || now < tick || now - tick > SNAP_TICKS) {
                yaw = yawO = goal;
            } else {
                yawO = yaw;
                yaw += (goal - yaw) * TURN_SPEED;
            }
            tick = now;
            seen = true;
        }
    }

    /** The turn that puts the bowl's up toward {@code facing}, as the blockstate's rotations turn its model. */
    private static Quaternionf facingRotation(Direction facing) {
        return switch (facing) {
            case DOWN -> Axis.XP.rotationDegrees(180);
            case NORTH -> Axis.XP.rotationDegrees(-90);
            case EAST -> Axis.YP.rotationDegrees(-90).mul(Axis.XP.rotationDegrees(-90));
            case SOUTH -> Axis.YP.rotationDegrees(-180).mul(Axis.XP.rotationDegrees(-90));
            case WEST -> Axis.YP.rotationDegrees(-270).mul(Axis.XP.rotationDegrees(-90));
            default -> new Quaternionf();
        };
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
