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

import com.aetherteam.aether.entity.passive.Aerwhale;
import com.linc.alteredaether.AlteredAether;
import com.linc.alteredaether.entity.TamedAerwhale;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;

/**
 * A saddle on a tamed aerwhale's back: a blanket with gold trim, a leather seat with pommel and cantle, and a girth
 * strap with buckles that wraps right around the body. With a chest, a pair of saddlebags hangs on its flanks behind the
 * girth, slung from a strap over the back. It is its own geometry, sitting on the surface of the Aether's model, with
 * one shape for its current model and one for the b1.7.3 model its legacy setting draws.
 *
 * <p>The texture comes from {@code tools/textures/aerwhale_saddle.py}; its UV regions match the boxes below.
 */
public final class AerwhaleSaddleLayer extends RenderLayer<Aerwhale, EntityModel<Aerwhale>> {
    public static final ModelLayerLocation SADDLE = new ModelLayerLocation(AlteredAether.id("aerwhale"), "saddle");
    public static final ModelLayerLocation CLASSIC_SADDLE =
            new ModelLayerLocation(AlteredAether.id("aerwhale"), "classic_saddle");
    private static final ResourceLocation TEXTURE = AlteredAether.id("textures/entity/aerwhale_saddle.png");

    private final ModelPart saddle;
    private final ModelPart classicSaddle;
    private final ModelPart bags;
    private final ModelPart classicBags;

    public AerwhaleSaddleLayer(RenderLayerParent<Aerwhale, EntityModel<Aerwhale>> parent, EntityModelSet models) {
        super(parent);
        ModelPart root = models.bakeLayer(SADDLE);
        ModelPart classicRoot = models.bakeLayer(CLASSIC_SADDLE);
        this.saddle = root.getChild("saddle");
        this.classicSaddle = classicRoot.getChild("saddle");
        this.bags = root.getChild("bags");
        this.classicBags = classicRoot.getChild("bags");
    }

