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
package com.linc.alteredaether.debug;

import com.aetherteam.aether.block.AetherBlocks;
import com.aetherteam.aether.item.AetherItems;
import com.linc.alteredaether.AlteredAether;
import com.linc.alteredaether.electric.ElectricRegistries;
import com.linc.alteredaether.essence.CapstoneGem;
import com.linc.alteredaether.essence.EssenceCapstoneBlock;
import com.linc.alteredaether.essence.EssenceCapstoneBlockEntity;
import com.linc.alteredaether.essence.EssenceOrbBlockEntity;
import com.linc.alteredaether.essence.EssencePlinthBlockEntity;
import com.linc.alteredaether.essence.EssenceRegistries;
import com.linc.alteredaether.registry.AARegistries;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * A scripted look at an essence source in a running client, the parts a GameTest can't see: with the system property
 * {@code altered_aether.essence_tour} set (the client run's {@code -PaaEssenceTour=<label>}), it quick-plays the world
 * "tour", builds a Wellspring's circle in open sky over the Aether's spawn and shoots
 * {@code screenshots/essence_<label>/NN_name.png}: the orb alone, with two rings, with all four and the motes, the
 * capstones' gem spots, the plinth charging a jar and the rod jars, the items, Jade on the orb and JEI's infusion page.
 * Then it quits.
 */
public final class EssenceTour {
    private static final String LABEL = System.getProperty("altered_aether.essence_tour", "");
    private static final ResourceKey<Level> THE_AETHER =
            ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath("aether", "the_aether"));
    private static final int STAGE_Y = 280, STAGE_OFFSET = 1200;
    /** The orb, from the stage's origin; the plinth stands right under it on the floor. */
    private static final BlockPos ORB = new BlockPos(0, 4, 0);

    private static volatile BlockPos origin;

    record Step(String name, Consumer<ServerLevel> setup, Vec3 cam, Vec3 look, int delay, boolean gui, Runnable client) {
    }

    private EssenceTour() {
    }

    private static boolean on() {
        return !LABEL.isBlank();
    }

    static void log(String message, Object... args) {
        AlteredAether.LOGGER.info("Essence tour: " + message, args);
    }

    @EventBusSubscriber(modid = AlteredAether.MOD_ID)
    public static final class Server {
        @SubscribeEvent
        public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
            if (!on() || !(event.getEntity() instanceof ServerPlayer player)) {
                return;
            }
            MinecraftServer server = player.getServer();
            ServerLevel aether = server.getLevel(THE_AETHER);
            if (aether == null) {
                AlteredAether.LOGGER.error("Essence tour: no Aether");
                return;
            }
            BlockPos spawn = aether.getSharedSpawnPos();
            BlockPos o = new BlockPos(spawn.getX() - STAGE_OFFSET, STAGE_Y, spawn.getZ());
            aether.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
            aether.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
            aether.setDayTime(6000);
            player.getInventory().clearContent();
            player.setGameMode(GameType.CREATIVE);
            player.getAbilities().flying = true;
            player.onUpdateAbilities();
            player.teleportTo(aether, o.getX() + 0.5, o.getY() + 4, o.getZ() + 12.5, 180, 20);
            stage(aether, o);
            origin = o;
            log("stage at {}", o.toShortString());
        }

        private Server() {
        }
    }

    private static void stage(ServerLevel level, BlockPos o) {
        AABB area = new AABB(o.offset(-12, -2, -12).getCenter(), o.offset(12, 10, 12).getCenter());
        for (Entity entity : level.getEntitiesOfClass(Entity.class, area, e -> !(e instanceof ServerPlayer))) {
            entity.discard();
        }
        for (BlockPos pos : BlockPos.betweenClosed(o.offset(-12, -2, -12), o.offset(12, 10, 12))) {
            if (level.getBlockEntity(pos) instanceof net.minecraft.world.Clearable clearable) {
                clearable.clearContent();
            }
            if (!level.getBlockState(pos).isAir()) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2 | 16);
            }
        }
        var floor = AetherBlocks.HOLYSTONE_BRICKS.get().defaultBlockState();
        for (int x = -9; x <= 9; x++) {
            for (int z = -9; z <= 9; z++) {
                level.setBlockAndUpdate(o.offset(x, -1, z), floor);
            }
        }
        BlockPos orb = o.offset(ORB);
        level.setBlockAndUpdate(orb, EssenceRegistries.ESSENCE_WELLSPRING.get().defaultBlockState());
        if (level.getBlockEntity(orb) instanceof EssenceOrbBlockEntity entity) {
            entity.setGrade(0.6);
        }
        // the capstones on celezite pillars, without gems yet
        var pillar = AARegistries.CELEZITE.polished.block().get().defaultBlockState();
        for (int point = 0; point < 8; point++) {
            int r = (point & 1) == 0 ? 4 : 3;
            BlockPos at = orb.offset(EssenceOrbBlockEntity.DX[point] * r, 0, EssenceOrbBlockEntity.DZ[point] * r);
            for (BlockPos below = at.below(); below.getY() >= o.getY(); below = below.below()) {
                level.setBlockAndUpdate(below, pillar);
            }
            level.setBlockAndUpdate(at, EssenceRegistries.ESSENCE_CAPSTONE.get().defaultBlockState());
        }
        level.setBlockAndUpdate(o, EssenceRegistries.ESSENCE_PLINTH.get().defaultBlockState());
        // two jars with rods inside the circle
        for (int side : new int[]{-2, 2}) {
            BlockPos jar = o.offset(side, 0, 2);
            level.setBlockAndUpdate(jar, ElectricRegistries.AETHER_JAR.get().defaultBlockState());
            level.setBlockAndUpdate(jar.above(), Blocks.LIGHTNING_ROD.defaultBlockState());
        }
    }

    /** Sets a gem in the capstones at the given compass points. */
    private static void gems(ServerLevel level, Item gem, int... points) {
        BlockPos orb = origin.offset(ORB);
        for (int point : points) {
            int r = (point & 1) == 0 ? 4 : 3;
            BlockPos at = orb.offset(EssenceOrbBlockEntity.DX[point] * r, 0, EssenceOrbBlockEntity.DZ[point] * r);
            if (level.getBlockEntity(at) instanceof EssenceCapstoneBlockEntity capstone) {
                capstone.setGem(new ItemStack(gem));
                level.setBlockAndUpdate(at, level.getBlockState(at).setValue(EssenceCapstoneBlock.GEM,
                        CapstoneGem.of(capstone.gem())));
            }
        }
    }

    private static void drop(ServerLevel level, double dx, Item item) {
        BlockPos at = origin.offset(6, 0, 6);
        ItemEntity entity = new ItemEntity(level, at.getX() + 0.5 + dx, at.getY() + 0.1, at.getZ() + 0.5,
                new ItemStack(item));
        entity.setDeltaMovement(Vec3.ZERO);
        entity.setPickUpDelay(32767);
        level.addFreshEntity(entity);
    }

    private static List<Step> script() {
        Vec3 wide = new Vec3(0.5, 6.5, 11.5), centre = new Vec3(0.5, 3.0, 0.5);
        Vec3 close = new Vec3(0.5, 5.2, 3.2), orb = Vec3.atCenterOf(ORB);
        List<Step> steps = new ArrayList<>();
        steps.add(new Step("circle_bare", level -> { }, wide, centre, 40, false, null));
        steps.add(new Step("orb_close", level -> { }, close, orb, 30, false, null));
        // the north-south and east-west pairs gemmed: two rings
        steps.add(new Step("two_rings", level -> gems(level, AetherItems.ZANITE_GEMSTONE.get(), 0, 2, 4, 6),
                close, orb, 60, false, null));
        // the diagonals too, with the other gems: all four rings and the motes to every capstone
        steps.add(new Step("four_rings", level -> {
            gems(level, AetherItems.AMBROSIUM_SHARD.get(), 1, 5);
            gems(level, AARegistries.ENCHANTED_GRAVITITE_INGOT.get(), 3);
            gems(level, AetherItems.ZANITE_GEMSTONE.get(), 7);
        }, close, orb, 60, false, null));
        steps.add(new Step("circle_full", level -> { }, wide, centre, 80, false, null));
        steps.add(new Step("capstone_gems", level -> { }, new Vec3(2.5, 4.6, -1.6), new Vec3(4.5, 4.5, -3.5), 20,
                false, null));
        // a jar on the plinth, charging: the stream of motes down to it, and the rod jars
        steps.add(new Step("plinth_jar", level -> {
            if (level.getBlockEntity(origin) instanceof EssencePlinthBlockEntity plinth) {
                plinth.insert(new ItemStack(ElectricRegistries.AETHER_JAR_ITEM.get(), 3), false);
            }
        }, new Vec3(0.5, 2.6, 4.2), new Vec3(0.5, 1.6, 0.5), 80, false, null));
        steps.add(new Step("plinth_close", level -> { }, new Vec3(0.5, 1.9, 2.0), new Vec3(0.5, 0.8, 0.5), 30,
                false, null));
        steps.add(new Step("rod_jars", level -> { }, new Vec3(0.5, 2.8, 6.5), new Vec3(0.5, 1.5, 2.5), 60, false,
                null));
        steps.add(new Step("items", level -> {
            drop(level, -0.9, EssenceRegistries.ESSENCE_CAPSTONE_ITEM.get());
            drop(level, -0.3, EssenceRegistries.ESSENCE_PLINTH_ITEM.get());
            drop(level, 0.3, EssenceRegistries.QUINTESSENCE_PHIAL.get());
            drop(level, 0.9, EssenceRegistries.DOWSING_ROD.get());
        }, new Vec3(6.5, 1.6, 9.0), new Vec3(6.5, 0.3, 6.5), 30, false, null));
        steps.add(new Step("jade_orb", level -> { }, new Vec3(0.5, 4.5, 2.2), orb, 50, true, null));
        steps.add(new Step("dowsing_rod", level -> {
            for (ServerPlayer player : level.players()) {
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(EssenceRegistries.DOWSING_ROD.get()));
            }
        }, new Vec3(0.5, 2.6, 8.5), new Vec3(0.5, 2.6, 0.5), 50, true, null));
        steps.add(new Step("jei_infusion", level -> { }, wide, centre, 30, true, () -> {
            Minecraft.getInstance().setScreen(null);
            if (ModList.get().isLoaded("jei")) {
                ElectricTourJei.showType(AlteredAether.id("essence_infusion"));
            }
        }));
        return steps;
    }

    @EventBusSubscriber(modid = AlteredAether.MOD_ID, value = Dist.CLIENT)
    public static final class Client {
        private static List<Step> steps;
        private static int index = -1, waited, settled;
        private static int framesHere;
        private static Vec3 wanted;

        @SubscribeEvent
        public static void onRender(RenderLevelStageEvent event) {
            Vec3 want = wanted;
            if (want != null && event.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL
                    && event.getCamera().getPosition().distanceToSqr(want) < 1.0E-4) {
                framesHere++;
            }
        }

        @SubscribeEvent
        public static void onTick(ClientTickEvent.Post event) {
            Minecraft mc = Minecraft.getInstance();
            BlockPos o = origin;
            if (!on() || o == null || mc.player == null || mc.level == null || !mc.level.dimension().equals(THE_AETHER)) {
                return;
            }
            MinecraftServer server = mc.getSingleplayerServer();
            if (steps == null) {
                steps = script();
            }
            if (index < 0) {
                boolean ready = mc.levelRenderer.hasRenderedAllSections() && mc.level.getBlockState(o.below()).isSolid();
                settled = ready ? settled + 1 : 0;
                mc.player.moveTo(o.getX() + 0.5, o.getY() + 4, o.getZ() + 12.5, 180, 20);
                if (settled < 80) {
                    return;
                }
                next(mc, server);
                return;
            }
            Step step = steps.get(index);
            Vec3 cam = Vec3.atLowerCornerOf(o).add(step.cam());
            Vec3 look = Vec3.atLowerCornerOf(o).add(step.look());
            double dx = look.x - cam.x, dy = look.y - cam.y, dz = look.z - cam.z;
            float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90;
            float pitch = (float) -(Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * Mth.RAD_TO_DEG);
            mc.player.moveTo(cam.x, cam.y - mc.player.getEyeHeight(), cam.z, yaw, pitch);
            mc.player.setYHeadRot(yaw);
            mc.player.setDeltaMovement(Vec3.ZERO);
            mc.options.hideGui = !step.gui();
            if (wanted == null || wanted.distanceToSqr(cam) > 1.0E-6) {
                wanted = cam;
                framesHere = 0;
            }
            if (++waited < step.delay()) {
                return;
            }
            if (step.client() != null && waited == step.delay()) {
                step.client().run();
            }
            if (waited < step.delay() + (step.client() != null ? 40 : 0)) {
                return;
            }
            if (mc.screen == null && framesHere < 3 && waited < step.delay() + 400) {
                return;
            }
            String dir = "essence_" + LABEL;
            new java.io.File(mc.gameDirectory, "screenshots/" + dir).mkdirs();
            String file = dir + "/" + String.format("%02d_%s.png", index, step.name());
            log("shooting {}", step.name());
            Screenshot.grab(mc.gameDirectory, file, mc.getMainRenderTarget(),
                    message -> AlteredAether.LOGGER.info("Essence tour: {}", message.getString()));
            next(mc, server);
        }

        private static void next(Minecraft mc, MinecraftServer server) {
            index++;
            waited = 0;
            if (index >= steps.size()) {
                log("done");
                origin = null;
                mc.stop();
                return;
            }
            Step step = steps.get(index);
            log("step {} {}", index, step.name());
            if (server != null) {
                server.execute(() -> {
                    ServerLevel aether = server.getLevel(THE_AETHER);
                    if (aether != null) {
                        try {
                            step.setup().accept(aether);
                        } catch (RuntimeException e) {
                            AlteredAether.LOGGER.error("Essence tour: step {} failed", step.name(), e);
                        }
                    }
                });
            }
        }

        private Client() {
        }
    }
}
