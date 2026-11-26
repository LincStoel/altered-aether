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
package com.linc.alteredaether.compat.create;

import com.linc.alteredaether.AlteredAether;
import com.linc.alteredaether.electric.AetherJarBlock;
import com.linc.alteredaether.electric.WheelPonderText;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import com.simibubi.create.infrastructure.ponder.AllCreatePonderTags;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.api.registration.PonderTagRegistrationHelper;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * The Aerogel Wheel's Ponder scene: the frame of jars and rods going up, lightning charging the jars, the speed panel,
 * and the wheel stopping when a jar runs dry. The schematic is tools/ponder/make_wheel_scene.py's.
 */
public final class WheelPonder implements PonderPlugin {
    private static final ResourceLocation WHEEL = AlteredAether.id("aerogel_wheel");

    @Override
    public String getModId() {
        return AlteredAether.MOD_ID;
    }

    @Override
    public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        helper.forComponents(WHEEL).addStoryBoard(WheelPonderText.SCENE, WheelPonder::wheel,
                AllCreatePonderTags.KINETIC_SOURCES);
    }

    @Override
    public void registerTags(PonderTagRegistrationHelper<ResourceLocation> helper) {
        helper.addToTag(AllCreatePonderTags.KINETIC_SOURCES).add(WHEEL);
    }

    /** Layout (see the schematic): the wheel at (3,2,3), jars at (1,1,3) and (5,1,3), rods on them, a millstone over it. */
    private static void wheel(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title(WheelPonderText.SCENE, WheelPonderText.HEADER);
        scene.configureBasePlate(0, 0, 7);
        scene.showBasePlate();
        scene.idle(10);

        BlockPos wheel = util.grid().at(3, 2, 3);
        BlockPos westJar = util.grid().at(1, 1, 3), eastJar = util.grid().at(5, 1, 3);
        BlockPos westRod = westJar.above(), eastRod = eastJar.above();
        Selection wheelSel = util.select().position(wheel);
        Selection shafts = util.select().fromTo(3, 3, 3, 3, 4, 3);
        Selection kinetic = util.select().fromTo(3, 2, 3, 3, 4, 3);

        scene.world().showSection(wheelSel, Direction.DOWN);
        scene.idle(10);
        line(scene, 0, util.vector().topOf(wheel), 70);
        scene.idle(80);

        scene.world().showSection(util.select().position(westJar), Direction.DOWN);
        scene.world().showSection(util.select().position(eastJar), Direction.DOWN);
        scene.idle(10);
        scene.world().showSection(util.select().position(westRod), Direction.DOWN);
        scene.world().showSection(util.select().position(eastRod), Direction.DOWN);
        scene.idle(10);
        line(scene, 1, util.vector().centerOf(westJar), 80);
        scene.idle(90);
        scene.overlay().showLine(PonderPalette.WHITE, util.vector().topOf(westRod).subtract(0, 0.5, 0),
                util.vector().centerOf(wheel), 70);
        scene.overlay().showLine(PonderPalette.WHITE, util.vector().topOf(eastRod).subtract(0, 0.5, 0),
                util.vector().centerOf(wheel), 70);
        line(scene, 2, util.vector().centerOf(wheel), 70);
        scene.idle(80);

        // lightning on the rods: a flash of sparks, and the jars light up
        for (BlockPos rod : new BlockPos[]{westRod, eastRod}) {
            scene.effects().emitParticles(util.vector().topOf(rod),
                    scene.effects().simpleParticleEmitter(ParticleTypes.ELECTRIC_SPARK, new Vec3(0, -0.1, 0)), 6, 4);
        }
        scene.world().modifyBlock(westJar, state -> state.setValue(AetherJarBlock.CHARGE, 4), false);
        scene.world().modifyBlock(eastJar, state -> state.setValue(AetherJarBlock.CHARGE, 4), false);
        line(scene, 3, util.vector().centerOf(eastJar), 80);
        scene.idle(90);

        scene.world().showSection(shafts, Direction.DOWN);
        scene.idle(10);
        scene.world().setKineticSpeed(kinetic, 8);
        scene.idle(10);
        scene.overlay().showControls(util.vector().blockSurface(wheel, Direction.SOUTH).add(0, 0.3, 0), Pointing.DOWN, 50)
                .rightClick();
        line(scene, 4, util.vector().blockSurface(wheel, Direction.SOUTH), 70);
        scene.idle(80);
        scene.world().setKineticSpeed(kinetic, 32);
        line(scene, 5, util.vector().topOf(util.grid().at(3, 4, 3)), 80);
        scene.idle(90);

        scene.world().modifyBlock(westJar, state -> state.setValue(AetherJarBlock.CHARGE, 0), false);
        scene.world().setKineticSpeed(kinetic, 0);
        line(scene, 6, util.vector().centerOf(westJar), 90);
        scene.idle(100);
        scene.markAsFinished();
    }

    /** The {@code index}th line of the scene's text, pointing at {@code at}. */
    private static void line(CreateSceneBuilder scene, int index, Vec3 at, int ticks) {
        scene.overlay().showText(ticks).text(WheelPonderText.LINES.get(index)).pointAt(at).placeNearTarget()
                .attachKeyFrame();
    }
}