    /**
     * Fitted to the Aether's model, whose body is one part ("head") posed at y 24 and tipped slightly forward. The
     * broad front box spans x -12..12, y -19..-1, z -31..-3, over a wider base (x -13..13, y -6..0); the saddle sits
     * on its flat top, where the rider's seat is. Behind it, the three passengers' pads sit on the lower middle box
     * ("middle_top", x 1..23, top at y 2, z 0.5..25.5 in its own pose), two side by side and one behind.
     */
    public static LayerDefinition createSaddle() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition saddle = mesh.getRoot().addOrReplaceChild("saddle", CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-9, -20, -22, 18, 1, 16)        // blanket
                        .texOffs(0, 17).addBox(-5, -22, -19, 10, 2, 10)       // seat
                        .texOffs(40, 17).addBox(-5, -24, -11, 10, 2, 2)       // cantle
                        .texOffs(40, 21).addBox(-1.5F, -25, -19, 3, 3, 2)     // pommel
                        // girth strap: over the top, down the sides, out over the base and under the belly
                        .texOffs(0, 32).addBox(-12.5F, -19.5F, -15, 25, 0.5F, 2)
                        .texOffs(0, 32).addBox(-12.5F, -19.5F, -15, 0.5F, 13, 2)
                        .texOffs(0, 32).addBox(12, -19.5F, -15, 0.5F, 13, 2)
                        .texOffs(0, 32).addBox(-13.5F, -6.5F, -15, 1.5F, 0.5F, 2)
                        .texOffs(0, 32).addBox(12, -6.5F, -15, 1.5F, 0.5F, 2)
                        .texOffs(0, 32).addBox(-13.5F, -6.5F, -15, 0.5F, 7, 2)
                        .texOffs(0, 32).addBox(13, -6.5F, -15, 0.5F, 7, 2)
                        .texOffs(0, 32).addBox(-13.5F, 0, -15, 27, 0.5F, 2)
                        .texOffs(64, 32).addBox(-12.75F, -14, -15.5F, 0.5F, 2, 3) // buckles
                        .texOffs(64, 32).addBox(12.25F, -14, -15.5F, 0.5F, 2, 3),
                PartPose.offsetAndRotation(0, 24, 0, -0.0436F, 0, 0));
        saddle.addOrReplaceChild("passenger_seats", CubeListBuilder.create()
                        .texOffs(0, 17).addBox(1.5F, 0, 3, 10, 2, 10)
                        .texOffs(0, 17).addBox(12.5F, 0, 3, 10, 2, 10)
                        .texOffs(0, 17).addBox(7, 0, 14.5F, 10, 2, 10),
                PartPose.offsetAndRotation(-12, -19, -4, -0.0873F, 0, 0));
        // saddlebags against the sides of the front box, behind the girth
        mesh.getRoot().addOrReplaceChild("bags", CubeListBuilder.create()
                        .texOffs(72, 0).addBox(-15, -16, -11, 3, 8, 7)
                        .texOffs(72, 0).addBox(12, -16, -11, 3, 8, 7)
                        .texOffs(72, 16).addBox(-15.5F, -17, -11.5F, 4, 3, 8)
                        .texOffs(72, 16).addBox(11.5F, -17, -11.5F, 4, 3, 8)
                        .texOffs(64, 32).addBox(-15.75F, -15, -9, 0.5F, 2, 3)
                        .texOffs(64, 32).addBox(15.25F, -15, -9, 0.5F, 2, 3)
                        .texOffs(0, 32).addBox(-12.5F, -19.5F, -8.25F, 25, 0.5F, 1.5F)
                        .texOffs(0, 32).addBox(-12.5F, -19.5F, -8.25F, 0.5F, 3.5F, 1.5F)
                        .texOffs(0, 32).addBox(12, -19.5F, -8.25F, 0.5F, 3.5F, 1.5F),
                PartPose.offsetAndRotation(0, 24, 0, -0.0436F, 0, 0));
        return LayerDefinition.create(mesh, 128, 64);
    }

    /**
     * Fitted to the b1.7.3 model, whose body is a box at the origin spanning x -12..9, y -9..9, z -14..16. The
     * passengers' pads sit two side by side behind the rider's seat and one in front of it.
     */
    public static LayerDefinition createClassicSaddle() {
        MeshDefinition mesh = new MeshDefinition();
        mesh.getRoot().addOrReplaceChild("saddle", CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-10.5F, -10, -8, 18, 1, 16)
                        .texOffs(0, 17).addBox(-6.5F, -12, -5, 10, 2, 10)
                        .texOffs(40, 17).addBox(-6.5F, -14, 3, 10, 2, 2)
                        .texOffs(40, 21).addBox(-3, -15, -5, 3, 3, 2)
                        .texOffs(0, 32).addBox(-12.5F, -9.5F, -1, 22, 0.5F, 2)
                        .texOffs(0, 32).addBox(-12.5F, -9.5F, -1, 0.5F, 19, 2)
                        .texOffs(0, 32).addBox(9, -9.5F, -1, 0.5F, 19, 2)
                        .texOffs(0, 32).addBox(-12.5F, 9, -1, 22, 0.5F, 2)
                        .texOffs(64, 32).addBox(-12.75F, -2, -1.5F, 0.5F, 2, 3)
                        .texOffs(64, 32).addBox(9.25F, -2, -1.5F, 0.5F, 2, 3)
                        .texOffs(0, 17).addBox(-12, -11, 6, 10, 2, 10)
                        .texOffs(0, 17).addBox(-1, -11, 6, 10, 2, 10)
                        .texOffs(0, 17).addBox(-6.5F, -11, -15, 10, 2, 10),
                PartPose.ZERO);
        mesh.getRoot().addOrReplaceChild("bags", CubeListBuilder.create()
                        .texOffs(72, 0).addBox(-15, -4, 3, 3, 8, 7)
                        .texOffs(72, 0).addBox(9, -4, 3, 3, 8, 7)
                        .texOffs(72, 16).addBox(-15.5F, -5, 2.5F, 4, 3, 8)
                        .texOffs(72, 16).addBox(8.5F, -5, 2.5F, 4, 3, 8)
                        .texOffs(64, 32).addBox(-15.75F, -3, 5, 0.5F, 2, 3)
                        .texOffs(64, 32).addBox(11.25F, -3, 5, 0.5F, 2, 3)
                        .texOffs(0, 32).addBox(-12.5F, -9.5F, 5.75F, 22, 0.5F, 1.5F)
                        .texOffs(0, 32).addBox(-12.5F, -9.5F, 5.75F, 0.5F, 4.5F, 1.5F)
                        .texOffs(0, 32).addBox(9, -9.5F, 5.75F, 0.5F, 4.5F, 1.5F),
                PartPose.ZERO);
        return LayerDefinition.create(mesh, 128, 64);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, Aerwhale whale, float limbSwing,
                       float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (!(whale instanceof TamedAerwhale tamed) || whale.isInvisible()) {
            return;
        }
        boolean legacy = TamedAerwhale.legacyModel.getAsBoolean();
        VertexConsumer vertices = buffer.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        int overlay = LivingEntityRenderer.getOverlayCoords(whale, 0);
        if (tamed.isSaddled()) {
            (legacy ? classicSaddle : saddle).render(poseStack, vertices, packedLight, overlay);
        }
        if (tamed.hasChest()) {
            (legacy ? classicBags : bags).render(poseStack, vertices, packedLight, overlay);
        }
    }
}
